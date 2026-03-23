import com.example.vtubercamera_kmp_ver.camera.AvatarPreviewData
import com.example.vtubercamera_kmp_ver.camera.CameraLensFacing
import org.jetbrains.compose.resources.StringResource

data class CameraUiState(
    val lensFacing: CameraLensFacing = CameraLensFacing.Back,
    val isPermissionGranted: Boolean = false,
    val isPermissionChecking: Boolean = true,
    val avatarPreview: AvatarPreviewData? = null,
    val filePickerErrorMessageRes: StringResource? = null,
)
