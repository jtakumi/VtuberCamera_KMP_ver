package com.example.vtubercamera_kmp_ver.camera.permission

import com.example.vtubercamera_kmp_ver.camera.PermissionRepository
import com.example.vtubercamera_kmp_ver.camera.PermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 権限状態の変化を表す型。CameraViewModel が session 側の同期/非同期エフェクトを wiring する。
sealed interface PermissionChange {
    // 非 Granted → Granted への遷移。CameraSession を新規に開始する必要がある。
    data object GrantedEntered : PermissionChange

    // 既に Granted の状態で再度 Granted を受け取った場合。preview 表示はリセットするが session は起動済み。
    data object GrantedRefreshed : PermissionChange

    // Denied への遷移、または初回 Denied 受信。
    data object DeniedReceived : PermissionChange

    // Unknown への遷移 (権限ダイアログ表示中など)。
    data object UnknownReceived : PermissionChange
}

// カメラ権限の確認・要求と、その状態遷移に応じた他ドメインへの通知を仲介する。
class CameraPermissionCoordinator(
    private val permissionRepository: PermissionRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CameraPermissionUiState())
    val state: StateFlow<CameraPermissionUiState> = _state.asStateFlow()

    // 権限状態の遷移を通知するコールバック。CameraViewModel が各 change を session 側 API へ wiring する。
    var onPermissionChanged: (PermissionChange) -> Unit = {}

    // Request 開始時に呼ばれる。CameraViewModel が session.clearError() を wiring する。
    var onPermissionRequested: () -> Unit = {}

    // 画面初期化時に権限状態を確認し、対応する遷移コールバックを発火する。
    fun initialize() {
        scope.launch {
            val previousState = _state.value.permissionState
            val nextState = permissionRepository.checkCameraPermission()
            _state.update { it.copy(permissionState = nextState) }
            dispatchChange(previousState, nextState)
        }
    }

    // カメラ権限の再要求フローを開始する。Unknown 表示 + 同期コールバック + 非同期要求。
    fun onRequestPermission() {
        _state.update { it.copy(permissionState = PermissionState.Unknown) }
        onPermissionRequested()
        scope.launch {
            permissionRepository.requestCameraPermission()
        }
    }

    // ネイティブ側から受け取った権限状態の変化を反映し、必要な遷移コールバックを同期的に発火する。
    fun onPermissionStateChanged(
        isGranted: Boolean,
        isChecking: Boolean,
    ) {
        val nextState = when {
            isChecking -> PermissionState.Unknown
            isGranted -> PermissionState.Granted
            else -> PermissionState.Denied
        }
        val previousState = _state.value.permissionState
        _state.update { it.copy(permissionState = nextState) }
        dispatchChange(previousState, nextState)
    }

    private fun dispatchChange(previous: PermissionState, next: PermissionState) {
        val change = when (next) {
            PermissionState.Granted -> if (previous == PermissionState.Granted) {
                PermissionChange.GrantedRefreshed
            } else {
                PermissionChange.GrantedEntered
            }
            PermissionState.Denied -> PermissionChange.DeniedReceived
            PermissionState.Unknown -> PermissionChange.UnknownReceived
        }
        onPermissionChanged(change)
    }
}
