# Keycloak Custom User Storage SPI

Plugin Keycloak (JAR) para autenticación contra base de datos SQL Server legacy (solo lectura).

## Requisitos

- Java 21
- Maven
- Docker (Keycloak corriendo)

## Compilar

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package
```

Genera: `target/keycloak-user-storage-spi-1.0.0.jar`

## Desplegar en Keycloak (Docker)

### 1. Buscar el contenedor

```bash
docker ps -a | grep keycloak
```

### 2. Copiar el JAR al contenedor

```bash
docker cp target/keycloak-user-storage-spi-1.0.0.jar <nombre-contenedor>:/opt/keycloak/providers/keycloak-user-storage-spi-1.0.0.jar
```

### 3. Reiniciar el contenedor

```bash
docker restart <nombre-contenedor>
```

### 4. Verificar

Abrir Keycloak Admin Console y crear un **User Federation** provider. El SPI aparecerá como `legacy-db-user-provider`.

## Configuración (via UI de Keycloak)

Al crear el provider en la consola de Keycloak, se muestran 3 campos:

| Campo | Descripción | Tipo |
|-------|-------------|------|
| `JDBC_URL` | URL de conexión JDBC a SQL Server | String |
| `DB_USER` | Usuario de la base de datos | String |
| `DB_PASSWORD` | Contraseña de la base de datos | Password |

## Base de datos legacy

El SPI busca usuarios en la tabla `ESEGURIDAD.SGTM_USUARIO` con los campos:

- `ID`, `NOMBRES`, `APPATERNO`, `APMATERNO`, `LOGIN`, `EMAILALTERNATIVO`, `HABILITADO`, `PASSWORD`

## Notas

- La contraseña se valida con comparación directa (placeholder). Reemplazar con BCrypt u otro hash según el esquema de la BD legacy.
- El provider es **solo lectura**: no permite crear, modificar ni eliminar usuarios desde Keycloak.



## docker run Keycloak

```bash
docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:latest start-dev
```