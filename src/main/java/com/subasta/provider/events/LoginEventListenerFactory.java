package com.subasta.provider.events;

import com.subasta.provider.storage.CustomUserStorageFactory;
import com.subasta.repository.DatabaseManager;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.logging.Logger;

public class LoginEventListenerFactory implements EventListenerProviderFactory {

    private static final Logger logger = Logger.getLogger(LoginEventListenerFactory.class.getName());

    public static final String PROVIDER_ID = "login-block-event-listener";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        DatabaseManager dbManager = CustomUserStorageFactory.getDatabaseManager();
        if (dbManager == null) {
            dbManager = CustomUserStorageFactory.tryInitializeFromSession(session);
        }
        if (dbManager == null) {
            logger.warning("[EVENT-LISTENER] DatabaseManager not available, creating listener without DB access");
            return new LoginEventListener(null);
        }
        logger.info("[EVENT-LISTENER] Creating LoginEventListenerProvider");
        return new LoginEventListener(dbManager);
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
        //
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        //
    }

    @Override
    public void close() {
        //
    }
}
