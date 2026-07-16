package com.subasta.provider.auth;

import com.subasta.provider.storage.CustomUserStorageFactory;
import com.subasta.repository.DatabaseManager;
import com.subasta.repository.UserRepository;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.OAuth2ErrorRepresentation;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AccountBlockCheck implements Authenticator {

    private static final Logger logger = Logger.getLogger(AccountBlockCheck.class.getName());

    private static final String ERROR_DESCRIPTION_BLOCKED = "Su cuenta ha sido bloqueada por intentos fallidos";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String username = context.getHttpRequest().getDecodedFormParameters().getFirst("username");

        if (username == null || username.trim().isEmpty()) {
            context.success();
            return;
        }

        DatabaseManager dbManager = CustomUserStorageFactory.getDatabaseManager();
        if (dbManager == null) {
            dbManager = CustomUserStorageFactory.tryInitializeFromSession(context.getSession());
        }
        if (dbManager == null) {
            logger.log(Level.WARNING, () -> "[ACCOUNT-BLOCK-CHECK] DatabaseManager not available, skipping verification");
            context.success();
            return;
        }

        UserRepository userRepository = new UserRepository(dbManager);
        if (userRepository.isUserBlocked(username)) {
            logger.log(Level.WARNING, () -> "[ACCOUNT-BLOCK-CHECK] Blocked account detected for: " + username);
            OAuth2ErrorRepresentation errorRep = new OAuth2ErrorRepresentation("invalid_grant", ERROR_DESCRIPTION_BLOCKED);
            Response challengeResponse = Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorRep)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
            context.forceChallenge(challengeResponse);
            return;
        }

        context.success();
    }

    @Override
    public void action(AuthenticationFlowContext context) {
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        //
    }

    @Override
    public void close() {
        //
    }
}
