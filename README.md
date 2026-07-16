# Keycloak Subastas

Plugin Keycloak (JAR) para autenticación contra base de datos SQL Server legacy (solo lectura).

## Requisitos

- Java 21
- Maven
- Docker + Docker Compose

## Compilar

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package
```

Genera: `target/keycloak-subastas-1.0.0.jar`

## Desplegar (primera vez)

```bash
./deploy.sh
```

Este script compila, copia el JAR a `plugins/` y levanta Keycloak con Docker Compose.

## Desplegar (actualización de JAR)

Si Keycloak ya está corriendo y solo actualizaste código:

```bash
./deploy.sh
```

El script hace `docker compose down` + `docker compose up -d`. Los datos persisten en el volumen Docker.

## Desplegar en servidor de la empresa (manual)

Si no se usa `deploy.sh` (por ejemplo, copiando el JAR manualmente al servidor):

### 1. Copiar el JAR al servidor

```bash
scp target/keycloak-subastas-1.0.0.jar user@servidor:/ruta/keycloak-subastas/plugins/
```

### 2. Entrar al servidor

```bash
ssh user@servidor
cd /ruta/keycloak-subastas
```

### 3. Detener y levantar

```bash
docker compose down
docker compose up -d
```

### 4. Verificar logs

```bash
docker compose logs -f --tail=50
```

## Estructura de volumes Docker

El `docker-compose.yml` usa 3 volumes:

| Volume | Tipo | Propósito |
|--------|------|-----------|
| `./plugins:/opt/keycloak/providers` | Bind mount | JARs del SPI (se sincroniza con el host) |
| `./realm-export:/opt/keycloak/data/import` | Bind mount | Realm JSON para importación inicial |
| `keycloak-data:/opt/keycloak/data` | Named volume | Base de datos interna de Keycloak (H2) |

**IMPORTANTE:** El volume `keycloak-data` almacena toda la configuración del Keycloak Admin Console (realms, usuarios, clientes, authentication flows, etc.). Si se pierde, hay que reconfigurar todo desde cero.

## Renombrar el proyecto

Si se renombra el directorio del proyecto (ej: `keycloak-user-storage-spi` → `keycloak-subastas`), Docker Compose crea un **volume nuevo vacío** porque los volumes se nombran con el prefijo del proyecto:

```
keycloak-user-storage-spi_keycloak-data  ← data vieja
keycloak-subastas_keycloak-data          ← vacío (nuevo)
```

### Recuperar la data

```bash
# 1. Detener el contenedor
docker stop keycloak-subastas-container
docker rm keycloak-subastas-container

# 2. Crear el volume nuevo y copiar los datos del viejo
docker volume create keycloak-subastas_keycloak-data
docker run --rm \
  -v keycloak-user-storage-spi_keycloak-data:/from \
  -v keycloak-subastas_keycloak-data:/to \
  alpine sh -c "cp -a /from/. /to/"

# 3. Eliminar el volume viejo
docker volume rm keycloak-user-storage-spi_keycloak-data

# 4. Levantar
docker compose up -d
```

Verificar que el log muestre: `Realm 'subasta-3.0' already exists. Import skipped`

## Si el contenedor tiene nombre conflictivo

Error: `The container name "/keycloak-subastas-container" is already in use`

```bash
# El contenedor viejo sigue corriendo, hay que eliminarlo
docker stop keycloak-subastas-container
docker rm keycloak-subastas-container

# Luego levantar
docker compose up -d
```

Esto pasa cuando el contenedor fue creado por otro compose project o por `docker run`.

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

- La contraseña se valida con BCrypt.
- El provider es **solo lectura**: no permite crear, modificar ni eliminar usuarios desde Keycloak.
- El dominio `*.database.windows.net` debe estar en la allowlist del firewall si se conecta a Azure SQL.
