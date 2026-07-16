package com.subasta.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuditRepository {

    private static final Logger logger = Logger.getLogger(AuditRepository.class.getName());

    private final Connection connection;

    public AuditRepository(Connection connection) {
        this.connection = connection;
    }

    public void saveLoginAttempt(String sessionUuid, String username, String ip, String hostname, boolean success, String failureReason) {

        //language=TSQL
        String sql = """
                INSERT INTO ESEGURIDAD.SGTM_AUDITORIA_SESIONES
                    (USUARIO, SESION_UUID, INICIO_SESION, DIRECCION_IP, HOSTNAME,
                     TIPO_INICIO, LOGIN_EXITOSO, MOTIVO_FALLO, CREADOPOR, FECHACREACION)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, sessionUuid);
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(4, ip);
            stmt.setString(5, hostname);
            stmt.setString(6, "ROPC");
            stmt.setBoolean(7, success);
            stmt.setString(8, failureReason);
            stmt.setString(9, username);
            stmt.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, e, () -> "Error guardando auditoria de login para: " + username);
        }
    }

    public void closeSession(String sessionUuid, String tipoCierre) {
        //language=TSQL
        String sql = """
                UPDATE ESEGURIDAD.SGTM_AUDITORIA_SESIONES
                SET FIN_SESION = ?, TIPO_CIERRE = ?
                WHERE SESION_UUID = ? AND FIN_SESION IS NULL
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, tipoCierre);
            stmt.setString(3, sessionUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, e, () -> "Error cerrando sesion: " + sessionUuid);
        }
    }
}
