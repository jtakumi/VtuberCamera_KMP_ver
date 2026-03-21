package com.example.vtubercamera_kmp_ver.resources

import org.jetbrains.compose.resources.getString
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_checking_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_denied_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_launch_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_not_determined_description
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_open_settings_button
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_permission_required_message
import vtubercamera_kmp_ver.composeapp.generated.resources.camera_switch_button

class CameraPermissionTexts(
    val requiredMessage: String,
    val deniedDescription: String,
    val notDeterminedDescription: String,
    val checkingDescription: String,
    val openSettingsButtonTitle: String,
    val launchCameraButtonTitle: String,
    val switchCameraButtonTitle: String,
)

class CameraPermissionTextsLoader {
    suspend fun load(): CameraPermissionTexts {
        return CameraPermissionTexts(
            requiredMessage = getString(Res.string.camera_permission_required_message),
            deniedDescription = getString(Res.string.camera_permission_denied_description),
            notDeterminedDescription = getString(Res.string.camera_permission_not_determined_description),
            checkingDescription = getString(Res.string.camera_permission_checking_description),
            openSettingsButtonTitle = getString(Res.string.camera_permission_open_settings_button),
            launchCameraButtonTitle = getString(Res.string.camera_permission_launch_button),
            switchCameraButtonTitle = getString(Res.string.camera_switch_button),
        )
    }
}
