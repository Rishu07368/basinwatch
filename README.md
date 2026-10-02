# BasinWatch

**BasinWatch** is an offline floodplain operations console for monitoring a changing basin scenario and coordinating limited response resources. It is a desktop planning simulation, not a Java concept demo, certified flood forecast, or real emergency dispatch system.

## What the operator can do

- Watch a schematic basin map as rain and water levels change over time.
- Review station observations and severity-ranked zone alerts.
- Dispatch pumps, levee crews, and evacuation teams to concurrent response missions.
- See available resources, active mission progress, event history, and resulting risk changes.
- Pause, step, or tune a scenario; inspect background-system health only when desired.
- Save a session, close BasinWatch, and restore the model and active operations later.
- Generate readable operational reports and retain a typed sensor archive for review.

## Run

Requirements for a developer build: JDK 17 or newer and Maven 3.8 or newer. The interface uses standard Swing; no JavaFX runtime, external service, database, or network connection is required.

From this directory:

```powershell
mvn package
java -jar target\basinwatch-1.0.0.jar
```

The first launch creates `data\logs`, `data\archive`, `data\saves`, and `data\reports` under the user's BasinWatch data folder (`%APPDATA%\BasinWatch` on Windows, `~/.basinwatch` elsewhere). A user-selected save can be loaded after relaunch. To select another writable location, start the app with `--data-dir "D:\BasinWatch Data"`.

## Deploy to another Windows PC

See [DEPLOYMENT.md](DEPLOYMENT.md) for step-by-step instructions to build and distribute a portable Windows app bundle that includes its Java runtime. End users do not need Java or Maven installed.

## Example workflow

1. Start the observation feed or advance one simulation step.
2. Select a zone after a high-water alert appears.
3. Review its risk drivers and the current pump/crew/vehicle stock.
4. Dispatch a feasible response. The reservation is atomic; another operation cannot claim those same units.
5. Follow observations, mission progress, and changing zone risk in the timeline and map.
6. Save the scenario, exit cleanly, then restore the save to continue.

## Architecture

The Swing event-dispatch thread exclusively owns the UI. A dedicated `SensorFeed` thread writes observation records through connected piped character streams. An intake task validates and places them into a bounded producer-consumer buffer. A small worker set handles accepted events and progresses independent response work. `wait()`/`notifyAll()` coordinate work availability; synchronized resource reservation protects genuinely shared finite inventory. Workers publish immutable snapshots to the Swing event queue.

The resource allocator uses one short critical section and a stable acquisition policy; normal operation does not intentionally deadlock. Severity ordering is explicit domain data, not a thread-priority assumption. The optional Systems Inspector reports worker states and queue metrics, and an optional experiment can alter feed/worker settings without changing core correctness.

Persistence uses:

- UTF-8 `BufferedWriter` operational logs and reports.
- A `BufferedOutputStream`/`DataOutputStream` archive for typed sensor measurements.
- A validated, versioned `SessionSnapshot` serialized to a temporary file and atomically replaced where supported. Runtime locks, threads, and Swing objects are excluded and recreated after load.
- `File` metadata for listing saves and displaying file sizes.

See [ARCHITECTURE.md](ARCHITECTURE.md) for lifecycles, lock rules, error boundaries, and tests. See [PROJECT_CONCEPT_MAP.md](PROJECT_CONCEPT_MAP.md) for the syllabus-to-feature mapping, and [PROJECT_CONCEPTS.md](PROJECT_CONCEPTS.md) for the domain shortlist and selection analysis.

## Java technologies and concepts

- Java 17+, Swing, standard `java.io`, and `java.util.concurrent` only where a standard lifecycle/EDT handoff is appropriate.
- Encapsulated domain aggregates, immutable event/snapshot values, enums for bounded domain states, and meaningful polymorphic events.
- `Thread`, `Runnable`, interruption/join, piped streams, bounded producer-consumer communication, monitors, synchronization, and optional priority experiments.
- Byte/character streams, buffering, data streams, byte-array streams, file metadata, object serialization, and explicit exception handling.

## Build and test

```powershell
mvn package
.\scripts\test.ps1
```

The test runner compiles and executes dependency-free invariant tests with assertions enabled. Tests exercise concurrent resource contention, buffer waiting/closure, event processing, persistence round-trip and failure cases, and orderly shutdown.

## Project layout

```text
src/main/java/com/basinwatch/
  app/          Swing entry point, frame, map and panels
  domain/       Basin, zones, events, resources and missions
  engine/       Sensor feed, intake, event buffer, workers and lifecycle
  io/           Snapshot persistence, binary archive, logs and reports
  learning/     Optional architecture inspector content
src/test/java/  Dependency-free application/core invariant tests
scripts/        Local test runner
user data/      %APPDATA%\BasinWatch\data on Windows
```

## Known limitations

- The map is a schematic, not geospatially accurate.
- Rainfall, levels, timing, equipment capacities, and risk thresholds are transparent scenario parameters, not calibrated hydrology.
- The app is a single-user local simulation; it does not ingest real sensors, send alerts, control gates/pumps, or connect to dispatch services.
- Java thread priority is nondeterministic and is never a correctness mechanism.
- Java object serialization is private to this application/versioned snapshot format; only local application-created saves should be loaded.
- Swing appearance varies by operating system; accessible text labels and keyboard-operable controls remain available.

## Future improvements

Import a user-authored scenario format, add replay scrubbing and exportable CSV reports, improve map editing, add explicit fault-injection scenarios for resource contention, and validate a hydrology model before any real-world advisory use.
