package application.events.store;

import java.util.List;

import io.cloudevents.CloudEvent;

/**
 * An interface for simple storage of cloud events.
 */
public interface CloudEventStore {

    public static final int DEFAULT_MAX_EVENTS = 100;

    List<CloudEvent<?, ?>> getEvents() throws Exception;

    long getNumEvents() throws Exception;

    void addEvent(CloudEvent<?, ?> event) throws Exception;

    void removeAllEvents() throws Exception;

    void ping();

    void shutdown();

}
