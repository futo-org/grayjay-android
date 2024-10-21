#!/bin/sh
DOCUMENT_ROOT=/var/www/html

# Sign sources
echo "Signing all sources..."
/usr/bin/bash ./sign-all-sources.sh

# Build content
echo "Building content..."
./gradlew --stacktrace assembleUnstableRelease

# Take site offline
echo "Taking site offline..."
touch $DOCUMENT_ROOT/maintenance.file

# Swap over the content
echo "Deploying content..."
cp ./app/build/outputs/apk/unstable/release/app-unstable-x86_64-release.apk $DOCUMENT_ROOT/app-x86_64-release-unstable.apk
cp ./app/build/outputs/apk/unstable/release/app-unstable-arm64-v8a-release.apk $DOCUMENT_ROOT/app-arm64-v8a-release-unstable.apk
cp ./app/build/outputs/apk/unstable/release/app-unstable-armeabi-v7a-release.apk $DOCUMENT_ROOT/app-armeabi-v7a-release-unstable.apk
cp ./app/build/outputs/apk/unstable/release/app-unstable-universal-release.apk $DOCUMENT_ROOT/app-universal-release-unstable.apk
cp ./app/build/outputs/apk/unstable/release/app-unstable-x86-release.apk $DOCUMENT_ROOT/app-x86-release-unstable.apk
cp ./app/build/outputs/apk/unstable/release/app-unstable-arm64-v8a-release.apk $DOCUMENT_ROOT/app-release-unstable.apk
git describe --tags > $DOCUMENT_ROOT/version-unstable.txt

aws s3 cp ./app/build/outputs/apk/unstable/release/app-unstable-x86_64-release.apk s3://artifacts-grayjay-app/app-x86_64-release-unstable.apk
aws s3 cp ./app/build/outputs/apk/unstable/release/app-unstable-arm64-v8a-release.apk s3://artifacts-grayjay-app/app-arm64-v8a-release-unstable.apk
aws s3 cp ./app/build/outputs/apk/unstable/release/app-unstable-armeabi-v7a-release.apk s3://artifacts-grayjay-app/app-armeabi-v7a-release-unstable.apk
aws s3 cp ./app/build/outputs/apk/unstable/release/app-unstable-universal-release.apk s3://artifacts-grayjay-app/app-universal-release-unstable.apk
aws s3 cp ./app/build/outputs/apk/unstable/release/app-unstable-x86-release.apk s3://artifacts-grayjay-app/app-x86-release-unstable.apk
aws s3 cp ./app/build/outputs/apk/unstable/release/app-unstable-arm64-v8a-release.apk s3://artifacts-grayjay-app/app-release-unstable.apk

git describe --tags > ./version-unstable.txt
aws s3 cp ./version-unstable.txt s3://artifacts-grayjay-app/version-unstable.txt

# Notify Cloudflare to wipe the CDN cache
echo "Purging Cloudflare cache for zone $CLOUDFLARE_ZONE_ID..."
curl -X POST "https://api.cloudflare.com/client/v4/zones/$CLOUDFLARE_ZONE_ID/purge_cache" \
     -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
     -H "Content-Type: application/json" \
     --data '{"files":["https://releases.grayjay.app/app-x86_64-release-unstable.apk", "https://releases.grayjay.app/app-arm64-v8a-release-unstable.apk", "https://releases.grayjay.app/app-armeabi-v7a-release-unstable.apk", "https://releases.grayjay.app/app-universal-release-unstable.apk", "https://releases.grayjay.app/app-x86-release-unstable.apk", "https://releases.grayjay.app/app-release-unstable.apk", "https://releases.grayjay.app/version-unstable.txt"]}'

sleep 30

# Take site back online
echo "Bringing site back online..."
rm $DOCUMENT_ROOT/maintenance.file
