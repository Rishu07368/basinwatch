package com.basinwatch.domain;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Locale;

public final class BasinModel {
    private static final int ACTIVITY_LIMIT = 160;

    private final LinkedHashMap<String, BasinZone> zones = new LinkedHashMap<>();
    private final LinkedHashMap<String, ResponseMission> missions = new LinkedHashMap<>();
    private final ArrayDeque<ActivityEntry> activity = new ArrayDeque<>();
    private final ResourceAllocator resources;
    private final AtomicLong missionSequence = new AtomicLong();
    private long tick;
    private long lastPulseTick = -1;

    public BasinModel() {
        this(ResourceAllocator.standard());
        addZone("UPR", "Upper Weir", 0, 0, 1.08, 1800);
        addZone("MIL", "Mill Quarter", 1, 0, 1.34, 5200);
        addZone("NOR", "Northbank", 2, 0, 0.91, 2400);
        addZone("OLD", "Old Market", 0, 1, 1.24, 7100);
        addZone("MAR", "Marsh End", 1, 1, 0.74, 1100);
        addZone("SOU", "South Reach", 2, 1, 1.02, 3100);
        record("SYSTEM", "BasinWatch scenario ready · 6 monitored zones · local simulation");
    }

    private BasinModel(ResourceAllocator resources) {
        this.resources = resources;
    }

    private void addZone(String id, String name, int column, int row, double water, int residents) {
        zones.put(id, new BasinZone(id, name, column, row, water, residents));
    }

    public synchronized List<String> process(BasinEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event is required.");
        }
        ArrayList<String> messages = new ArrayList<>();
        if (event instanceof SensorReading reading) {
            applyReading(reading, messages);
        } else if (event instanceof SimulationPulse pulse) {
            applyPulse(pulse, messages);
        } else if (event instanceof DispatchRequest request) {
            applyDispatch(request, messages);
        } else {
            throw new IllegalArgumentException("Unsupported basin event: " + event.getClass().getName());
        }
        return List.copyOf(messages);
    }

    private void applyReading(SensorReading reading, List<String> messages) {
        BasinZone zone = requireZone(reading.zoneId());
        RiskLevel previousRisk = zone.snapshot().risk();
        if (!zone.apply(reading)) {
            messages.add("Ignored stale station reading from " + reading.stationId());
            return;
        }
        tick = Math.max(tick, reading.tick());
        RiskLevel currentRisk = zone.snapshot().risk();
        String message = zone.compactStatus() + " · rain "
                + String.format(Locale.ROOT, "%.1f mm", reading.rainfallMillimeters());
        if (currentRisk.rank() > previousRisk.rank()) {
            message = "Risk increased: " + message;
        }
        record(currentRisk.rank() > previousRisk.rank() ? "RISK UPDATE" : "SENSOR", message);
        messages.add(message);
    }

    private void applyPulse(SimulationPulse pulse, List<String> messages) {
        if (pulse.tick() <= lastPulseTick) {
            return;
        }
        lastPulseTick = pulse.tick();
        tick = Math.max(tick, pulse.tick());
        for (ResponseMission mission : missions.values()) {
            if (!mission.advance()) {
                continue;
            }
            BasinZone zone = requireZone(mission.snapshot().zoneId());
            switch (mission.snapshot().type()) {
                case PUMP_DEPLOYMENT -> zone.pump(mission.snapshot().type().waterEffectMeters());
                case LEVEE_REINFORCEMENT -> zone.reinforceLevee();
                case EVACUATION -> zone.evacuate();
            }
            resources.release(mission.snapshot().type().requirements());
            String message = mission.snapshot().type().label() + " complete · " + zone.name();
            record("MISSION COMPLETE", message);
            messages.add(message);
        }
        record("SYSTEM", "Simulation interval " + pulse.tick());
    }

    private void applyDispatch(DispatchRequest request, List<String> messages) {
        BasinZone zone = requireZone(request.zoneId());
        MissionType type = request.missionType();
        pruneMissionHistory();
        if (missions.size() >= 500) {
            String message = "Mission history is full; export a report before starting another operation";
            record("RESOURCE LIMIT", message);
            messages.add(message);
            return;
        }
        if (!resources.tryReserve(type.requirements())) {
            String message = "Insufficient available units for " + type.label();
            record("RESOURCE LIMIT", message + " · " + zone.name());
            messages.add(message);
            return;
        }
        String id = "BW-" + String.format("%04d", missionSequence.incrementAndGet());
        ResponseMission mission = new ResponseMission(id, type, zone.id(), tick);
        missions.put(id, mission);
        String message = id + " dispatched · " + type.label() + " · " + zone.name();
        record("UNIT ALLOCATED", message);
        messages.add(message);
    }

    public synchronized BasinSnapshot snapshot() {
        List<ZoneSnapshot> zoneSnapshots = zones.values().stream()
                .map(BasinZone::snapshot)
                .toList();
        List<MissionSnapshot> missionSnapshots = missions.values().stream()
                .map(ResponseMission::snapshot)
                .sorted(Comparator.comparing(MissionSnapshot::startedAtTick).reversed())
                .toList();
        return new BasinSnapshot(tick, zoneSnapshots, resources.snapshot(), missionSnapshots,
                List.copyOf(activity));
    }

    public synchronized void recordSystemEvent(String category, String message) {
        record(category, message);
    }

    public synchronized long currentTick() {
        return tick;
    }

    public synchronized int zoneCount() {
        return zones.size();
    }

    public static BasinModel restore(BasinSnapshot saved) {
        if (saved == null || saved.tick() < 0 || saved.zones().size() != 6) {
            throw new IllegalArgumentException("Saved basin state is incomplete.");
        }
        EnumMap<ResourceType, Integer> stock = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            Integer count = saved.availableResources().get(type);
            if (count == null || count < 0 || count > 50) {
                throw new IllegalArgumentException("Saved resource inventory is invalid.");
            }
            stock.put(type, count);
        }
        EnumMap<ResourceType, Integer> standardCapacity = new EnumMap<>(
                ResourceAllocator.standard().snapshot());
        EnumMap<ResourceType, Integer> commitments = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            commitments.put(type, 0);
        }
        BasinModel model = new BasinModel(new ResourceAllocator(standardCapacity, stock));
        for (ZoneSnapshot zoneState : saved.zones()) {
            BasinZone zone = BasinZone.restore(zoneState);
            if (model.zones.putIfAbsent(zone.id(), zone) != null) {
                throw new IllegalArgumentException("Saved basin has duplicate zones.");
            }
        }
        if (!model.zones.keySet().containsAll(List.of("UPR", "MIL", "NOR", "OLD", "MAR", "SOU"))) {
            throw new IllegalArgumentException("Saved basin zones do not match this scenario.");
        }
        model.tick = saved.tick();
        model.lastPulseTick = saved.tick();
        for (MissionSnapshot missionState : saved.missions()) {
            if (missionState.id() == null || missionState.type() == null
                    || !model.zones.containsKey(missionState.zoneId())
                    || missionState.status() == null
                    || missionState.progressTicks() < 0
                    || missionState.progressTicks() > missionState.durationTicks()
                    || missionState.durationTicks() != missionState.type().durationTicks()
                    || missionState.startedAtTick() < 0) {
                throw new IllegalArgumentException("Saved response mission is invalid.");
            }
            ResponseMission mission = new ResponseMission(missionState.id(), missionState.type(),
                    missionState.zoneId(), missionState.startedAtTick(), missionState.progressTicks(),
                    missionState.status());
            if (model.missions.putIfAbsent(missionState.id(), mission) != null) {
                throw new IllegalArgumentException("Saved basin has duplicate mission identifiers.");
            }
            long sequence = parseMissionSequence(missionState.id());
            model.missionSequence.accumulateAndGet(sequence, Math::max);
            if (missionState.status() == MissionStatus.ACTIVE) {
                missionState.type().requirements().forEach((type, amount) ->
                        commitments.merge(type, amount, Math::addExact));
            }
        }
        for (ResourceType type : ResourceType.values()) {
            if (stock.get(type) + commitments.get(type) != standardCapacity.get(type)) {
                throw new IllegalArgumentException(
                        "Saved stock and active reservations do not balance for " + type + ".");
            }
        }
        for (ActivityEntry entry : saved.activity()) {
            if (entry != null && entry.at() != null && entry.category() != null
                    && entry.message() != null) {
                model.activity.addLast(entry);
                while (model.activity.size() > ACTIVITY_LIMIT) {
                    model.activity.removeFirst();
                }
            }
        }
        return model;
    }

    private static long parseMissionSequence(String id) {
        try {
            return Long.parseLong(id.substring(id.lastIndexOf('-') + 1));
        } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
            return 0;
        }
    }

    private BasinZone requireZone(String zoneId) {
        BasinZone zone = zones.get(zoneId);
        if (zone == null) {
            throw new IllegalArgumentException("Unknown flood zone: " + zoneId);
        }
        return zone;
    }

    private void record(String category, String message) {
        activity.addLast(new ActivityEntry(Instant.now(), category, message));
        while (activity.size() > ACTIVITY_LIMIT) {
            activity.removeFirst();
        }
    }

    private void pruneMissionHistory() {
        while (missions.size() >= 500) {
            String completedMission = missions.values().stream()
                    .filter(mission -> mission.snapshot().status() != MissionStatus.ACTIVE)
                    .map(mission -> mission.snapshot().id())
                    .findFirst()
                    .orElse(null);
            if (completedMission == null) {
                return;
            }
            missions.remove(completedMission);
        }
    }
}
