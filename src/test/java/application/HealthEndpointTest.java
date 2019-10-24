package application;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class HealthEndpointTest {

    @Mock private CustomHealthEndpoint objectUnderTest;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Mockito.when(this.objectUnderTest.health()).thenReturn(CustomHealthEndpoint.STATUS_UP);
    }

    @Test
    public void testEndpoint() throws Exception {
        String response = this.objectUnderTest.health();
        assertTrue("Invalid response from server : " + response, response.equals(CustomHealthEndpoint.STATUS_UP));
    }

}
