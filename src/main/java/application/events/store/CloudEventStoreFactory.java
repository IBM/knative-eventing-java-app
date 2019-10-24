package application.events.store;

import application.events.store.cloudant.CloudEventStoreCloudant;

import org.springframework.stereotype.Component;

@Component
public class CloudEventStoreFactory {

    private final CloudEventStoreCloudant cloudantStore;

    public CloudEventStoreFactory(CloudEventStoreCloudant cloudantStore) {
        this.cloudantStore = cloudantStore;
    }

    public CloudEventStore getDefault() {
        // additional methods/configuration can be implemented to support more database backends
        return this.cloudantStore;
    }

}
