package com.subasta.mode;

import com.subasta.provider.CustomUserStorageProvider;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;

import java.util.List;
import java.util.stream.Stream;

public class DelegateCredentialManager implements SubjectCredentialManager {

    private final CustomUserStorageProvider provider;
    private final RealmModel realm;
    private final UserModel user;

    public DelegateCredentialManager(CustomUserStorageProvider provider, RealmModel realm, UserModel user) {
        this.provider = provider;
        this.realm = realm;
        this.user = user;
    }

    @Override
    public boolean isValid(List<CredentialInput> inputs) {
        for (CredentialInput input : inputs) {
            if (!provider.isValid(realm, user, input)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean updateCredential(CredentialInput input) {
        return false;
    }

    @Override
    public void updateStoredCredential(CredentialModel cred) {
    }

    @Override
    public CredentialModel createStoredCredential(CredentialModel cred) {
        return null;
    }

    @Override
    public boolean removeStoredCredentialById(String id) {
        return false;
    }

    @Override
    public CredentialModel getStoredCredentialById(String id) {
        return null;
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsStream() {
        return Stream.empty();
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsByTypeStream(String type) {
        return Stream.empty();
    }

    @Override
    public CredentialModel getStoredCredentialByNameAndType(String name, String type) {
        return null;
    }

    @Override
    public boolean moveStoredCredentialTo(String id, String newPreviousCredentialId) {
        return false;
    }

    @Override
    public void updateCredentialLabel(String credentialId, String credentialLabel) {
    }

    @Override
    public void disableCredentialType(String credentialType) {
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream() {
        return Stream.empty();
    }

    @Override
    public boolean isConfiguredFor(String type) {
        return provider.isConfiguredFor(realm, user, type);
    }

    @Override
    public boolean isConfiguredLocally(String type) {
        return false;
    }

    @Override
    public Stream<String> getConfiguredUserStorageCredentialTypesStream() {
        return Stream.of(CredentialModel.PASSWORD);
    }

    @Override
    public CredentialModel createCredentialThroughProvider(CredentialModel model) {
        return null;
    }
}
