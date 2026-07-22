package com.subasta.provider.storage;

import com.subasta.model.UserAdapter;
import com.subasta.model.UserData;
import com.subasta.repository.DatabaseManager;
import com.subasta.repository.UserRepository;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class CustomUserStorage
        implements UserStorageProvider, UserLookupProvider, CredentialInputValidator,
        CredentialInputUpdater, UserQueryProvider {

    private static final Logger logger = Logger.getLogger(CustomUserStorage.class.getName());

    private final KeycloakSession session;
    private final ComponentModel model;
    // private final DatabaseManager databaseManager;
    private final UserRepository userRepository;

    public CustomUserStorage(KeycloakSession session, ComponentModel model,
                             DatabaseManager databaseManager) {
        this.session = session;
        this.model = model;
        // this.databaseManager = databaseManager;
        this.userRepository = new UserRepository(databaseManager);
    }

    private UserModel mapUser(RealmModel realm, UserData data) {
        String storageId = StorageId.keycloakId(model, String.valueOf(data.id()));
        UserAdapter adapter = getUserAdapter(realm, data, storageId);

        adapter.setSingleAttribute("oid", String.valueOf(data.id()));
        adapter.setSingleAttribute("preferred_username", data.login());

        String fullName = ((data.nombres() != null ? data.nombres() : "") + " " +
                (data.apellidoPaterno() != null ? data.apellidoPaterno() : "") + " " +
                (data.apellidoMaterno() != null ? data.apellidoMaterno() : "")).trim();
        adapter.setSingleAttribute("name", fullName);

        adapter.setSingleAttribute("scp", "User.read User.write");

        adapter.setAttribute("roles", userRepository.fetchUserRoles(data.id()));

        return adapter;
    }

    private UserAdapter getUserAdapter(RealmModel realm, UserData data, String storageId) {
        UserAdapter adapter = new UserAdapter(session, realm, model, storageId, this);

        adapter.setUsername(data.login());
        adapter.setFirstName(data.nombres() != null ? data.nombres() : "");

        String lastName = (data.apellidoPaterno() != null ? data.apellidoPaterno() : "") +
                (data.apellidoMaterno() != null ? " " + data.apellidoMaterno() : "");
        adapter.setLastName(lastName.trim());

        String resolvedEmail;
        if (data.email() != null && !data.email().trim().isEmpty()) {
            resolvedEmail = data.email();
        } else if (data.emailAlternativo() != null && !data.emailAlternativo().trim().isEmpty()) {
            resolvedEmail = data.emailAlternativo();
        } else {
            resolvedEmail = data.login();
        }
        adapter.setEmail(resolvedEmail);

        boolean isBlocked = userRepository.isUserBlocked(data.login());
        boolean isEnabled = data.habilitado() == 1 && !isBlocked;
        adapter.setEnabled(isEnabled);

        if (isBlocked) {
            logger.log(Level.INFO, () -> "[BLOCKED] User " + data.login() + " is blocked → isEnabled=false");
        }

        return adapter;
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        UserData data = userRepository.findUserById(Long.parseLong(externalId));
        return data != null ? mapUser(realm, data) : null;
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        UserData data = userRepository.findUserByUsername(username);
        return data != null ? mapUser(realm, data) : null;
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        UserData data = userRepository.findUserByEmail(email);
        return data != null ? mapUser(realm, data) : null;
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        if (!supportsCredentialType(credentialType)) {
            return false;
        }
        String password = userRepository.getPasswordHash(user.getUsername());
        return password != null;
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        String username = user.getUsername();
        String storedHash = userRepository.getPasswordHash(username);
        if (storedHash == null) {
            logger.log(Level.WARNING, () -> "No password hash found for user: " + username);
            return false;
        }

        String passwordIngresada = input.getChallengeResponse();
        boolean valid;
        try {
            valid = BCrypt.checkpw(passwordIngresada, storedHash);
        } catch (Exception e) {
            logger.log(Level.WARNING, () -> "Bcrypt validation error for user: " + username);
            valid = false;
        }

        if (valid) {
            logger.log(Level.INFO, () -> "[LOGIN] Login exitoso para: " + username);
        } else {
            logger.log(Level.WARNING, () -> "[LOGIN] Login fallido para: " + username);
        }

        return valid;
    }

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) {
            return false;
        }
        String password = input.getChallengeResponse();
        String username = user.getUsername();
        String email = user.getEmail() != null ? user.getEmail() : username;

        String currentHash = userRepository.getPasswordHash(username);
        if (currentHash != null && !currentHash.isEmpty() && BCrypt.checkpw(password, currentHash)) {
            throw new ModelException("invalidPasswordHistoryMessage", 8);
        }

        List<String> historyHashes = userRepository.getPasswordHistory(email, 8);
        for (String historyHash : historyHashes) {
            if (historyHash != null && !historyHash.isEmpty() && BCrypt.checkpw(password, historyHash)) {
                throw new ModelException("invalidPasswordHistoryMessage", 8);
            }
        }

        PasswordPolicyManagerProvider policyManager = session.getProvider(PasswordPolicyManagerProvider.class);
        if (policyManager != null) {
            PolicyError error = policyManager.validate(realm, user, password);
            if (error != null) {
                throw new ModelException(error.getMessage(), error.getParameters());
            }
        }

        if (currentHash != null && !currentHash.isEmpty()) {
            userRepository.insertPasswordHistory(email, currentHash);
        }

        String encodedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        return userRepository.updatePassword(username, encodedPassword);
    }

    public boolean updateStoredPassword(UserModel user, String rawPassword) {
        String username = user.getUsername();
        String email = user.getEmail() != null ? user.getEmail() : username;

        String currentHash = userRepository.getPasswordHash(username);
        if (currentHash != null && !currentHash.isEmpty()) {
            userRepository.insertPasswordHistory(email, currentHash);
        }

        String encodedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        boolean updated = userRepository.updatePassword(username, encodedPassword);
        logger.log(Level.INFO, () -> "[CREDENTIAL] updateStoredPassword sync to external DB for: " + username + " result=" + updated);
        return updated;
    }

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
        //
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(RealmModel realm, UserModel user) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, Integer firstResult, Integer maxResults) {
        List<UserData> usersData;
        String search = params.get(UserModel.SEARCH);

        if (search != null && !search.trim().isEmpty()) {
            usersData = userRepository.searchUsers(search);
        } else {
            usersData = userRepository.searchByEmailAndUsername(
                    params.get(UserModel.EMAIL),
                    params.get(UserModel.USERNAME)
            );
        }

        List<UserModel> users = new ArrayList<>();
        for (UserData data : usersData) {
            users.add(mapUser(realm, data));
        }

        return applyPagination(users.stream(), firstResult, maxResults);
    }

    private Stream<UserModel> applyPagination(Stream<UserModel> stream, Integer firstResult, Integer maxResults) {
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
        //
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        return userRepository.countUsers();
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        return getUsersCount(realm);
    }

    @Override
    public int getUsersCount(RealmModel realm, java.util.Set<String> groupIds) {
        return 0;
    }

    @Override
    public int getUsersCount(RealmModel realm, String search) {
        return userRepository.countUsersBySearch(search);
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        return 0;
    }
}
