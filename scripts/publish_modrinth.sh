#!/usr/bin/env bash
# Publishes one built jar to Modrinth as its own version.
#
# One version per Minecraft target is how this project is listed there, so each
# jar gets its own version_number and game_versions entry.
#
# The version number, display name, release channel and changelog file are all
# derived from the jar name (inputbooster-<mod_version>-mc<suffix>.jar) and the
# gradle.properties of the project it came from, so a new release needs no edit
# here.
#
# Env:
#   MODRINTH_TOKEN         Modrinth personal access token with write access
#   MODRINTH_PROJECT_ID    project id or slug
#   MODRINTH_VERSION_TYPE  override the derived alpha/beta/release channel
#
# Usage: publish_modrinth.sh <jar> <minecraft_version>
set -uo pipefail

jar=${1:?jar}
game_version=${2:?minecraft version}

base=$(basename "$jar")
if ! printf '%s' "$base" | grep -Eq '^inputbooster-.+-mc[0-9]+\.jar$'; then
  echo "::error title=Modrinth upload failed::unexpected jar name: $base"
  exit 1
fi
mod_version=$(printf '%s' "$base" | sed -E 's/^inputbooster-(.*)-mc[0-9]+\.jar$/\1/')
suffix=$(printf '%s' "$base" | sed -E 's/^inputbooster-.*-mc([0-9]+)\.jar$/\1/')
version_number="${mod_version}-mc${suffix}"
version_name="InputBooster ${mod_version} (MC ${game_version})"

case "$mod_version" in
  *alpha*) derived_type=alpha ;;
  *beta*)  derived_type=beta ;;
  *)       derived_type=release ;;
esac
version_type=${MODRINTH_VERSION_TYPE:-$derived_type}

MODRINTH_CHANGELOG=${MODRINTH_CHANGELOG:-RELEASE_NOTES_${mod_version}.md}

# Everything below is invisible in the log without admin rights, so failures are
# written to the step summary (readable through the checks API) as well.
summary() {
  echo "$*"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"; fi
}
fail() {
  summary "FAILED ${version_number}: $*"
  echo "::error title=Modrinth upload failed::${version_number}: $*"
  exit 1
}

[ -n "${MODRINTH_TOKEN:-}" ]          || fail "MODRINTH_TOKEN is not set"
[ -n "${MODRINTH_PROJECT_ID:-}" ]     || fail "MODRINTH_PROJECT_ID is not set"
[ -n "${MODRINTH_CHANGELOG:-}" ]      || fail "MODRINTH_CHANGELOG is not set"
[ -f "$jar" ]                         || fail "jar not found: $jar"
[ -f "$MODRINTH_CHANGELOG" ]          || fail "changelog not found: $MODRINTH_CHANGELOG"

summary "derived: version ${version_number}, channel ${version_type}, changelog ${MODRINTH_CHANGELOG}, name \"${version_name}\""

api=https://api.modrinth.com/v2
auth=(-H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: Ahaduzzamankhan/inputbooster-release-ci")

# A token that cannot authenticate is the most common setup mistake, so it is
# checked once up front and reported by name.
user=$(curl -sS -w '' "${auth[@]}" "$api/user" 2>&1) || fail "could not reach the Modrinth API: $user"
case "$user" in
  *'"username"'*) summary "Authenticated as $(printf '%s' "$user" | sed -n 's/.*"username":"\([^"]*\)".*/\1/p')" ;;
  *) fail "MODRINTH_TOKEN was rejected: $(printf '%s' "$user" | head -c 400)" ;;
esac

# The version payload takes the numeric/base62 project id, not the slug: sending
# a slug there fails with "Base62 decoding overflowed". Resolving it also lets
# either form be configured.
project=$(curl -sS "${auth[@]}" "$api/project/$MODRINTH_PROJECT_ID" 2>&1) \
  || fail "could not read project '$MODRINTH_PROJECT_ID': $(printf '%s' "$project" | head -c 400)"
project_id=$(printf '%s' "$project" | python3 -c '
import json, sys
try:
    print(json.load(sys.stdin)["id"])
except Exception:
    raise SystemExit(1)
' 2>/dev/null) || fail "project '$MODRINTH_PROJECT_ID' did not return an id: $(printf '%s' "$project" | head -c 400)"
summary "Project id ${project_id}"

versions=$(curl -sS "${auth[@]}" "$api/project/$project_id/version" 2>&1) \
  || fail "could not list versions: $(printf '%s' "$versions" | head -c 400)"
case "$versions" in
  \[*) ;;
  *) fail "project '$MODRINTH_PROJECT_ID' did not return a version list: $(printf '%s' "$versions" | head -c 400)" ;;
esac

if printf '%s' "$versions" | python3 -c '
import json, sys
wanted = sys.argv[1]
sys.exit(0 if any(v.get("version_number") == wanted for v in json.load(sys.stdin)) else 1)
' "$version_number"; then
  summary "SKIP ${version_number}: already on Modrinth"
  exit 0
fi

payload=$(VERSION_NUMBER="$version_number" VERSION_NAME="$version_name" \
          GAME_VERSION="$game_version" VERSION_TYPE="$version_type" \
          CHANGELOG_FILE="$MODRINTH_CHANGELOG" \
          PROJECT_ID="$project_id" python3 - <<'PY' || exit 1
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
    # Required by the create-version schema, even though it is deprecated.
    "featured": False,
    "project_id": os.environ["PROJECT_ID"],
    "file_parts": ["file"],
    "primary_file": "file",
}))
PY
) || fail "could not build the request payload"

summary "Uploading $(basename "$jar") as ${version_number} (MC ${game_version})"
response=$(curl -sS -w '\n%{http_code}' "${auth[@]}" -X POST "$api/version" \
  --form-string "data=$payload" \
  -F "file=@$jar" 2>&1) || fail "curl failed: $(printf '%s' "$response" | head -c 400)"

status=$(printf '%s' "$response" | tail -n1)
body=$(printf '%s' "$response" | sed '$d')

case "$status" in
  200|201) ;;
  *) fail "HTTP $status from Modrinth: $(printf '%s' "$body" | head -c 900)" ;;
esac

published=$(printf '%s' "$body" | python3 -c '
import json, sys
try:
    v = json.load(sys.stdin)
except Exception:
    print("unparsable response"); raise SystemExit(0)
print(v.get("version_number"), "id", v.get("id"), "files", [f.get("filename") for f in v.get("files", [])])
' 2>/dev/null) || published="unparsable response"

summary "OK ${version_number}: ${published}"
