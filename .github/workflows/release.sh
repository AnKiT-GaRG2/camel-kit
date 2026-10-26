#!/usr/bin/env bash
set -euo pipefail

# Bound the reactor to the selected product; never deploy the root reactor here.
arguments=$(python3 tools/release_target.py "${RELEASE_TARGET:?}" --tag "${RELEASE_TAG:?}")
mapfile -t target_args <<< "$arguments"
profiles=sourcecheck,release
if [[ "$RELEASE_TARGET" == kit ]]; then
  profiles+=,linux-ship-certification
fi
# Central validates and stages the upload. Promotion is a separate release-manager action.
exec ./mvnw -B "${target_args[@]}" -P"$profiles" -Deval.requireVectors=true \
  -DdeployAtEnd=true -Dgpg.keyname="${GPG_ID:?}" -Dgpg.passphrase= clean deploy
