#!/usr/bin/env bash
# Brief task 1: the app is silent and works fully offline.
# Fails if any source file uses an audio, vibration, haptic or sound-effect
# API, if a manifest declares VIBRATE or ACCESS_NETWORK_STATE, or if network
# code appears outside the reviewed files of update/. A reviewed exception is marked on the
# same line with:   // silence-check: allow (<reason>)
# INTERNET is allowed only when the app manifest declares it on a reviewed line
# (updates and optional downloads, both on the user's tap); network APIs only in files whose first line has
# "network-check: allow-file (<reason>)".
set -euo pipefail
cd "$(dirname "$0")/.."

patterns=(
  'android\.media\.'            'MediaPlayer'          'SoundPool'           'AudioManager'
  'AudioTrack'                  'AudioFocus'           'ToneGenerator'       'Ringtone'
  'Vibrator'                    'VibrationEffect'      'VibratorManager'     'performHapticFeedback'
  'HapticFeedbackConstants'     'playSoundEffect'      'SoundEffectConstants' 'TextToSpeech'
  'setSound\('                  'NotificationCompat'   'androidx\.media'     'ExoPlayer'
  'HapticFeedback'              'isSoundEffectsEnabled' 'isHapticFeedbackEnabled'
)
regex=$(IFS='|'; echo "${patterns[*]}")
status=0

# A file may be exempted as a whole only with a first line containing
# "silence-check: allow-file (<reason>)"; such files are listed on every run.
allowed=$(grep -rlE --include='*.kt' --include='*.java' 'silence-check: allow-file' core/src data/src app/src 2>/dev/null \
          | while read -r f; do head -n 1 "$f" | grep -q 'silence-check: allow-file' && echo "$f"; done || true)
hits=$(grep -rnE "$regex" --include='*.kt' --include='*.java' --include='*.xml' \
        core/src data/src app/src 2>/dev/null | grep -v 'silence-check: allow' || true)
for f in $allowed; do hits=$(echo "$hits" | grep -v "^$f:" || true); done
if [[ -n "$hits" ]]; then
  echo "silence check: audio/vibration/sound API found (mark a reviewed line with 'silence-check: allow'):"
  echo "$hits"
  status=1
fi

# Source manifests, plus the merged manifests of previous builds (catches a
# permission added by a library).
manifests=(core/src data/src app/src app/build/intermediates/merged_manifest* app/build/intermediates/merged_manifests*)
perm=$(grep -rnE 'android\.permission\.(VIBRATE|ACCESS_NETWORK_STATE)' --include='AndroidManifest.xml' \
        "${manifests[@]}" 2>/dev/null || true)
# INTERNET: only the reviewed line in the app manifest (update check); never in a library or another module.
if ! grep -qE 'android\.permission\.INTERNET.*silence-check: allow' app/src/main/AndroidManifest.xml; then
  perm+=$(grep -rnE 'android\.permission\.INTERNET' --include='AndroidManifest.xml' "${manifests[@]}" 2>/dev/null || true)
fi
perm+=$(grep -rnE 'android\.permission\.INTERNET' --include='AndroidManifest.xml' core/src data/src 2>/dev/null || true)
if [[ -n "$perm" ]]; then
  echo "silence check: forbidden permission in manifest:"
  echo "$perm"
  status=1
fi

# Network code only in the reviewed files of update/ (update check, in-app update, optional downloads).
net_allowed=$(grep -rlE --include='*.kt' --include='*.java' 'network-check: allow-file' core/src data/src app/src/main 2>/dev/null \
          | while read -r f; do head -n 1 "$f" | grep -q 'network-check: allow-file' && echo "$f"; done || true)
net=$(grep -rnE 'HttpURLConnection|HttpsURLConnection|java\.net\.|okhttp|retrofit|WebView|DownloadManager|ktor' \
        --include='*.kt' --include='*.java' core/src data/src app/src/main 2>/dev/null || true)
for f in $net_allowed; do net=$(echo "$net" | grep -v "^$f:" || true); done
if [[ -n "$net" ]]; then
  echo "silence check: network code outside the reviewed files of update/:"
  echo "$net"
  status=1
fi

if [[ $status -eq 0 ]]; then
  echo "silence check: OK (no audio, vibration, sound-effect API; network only in the reviewed update and download files; no VIBRATE permission)"
  for f in $net_allowed; do echo "  reviewed network use: $f — $(head -n 1 "$f" | sed 's/.*allow-file *//')"; done
  for f in $allowed; do echo "  reviewed exception: $f — $(head -n 1 "$f" | sed 's/.*allow-file *//')"; done
fi
exit $status
