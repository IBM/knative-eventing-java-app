package application;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ibm.cloud.cloudant.v1.Cloudant;

import com.ibm.cloud.sdk.core.security.BasicAuthenticator;
import com.ibm.cloud.sdk.core.service.exception.NotFoundException;
import com.ibm.cloud.sdk.core.security.Authenticator;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

/**
 * Spring Application configuration.
 */
@Configuration
public class SBApplicationConfig {

    /*
     * The Cloudant URL, username, and password are provided by IBM Cloud Spring
     * Bind as defined in the mappings.json file. It will search in environment
     * variables or localdev-config.json for the specified properties.
     */

    @Value("${cloudant_url}")
    private String cloudantUrl;

    @Value("${cloudant_username}")
    private String cloudantUsername;

    @Value("${cloudant_password}")
    private String cloudantPassword;

    /**
     * Enables Spring to automatically create <code>Cloudant</code> instances
     * configured to connect to the bound Cloudant client.
     * 
     * @return A fully-configured <code>Cloudant</code> instance.
     */
    @ConditionalOnMissingBean
    @Bean
    public Cloudant cloudant() {
        // Create the authenticator.
        Authenticator authenticator = new BasicAuthenticator.Builder().username(this.cloudantUsername)
                .password(this.cloudantPassword).build();

        // Create the client
        Cloudant client = new Cloudant("", authenticator);
        client.setServiceUrl(cloudantUrl);

        return client;
    }

    public static GsonBuilder getCustomGsonBuilder() {
        // workaround for known issue:
        // https://github.com/cloudant/java-cloudant/issues/357
        GsonBuilder gsonBuilder = new GsonBuilder().registerTypeAdapter(ZonedDateTime.class,
                new JsonDeserializer<ZonedDateTime>() {
                    @Override
                    public ZonedDateTime deserialize(JsonElement json, Type type,
                            JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
                        if (json.isJsonObject()) {
                            // example as string:
                            // {"dateTime":{"date":{"year":2019,"month":10,"day":9},"time":{"hour":21,"minute":23,"second":8,"nano":288000000}},"offset":{"totalSeconds":-18000},"zone":{"totalSeconds":-18000}}
                            JsonObject jsonObject = json.getAsJsonObject();
                            JsonObject dateTimeElem = jsonObject.get("dateTime").getAsJsonObject();
                            JsonObject dateElem = dateTimeElem.get("date").getAsJsonObject();
                            JsonObject timeElem = dateTimeElem.get("time").getAsJsonObject();
                            JsonObject offsetElem = jsonObject.get("offset").getAsJsonObject();

                            // example with expected format: 2007-12-03T10:15:30+01:00[Europe/Paris]
                            NumberFormat format = NumberFormat.getInstance();
                            format.setMinimumIntegerDigits(2);
                            format.setMaximumIntegerDigits(2);
                            StringBuilder sb = new StringBuilder();
                            sb.append(dateElem.get("year").getAsInt()).append("-")
                                    .append(format.format(dateElem.get("month").getAsInt())).append("-")
                                    .append(format.format(dateElem.get("day").getAsInt())).append("T")
                                    .append(format.format(timeElem.get("hour").getAsInt())).append(":")
                                    .append(format.format(timeElem.get("minute").getAsInt())).append(":")
                                    .append(format.format(timeElem.get("second").getAsInt()));
                            int offsetSecs = offsetElem.get("totalSeconds").getAsInt();
                            int hours = Math.abs(offsetSecs) / 3600;
                            if (offsetSecs < 0) {
                                sb.append("-");
                            } else {
                                sb.append("+");
                            }
                            sb.append(format.format(hours)).append(":").append("00");

                            String formatStr = sb.toString();
                            return ZonedDateTime.parse(formatStr);
                        }

                        if (json.isJsonArray()) {
                            // TODO not sure if this will ever execute
                            JsonArray jsonArray = json.getAsJsonArray();
                            String jsonStr = jsonArray.getAsString();
                            return ZonedDateTime.parse(jsonStr);
                        }
                        if (json.isJsonPrimitive()) {
                            // TODO not sure if this will ever execute
                            JsonPrimitive jsonPrimitive = json.getAsJsonPrimitive();
                            String jsonStr = jsonPrimitive.getAsString();
                            return ZonedDateTime.parse(jsonStr);
                        }
                        if (json.isJsonNull()) {
                            // TODO not sure if this will ever execute
                            return null;
                        }

                        return null;
                    }
                });

        return gsonBuilder;
    }
}
