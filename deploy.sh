#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGINS_DIR="$SCRIPT_DIR/plugins"
JAR_NAME="keycloak-user-storage-spi-1.0.0.jar"
CONTAINER="keycloak-subastas-container"

echo "==> Building JAR..."
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package -q -f "$SCRIPT_DIR/pom.xml"

echo "==> Copiando JAR y dependencias a plugins/..."
mkdir -p "$PLUGINS_DIR"
cp "$SCRIPT_DIR/target/$JAR_NAME" "$PLUGINS_DIR/"

# Copiar jbcrypt si no existe en plugins/
if [ ! -f "$PLUGINS_DIR/jbcrypt-0.4.jar" ]; then
    echo "==> Descargando jbcrypt-0.4.jar..."
    curl -sL -o "$PLUGINS_DIR/jbcrypt-0.4.jar" "https://repo1.maven.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar"
fi

echo "==> Reiniciando Keycloak..."
cd "$SCRIPT_DIR"
docker compose down
docker compose up -d

echo "==> Deploy completado. Logs:"
docker compose logs -f --tail=50
