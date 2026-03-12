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
./gradlew --stacktrace assembleStableRelease

VERSION="$(git describe --tags)"

echo "Deploying artifacts to Cloudflare R2..."
upload_apk_latest_and_versioned "./app/build/outputs/apk/stable/release/app-stable-x86_64-release.apk" "app-x86_64-release.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release.apk" "app-arm64-v8a-release.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/stable/release/app-stable-armeabi-v7a-release.apk" "app-armeabi-v7a-release.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/stable/release/app-stable-universal-release.apk" "app-universal-release.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/stable/release/app-stable-x86-release.apk" "app-x86-release.apk"
upload_apk_latest_and_versioned "./app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release.apk" "app-release.apk"

tmp_version="$(mktemp)"
printf '%s\n' "$VERSION" > "$tmp_version"
r2_cp "$tmp_version" "$VERSION/version.txt" \
  "public, max-age=31536000, immutable" \
  "text/plain; charset=utf-8"
r2_cp "$tmp_version" "version.txt" \
  "no-store" \
  "text/plain; charset=utf-8"
rm -f "$tmp_version"

tmp_changelog="$(mktemp)"
git tag -l --format='%(contents)' "$VERSION" > "$tmp_changelog"
r2_cp "$tmp_changelog" "changelogs/$VERSION" \
  "public, max-age=31536000, immutable" \
  "text/plain; charset=utf-8"
rm -f "$tmp_changelog"

DOCUMENT_ROOT=/var/www/html
echo "Deploying content..."
cp ./app/build/outputs/apk/stable/release/app-stable-x86_64-release.apk "$DOCUMENT_ROOT/app-x86_64-release.apk"
cp ./app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release.apk "$DOCUMENT_ROOT/app-arm64-v8a-release.apk"
cp ./app/build/outputs/apk/stable/release/app-stable-armeabi-v7a-release.apk "$DOCUMENT_ROOT/app-armeabi-v7a-release.apk"
cp ./app/build/outputs/apk/stable/release/app-stable-universal-release.apk "$DOCUMENT_ROOT/app-universal-release.apk"
cp ./app/build/outputs/apk/stable/release/app-stable-x86-release.apk "$DOCUMENT_ROOT/app-x86-release.apk"
cp ./app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release.apk "$DOCUMENT_ROOT/app-release.apk"
VERSION="$(git describe --tags)"
echo "$VERSION" > "$DOCUMENT_ROOT/version.txt"
mkdir -p "$DOCUMENT_ROOT/changelogs"
git tag -l --format='%(contents)' "$VERSION" > "$DOCUMENT_ROOT/changelogs/$VERSION"

echo "Done."