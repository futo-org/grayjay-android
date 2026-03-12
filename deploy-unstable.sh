#!/bin/sh
set -eu

R2_ENDPOINT="https://$CF_R2_ACCOUNT_ID.r2.cloudflarestorage.com"

r2_cp() {
  src="$1"
  key="$2"
  cache_control="$3"
  content_type="$4"

  AWS_ACCESS_KEY_ID="$CF_R2_ACCESS_KEY_ID" \
  AWS_SECRET_ACCESS_KEY="$CF_R2_SECRET_ACCESS_KEY" \
  AWS_DEFAULT_REGION=auto \
  aws s3 cp "$src" "s3://$CF_R2_BUCKET/$key" \
    --endpoint-url "$R2_ENDPOINT" \
    --only-show-errors \
    --cache-control "$cache_control" \
    --content-type "$content_type"
}

upload_apk_latest_and_versioned() {
  src="$1"
  filename="$2"

  r2_cp "$src" "$VERSION/$filename" \
    "public, max-age=31536000, immutable" \
    "application/vnd.android.package-archive"

  r2_cp "$src" "$filename" \
    "no-store" \
    "application/vnd.android.package-archive"
}

echo "Signing all sources..."
/usr/bin/bash ./sign-all-sources.sh

echo "Building content..."
./gradlew --stacktrace assembleUnstableRelease

VERSION="$(git describe --tags)"

echo "Deploying unstable artifacts to Cloudflare R2..."
upload_apk_latest_and_versioned "./app/build/outputs/apk/unstable/release/app-unstable-x86_64-release.apk" "app-x86_64-release-unstable.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/unstable/release/app-unstable-arm64-v8a-release.apk" "app-arm64-v8a-release-unstable.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/unstable/release/app-unstable-armeabi-v7a-release.apk" "app-armeabi-v7a-release-unstable.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/unstable/release/app-unstable-universal-release.apk" "app-universal-release-unstable.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/unstable/release/app-unstable-x86-release.apk" "app-x86-release-unstable.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/unstable/release/app-unstable-arm64-v8a-release.apk" "app-release-unstable.apk"

tmp_version="$(mktemp)"
printf '%s\n' "$VERSION" > "$tmp_version"
r2_cp "$tmp_version" "$VERSION/version-unstable.txt" \
  "public, max-age=31536000, immutable" \
  "text/plain; charset=utf-8"
r2_cp "$tmp_version" "version-unstable.txt" \
  "no-store" \
  "text/plain; charset=utf-8"
rm -f "$tmp_version"

DOCUMENT_ROOT=/var/www/html
echo "Deploying content..."
cp ./app/build/outputs/apk/unstable/release/app-unstable-x86_64-release.apk "$DOCUMENT_ROOT/app-x86_64-release-unstable.apk"
cp ./app/build/outputs/apk/unstable/release/app-unstable-arm64-v8a-release.apk "$DOCUMENT_ROOT/app-arm64-v8a-release-unstable.apk"
cp ./app/build/outputs/apk/unstable/release/app-unstable-armeabi-v7a-release.apk "$DOCUMENT_ROOT/app-armeabi-v7a-release-unstable.apk"
cp ./app/build/outputs/apk/unstable/release/app-unstable-universal-release.apk "$DOCUMENT_ROOT/app-universal-release-unstable.apk"
cp ./app/build/outputs/apk/unstable/release/app-unstable-x86-release.apk "$DOCUMENT_ROOT/app-x86-release-unstable.apk"
cp ./app/build/outputs/apk/unstable/release/app-unstable-arm64-v8a-release.apk "$DOCUMENT_ROOT/app-release-unstable.apk"
VERSION="$(git describe --tags)"
echo "$VERSION" > "$DOCUMENT_ROOT/version-unstable.txt"

echo "Done."