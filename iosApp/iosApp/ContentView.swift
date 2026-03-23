import SwiftUI
@preconcurrency import AVFoundation
import ComposeApp
import UniformTypeIdentifiers
import UIKit

struct ContentView: View {
    @StateObject private var viewModel = IOSCameraViewModel()
    @State private var isFileImporterPresented = false
    @State private var avatarPreview: IOSAvatarPreview?
    @State private var fileErrorMessage: String?

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

                    if let avatarPreview {
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
            onCompletion: handleFileImport
        )
        .alert("ファイルエラー", isPresented: Binding(
            get: { fileErrorMessage != nil },
            set: { isPresented in
                if !isPresented {
                    fileErrorMessage = nil
                }
            }
        )) {
            Button("閉じる", role: .cancel) {
                fileErrorMessage = nil
            }
        } message: {
            Text(fileErrorMessage ?? "")
        }
        .onAppear {
            viewModel.refreshAuthorization()
        }
        .onDisappear {
            viewModel.stopSession()
        }
    }

    private func handleFileImport(_ result: Result<[URL], Error>) {
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

private struct IOSAvatarPreview {
    let fileName: String
    let avatarName: String
    let authorName: String?
    let vrmVersion: String?
    let thumbnail: UIImage?
}

private enum IOSVrmAvatarParser {
    private static let supportedExtensions: Set<String> = ["vrm", "glb"]

    static func parse(url: URL) throws -> IOSAvatarPreview {
        let didAccess = url.startAccessingSecurityScopedResource()
        defer {
            if didAccess {
                url.stopAccessingSecurityScopedResource()
            }
        }

        let fileName = url.lastPathComponent
        guard supportedExtensions.contains(url.pathExtension.lowercased()) else {
            throw ParserError.invalidFileType
        }

        let data = try Data(contentsOf: url)
        let fileBytes = [UInt8](data)
        guard fileBytes.count >= 20 else {
            throw ParserError.readFailed
        }
        guard readIntLE(fileBytes, offset: 0) == 0x46546C67 else {
            throw ParserError.invalidFileType
        }

        let declaredLength = readIntLE(fileBytes, offset: 8)
        guard declaredLength <= fileBytes.count, declaredLength >= 20 else {
            throw ParserError.invalidFormat
        }

        var offset = 12
        var jsonChunk: Data?
        var binaryChunk: Data?
        while offset + 8 <= declaredLength {
            let chunkLength = readIntLE(fileBytes, offset: offset)
            let chunkType = readIntLE(fileBytes, offset: offset + 4)
            let chunkStart = offset + 8
            let chunkEnd = chunkStart + chunkLength
            guard chunkLength >= 0, chunkEnd <= declaredLength else {
                throw ParserError.invalidFormat
            }

            let chunkData = data.subdata(in: chunkStart..<chunkEnd)
            if chunkType == 0x4E4F534A {
                jsonChunk = chunkData
            } else if chunkType == 0x004E4942 {
                binaryChunk = chunkData
            }
            offset = chunkEnd
        }

        guard
            let jsonChunk,
            let jsonObject = try JSONSerialization.jsonObject(with: jsonChunk) as? [String: Any]
        else {
            throw ParserError.metadataFailed
        }

        let meta0 = (((jsonObject["extensions"] as? [String: Any])?["VRM"] as? [String: Any])?["meta"] as? [String: Any])
        let meta1 = (((jsonObject["extensions"] as? [String: Any])?["VRMC_vrm"] as? [String: Any])?["meta"] as? [String: Any])
        let avatarName = (meta0?["title"] as? String)
            ?? (meta1?["name"] as? String)
            ?? (fileName as NSString).deletingPathExtension
        let authorName = (meta0?["author"] as? String)
            ?? ((meta1?["authors"] as? [String])?.first)
        let vrmVersion = (meta0?["version"] as? String)
            ?? (((jsonObject["extensions"] as? [String: Any])?["VRMC_vrm"] as? [String: Any])?["specVersion"] as? String)
            ?? ((jsonObject["asset"] as? [String: Any])?["version"] as? String)

        let thumbnailImageIndex: Int? = {
            if
                let textureIndex = meta0?["texture"] as? Int,
                let textures = jsonObject["textures"] as? [[String: Any]],
                textureIndex >= 0,
                textureIndex < textures.count,
                let source = textures[textureIndex]["source"] as? Int
            {
                return source
            }
            return meta1?["thumbnailImage"] as? Int
        }()

        let thumbnail: UIImage? = {
            guard
                let thumbnailImageIndex,
                let binaryChunk,
                let images = jsonObject["images"] as? [[String: Any]],
                thumbnailImageIndex >= 0,
                thumbnailImageIndex < images.count,
                let bufferViewIndex = images[thumbnailImageIndex]["bufferView"] as? Int,
                let mimeType = images[thumbnailImageIndex]["mimeType"] as? String,
                mimeType.hasPrefix("image/"),
                let bufferViews = jsonObject["bufferViews"] as? [[String: Any]],
                bufferViewIndex >= 0,
                bufferViewIndex < bufferViews.count,
                let byteLength = bufferViews[bufferViewIndex]["byteLength"] as? Int
            else {
                return nil
            }

            let byteOffset = (bufferViews[bufferViewIndex]["byteOffset"] as? Int) ?? 0
            let end = byteOffset + byteLength
            guard byteOffset >= 0, end <= binaryChunk.count else {
                return nil
            }
            let imageData = binaryChunk.subdata(in: byteOffset..<end)
            return UIImage(data: imageData)
        }()

        return IOSAvatarPreview(
            fileName: fileName,
            avatarName: avatarName,
            authorName: authorName,
            vrmVersion: vrmVersion,
            thumbnail: thumbnail
        )
    }

    private static func readIntLE(_ bytes: [UInt8], offset: Int) -> Int {
        Int(bytes[offset])
            | (Int(bytes[offset + 1]) << 8)
            | (Int(bytes[offset + 2]) << 16)
            | (Int(bytes[offset + 3]) << 24)
    }

    private enum ParserError: LocalizedError {
        case invalidFileType
        case readFailed
        case invalidFormat
        case metadataFailed

        var errorDescription: String? {
            switch self {
            case .invalidFileType:
                return "VRM/GLBファイルを選択してください。"
            case .readFailed:
                return "VRM/GLBファイルの読み込みに失敗しました。"
            case .invalidFormat:
                return "VRM/GLBファイルの形式が不正です。"
            case .metadataFailed:
                return "VRM/GLBメタデータの解析に失敗しました。"
            }
        }
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

    private let sessionQueue = DispatchQueue(label: "IOSCameraViewModel.sessionQueue", qos: .userInitiated)
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
        let captureSession = avCaptureSession
        guard captureSession.isRunning else { return }
        sessionQueue.async {
            captureSession.stopRunning()
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
