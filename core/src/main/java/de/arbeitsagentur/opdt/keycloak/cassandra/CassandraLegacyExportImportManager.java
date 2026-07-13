package de.arbeitsagentur.opdt.keycloak.cassandra;

import static org.keycloak.models.utils.DefaultRequiredActions.getDefaultRequiredActionCaseInsensitively;
import static org.keycloak.models.utils.RepresentationToModel.*;
import static org.keycloak.models.utils.RepresentationToModel.createGroups;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.keycloak.models.*;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.representations.idm.UserConsentRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.storage.DatastoreProvider;
import org.keycloak.storage.datastore.DefaultExportImportManager;

public class CassandraLegacyExportImportManager extends DefaultExportImportManager {
    private KeycloakSession session;

    public CassandraLegacyExportImportManager(KeycloakSession session) {
        super(session);
        this.session = session;
    }

    // Fix UserStoragePrivateUtil.userLocalStorage(session) to not require LegacyDatastoreProvider
    @Override
    public UserModel createUser(RealmModel newRealm, UserRepresentation userRep) {
        convertDeprecatedSocialProviders(userRep);

        // Import users just to user storage. Don't federate
        UserModel user = session.getProvider(DatastoreProvider.class)
                .users()
                .addUser(newRealm, userRep.getId(), userRep.getUsername(), false, false);
        user.setEnabled(userRep.isEnabled() != null && userRep.isEnabled());
        user.setCreatedTimestamp(userRep.getCreatedTimestamp());
        user.setEmail(userRep.getEmail());
        if (userRep.isEmailVerified() != null) user.setEmailVerified(userRep.isEmailVerified());
        user.setFirstName(userRep.getFirstName());
        user.setLastName(userRep.getLastName());
        user.setFederationLink(userRep.getFederationLink());
        if (userRep.getAttributes() != null) {
            for (Map.Entry<String, List<String>> entry : userRep.getAttributes().entrySet()) {
                List<String> value = entry.getValue();
                if (value != null) {
                    user.setAttribute(entry.getKey(), new ArrayList<>(value));
                }
            }
        }
        if (userRep.getRequiredActions() != null) {
            for (String requiredAction : userRep.getRequiredActions()) {
                user.addRequiredAction(getDefaultRequiredActionCaseInsensitively(requiredAction));
            }
        }
        createCredentials(userRep, session, newRealm, user, false);
        createFederatedIdentities(userRep, session, newRealm, user);
        createRoleMappings(userRep, user, newRealm);
        if (userRep.getClientConsents() != null) {
            for (UserConsentRepresentation consentRep : userRep.getClientConsents()) {
                UserConsentModel consentModel = RepresentationToModel.toModel(newRealm, consentRep, session);
                session.users().addConsent(newRealm, user.getId(), consentModel);
            }
        }

        if (userRep.getNotBefore() != null) {
            session.users().setNotBeforeForUser(newRealm, user, userRep.getNotBefore());
        }

        if (userRep.getServiceAccountClientId() != null) {
            String clientId = userRep.getServiceAccountClientId();
            ClientModel client = newRealm.getClientByClientId(clientId);
            if (client == null) {
                throw new RuntimeException(
                        "Unable to find client specified for service account link. Client: " + clientId);
            }
            user.setServiceAccountClientLink(client.getId());
        }
        createGroups(session, userRep, newRealm, user);
        return user;
    }
}
