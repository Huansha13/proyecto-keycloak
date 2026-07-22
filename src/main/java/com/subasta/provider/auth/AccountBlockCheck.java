package com.subasta.provider.auth;

import com.subasta.provider.storage.CustomUserStorageFactory;
import com.subasta.repository.DatabaseManager;
import com.subasta.repository.UserRepository;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AccountBlockCheck implements Authenticator {

    private static final Logger logger = Logger.getLogger(AccountBlockCheck.class.getName());

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
            String path = context.getUriInfo().getPath();
            boolean isLoginFlow = path.contains("/protocol/openid-connect/auth");
            if (isLoginFlow) {
                logger.log(Level.WARNING, () -> "[ACCOUNT-BLOCK-CHECK] Blocked account detected for: " + username);
                context.challenge(context.form().setError("userDisabledMessage").createLoginUsernamePassword());
                return;
            }
            logger.log(Level.INFO, () -> "[ACCOUNT-BLOCK-CHECK] Blocked user in non-login flow, skipping: " + username);
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
    }

    @Override
    public void close() {
    }
}
