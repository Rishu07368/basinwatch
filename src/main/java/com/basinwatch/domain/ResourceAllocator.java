package com.basinwatch.domain;

import java.util.EnumMap;
import java.util.Map;

public final class ResourceAllocator {
    private final EnumMap<ResourceType, Integer> available = new EnumMap<>(ResourceType.class);
    private final EnumMap<ResourceType, Integer> capacity = new EnumMap<>(ResourceType.class);

    public ResourceAllocator(Map<ResourceType, Integer> initialStock) {
        this(initialStock, initialStock);
    }

    public ResourceAllocator(Map<ResourceType, Integer> totalCapacity,
                             Map<ResourceType, Integer> initiallyAvailable) {
        for (ResourceType type : ResourceType.values()) {
            int maximum = totalCapacity.getOrDefault(type, 0);
            int amount = initiallyAvailable.getOrDefault(type, 0);
            if (maximum < 0 || amount < 0 || amount > maximum) {
                throw new IllegalArgumentException("Available resource stock is outside its capacity.");
            }
            available.put(type, amount);
            capacity.put(type, maximum);
        }
    }

    public static ResourceAllocator standard() {
        return new ResourceAllocator(Map.of(
                ResourceType.PUMP, 3,
                ResourceType.LEVEE_CREW, 3,
                ResourceType.SUPPLY_TRUCK, 2,
                ResourceType.EVAC_VEHICLE, 2,
                ResourceType.FIELD_CREW, 4));
    }

    public synchronized boolean tryReserve(Map<ResourceType, Integer> request) {
        validateRequest(request);
        for (Map.Entry<ResourceType, Integer> entry : request.entrySet()) {
            if (available.get(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        request.forEach((type, amount) -> available.put(type, available.get(type) - amount));
        return true;
    }

    public synchronized void release(Map<ResourceType, Integer> reservation) {
        validateRequest(reservation);
        reservation.forEach((type, amount) -> {
            if (amount > capacity.get(type) - available.get(type)) {
                throw new IllegalStateException("Released units exceed the reserved stock for " + type + ".");
            }
        });
        reservation.forEach((type, amount) -> available.merge(type, amount, Math::addExact));
    }

    public synchronized Map<ResourceType, Integer> snapshot() {
        return Map.copyOf(available);
    }

    private static void validateRequest(Map<ResourceType, Integer> request) {
        if (request == null || request.isEmpty()) {
            throw new IllegalArgumentException("A resource request must not be empty.");
        }
        request.forEach((type, amount) -> {
            if (type == null || amount == null || amount <= 0) {
                throw new IllegalArgumentException("Resource requests must be positive and typed.");
            }
        });
    }
}
