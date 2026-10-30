package com.clayplay

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.clayplay.audio.ClayAudio
import com.clayplay.audio.Haptics
import com.clayplay.render.SceneRenderer
import com.clayplay.scene.Toy
import com.clayplay.ui.HudView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Single-screen app: a GLSurfaceView for the diorama, a HudView overlay, and
 * touch handling that decides between "poke a toy" and "orbit the camera".
 *
 * Gesture policy: a short press with little movement is a poke; anything that
 * travels more than a few dp becomes an orbit drag. Using movement rather than
 * press duration means a poke registers on release, which feels responsive
 * while still letting the user drag the room.
 */
class MainActivity : Activity(), SceneRenderer.Callbacks {

    private lateinit var glView: GLSurfaceView
    private lateinit var hud: HudView
    private lateinit var renderer: SceneRenderer

    private var audio: ClayAudio? = null
    private var haptics: Haptics? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragAccum = 0f
    private var isDragging = false

    private var pressedToy: Toy? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audio = try { ClayAudio() } catch (t: Throwable) {
            Log.w(TAG, "audio init failed: " + t.message); null
        }
        haptics = try { Haptics(this) } catch (t: Throwable) {
            Log.w(TAG, "haptics init failed: " + t.message); null
        }

        val root = FrameLayout(this)

        renderer = SceneRenderer(this)

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            // 24-bit depth with alpha, plus stencil for safety on odd drivers.
            setEGLConfigChooser(8, 8, 8, 8, 24, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(
            glView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        hud = HudView(this)
        root.addView(
            hud,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        hud.onToggleAudio = {
            val a = audio
            val now = !(a?.isEnabled() ?: false)
            a?.setEnabled(now)
            hud.audioOn = now
            haptics?.tap(14)
        }
        hud.onToggleHaptics = {
            val h = haptics
            val now = !(h?.isEnabled() ?: false)
            h?.setEnabled(now)
            hud.hapticsOn = now
        }
        hud.audioOn = audio?.isEnabled() ?: false
        hud.hapticsOn = haptics?.isEnabled() ?: false

        renderer.attachHandler(mainHandler)
        renderer.onSelectionChanged = { toy ->
            hud.setSelectedToy(toy?.displayName)
        }

        glView.setOnTouchListener { _, ev -> handleTouch(ev) }
        // HudView handles its own touches and only consumes them when they land
        // on a toggle, letting everything else reach the GL view underneath.

        setContentView(root)
    }

    // ------------------------------------------------------------------ touch

    private fun handleTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                lastX = ev.x
                dragAccum = 0f
                isDragging = false
                pressedToy = renderer.pick(ev.x, ev.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - lastX
                lastX = ev.x
                dragAccum += Math.abs(dx) + Math.abs(ev.y - downY) * 0.15f

                // 12dp of travel before it counts as an orbit gesture, so a
                // finger roll during a tap does not spin the room.
                if (!isDragging && dragAccum > 12f * resources.displayMetrics.density) {
                    isDragging = true
                }
                if (isDragging) {
                    renderer.dragOrbit(dx)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val toy = pressedToy ?: renderer.pick(ev.x, ev.y)
                    if (toy != null) {
                        renderer.poke(toy)
                        audio?.play(toy.id)
                        haptics?.tap()
                    }
                }
                pressedToy = null
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedToy = null
                isDragging = false
                return true
            }
        }
        return false
    }

    // -------------------------------------------------------- renderer events

    override fun onToyTouched(toy: Toy) {
        // selection is reported through onSelectionChanged, nothing else to do
    }

    override fun onToyReleased(toy: Toy) {
    }

    override fun onReady() {
        // First frame is up; the HUD is static so nothing to sync here.
    }

    // ------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        glView.queueEvent { renderer.releaseAll() }
        audio?.release()
        audio = null
    }

    companion object {
        private const val TAG = "ClayPlay"
    }
}
