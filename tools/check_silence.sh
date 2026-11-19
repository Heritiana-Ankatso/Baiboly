#!/usr/bin/env bash
# Brief task 1: the app is silent and offline by default.
# Fails if any source file uses an audio, vibration, haptic or sound-effect
# API, or if a manifest declares INTERNET or VIBRATE. A reviewed exception is
# marked on the same line with:   // silence-check: allow (<reason>)
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

hits=$(grep -rnE "$regex" --include='*.kt' --include='*.java' --include='*.xml' \
        core/src data/src app/src 2>/dev/null | grep -v 'silence-check: allow' || true)
if [[ -n "$hits" ]]; then
  echo "silence check: audio/vibration/sound API found (mark a reviewed line with 'silence-check: allow'):"
  echo "$hits"
  status=1
fi

# Source manifests, plus the merged manifests of previous builds (catches a
# permission added by a library).
perm=$(grep -rnE 'android\.permission\.(INTERNET|VIBRATE|ACCESS_NETWORK_STATE)' --include='AndroidManifest.xml' \
        core/src data/src app/src app/build/intermediates/merged_manifest* app/build/intermediates/merged_manifests* \
        2>/dev/null || true)
if [[ -n "$perm" ]]; then
  echo "silence check: forbidden permission in manifest:"
  echo "$perm"
  status=1
fi

[[ $status -eq 0 ]] && echo "silence check: OK (no audio, vibration, sound-effect API; no INTERNET/VIBRATE permission)"
exit $status
