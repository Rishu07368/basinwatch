# Project concepts and architectural selection

## Syllabus inspected before implementation

The primary learning reference is [OOP_Unit_2_Interactive_Notes.html](../OOP_Unit_2_Interactive_Notes.html). It contains 14 topics and 28 source-aligned program examples. The actual scope is:

- Java I/O foundations; byte streams; `InputStream`/`OutputStream`; files and `File`; character streams; filter, buffered and data streams; byte-array streams; serialization, deserialization and `transient`; piped streams.
- Processes and threads; creating threads with `Thread` and `Runnable`; lifecycle and core methods; synchronization, monitor locks and critical sections; inter-thread communication and producer/consumer using `wait`, `notify` and `notifyAll`; priority; deadlock and safe scheduling expectations.
- The material also covers exception handling, console streams, stream hierarchies, thread advantages/disadvantages, source exercises and MCQs.

Its marked clarifications are part of the design constraints: Java reports `RUNNABLE` rather than separate `RUNNING`; `wait()` releases its monitor while `sleep()` does not; priority is only a scheduling hint; `File` describes paths/metadata rather than file contents; byte-array input does not grow its source array; and the source's synchronized-accessor producer/consumer sketch is not sufficient condition signalling.

## Candidate concepts

### 1. BasinWatch — Floodplain Operations Console

- **Real-world problem:** Flood operators need to interpret rising water and rain observations, identify threatened districts, and coordinate limited response equipment before access routes fail.
- **Who would use it:** A municipal flood-control room, basin authority, or emergency-planning team.
- **What the user does:** Watches a live basin map and sensor feed, starts or steps through a storm, dispatches pumps/levee crews/evacuation teams, and reviews the resulting operations.
- **Why it is interesting:** Conditions evolve while multiple districts compete for finite resources; user decisions have visible consequences on risk and mission outcomes.
- **Major features:** Zone map, sensor stations, changing water levels, severity-ranked alerts, concurrent field operations, resource board, activity timeline, save/restore, and an adjustable simulation.
- **Naturally needed Unit 2 concepts:** Sensor/event streams, a live producer-consumer pipeline, threads for independent observation and response, synchronized resource reservation, condition waiting, file logs and archives, and saved simulation state.
- **Technical challenge:** Keep sensor ingestion responsive while operations modify shared zone and resource state; make each reservation atomic and prevent lock-order deadlocks.
- **Visual potential:** Strong: a basin/river schematic, risk coloring, station readings, active mission routes, resource availability, and a chronological event feed.
- **Desktop feasibility:** High with Swing and the standard JDK; custom-painted panels and local files need no external service.

### 2. GridRestoration — Electric Distribution Outage Desk

- **Real-world problem:** A storm can disable feeders and substations while restoration crews, switching windows, and backup generators are limited.
- **Who would use it:** Distribution-grid dispatchers and utility restoration planners.
- **What the user does:** Inspects a network diagram, investigates faults, assigns crews, switches supply paths, and prioritizes restoration.
- **Why it is interesting:** The dependency graph means a local repair can restore many downstream customers, while unsafe switching can isolate a whole branch.
- **Major features:** Feeder topology, load/outage overlays, crew dispatch, repair progress, switching actions, and restoration estimates.
- **Naturally needed Unit 2 concepts:** Concurrent fault reports and repair operations, synchronization for shared crews/switches, event queues, persistent outage logs, and save/restore.
- **Technical challenge:** Model network dependencies and enforce safe switching/resource invariants under concurrent repairs.
- **Visual potential:** High: a live node-and-feeder diagram with energized/de-energized paths and load indicators.
- **Desktop feasibility:** High as a deliberately bounded distribution-network simulation; a full electrical power-flow solver would be out of scope.

### 3. ColdTrace — Temperature Excursion Response

- **Real-world problem:** Temperature excursions can invalidate sensitive medicines or biological samples before a shipment reaches its destination.
- **Who would use it:** Cold-chain coordinators at clinics, laboratories, and distribution depots.
- **What the user does:** Monitors sensor traces, identifies at-risk consignments, reroutes shipments to refrigeration capacity, and records disposition decisions.
- **Why it is interesting:** A small delay can turn a routine delivery into a time-critical response with constrained storage.
- **Major features:** Shipment timeline, temperature chart, depot capacity, excursion alerts, rerouting, evidence export, and case restore.
- **Naturally needed Unit 2 concepts:** Sensor ingestion, background analysis, synchronized refrigeration-capacity reservations, byte-oriented readings, buffered text reports, and persistent case snapshots.
- **Technical challenge:** Correlate out-of-order measurements with shipment state and avoid double-booking scarce cold storage.
- **Visual potential:** High: temperature bands and time remaining are naturally chartable; depots and routes form a useful network view.
- **Desktop feasibility:** High for local simulation and planning; live telemetry integrations would require external systems and are intentionally excluded.

### 4. SkyShield — Airport Wildlife Hazard Desk

- **Real-world problem:** Wildlife activity near runways creates rapidly changing hazards that must be assessed alongside aircraft movements and mitigation teams.
- **Who would use it:** Airport wildlife-control coordinators and airfield operations staff.
- **What the user does:** Reviews sightings, assigns patrols, closes or reopens affected areas, and watches risk change with time and weather.
- **Why it is interesting:** The same sighting can have different operational consequences depending on runway activity, species, and location.
- **Major features:** Airfield schematic, sighting feed, risk zones, patrol dispatch, runway-area status, and incident reports.
- **Naturally needed Unit 2 concepts:** Concurrent observations, queued incident processing, synchronized patrol assignment, text and binary records, and saved operational state.
- **Technical challenge:** Maintain a coherent view of overlapping hazard and runway states without claiming to replace certified airport procedures.
- **Visual potential:** High: runway geometry and time-stamped sightings make a focused operational display.
- **Desktop feasibility:** High as a training/planning simulation; it must clearly state that it is not an operational safety system.

### 5. RailPulse — Passenger Rail Disruption Desk

- **Real-world problem:** Track faults and delays propagate through a timetable while trains, platforms, and recovery crews have limited capacity.
- **Who would use it:** Rail traffic controllers and service-recovery planners.
- **What the user does:** Reviews disruptions, applies platform or routing changes, dispatches repair teams, and examines downstream delay propagation.
- **Why it is interesting:** A small upstream delay can cascade into a network-wide scheduling problem.
- **Major features:** Route graph, train timeline, incident feed, platform capacity, repair missions, and service-recovery snapshots.
- **Naturally needed Unit 2 concepts:** Concurrent incident and train updates, synchronized track/platform reservations, producer-consumer event handling, and file-based recovery records.
- **Technical challenge:** Track route conflicts and delay dependencies while making each reroute atomic.
- **Visual potential:** Very high: a time-distance timetable and animated route graph communicate propagation clearly.
- **Desktop feasibility:** High for a small fictional network; real railway control integration is explicitly excluded.

### 6. Pelagic Survey — Marine Research Expedition Console

- **Real-world problem:** Research vessels and autonomous instruments collect observations under limited battery, bandwidth, and weather windows.
- **Who would use it:** Oceanographic field teams and expedition planners.
- **What the user does:** Schedules survey legs, monitors instrument status, prioritizes incoming observations, and preserves a recoverable mission record.
- **Why it is interesting:** Weather and equipment limits force trade-offs between coverage, sample quality, and safe return.
- **Major features:** Survey-area map, vehicle status, sample stream, mission timeline, battery allocation, and exportable field notes.
- **Naturally needed Unit 2 concepts:** Concurrent instrument work, piped readings, shared battery/vehicle coordination, byte archives, character reports, and serialization of mission progress.
- **Technical challenge:** Coordinate asynchronous instrument data with mission scheduling under intermittent availability.
- **Visual potential:** High: geographic transects, sensor footprints, and resource endurance provide meaningful visuals.
- **Desktop feasibility:** High as an offline simulator; actual oceanographic hardware and navigation integrations are out of scope.

### 7. NightSky Scheduler — Observatory Night Operations

- **Real-world problem:** Observing time is scarce and weather, equipment faults, and target visibility change throughout the night.
- **Who would use it:** Observatory operators and astronomy research teams.
- **What the user does:** Builds an observing queue, responds to weather windows, allocates instruments, and reviews data capture.
- **Why it is interesting:** A lost observation window cannot simply be recovered later; choices affect scientific yield.
- **Major features:** Night timeline, visibility windows, instrument status, queue prioritization, capture log, and session restore.
- **Naturally needed Unit 2 concepts:** Concurrent weather/instrument updates, synchronized telescope reservations, buffered observation records, and persistent snapshots.
- **Technical challenge:** Respect equipment conflicts and changing time windows while keeping scheduling rules understandable.
- **Visual potential:** High: a time-window timeline and sky chart are compelling and informative.
- **Desktop feasibility:** High for a fictional observatory; precise astronomical calculations and real telescope control would require additional libraries and safety work.

### 8. ArchiveClimate — Museum Environmental Incident Desk

- **Real-world problem:** Temperature, humidity, or water ingress outside safe ranges can damage collections, and response equipment/room capacity is limited.
- **Who would use it:** Museum conservators and facilities teams.
- **What the user does:** Watches room sensors, prioritizes threatened galleries, moves vulnerable cases, and documents interventions.
- **Why it is interesting:** A facilities event becomes a time-sensitive conservation decision with competing priorities.
- **Major features:** Gallery plan, climate trends, threshold alerts, intervention resources, case history, and incident reports.
- **Naturally needed Unit 2 concepts:** Sensor processing threads, condition-based event queues, synchronized equipment/room reservations, file logs, and saved incident state.
- **Technical challenge:** Preserve a coherent history while changing room conditions and intervention availability concurrently.
- **Visual potential:** High: gallery zones, climate charts, and risk gradients fit a visual desktop tool.
- **Desktop feasibility:** High as a planning simulator; it must not present simulated readings as real conservation advice.

## Selection and architectural reason

**Selected: BasinWatch — Floodplain Operations Console.**

The central problem is: **Help a basin operations team monitor changing flood conditions, identify threatened zones, and coordinate limited response resources while new observations and field operations arrive concurrently.**

This is selected because the concurrency and shared-state demands arise directly from the domain: sensor observations continue while independent field operations progress, and several operations may compete for the same pumps, crews, or evacuation vehicles. A single Swing application can show those consequences in a basin map and event timeline without a backend, fabricated network service, or a large framework. It also gives file persistence, audit logging, binary sensor archives, and session recovery genuine operational purposes.

The application will deliberately remain a bounded offline planning simulation—not a certified flood forecast or dispatch system. Thread priority will be diagnostic/experimental only; correctness and severity order will come from explicit application rules. The default resource coordinator will use one short-lived monitor-protected reservation transaction and a stable resource order, avoiding a normal-operation deadlock rather than relying on thread priority or timing.
