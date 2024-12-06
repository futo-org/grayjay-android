#!/bin/sh
DOCUMENT_ROOT=/var/www/html

# Sign sources
echo "Signing all sources..."
/usr/bin/bash ./sign-all-sources.sh

# Build content
echo "Building content..."
./gradlew --stacktrace bundlePlaystoreRelease

# Take site offline
echo "Taking site offline..."
touch $DOCUMENT_ROOT/maintenance.file

# Swap over the content
echo "Deploying content..."
cp ./app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab $DOCUMENT_ROOT/app-playstore-release.aab
aws s3 cp ./app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab s3://artifacts-grayjay-app/app-playstore-release.aab

# Notify Cloudflare to wipe the CDN cache
echo "Purging Cloudflare cache for zone $CLOUDFLARE_ZONE_ID..."
curl -X POST "https://api.cloudflare.com/client/v4/zones/$CLOUDFLARE_ZONE_ID/purge_cache" \
     -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
     -H "Content-Type: application/json" \
     --data '{"files":["https://releases.grayjay.app/app-playstore-release.aab"]}'

sleep 30

# Take site back online
echo "Bringing site back online..."
rm $DOCUMENT_ROOT/maintenance.file
