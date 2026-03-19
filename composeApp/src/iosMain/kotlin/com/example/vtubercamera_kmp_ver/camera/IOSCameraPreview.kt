package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun rememberCameraPermissionController(): CameraPermissionController {
    return remember {
        CameraPermissionController(
            isGranted = false,
            isChecking = false,
            requestPermission = {},
        )
    }
}

@Composable
actual fun CameraPreviewHost(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("iOS camera preview is hosted by the native iOS app.")
    }
}

/*
@Composable actual fun を使用している理由

理由は 2 つあります。
1. これは Kotlin Multiplatform の expect/actual で、プラットフォームごとの差し替え点だから
2. その差し替え点が Compose のコンポジション内で呼ばれる API だから
コード上では、共通側でこの契約が定義されています。
- CameraPermissionController.kt

ここで
- rememberCameraPermissionController は expect
- しかも @Composable 付き
になっています。つまり「各プラットフォームで実装は違ってよいが、Compose から呼べる形で実装してくれ」という契約です。

実際に共通 UI からその関数を呼んでいるのはここです。
- CameraScreen.kt
- CameraScreen.kt
ここでは CameraScreen の中で permissionController を取得しており、完全に Compose の描画フローの一部です。なので、各プラットフォーム実装も Composable である必要があります。

iOS 側は今は簡易実装ですが、それでも Composable にしてあるのは自然です。
- IOSCameraPreview.kt
この関数の中でも remember を使っています。remember 自体が Compose のコンポジション内でしか使えないので、関数は @Composable でなければなりません。

Android 側を見ると、その必要性はさらに明確です。
- AndroidCameraPreview.kt
Android 実装では
- LocalContext.current
- rememberLauncherForActivityResult
- LaunchedEffect
- remember
を使っています。これらは全部 Compose 専用 API なので、普通の関数にはできません。

要するに整理するとこうです。

- actual が付いている理由
  - 共通の expect 宣言に対する iOS 実装だから
- @Composable が付いている理由
  - 共通 UI のコンポジション中で呼ばれる
  - 関数内部で remember などの Compose API を使う
  - expect 側が @Composable 契約になっているので、actual 側もそれに合わせる必要がある

補足すると、actual fun だから Composable なのではありません。逆です。
「Compose から呼ぶ platform 差し替え関数」なので、結果として @Composable actual fun になっています。
*/
