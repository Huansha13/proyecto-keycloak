package com.subasta.events;

import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.logging.Logger;

import com.subasta.provider.CustomUserStorageProviderFactory;

public class LoginEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger logger = Logger.getLogger(LoginEventListenerProviderFactory.class.getName());

    public static final String PROVIDER_ID = "login-block-event-listener";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        String[] dbConfig = CustomUserStorageProviderFactory.getDbConfig();
        if (dbConfig == null || dbConfig.length < 3) {
            logger.warning("[EVENT-LISTENER] DB config not available yet from UserStorageProvider");
            return new LoginEventListenerProvider(null, null, null);
        }
        logger.info("Creating LoginEventListenerProvider");
        return new LoginEventListenerProvider(dbConfig[0], dbConfig[1], dbConfig[2]);
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
