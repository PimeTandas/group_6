package client;

import protocol.Request;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CommandParser {
    public static Request parse(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("enter a command");
        }
        String[] parts = line.trim().split("\\s+");
        Map<String, String> arguments = new HashMap<>();
        // named arguments give the resource services room to add their own commands later
        for (int i = 1; i < parts.length; i++) {
            String[] argument = parts[i].split("=", 2);
            if (argument.length != 2 || argument[0].isBlank() || argument[1].isBlank()) {
                throw new IllegalArgumentException("arguments should use name=value");
            }
            if (arguments.putIfAbsent(argument[0], argument[1]) != null) {
                throw new IllegalArgumentException("duplicate argument: " + argument[0]);
            }
        }
        return new Request(UUID.randomUUID().toString(), parts[0].toUpperCase(Locale.ROOT), arguments);
    }
}
