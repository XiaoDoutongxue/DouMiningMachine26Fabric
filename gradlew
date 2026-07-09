#!/usr/bin/env sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
GRADLE_VERSION=9.5.1
GRADLE_HOME="$APP_HOME/.gradle/local-gradle/gradle-$GRADLE_VERSION"
GRADLE_ZIP="$APP_HOME/.gradle/local-gradle/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  echo "[DouMiningMachine] Gradle $GRADLE_VERSION not found, downloading..."
  mkdir -p "$APP_HOME/.gradle/local-gradle"
  if command -v curl >/dev/null 2>&1; then
    curl -L -o "$GRADLE_ZIP" "$GRADLE_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$GRADLE_ZIP" "$GRADLE_URL"
  else
    echo "curl/wget not found. Please install Gradle $GRADLE_VERSION manually."
    exit 1
  fi
  unzip -o "$GRADLE_ZIP" -d "$APP_HOME/.gradle/local-gradle" >/dev/null
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
