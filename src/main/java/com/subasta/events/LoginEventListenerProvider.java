package com.subasta.events;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.Errors;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginEventListenerProvider implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(LoginEventListenerProvider.class.getName());

    private String jdbcUrl;
    private String dbUser;
    private String dbPassword;

    public LoginEventListenerProvider(String jdbcUrl, String dbUser, String dbPassword) {
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
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
            logger.warning("[EVENT-LISTENER] LOGIN_ERROR sin username, error=" + error);
            return;
        }

        logger.warning("[EVENT-LISTENER] Keycloak bloqueó cuenta: " + username + " (error=" + error + ")");

        if (jdbcUrl == null) {
            String[] config = com.subasta.provider.CustomUserStorageProviderFactory.getDbConfig();
            if (config != null && config.length >= 3) {
                jdbcUrl = config[0];
                dbUser = config[1];
                dbPassword = config[2];
                logger.info("[EVENT-LISTENER] JDBC config obtiene de UserStorageProviderFactory");
            } else {
                logger.warning("[EVENT-LISTENER] JDBC config no disponible, no se puede actualizar BD");
                return;
            }
        }

        try (Connection conn = getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE ESEGURIDAD.SGTM_USUARIO " +
                            "SET HABILITADO = 0, BLOQUEADO_POR_INTENTOS = 1, " +
                            "MODIFICADOPOR = 'KEYCLOAK', FECHAMODIFICACION = GETDATE() " +
                            "WHERE UPPER(LOGIN) = UPPER(?)"
            );
            stmt.setString(1, username);
            int rows = stmt.executeUpdate();
            logger.info("[EVENT-LISTENER] BD actualizada para " + username + ": " + rows + " row(s)");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[EVENT-LISTENER] Error actualizando BD para " + username, e);
        }
    }

    @Override
    public void onEvent(org.keycloak.events.admin.AdminEvent event, boolean includeRepresentation) {
    }

    @Override
    public void close() {
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }
}
