package com.subasta.provider;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;
import java.util.logging.Logger;

public class CustomUserStorageProviderFactory
        implements UserStorageProviderFactory<CustomUserStorageProvider> {

    private static final Logger logger = Logger.getLogger(CustomUserStorageProviderFactory.class.getName());

    public static final String PROVIDER_ID = "legacy-db-user-provider";

    static final String JDBC_URL = "JDBC_URL";
    static final String DB_USER = "DB_USER";
    static final String DB_PASSWORD = "DB_PASSWORD";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public CustomUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        logger.info("Creating CustomUserStorageProvider for component: " + model.getName());

        String jdbcUrl = model.get(JDBC_URL, "");
        String dbUser = model.get(DB_USER, "");
        String dbPassword = model.get(DB_PASSWORD, "");

        return new CustomUserStorageProvider(session, model, jdbcUrl, dbUser, dbPassword);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of(
                configProperty(JDBC_URL, "JDBC URL", "URL de conexion JDBC a SQL Server", ProviderConfigProperty.STRING_TYPE),
                configProperty(DB_USER, "Database User", "Usuario de la base de datos", ProviderConfigProperty.STRING_TYPE),
                configProperty(DB_PASSWORD, "Database Password", "Contrasena de la base de datos", ProviderConfigProperty.PASSWORD)
        );
    }

    @Override
    public void close() {
    }

    private ProviderConfigProperty configProperty(String name, String label, String helpText, String type) {
        ProviderConfigProperty prop = new ProviderConfigProperty();
        prop.setName(name);
        prop.setLabel(label);
        prop.setHelpText(helpText);
        prop.setType(type);
        return prop;
    }
}
