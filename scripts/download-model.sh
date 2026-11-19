#!/usr/bin/env bash
# 기기 안에서 동작하는 한국어 음성 인식 모델(Vosk, Apache-2.0)을 앱 assets 로 내려받는다.
set -euo pipefail
NAME=vosk-model-small-ko-0.22
DEST="$(dirname "$0")/../app/src/main/assets/model-ko"
ASSETS="$(dirname "$0")/../app/src/main/assets"

# AI 소리 분류기 (구글 YAMNet, Apache-2.0): 잠꼬대와 코골이를 구분
if [ ! -f "$ASSETS/yamnet.tflite" ]; then
  mkdir -p "$ASSETS"
  curl -fsSL -o "$ASSETS/yamnet.tflite" \
    "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite"
fi

if [ -f "$DEST/conf/model.conf" ]; then echo "model already present"; exit 0; fi
TMP="$(mktemp -d)"
curl -fsSL -o "$TMP/model.zip" "https://alphacephei.com/vosk/models/$NAME.zip"
unzip -q "$TMP/model.zip" -d "$TMP"
mkdir -p "$DEST"
cp -r "$TMP/$NAME/." "$DEST/"
rm -rf "$TMP"
echo "model ready: $DEST"
