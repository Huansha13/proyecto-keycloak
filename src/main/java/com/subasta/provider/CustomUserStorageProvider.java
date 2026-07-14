package com.subasta.provider;

import com.subasta.mode.UserAdapter;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class CustomUserStorageProvider
        implements UserStorageProvider, UserLookupProvider, CredentialInputValidator, UserQueryProvider {

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
                            "FROM ESEGURIDAD.SGTM_USUARIO WHERE LOGIN = ?"
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
                            "FROM ESEGURIDAD.SGTM_USUARIO WHERE LOGIN = ? OR EMAIL = ? OR EMAILALTERNATIVO = ?"
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

        logger.info(String.format(">>> [MAPEO BD] ID: %d | Login: '%s' | Nombres: '%s' | AppP: '%s' | AppM: '%s' | Email: '%s' | EmailAlt: '%s'",
                id, login, nombres, apellidoPaterno, apellidoMaterno, email, emailAlternativo));

        String storageId = StorageId.keycloakId(model, String.valueOf(id));
        UserAdapter adapter = new UserAdapter(session, realm, model, storageId);

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

        String storedHash = getPasswordHash(user.getUsername());
        if (storedHash == null) {
            logger.warning("No password hash found for user: " + user.getUsername());
            return false;
        }

        String passwordIngresada = input.getChallengeResponse();

        try {
            // FIX: Verificación real de la contraseña usando BCrypt
            return BCrypt.checkpw(passwordIngresada, storedHash);
        } catch (Exception e) {
            // Fallback en caso de que el hash en BD no sea de formato BCrypt o esté en texto plano
            logger.warning("Error comparando hashes BCrypt. Intentando comparacion plana por si acaso...");
            return storedHash.equals(passwordIngresada);
        }
    }

    private String getPasswordHash(String username) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement(
                    "SELECT PASSWORD FROM ESEGURIDAD.SGTM_USUARIO WHERE LOGIN = ?"
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