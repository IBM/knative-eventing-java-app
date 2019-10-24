package application;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/*
 * We use a custom health endpoint, and disable the one provided by Spring Boot Actuator, in order to be able to unit test 
 * the endpoint. Otherwise we would need unit tests to be able to connect to Cloudant to be able to test it.
 */
/**
 * A basic health endpoint providing a simple status.
 */
@Component
@Endpoint(id = "health")
public class CustomHealthEndpoint {

    public static final String STATUS_UP = "{\"status\":\"UP\"}";

    @ReadOperation
    public String health() {
        return STATUS_UP;
    }

}
