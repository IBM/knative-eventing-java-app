package application.events;

import org.springframework.stereotype.Component;

/**
 * Provides a factory for instantiating event service instances.
 */
@Component
public class EventServiceFactory {

    private final EventService eventStore;

    public EventServiceFactory(EventService cloudantStore) {
        this.eventStore = cloudantStore;
    }
    
    public EventService getDefault() {
        return this.eventStore;
    }

}
