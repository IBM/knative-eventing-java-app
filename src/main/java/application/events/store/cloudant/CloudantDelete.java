package application.events.store.cloudant;

import com.cloudant.client.api.model.Document;

/**
 * Used for the deletion of Cloudant documents in bulk.
 */
public class CloudantDelete extends Document {

    public CloudantDelete(String id, String rev) {
        this.setId(id);
        this.setRevision(rev);
        this.setDeleted(true);
    }
}