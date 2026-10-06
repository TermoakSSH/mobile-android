#!/usr/bin/env bash
# Builds and publishes the Android app without GitHub Actions, with the
# version in gradle.properties (termoakVersion), as the android-vX.Y.Z
# release: APKs signed with your keystore, one per ABI plus a universal one
# with both (Termoak-android-vX.Y.Z-arm64-v8a.apk, ...-armeabi-v7a.apk and
# ...-universal.apk). x86_64 (emulator) only goes into debug builds.
#
#   scripts/release-local.sh status
#   scripts/release-local.sh version android [X.Y.Z]
#   scripts/release-local.sh build android
#   scripts/release-local.sh publish android
#   scripts/release-local.sh android-keystore
#
# build compiles the engine (termoak-ffi, from the core/ submodule, with
# core's size-optimized `mobile` profile) and the APKs in the
# core/scripts/android-builder.Dockerfile image: no Android Studio needed.
# The cargo registry and the Gradle cache are kept in target/android.
#
# publish creates the <component>-vX.Y.Z release with the contents of
# dist/<component>/, using the GitHub API (curl, no `gh`). It is created as a
# draft and published once all files are uploaded. If the release already
# exists, the files are added to it.
#
# android-keystore creates the keystore used to sign the APK, in
# ~/.config/termoak/android/ (with its password in keystore.env). Keep a copy
# off this machine: without it, updates cannot be installed over the app.
#
# Variables:
#   GITHUB_TOKEN  GitHub token (publish and download). If unset, it is read
#                 from ~/.config/termoak/github-token. Fine-grained, with access to
#                 the TermoakSSH repositories, Contents: Read and write (and
#                 Actions: Read for download)
#   REPO          owner/repository (default: TermoakSSH/mobile-android)
#   COMMIT        commit to tag (default: HEAD)
#   VERSION       version for download and publish (default: the manifest's)
#   TERMOAK_OFFICIAL_SERVER
#                 official server of the build (default https://termoak.com),
#                 e.g. https://next.termoak.com for test builds: the engine's
#                 officialServerUrl() and BuildConfig.DEFAULT_SERVER
#
# Compatible with macOS's bash 3.2.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

die() { echo "error: $*" >&2; exit 1; }
say() { printf '\n==> %s\n' "$*"; }

COMPONENTS="android"

# --- components ---------------------------------------------------------------

manifest_of() { # component
  case "$1" in
    android) echo gradle.properties ;;
    *) die "unknown component: ${1:-} (android)" ;;
  esac
}

title_of() { echo Android; }

# What the app is built from (for `status`): core is the submodule.
paths_of() { echo "app gradle build.gradle.kts settings.gradle.kts gradle.properties core"; }

version_of() { # component
  local v
  v="$(sed -n 's/^termoakVersion=\(.*\)$/\1/p' gradle.properties | head -1)"
  [[ -n "$v" ]] || die "gradle.properties has no termoakVersion=X.Y.Z line"
  echo "$v"
}

# A component's latest published tag.
last_tag_of() { git tag -l "$1-v*" --sort=-v:refname | head -1; }

# Component and version from the command line. In build, `version` and `tag`
# always come from the manifest; in download and publish they can be changed
# with VERSION (e.g. for binaries from an earlier release.yml run).
select_component() { # component command
  component="${1:-}"
  [[ -n "$component" ]] || die "missing component: android"
  manifest_of "$component" >/dev/null
  version="$(version_of "$component")"
  if [[ -n "${VERSION:-}" && "$2" != build ]]; then
    version="${VERSION#v}"
  fi
  tag="$component-v$version"
  dist="$root/dist/$component"
}

# --- status and version -------------------------------------------------------

cmd_status() {
  git fetch -q --tags origin 2>/dev/null || true
  printf '%-9s %-9s %-16s %s\n' component version 'latest tag' 'commits since'
  local c v last n note
  for c in $COMPONENTS; do
    v="$(version_of "$c")"
    last="$(last_tag_of "$c")"
    note=""
    if [[ -z "$last" ]]; then
      last="-"
      n="$(git rev-list --count HEAD)"
    else
      # shellcheck disable=SC2046
      n="$(git rev-list --count "$last..HEAD" -- $(paths_of "$c"))"
    fi
    if [[ "$n" != 0 ]] && git rev-parse -q --verify "refs/tags/$c-v$v" >/dev/null; then
      note="  (v$v already published: bump the version before publishing)"
    fi
    printf '%-9s %-9s %-16s %s%s\n' "$c" "$v" "$last" "$n" "$note"
  done
}

cmd_version() { # component [X.Y.Z]
  select_component "${1:-}" version
  local new="${2:-}" file
  if [[ -z "$new" ]]; then
    echo "$version"
    return
  fi
  new="${new#v}"
  [[ "$new" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] || die "\"$new\" is not an X.Y.Z version"
  [[ "$new" != "$version" ]] || die "$component is already at $new"
  file="$(manifest_of "$component")"
  awk -v v="$new" '/^termoakVersion=/ { print "termoakVersion=" v; next } { print }' \
    "$file" >"$file.tmp" && mv "$file.tmp" "$file"
  say "$component: $version → $new ($file)"
}

# --- android ------------------------------------------------------------------

android_dir() { echo "${XDG_CONFIG_HOME:-$HOME/.config}/termoak/android"; }

cmd_android_keystore() {
  local dir pass
  dir="$(android_dir)"
  if [[ -f "$dir/keystore.jks" ]]; then
    say "There is already a keystore in $dir: leaving it alone (without it the installed app cannot be updated)"
    return
  fi
  command -v docker >/dev/null || die "Docker is missing"
  build_android_image
  install -d -m 700 "$dir"
  pass="$(head -c 32 /dev/urandom | base64 | tr -d '/+=' | cut -c1-32)"
  docker run --rm -v "$dir:/ks" termoak-android-builder \
    keytool -genkeypair -keystore /ks/keystore.jks -storetype PKCS12 -alias termoak \
    -keyalg RSA -keysize 4096 -validity 36500 -storepass "$pass" \
    -dname "CN=Termoak, O=Ohz Digital SL" >/dev/null
  (
    umask 077
    printf 'TERMOAK_ANDROID_KEYSTORE_PASSWORD=%s\nTERMOAK_ANDROID_KEY_ALIAS=termoak\n' "$pass" >"$dir/keystore.env"
  )
  chmod 600 "$dir/keystore.jks"
  say "Keystore created in $dir. Keep a copy of keystore.jks and keystore.env off this machine."
}

build_android_image() {
  [[ -f core/Cargo.toml ]] || die "the core submodule is missing: git submodule update --init"
  # Built only when missing (it is ~7 GB and slow to rebuild without the Docker
  # cache); REBUILD_IMAGE=1 rebuilds it, e.g. after the Dockerfile changes.
  if [[ "${REBUILD_IMAGE:-0}" != 1 ]] && docker image inspect termoak-android-builder >/dev/null 2>&1; then
    return
  fi
  say "Android build image"
  docker build -q -t termoak-android-builder - <core/scripts/android-builder.Dockerfile >/dev/null
}

build_android() {
  # The same ABIs as releaseAbis in app/build.gradle.kts.
  local dir abis=(arm64-v8a armeabi-v7a) targets=() abi apk
  dir="$(android_dir)"
  [[ -f "$dir/keystore.jks" && -f "$dir/keystore.env" ]] ||
    die "the Android keystore is missing: scripts/release-local.sh android-keystore"
  command -v docker >/dev/null || die "Docker is missing"
  build_android_image
  for abi in "${abis[@]}"; do targets+=(-t "$abi"); done
  say "Android $version: engine (${abis[*]}) and APKs"
  # The cargo registry and the Gradle cache are kept in target/android.
  # The official server is compiled into the engine (servers::OFFICIAL_SERVER)
  # and into the app (BuildConfig.DEFAULT_SERVER, the fallback).
  local official=() server_prop=""
  if [[ -n "${TERMOAK_OFFICIAL_SERVER:-}" ]]; then
    official=(-e "TERMOAK_OFFICIAL_SERVER=$TERMOAK_OFFICIAL_SERVER")
    server_prop="-PtermoakServer=$TERMOAK_OFFICIAL_SERVER"
  fi
  docker run --rm -v "$root:/src" -v "$dir:/ks:ro" -w /src \
    -e CARGO_HOME=/src/target/android/cargo-home -e CARGO_TARGET_DIR=/src/target/android \
    -e GRADLE_USER_HOME=/src/target/android/gradle ${official[@]+"${official[@]}"} \
    --env-file "$dir/keystore.env" -e TERMOAK_ANDROID_KEYSTORE=/ks/keystore.jks \
    termoak-android-builder bash -c "set -e
      export PATH=/usr/local/cargo/bin:\$PATH
      rm -rf core/bindings/kotlin/src/main/jniLibs
      cd core
      cargo ndk ${targets[*]} --platform 26 -o bindings/kotlin/src/main/jniLibs \
        build -p termoak-ffi --lib --profile mobile --locked
      cd ..
      ./gradlew --no-daemon -q clean assembleRelease $server_prop"
  # Gradle's ABI splits (app/build.gradle.kts): app-<abi>-release.apk and
  # app-universal-release.apk.
  for apk in "${abis[@]}" universal; do
    cp "app/build/outputs/apk/release/app-$apk-release.apk" "$dist/Termoak-android-v$version-$apk.apk"
  done
}

cmd_build() { # android
  select_component "${1:-}" build
  rm -rf "$dist"
  mkdir -p "$dist"
  echo "$tag" >"$dist/.version"
  build_android
  say "Done: $tag in dist/android/"
  ls -l "$dist"
}

# --- GitHub API (curl) --------------------------------------------------------

github_setup() {
  command -v curl >/dev/null || die "curl is missing"
  command -v python3 >/dev/null || die "python3 is missing (needed to read GitHub's JSON responses)"
  local file="${XDG_CONFIG_HOME:-$HOME/.config}/termoak/github-token"
  github_token="${GITHUB_TOKEN:-}"
  if [[ -z "$github_token" && -f "$file" ]]; then
    github_token="$(tr -d '[:space:]' <"$file")"
  fi
  # Otherwise, the login of the GitHub CLI if it is installed (`gh auth login`).
  if [[ -z "$github_token" ]] && command -v gh >/dev/null; then
    github_token="$(gh auth token 2>/dev/null || true)"
  fi
  [[ -n "$github_token" ]] ||
    die "the GitHub token is missing: GITHUB_TOKEN, $file or \`gh auth login\`"
  # This repository; REPO=owner/repository publishes somewhere else (a fork).
  repo="${REPO:-TermoakSSH/mobile-android}"
  [[ "$repo" == */* ]] || die "cannot tell which repository this is: set REPO=owner/repository"
}

# Calls the API. Leaves the response in api_body and the HTTP status in api_status.
github() { # method path-or-url [curl arguments...]
  local method="$1" url="$2" out
  shift 2
  [[ "$url" == https://* ]] || url="https://api.github.com$url"
  # -L: GitHub redirects downloads to its storage (curl does not forward the
  # token to another domain).
  out="$(curl -sS -L -X "$method" -w '\n%{http_code}' \
    -H "Authorization: Bearer $github_token" -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" "$@" "$url")" || die "could not connect to GitHub"
  api_status="${out##*$'\n'}"
  api_body="${out%$'\n'*}"
}
# Like github(), but stops if GitHub answers with an error.
github_ok() { # what-we-were-doing method path [curl arguments...]
  local what="$1"
  shift
  github "$@"
  if [[ "$api_status" != 2* ]]; then
    printf '%s\n' "$api_body" >&2
    die "GitHub answered $api_status when trying to $what"
  fi
}
# Python expression over the JSON response (in `d`).
json() {
  printf '%s' "$api_body" | python3 -c "import json, sys; d = json.load(sys.stdin); v = $1; print('' if v is None else v)"
}
# JSON object from key value pairs. `draft` is a boolean; everything else is
# a string (make_latest too: the API expects "true" or "false").
json_object() {
  python3 -c '
import json, sys
a = sys.argv[1:]
print(json.dumps({k: (v == "true") if k == "draft" else v for k, v in zip(a[::2], a[1::2])}))' "$@"
}

# --- publish ------------------------------------------------------------------

cmd_publish() { # component
  select_component "${1:-}" publish
  local commit existing=0 file files=() prev latest id asset_id
  [[ -z "${2:-}" ]] || die "unknown option: $2"
  [[ "$(cat "$dist/.version" 2>/dev/null)" == "$tag" ]] ||
    die "dist/$component/ does not hold $tag: run scripts/release-local.sh build $component first"
  github_setup

  github GET "/repos/$repo/releases/tags/$tag"
  if [[ "$api_status" == 200 ]]; then
    existing=1
    id="$(json 'd["id"]')"
    say "Release $tag already exists: adding the files"
  elif [[ "$api_status" != 404 ]]; then
    printf '%s\n' "$api_body" >&2
    die "GitHub answered $api_status when looking up release $tag (does the token have access to the repository?)"
  else
    commit="${COMMIT:-$(git rev-parse HEAD)}"
    git fetch -q --tags origin 2>/dev/null || true
    [[ -n "$(git branch -r --contains "$commit" 2>/dev/null)" ]] ||
      die "commit $commit is not on GitHub: push it first (git push)"
  fi


  for file in "$dist"/*; do
    [[ -f "$file" ]] && files+=("$file")
  done
  [[ ${#files[@]} -gt 0 ]] || die "nothing to publish in dist/$component/"

  if [[ $existing == 0 ]]; then
    # An earlier attempt that failed halfway leaves a draft: reuse it.
    github_ok "look for drafts" GET "/repos/$repo/releases?per_page=100"
    id="$(json "next((r['id'] for r in d if r['draft'] and r['tag_name'] == '$tag'), None)")"
  fi
  if [[ $existing == 0 && -n "$id" ]]; then
    say "Found a draft of $tag from an earlier attempt: completing it"
  elif [[ $existing == 0 ]]; then
    # Notes since the previous version of this same component.
    prev="$(last_tag_of "$component")"
    if [[ -n "$prev" && "$prev" != "$tag" ]]; then
      github_ok "generate the notes" POST "/repos/$repo/releases/generate-notes" \
        -d "$(json_object tag_name "$tag" target_commitish "$commit" previous_tag_name "$prev")"
    else
      github_ok "generate the notes" POST "/repos/$repo/releases/generate-notes" \
        -d "$(json_object tag_name "$tag" target_commitish "$commit")"
    fi
    say "Creating release $tag in $repo ($commit) as a draft"
    github_ok "create the release" POST "/repos/$repo/releases" \
      -d "$(json_object tag_name "$tag" target_commitish "$commit" \
        name "$(title_of "$component") $version" body "$(json 'd["body"]')" draft true)"
    id="$(json 'd["id"]')"
  fi

  for file in ${files[@]+"${files[@]}"}; do
    # If one with that name already exists (existing release), it is replaced.
    github_ok "read the release" GET "/repos/$repo/releases/$id"
    asset_id="$(json "next((a['id'] for a in d['assets'] if a['name'] == '$(basename "$file")'), None)")"
    if [[ -n "$asset_id" ]]; then
      github_ok "delete $(basename "$file")" DELETE "/repos/$repo/releases/assets/$asset_id"
    fi
    echo "  uploading $(basename "$file")"
    github_ok "upload $(basename "$file")" POST \
      "https://uploads.github.com/repos/$repo/releases/$id/assets?name=$(basename "$file")" \
      -H "Content-Type: application/octet-stream" --data-binary "@$file"
  done

  if [[ $existing == 0 ]]; then
    latest=true
    github_ok "publish the release" PATCH "/repos/$repo/releases/$id" \
      -d "$(json_object draft false make_latest "$latest")"
  fi
  say "Published $tag: https://github.com/$repo/releases/tag/$tag"
}

# With exit in every branch bash does not read this file again: it can be
# edited (or git pulled) while it builds.
case "${1:-}" in
  status) cmd_status; exit ;;
  version) shift; cmd_version "$@"; exit ;;
  build) shift; cmd_build "$@"; exit ;;
  publish) shift; cmd_publish "$@"; exit ;;
  android-keystore) cmd_android_keystore; exit ;;
  *) awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "$0"; exit 1 ;;
esac
