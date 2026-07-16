package com.subasta.auth;

import com.subasta.provider.CustomUserStorageProviderFactory;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.OAuth2ErrorRepresentation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccountBlockCheckAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(AccountBlockCheckAuthenticator.class.getName());

    private static final String ERROR_DESCRIPTION_BLOCKED = "Su cuenta ha sido bloqueada por intentos fallidos";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String username = context.getHttpRequest().getDecodedFormParameters().getFirst("username");

        if (username == null || username.trim().isEmpty()) {
            context.success();
            return;
        }

        String[] config = CustomUserStorageProviderFactory.getDbConfig();

        if (config == null || config.length < 3) {
            logger.warning("[ACCOUNT-BLOCK-CHECK] DB config no disponible, saltando verificacion");
            context.success();
            return;
        }

        try (Connection conn = DriverManager.getConnection(config[0], config[1], config[2])) {
            // language=TSQL
            String sql = """
            SELECT BLOQUEADO_POR_INTENTOS FROM ESEGURIDAD.SGTM_USUARIO WHERE UPPER(LOGIN) = UPPER(?)
            """;

            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt("BLOQUEADO_POR_INTENTOS") == 1) {
                logger.warning("[ACCOUNT-BLOCK-CHECK] Cuenta bloqueada detectada para: " + username);

                OAuth2ErrorRepresentation errorRep = new OAuth2ErrorRepresentation("invalid_grant", ERROR_DESCRIPTION_BLOCKED);
                Response challengeResponse = Response.status(Response.Status.BAD_REQUEST)
                        .entity(errorRep)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build();
                context.forceChallenge(challengeResponse);
                return;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[ACCOUNT-BLOCK-CHECK] Error verificando bloqueo para: " + username, e);
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
