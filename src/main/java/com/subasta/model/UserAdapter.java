package com.subasta.model;

import com.subasta.provider.storage.CustomUserStorage;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.*;
import org.keycloak.storage.adapter.AbstractUserAdapter;

import java.util.*;



public class UserAdapter extends AbstractUserAdapter {

    private static final String REQUIRED_ACTIONS = "requiredActions";
    private final String keycloakId;
    private final Map<String, List<String>> attributes = new HashMap<>();
    private final CustomUserStorage provider;

    public UserAdapter(
            KeycloakSession session,
            RealmModel realm,
            ComponentModel model,
            String keycloakId,
            CustomUserStorage provider
    ) {
        super(session, realm, model);
        this.keycloakId = keycloakId;
        this.provider = provider;
    }

    @Override
    public String getId() {
        return this.keycloakId;
    }

    @Override
    public String getUsername() {
        return getFirst(UserModel.USERNAME);
    }

    @Override
    public void setUsername(String username) {
        setSingleAttribute(UserModel.USERNAME, username);
    }

    @Override
    public String getFirstName() {
        return getFirst(UserModel.FIRST_NAME);
    }

    @Override
    public void setFirstName(String firstName) {
        setSingleAttribute(UserModel.FIRST_NAME, firstName);
    }

    @Override
    public String getLastName() {
        return getFirst(UserModel.LAST_NAME);
    }

    @Override
    public void setLastName(String lastName) {
        setSingleAttribute(UserModel.LAST_NAME, lastName);
    }

    @Override
    public String getEmail() {
        return getFirst(UserModel.EMAIL);
    }

    @Override
    public void setEmail(String email) {
        setSingleAttribute(UserModel.EMAIL, email);
    }

    @Override
    public boolean isEnabled() {
        String val = getFirst(UserModel.ENABLED);
        return val == null || Boolean.parseBoolean(val);
    }

    @Override
    public void setEnabled(boolean enabled) {
        setSingleAttribute(UserModel.ENABLED, String.valueOf(enabled));
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        return new HashMap<>(attributes);
    }

    @Override
    public List<String> getAttribute(String name) {
        List<String> values = attributes.get(name);
        return values != null ? values : Collections.emptyList();
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        attributes.put(name, values);
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        attributes.put(name, Collections.singletonList(value));
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public boolean isEmailVerified() {
        return true;
    }

    @Override
    public void setEmailVerified(boolean verified) {
        //
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        return new DelegateCredentialManager(provider, realm, this);
    }

    @Override
    public void addRequiredAction(String action) {
        List<String> current = attributes.getOrDefault(REQUIRED_ACTIONS, new ArrayList<>());
        if (!current.contains(action)) {
            current.add(action);
            attributes.put(REQUIRED_ACTIONS, current);
        }
    }

    @Override
    public void removeRequiredAction(String action) {
        List<String> current = attributes.get(REQUIRED_ACTIONS);
        if (current != null) {
            current.remove(action);
            if (current.isEmpty()) {
                attributes.remove(REQUIRED_ACTIONS);
            }
        }
    }

    @Override
    public Set<String> getRequiredActions() {
        List<String> current = attributes.get(REQUIRED_ACTIONS);
        return current != null ? new HashSet<>(current) : Collections.emptySet();
    }

    private String getFirst(String name) {
        List<String> values = attributes.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserModel that)) return false;

        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
