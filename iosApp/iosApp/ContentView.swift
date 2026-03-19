import SwiftUI
import AVFoundation
import ComposeApp
import UIKit

struct ContentView: View {
    @StateObject private var viewModel = IOSCameraViewModel()

    var body: some View {
        ZStack {
            if viewModel.isAuthorized {
                CameraPreviewView(session: viewModel.session)
                    .ignoresSafeArea()
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
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewContainerView {
        let view = PreviewContainerView()
        view.previewLayer.videoGravity = .resizeAspectFill
        view.previewLayer.session = session
        return view
    }

    func updateUIView(_ uiView: PreviewContainerView, context: Context) {
        uiView.previewLayer.session = session
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
    @Published var authorizationStatus: AVAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
    @Published var permissionTexts: CameraPermissionTexts?

    let session = AVCaptureSession()

    private var isConfigured = false
    private let permissionTextsLoader = CameraPermissionTextsLoader()

    init() {
        loadPermissionTexts()
    }

    var isAuthorized: Bool {
        authorizationStatus == .authorized
    }

    func refreshAuthorization() {
        authorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
        if isAuthorized {
            startSession()
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
        guard session.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async {
            self.session.stopRunning()
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
        configureSessionIfNeeded()
        guard isConfigured, !session.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async {
            self.session.startRunning()
        }
    }

    private func configureSessionIfNeeded() {
        guard !isConfigured else { return }
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        guard
            let camera = AVCaptureDevice.default(for: .video),
            let input = try? AVCaptureDeviceInput(device: camera),
            session.canAddInput(input)
        else {
            return
        }

        session.addInput(input)
        isConfigured = true
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
}

