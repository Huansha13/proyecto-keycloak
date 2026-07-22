package com.subasta.provider.events;

import com.subasta.repository.AuditRepository;
import com.subasta.repository.DatabaseManager;
import com.subasta.repository.UserRepository;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.Errors;

import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginEventListener implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(LoginEventListener.class.getName());

    private static final String HOSTNAME = "KEYCLOAK";
    private static final int MAX_TEMPORARY_LOCKOUTS = 3;

    private final DatabaseManager databaseManager;
    private final UserRepository userRepository;

    public LoginEventListener(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.userRepository = new UserRepository(databaseManager);
    }

    @Override
    public void onEvent(Event event) {
        if (databaseManager == null) {
            return;
        }

        switch (event.getType()) {
            case LOGIN -> handleLoginSuccess(event);
            case LOGIN_ERROR -> handleLoginError(event);
            case LOGOUT -> handleLogout(event);
            default -> {
                //
            }
        }
    }

    private void handleLoginSuccess(Event event) {
        String username = getUsername(event);
        String sessionId = event.getSessionId();
        String ip = event.getIpAddress();

        if (username == null || sessionId == null) {
            return;
        }

        logger.log(Level.INFO, () -> "[EVENT-LISTENER] Login exitoso: " + username + " session=" + sessionId);

        userRepository.resetFailedAttempts(username);

        try (Connection conn = databaseManager.getConnection()) {
            new AuditRepository(conn).saveLoginAttempt(sessionId, username, ip, HOSTNAME, true, null);
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "[EVENT-LISTENER] Error auditando login exitoso: " + username);
        }
    }

    private void handleLoginError(Event event) {
        String username = getUsername(event);
        String sessionId = event.getSessionId();
        String ip = event.getIpAddress();
        String error = event.getError();

        if (username == null) {
            return;
        }

        final String logUsername = username;
        final String logError = error;
        logger.log(Level.WARNING, () -> "[EVENT-LISTENER] Login fallido: " + logUsername + " error=" + logError);

        if (Errors.USER_TEMPORARILY_DISABLED.equals(error)
                || Errors.INVALID_USER_CREDENTIALS.equals(error)
                || Errors.USER_NOT_FOUND.equals(error)) {
            int attempts = userRepository.incrementFailedAttempts(username);
            logger.log(Level.WARNING, () -> "[EVENT-LISTENER] Intento fallido #" + attempts + " para: " + logUsername);
            if (attempts > MAX_TEMPORARY_LOCKOUTS) {
                logger.log(Level.WARNING, () -> "[EVENT-LISTENER] Maximo de intentos alcanzado, bloqueando permanentemente: " + logUsername);
                userRepository.blockUser(username);
            }
        }

        try (Connection conn = databaseManager.getConnection()) {
            String sessionUuid = sessionId != null ? sessionId : java.util.UUID.randomUUID().toString();
            new AuditRepository(conn).saveLoginAttempt(sessionUuid, username, ip, HOSTNAME, false, error);
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "[EVENT-LISTENER] Error auditando login fallido: " + username);
        }
    }

    private void handleLogout(Event event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) {
            return;
        }

        String username = getUsername(event);
        final String logUsername = username != null ? username : "unknown";
        final String logSessionId = sessionId;
        logger.log(Level.INFO, () -> "[EVENT-LISTENER] Logout: " + logUsername + " session=" + logSessionId);

        try (Connection conn = databaseManager.getConnection()) {
            new AuditRepository(conn).closeSession(sessionId, "LOGOUT");
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "[EVENT-LISTENER] Error cerrando sesion: " + logSessionId);
        }
    }

    private String getUsername(Event event) {
        if (event.getDetails() != null) {
            return event.getDetails().get("username");
        }
        return null;
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
