package ma.atlasbank.platform.jpa.audit;

import ma.atlasbank.platform.security.CurrentActor;
import org.hibernate.envers.RevisionListener;

/**
 * Stamps every Envers revision with the authenticated user (or {@code system}).
 */
public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((AuditRevision) revisionEntity).assignActor(CurrentActor.name());
    }
}
