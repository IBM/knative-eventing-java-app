package application.events;

import java.util.List;

import io.cloudevents.CloudEvent;

/**
 * Defines the interface for working with Cloud Events.
 */
public interface EventService {
    
    String STATUS_UP = "{\"status\": \"UP\"}";

    String getStatus();
    
    List<CloudEvent<?, ?>> getEvents() throws Exception;
    
    void addEvent(CloudEvent<?, ?> event) throws Exception;
    
    void removeAllEvents() throws Exception;

    long getNumEvents() throws Exception;
    
    String getEnvironment() throws Exception;

}
