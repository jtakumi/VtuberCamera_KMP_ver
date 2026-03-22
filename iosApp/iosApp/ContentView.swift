import SwiftUI
import AVFoundation
import ComposeApp
import UIKit

struct ContentView: View {
    @StateObject private var viewModel = IOSCameraViewModel()

    var body: some View {
        ZStack {
            if viewModel.isAuthorized {
                ZStack(alignment: .topTrailing) {
                    CameraPreviewView(avCaptureSession: viewModel.avCaptureSession)
                        .ignoresSafeArea()

                    if viewModel.canSwitchCamera, let texts = viewModel.permissionTexts {
                        Button(texts.switchCameraButtonTitle, action: viewModel.switchCamera)
                            .buttonStyle(.borderedProminent)
                            .padding(.top, 16)
                            .padding(.trailing, 16)
                    }
                }
            } else {
                PermissionPromptView(
                    status: viewModel.authorizationStatus,
                    texts: viewModel.permissionTexts,
                    onPrimaryAction: viewModel.handlePrimaryAction,
                )
            }
        }
        .background(Color.black.ignoresSafeArea())
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
                    .foregroundStyle(.secondary)
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

@MainActor
private final class IOSCameraViewModel: ObservableObject {
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

    let avCaptureSession = AVCaptureSession()

    private var isConfigured = false
    private var lensFacing: LensFacing = .back
    private var currentInput: AVCaptureDeviceInput?
    private let permissionTextsLoader = CameraPermissionTextsLoader()

    init() {
        refreshCameraCapabilities()
        loadPermissionTexts()
    }

    var isAuthorized: Bool {
        authorizationStatus == .authorized
    }

    func refreshAuthorization() {
        authorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
        refreshCameraCapabilities()
        if isAuthorized {
            startSession()
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

    func stopSession() {
        guard avCaptureSession.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async {
            self.avCaptureSession.stopRunning()
        }
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
                    self.startSession()
                }
            }
        }
    }

    private func startSession() {
        guard configureSessionIfNeeded(), !avCaptureSession.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async {
            self.avCaptureSession.startRunning()
        }
    }

    func switchCamera() {
        guard canSwitchCamera else { return }
        let nextLensFacing = lensFacing.toggled
        guard availableLensFacings().contains(nextLensFacing) else { return }
        lensFacing = nextLensFacing
        _ = configureSession(for: lensFacing)
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
