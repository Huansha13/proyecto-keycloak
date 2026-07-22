#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGINS_DIR="$SCRIPT_DIR/plugins"
JAR_NAME="keycloak-subastas-1.0.0.jar"
ENV=${1:-local}

if [ ! -f "$SCRIPT_DIR/.env.$ENV" ]; then
  echo "ERROR: .env.$ENV no existe. Ambientes validos: local, dev, qa, prd"
  exit 1
fi

echo "==> Building JAR..."
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package -q -f "$SCRIPT_DIR/pom.xml"

echo "==> Copiando JAR a plugins/..."
mkdir -p "$PLUGINS_DIR"
cp "$SCRIPT_DIR/target/$JAR_NAME" "$PLUGINS_DIR/"

if [ ! -f "$PLUGINS_DIR/jbcrypt-0.4.jar" ]; then
    echo "==> Descargando jbcrypt-0.4.jar..."
    curl -sL -o "$PLUGINS_DIR/jbcrypt-0.4.jar" "https://repo1.maven.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar"
fi

echo "==> Deploying to: $ENV"
docker compose --env-file "$SCRIPT_DIR/.env.$ENV" down
docker compose --env-file "$SCRIPT_DIR/.env.$ENV" up -d

echo "==> Deploy completado ($ENV). Logs:"
docker compose --env-file "$SCRIPT_DIR/.env.$ENV" logs -f --tail=50
