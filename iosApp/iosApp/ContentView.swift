import SwiftUI
@preconcurrency import AVFoundation
import ARKit
import ComposeApp
import UniformTypeIdentifiers
import UIKit
import SceneKit


private enum AppColors {
    static let background = Color.black
    static let textSecondary = Color.secondary
    static let avatarFallbackFill = Color.white.opacity(0.12)
    static let avatarBodyFallbackFill = Color.white.opacity(0.24)
    static let avatarFallbackText = Color.white
}

struct ContentView: View {
    @StateObject private var viewModel = IOSCameraViewModel()
    @State private var isFileImporterPresented = false

    var body: some View {
        ZStack {
            if viewModel.isAuthorized {
                if viewModel.isUsingARFaceTracking {
                    ARFaceTrackingPreviewView(onFrameChanged: viewModel.handleFaceTrackingFrameChanged)
                        .ignoresSafeArea()
                } else {
                    CameraPreviewView(avCaptureSession: viewModel.avCaptureSession)
                        .ignoresSafeArea()
                }
            } else {
                PermissionPromptView(
                    status: viewModel.authorizationStatus,
                    texts: viewModel.permissionTexts,
                    onPrimaryAction: viewModel.handlePrimaryAction,
                )
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    Spacer()
                    Button("ファイルを開く") {
                        isFileImporterPresented = true
                    }
                    .buttonStyle(.borderedProminent)

                    if viewModel.isAuthorized, viewModel.canSwitchCamera, let texts = viewModel.permissionTexts {
                        Button(texts.switchCameraButtonTitle, action: viewModel.switchCamera)
                            .buttonStyle(.borderedProminent)
                    }
                }

                if viewModel.isAuthorized {
                    FaceTrackingDebugOverlay(
                        statusText: viewModel.faceTrackingStatusText,
                        frame: viewModel.faceTrackingFrame
                    )
                }

                Spacer()
            }
            .padding(.top, 16)
            .padding(.horizontal, 16)

            if let avatarPreview = viewModel.avatarPreview {
                VStack {
                    Spacer()
                    IOSAvatarBodyView(avatarPreview: avatarPreview)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 32)
                }

                VStack {
                    Spacer()
                    IOSAvatarPreviewCard(avatarPreview: avatarPreview)
                        .padding(.leading, 16)
                        .padding(.bottom, 24)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .background(AppColors.background.ignoresSafeArea())
        .fileImporter(
            isPresented: $isFileImporterPresented,
            allowedContentTypes: [.item],
            allowsMultipleSelection: false,
            onCompletion: viewModel.handleFileImport
        )
        .alert("ファイルエラー", isPresented: Binding(
            get: { viewModel.isFileErrorPresented },
            set: { isPresented in
                if !isPresented {
                    viewModel.dismissFileError()
                }
            }
        )) {
            Button("閉じる", role: .cancel) {
                viewModel.dismissFileError()
            }
        } message: {
            Text(viewModel.fileErrorMessage ?? "")
        }
        .onAppear {
            viewModel.refreshAuthorization()
        }
        .onDisappear {
            viewModel.stopSession()
        }
    }
}

private struct PermissionPromptView: View {
    let status: AVAuthorizationStatus
    let texts: CameraPermissionTexts?
    let onPrimaryAction: () -> Void

    var body: some View {
        if let texts {
            VStack(spacing: 16) {
                Text(texts.requiredMessage)
                    .font(.title3.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text(descriptionText(for: texts))
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(AppColors.textSecondary)
                Button(primaryButtonTitle(for: texts), action: onPrimaryAction)
                    .buttonStyle(.borderedProminent)
            }
            .padding(.horizontal, 24)
        } else {
            ProgressView()
        }
    }

    private func descriptionText(for texts: CameraPermissionTexts) -> String {
        switch status {
        case .denied, .restricted:
            return texts.deniedDescription
        case .notDetermined:
            return texts.notDeterminedDescription
        default:
            return texts.checkingDescription
        }
    }

    private func primaryButtonTitle(for texts: CameraPermissionTexts) -> String {
        switch status {
        case .denied, .restricted:
            return texts.openSettingsButtonTitle
        default:
            return texts.launchCameraButtonTitle
        }
    }
}

private struct CameraPreviewView: UIViewRepresentable {
    let avCaptureSession: AVCaptureSession

    func makeUIView(context: Context) -> PreviewContainerView {
        let view = PreviewContainerView()
        view.previewLayer.videoGravity = .resizeAspectFill
        view.previewLayer.session = avCaptureSession
        return view
    }

    func updateUIView(_ uiView: PreviewContainerView, context: Context) {
        uiView.previewLayer.session = avCaptureSession
    }
}

private final class PreviewContainerView: UIView {
    override class var layerClass: AnyClass {
        AVCaptureVideoPreviewLayer.self
    }

    var previewLayer: AVCaptureVideoPreviewLayer {
        layer as! AVCaptureVideoPreviewLayer
    }
}

private struct ARFaceTrackingPreviewView: UIViewRepresentable {
    let onFrameChanged: (IOSNormalizedFaceFrame?) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onFrameChanged: onFrameChanged)
    }

    func makeUIView(context: Context) -> ARSCNView {
        let view = ARSCNView(frame: .zero)
        view.scene = SCNScene()
        view.automaticallyUpdatesLighting = false
        view.preferredFramesPerSecond = 60
        view.contentMode = .scaleAspectFill
        view.session.delegate = context.coordinator
        context.coordinator.startSession(using: view.session)
        return view
    }

    func updateUIView(_ uiView: ARSCNView, context: Context) {
        context.coordinator.onFrameChanged = onFrameChanged
        context.coordinator.resumeIfNeeded(using: uiView.session)
    }

    static func dismantleUIView(_ uiView: ARSCNView, coordinator: Coordinator) {
        coordinator.stopSession(using: uiView.session)
    }

    final class Coordinator: NSObject, ARSessionDelegate {
        var onFrameChanged: (IOSNormalizedFaceFrame?) -> Void

        private let poseNode = SCNNode()
        private var previousFrame: IOSNormalizedFaceFrame?
        private var isRunning = false

        init(onFrameChanged: @escaping (IOSNormalizedFaceFrame?) -> Void) {
            self.onFrameChanged = onFrameChanged
        }

        func startSession(using session: ARSession) {
            guard ARFaceTrackingConfiguration.isSupported else {
                publish(nil)
                return
            }

            let configuration = ARFaceTrackingConfiguration()
            configuration.isLightEstimationEnabled = false
            session.run(configuration, options: [.resetTracking, .removeExistingAnchors])
            isRunning = true
        }

        func resumeIfNeeded(using session: ARSession) {
            guard !isRunning else { return }
            startSession(using: session)
        }

        func stopSession(using session: ARSession) {
            session.pause()
            isRunning = false
            previousFrame = nil
            publish(nil)
        }

        func session(_ session: ARSession, didUpdate anchors: [ARAnchor]) {
            guard let faceAnchor = anchors.compactMap({ $0 as? ARFaceAnchor }).first else {
                previousFrame = nil
                publish(nil)
                return
            }

            let pose = headPoseDegrees(from: faceAnchor)
            let currentFrame = IOSNormalizedFaceFrame(
                timestampMillis: Int64((session.currentFrame?.timestamp ?? 0) * 1000),
                trackingConfidence: trackingConfidence(for: session),
                headYawDegrees: pose.yaw,
                headPitchDegrees: pose.pitch,
                headRollDegrees: pose.roll,
                leftEyeBlink: blendShapeValue(.eyeBlinkLeft, from: faceAnchor),
                rightEyeBlink: blendShapeValue(.eyeBlinkRight, from: faceAnchor),
                jawOpen: blendShapeValue(.jawOpen, from: faceAnchor),
                mouthSmile: average(
                    blendShapeValue(.mouthSmileLeft, from: faceAnchor),
                    blendShapeValue(.mouthSmileRight, from: faceAnchor)
                )
            )

            let smoothedFrame = smooth(previousFrame: previousFrame, currentFrame: currentFrame)
            previousFrame = smoothedFrame
            publish(smoothedFrame)
        }

        func session(_ session: ARSession, didFailWithError error: Error) {
            previousFrame = nil
            publish(nil)
        }

        func sessionWasInterrupted(_ session: ARSession) {
            previousFrame = nil
            publish(nil)
        }

        func sessionInterruptionEnded(_ session: ARSession) {
            startSession(using: session)
        }

        private func publish(_ frame: IOSNormalizedFaceFrame?) {
            DispatchQueue.main.async {
                self.onFrameChanged(frame)
            }
        }

        private func trackingConfidence(for session: ARSession) -> Float {
            guard let camera = session.currentFrame?.camera else {
                return 0
            }

            switch camera.trackingState {
            case .normal:
                return 1
            case .limited:
                return 0.55
            case .notAvailable:
                return 0
            }
        }

        private func headPoseDegrees(from faceAnchor: ARFaceAnchor) -> (yaw: Float, pitch: Float, roll: Float) {
            poseNode.simdTransform = faceAnchor.transform
            let eulerAngles = poseNode.simdEulerAngles
            return (
                yaw: (-eulerAngles.y).degrees,
                pitch: eulerAngles.x.degrees,
                roll: (-eulerAngles.z).degrees
            )
        }

        private func blendShapeValue(
            _ key: ARFaceAnchor.BlendShapeLocation,
            from faceAnchor: ARFaceAnchor
        ) -> Float {
            (faceAnchor.blendShapes[key]?.floatValue ?? 0).clamped(to: 0...1)
        }

        private func smooth(
            previousFrame: IOSNormalizedFaceFrame?,
            currentFrame: IOSNormalizedFaceFrame
        ) -> IOSNormalizedFaceFrame {
            guard let previousFrame else {
                return currentFrame
            }

            return IOSNormalizedFaceFrame(
                timestampMillis: currentFrame.timestampMillis,
                trackingConfidence: currentFrame.trackingConfidence,
                headYawDegrees: lerp(previousFrame.headYawDegrees, currentFrame.headYawDegrees, alpha: 0.45),
                headPitchDegrees: lerp(previousFrame.headPitchDegrees, currentFrame.headPitchDegrees, alpha: 0.45),
                headRollDegrees: lerp(previousFrame.headRollDegrees, currentFrame.headRollDegrees, alpha: 0.4),
                leftEyeBlink: smoothBlink(previous: previousFrame.leftEyeBlink, current: currentFrame.leftEyeBlink),
                rightEyeBlink: smoothBlink(previous: previousFrame.rightEyeBlink, current: currentFrame.rightEyeBlink),
                jawOpen: smoothJaw(previous: previousFrame.jawOpen, current: currentFrame.jawOpen),
                mouthSmile: lerp(previousFrame.mouthSmile, currentFrame.mouthSmile, alpha: 0.35)
            )
        }

        private func smoothBlink(previous: Float, current: Float) -> Float {
            let snapped: Float
            switch current {
            case 0.68...:
                snapped = 1
            case ...0.32:
                snapped = 0
            default:
                snapped = current
            }

            let alpha: Float = snapped > previous ? 0.55 : 0.28
            return lerp(previous, snapped, alpha: alpha).clamped(to: 0...1)
        }

        private func smoothJaw(previous: Float, current: Float) -> Float {
            let alpha: Float = current > previous ? 0.58 : 0.24
            return lerp(previous, current, alpha: alpha).clamped(to: 0...1)
        }

        private func lerp(_ start: Float, _ end: Float, alpha: Float) -> Float {
            start + (end - start) * alpha
        }

        private func average(_ left: Float, _ right: Float) -> Float {
            ((left + right) * 0.5).clamped(to: 0...1)
        }
    }
}

private struct FaceTrackingDebugOverlay: View {
    let statusText: String
    let frame: IOSNormalizedFaceFrame?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Face Tracking")
                .font(.headline)
            Text(statusText)
                .font(.subheadline)
                .foregroundStyle(AppColors.textSecondary)

            if let frame {
                FaceTrackingMetricRow(label: "Yaw", value: degreesLabel(frame.headYawDegrees))
                FaceTrackingMetricRow(label: "Pitch", value: degreesLabel(frame.headPitchDegrees))
                FaceTrackingMetricRow(label: "Roll", value: degreesLabel(frame.headRollDegrees))
                FaceTrackingMetricRow(label: "Blink L", value: percentLabel(frame.leftEyeBlink))
                FaceTrackingMetricRow(label: "Blink R", value: percentLabel(frame.rightEyeBlink))
                FaceTrackingMetricRow(label: "Jaw", value: percentLabel(frame.jawOpen))
                FaceTrackingMetricRow(label: "Smile", value: percentLabel(frame.mouthSmile))
                FaceTrackingMetricRow(label: "Confidence", value: percentLabel(frame.trackingConfidence))
            }
        }
        .padding(14)
        .frame(maxWidth: 220, alignment: .leading)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }

    private func degreesLabel(_ value: Float) -> String {
        "\(Int(value.rounded())) deg"
    }

    private func percentLabel(_ value: Float) -> String {
        "\(Int((value.clamped(to: 0...1) * 100).rounded()))%"
    }
}

private struct FaceTrackingMetricRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundStyle(AppColors.textSecondary)
            Spacer(minLength: 12)
            Text(value)
        }
        .font(.caption.monospacedDigit())
    }
}

private struct IOSAvatarPreviewCard: View {
    let avatarPreview: IOSAvatarPreview

    var body: some View {
        HStack(spacing: 12) {
            Group {
                if let thumbnail = avatarPreview.thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFill()
                } else {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(AppColors.avatarFallbackFill)
                        Text("VRM")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(AppColors.avatarFallbackText)
                    }
                }
            }
            .frame(width: 96, height: 96)
            .clipShape(RoundedRectangle(cornerRadius: 16))

            VStack(alignment: .leading, spacing: 6) {
                Text(avatarPreview.avatarName)
                    .font(.headline)
                Text(avatarPreview.fileName)
                    .font(.caption)
                    .foregroundStyle(AppColors.textSecondary)
                if let authorName = avatarPreview.authorName {
                    Text("Author: \(authorName)")
                        .font(.subheadline)
                }
                if let vrmVersion = avatarPreview.vrmVersion {
                    Text("Version: \(vrmVersion)")
                        .font(.subheadline)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(maxWidth: 320, alignment: .leading)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
}

private struct IOSAvatarBodyView: View {
    let avatarPreview: IOSAvatarPreview

    var body: some View {
        Group {
            if let thumbnail = avatarPreview.thumbnail {
                Image(uiImage: thumbnail)
                    .resizable()
                    .scaledToFit()
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 28)
                        .fill(AppColors.avatarBodyFallbackFill)
                    Text(avatarPreview.avatarName)
                        .font(.title2.weight(.bold))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(AppColors.avatarFallbackText)
                        .padding(24)
                }
            }
        }
        .frame(maxWidth: 300, maxHeight: 420)
    }
}

private extension Float {
    var degrees: Float {
        self * 180 / .pi
    }

    func clamped(to range: ClosedRange<Float>) -> Float {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
