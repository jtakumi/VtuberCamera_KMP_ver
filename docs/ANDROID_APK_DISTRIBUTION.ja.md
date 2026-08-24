# Android 配布 APK の署名とインストール失敗の切り分け

GitHub Releases に添付した APK が端末で「問題が発生しました / Something went wrong」となってインストールできない場合の、原因切り分けと恒久対策をまとめます。

## 1. ver1.1.0 の APK を検証した結果

`ver1.1.0` の `composeApp-release.apk`（94,421,087 bytes, sha256 `a9057fb2…d52e6a5`）を解析した結果は次のとおりです。

| 検証項目 | 結果 |
| --- | --- |
| ZIP / APK 構造 | 正常（565 entries、破損なし） |
| APK Signing Block | 存在する（Scheme v2 のみ、v1 / v3 なし） |
| v2 署名ダイジェスト | APK 実体と一致（改ざん・転送破損なし） |
| 署名証明書 | `CN=Android Debug, O=Android, C=US`（有効期限 2024-06-08 〜 2054-06-01） |
| minSdkVersion / targetSdkVersion | 29 / 36 |
| versionCode / versionName | 1 / 1.0 |
| `resources.arsc` | 無圧縮・4 byte アラインメント済み（Android 11+ の要件を満たす） |
| ネイティブライブラリ | 4 ABI 分を無圧縮で格納、16 KB アラインメント済み |
| `android:testOnly` / `android:debuggable` | いずれも付与されていない |

つまり **APK ファイル自体は壊れておらず、形式としてはインストール可能** です。したがって失敗要因は端末側の状態か、署名・バージョン運用に起因するものに絞られます。

## 2. 端末側で実際のエラーコードを確認する

Android の「問題が発生しました」は複数の失敗を 1 つにまとめた汎用メッセージなので、まず本当の失敗理由を取得します。

```bash
adb install -r composeApp-release.apk
# もしくはインストール操作中のログを見る
adb logcat | grep -Ei "PackageInstaller|PackageManager|INSTALL_FAILED"
```

代表的な失敗理由と対処:

| エラー | 意味 | 対処 |
| --- | --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / `INSTALL_FAILED_DUPLICATE_PERMISSION` | 同じ `applicationId` のアプリが別の鍵で署名された状態で既に入っている | 既存アプリをアンインストールしてから入れ直す |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 端末に入っているものより `versionCode` が低い | `versionCode` を上げる（後述） |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | 空き容量不足（本 APK は約 94 MB、展開後はさらに必要） | 空き容量を確保する |
| `INSTALL_FAILED_VERIFICATION_FAILURE` | Play Protect / 提供元不明アプリのブロック | 一時的にスキャンを無効化するか、インストール元アプリに許可を与える |

`ver1.1.0` は **開発機の debug keystore（`~/.android/debug.keystore`）で署名されています**。debug keystore はマシンごとに自動生成されるため、

- 別のマシンでビルドした同アプリが端末に入っている
- Android Studio から `Run` した debug ビルドが入っている（`applicationId` は release と同じ `com.example.vtubercamera_kmp_ver`）

といった場合、署名不一致で必ずインストールに失敗します。まずは端末から既存アプリを削除して再試行してください。

```bash
adb uninstall com.example.vtubercamera_kmp_ver
```

## 3. 恒久対策: release 用の署名鍵を使う

配布用 APK は debug keystore ではなく、固定の release keystore で署名します。鍵が変わると既存インストールへの上書きができなくなるため、**一度作った鍵は紛失しないように保管** します。

### 3.1 鍵を作る

```bash
keytool -genkeypair -v \
  -keystore vtubercamera-release.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias vtubercamera
```

### 3.2 ローカルビルドでの設定

リポジトリ直下に `keystore.properties` を置きます（`.gitignore` 済み。`*.jks` / `*.keystore` も同様）。

```properties
storeFile=vtubercamera-release.jks
storePassword=********
keyAlias=vtubercamera
keyPassword=********
```

`storeFile` は絶対パス、またはリポジトリ直下からの相対パスで指定します。

### 3.3 CI での設定

同じ値を環境変数から渡せます。

| 環境変数 | 内容 |
| --- | --- |
| `RELEASE_STORE_FILE` | keystore のパス |
| `RELEASE_STORE_PASSWORD` | keystore のパスワード |
| `RELEASE_KEY_ALIAS` | 鍵のエイリアス |
| `RELEASE_KEY_PASSWORD` | 鍵のパスワード |

いずれか 1 つでも欠けている場合、release ビルドは警告を出したうえで debug keystore にフォールバックします。この APK は **ローカル検証専用** で、配布してはいけません。

## 4. 恒久対策: versionCode / versionName を毎回更新する

`versionCode` と `versionName` は `gradle.properties` の `appVersionCode` / `appVersionName` で管理します。

```properties
appVersionCode=2
appVersionName=1.1.0
```

配布ビルドごとに `appVersionCode` を必ず 1 以上増やします。CI からは次のように上書きできます。

```bash
./gradlew :composeApp:assembleRelease -PappVersionCode=3 -PappVersionName=1.1.1
```

## 5. 配布 APK のビルド手順

```bash
./gradlew :composeApp:assembleRelease
# 出力: composeApp/build/outputs/apk/release/composeApp-release.apk
```

アップロード前に署名を確認します。

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs -v composeApp-release.apk
```

`Signer #1 certificate DN` が release 鍵になっていること、`Verified using v2 scheme` が `true` であることを確認してください。

## 6. 既知の残課題

- `applicationId` が `com.example.vtubercamera_kmp_ver` のままです。Google Play へ出す場合は `com.example.` 始まりのパッケージ名は使えないため、公開前に変更が必要です。変更するとパッケージが別アプリ扱いになり、既存インストールは上書きできません。
- APK に 4 ABI 分のネイティブライブラリを同梱しているため約 94 MB あります。配布サイズを下げたい場合は ABI split か App Bundle の採用を検討します。
