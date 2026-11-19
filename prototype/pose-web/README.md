# 동작 실험 페이지 (0-a단계)

녹화한 운동 영상 속 동작을 위드무브 사람형 캐릭터로 바꿔 보여주는 웹 실험이에요.

- 자세 인식: MediaPipe Pose Landmarker (lite), 3D 월드 좌표 사용
- 전송 형식 실험: 몸 부위 14곳의 방향 + 얼굴 방향만 사용 (프레임당 약 50바이트)
- 스쿼트 횟수: 무릎 각도 115° 아래로 내려갔다가 155° 위로 올라오면 1회

## 로컬 실행

용량이 커서 실행 파일과 모델은 저장소에 넣지 않았어요. 아래처럼 받은 뒤 실행하세요.

```bash
cd prototype/pose-web
npm pack @mediapipe/tasks-vision@0.10.14 && tar xzf mediapipe-tasks-vision-0.10.14.tgz
mkdir -p wasm models && cp package/wasm/vision_wasm_internal.* wasm/
curl -L -o models/pose_landmarker_lite.model.wasm \
  https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task
python3 -m http.server 8000
```
