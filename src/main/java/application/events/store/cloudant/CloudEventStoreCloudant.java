/**
 *
 */
package application.events.store.cloudant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.cloudant.v1.model.AllDocsResult;
import com.ibm.cloud.cloudant.v1.model.DeleteDocumentOptions;
import com.ibm.cloud.cloudant.v1.model.DocsResultRow;
import com.ibm.cloud.cloudant.v1.model.PostAllDocsOptions;
import com.ibm.cloud.cloudant.v1.model.PostDocumentOptions;
import com.ibm.cloud.sdk.core.service.exception.NotFoundException;
import com.ibm.cloud.cloudant.v1.model.Document;
import com.ibm.cloud.cloudant.v1.model.DocumentResult;

import application.SBApplicationConfig;
import application.events.store.CloudEventStore;
import io.cloudevents.CloudEvent;
import io.cloudevents.v02.CloudEventImpl;

/**
 * Cloudant Spring implementation
 */
@Component
public class CloudEventStoreCloudant implements CloudEventStore {

    private static final Logger logger = LoggerFactory.getLogger(CloudEventStoreCloudant.class);

    private final Cloudant client;
    private final String dbName;
    private final Gson gson;

    public CloudEventStoreCloudant(Cloudant client, GsonBuilder gsonBuilder) {
        this.client = client;
        this.gson = SBApplicationConfig.getCustomGsonBuilder().create();
        this.dbName = DatabaseUtils.getDatabaseName();
    }

    @Override
    public List<CloudEvent<?, ?>> getEvents() {
        try {
            List<CloudEvent<?, ?>> events = new ArrayList<>();

            PostAllDocsOptions docsOptions = new PostAllDocsOptions.Builder().db(this.dbName).includeDocs(true).build();
            AllDocsResult allDocResults = this.client.postAllDocs(docsOptions).execute().getResult();

            for (DocsResultRow docResult : allDocResults.getRows()) {
                Document document = docResult.getDoc();

                @SuppressWarnings("rawtypes")
                CloudEventImpl evt = this.gson.fromJson(document.toString(), CloudEventImpl.class);

                events.add(evt);
            }

            return events;
        } catch (NotFoundException e) {
            logger.warn("Unable to retrieve all documents from Cloudant", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long getNumEvents() {
        try {
            PostAllDocsOptions docsOptions = new PostAllDocsOptions.Builder().db(this.dbName).build();
            AllDocsResult allDocResults = this.client.postAllDocs(docsOptions).execute().getResult();

            return allDocResults.getTotalRows();
        } catch (Exception e) {
            logger.warn("Unable to retrieve all documents from Cloudant", e);
            return -1;
        }
    }

    @Override
    public void addEvent(CloudEvent<?, ?> event) throws Exception {
        // Convert event into document object
        Document document = new Document();
        document.setProperties(this.gson.fromJson(this.gson.toJson(event), Map.class)); // https://github.com/cloudant/java-cloudant/blob/master/MIGRATION.md

        // Post document and get response
        PostDocumentOptions postDocumentOptions = new PostDocumentOptions.Builder().db(this.dbName).document(document)
                .build();
        DocumentResult response = this.client.postDocument(postDocumentOptions).execute().getResult();

        // Check for errors
        String error = response.getError();
        if (error != null) {
            logger.error("Error adding event to Cloudant: " + error);
            throw new Exception(error);
        }
    }

    @Override
    public void removeAllEvents() throws Exception {
        try {
            PostAllDocsOptions docsOptions = new PostAllDocsOptions.Builder().db(this.dbName).includeDocs(true).build();
            AllDocsResult allDocResults = this.client.postAllDocs(docsOptions).execute().getResult();

            for (DocsResultRow docResult : allDocResults.getRows()) {
                Document document = docResult.getDoc();

                DeleteDocumentOptions deleteDocumentOptions = new DeleteDocumentOptions.Builder().db(this.dbName)
                        .docId(document.getId()).rev(document.getRev()).build();

                DocumentResult deleteDocumentResponse = client.deleteDocument(deleteDocumentOptions).execute()
                        .getResult();

                if (deleteDocumentResponse.isOk()) {
                    logger.info("You have deleted the document.");
                }
            }
        } catch (NotFoundException e) {
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
