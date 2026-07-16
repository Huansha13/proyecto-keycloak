package com.subasta.repository;

import com.subasta.model.UserData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserRepository {

    private static final Logger logger = Logger.getLogger(UserRepository.class.getName());

    private final DatabaseManager databaseManager;

    public UserRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isUserBlocked(String username) {
        //language=TSQL
        String sql = """
                SELECT BLOQUEADO_POR_INTENTOS
                FROM ESEGURIDAD.SGTM_USUARIO
                WHERE UPPER(LOGIN) = UPPER(?)
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("BLOQUEADO_POR_INTENTOS") == 1;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, e, () -> "Error checking block status for: " + username);
        }
        return false;
    }

    public void blockUser(String username) {
        //language=TSQL
        String sql = """
                UPDATE ESEGURIDAD.SGTM_USUARIO
                SET HABILITADO = 0,
                    BLOQUEADO_POR_INTENTOS = 1,
                    MODIFICADOPOR = 'KEYCLOAK',
                    FECHAMODIFICACION = GETDATE()
                WHERE UPPER(LOGIN) = UPPER(?)
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
            logger.log(Level.INFO, () -> "[USER-REPO] User blocked: " + username);
        } catch (SQLException e) {
            logger.log(Level.WARNING, e, () -> "Error blocking user: " + username);
        }
    }

    public void unblockUser(String username) {
        //language=TSQL
        String sql = """
                UPDATE ESEGURIDAD.SGTM_USUARIO
                SET BLOQUEADO_POR_INTENTOS = 0,
                    HABILITADO = 1
                WHERE UPPER(LOGIN) = UPPER(?)
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
            logger.log(Level.INFO, () -> "[USER-REPO] User unblocked: " + username);
        } catch (SQLException e) {
            logger.log(Level.WARNING, e, () -> "Error unblocking user: " + username);
        }
    }

    public String getPasswordHash(String username) {
        //language=TSQL
        String sql = """
                SELECT PASSWORD
                FROM ESEGURIDAD.SGTM_USUARIO
                WHERE UPPER(LOGIN) = UPPER(?)
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PASSWORD");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error fetching password for user: " + username);
        }
        return null;
    }

    public boolean updatePassword(String username, String encodedPassword) {
        //language=TSQL
        String sql = """
                UPDATE ESEGURIDAD.SGTM_USUARIO
                SET PASSWORD = ?,
                    FECHAULTIMOCAMBIOPASSWORD = ?,
                    MODIFICADOPOR = 'KEYCLOAK',
                    FECHAMODIFICACION = ?
                WHERE UPPER(LOGIN) = UPPER(?)
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, encodedPassword);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(4, username);
            stmt.executeUpdate();
            logger.log(Level.INFO, () -> "[USER-REPO] Password updated for: " + username);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error updating password for: " + username);
            return false;
        }
    }

    public UserData findUserById(long id) {
        //language=TSQL
        String sql = """
                SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO
                FROM ESEGURIDAD.SGTM_USUARIO
                WHERE ID = ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapUserData(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error fetching user by ID: " + id);
        }
        return null;
    }

    public UserData findUserByUsername(String username) {
        //language=TSQL
        String sql = """
                SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO
                FROM ESEGURIDAD.SGTM_USUARIO
                WHERE UPPER(LOGIN) = UPPER(?)
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapUserData(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error fetching user by username: " + username);
        }
        return null;
    }

    public UserData findUserByEmail(String email) {
        //language=TSQL
        String sql = """
                SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO
                FROM ESEGURIDAD.SGTM_USUARIO
                WHERE UPPER(LOGIN) = UPPER(?)
                   OR UPPER(EMAIL) = UPPER(?)
                   OR UPPER(EMAILALTERNATIVO) = UPPER(?)
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, email);
            stmt.setString(3, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapUserData(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error fetching user by email: " + email);
        }
        return null;
    }

    public List<String> fetchUserRoles(long userId) {
        List<String> roles = new ArrayList<>();
        //language=TSQL
        String sql = """
                SELECT DISTINCT R.NOMBRECORTO
                FROM ESEGURIDAD.SGTR_USUARIO_PERFILES UP
                INNER JOIN ESEGURIDAD.SGTM_PERFIL P ON P.ID = UP.ID_PERFIL AND P.HABILITADO = 1
                INNER JOIN ESEGURIDAD.SGTM_ROL_EMPRESARIAL R ON R.ID = P.ROLEMPRESARIAL AND R.HABILITADO = 1
                WHERE UP.ID_USUARIO = ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String nombreCorto = rs.getString("NOMBRECORTO");
                    if (nombreCorto != null && !nombreCorto.trim().isEmpty()) {
                        roles.add(nombreCorto.toUpperCase().trim());
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error fetching roles for user ID: " + userId);
        }
        return roles;
    }

    public List<UserData> searchUsers(String searchTerm) {
        List<UserData> users = new ArrayList<>();
        //language=TSQL
        String sql = """
                SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO
                FROM ESEGURIDAD.SGTM_USUARIO
                WHERE LOGIN LIKE ? OR NOMBRES LIKE ?
                """;
        String param = searchTerm.trim().equals("*") ? "%" : "%" + searchTerm + "%";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, param);
            stmt.setString(2, param);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapUserData(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error searching users: " + searchTerm);
        }
        return users;
    }

    public List<UserData> searchByEmailAndUsername(String email, String username) {
        List<UserData> users = new ArrayList<>();
        //language=TSQL
        String sql = """
                SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO
                FROM ESEGURIDAD.SGTM_USUARIO
                WHERE 1=1
                """;
        List<Object> queryParams = new ArrayList<>();

        if (email != null && !email.trim().isEmpty()) {
            sql += " AND (EMAILALTERNATIVO LIKE ? OR LOGIN LIKE ?)";
            queryParams.add("%" + email + "%");
            queryParams.add("%" + email + "%");
        }
        if (username != null && !username.trim().isEmpty()) {
            sql += " AND LOGIN LIKE ?";
            queryParams.add("%" + username + "%");
        }

        if (queryParams.isEmpty()) {
            return users;
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < queryParams.size(); i++) {
                stmt.setObject(i + 1, queryParams.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapUserData(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error searching users by email and username");
        }
        return users;
    }

    public int countUsers() {
        //language=TSQL
        String sql = """
                SELECT COUNT(*)
                FROM ESEGURIDAD.SGTM_USUARIO
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error counting total users");
        }
        return 0;
    }

    public int countUsersBySearch(String searchTerm) {
        //language=TSQL
        String sql = """
                SELECT COUNT(*)
                FROM ESEGURIDAD.SGTM_USUARIO
                WHERE LOGIN LIKE ? OR NOMBRES LIKE ?
                """;
        String param = searchTerm.trim().equals("*") ? "%" : "%" + searchTerm + "%";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, param);
            stmt.setString(2, param);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e, () -> "Error counting users with search: " + searchTerm);
        }
        return 0;
    }

    private UserData mapUserData(ResultSet rs) throws SQLException {
        return new UserData(
                rs.getLong("ID"),
                rs.getString("NOMBRES"),
                rs.getString("APPATERNO"),
                rs.getString("APMATERNO"),
                rs.getString("LOGIN"),
                rs.getString("EMAIL"),
                rs.getString("EMAILALTERNATIVO"),
                rs.getInt("HABILITADO")
        );
    }
}
