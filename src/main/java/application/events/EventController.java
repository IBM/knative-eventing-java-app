package application.events;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.cloudevents.CloudEvent;
import io.cloudevents.json.Json;
import io.cloudevents.v02.AttributesImpl;
import io.cloudevents.v02.http.Unmarshallers;

/*
 * Best practice is to use a version identifier in the request mapping, e.g. /v1. however, Knative Eventing does not yet
 * support routing to a custom URL path. When this is implemented, the request mapping URL can change.
 * https://github.com/knative/eventing/issues/1918
 */

/**
 * REST controller for events.
 */
@RestController
public class EventController {
    
    private static final Logger logger = LoggerFactory.getLogger(EventController.class);
    
    private final EventService eventService;
    private final EventServiceFactory eventServiceFactory;

    public EventController(EventServiceFactory eventServiceFactory) {
        this.eventServiceFactory = eventServiceFactory;
        this.eventService = this.eventServiceFactory.getDefault();
    }

    @GetMapping(value = "/v1", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> landing() {
        String response = this.eventService.getStatus();
        return ResponseEntity.ok(response);
    }

    /**
     * Receives an event and stores it into the default cloud event store. When Knative supports routing to a custom URL path, the value here should change to something like /event.
     *
     * @param headers The request headers.
     * @param body    The request body.
     * 
     * @return 202 if the event is successfully stored
     * @throws Exception 
     */
    @PostMapping(value = "/", consumes = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> event(@RequestHeader Map<String, Object> headers, @RequestBody String body) throws Exception {
        logger.debug("Receved request headers: " + headers);
        logger.debug("Received request body: " + body);
        try {
            Object contentTypeVal = headers.get("Content-Type");
            if (contentTypeVal == null || MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(contentTypeVal.toString())) {
                @SuppressWarnings("rawtypes")
                CloudEvent<AttributesImpl, Map> cloudEvent = Unmarshallers.binary(Map.class)
                        .withHeaders(() -> headers)
                        .withPayload(() -> body)
                        .unmarshal();
                logger.debug("Received CloudEvent: " + cloudEvent);
                this.eventService.addEvent(cloudEvent);
            }
            return ResponseEntity.accepted().build();
            
        } catch (Exception e) {
            String errMsg = "ERROR: Exception processing received event";
            logger.error(errMsg, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errMsg, e);
        }
    }

    /**
     * Returns up to MAX_NUM_EVENTS events in plain text.
     *
     * @param deleteAll If true, all the events in the cloud event store will be deleted.
     * 
     * @return A plain text rendering of up to MAX_NUM_EVENTS events, or a message indicating events have been deleted when
     * deleteAll is true.
     * @throws Exception 
     */
    @GetMapping(value = "/v1/events", produces = MediaType.TEXT_PLAIN_VALUE)
    public @ResponseBody
    ResponseEntity<String> events(@RequestParam(name = "deleteAll", defaultValue = "false") boolean deleteAll) throws Exception {
        StringBuilder sb = new StringBuilder();
        HttpStatus responseCode = HttpStatus.OK;
        try {
            if (deleteAll) {
                logger.info("Deleting all cloud events");
                this.eventService.removeAllEvents();
                sb.append("All cloud events deleted. Remove the deleteAll parameter and reload the page.");
                
            } else {
                long numEvents = this.eventService.getNumEvents();
                if (numEvents == 0) {
                    sb.append("No events found in the database!");
                } else if (numEvents < 0) {
                    sb.append("ERROR: Unable to retrieve number of events due to an unexpected error. See the application logs for details.");
                    responseCode = HttpStatus.INTERNAL_SERVER_ERROR;
                } else {
                    int maxEvents = 100;
                    sb.append("NUMBER OF EVENTS (MAX " + maxEvents + " DISPLAYED): " + numEvents).append("\n\n");
                    List<CloudEvent<?, ?>> events = this.eventService.getEvents();
                    int count = 0;
                    for (CloudEvent<?, ?> evt : events) {
                        String evtStr = Json.encode(evt);
                        sb.append(evtStr).append("\n\n");
                        count++;
                        if (count == maxEvents) {
                            break;
                        }
                    }
                }
            }

            String response = sb.toString();
            return ResponseEntity.status(responseCode).body(response);
            
        } catch (Exception e) {
            String errMsg = "ERROR: Exception while retrieving events: " + e.getMessage();
            logger.error(errMsg, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errMsg, e);
        }
    }

    @GetMapping(value = "/v1/env", produces = MediaType.TEXT_PLAIN_VALUE)
    public @ResponseBody
    ResponseEntity<String> cloudant() {
        try {
            String response = this.eventService.getEnvironment();
            return new ResponseEntity<String>(response, HttpStatus.OK);
            
        } catch (Exception e) {
            String errMsg = "ERROR: Exception while retrieving cloudant enviroment information: " + e.getMessage();
            logger.error(errMsg, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errMsg, e);
        }
    }

}
