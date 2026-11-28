package persistance;

import data.InitialDataLoader;
import protocol.MessageCodec;
import service.SpaceportService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PersistanceService {
    private final Path dir;

    public PersistanceService(Path dir) {
        this.dir = dir;
    }

    public Path getDir() {
        return dir;
    }

    public synchronized void save(SpaceportService spaceport) throws IOException {
        Map<String, List<String>> files = new LinkedHashMap<>();

        files.put("transports.csv", spaceport.getTransports().stream().map(t -> String.join(",",
                t.id(), t.state().name(), blank(t.bayId()), blank(t.teamId()),
                Integer.toString(t.capacity()), Integer.toString(t.quantity()), t.departure().toString())).toList());

        files.put("docking.csv", spaceport.getBays().stream().map(b -> String.join(",",
                b.id(), b.status().name(), blank(b.transportId()))).toList());

        files.put("maintenance.csv", spaceport.getTeams().stream().map(m -> String.join(",",
                m.id(), m.status().name(), blank(m.transportId()))).toList());

        files.put(InitialDataLoader.EVENTLOG, spaceport.getEvents().stream().map(MessageCodec::encode).toList());

        Files.createDirectories(dir);
        for (Map.Entry<String, List<String>> file : files.entrySet()) {
            Files.write(dir.resolve(file.getKey() + ".tmp"), file.getValue(), StandardCharsets.UTF_8);
        }
        for (String name : files.keySet()) {
            Files.move(dir.resolve(name + ".tmp"), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String blank(String id) {
        return id == null ? "" : id;
    }
}