#!/usr/bin/env bash
# Sets the dev build's GitHub secrets and variable (dev-build.yml) from this Mac: the debug keystore Android
# Studio made (~/.android/debug.keystore, whose SHA-1 Play Games knows for io.ntole.kvizic.dev), Firebase App
# Distribution's service account key, and the dev app's Firebase id. Nothing is printed.
#
#     FIREBASE_KEY=~/Downloads/<key>.json FIREBASE_APP_ID=1:…:android:… tools/release/set-dev-build-secrets.sh
set -euo pipefail
repo=niktok1/kvizic
: "${FIREBASE_KEY:?name the service account's JSON: FIREBASE_KEY=~/Downloads/....json}"
: "${FIREBASE_APP_ID:?name the dev app's Firebase id: FIREBASE_APP_ID=1:...:android:...}"

base64 -i ~/.android/debug.keystore | tr -d '\n' | gh secret set DEBUG_KEYSTORE_BASE64 -R "$repo"
gh secret set FIREBASE_SERVICE_ACCOUNT_JSON -R "$repo" < "${FIREBASE_KEY/#\~/$HOME}"
gh variable set FIREBASE_APP_ID_DEV -R "$repo" --body "$FIREBASE_APP_ID"
echo "set; the repository's secrets are:"
gh secret list -R "$repo"
