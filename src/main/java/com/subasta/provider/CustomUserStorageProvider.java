package com.subasta.provider;

import com.subasta.mode.UserAdapter;
import com.subasta.repository.AuditRepository;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class CustomUserStorageProvider
        implements UserStorageProvider, UserLookupProvider, CredentialInputValidator,
        CredentialInputUpdater, UserQueryProvider {

    private static final Logger logger = Logger.getLogger(CustomUserStorageProvider.class.getName());

    private final KeycloakSession session;
    private final ComponentModel model;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    private Connection connection;

    public CustomUserStorageProvider(KeycloakSession session, ComponentModel model,
                                     String dbUrl, String dbUser, String dbPassword) {
        this.session = session;
        this.model = model;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        }
        return connection;
    }

    private String getClientIp() {
        try {
            if (session.getContext() != null && session.getContext().getConnection() != null) {
                return session.getContext().getConnection().getRemoteAddr();
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "No se pudo obtener IP del request", e);
        }
        return "unknown";
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO " +
                            "FROM ESEGURIDAD.SGTM_USUARIO WHERE ID = ?"
            );
            stmt.setLong(1, Long.parseLong(externalId));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapUser(realm, rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching user by ID: " + externalId, e);
        }
        return null;
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO " +
                            "FROM ESEGURIDAD.SGTM_USUARIO WHERE UPPER(LOGIN) = UPPER(?)"
            );
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapUser(realm, rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching user by username: " + username, e);
        }
        return null;
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        try {
            // FIX: Ahora busca el correo tanto en LOGIN como en EMAILALTERNATIVO
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO " +
                            "FROM ESEGURIDAD.SGTM_USUARIO WHERE UPPER(LOGIN) = UPPER(?) OR UPPER(EMAIL) = UPPER(?) OR UPPER(EMAILALTERNATIVO) = UPPER(?)"
            );
            stmt.setString(1, email);
            stmt.setString(2, email);
            stmt.setString(3, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapUser(realm, rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching user by email: " + email, e);
        }
        return null;
    }

    private UserModel mapUser(RealmModel realm, ResultSet rs) throws SQLException {
        long id = rs.getLong("ID");
        String nombres = rs.getString("NOMBRES");
        String apellidoPaterno = rs.getString("APPATERNO");
        String apellidoMaterno = rs.getString("APMATERNO");
        String login = rs.getString("LOGIN");
        String email = rs.getString("EMAIL");
        String emailAlternativo = rs.getString("EMAILALTERNATIVO");
        int habilitado = rs.getInt("HABILITADO");

        String storageId = StorageId.keycloakId(model, String.valueOf(id));
        UserAdapter adapter = new UserAdapter(session, realm, model, storageId, this);

        adapter.setUsername(login);
        adapter.setFirstName(nombres != null ? nombres : "");

        String lastName = (apellidoPaterno != null ? apellidoPaterno : "") +
                (apellidoMaterno != null ? " " + apellidoMaterno : "");
        adapter.setLastName(lastName.trim());

        String resolvedEmail = null;
        if (email != null && !email.trim().isEmpty()) {
            resolvedEmail = email;
        } else if (emailAlternativo != null && !emailAlternativo.trim().isEmpty()) {
            resolvedEmail = emailAlternativo;
        } else {
            resolvedEmail = login;
        }
        adapter.setEmail(resolvedEmail);

        adapter.setEnabled(habilitado == 1);

        // === ATRIBUTOS CUSTOM PARA JWT (compatibilidad con Azure AD) ===
        adapter.setSingleAttribute("oid", String.valueOf(id));
        adapter.setSingleAttribute("preferred_username", login);

        String fullName = ((nombres != null ? nombres : "") + " " +
                (apellidoPaterno != null ? apellidoPaterno : "") + " " +
                (apellidoMaterno != null ? apellidoMaterno : "")).trim();
        adapter.setSingleAttribute("name", fullName);

        adapter.setSingleAttribute("scp", "User.read User.write");

        adapter.setAttribute("roles", fetchUserRoles(id));

        return adapter;
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return CredentialModel.PASSWORD.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        if (!supportsCredentialType(credentialType)) {
            return false;
        }
        String password = getPasswordHash(user.getUsername());
        return password != null;
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        String username = user.getUsername();
        String ip = getClientIp();

        // 1. Verificar si el usuario esta bloqueado en BD (sincronizado desde eventos Keycloak)
        try {
            if (isBlockedInDb(username)) {
                logger.warning("[LOGIN] Usuario bloqueado en BD: " + username);
                new AuditRepository(getConnection()).saveLoginAttempt(username, ip, "keycloak", false, "Cuenta bloqueada");
                return false;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error verificando bloqueo para: " + username, e);
        }

        // 2. Obtener hash de contrasena
        String storedHash = getPasswordHash(username);
        if (storedHash == null) {
            logger.warning("No password hash found for user: " + username);
            return false;
        }

        // 3. Validar contrasena con BCrypt
        String passwordIngresada = input.getChallengeResponse();
        boolean valid;
        try {
            valid = BCrypt.checkpw(passwordIngresada, storedHash);
        } catch (Exception e) {
            logger.warning("BCrypt validation error: " + e.getMessage());
            valid = storedHash.equals(passwordIngresada);
        }

        // 4. Registrar auditoria
        try {
            AuditRepository auditRepo = new AuditRepository(getConnection());
            if (valid) {
                auditRepo.saveLoginAttempt(username, ip, "keycloak", true, null);
                resetBlockedInDb(username);
                logger.info("[LOGIN] Login exitoso para: " + username);
            } else {
                auditRepo.saveLoginAttempt(username, ip, "keycloak", false, "Credenciales incorrectas");
                logger.warning("[LOGIN] Login fallido para: " + username);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error guardando auditoria para: " + username, e);
        }

        return valid;
    }

    private boolean isBlockedInDb(String username) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT BLOQUEADO_POR_INTENTOS FROM ESEGURIDAD.SGTM_USUARIO WHERE UPPER(LOGIN) = UPPER(?)"
            );
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int bloqueado = rs.getInt("BLOQUEADO_POR_INTENTOS");
                return bloqueado == 1;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error checking block status for: " + username, e);
        }
        return false;
    }

    private void resetBlockedInDb(String username) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "UPDATE ESEGURIDAD.SGTM_USUARIO SET BLOQUEADO_POR_INTENTOS = 0, HABILITADO = 1 WHERE UPPER(LOGIN) = UPPER(?)"
            );
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error resetting block status for: " + username, e);
        }
    }

    private int countRecentFailedAttempts(String username) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM ESEGURIDAD.SGTM_AUDITORIA_SESIONES " +
                            "WHERE UPPER(USUARIO) = UPPER(?) AND LOGIN_EXITOSO = 0 " +
                            "AND FECHACREACION > DATEADD(MINUTE, -15, GETDATE())"
            );
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error counting failed attempts for: " + username, e);
        }
        return 0;
    }

    private void disableUserInDb(String username) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "UPDATE ESEGURIDAD.SGTM_USUARIO SET HABILITADO = 0, BLOQUEADO_POR_INTENTOS = 1, " +
                            "MODIFICADOPOR = 'KEYCLOAK', FECHAMODIFICACION = GETDATE() WHERE UPPER(LOGIN) = UPPER(?)"
            );
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error disabling user: " + username, e);
        }
    }

    // ========================================================================
    // CREDENTIAL INPUT UPDATER - Sync contraseña y fecha de cambio con BD
    // ========================================================================

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        String username = user.getUsername();
        String encodedPassword = BCrypt.hashpw(input.getChallengeResponse(), BCrypt.gensalt());

        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "UPDATE ESEGURIDAD.SGTM_USUARIO SET PASSWORD = ?, FECHAULTIMOCAMBIOPASSWORD = ?, MODIFICADOPOR = 'KEYCLOAK', FECHAMODIFICACION = ? WHERE UPPER(LOGIN) = UPPER(?)"
            );
            stmt.setString(1, encodedPassword);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(4, username);
            stmt.executeUpdate();
            logger.info("[PASSWORD] Contraseña actualizada en BD para: " + username);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error actualizando contraseña en BD para: " + username, e);
            return false;
        }
    }

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
        // No aplica - las contraseñas se manejan en BD
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(RealmModel realm, UserModel user) {
        return Stream.empty();
    }

    private String getPasswordHash(String username) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT PASSWORD FROM ESEGURIDAD.SGTM_USUARIO WHERE UPPER(LOGIN) = UPPER(?)"
            );
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("PASSWORD");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching password for user: " + username, e);
        }
        return null;
    }

    private List<String> fetchUserRoles(long userId) {
        List<String> roles = new ArrayList<>();
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT DISTINCT R.NOMBRECORTO " +
                            "FROM ESEGURIDAD.SGTR_USUARIO_PERFILES UP " +
                            "INNER JOIN ESEGURIDAD.SGTM_PERFIL P ON P.ID = UP.ID_PERFIL AND P.HABILITADO = 1 " +
                            "INNER JOIN ESEGURIDAD.SGTM_ROL_EMPRESARIAL R ON R.ID = P.ROLEMPRESARIAL AND R.HABILITADO = 1 " +
                            "WHERE UP.ID_USUARIO = ?"
            );
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String nombreCorto = rs.getString("NOMBRECORTO");
                if (nombreCorto != null && !nombreCorto.trim().isEmpty()) {
                    roles.add(nombreCorto.toUpperCase().trim());
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching roles for user ID: " + userId, e);
        }
        return roles;
    }

    // ========================================================================
    // IMPLEMENTACIÓN DE USER QUERY PROVIDER (Para la barra de búsqueda visual)
    // ========================================================================

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, Integer firstResult, Integer maxResults) {
        List<UserModel> users = new ArrayList<>();
        // Esta es la consulta base
        StringBuilder sql = new StringBuilder("SELECT ID, NOMBRES, APPATERNO, APMATERNO, LOGIN, EMAIL, EMAILALTERNATIVO, HABILITADO FROM ESEGURIDAD.SGTM_USUARIO WHERE 1=1");

        String email = params.get(UserModel.EMAIL);
        String username = params.get(UserModel.USERNAME);
        String search = params.get(UserModel.SEARCH); // AQUÍ ESTABA EL MALDITO ERROR

        // Armar los filtros dinámicos
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOGIN LIKE ? OR NOMBRES LIKE ?)");
        } else {
            if (email != null && !email.trim().isEmpty()) {
                sql.append(" AND (EMAILALTERNATIVO LIKE ? OR LOGIN LIKE ?)");
            }
            if (username != null && !username.trim().isEmpty()) {
                sql.append(" AND LOGIN LIKE ?");
            }
        }

        logger.info(">>> [SQL ARMADO]: " + sql);
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql.toString());
            int paramIndex = 1;

            // Llenar los parámetros en el mismo orden
            if (search != null && !search.trim().isEmpty()) {
                String searchParam = search.trim().equals("*") ? "%" : "%" + search + "%";
                stmt.setString(paramIndex++, searchParam);
                stmt.setString(paramIndex++, searchParam);
            } else {
                if (email != null && !email.trim().isEmpty()) {
                    stmt.setString(paramIndex++, "%" + email + "%");
                    stmt.setString(paramIndex++, "%" + email + "%");
                }
                if (username != null && !username.trim().isEmpty()) {
                    stmt.setString(paramIndex++, "%" + username + "%");
                }
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(mapUser(realm, rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error en busqueda por atributos", e);
        }

        return aplicarPaginacion(users.stream(), firstResult, maxResults);
    }

    private Stream<UserModel> aplicarPaginacion(Stream<UserModel> stream, Integer firstResult, Integer maxResults) {
        if (firstResult != null && firstResult > 0) {
            stream = stream.skip(firstResult);
        }
        if (maxResults != null && maxResults > 0) {
            stream = stream.limit(maxResults);
        }
        return stream;
    }


    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group, Integer firstResult, Integer maxResults) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        return Stream.empty();
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error closing database connection", e);
        }
    }
    // ========================================================================
    // IMPLEMENTACIÓN DE USER COUNT METHODS PROVIDER (Fija el error de Not implemented)
    // ========================================================================

    @Override
    public int getUsersCount(RealmModel realm) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM ESEGURIDAD.SGTM_USUARIO"
            );
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error contando el total de usuarios", e);
        }
        return 0;
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        return getUsersCount(realm);
    }

    @Override
    public int getUsersCount(RealmModel realm, java.util.Set<String> groupIds) {
        // Si no manejas grupos externos, retornar 0 o delegar al conteo principal es suficiente
        return 0;
    }

    @Override
    public int getUsersCount(RealmModel realm, String search) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM ESEGURIDAD.SGTM_USUARIO WHERE LOGIN LIKE ? OR NOMBRES LIKE ?"
            );
            String param = search.trim().equals("*") ? "%" : "%" + search + "%";
            stmt.setString(1, param);
            stmt.setString(2, param);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error contando usuarios con busqueda: " + search, e);
        }
        return 0;
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        // Puedes implementar la lógica de conteo por atributos aquí si es necesario,
        // o retornar 0 temporalmente para que no bloquee la interfaz.
        return 0;
    }
}