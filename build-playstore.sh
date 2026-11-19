#!/bin/sh
set -eu

# Sign sources
echo "Signing all sources..."
/usr/bin/bash ./sign-all-sources.sh

# Build content
echo "Building content..."
./gradlew --no-daemon --max-workers=1 --stacktrace bundlePlaystoreRelease

# The pipeline publishes the generated AAB directly to Google Play and retains
# it as a CI artifact. No public download or local web-root copy is needed.
