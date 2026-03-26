---
description: "Use when implementing or discussing face recognition, facial expression mapping, avatar control, camera pipeline, AR, VRM, tracking, or low-latency realtime rendering features in VtuberCamera_KMP_ver. Keeps proposals aligned with the product goal of controlling an avatar from detected facial movement and expression."
name: "Avatar Control Domain Context"
---
# Avatar Control Domain Context

- Treat this app as a face-recognition-driven avatar control product, not as a generic camera sample.
- When proposing architecture, APIs, or UX, prioritize the end-to-end flow: camera input -> face detection or tracking -> avatar state update -> real-time visual feedback.
- Prefer designs that keep tracking state, avatar state, and rendering responsibilities explicit and easy to evolve across Android and iOS.
- Favor low-latency data flow over convenience abstractions when the tradeoff affects tracking responsiveness, expression follow-through, or frame-to-frame stability.
- Make facial expression mapping explicit: define how detected facial signals map into avatar blendshapes, expressions, pose offsets, or animation parameters instead of leaving that translation implicit in rendering code.
- When discussing AR, VRM, Filament, or rendering work, connect the proposal back to how facial data will drive avatar pose, expression, or orientation.
- Call out where smoothing, filtering, or prediction is applied, and explain the latency vs stability tradeoff.
- If a suggestion only improves camera behavior but does not support avatar control requirements, call out that tradeoff explicitly.
- Keep future extensibility in mind for expression control, pose mapping, calibration, and low-latency real-time updates.