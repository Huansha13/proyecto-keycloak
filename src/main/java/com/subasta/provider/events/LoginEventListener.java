package com.subasta.provider.events;

import com.subasta.repository.DatabaseManager;
import com.subasta.repository.UserRepository;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.Errors;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginEventListener implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(LoginEventListener.class.getName());

    private final UserRepository userRepository;

    public LoginEventListener(DatabaseManager databaseManager) {
        this.userRepository = new UserRepository(databaseManager);
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() != EventType.LOGIN_ERROR) {
            return;
        }

        String error = event.getError();
        if (!Errors.USER_DISABLED.equals(error) && !Errors.USER_TEMPORARILY_DISABLED.equals(error)) {
            return;
        }

        String username = null;
        if (event.getDetails() != null) {
            username = event.getDetails().get("username");
        }
        if (username == null || username.isEmpty()) {
            final String logError = error;
            logger.log(Level.WARNING, () -> "[EVENT-LISTENER] LOGIN_ERROR sin username, error=" + logError);
            return;
        }

        final String logUsername = username;
        final String logError = error;
        logger.log(Level.WARNING, () -> "[EVENT-LISTENER] Keycloak bloqueo cuenta: " + logUsername + " (error=" + logError + ")");
        userRepository.blockUser(username);
    }

    @Override
    public void onEvent(org.keycloak.events.admin.AdminEvent event, boolean includeRepresentation) {
        //
    }

    @Override
    public void close() {
        //
    }
}
