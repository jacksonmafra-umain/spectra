#!/usr/bin/env bash
#
# Micro-commits for the Spectra work (MockDeviceKit, active-device fix, UI
# redesign, custom-scheme registration, real frame rendering, audio, icon fixes,
# docs). Run from the repo root:
#
#     bash recommit-microcommits.sh
#
# It clears the stale sandbox lock files, undoes the throwaway "probe" commit,
# and re-commits everything in themed steps. Delete this script afterwards.
set -e

# 0. Clear stale locks left by the sandbox (harmless if they don't exist).
rm -f .git/index.lock .git/HEAD.lock .git/objects/maintenance.lock || true

# 1. Undo the throwaway probe commit, keeping its change in the working tree.
if git log -1 --pretty=%s | grep -q "probe commit capability"; then
  git reset HEAD~1
fi

git config user.email "jackson.mafra@umain.com"
git config user.name "Jackson Mafra"

commit () { git commit -q -m "$1"; echo "  ✓ $1"; }

# --- 1. build ---------------------------------------------------------------
git add .gitignore
commit "build: keep the downloaded Meta sample out of the repo"

# --- 2. library: common API -------------------------------------------------
git add demo/spectra/src/commonMain/kotlin/com/umain/spectra/core/MockDeviceKit.kt \
        demo/spectra/src/commonMain/kotlin/com/umain/spectra/core/Audio.kt \
        demo/spectra/src/commonMain/kotlin/com/umain/spectra/SpectraClient.kt \
        demo/spectra/src/commonMain/kotlin/com/umain/spectra/mock/MockSpectraClient.kt
commit "feat(library): add MockDeviceKit, SpectraAudio and hasActiveDevice capabilities"

# --- 3. library: iOS backend ------------------------------------------------
git add demo/spectra/src/iosMain
commit "feat(library): iOS backend — native bridge, active-device stream, MockDeviceKit, audio"

# --- 4. library: Android backend --------------------------------------------
git add demo/spectra/src/androidMain
commit "feat(library): Android audio backend (A2DP playback + HFP mic)"

# --- 5. library: version ----------------------------------------------------
git add demo/spectra/build.gradle.kts
commit "chore(library): bump Spectra to 0.2.0"

# --- 6. demo: shared UI -----------------------------------------------------
git add demo/shared
commit "feat(demo): CameraAccess-style UI, real frame rendering, audio controls"

# --- 7. iOS app -------------------------------------------------------------
git add demo/iosApp
commit "feat(ios): Swift bridge (camera, MockDeviceKit, audio) + custom-scheme registration"

# --- 8. Android app + icons -------------------------------------------------
git add -A demo/androidApp demo/gradlew.bat
commit "fix(android): RECORD_AUDIO, fix duplicate/ malformed launcher-icon resources"

# --- 9. docs ----------------------------------------------------------------
git add README.md demo/README.md docs
commit "docs: v0.2.0 — MockDeviceKit, active-device, custom-scheme registration, audio"

# --- 10. anything left over -------------------------------------------------
if [ -n "$(git status --porcelain)" ]; then
  git add -A
  commit "chore: remaining demo/project assets"
fi

echo
echo "Done. Review with:  git log --oneline -12"
echo "Then delete this script:  rm recommit-microcommits.sh"
