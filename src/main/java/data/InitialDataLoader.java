package data;

import domains.DockingBay;
import domains.MaintenanceTeam;
import domains.Transporter;
import enums.BayStatus;
import enums.MaintenanceStatus;
import enums.TransportState;
import server.TransportRegistry;
import service.CargoServiceImpl;
import service.EventLogService;
import service.SpaceportService;
import protocol.EventMessage;
import protocol.MessageCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InitialDataLoader {
    private static final List<String> PERISTEDFILES = List.of("docking.csv", "maintenance.csv", "transports.csv");
    public static final String EVENTLOG = "events.log";

    public static SpaceportService load() throws IOException {
        return load(null);
    }

    public static SpaceportService load(Path savedDir) throws IOException {
        long savedFiles = savedDir == null ? 0 : PERISTEDFILES.stream().filter(name -> Files.exists(savedDir.resolve(name))).count();
        if (savedFiles != 0 && savedFiles != PERISTEDFILES.size()) {
            throw new IOException("saved data in " + savedDir + " is incomplete");
        }
        Path source = savedFiles == 0 ? null : savedDir;
        System.out.println(source == null ? "Loading supplied sample data" : "Loading saved data from " + source);
        return build(source);
    }

    private static SpaceportService build(Path source) throws IOException {
        try {
            List<String[]> bayRows = readRows(source, "docking.csv", 3);
            List<String[]> teamRows = readRows(source, "maintenance.csv", 3);
            Map<String, DockingBay> bays = new LinkedHashMap<>();
            for (String[] row : bayRows) {
                checkId(row[0]);
                DockingBay bay = new DockingBay(row[0], BayStatus.valueOf(row[1]), null);
                if (bays.putIfAbsent(row[0], bay) != null) {
                    throw new IllegalArgumentException("duplicate bay: " + row[0]);
                }
            }
            Map<String, MaintenanceTeam> teams = new LinkedHashMap<>();
            for (String[] row : teamRows) {
                checkId(row[0]);
                MaintenanceTeam team = new MaintenanceTeam(row[0], MaintenanceStatus.valueOf(row[1]), null);
                if (teams.putIfAbsent(row[0], team) != null) {
                    throw new IllegalArgumentException("duplicate team: " + row[0]);
                }
            }
            TransportRegistry transports = new TransportRegistry();
            for (String[] row : readRows(source, "transports.csv", 7)) {
                checkId(row[0]);
                TransportState state = TransportState.valueOf(row[1]);
                DockingBay bay = row[2].isEmpty() ? null : bays.get(row[2]);
                MaintenanceTeam team = row[3].isEmpty() ? null : teams.get(row[3]);
                int capacity = Integer.parseInt(row[4]);
                int quantity = Integer.parseInt(row[5]);
                if (capacity < 0 || quantity < 0 || quantity > capacity) {
                    throw new IllegalArgumentException("invalid cargo for " + row[0]);
                }
                if ((!row[2].isEmpty() && bay == null) || (!row[3].isEmpty() && team == null)) {
                    throw new IllegalArgumentException("unknown bay or team for " + row[0]);
                }
                boolean needsBay = state == TransportState.DOCKED || state == TransportState.SERVICING
                        || state == TransportState.READY;
                if (needsBay != (bay != null) || (state == TransportState.SERVICING) != (team != null)) {
                    throw new IllegalArgumentException("allocations do not match the state of " + row[0]);
                }
                if (bay != null && (bay.getStatus() != BayStatus.OCCUPIED || bay.getOccupant() != null)) {
                    throw new IllegalArgumentException("invalid bay allocation for " + row[0]);
                }
                if (team != null && (team.getMaintenanceStatus() != MaintenanceStatus.BUSY || team.getTransporter() != null)) {
                    throw new IllegalArgumentException("invalid team allocation for " + row[0]);
                }
                Transporter transport = new Transporter(row[0], state, bay, capacity, quantity,
                        LocalDateTime.parse(row[6]), team);
                transports.register(transport);
                // both sides use the same objects before any client threads start
                if (bay != null) bay.setOccupant(transport);
                if (team != null) team.setTransporter(transport);
            }
            for (String[] row : bayRows) {
                DockingBay bay = bays.get(row[0]);
                String occupant = bay.getOccupant() == null ? "" : bay.getOccupant().getTransporterId();
                if (!row[2].equals(occupant) || (bay.getStatus() == BayStatus.OCCUPIED) != !occupant.isEmpty()) {
                    throw new IllegalArgumentException("bay allocation does not match transports.csv: " + row[0]);
                }
            }
            for (String[] row : teamRows) {
                MaintenanceTeam team = teams.get(row[0]);
                String assigned = team.getTransporter() == null ? "" : team.getTransporter().getTransporterId();
                if (!row[2].equals(assigned) || (team.getMaintenanceStatus() == MaintenanceStatus.BUSY) != !assigned.isEmpty()) {
                    throw new IllegalArgumentException("team allocation does not match transports.csv: " + row[0]);
                }
            }
            return new SpaceportService(transports, new CargoServiceImpl(), new EventLogService(readEvents(source)),
                    List.copyOf(bays.values()), List.copyOf(teams.values()));
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new IOException("invalid initial data: " + e.getMessage(), e);
        }
    }

    private static List<EventMessage> readEvents(Path source) throws IOException {
        if (source == null || !Files.exists(source.resolve(EVENTLOG))) {
            return List.of();
        }
        List<EventMessage> events = new ArrayList<>();
        for (String line : Files.readAllLines(source.resolve(EVENTLOG), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            if (!(MessageCodec.decode(line) instanceof EventMessage event)) {
                throw new IllegalArgumentException("events.log contains a message that is not an event");
            }
            events.add(event);
        }
        return events;
    }

    private static BufferedReader open(Path source, String filename) throws IOException {
        if (source != null) {
            return Files.newBufferedReader(source.resolve(filename), StandardCharsets.UTF_8);
        }
        InputStream input = InitialDataLoader.class.getResourceAsStream("/data/" + filename);
        if (input == null) {
            throw new IOException("initial data file was not found: " + filename);
        }
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private static List<String[]> readRows(Path source, String filename, int columns) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = open(source, filename)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                // the supplied files have no header; keep empty allocation fields
                String[] row = line.split(",", -1);
                if (row.length != columns) {
                    throw new IOException("expected " + columns + " fields in " + filename);
                }
                for (int i = 0; i < row.length; i++) row[i] = row[i].trim();
                rows.add(row);
            }
        }
        return rows;
    }

    private static void checkId(String id) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("ids must not be blank");
        }
    }
}
