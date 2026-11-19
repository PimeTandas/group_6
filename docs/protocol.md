# client communication

## connection and messages

the socket uses utf-8. a client first sends its name on one line and its role
(`CLIENT` or `ADMIN`) on the next. names are trimmed, case-sensitive and must
be unique among connected clients. an incomplete handshake times out after
10 seconds of inactivity on a read.

the server sends a response with an empty request id for the handshake.
on success, later messages are json envelopes, with exactly one envelope per
line. gson escapes newlines inside strings. the `type` identifies a `request`,
`response` or `event`; `payload` contains the matching record.

example request:

```json
{"type":"request","payload":{"requestId":"r1","command":"PING","arguments":{}}}
```

example response:

```json
{"type":"response","payload":{"requestId":"r1","success":true,"message":"pong","data":[]}}
```

example notification:

```json
{"type":"event","payload":{"timestamp":"2026-11-19T12:00:00Z","eventType":"CLIENT_CONNECTED","details":{"client":"terminal-2"}}}
```

the cli generates a unique request id for each command. responses copy that
id, so events can arrive between replies without being mistaken for a reply.
malformed messages receive an error with an empty request id because the
server cannot rely on their contents. the connection stays open.

## commands and permissions

`PING` and `QUIT` are available to both roles. `LIST_CLIENTS` is checked by the
server and only available to admins. these commands take no arguments.
unknown commands and unexpected arguments return a failure response.
`HELP` is handled locally by the cli.

the parser accepts `command name=value` arguments ready for future commands.
values cannot contain whitespace in this first version. parsing a command
does not mean the server implements it.

the original plain `QUIT` line is still accepted for older terminals and tests.
new clients send a structured quit request and wait briefly for the reply.

roles are chosen at connection time, matching the existing project setup.
this checks permissions for an established role; it does not authenticate
administrators. authentication would need a separate agreed design.

## threads and shared state

the client main thread reads keyboard commands. after registration, only
`ServerMessageListener` reads the socket. it decodes responses and events
and displays them without requiring another command from the operator.

each server handler shares the same `ClientRegistry`. registration uses
`putIfAbsent`, so two handlers cannot claim one name. removal checks both
the name and handler, so a rejected duplicate cannot remove the original.

responses and broadcasts use the handler's same send lock. this protects a
whole message line from concurrent writes. the welcome is sent under that
lock during registration so another broadcast cannot overtake it.
there is no global command lock.

## connecting the other services

add command cases to `CommandDispatcher`, validate arguments and call the
shared service instances. inject those services from `Server`; do not create
separate transport/resource stores for each connection.

return the original request id in each response. resource services should
validate business rules, update state and record their event exactly once.
after a successful change, publish an `EventMessage` through
`ClientRegistry.broadcast`. the scheduler can use that same method.
avoid holding transport or resource locks while doing socket writes.

`Response.data` currently contains a list of strings for client names.
agree structured resource response types before adding resource commands.

## current limits

broadcast writes are synchronous. a terminal that stops reading can delay
the thread broadcasting to it; an outgoing queue per client is a possible
next step. message size limits and request rate limits are not implemented.
this is a working project communication layer, not an internet-facing server.

notifications print on a new line and can interrupt the visual input prompt.
after a server disconnect the listener reports it immediately, but a main
thread already waiting on console input may need enter before it exits.

the existing server shutdown still uses process exit. coordinated service
shutdown and persistence remain part of the server-services work.
