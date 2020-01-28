package application.events.store.cloudant;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloudant.client.api.CloudantClient;

import application.events.EventService;
import application.events.store.CloudEventStore;
import application.events.store.CloudEventStoreFactory;
import io.cloudevents.CloudEvent;

/**
 * Cloudant-backed implementation of the event service interface.
 */
@Component
public class EventServiceCloudantImpl implements EventService {

    private final Map<String, String> env = System.getenv();

    private final CloudEventStore eventStore;
    private final CloudantClient cloudantClient;

    public EventServiceCloudantImpl(CloudEventStoreFactory cesFactory, CloudantClient cloudantClient) {
        this.cloudantClient = cloudantClient;
        this.eventStore = cesFactory.getDefault();
    }

    @Override
    public String getStatus() {
        String response = STATUS_UP;
        return response;
    }

    @Override
    public List<CloudEvent<?, ?>> getEvents() throws Exception {
        return this.eventStore.getEvents();
    }

    @Override
    public void addEvent(CloudEvent<?, ?> event) throws Exception {
        this.eventStore.addEvent(event);
    }

    @Override
    public void removeAllEvents() throws Exception {
        this.eventStore.removeAllEvents();
    }

    @Override
    public long getNumEvents() throws Exception {
        return this.eventStore.getNumEvents();
    }

    @Override
    public String getEnvironment() throws Exception {
        String response;
        if (this.cloudantClient != null) {
            response = "Available databases: " + this.cloudantClient.getAllDbs().toString().replaceAll("[^a-z0-9_$()+_/]", ""); // Make sure we dont get garbage names see: https://cloud.ibm.com/docs/services/Cloudant?topic=cloudant-getting-started-with-cloudant
        } else {
            response = "No Cloudant connection available";
        }
        response += "\nEnvironment:\n" + this.env.toString();
        return response;
    }

}
