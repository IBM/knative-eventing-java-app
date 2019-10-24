package application;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.Database;
import com.cloudant.client.api.views.AllDocsRequest;
import com.cloudant.client.api.views.AllDocsRequestBuilder;
import com.cloudant.client.api.views.AllDocsResponse;

import application.events.EventController;
import application.events.EventService;
import application.events.EventServiceFactory;
import application.events.store.CloudEventStoreFactory;
import io.cloudevents.extensions.DistributedTracingExtension;
import io.cloudevents.extensions.ExtensionFormat;
import io.cloudevents.format.Wire;
import io.cloudevents.v02.CloudEventBuilder;
import io.cloudevents.v02.CloudEventImpl;
import io.cloudevents.v02.http.Marshallers;

public class EventControllerEndpointTest {

    @Mock private CloudEventStoreFactory cesFactory;
    @Mock private CloudantClient cloudantClient;
    @Mock private Database cloudantDatabase;
    
    @Mock private EventService eventService;
    @Mock private EventServiceFactory eventServiceFactory;// = new EventServiceFactory(this.eventService);

    private EventController objectUnderTest;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Mockito.when(this.eventServiceFactory.getDefault()).thenReturn(this.eventService);
        this.objectUnderTest = new EventController(this.eventServiceFactory);
    }

    @Test
    public void testApiEndpoint() throws Exception {
        Mockito.when(this.eventService.getStatus()).thenReturn(EventService.STATUS_UP);
        ResponseEntity<String> response = this.objectUnderTest.landing();
        validateServerResponse(response, HttpStatus.OK);
        assertTrue("Invalid response from server : " + response, response.getBody().equalsIgnoreCase(EventService.STATUS_UP));
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

    public ResponseEntity<String> testEmptyEvents(boolean deleteAll) throws Exception {
        @SuppressWarnings("rawtypes")
        List<CloudEventImpl> docs = Collections.emptyList();
        this.mockGetEvents(docs);
        ResponseEntity<String> response = objectUnderTest.events(deleteAll);
        return response;
    }

    public void mockGetEvents(@SuppressWarnings("rawtypes") List<CloudEventImpl> docs) throws Exception {
        long expectedNumEvents = docs.size();
        AllDocsRequestBuilder builderMock = Mockito.mock(AllDocsRequestBuilder.class);
        AllDocsRequest reqMock = Mockito.mock(AllDocsRequest.class);
        AllDocsResponse dbResponseMock = Mockito.mock(AllDocsResponse.class);
        
        Mockito.when(this.eventService.getNumEvents()).thenReturn(expectedNumEvents);
        Mockito.when(this.cloudantDatabase.getAllDocsRequestBuilder()).thenReturn(builderMock);
        Mockito.when(builderMock.includeDocs(true)).thenReturn(builderMock);
        Mockito.when(builderMock.build()).thenReturn(reqMock);
        Mockito.when(reqMock.getResponse()).thenReturn(dbResponseMock);
        Mockito.when(dbResponseMock.getDocsAs(CloudEventImpl.class)).thenReturn(docs);
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
        assertEquals(ce, captorVal);
        
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
        assertEquals(ce, captorVal);

        ResponseEntity<String> response = this.testEmptyEvents();
        System.out.println("testEventsDeleteAllEndpoint response: " + response);
        validateServerResponse(response, HttpStatus.OK);

        response = this.testEmptyEvents(true);
        System.out.println("testEventsDeleteAllEndpoint response: " + response);
        validateServerResponse(response, HttpStatus.OK);
        assertTrue("Invalid response from server : " + response, response.getBody().contains("All cloud events deleted"));
        
        response = this.objectUnderTest.events(false);
        assertTrue("Invalid response from server : " + response, response.getBody().startsWith("No events found in the database"));
    }
    
    public static void assertEquals(CloudEventImpl<?> expectedCe, CloudEventImpl<?> actualCe) {
        Assert.assertEquals("Unexpected data", expectedCe.getData().get(), actualCe.getData().get());
        Assert.assertEquals("Unexpected data", expectedCe.getAttributes().getContenttype().get(), 
                    actualCe.getAttributes().getContenttype().get());
        
        Assert.assertEquals("Unexpected id", expectedCe.getAttributes().getId(), 
                actualCe.getAttributes().getId());
        
        Assert.assertEquals("Unexpected content type presence", expectedCe.getAttributes().getContenttype().isPresent(), 
                actualCe.getAttributes().getContenttype().isPresent());
        if (expectedCe.getAttributes().getContenttype().isPresent()) {
            Assert.assertEquals("Unexpected content type", expectedCe.getAttributes().getContenttype().get(), 
                    actualCe.getAttributes().getContenttype().get());
        }
        
        Assert.assertEquals("Unexpected media type", expectedCe.getAttributes().getMediaType().isPresent(), 
                actualCe.getAttributes().getMediaType().isPresent());
        if (expectedCe.getAttributes().getMediaType().isPresent()) {
            Assert.assertEquals("Unexpected media type", expectedCe.getAttributes().getMediaType().get(), 
                    actualCe.getAttributes().getMediaType().get());
        }
        
        Assert.assertEquals("Unexpected schema URL presence", expectedCe.getAttributes().getSchemaurl().isPresent(), 
                actualCe.getAttributes().getSchemaurl().isPresent());
        if (expectedCe.getAttributes().getSchemaurl().isPresent()) {
            Assert.assertEquals("Unexpected schema URL", expectedCe.getAttributes().getSchemaurl().get(), 
                    actualCe.getAttributes().getSchemaurl().get());
        }
        
        Assert.assertEquals("Unexpected source", expectedCe.getAttributes().getSource(), 
                actualCe.getAttributes().getSource());
        
        Assert.assertEquals("Unexpected spec version", expectedCe.getAttributes().getSpecversion(), 
                actualCe.getAttributes().getSpecversion());
        
        Assert.assertEquals("Unexpected time presence", expectedCe.getAttributes().getTime().isPresent(), 
                actualCe.getAttributes().getTime().isPresent());
        if (expectedCe.getAttributes().getTime().isPresent()) {
            ZonedDateTime expectedTime = expectedCe.getAttributes().getTime().get();
            ZonedDateTime actualTime = actualCe.getAttributes().getTime().get();
            Assert.assertEquals("Unexpected time", expectedTime.toEpochSecond(), actualTime.toEpochSecond());
        }
        
        Assert.assertEquals("Unexpected type", expectedCe.getAttributes().getType(), 
                actualCe.getAttributes().getType());
    }
    
    public static String getPayload(CloudEventImpl<Map<?, ?>> ce) {
        return getWire(ce).getPayload().get();
    }

    public static Map<String, Object> getHeadersMap(CloudEventImpl<Map<?, ?>> ce) {
        /* Marshal the event as a Wire instance and grab the headers and body*/
        Wire<String, String, String> wire = getWire(ce);
        Map<String, String> headerVals = wire.getHeaders();
        Map<String, Object> returnVal = new HashMap<>();
        returnVal.putAll(headerVals);
        return returnVal;
    }

    public static <T> Wire<String, String, String> getWire(CloudEventImpl<Map<?, ?>> ce) {
        Wire<String, String, String> wire = Marshallers.<Map<?,?>>binary()
                .withEvent(() -> ce)
                .marshal();
        return wire;
    }

    public static HttpEntity<String> createHttpEntityFromEvent(CloudEventImpl<Map<?, ?>> ce) {
        /* Marshal the event as a Wire instance and grab the headers and body*/
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
        
        /*Create a tracing extension*/
        final DistributedTracingExtension dt = new DistributedTracingExtension();
        dt.setTraceparent("0");
        dt.setTracestate("congo=4");
        /*Format it as extension format*/
        final ExtensionFormat tracing = new DistributedTracingExtension.Format(dt);
        /* Build a CloudEvent instance */
        CloudEventImpl<Map<?,?>> ce =
            CloudEventBuilder.<Map<?,?>>builder()
                .withType("knative.eventing.test")
                .withSource(URI.create("https://github.com/cloudevents/spec/pull"))
                .withId("A234-1234-1234")                   
                .withTime(ZonedDateTime.now())
                .withContenttype(MediaType.APPLICATION_JSON_VALUE)
                .withData(testValue)
                .withExtension(tracing)
                .build();
        return ce;
    }

    public static void validateServerResponse(ResponseEntity<String> response, HttpStatus expectedCode) {
        Assert.assertEquals("Unexpected response code", expectedCode, response.getStatusCode());
        String body = response.getBody();
        assertTrue("Empty response from server", !body.isEmpty());
        assertFalse("Error response from server: " + response, body.contains("ERROR:"));
        assertFalse("Warning response from server: " + response, body.contains("WARNING:"));
    }

    public static void validateNonzeroEvents(ResponseEntity<String> response) {
        assertTrue("Unexpected events response from server: " + response, response.getBody().startsWith("NUMBER OF EVENTS"));
    }

    public static void validateEntityPost(ResponseEntity<Void> result) {
        Assert.assertEquals("Unexpected response code", HttpStatus.ACCEPTED, result.getStatusCode());
    }

}
