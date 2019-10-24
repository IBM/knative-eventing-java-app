/**
 *
 */
package application.events.store.cloudant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.Database;
import com.cloudant.client.api.model.Document;
import com.cloudant.client.api.model.Response;
import com.cloudant.client.api.views.AllDocsResponse;
import com.cloudant.client.org.lightcouch.NoDocumentException;

import application.events.store.CloudEventStore;
import io.cloudevents.CloudEvent;
import io.cloudevents.v02.CloudEventImpl;

/**
 * Cloudant Spring implementation
 */
@Component
public class CloudEventStoreCloudant implements CloudEventStore {
    
    private static final Logger logger = LoggerFactory.getLogger(CloudEventStoreCloudant.class);

    @SuppressWarnings("unused")
    private final CloudantClient client;
    private final Database database;

    public CloudEventStoreCloudant(CloudantClient client, Database database) {
        this.client = client;
        this.database = database;
    }

    @Override
    public List<CloudEvent<?, ?>> getEvents() {
        AllDocsResponse allDocsResponse;
        try {
            List<CloudEvent<?, ?>> events = new ArrayList<>();
            allDocsResponse = this.database.getAllDocsRequestBuilder().includeDocs(true).build().getResponse();
            @SuppressWarnings("rawtypes")
            List<CloudEventImpl> docIds = allDocsResponse.getDocsAs(CloudEventImpl.class);
            for (int i = 0; i < docIds.size(); i++) {
                @SuppressWarnings("rawtypes")
                CloudEventImpl evt = docIds.get(i);
                try {
                    events.add(evt);
                } catch (NoDocumentException nde) {
                    logger.debug("Unable to find cloud event document", nde);
                }
            }

            return events;
        } catch (IOException e) {
            logger.warn("Unable to retrieve all documents from Cloudant", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long getNumEvents() {
        AllDocsResponse allDocsResponse;
        try {
            allDocsResponse = this.database.getAllDocsRequestBuilder().build().getResponse();
            List<String> docIds = allDocsResponse.getDocIds();
            return docIds.size();
        } catch (IOException e) {
            logger.warn("Unable to retrieve all documents from Cloudant", e);
            return -1;
        }
    }

    @Override
    public void addEvent(CloudEvent<?, ?> event) throws Exception {
        Response response = this.database.post(event);
        String error = response.getError();
        if (error != null) {
            logger.error("Error adding event to Cloudant: " + error);
            throw new Exception(error);
        }
    }

    @Override
    public void removeAllEvents() throws Exception {
        AllDocsResponse allDocsResponse;
        try {
            allDocsResponse = this.database.getAllDocsRequestBuilder().includeDocs(true).build().getResponse();
            List<Document> docs = allDocsResponse.getDocs();
            this.database.bulk(docs.stream()
                    .map(document -> new CloudantDelete(document.getId(), document.getRevision()))
                    .collect(Collectors.toList())
            );
        } catch (IOException e) {
            String errMsg = "Unable to retrieve all documents from Cloudant";
            logger.error(errMsg, e);
            throw new Exception(errMsg, e);
        }
    }

    @Override
    public void ping() {

    }

    @Override
    public void shutdown() {

    }

}
