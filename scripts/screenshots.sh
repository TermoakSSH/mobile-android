#!/usr/bin/env bash
# Regenerates the store listing's images: the screenshots (phone, 7" and
# 10" tablets, in English and Spanish), the feature graphic and the icon, in
# the fastlane layout that Google Play and F-Droid read:
#
#   fastlane/metadata/android/<en-US|es-ES>/images/
#     phoneScreenshots/  sevenInchScreenshots/  tenInchScreenshots/  (01_vault.png … 08_settings.png)
#     featureGraphic.png (1024×500)  icon.png (512×512)
#
#   scripts/screenshots.sh                  # everything
#   scripts/screenshots.sh --only 'phone-es'  # some of them ("device-lang-shot", a regex)
#   scripts/screenshots.sh --fdroid ../packages/fdroid/metadata/com.termoak
#                                           # also into an F-Droid metadata folder
#                                           # (screenshots, feature graphic and the texts)
#
# No emulator: the real app (AppRoot with its screens, components and theme)
# is drawn on the JVM by Robolectric with Android's own graphics, at each
# device's size and density (app/src/test/.../store/StoreScreenshotTest.kt,
# StoreGraphicsTest.kt). The engine (the Rust library) can't load on the
# JVM: its calls are answered by stand-ins with demo data (example.com,
# 10.0.x.x). It runs in the same Docker image as release-local.sh
# (termoak-android-builder, Gradle cache in target/android) and takes the
# build lock ($TERMOAK_BUILD_LOCK, by default ~/.termoak-build.lock when it
# exists) unless a caller already holds it.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

die() { echo "error: $*" >&2; exit 1; }

only=""
fdroid=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only) only="${2:-}"; shift 2 ;;
    --fdroid) fdroid="${2:-}"; [[ -n "$fdroid" ]] || die "--fdroid needs a folder"; shift 2 ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
done
[[ "$only" =~ ^[A-Za-z0-9|().*_-]*$ ]] || die "--only: letters, digits and | ( ) . * _ - only"

# ----- The build lock (one heavy build at a time on the build machine) -----

lock="${TERMOAK_BUILD_LOCK:-$HOME/.termoak-build.lock}"
held_by_caller() { # The lock is held by this process or one of its parents (e.g. `flock lock scripts/screenshots.sh`).
  [[ -r /proc/locks ]] || return 1
  local inode pids pid
  inode="$(stat -c %i "$lock" 2>/dev/null)" || return 1
  pids="$(awk -v ino=":$inode" '$0 ~ ino" " || $0 ~ ino"$" { print $5 }' /proc/locks)"
  pid=$$
  while [[ -n "$pid" && "$pid" != 0 ]]; do
    grep -qx "$pid" <<<"$pids" && return 0
    pid="$(awk '{ print $4 }' "/proc/$pid/stat" 2>/dev/null || true)"
  done
  return 1
}
if [[ -z "${TERMOAK_LOCKED:-}" && -e "$lock" ]] && command -v flock >/dev/null && ! held_by_caller; then
  echo "==> waiting for the build lock ($lock)"
  TERMOAK_LOCKED=1 exec flock "$lock" "$0" ${only:+--only "$only"} ${fdroid:+--fdroid "$fdroid"}
fi

# ----- Render -----

command -v docker >/dev/null || die "Docker is missing"
docker image inspect termoak-android-builder >/dev/null 2>&1 ||
  die "the termoak-android-builder image is missing: scripts/release-local.sh builds it"
rm -rf app/build/store
echo "==> rendering the store images (Robolectric)"
docker run --rm -v "$root:/src" -w /src -e GRADLE_USER_HOME=/src/target/android/gradle termoak-android-builder \
  bash -c "./gradlew --no-daemon -q :app:testDebugUnitTest --tests 'com.termoak.app.store.*' \
    -PtermoakStore=1 -PtermoakStoreOnly='$only'"

# ----- Copy into the fastlane layout -----

out="fastlane/metadata/android"
count=0
for lang in en-US es-ES; do
  src="app/build/store/$lang"
  [[ -d "$src" ]] || continue
  for dir in phoneScreenshots sevenInchScreenshots tenInchScreenshots; do
    [[ -d "$src/$dir" ]] || continue
    mkdir -p "$out/$lang/images/$dir"
    for png in "$src/$dir"/*.png; do
      cp "$png" "$out/$lang/images/$dir/"
      count=$((count + 1))
    done
  done
  [[ -f "$src/featureGraphic.png" ]] && cp "$src/featureGraphic.png" "$out/$lang/images/featureGraphic.png"
  [[ -f app/build/store/icon.png ]] && cp app/build/store/icon.png "$out/$lang/images/icon.png"
done
echo "==> $count screenshots in $out"

# ----- F-Droid metadata (screenshots, feature graphic and texts; its own icon stays) -----

if [[ -n "$fdroid" ]]; then
  [[ -d "$fdroid" ]] || die "no folder $fdroid"
  for lang in en-US es-ES; do
    [[ -d "$out/$lang" ]] || continue
    mkdir -p "$fdroid/$lang/images"
    for dir in phoneScreenshots sevenInchScreenshots tenInchScreenshots; do
      [[ -d "$out/$lang/images/$dir" ]] || continue
      rm -rf "$fdroid/$lang/images/$dir"
      cp -r "$out/$lang/images/$dir" "$fdroid/$lang/images/$dir"
    done
    cp "$out/$lang/images/featureGraphic.png" "$fdroid/$lang/images/" 2>/dev/null || true
    for txt in title.txt short_description.txt full_description.txt; do
      [[ -f "$out/$lang/$txt" ]] && cp "$out/$lang/$txt" "$fdroid/$lang/$txt"
    done
  done
  echo "==> copied to $fdroid"
fi
