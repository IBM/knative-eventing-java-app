package application.events.store.cloudant;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

@SuppressWarnings("unchecked")
public class DatabaseUtils {

    public static final String KEY_IMAGE_NAME = "K_SERVICE";
    
    /**
     * <b>Important:</b> The database with this name will be used by unit tests which will delete all events. The deployed 
     * application will use a database with the name defined by the {@value #KEY_IMAGE_NAME} environment variable, which 
     * maps to the Knative serving metadata.name property.
     */
    public static final String DEFAULT_DB_NAME = "knative-eventing-db";
    
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final Map<String, String> env = System.getenv();
    public static final String ENV_CLOUDANT_STR = env.get("service_cloudant");
    public static Map<String, String> cloudantSvcProps;

    static {
        try {
            if (ENV_CLOUDANT_STR != null) {
                cloudantSvcProps = mapper.readValue(ENV_CLOUDANT_STR, Map.class);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage() == null ? "Setting cloudant service properties to null." : e.getMessage());
            cloudantSvcProps = null;
        }
    }

    public static final String ENV_IMG_NAME = env.get(DatabaseUtils.KEY_IMAGE_NAME);

    private static String dbName;

    /**
     * Returns the name of the cloud event store database if defined in the environment.
     */
    public static final String getDatabaseName() {
        if (dbName == null || dbName.trim().isEmpty()) {
            synchronized (DatabaseUtils.class) {
                if (ENV_IMG_NAME == null || ENV_IMG_NAME.trim().isEmpty()) {
                    System.out.println("No image name specified for database name. Using default: " + DatabaseUtils.DEFAULT_DB_NAME);
                    dbName = DatabaseUtils.DEFAULT_DB_NAME;
                } else {
                    dbName = ENV_IMG_NAME;
                }
            }
        }
        return dbName;
    }

}
