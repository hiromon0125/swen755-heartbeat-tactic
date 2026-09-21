#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" :app:runSensorReader "$@"
