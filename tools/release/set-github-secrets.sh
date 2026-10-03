#!/usr/bin/env bash
# Sets the Android release's GitHub secrets and variables (release-android.yml) from this Mac: the upload
# keystore and its passwords from local.properties, as secrets of the `production` environment, and the PostHog
# and Play Games ids as repository variables. Nothing is printed or written: each value goes from its file
# straight into `gh`. Needs `gh` signed in as niktok1 with the repository's Secrets, Variables and
# Environments permissions (`gh auth login`, or a token that has them), and the `production` environment made.
#
#     tools/release/set-github-secrets.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
repo=niktok1/kvizic
props=local.properties

setting() {
  local value
  value=$(grep -E "^$1=" "$props" | head -1 | cut -d= -f2-)
  [ -n "$value" ] || { echo "local.properties has no $1" >&2; exit 1; }
  printf '%s' "$value"
}

store=$(setting kvizic.upload.storeFile)
store=${store/#\~/$HOME}
[ "$(setting kvizic.upload.keyAlias)" = kvizic-upload ] || { echo "the workflow signs with the alias kvizic-upload" >&2; exit 1; }

base64 -i "$store" | tr -d '\n' | gh secret set UPLOAD_KEYSTORE_BASE64 --env production -R "$repo"
setting kvizic.upload.storePassword | gh secret set UPLOAD_STORE_PASSWORD --env production -R "$repo"
setting kvizic.upload.keyPassword | gh secret set UPLOAD_KEY_PASSWORD --env production -R "$repo"
gh variable set KVIZIC_POSTHOG_KEY -R "$repo" --body "$(setting kvizic.posthog.key)"
gh variable set KVIZIC_PLAYGAMES_APP_ID -R "$repo" --body "$(setting kvizic.playgames.appId)"
gh variable set KVIZIC_PLAYGAMES_SERVER_CLIENT_ID -R "$repo" --body "$(setting kvizic.playgames.serverClientId)"

# The service account's key, if its JSON file is named: PLAY_KEY=~/Downloads/kvizic-play-….json
if [ -n "${PLAY_KEY:-}" ]; then
  gh secret set PLAY_SERVICE_ACCOUNT_JSON --env production -R "$repo" < "${PLAY_KEY/#\~/$HOME}"
fi
echo "set; the production environment's secrets are:"
gh secret list --env production -R "$repo"
