import com.example.vtubercamera_kmp_ver.camera.CameraLensFacing

data class CameraUiState(
    val lensFacing: CameraLensFacing = CameraLensFacing.Back,
    val isPermissionGranted: Boolean = false,
    val isPermissionChecking: Boolean = true,
)