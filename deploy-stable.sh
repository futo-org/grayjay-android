#!/bin/sh
DOCUMENT_ROOT=/var/www/html

# Sign sources
echo "Signing all sources..."
/usr/bin/bash ./sign-all-sources.sh

# Build content
echo "Building content..."
./gradlew --stacktrace assembleStableRelease

# Take site offline
echo "Taking site offline..."
touch $DOCUMENT_ROOT/maintenance.file

# Swap over the content
echo "Deploying content..."
cp ./app/build/outputs/apk/stable/release/app-stable-x86_64-release.apk $DOCUMENT_ROOT/app-x86_64-release.apk
cp ./app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release.apk $DOCUMENT_ROOT/app-arm64-v8a-release.apk
cp ./app/build/outputs/apk/stable/release/app-stable-armeabi-v7a-release.apk $DOCUMENT_ROOT/app-armeabi-v7a-release.apk
cp ./app/build/outputs/apk/stable/release/app-stable-universal-release.apk $DOCUMENT_ROOT/app-universal-release.apk
cp ./app/build/outputs/apk/stable/release/app-stable-x86-release.apk $DOCUMENT_ROOT/app-x86-release.apk
cp ./app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release.apk $DOCUMENT_ROOT/app-release.apk

VERSION=$(git describe --tags)
echo $VERSION > $DOCUMENT_ROOT/version.txt
mkdir -p $DOCUMENT_ROOT/changelogs
git tag -l $VERSION -n1000 | awk '{$1=""; print $0}' | sed -e 's/^[ \t]*//' > $DOCUMENT_ROOT/changelogs/$VERSION

# Notify Cloudflare to wipe the CDN cache
echo "Purging Cloudflare cache for zone $CLOUDFLARE_ZONE_ID..."
curl -X POST "https://api.cloudflare.com/client/v4/zones/$CLOUDFLARE_ZONE_ID/purge_cache" \
     -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
     -H "Content-Type: application/json" \
     --data '{"files":["https://releases.grayjay.app/app-x86_64-release.apk", "https://releases.grayjay.app/app-arm64-v8a-release.apk", "https://releases.grayjay.app/app-armeabi-v7a-release.apk", "https://releases.grayjay.app/app-universal-release.apk", "https://releases.grayjay.app/app-x86-release.apk", "https://releases.grayjay.app/app-release.apk", "https://releases.grayjay.app/version.txt"]}'

sleep 30

# Take site back online
echo "Bringing site back online..."
rm $DOCUMENT_ROOT/maintenance.file
