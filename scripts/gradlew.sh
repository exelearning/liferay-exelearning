#!/usr/bin/env bash
# Runs the Gradle wrapper with a local JDK 21 when available, otherwise inside
# the pinned eclipse-temurin:21 container with a persistent Gradle cache volume.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_IMAGE="${BUILD_IMAGE:-liferay-exelearning-build:21}"

java_major() {
	"$1" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}'
}

JAVA_BIN=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
	JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1; then
	JAVA_BIN="$(command -v java)"
fi

if [ -z "${GRADLE_IN_DOCKER:-}" ] && [ -n "$JAVA_BIN" ] && [ "$(java_major "$JAVA_BIN")" = "21" ]; then
	exec "$ROOT/gradlew" -p "$ROOT" "$@"
fi

if ! command -v docker >/dev/null 2>&1; then
	echo "error: JDK 21 not found and Docker is unavailable; install JDK 21 or Docker." >&2
	exit 1
fi

if ! docker image inspect "$BUILD_IMAGE" >/dev/null 2>&1; then
	docker build -q -t "$BUILD_IMAGE" "$ROOT/docker/build" >/dev/null
fi

VOLUME=liferay-exelearning-gradle
if ! docker volume inspect "$VOLUME" >/dev/null 2>&1; then
	docker volume create "$VOLUME" >/dev/null
	docker run --rm -v "$VOLUME":/gradle-home "$BUILD_IMAGE" chown "$(id -u):$(id -g)" /gradle-home
fi

TTY_FLAGS=()
if [ -t 0 ] && [ -t 1 ]; then
	TTY_FLAGS=(-it)
fi

exec docker run --rm ${TTY_FLAGS[@]+"${TTY_FLAGS[@]}"} \
	-u "$(id -u):$(id -g)" \
	-e GRADLE_USER_HOME=/gradle-home \
	-e HOME=/tmp \
	-e GIT_CONFIG_COUNT=1 \
	-e GIT_CONFIG_KEY_0=safe.directory \
	-e GIT_CONFIG_VALUE_0='*' \
	-v "$VOLUME":/gradle-home \
	-v "$ROOT":"$ROOT" \
	-w "$ROOT" \
	"$BUILD_IMAGE" ./gradlew "$@"
