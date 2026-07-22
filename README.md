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

## Desplegar

```bash
./deploy.sh local    # tu ambiente de desarrollo 
./deploy.sh dev      # desarrollo en servidor 
./deploy.sh qa       # QA
./deploy.sh prd      # produccion (solo en servidor prd)
```