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

/**
この箇所は、権限ダイアログに表示する文言を非同期で読み込み、その結果を SwiftUI の状態へ反映する処理です。対象は VtuberCamera_KMP_ver/iosApp/iosApp/ContentView.swift です。
1. permissionTextsLoader.load { texts, _ in
この load は、KMP 側の CameraPermissionTextsLoader を呼んで文言セットを取得しています。元の定義は VtuberCamera_KMP_ver/composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/resources/CameraPermissionTexts.kt にあり、requiredMessage や deniedDescription などの文言をまとめて返します。

2. guard let texts else { return }
読み込み結果が nil の場合は何もせず終了します。
つまり、文言の取得に失敗したり、結果が来なかった場合に不正な状態更新を避けています。

3. Task { @MainActor in
ここが重要です。
このクロージャはどのスレッド、どのアクターで呼ばれるか保証されないため、UI 状態の更新だけは MainActor に明示的に戻しています。

4. self.permissionTexts = texts
permissionTexts は @Published なので、ここを書き換えると SwiftUI 側が再描画されます。
実際には VtuberCamera_KMP_ver/iosApp/iosApp/ContentView.swift の PermissionPromptView がこの値を見て、ProgressView から実際の文言表示に切り替わります。

補足です。

- この ViewModel 自体は VtuberCamera_KMP_ver/iosApp/iosApp/ContentView.swift で @MainActor 付きです。
- それでも Task { @MainActor in ... } を入れているのは、load の完了ハンドラが MainActor 上で実行される保証がないためです。
- つまり意図としては「非同期取得はどこで完了してもよいが、SwiftUI の状態更新だけは必ずメイン側で行う」です。

この6行をひとことで言うと、
「KMP から権限文言を読み込み、取得できたときだけ MainActor 上で permissionTexts を更新して、画面表示を切り替える」
という処理です。

*/
    private func loadPermissionTexts() {
        permissionTextsLoader.load { texts, _ in
            guard let texts else { return }
            // asyncを使わずにMainActorを使用している理由は後述のコメントを参照
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

/**
* private fun loadPermissionTexts()でMainActorを使用している理由
*
理由は、ContentView.swift でやっていることが「単にメインスレッドへ投げる」だけではなく、「Swift Concurrency の MainActor 上で UI 状態を更新する」ことだからです。

要点は3つです。

1. `MainActor` と整合するから
この ViewModel は ContentView.swift で `@MainActor` が付いています。  
つまり `permissionTexts` は「MainActor に隔離された状態」です。

`Task { @MainActor in ... }` は、その更新処理を明示的に MainActor 上で実行します。  
一方で `DispatchQueue.main.async` は「メインスレッドで実行する」だけで、Swift の actor isolation の文脈には乗りません。

この差は重要です。

- `Task { @MainActor in ... }`
  - Swift Concurrency のルールに従う
  - 「この処理は MainActor 所属」という意図が型システムに伝わる
- `DispatchQueue.main.async`
  - GCD でメインキューに投げるだけ
  - actor の保証としては弱い

2. コールバック API から Swift Concurrency へ橋渡ししているから

`permissionTextsLoader.load { ... }` は完了ハンドラ形式の API です。  
このクロージャの中では `await MainActor.run { ... }` をそのまま呼べません。`await` が使えない文脈だからです。

そのため、

```swift
Task { @MainActor in
    self.permissionTexts = texts
}
```

の形で、新しく Swift Concurrency のタスクを作って MainActor に乗せています。

つまりこれは、
- 外側: 旧来の callback ベース API
- 内側: Swift Concurrency / MainActor
の橋渡しです。

3. 将来拡張しやすいから

今は代入1行だけですが、後でこの中に非同期処理を足したくなることがあります。

```swift
Task { @MainActor in
    self.permissionTexts = texts
    // 将来的に async な処理も追加しやすい
}
```

`DispatchQueue.main.async` だと GCD ベースの書き方になり、Swift Concurrency の文脈と分断されます。  
`Task` にしておくと、今後 `await` を含む処理へ自然に拡張できます。

補足として、実務上は `DispatchQueue.main.async` でもこの1行は動く可能性が高いです。  
ただしこのコードでは、
- ViewModel が `@MainActor`
- 更新対象が `@Published` な UI 状態
- callback から Swift Concurrency に戻したい
という条件がそろっているので、`Task + MainActor` の方が意図と設計がきれいに一致します。

短く言うと、
- `DispatchQueue.main.async` は「メインスレッドに投げる」
- `Task { @MainActor in ... }` は「MainActor に隔離された状態を、Swift Concurrency の流儀で安全に更新する」
という違いです。

この箇所に限れば、`Task + MainActor` を選んでいる理由は「`@MainActor` な ViewModel の状態更新として、より正しい抽象だから」です。
*/

}

