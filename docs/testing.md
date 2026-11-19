# Testing

Run the automated tests from the project root with `mvn test`.
The project needs Java 17 or later and Maven. The tests can also be run
from `src/test/java` in IntelliJ.

## Current coverage

| Test | Expected result | Result |
| --- | --- | --- |
| Transport lifecycle | Each step from WAITING to DEPARTED succeeds | Passed |
| Skipped and backward transitions | Exception, with the original state unchanged | Passed (16 cases) |
| Repeated state | Exception, with the original state unchanged | Passed (5 cases) |
| Null next state | Exception, with the original state unchanged | Passed (5 cases) |
| Client and admin handshakes followed by QUIT | Correct name and role logged once on connection and once on disconnection | Passed (2 cases) |
| Client closes its output without QUIT | Handler finishes and records one disconnection | Passed |
| Eight concurrent client sessions | All register before any quits; each connection and disconnection recorded once | Passed |

Last run: 19 November 2026. Maven reported 31 tests, no failures or errors.
The run used JDK 23 with the project's Java 17 source and target settings.

## Socket test approach

The handler tests use real loopback sockets on an automatically assigned port.
They do not start the full Server or use port 1234. A thread-safe in-memory
recorder replaces ClientLogService, so these tests do not write to clients.log.

The concurrent test uses latches to release eight client threads together.
It waits for every connection event before allowing clients to send QUIT.
This checks overlapping sessions rather than a sequence of completed sessions.
Event order between different clients is allowed to vary. Futures propagate
handler failures, and waits have time limits to catch handlers that get stuck.
The tests close both ends of each socket and shut down their executors.

## Remaining coverage

These tests cover the existing transport transition rules and handler lifecycle.
They do not establish thread safety of the complete spaceport system.

- Cargo validation, simultaneous loads/unloads and transfers. CargoService is
  still on the separate transport-cargo branch.
- Bay allocation, exchanges and closing a bay during allocation.
- Maintenance team contention and completion.
- Departures racing with cargo and resource operations.
- Operational event logging, concurrent file logging and notifications.
- Persistence and restoration of resource relationships and event history.
- Malformed handshakes, socket cleanup by the handler, and server shutdown.
- Full client/server command tests and manual multi-terminal testing.

Several of these features are not implemented on this branch yet. No manual
test results are claimed here.
