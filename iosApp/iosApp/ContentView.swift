import SwiftUI
@preconcurrency import AVFoundation
import ComposeApp
import UniformTypeIdentifiers
import UIKit

struct ContentView: View {
    @StateObject private var viewModel = IOSCameraViewModel()
    @State private var isFileImporterPresented = false

    var body: some View {
        ZStack {
            if viewModel.isAuthorized {
                ZStack(alignment: .topTrailing) {
                    CameraPreviewView(avCaptureSession: viewModel.avCaptureSession)
                        .ignoresSafeArea()

                    HStack(spacing: 12) {
                        Button("ファイルを開く") {
                            isFileImporterPresented = true
                        }
                        .buttonStyle(.borderedProminent)

                        if viewModel.canSwitchCamera, let texts = viewModel.permissionTexts {
                            Button(texts.switchCameraButtonTitle, action: viewModel.switchCamera)
                                .buttonStyle(.borderedProminent)
                        }
                    }
                    .padding(.top, 16)
                    .padding(.trailing, 16)

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
            } else {
                PermissionPromptView(
                    status: viewModel.authorizationStatus,
                    texts: viewModel.permissionTexts,
                    onPrimaryAction: viewModel.handlePrimaryAction,
                )
            }
        }
        .background(Color.black.ignoresSafeArea())
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
                            .fill(Color.white.opacity(0.12))
                        Text("VRM")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(.white)
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
                    .foregroundStyle(.secondary)
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
                        .fill(Color.white.opacity(0.24))
                    Text(avatarPreview.avatarName)
                        .font(.title2.weight(.bold))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white)
                        .padding(24)
                }
            }
        }
        .frame(maxWidth: 300, maxHeight: 420)
    }
}
