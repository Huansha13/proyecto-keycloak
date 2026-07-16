package com.subasta.provider.storage;

import com.subasta.repository.DatabaseManager;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CustomUserStorageFactory
        implements UserStorageProviderFactory<CustomUserStorage> {

    private static final Logger logger = Logger.getLogger(CustomUserStorageFactory.class.getName());

    public static final String PROVIDER_ID = "legacy-db-user-provider";

    static final String JDBC_URL = "JDBC_URL";
    static final String DB_USER = "DB_USER";
    static final String DB_PASSWORD = "DB_PASSWORD";

    private static final AtomicReference<DatabaseManager> databaseManager = new AtomicReference<>();

    public static DatabaseManager getDatabaseManager() {
        return databaseManager.get();
    }

    public static DatabaseManager tryInitializeFromSession(KeycloakSession session) {
        if (databaseManager.get() != null) {
            return databaseManager.get();
        }
        session.getContext().getRealm().getComponentsStream()
                .filter(comp -> PROVIDER_ID.equals(comp.getProviderId()))
                .findFirst()
                .ifPresent(comp -> {
                    String jdbcUrl = comp.get(JDBC_URL, "");
                    String dbUser = comp.get(DB_USER, "");
                    String dbPassword = comp.get(DB_PASSWORD, "");
                    databaseManager.compareAndSet(null,
                            DatabaseManager.getInstance(jdbcUrl, dbUser, dbPassword));
                    logger.log(Level.INFO, "[FACTORY] DatabaseManager initialized early from component model");
                });
        return databaseManager.get();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public CustomUserStorage create(KeycloakSession session, ComponentModel model) {
        logger.log(Level.INFO, () -> "Creating CustomUserStorageProvider for component: " + model.getName());

        String jdbcUrl = model.get(JDBC_URL, "");
        String dbUser = model.get(DB_USER, "");
        String dbPassword = model.get(DB_PASSWORD, "");

        if (databaseManager.get() == null) {
            synchronized (CustomUserStorageFactory.class) {
                if (databaseManager.get() == null) {
                    databaseManager.set(DatabaseManager.getInstance(jdbcUrl, dbUser, dbPassword));
                }
            }
        }

        return new CustomUserStorage(session, model, databaseManager.get());
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
        DatabaseManager dbManager = databaseManager.get();
        if (dbManager != null) {
            dbManager.close();
        }
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
