#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" :app:installDist --quiet
exec "$PROJECT_DIR/app/build/install/app/bin/app" "$@"
