import SwiftUI
import AVFoundation
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
    let onPrimaryAction: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text("カメラプレビューを表示するにはカメラ権限が必要です。")
                .font(.title3.weight(.semibold))
                .multilineTextAlignment(.center)
            Text(descriptionText)
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            Button(primaryButtonTitle, action: onPrimaryAction)
                .buttonStyle(.borderedProminent)
        }
        .padding(.horizontal, 24)
    }

    private var descriptionText: String {
        switch status {
        case .denied, .restricted:
            return "設定アプリでカメラへのアクセスを許可すると、背面カメラのプレビューを表示できます。"
        case .notDetermined:
            return "「カメラを起動」を押すと、iOS の権限ダイアログを表示します。"
        default:
            return "権限を確認しています。"
        }
    }

    private var primaryButtonTitle: String {
        switch status {
        case .denied, .restricted:
            return "設定を開く"
        default:
            return "カメラを起動"
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

    let session = AVCaptureSession()

    private var isConfigured = false

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

