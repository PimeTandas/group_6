package service;

import protocol.EventMessage;

import java.util.ArrayList;
import java.util.List;

public class EventLogService {
    private final List<EventMessage> events = new ArrayList<>();

    public EventLogService() {
    }

    public synchronized void record(EventMessage event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        events.add(event);
    }

    public EventLogService(List<EventMessage> persistedEvents) {
        persistedEvents.forEach(this::record);
    }

    public synchronized List<EventMessage> getEvents() {
        // callers can read a stable copy while another handler appends an event
        return List.copyOf(events);
    }
}
