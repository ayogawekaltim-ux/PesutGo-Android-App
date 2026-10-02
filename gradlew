#!/bin/sh
# Lightweight repository bootstrapper. The build workflow provisions Gradle 8.13.
# A standard gradle-wrapper.jar is intentionally not vendored in this generated project.
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle 8.13 is required. Open this project in Android Studio or install Gradle 8.13, then run this script again." >&2
exit 1
