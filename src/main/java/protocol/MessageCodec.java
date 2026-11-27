package protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MessageCodec {
    private static final Gson gson = new Gson();

    public static String encode(Message message) {
        String type;
        if (message instanceof Request) {
            type = "request";
        } else if (message instanceof Response) {
            type = "response";
        } else if (message instanceof EventMessage) {
            type = "event";
        } else {
            throw new IllegalArgumentException("unsupported message type");
        }
        JsonObject envelope = new JsonObject();
        envelope.addProperty("type", type);
        envelope.add("payload", gson.toJsonTree(message));
        // one json object per line, so embedded newlines cannot split a message
        return gson.toJson(envelope);
    }

    public static Message decode(String line) {
        try {
            JsonObject envelope = JsonParser.parseString(line).getAsJsonObject();
            JsonObject payload = envelope.getAsJsonObject("payload");
            if (payload == null) {
                throw new IllegalArgumentException("missing payload");
            }
            return switch (envelope.get("type").getAsString()) {
                case "request" -> gson.fromJson(payload, Request.class);
                case "response" -> gson.fromJson(payload, Response.class);
                case "event" -> gson.fromJson(payload, EventMessage.class);
                default -> throw new IllegalArgumentException("unknown message type");
            };
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid protocol message", e);
        }
    }
}
