# Group 6
## Port Wander – Spaceport Management System

A concurrent client-server Space Port management system written in Java. A central server manages and interfaces with the spaceport backend and multiple CLI terminals can connect under different personas at the same time to perform various workflows.

## Project Structure

```
src/main/java/
├── client/         Client terminal (connects to server via socket)
├── domains/        Transporter, DockingBay, MaintenanceTeam
├── enums/          States and types
├── persistance/    *Not built yet - loading and saving of data* 
├── repository/     *Not built yet - might not need*
├── server/         Server and client handler threads
├── service/        Shared services used by the server (backend)
└── util/           Constants (console colours, etc)


src/main/resources/data/
└── clients.log   Connection/disconnection log (created automatically)

ADD ADDITIONAL LOGS WE ARE KEEPING AS WE BUILD THEM INTO THE SYSTEM
```

## Running the Application
### 1. Start the Server

- **IntelliJ:** open `Server.java` and click the green run arrow next `top left`.

    This will start the Server on port <u>1234</u>. Type `QUIT` to quit.

### 2. Start one or more Clients
The server *should* be able to handle many clients so you can launch as many as these as you like.
- **IntelliJ:** open `Client.java` and click the green run arrow next `top left`.
    
    Once running you will be prompted for a client name, client type and port to connect to. Type `QUIT` to quit.

## Todo
- [x] Server setup
- [x] Client setup
- [x] Multi-client server with thread pool
- [x] Client connection logging
- [ ] Send notifications to connected clients on thread pool
- [ ] Flesh this out with more requirements from spec
- [ ] Setup client action options
- [ ] Work out client/ admin split
- [ ] Persistance layer
- [ ] Business Logic added as services?
- [ ] Server should be multithreaded - console + spin off tasks

