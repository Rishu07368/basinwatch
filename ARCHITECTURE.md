# BasinWatch architecture

## Runtime shape

The first release is a single-process, offline Swing desktop application. The event-dispatch thread (EDT) owns all Swing components. A `BasinEngine` owns the model, sensor feed, intake, bounded event buffer, response workers, and persistence services. Domain services publish immutable view snapshots to the EDT; they never mutate components directly.

```text
SensorFeed (Thread)
  └─ PipedWriter ──> PipedReader + BufferedReader (SensorIntake, Runnable)
                           └─ bounded monitor-protected EventBuffer
                                  └─ response worker Runnables
                                       ├─ BasinModel / zone risk
                                       ├─ ResourceAllocator (atomic reservation)
                                       └─ Mission progress
                                             └─ immutable UI snapshots → EDT

Model events ──> BufferedWriter operational log
             ├─ Buffered DataOutputStream sensor archive
             └─ bounded asynchronous DatabaseService → JDBC repositories → SQLite
Session snapshot ──> ObjectOutputStream → temporary save → atomic replace
```

## Domain boundaries

- `BasinModel` is the aggregate root for the current simulation tick, zones, stations, inventory, missions, and recent incidents. It enforces model invariants and returns immutable snapshots.
- `FloodZone` owns its simulated water state and risk assessment. `SensorStation` describes a source and its local readings.
- `BasinEvent` is an immutable event contract; concrete rain, water-level, and operator-action events carry only validated data.
- `ResponseMission` represents an active action with explicit status, target zone, progress, and resource requirements. Mission progression is deterministic from simulation ticks, not worker sleep timing.
- `ResourceAllocator` performs all-or-nothing multi-resource reservation and release under one short monitor. It performs no file, UI, or blocking operation while holding that monitor.
- `BasinEngine` owns lifecycle and connects the feed, intake, buffer, workers, model, and persistence. It accepts explicit commands rather than exposing mutable model collections.

## Threading and coordination

1. `SensorFeed extends Thread` emits a bounded number of encoded sensor readings per simulation interval through a connected `PipedWriter`. Interrupt and pipe close signal shutdown.
2. `SensorIntake implements Runnable` reads complete lines from the connected `PipedReader`, parses and validates the record, then enqueues an immutable event.
3. `EventBuffer` has a fixed capacity. `put` waits while full; `take` waits while empty. Both check their conditions in `while` loops while holding the same intrinsic monitor. Producers and close signal `notifyAll()`. Closing the buffer wakes every waiter and drains already accepted work before workers exit.
4. A configured small number of `ResponseWorker implements Runnable` tasks consume events. Model transitions and resource reservation are guarded at the aggregate/allocator boundary; workers do not hold those locks while they wait, log, or publish UI updates.
5. The engine uses explicit severity and timestamp ordering in application data. `Thread.setPriority` is available only to an optional experiment and cannot reorder incidents or guarantee execution.
6. Shutdown stops new feed work, closes the pipe and event buffer, interrupts waits, joins owned threads with a bounded timeout, flushes and closes persistence writers, then saves a consistent snapshot. It reports a timeout or I/O error rather than pretending shutdown succeeded.

## Resource safety and deadlock

Multiple response missions can need pumps, crews, or vehicles. A reservation is an all-or-nothing operation under a single allocator monitor, so two simultaneous dispatches cannot reserve the same unit. The critical section contains only inventory validation and state updates.

This architecture avoids lock cycles in normal operation: it has one resource-inventory monitor and never nests it with the basin model lock; where model and inventory changes need one transaction, the aggregate owns the documented lock order. No lock is held during I/O, sleeps, callbacks, or condition waits on a different monitor. Tests stress simultaneous reservations and assert conservation of inventory. A separate deadlock diagram in the Engineering Inspector explains the two-lock circular-wait hazard without placing the normal application in an intentional deadlock.

## Persistence and I/O

- `data/logs/operations.log`: UTF-8, append-only human-readable operational events using `BufferedWriter`.
- `data/archive/sensors.bin`: versioned records written with `DataOutputStream` over `BufferedOutputStream`; each record has an explicit tick, station/zone, measurement values, and event code.
- `data/saves/*.bws`: a versioned `SessionSnapshot` written to a temporary sibling and then atomically moved into place when supported. The snapshot includes model state, not live threads, monitors, Swing objects, or open streams.
- `data/reports/`: operator-generated text summaries. Reports use character streams and explicit UTF-8.
- `data/basinwatch.db`: optional SQLite history for accepted sensor readings, mission lifecycle, allocated resources, and important operational events. It supplements, and does not replace, the file archive, journal, report, or session snapshot.
- `File` metadata is used to list available snapshots and archive/log sizes. File paths and data content remain separate responsibilities.
- The save reader applies `ObjectInputFilter` limits and an application-class allowlist, validates the snapshot version and domain invariants, and rejects corrupt/unsupported saves with an actionable message. Saves are intended for local app use and are not a general interchange format.

`ByteArrayOutputStream`/`ByteArrayInputStream` are confined to building and validating bounded snapshot payloads before save replacement. They do not replace persistent files.

### JDBC architecture and learning notes

```text
BasinWatch Simulation
        |
        v
Domain / Engine
        |
        +------------------+
        |                  |
        v                  v
File Persistence       JDBC Layer
                           |
                           v
                        SQLite
```

SQLite is an embedded, offline database: there is no server to install or start. `AppPaths.database()` places it beside the app's existing data folders. `DatabaseManager` builds the SQLite JDBC URL and opens a fresh connection for each repository operation. `DatabaseInitializer` creates the schema at startup.

| JDBC term | BasinWatch implementation |
|---|---|
| JDBC driver | Xerial `sqlite-jdbc` from `pom.xml`; it translates standard JDBC calls to SQLite |
| `Connection` | `DatabaseManager.openConnection()` opens/configures a connection; repositories close it with try-with-resources |
| `PreparedStatement` | Repository inserts, filters, updates, and deletes bind values with `setString`, `setDouble`, and `setInt` |
| `ResultSet` | Repository `findRecent` methods read rows and map them to immutable `DatabaseHistoryEntry` values |
| `SQLException` | `DatabaseService` reports failures to `BasinEngine`; an unavailable database does not stop the simulation |
| Transaction | `MissionRepository.recordDispatch` commits mission + resources + dispatch event together; `updateStatus` commits completion + event together and rolls back on failure |

| Table | Stored data |
|---|---|
| `sensor_readings` | Timestamp, zone, water level, rainfall, and calculated risk |
| `missions` | Mission ID/type, zone, status, creation time, and completion time |
| `resource_usage` | Resource type and amount allocated to a mission, with timestamp |
| `operational_events` | Important risk and mission events with timestamp, zone, and message |

`DatabaseService` owns a bounded single-thread executor. The response workers enqueue immutable values; SQL, history reads, startup initialization, and retention cleanup run on that worker rather than on Swing's EDT. Repositories use `PreparedStatement` parameters for application data, `ResultSet` for reads, and try-with-resources for connections/statements/results. Dispatch inserts the mission, its resource usage, and its event in one transaction. Completion updates the active mission and inserts a completion event in one transaction; SQL failures roll back the transaction. Old sensor rows are pruned after initialization. The project's Maven shade step bundles the driver so the existing `java -jar` run command and portable app-image continue working without a separate dependency classpath.

Database errors are reported in the application status/history view and operational log. A failed optional database write does not roll back or stop the simulation and does not replace its file-based persistence. UI history refreshes are asynchronous and update Swing components only on the EDT.

## UI structure

- **Situation overview:** basin map, six zones with risk/water level, sensor markers, current simulation state, and a compact operational timeline.
- **Response desk:** available unit counts, active missions, incident detail, and validated dispatch controls.
- **Activity feed:** chronological status, warning, allocation, completion, and persistence events.
- **Systems inspector (optional):** worker states, queue depth/capacity, event throughput, save/archive paths and sizes, and experiment controls. It is diagnostic, not needed for normal operation.
- **Architecture notes (optional):** short explanations of feature purpose, Java mechanism, source location, and failure mode, all keyed to the concept map.
- **Database history:** asynchronous views of recent sensor readings, missions, operational events, and resource allocations stored in SQLite.

Swing uses a restrained dark-navy operations palette with risk colors reserved for zone conditions. The basin view is a purposeful schematic, clearly marked as illustrative and not a geographic forecast.

## Error boundaries

- Sensor text is parsed and validated at intake; malformed records are logged and counted without terminating the intake service.
- Domain constructors and command validation reject impossible zone IDs, negative quantities, and invalid mission transitions.
- Resource shortages return a typed dispatch outcome and are shown to the operator; they do not become null state or success-shaped fallbacks.
- Persistence distinguishes missing, unsupported-version, invalid-filter, corrupt, and ordinary I/O failures.
- SQLite initialization, writes, and history queries report `SQLException` failures without making the optional database a dependency for simulation or file persistence.
- Worker exceptions are reported with event context; the engine marks a failed operation and continues unrelated work where invariants remain intact.
- Interrupted workers preserve interrupt status and follow the orderly shutdown path.
- Swing actions surface errors in a status banner/dialog and preserve the prior valid model state.

## Test strategy

The Java assertion suite runs through `scripts/test.ps1` with Maven-provided SQLite JDBC dependencies. It covers atomic concurrent reservation, resource release/inventory conservation, bounded-buffer wait/wakeup and close behavior, event validation/processing, snapshot round-trip and corrupt/unsupported input, JDBC schema and repository operations, mission transactions, unavailable-database resilience, and safe engine shutdown. The script fails the process on any failed assertion.

## Scope limits

BasinWatch is a local educational planning simulator. It does not ingest real gauges, issue public alerts, calculate hydrologically validated forecasts, control infrastructure, or replace emergency procedures. Scenario parameters are illustrative and deliberately visible as simulation settings.
