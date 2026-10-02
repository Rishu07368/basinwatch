# BasinWatch — Java concept map

## Real application purpose

BasinWatch is an offline floodplain operations console. It models incoming rain and water-level observations, evaluates risk in a small basin, and lets an operator coordinate limited pumps, levee crews, and evacuation teams while independent operations are in progress.

The map below is drawn from the 14 topics in the supplied [interactive notes](../OOP_Unit_2_Interactive_Notes.html). It distinguishes core runtime mechanisms from optional diagnostic/learning material; concepts are not given artificial responsibilities merely to tick a syllabus box.

| Java concept | Where it appears in the application | Why the application actually needs it |
|---|---|---|
| Java I/O streams; input/output direction | Sensor journal writer, operational text log, report export, and session store | The console must retain an audit trail, recover a session, and export field-readable reports after the live simulation closes. |
| Byte streams: `InputStream` / `OutputStream` | Buffered binary sensor archive and save-file transport | Numeric sensor measurements and versioned snapshots need a durable representation that is distinct from the human-readable incident log. |
| Character streams: `Reader` / `Writer` | Buffered UTF-8 event log, report, and configuration reader/writer | Operators and maintainers need readable, editable records and diagnostics. |
| `File` and file metadata | Creating the app data directories, checking logs and save slots, listing snapshots, and reporting file sizes | The local console must manage its own files and clearly report whether an archive or saved session exists and is accessible. |
| File streams | The concrete file endpoints underlying session snapshots and sensor archives | The data must survive process exit rather than exist only in memory. |
| Buffered streams | Buffered binary sensor archive and buffered character event log | Sensor events and frequent small log entries should be batched instead of causing a low-level file operation for each field/line. |
| Data streams | Typed binary records containing simulation tick, zone identifier, rainfall, water level, and event category | An archive must preserve values and their types in a repeatable record order for replay and diagnosis. |
| Filter/wrapper streams | `BufferedInputStream`/`BufferedOutputStream` and data wrappers around archive file streams | Each layer adds a real concern—buffering or typed record encoding—without coupling the domain to a file device. |
| Byte-array streams | In-memory encoding/decoding of a bounded event or snapshot payload before it is committed | The persistence boundary can validate a complete payload before replacing a prior save, avoiding partially written state. |
| Serialization/deserialization | Versioned `SessionSnapshot` DTOs written and restored from an operator-selected save slot | Reopening a session must recover zone levels, stock, active response work, simulation tick, and event history without replaying the entire lifetime of the application. |
| `transient` and runtime-only state | Locks, threads, UI references, and open stream handles are owned by live services, not serialized snapshot data | A restored session must reconstruct fresh runtime coordination primitives instead of reviving invalid or unsafe process resources. |
| Piped character streams | A `SensorFeed` thread writes encoded sensor observations to a connected `PipedWriter`; an intake task reads them through `PipedReader`/`BufferedReader` | A live producer can publish observations independently while intake waits for bytes/characters to arrive, keeping the producer and downstream analysis loosely coupled. |
| Process and thread distinction | The Swing process hosts an EDT plus the simulation, intake, and response worker threads | Work progresses within one application process; background work must not freeze the operator’s interface. |
| `Thread` subclass | `SensorFeed`, which owns and stops the simulation’s timed sensor-emission lifecycle | The feed has a distinct lifecycle and blocking pipe endpoint to own; a dedicated named thread makes start, interruption, state reporting, and shutdown explicit. |
| `Runnable` and task/execution separation | Sensor intake and response workers | Parsing and response work are tasks that can be scheduled by worker threads without making domain jobs inherit from `Thread`. |
| `start()` versus `run()` | Engine startup and worker lifecycle | `start()` is required for independent execution; calling a task’s `run()` on the Swing event thread would block the controls. |
| Thread lifecycle and core methods (`interrupt`, `join`, `getState`, `sleep`) | Pause/stop/shutdown, timed feed cadence, graceful save/exit, and Engineering Inspector | The console must stop without abandoning file writes, and diagnostics need truthful worker state. Delays are interruptible; the UI never polls state as a coordination mechanism. |
| Shared process memory | Basin aggregate, mission registry, event buffer, and resource inventory | Sensor processing and response work must affect the same current operational picture. |
| Synchronization, monitors, synchronized blocks/methods | A short synchronized inventory reservation/release transaction and condition-based bounded event buffer | Simultaneous dispatches must not reserve the same pump or crew, and consumers must not observe half-applied resource changes. |
| Inter-thread communication: `wait()` / `notifyAll()` | Response workers wait while the event buffer is empty; producers signal after enqueue/close | Workers should sleep rather than spin while there is no incoming work; `notifyAll()` lets eligible waiting consumers re-check the shared condition after state changes. |
| Producer/consumer coordination | Sensor feed → piped intake → bounded event buffer → response workers | The producers can continue independently of analysis throughput; the bounded buffer applies back-pressure instead of silently growing without limit. |
| Thread priority | Optional Engineering Inspector experiment for the lower-priority archive/report worker | It demonstrates a genuine best-effort scheduling knob for non-urgent work, while operational ordering remains explicit and does not depend on JVM scheduling policy. |
| Deadlock and prevention | The resource allocator uses one atomic inventory monitor, a stable resource ordering, and no blocking I/O while holding the lock; diagnostics show reservation activity | Operations can compete for multiple kinds of equipment. A consistent acquisition rule prevents circular hold-and-wait; the normal user workflow does not intentionally deadlock. |
| Exception handling | Parse/validation boundary, event worker, persistence, report export, and UI action boundary | Malformed telemetry, unavailable files, corrupt saves, and unavailable resources need distinct, visible outcomes without killing unrelated simulation work. |
| Encapsulation, composition, polymorphism, inheritance | Domain aggregates compose zones, stations, crews, and missions; typed `BasinEvent` variants are processed polymorphically; narrow inheritance is reserved for a common event contract and Swing components | The model groups state that must change together, makes event-specific behavior explicit, and keeps the display from directly mutating inventory internals. |
| Enums, constructors, access modifiers, `static final` constants, overloading/overriding | Risk/severity, resource/event/status values, validated model constructors, immutable configuration, and UI renderer/handler overrides | The domain has a fixed vocabulary and invariants; typed values and controlled mutation prevent invalid states. Overloading is used only for meaningful input variants; overriding adapts real Swing/event contracts. |

## Important exclusions and guarantees

- The normal dispatch path does not depend on Java thread priority.
- No `Thread.stop`, `suspend`, or `resume` calls are used.
- A thread’s `RUNNABLE` state is not misrepresented as a separate Java `RUNNING` enum.
- `wait()` occurs only while owning its monitor and in a condition-check loop; `sleep()` is never used as a lock or signal.
- Session deserialization is restricted to the application’s snapshot classes and bounded input sizes; save files are treated as local application data, not as trusted arbitrary Java objects.
- Piped streams carry application sensor records, not a decorative threading example.
