package com.clayplay.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.clayplay.gl.GpuMesh
import com.clayplay.gl.Mat4
import com.clayplay.gl.MeshGen
import com.clayplay.gl.ShaderProgram
import com.clayplay.gl.ShaderSource
import com.clayplay.gl.Vec3
import com.clayplay.scene.Room
import com.clayplay.scene.Toy
import com.clayplay.scene.ToyMood
import com.clayplay.toys.ToyFactory
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.random
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The whole 3D scene: camera, lighting, toys, and the interaction springs.
 *
 * Rendering order is deliberate:
 *   1. backdrop gradient (fills the screen, no depth write)
 *   2. opaque room + toys (depth tested and written)
 *   3. contact shadows (blended, depth test on but no depth write)
 *
 * Shadows go last so they can blend over the floor they sit on without needing
 * a separate render target.
 */
class SceneRenderer(
    private val callbacks: Callbacks
) : GLSurfaceView.Renderer {

    interface Callbacks {
        fun onToyTouched(toy: Toy)
        fun onToyReleased(toy: Toy)
        fun onReady()
    }

    // ------------------------------------------------------------- GL objects

    private var clayProgram: ShaderProgram? = null
    private var shadowProgram: ShaderProgram? = null
    private var backdropProgram: ShaderProgram? = null

    private var meshFloor: GpuMesh? = null
    private var meshWalls: GpuMesh? = null
    private var meshRug: GpuMesh? = null
    private var meshPebbles: GpuMesh? = null
    private var meshShadow: GpuMesh? = null
    private var meshBackdrop: GpuMesh? = null

    val toys = ArrayList<Toy>()

    // ---------------------------------------------------------------- camera

    // Three-quarter view looking slightly down into the room corner.
    private val camEye = floatArrayOf(4.6f, 4.9f, 6.6f)
    private val camCenter = floatArrayOf(0f, 0.15f, 0f)
    private val camUp = floatArrayOf(0f, 1f, 0f)
    private val camPos = floatArrayOf(4.6f, 4.9f, 6.6f)

    // Slight orbit drift so the scene never feels frozen.
    private var orbit = 0f
    private var userOrbit = 0f
    private var orbitVelocity = 0f

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val invViewProj = FloatArray(16)

    // ------------------------------------------------------------- lighting

    private val lightDir = Vec3.normalize(floatArrayOf(-0.55f, 0.86f, 0.42f))
    private val lightColor = floatArrayOf(1.06f, 0.97f, 0.86f)
    private val skyColor = floatArrayOf(0.60f, 0.65f, 0.74f)
    private val groundColor = floatArrayOf(0.52f, 0.38f, 0.28f)
    private val fogColor = floatArrayOf(0.83f, 0.78f, 0.72f)

    // --------------------------------------------------------------- runtime

    private var viewW = 1
    private var viewH = 1
    private var lastTime = 0L
    private var frameCount = 0

    private var selected: Toy? = null
    var onSelectionChanged: ((Toy?) -> Unit)? = null

    private val tmpMat = FloatArray(16)
    private val tmpMat2 = FloatArray(16)
    private val tmpMvp = FloatArray(16)
    private val tmpModel = FloatArray(16)
    private val tmpNm = FloatArray(9)
    private val tmpP = FloatArray(3)

    private var ready = false
    private val pending = ArrayList<() -> Unit>()

    /** Runs on the GL thread, immediately if already initialised. */
    fun onGlThread(block: () -> Unit) {
        synchronized(pending) {
            if (ready) block() else pending.add(block)
        }
    }

    // ------------------------------------------------------------ GL callbacks

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glClearColor(fogColor[0], fogColor[1], fogColor[2], 1f)

        clayProgram = ShaderProgram(ShaderSource.CLAY_VS, ShaderSource.CLAY_FS, "ClayProgram")
        shadowProgram = ShaderProgram(ShaderSource.SHADOW_VS, ShaderSource.SHADOW_FS, "ShadowProgram")
        backdropProgram = ShaderProgram(ShaderSource.BACKDROP_VS, ShaderSource.BACKDROP_FS, "BackdropProgram")

        meshFloor = Room.floor()
        meshWalls = Room.walls()
        meshRug = Room.rug()
        meshPebbles = Room.pebbles()
        meshShadow = GpuMesh(MeshGen.quadXZ(1f))
        meshBackdrop = GpuMesh(MeshGen.fullScreenQuad())

        if (toys.isEmpty()) {
            toys.addAll(ToyFactory.buildAll())
            layoutToys()
        }

        // Restore toy poses if a GL context was lost and rebuilt.
        lastTime = 0L

        synchronized(pending) {
            ready = true
            pending.forEach { it() }
            pending.clear()
        }
        callbacks.onReady()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = if (width == 0) 1 else width
        viewH = if (height == 0) 1 else height
        GLES30.glViewport(0, 0, viewW, viewH)
        updateProjection()
    }

    private fun updateProjection() {
        val aspect = viewW.toFloat() / viewH.toFloat()
        // Narrow screens get a wider FOV so the room still fits horizontally.
        val fov = if (aspect < 0.62f) 52f else 44f
        Mat4.perspective(fov, aspect, 0.1f, 60f, proj)
    }

    /** Even ring layout, slightly jittered so it does not look machine placed. */
    private fun layoutToys() {
        val n = toys.size
        val ringRadius = 1.85f
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * (Math.PI * 2).toFloat() - 0.35f
            val jitter = (i % 3) * 0.05f
            val t = toys[i]
            t.x = cos(a) * (ringRadius + jitter)
            t.z = sin(a) * (ringRadius + jitter)
            t.phase = (i * 1.37f) % 6.28f
            t.yaw = a + 1.5708f
            t.groundY = Room.rugHeightAt(t.x, t.z)
        }
        // Put the blob right at the front centre so the first tap has an obvious target.
        toys.firstOrNull { it.id == "blob" }?.let {
            it.x = 0f
            it.z = 2.05f
            it.groundY = Room.rugHeightAt(it.x, it.z)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastTime == 0L) 0f else ((now - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastTime = now

        updateCamera(dt)
        animate(dt)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        drawBackdrop()
        drawScene()
        drawShadows()

        frameCount++
    }

    // ------------------------------------------------------------- camera math

    private fun updateCamera(dt: Float) {
        // gentle idle orbit plus the user's drag, decaying back toward centre
        orbit += dt * 0.045f
        if (abs(orbitVelocity) > 0.0001f) {
            userOrbit += orbitVelocity * dt
            orbitVelocity *= (1f - dt * 2.6f).coerceIn(0f, 1f)
            // spring back so the diorama framing is always recoverable
            userOrbit *= (1f - dt * 0.35f).coerceIn(0f, 1f)
        }

        val a = orbit + userOrbit
        val r = 8.2f
        camPos[0] = sin(a) * r * 0.72f + 1.6f
        camPos[1] = 5.1f
        camPos[2] = cos(a) * r * 0.78f + 2.2f

        camEye[0] = camPos[0]; camEye[1] = camPos[1]; camEye[2] = camPos[2]
        Mat4.lookAt(camEye, camCenter, camUp, view)
        Mat4.multiply(proj, view, viewProj)
        Mat4.invert4(viewProj, invViewProj)
    }

    // --------------------------------------------------------------- animation

    private fun animate(dt: Float) {
        val t = frameCount * 0.016f
        for (toy in toys) {
            toy.tickMood(dt)

            // Idle: slow breathing bob, per-toy phase.
            val bobAmp = when (toy.mood) {
                ToyMood.HAPPY -> 0.055f
                ToyMood.STARTLED -> 0.020f
                ToyMood.WAKING -> 0.070f
                ToyMood.IDLE -> 0.022f
            }
            val bobSpeed = when (toy.mood) {
                ToyMood.HAPPY -> 7.5f
                ToyMood.STARTLED -> 9.0f
                ToyMood.WAKING -> 5.0f
                ToyMood.IDLE -> 1.5f
            }
            val targetY = sin(t * bobSpeed + toy.phase) * bobAmp

            // Critically damped spring toward the target height.
            val k = 190f
            val damping = 17f
            val ay = (targetY - toy.y) * k - toy.vy * damping
            toy.vy += ay * dt
            toy.y += toy.vy * dt

            // Squash spring relaxes to zero.
            val squashTarget = 0f
            val asq = (squashTarget - toy.squash) * 220f - toy.vSquash * 14f
            toy.vSquash += asq * dt
            toy.squash += toy.vSquash * dt
            if (abs(toy.squash) < 0.0005f && abs(toy.vSquash) < 0.0005f) {
                toy.squash = 0f
                toy.vSquash = 0f
            }

            // Slow turn, faster when happy.
            val spin = when (toy.mood) {
                ToyMood.HAPPY -> 2.4f
                ToyMood.WAKING -> 1.2f
                else -> 0.12f
            }
            toy.yaw += spin * dt

            // Highlight eases toward selected state.
            val hTarget = if (toy === selected) 1f else 0f
            toy.highlight += (hTarget - toy.highlight) * (dt * 9f).coerceIn(0f, 1f)
        }
    }

    // --------------------------------------------------------------- rendering

    private fun drawBackdrop() {
        val prog = backdropProgram ?: return
        val mesh = meshBackdrop ?: return
        if (!prog.isValid) return

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        prog.use()
        prog.setVec3("uTop", 0.90f, 0.86f, 0.80f)
        prog.setVec3("uBottom", 0.74f, 0.69f, 0.64f)
        mesh.draw()
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawScene() {
        val prog = clayProgram ?: return
        if (!prog.isValid) return
        prog.use()

        prog.setMat4("uViewProj", viewProj)
        prog.setVec3("uLightDir", lightDir[0], lightDir[1], lightDir[2])
        prog.setVec3("uLightColor", lightColor[0], lightColor[1], lightColor[2])
        prog.setVec3("uSkyColor", skyColor[0], skyColor[1], skyColor[2])
        prog.setVec3("uGroundColor", groundColor[0], groundColor[1], groundColor[2])
        prog.setVec3("uCamPos", camPos[0], camPos[1], camPos[2])
        prog.setVec3("uFogColor", fogColor[0], fogColor[1], fogColor[2])
        prog.setFloat("uFogNear", 7.0f)
        prog.setFloat("uFogFar", 20.0f)
        prog.setFloat("uWrap", 0.62f)
        prog.setFloat("uAlpha", 1f)
        prog.setVec3("uTint", 1f, 1f, 1f)
        prog.setFloat("uHighlight", 0f)

        // static room pieces
        drawStatic(prog, meshFloor)
        drawStatic(prog, meshWalls)
        drawStatic(prog, meshRug)
        drawStatic(prog, meshPebbles)

        // toys
        for (toy in toys) {
            prog.setVec3("uTint", 1f, 1f, 1f)
            prog.setFloat("uHighlight", toy.highlight)
            val model = toyModel(toy)
            prog.setMat4("uModel", model)
            prog.setMat3("uNormalMat", Mat4.normalMatrix(model, tmpNm))
            prog.setMat4("uMVP", Mat4.multiply(viewProj, model, tmpMvp))
            toy.mesh.draw()
            prog.setFloat("uHighlight", 0f)
        }
    }

    private fun drawStatic(prog: ShaderProgram, mesh: GpuMesh?) {
        if (mesh == null) return
        val model = Mat4.identity(tmpMat2)
        prog.setMat4("uModel", model)
        prog.setMat3("uNormalMat", Mat4.normalMatrix(model, tmpNm))
        prog.setMat4("uMVP", Mat4.multiply(viewProj, model, tmpMvp))
        mesh.draw()
    }

    /**
     * Toy transform: squash-and-stretch around the toy's middle, then the idle
     * spin and lean. Volume is roughly preserved by scaling the two other axes
     * inversely, which is what makes a poke read as soft rather than a resize.
     */
    private fun toyModel(toy: Toy): FloatArray {
        val s = toy.squash
        val sy = 1f - s
        val sxz = 1f + s * 0.5f

        // Build into a dedicated buffer: the caller stores an MVP computed from
        // the returned matrix, so sharing a scratch array here would corrupt
        // the normal matrix for the very same draw.
        Mat4.translation(toy.x, toy.centreY(), toy.z, tmpModel)

        // lean into the squash direction, like clay being pushed
        if (abs(s) > 0.001f) {
            Mat4.multiply(tmpModel, Mat4.rotationZ(s * 0.28f), tmpModel)
        }
        Mat4.multiply(tmpModel, Mat4.rotationY(toy.yaw), tmpModel)
        Mat4.multiply(tmpModel, Mat4.scale(sxz, sy, sxz), tmpModel)
        return tmpModel
    }

    private fun drawShadows() {
        val prog = shadowProgram ?: return
        val mesh = meshShadow ?: return
        if (!prog.isValid) return

        prog.use()
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        for (toy in toys) {
            // Shadow shrinks and brightens as the toy lifts.
            val lift = ((toy.y + 0.1f) * 2.2f).coerceIn(0f, 0.5f)
            val r = toy.radius * (1.15f - lift * 0.45f) * (1f + toy.squash * 0.30f)
            val strength = (0.34f - lift * 0.35f).coerceIn(0.04f, 0.34f)

            // Offset the shadow slightly opposite the key light.
            val ox = toy.x - lightDir[0] * 0.10f
            val oz = toy.z - lightDir[2] * 0.10f

            // Sits just above the rug's surface at that spot, following its dome.
            val shadowY = Room.rugHeightAt(toy.x, toy.z) + 0.004f

            Mat4.multiply(
                Mat4.translation(ox, shadowY, oz),
                Mat4.scale(r, 1f, r),
                tmpMat
            )
            prog.setMat4("uMVP", Mat4.multiply(viewProj, tmpMat, tmpMvp))
            prog.setFloat("uStrength", strength)
            prog.setVec3("uShadowColor", 0.34f, 0.24f, 0.20f)
            mesh.draw()
        }

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    // ----------------------------------------------------------------- picking

    /** Screen-space tap -> world ray -> nearest toy hit. */
    fun pick(screenX: Float, screenY: Float): Toy? {
        if (!ready) return null

        // NDC. Screen y grows downward, NDC y grows upward.
        val ndcX = (screenX / viewW) * 2f - 1f
        val ndcY = 1f - (screenY / viewH) * 2f

        val nearP = FloatArray(3)
        val farP = FloatArray(3)
        Mat4.transformPoint(invViewProj, ndcX, ndcY, -1f, nearP)
        Mat4.transformPoint(invViewProj, ndcX, ndcY, 1f, farP)

        var dx = farP[0] - nearP[0]
        var dy = farP[1] - nearP[1]
        var dz = farP[2] - nearP[2]
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-6f) return null
        dx /= len; dy /= len; dz /= len

        var best: Toy? = null
        var bestT = Float.MAX_VALUE

        for (toy in toys) {
            // Toy bodies are roughly spherical; test as a sphere offset by the
            // squash so a squashed toy still picks correctly.
            val cx = toy.x
            val cy = toy.centreY()
            val cz = toy.z
            val r = toy.radius * (1f + abs(toy.squash) * 0.4f)

            val ox = nearP[0] - cx
            val oy = nearP[1] - cy
            val oz = nearP[2] - cz

            val b = ox * dx + oy * dy + oz * dz
            val c = ox * ox + oy * oy + oz * oz - r * r
            val disc = b * b - c
            if (disc < 0f) continue

            val sq = sqrt(disc)
            var hitT = -b - sq
            if (hitT < 0f) hitT = -b + sq
            if (hitT < 0f) continue

            if (hitT < bestT) {
                bestT = hitT
                best = toy
            }
        }

        // Also allow picking by the floor plane near a toy: a tap at the toy's
        // base is easier to land than a tap on its top on a phone.
        if (best == null) {
            if (abs(dy) > 1e-5f) {
                val tFloor = (Room.FLOOR_Y - nearP[1]) / dy
                if (tFloor > 0f) {
                    val fx = nearP[0] + dx * tFloor
                    val fz = nearP[2] + dz * tFloor
                    var closest: Toy? = null
                    var closestD = Float.MAX_VALUE
                    for (toy in toys) {
                        val d = toy.distSqTo(fx, fz)
                        val limit = (toy.radius * 1.35f)
                        if (d < limit * limit && d < closestD) {
                            closestD = d
                            closest = toy
                        }
                    }
                    best = closest
                }
            }
        }
        return best
    }

    // ------------------------------------------------------------ interaction

    /** Applies the poke response: squash, bounce and mood change. */
    fun poke(toy: Toy) {
        onGlThread {
            toy.squash = (toy.squash + 0.26f).coerceAtMost(0.34f)
            toy.vSquash = 0f
            toy.vy += 1.35f
            toy.setMood(ToyMood.HAPPY, 1.1f)
            select(toy)
        }
        callbacks.onToyTouched(toy)
    }

    fun release(toy: Toy) {
        callbacks.onToyReleased(toy)
    }

    private fun select(toy: Toy?) {
        if (selected === toy) return
        selected = toy
        val s = toy
        onSelectionChanged?.let { cb ->
            // always post to the main thread; GL thread must not touch views
            mainHandler?.post { cb(s) }
        }
    }

    private var mainHandler: android.os.Handler? = null

    fun attachHandler(h: android.os.Handler) { mainHandler = h }

    fun dragOrbit(deltaX: Float) {
        orbitVelocity -= deltaX * 0.010f
        orbitVelocity = orbitVelocity.coerceIn(-2.2f, 2.2f)
    }

    var selectedToy: Toy? get() = selected

    fun releaseAll() {
        clayProgram?.release(); clayProgram = null
        shadowProgram?.release(); shadowProgram = null
        backdropProgram?.release(); backdropProgram = null
        meshFloor?.release(); meshFloor = null
        meshWalls?.release(); meshWalls = null
        meshRug?.release(); meshRug = null
        meshPebbles?.release(); meshPebbles = null
        meshShadow?.release(); meshShadow = null
        meshBackdrop?.release(); meshBackdrop = null
        toys.forEach { it.mesh.release() }
        toys.clear()
        ready = false
    }
}
