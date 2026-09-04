#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle n'est pas installé. Ouvrez le projet dans Android Studio Quail 4, ou installez Gradle 9.6.0." >&2
exit 1
