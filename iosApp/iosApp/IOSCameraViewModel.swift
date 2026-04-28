import SwiftUI
@preconcurrency import AVFoundation
import ARKit
import ComposeApp
import Foundation

@MainActor
final class IOSCameraViewModel: ObservableObject {
    private enum LensFacing {
        case back
        case front

        var devicePosition: AVCaptureDevice.Position {
            switch self {
            case .back:
                return .back
            case .front:
                return .front
            }
        }

        var toggled: LensFacing {
            switch self {
            case .back:
                return .front
            case .front:
                return .back
            }
        }
    }

    @Published var authorizationStatus: AVAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
    @Published var permissionTexts: CameraPermissionTexts?
    @Published private(set) var canSwitchCamera = false
    @Published private(set) var avatarPreview: IOSAvatarPreview?
    @Published private(set) var fileErrorMessage: String?
    @Published private(set) var faceTrackingFrame: IOSNormalizedFaceFrame?

    let avCaptureSession = AVCaptureSession()
    let isARFaceTrackingSupported = ARFaceTrackingConfiguration.isSupported

    private let sessionQueue = DispatchQueue(label: "IOSCameraViewModel.sessionQueue", qos: .userInitiated)
    private var isConfigured = false
    private var lensFacing: LensFacing = .front
    private var currentInput: AVCaptureDeviceInput?
    private let permissionTextsLoader = CameraPermissionTextsLoader()

    init() {
        refreshCameraCapabilities()
        loadPermissionTexts()
    }

    var isAuthorized: Bool {
        authorizationStatus == .authorized
    }

    var isUsingARFaceTracking: Bool {
        isAuthorized && lensFacing == .front && isARFaceTrackingSupported
    }

    var faceTrackingStatusText: String {
        if isUsingARFaceTracking {
            return faceTrackingFrame == nil ? "ARKit: 顔を検出中" : "ARKit: 追跡中"
        }
        if lensFacing == .front && !isARFaceTrackingSupported {
            return "TrueDepth 非対応: AVFoundation preview を継続中"
        }
        return "前面カメラで ARKit face tracking を有効化"
    }

    var isFileErrorPresented: Bool {
        fileErrorMessage != nil
    }

    func refreshAuthorization() {
        authorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
        refreshCameraCapabilities()
        if isAuthorized {
            updateCameraPipeline()
        } else {
            stopSession()
        }
    }

    func handlePrimaryAction() {
        switch authorizationStatus {
        case .authorized:
            startSession()
        case .notDetermined:
            requestPermission()
        case .denied, .restricted:
            openAppSettings()
        @unknown default:
            break
        }
    }

    func handleFileImport(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            do {
                avatarPreview = try IOSVrmAvatarParser.parse(url: url)
                fileErrorMessage = nil
            } catch {
                avatarPreview = nil
                fileErrorMessage = error.localizedDescription
            }
        case .failure(let error):
            avatarPreview = nil
            fileErrorMessage = error.localizedDescription
        }
    }

    func dismissFileError() {
        fileErrorMessage = nil
    }

    func handleFaceTrackingFrameChanged(_ frame: IOSNormalizedFaceFrame?) {
        updateFaceTrackingFrame(frame)
    }

    func stopSession() {
        let captureSession = avCaptureSession
        updateFaceTrackingFrame(nil)
        guard captureSession.isRunning else { return }
        sessionQueue.async {
            captureSession.stopRunning()
        }
    }

    private func updateFaceTrackingFrame(_ frame: IOSNormalizedFaceFrame?) {
        faceTrackingFrame = frame
        publishAvatarRenderState(frame)
    }

    private func loadPermissionTexts() {
        permissionTextsLoader.load { texts, _ in
            guard let texts else { return }
            Task { @MainActor in
                self.permissionTexts = texts
            }
        }
    }

    private func requestPermission() {
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                self.authorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
                if granted {
                    self.updateCameraPipeline()
                }
            }
        }
    }

    private func startSession() {
        guard configureSessionIfNeeded() else { return }
        let captureSession = avCaptureSession
        guard !captureSession.isRunning else { return }
        sessionQueue.async {
            captureSession.startRunning()
        }
    }

    func switchCamera() {
        guard canSwitchCamera else { return }
        let nextLensFacing = lensFacing.toggled
        guard availableLensFacings().contains(nextLensFacing) else { return }
        lensFacing = nextLensFacing
        updateCameraPipeline()
    }

    private func updateCameraPipeline() {
        guard isAuthorized else {
            stopSession()
            return
        }

        if isUsingARFaceTracking {
            let captureSession = avCaptureSession
            faceTrackingFrame = nil
            guard captureSession.isRunning else { return }
            sessionQueue.async {
                captureSession.stopRunning()
            }
            return
        }

        faceTrackingFrame = nil
        _ = configureSession(for: lensFacing)
        startSession()
    }

    @discardableResult
    private func configureSessionIfNeeded() -> Bool {
        if isConfigured, currentInput?.device.position == lensFacing.devicePosition {
            return true
        }

        return configureSession(for: lensFacing)
    }

    @discardableResult
    private func configureSession(for requestedLensFacing: LensFacing) -> Bool {
        let availableLensFacings = availableLensFacings()
        guard let resolvedLensFacing = resolvedLensFacing(
            requestedLensFacing,
            availableLensFacings: availableLensFacings
        ) else {
            isConfigured = false
            return false
        }

        guard
            let camera = cameraDevice(for: resolvedLensFacing),
            let input = try? AVCaptureDeviceInput(device: camera)
        else {
            isConfigured = false
            return false
        }

        avCaptureSession.beginConfiguration()
        defer { avCaptureSession.commitConfiguration() }

        if let currentInput, currentInput.device.uniqueID == input.device.uniqueID {
            lensFacing = resolvedLensFacing
            isConfigured = true
            return true
        }

        if let currentInput {
            avCaptureSession.removeInput(currentInput)
        }

        guard avCaptureSession.canAddInput(input) else {
            if let currentInput, avCaptureSession.canAddInput(currentInput) {
                avCaptureSession.addInput(currentInput)
            }
            isConfigured = currentInput != nil
            return false
        }

        avCaptureSession.addInput(input)
        currentInput = input
        lensFacing = resolvedLensFacing
        isConfigured = true
        return true
    }

    private func publishAvatarRenderState(_ frame: IOSNormalizedFaceFrame?) {
        let normalizedFaceFrame = frame ?? IOSNormalizedFaceFrame(
            timestampMillis: 0,
            trackingConfidence: 0,
            headYawDegrees: 0,
            headPitchDegrees: 0,
            headRollDegrees: 0,
            leftEyeBlink: 0,
            rightEyeBlink: 0,
            jawOpen: 0,
            mouthSmile: 0
        )

        NotificationCenter.default.post(
            name: IOSAvatarRenderBridge.avatarRenderStateDidChangeNotification,
            object: nil,
            userInfo: [
                IOSAvatarRenderBridge.headYawDegreesKey: normalizedFaceFrame.headYawDegrees,
                IOSAvatarRenderBridge.headPitchDegreesKey: normalizedFaceFrame.headPitchDegrees,
                IOSAvatarRenderBridge.headRollDegreesKey: normalizedFaceFrame.headRollDegrees,
                IOSAvatarRenderBridge.leftEyeBlinkKey: normalizedFaceFrame.leftEyeBlink,
                IOSAvatarRenderBridge.rightEyeBlinkKey: normalizedFaceFrame.rightEyeBlink,
                IOSAvatarRenderBridge.jawOpenKey: normalizedFaceFrame.jawOpen,
                IOSAvatarRenderBridge.mouthSmileKey: normalizedFaceFrame.mouthSmile,
            ]
        )
    }

    private func openAppSettings() {
        guard
            let url = URL(string: UIApplication.openSettingsURLString),
            UIApplication.shared.canOpenURL(url)
        else {
            return
        }
        UIApplication.shared.open(url)
    }

    private func refreshCameraCapabilities() {
        let availableLensFacings = availableLensFacings()
        canSwitchCamera = availableLensFacings.count > 1
        if let resolvedLensFacing = resolvedLensFacing(
            lensFacing,
            availableLensFacings: availableLensFacings
        ) {
            lensFacing = resolvedLensFacing
        }
    }

    private func availableLensFacings() -> Set<LensFacing> {
        var lensFacings = Set<LensFacing>()
        if cameraDevice(for: .back) != nil {
            lensFacings.insert(.back)
        }
        if cameraDevice(for: .front) != nil {
            lensFacings.insert(.front)
        }
        return lensFacings
    }

    private func resolvedLensFacing(
        _ requestedLensFacing: LensFacing,
        availableLensFacings: Set<LensFacing>
    ) -> LensFacing? {
        if availableLensFacings.contains(requestedLensFacing) {
            return requestedLensFacing
        }
        if availableLensFacings.contains(.back) {
            return .back
        }
        if availableLensFacings.contains(.front) {
            return .front
        }
        return nil
    }

    private func cameraDevice(for lensFacing: LensFacing) -> AVCaptureDevice? {
        let discoverySession = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInWideAngleCamera],
            mediaType: .video,
            position: lensFacing.devicePosition
        )
        return discoverySession.devices.first
    }
}

struct IOSNormalizedFaceFrame {
    let timestampMillis: Int64
    let trackingConfidence: Float
    let headYawDegrees: Float
    let headPitchDegrees: Float
    let headRollDegrees: Float
    let leftEyeBlink: Float
    let rightEyeBlink: Float
    let jawOpen: Float
    let mouthSmile: Float
}