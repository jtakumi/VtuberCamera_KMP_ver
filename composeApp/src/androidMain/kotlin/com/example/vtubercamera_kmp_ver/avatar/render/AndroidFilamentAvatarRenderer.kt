package com.example.vtubercamera_kmp_ver.avatar.render

import android.content.Context
import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.vtubercamera_kmp_ver.avatar.state.AvatarRenderState
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import kotlin.math.sin
import android.view.View as AndroidView
import com.google.android.filament.View as FilamentView

class AndroidFilamentAvatarRenderer(
    context: Context,
) {
    val hostView: AndroidView

    private val surfaceView: SurfaceView
    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: FilamentView
    private val cameraEntity: Int
    private val camera: Camera
    private val uiHelper: UiHelper
    private val displayHelper: DisplayHelper
    private var swapChain: SwapChain? = null
    private var renderState: AvatarRenderState = AvatarRenderState.Neutral
    private var destroyed = false

    init {
        Filament.init()

        surfaceView = SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            holder.setFormat(PixelFormat.TRANSLUCENT)
        }
        hostView = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            clipChildren = false
            clipToPadding = false
            addView(surfaceView)
        }

        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        displayHelper = DisplayHelper(context)

        configureRenderer()
        configureView()
        configureSurface()
    }

    fun updateRenderState(nextRenderState: AvatarRenderState) {
        renderState = nextRenderState
    }

    fun render(frameTimeNanos: Long) {
        val currentSwapChain = swapChain ?: return
        if (destroyed || !uiHelper.isReadyToRender) {
            return
        }

        applyRenderState()
        if (renderer.beginFrame(currentSwapChain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    fun destroy() {
        if (destroyed) {
            return
        }

        destroyed = true
        uiHelper.detach()
        displayHelper.detach()
        destroySwapChain()
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
        camera.lookAt(
            0.0,
            0.0,
            4.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
        )
    }

    private fun applyRenderState() {
        val trackingInfluence = if (renderState.isTracking) {
            renderState.trackingConfidence.coerceIn(0f, 1f).toDouble()
        } else {
            0.0
        }
        val yawRadians = Math.toRadians(renderState.rig.headYawDegrees.coerceIn(-45f, 45f).toDouble())
        val pitchRadians = Math.toRadians(renderState.rig.headPitchDegrees.coerceIn(-30f, 30f).toDouble())

        camera.lookAt(
            sin(yawRadians) * 0.8 * trackingInfluence,
            sin(pitchRadians) * 0.45 * trackingInfluence,
            4.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
        )
    }

    private fun configureSurface() {
        uiHelper.isOpaque = false
        uiHelper.isMediaOverlay = true
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                destroySwapChain()
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                displayHelper.attach(renderer, surfaceView.display)
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
        uiHelper.attachTo(surfaceView)
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

    private fun destroySwapChain() {
        swapChain?.let { currentSwapChain ->
            engine.destroySwapChain(currentSwapChain)
            swapChain = null
        }
    }
}
