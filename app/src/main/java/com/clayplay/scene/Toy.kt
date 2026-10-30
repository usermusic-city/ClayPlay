package com.clayplay.scene

import com.clayplay.gl.GpuMesh

/**
 * Interaction state a toy can be in. Kept deliberately small: the app is about
 * poking clay, so "idle / waking / reacting" covers every behaviour.
 */
enum class ToyMood { IDLE, WAKING, HAPPY, STARTLED }

/**
 * One toy instance in the room.
 *
 * Position is in world units where the room floor is y=0 and the room is
 * roughly 10 x 10. `squash` is a transient non-uniform scale applied on top of
 * the base size so a poke deforms the toy instead of translating it.
 */
class Toy(
    val id: String,
    val displayName: String,
    val mesh: GpuMesh,
    val radius: Float,
    val restingHeight: Float
) {
    var x = 0f
    var y = 0f      // vertical offset above resting position
    var z = 0f

    /** Visual rotation, radians. */
    var yaw = 0f

    /** Transient deformation amount, roughly -0.3..0.3. */
    var squash = 0f

    /** Idle bob phase offset so toys never move in lockstep. */
    var phase = 0f

    var mood = ToyMood.IDLE
        private set

    /** Seconds remaining in the current mood. */
    private var moodTime = 0f

    /** Highlight amount driven by hover/selection, 0..1. */
    var highlight = 0f

    /** Velocities used by the springs. */
    var vy = 0f
    var vSquash = 0f

    /**
     * Surface height the toy stands on. Set from the rug's local height when the
     * toy is laid out, because the rug is domed rather than flat.
     */
    var groundY = 0f

    fun setMood(m: ToyMood, duration: Float) {
        mood = m
        moodTime = duration
    }

    fun tickMood(dt: Float) {
        if (moodTime > 0f) {
            moodTime -= dt
            if (moodTime <= 0f) mood = ToyMood.IDLE
        }
    }

    /** Y position of the toy's centre: ground surface + base offset + lift. */
    fun centreY(): Float = groundY + restingHeight + y

    /** Y position of the toy's lowest point, used for shadow projection. */
    fun baseY(): Float = groundY + y

    fun distSqTo(px: Float, pz: Float): Float {
        val dx = x - px
        val dz = z - pz
        return dx * dx + dz * dz
    }
}
