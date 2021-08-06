package application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.cloudant.v1.model.AllDocsResult;
import com.ibm.cloud.cloudant.v1.model.DocsResultRow;
import com.ibm.cloud.cloudant.v1.model.Document;
import com.ibm.cloud.sdk.core.http.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import application.events.EventController;
import application.events.EventService;
import application.events.EventServiceFactory;
import application.events.store.CloudEventStoreFactory;
import io.cloudevents.CloudEvent;
import io.cloudevents.extensions.DistributedTracingExtension;
import io.cloudevents.extensions.ExtensionFormat;
import io.cloudevents.format.Wire;
import io.cloudevents.v02.CloudEventBuilder;
import io.cloudevents.v02.CloudEventImpl;
import io.cloudevents.v02.http.Marshallers;

public class EventControllerEndpointTest {

    @Mock
    private CloudEventStoreFactory cesFactory;

    @Mock
    private Cloudant cloudant;

    @Mock
    private EventService eventService;

    @Mock
    private EventServiceFactory eventServiceFactory;

    private EventController objectUnderTest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(this.eventServiceFactory.getDefault()).thenReturn(this.eventService);
        this.objectUnderTest = new EventController(this.eventServiceFactory);
    }

    @Test
    public void testApiEndpoint() {
        Mockito.when(this.eventService.getStatus()).thenReturn(EventService.STATUS_UP);
        ResponseEntity<String> response = this.objectUnderTest.landing();
        validateServerResponse(response, HttpStatus.OK);
        assertTrue(response.getBody().equalsIgnoreCase(EventService.STATUS_UP),
                "Invalid response from server : " + response);
    }

    @Test
    public void testEventsEndpoint() throws Exception {
        ResponseEntity<String> response = this.testEmptyEvents();
        System.out.println("testEventsEndpoint response: " + response);
        validateServerResponse(response, HttpStatus.OK);
    }

    public ResponseEntity<String> testEmptyEvents() throws Exception {
        return this.testEmptyEvents(false);
    }

    public ResponseEntity<String> testEmptyEvents(final boolean deleteAll) throws Exception {
        @SuppressWarnings("rawtypes")
        List<CloudEventImpl> docs = Collections.emptyList();
        this.mockGetEvents(docs);
        ResponseEntity<String> response = objectUnderTest.events(deleteAll);
        return response;
    }

    public void mockGetEvents(@SuppressWarnings("rawtypes") final List<CloudEventImpl> docs) throws Exception {
        Gson customGson = SBApplicationConfig.getCustomGsonBuilder().create();

        List<DocsResultRow> docsResultRows = new ArrayList<>();

        for (CloudEvent<?, ?> cloudEvent : docs) {
            Document document = new Document();
            document.setProperties(customGson.fromJson(customGson.toJson(cloudEvent), Map.class));

            DocsResultRow docsResultRowMock = Mockito.mock(DocsResultRow.class);
            Mockito.when(docsResultRowMock.getDoc()).thenReturn(document);
            docsResultRows.add(docsResultRowMock);
        }

        long expectedNumEvents = docs.size();

        @SuppressWarnings("unchecked")
        Response<AllDocsResult> responseMock = Mockito.mock(Response.class);
        AllDocsResult allDocsResultMock = Mockito.mock(AllDocsResult.class);

        Mockito.when(this.eventService.getNumEvents()).thenReturn(expectedNumEvents);
        Mockito.when(responseMock.getResult()).thenReturn(allDocsResultMock);
        Mockito.when(allDocsResultMock.getRows()).thenReturn(docsResultRows);
    }

    @Test
    public void testEventEndpoint() throws Exception {
        CloudEventImpl<Map<?, ?>> ce = createTestCloudEvent();
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<CloudEventImpl> eventCaptor = ArgumentCaptor.forClass(CloudEventImpl.class);
        Mockito.doNothing().when(this.eventService).addEvent(eventCaptor.capture());

        ResponseEntity<Void> result = this.objectUnderTest.event(getHeadersMap(ce), getPayload(ce));
        validateEntityPost(result);
        Mockito.verify(this.eventService).addEvent(eventCaptor.capture());
        CloudEventImpl<?> captorVal = eventCaptor.getValue();
        assertCloudEventImplEquals(ce, captorVal);

        @SuppressWarnings("rawtypes")
        List<CloudEventImpl> docs = new ArrayList<>();
        docs.add(ce);
        this.mockGetEvents(docs);
        ResponseEntity<String> response = this.objectUnderTest.events(false);
        System.out.println("testEventEndpoint response: " + response);
        validateNonzeroEvents(response);
    }

    @Test
    public void testEventsDeleteAllEndpoint() throws Exception {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<CloudEventImpl> eventCaptor = ArgumentCaptor.forClass(CloudEventImpl.class);
        Mockito.doNothing().when(this.eventService).addEvent(eventCaptor.capture());

        CloudEventImpl<Map<?, ?>> ce = createTestCloudEvent();
        ResponseEntity<Void> result = this.objectUnderTest.event(getHeadersMap(ce), getPayload(ce));
        validateEntityPost(result);
        Mockito.verify(this.eventService).addEvent(eventCaptor.capture());
        CloudEventImpl<?> captorVal = eventCaptor.getValue();
        assertCloudEventImplEquals(ce, captorVal);

        ResponseEntity<String> response = this.testEmptyEvents();
        System.out.println("testEventsDeleteAllEndpoint response: " + response);
        validateServerResponse(response, HttpStatus.OK);

        response = this.testEmptyEvents(true);
        System.out.println("testEventsDeleteAllEndpoint response: " + response);
        validateServerResponse(response, HttpStatus.OK);
        assertTrue(response.getBody().contains("All cloud events deleted"),
                "Invalid response from server : " + response);

        response = this.objectUnderTest.events(false);
        assertTrue(response.getBody().startsWith("No events found in the database"),
                "Invalid response from server : " + response);
    }

    public static void assertCloudEventImplEquals(final CloudEventImpl<?> expectedCe,
            final CloudEventImpl<?> actualCe) {
        assertEquals(expectedCe.getData().get(), actualCe.getData().get(), "Unexpected data");
        assertEquals(expectedCe.getAttributes().getContenttype().get(), actualCe.getAttributes().getContenttype().get(),
                "Unexpected data");

        assertEquals(expectedCe.getAttributes().getId(), actualCe.getAttributes().getId(), "Unexpected id");

        assertEquals(expectedCe.getAttributes().getContenttype().isPresent(),
                actualCe.getAttributes().getContenttype().isPresent(), "Unexpected content type presence");
        if (expectedCe.getAttributes().getContenttype().isPresent()) {
            assertEquals(expectedCe.getAttributes().getContenttype().get(),
                    actualCe.getAttributes().getContenttype().get(), "Unexpected content type");
        }

        assertEquals(expectedCe.getAttributes().getMediaType().isPresent(),
                actualCe.getAttributes().getMediaType().isPresent(), "Unexpected media type");
        if (expectedCe.getAttributes().getMediaType().isPresent()) {
            assertEquals(expectedCe.getAttributes().getMediaType().get(), actualCe.getAttributes().getMediaType().get(),
                    "Unexpected media type");
        }

        assertEquals(expectedCe.getAttributes().getSchemaurl().isPresent(),
                actualCe.getAttributes().getSchemaurl().isPresent(), "Unexpected schema URL presence");
        if (expectedCe.getAttributes().getSchemaurl().isPresent()) {
            assertEquals(expectedCe.getAttributes().getSchemaurl().get(), actualCe.getAttributes().getSchemaurl().get(),
                    "Unexpected schema URL");
        }

        assertEquals(expectedCe.getAttributes().getSource(), actualCe.getAttributes().getSource(), "Unexpected source");

        assertEquals(expectedCe.getAttributes().getSpecversion(), actualCe.getAttributes().getSpecversion(),
                "Unexpected spec version");

        assertEquals(expectedCe.getAttributes().getTime().isPresent(), actualCe.getAttributes().getTime().isPresent(),
                "Unexpected time presence");
        if (expectedCe.getAttributes().getTime().isPresent()) {
            ZonedDateTime expectedTime = expectedCe.getAttributes().getTime().get();
            ZonedDateTime actualTime = actualCe.getAttributes().getTime().get();
            assertEquals(expectedTime.toEpochSecond(), actualTime.toEpochSecond(), "Unexpected time");
        }

        assertEquals(expectedCe.getAttributes().getType(), actualCe.getAttributes().getType(), "Unexpected type");
    }

    public static String getPayload(final CloudEventImpl<Map<?, ?>> ce) {
        return getWire(ce).getPayload().get();
    }

    public static Map<String, Object> getHeadersMap(final CloudEventImpl<Map<?, ?>> ce) {
        /* Marshal the event as a Wire instance and grab the headers and body */
        Wire<String, String, String> wire = getWire(ce);
        Map<String, String> headerVals = wire.getHeaders();
        Map<String, Object> returnVal = new HashMap<>();
        returnVal.putAll(headerVals);
        return returnVal;
    }

    public static <T> Wire<String, String, String> getWire(final CloudEventImpl<Map<?, ?>> ce) {
        Wire<String, String, String> wire = Marshallers.<Map<?, ?>>binary().withEvent(() -> ce).marshal();
        return wire;
    }

    public static HttpEntity<String> createHttpEntityFromEvent(final CloudEventImpl<Map<?, ?>> ce) {
        /* Marshal the event as a Wire instance and grab the headers and body */
        Wire<String, String, String> wire = getWire(ce);
        Map<String, String> headerVals = wire.getHeaders();
        HttpHeaders headers = new HttpHeaders();
        for (Entry<String, String> entry : headerVals.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            List<String> vals;
            if (headers.containsKey(key)) {
                vals = headers.get(key);
            } else {
                vals = new ArrayList<>();
                headers.put(key, vals);
            }
            vals.add(value);
        }

        System.out.println("Cloud Event headers: " + headers);
        System.out.println("Cloud Event body: " + wire.getPayload().get());
        HttpEntity<String> request = new HttpEntity<>(wire.getPayload().get(), headers);
        return request;
    }

    public static CloudEventImpl<Map<?, ?>> createTestCloudEvent() {
        Map<String, String> testValue = new HashMap<>();
        testValue.put("test", "value");

        /* Create a tracing extension */
        final DistributedTracingExtension dt = new DistributedTracingExtension();
        dt.setTraceparent("0");
        dt.setTracestate("congo=4");
        /* Format it as extension format */
        final ExtensionFormat tracing = new DistributedTracingExtension.Format(dt);
        /* Build a CloudEvent instance */
        CloudEventImpl<Map<?, ?>> ce = CloudEventBuilder.<Map<?, ?>>builder().withType("knative.eventing.test")
                .withSource(URI.create("https://github.com/cloudevents/spec/pull")).withId("A234-1234-1234")
                .withTime(ZonedDateTime.now()).withContenttype(MediaType.APPLICATION_JSON_VALUE).withData(testValue)
                .withExtension(tracing).build();
        return ce;
    }

    public static void validateServerResponse(final ResponseEntity<String> response, final HttpStatus expectedCode) {
        assertEquals(expectedCode, response.getStatusCode(), "Unexpected response code");
        String body = response.getBody();
        assertTrue(!body.isEmpty(), "Empty response from server");
        assertFalse(body.contains("ERROR:"), "Error response from server: " + response);
        assertFalse(body.contains("WARNING:"), "Warning response from server: " + response);
    }

    public static void validateNonzeroEvents(final ResponseEntity<String> response) {
        assertTrue(response.getBody().startsWith("NUMBER OF EVENTS"),
                "Unexpected events response from server: " + response);
    }

    public static void validateEntityPost(final ResponseEntity<Void> result) {
        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode(), "Unexpected response code");
    }
}
