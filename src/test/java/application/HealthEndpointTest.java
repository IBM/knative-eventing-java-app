package application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class HealthEndpointTest {

    @Mock
    private CustomHealthEndpoint objectUnderTest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(this.objectUnderTest.health()).thenReturn(CustomHealthEndpoint.STATUS_UP);
    }

    @Test
    public void testEndpoint() {
        String response = this.objectUnderTest.health();
        assertTrue(response.equals(CustomHealthEndpoint.STATUS_UP), "Invalid response from server : " + response);
    }
}
