package com.example.vtubercamera_kmp_ver.avatar.render

import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Gltfio
import com.example.vtubercamera_kmp_ver.camera.AvatarSelectionData
import kotlin.math.sin
import android.view.View as AndroidView
import com.google.android.filament.View as FilamentView

internal class AndroidFilamentAvatarRenderer(
    context: Context,
) {
    val hostView: AndroidView

    private val textureView: TextureView
    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val indirectLight: IndirectLight
    private val view: FilamentView
    private val cameraEntity: Int
    private val lightEntity: Int
    private val camera: Camera
    private val uiHelper: UiHelper
    private val displayHelper: DisplayHelper
    private val assetLoader: AndroidVrmAssetLoader
    private val renderBridge: AndroidAvatarRenderBridge
    private var swapChain: SwapChain? = null
    private var sceneFraming: AvatarSceneFraming = AvatarSceneFraming.Default
    private var appliedSceneFraming: AvatarSceneFraming? = null
    private var renderState: AvatarRenderState = AvatarRenderState.Neutral
    private var appliedRenderState: AvatarRenderState? = null
    private var destroyed = false
    private var loggedFirstRenderedFrame = false

    init {
        initializeNativeBindings()

        textureView = TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isOpaque = false
        }
        hostView = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            clipChildren = false
            clipToPadding = false
            addView(textureView)
        }

        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(1.0f, 1.0f, 1.0f))
            .intensity(INDIRECT_LIGHT_INTENSITY)
            .build(engine)
        view = engine.createView()
        cameraEntity = EntityManager.get().create()
        lightEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        displayHelper = DisplayHelper(context)
        assetLoader = AndroidVrmAssetLoader(engine)
        renderBridge = AndroidAvatarRenderBridge(
            engine = engine,
            scene = scene,
            assetLoader = assetLoader,
            resourceCleaner = FilamentResourceCleaner(),
            onSceneFramingChanged = ::updateSceneFraming,
            onRenderStateChanged = ::updateRenderStateFromBridge,
        )

        configureRenderer()
        configureView()
        configureIndirectLight()
        configureLight()
        configureSurface()
    }

    fun updateRendererState(
        avatarSelection: AvatarSelectionData,
        nextRenderState: AvatarRenderState,
        onAvatarLoadFailure: (AvatarAssetLoadException) -> Unit,
    ) {
        renderBridge.update(
            avatarSelection = avatarSelection,
            avatarRenderState = nextRenderState,
            onAvatarLoadFailure = onAvatarLoadFailure,
        )
    }

    fun render(frameTimeNanos: Long) {
        val currentSwapChain = swapChain ?: return
        if (destroyed || !uiHelper.isReadyToRender) {
            return
        }

        applyRenderState()
        renderBridge.prepareFrame()
        if (renderer.beginFrame(currentSwapChain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            if (!loggedFirstRenderedFrame) {
                Log.d(LOG_TAG, "Rendered first avatar frame")
                loggedFirstRenderedFrame = true
            }
        }
    }

    fun destroy() {
        if (destroyed) {
            return
        }

        destroyed = true
        renderBridge.destroy()
        uiHelper.detach()
        displayHelper.detach()
        destroySwapChain()
        scene.removeEntity(lightEntity)
        engine.destroyEntity(lightEntity)
        scene.indirectLight = null
        engine.destroyIndirectLight(indirectLight)
        assetLoader.destroy()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    private fun configureRenderer() {
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
    }

    private fun configureView() {
        view.scene = scene
        view.camera = camera
        view.blendMode = FilamentView.BlendMode.TRANSLUCENT
        updateCameraLookAt(sceneFraming, AvatarRenderState.Neutral)
    }

    private fun configureLight() {
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .direction(LIGHT_DIRECTION_X, LIGHT_DIRECTION_Y, LIGHT_DIRECTION_Z)
            .color(LIGHT_COLOR_R, LIGHT_COLOR_G, LIGHT_COLOR_B)
            .intensity(LIGHT_INTENSITY)
            .build(engine, lightEntity)
        scene.addEntity(lightEntity)
    }

    private fun configureIndirectLight() {
        scene.indirectLight = indirectLight
    }

    private fun applyRenderState() {
        val currentSceneFraming = sceneFraming
        if (renderState == appliedRenderState && currentSceneFraming == appliedSceneFraming) {
            return
        }
        updateCameraLookAt(currentSceneFraming, renderState)
        appliedRenderState = renderState
        appliedSceneFraming = currentSceneFraming
    }

    private fun configureSurface() {
        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                destroySwapChain()
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                displayHelper.attach(renderer, textureView.display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                destroySwapChain()
                engine.flushAndWait()
            }

            override fun onResized(width: Int, height: Int) {
                resize(width, height)
            }
        }
        uiHelper.attachTo(textureView)
    }

    private fun resize(width: Int, height: Int) {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val aspect = safeWidth.toDouble() / safeHeight.toDouble()

        view.viewport = Viewport(0, 0, safeWidth, safeHeight)
        camera.setProjection(
            45.0,
            aspect,
            0.1,
            100.0,
            Camera.Fov.VERTICAL,
        )
    }

    private fun updateSceneFraming(sceneFraming: AvatarSceneFraming) {
        this.sceneFraming = sceneFraming
    }

    private fun updateRenderStateFromBridge(nextState: AvatarRenderState) {
        renderState = nextState
    }

    private fun updateCameraLookAt(
        sceneFraming: AvatarSceneFraming,
        renderState: AvatarRenderState,
    ) {
        val trackingInfluence = if (renderState.isTracking) {
            renderState.trackingConfidence.coerceIn(0f, 1f).toDouble()
        } else {
            0.0
        }
        val expressionInfluence = (
            renderState.expressions.jawOpen * JAW_WEIGHT +
                renderState.expressions.mouthSmile * SMILE_WEIGHT +
                ((renderState.expressions.leftEyeBlink + renderState.expressions.rightEyeBlink) * 0.5f) * BLINK_WEIGHT
            ).coerceIn(0f, 1f).toDouble()
        val yawRadians = Math.toRadians(
            renderState.rig.headYawDegrees.toDouble().coerceIn(-MAX_YAW_DEGREES, MAX_YAW_DEGREES),
        )
        val pitchRadians = Math.toRadians(
            renderState.rig.headPitchDegrees.toDouble().coerceIn(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES),
        )

        camera.lookAt(
            sceneFraming.targetX + sin(yawRadians) * CAMERA_YAW_OFFSET_SCALE * trackingInfluence,
            sceneFraming.targetY +
                sin(pitchRadians) * CAMERA_PITCH_OFFSET_SCALE * trackingInfluence +
                expressionInfluence * EXPRESSION_Y_OFFSET_SCALE,
            sceneFraming.targetZ + sceneFraming.cameraDistance - expressionInfluence * EXPRESSION_Z_OFFSET_SCALE,
            sceneFraming.targetX,
            sceneFraming.targetY,
            sceneFraming.targetZ,
            0.0,
            1.0,
            0.0,
        )
    }

    private fun destroySwapChain() {
        swapChain?.let { currentSwapChain ->
            engine.destroySwapChain(currentSwapChain)
            swapChain = null
        }
    }

    private companion object {
        // Volatile keeps the one-time native init flag visible across renderer instances that may
        // be created on different threads before they converge on the synchronized init block.
        @Volatile
        private var nativeBindingsInitialized = false

        // Small eye offsets that map tracked head yaw/pitch into camera parallax.
        private const val CAMERA_YAW_OFFSET_SCALE = 0.8
        private const val CAMERA_PITCH_OFFSET_SCALE = 0.45
        // Conservative clamps to avoid aggressive camera motion from noisy tracking input.
        private const val MAX_YAW_DEGREES = 45.0
        private const val MAX_PITCH_DEGREES = 30.0
        private const val JAW_WEIGHT = 0.5f
        private const val SMILE_WEIGHT = 0.35f
        private const val BLINK_WEIGHT = 0.15f
        private const val EXPRESSION_Y_OFFSET_SCALE = 0.18
        private const val EXPRESSION_Z_OFFSET_SCALE = 0.24
        private const val LIGHT_DIRECTION_X = 0.35f
        private const val LIGHT_DIRECTION_Y = -1.0f
        private const val LIGHT_DIRECTION_Z = -0.45f
        private const val LIGHT_COLOR_R = 1.0f
        private const val LIGHT_COLOR_G = 0.98f
        private const val LIGHT_COLOR_B = 0.95f
        private const val LIGHT_INTENSITY = 110_000.0f
        private const val INDIRECT_LIGHT_INTENSITY = 35_000.0f
        private const val LOG_TAG = "VrmAvatarRender"

        private fun initializeNativeBindings() {
            if (nativeBindingsInitialized) {
                return
            }
            synchronized(AndroidFilamentAvatarRenderer::class.java) {
                if (nativeBindingsInitialized) {
                    return
                }
                Filament.init()
                Gltfio.init()
                nativeBindingsInitialized = true
            }
        }
    }
}
