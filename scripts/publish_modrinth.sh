#!/usr/bin/env bash
# Publishes one built jar to Modrinth as its own version.
#
# One version per Minecraft target is how this project is already listed there,
# so every jar gets its own version_number and game_versions entry.
#
# Env:
#   MODRINTH_TOKEN       Modrinth personal access token with write access
#   MODRINTH_PROJECT_ID  project id (or slug)
#   MODRINTH_CHANGELOG   file holding the changelog text (required)
#   MODRINTH_VERSION_TYPE  alpha | beta | release (default: alpha)
#
# Usage: publish_modrinth.sh <jar> <version_number> <name> <game_version>
set -euo pipefail

jar=${1:?jar}
version_number=${2:?version number}
version_name=${3:?version name}
game_version=${4:?minecraft version}
version_type=${MODRINTH_VERSION_TYPE:-alpha}

: "${MODRINTH_TOKEN:?MODRINTH_TOKEN is not set}"
: "${MODRINTH_PROJECT_ID:?MODRINTH_PROJECT_ID is not set}"
: "${MODRINTH_CHANGELOG:?MODRINTH_CHANGELOG is not set}"
[ -f "$jar" ] || { echo "jar not found: $jar" >&2; exit 1; }
[ -f "$MODRINTH_CHANGELOG" ] || { echo "changelog not found: $MODRINTH_CHANGELOG" >&2; exit 1; }

api=https://api.modrinth.com/v2
auth=(-H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: Ahaduzzamankhan/inputbooster-release-ci")

# Idempotent: a re-run of the workflow must not fail or create a second version.
existing=$(curl -sS "${auth[@]}" "$api/project/$MODRINTH_PROJECT_ID/version")
if ! printf '%s' "$existing" | head -c 1 | grep -q '\['; then
  echo "Modrinth did not return a version list for project '$MODRINTH_PROJECT_ID':" >&2
  printf '%s\n' "$existing" >&2
  exit 1
fi
if printf '%s' "$existing" | python3 -c "
import json, sys
wanted = sys.argv[1]
versions = json.load(sys.stdin)
sys.exit(0 if any(v.get('version_number') == wanted for v in versions) else 1)
" "$version_number"; then
  echo "Modrinth version $version_number already exists; nothing to upload."
  exit 0
fi

payload=$(MODRINTH_PROJECT_ID="$MODRINTH_PROJECT_ID" \
          VERSION_NUMBER="$version_number" VERSION_NAME="$version_name" \
          GAME_VERSION="$game_version" VERSION_TYPE="$version_type" \
          CHANGELOG_FILE="$MODRINTH_CHANGELOG" python3 - <<'PY'
import json, os
with open(os.environ["CHANGELOG_FILE"], encoding="utf-8") as fh:
    changelog = fh.read()
print(json.dumps({
    "name": os.environ["VERSION_NAME"],
    "version_number": os.environ["VERSION_NUMBER"],
    "changelog": changelog,
    "dependencies": [
        # Fabric API is required; Sodium is optional and is not forced on users.
        {"project_id": "P7dR8mSH", "dependency_type": "required"},
        {"project_id": "AANobbMI", "dependency_type": "optional"},
    ],
    "game_versions": [os.environ["GAME_VERSION"]],
    "version_type": os.environ["VERSION_TYPE"],
    "loaders": ["fabric"],
    "featured": False,
    "project_id": os.environ["MODRINTH_PROJECT_ID"],
    "file_parts": ["file"],
    "primary_file": "file",
}))
PY
)

echo "Uploading $(basename "$jar") as $version_number ($game_version)..."
response=$(curl -sS -w '\n%{http_code}' "${auth[@]}" -X POST "$api/version" \
  --form-string "data=$payload" \
  -F "file=@$jar")
status=$(printf '%s' "$response" | tail -n1)
body=$(printf '%s' "$response" | sed '$d')

if [ "$status" != "200" ] && [ "$status" != "201" ]; then
  echo "Modrinth upload failed (HTTP $status):" >&2
  printf '%s\n' "$body" >&2
  exit 1
fi

printf '%s' "$body" | python3 -c "
import json, sys
v = json.load(sys.stdin)
print('Published', v['version_number'], 'id', v['id'], 'files', [f['filename'] for f in v['files']])
"
