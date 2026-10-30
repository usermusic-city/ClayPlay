package com.clayplay.scene

import com.clayplay.gl.GpuMesh
import com.clayplay.gl.Mat4
import com.clayplay.gl.MeshBuilder
import com.clayplay.gl.MeshGen

/**
 * The little clay room.
 *
 * Sizing is chosen so eight toys fit without overlapping and the camera can see
 * everything from a fixed three-quarter angle. World units are abstract; the
 * camera distance sets the apparent scale.
 */
object Room {
    const val HALF_W = 3.4f
    const val HALF_D = 3.4f

    /**
     * Height of the rug's top surface above y=0.
     *
     * The rug is a slightly domed slab (superellipsoid with sy = 0.055 centred
     * at y = 0.012), so its surface is highest at the middle and falls to the
     * base at the rim. Toys must rest on that surface rather than on the flat
     * y=0 plane, otherwise distant toys sink into the rug while the centre toy
     * floats.
     */
    const val FLOOR_Y = 0.067f

    private const val RUG_BASE_Y = 0.012f
    private const val RUG_RISE = 0.055f

    /** Rug surface height at a horizontal position. */
    fun rugHeightAt(x: Float, z: Float): Float {
        val r = kotlin.math.sqrt(x * x + z * z) / PLAY_RADIUS
        val k = if (r > 1f) 1f else r
        val h = kotlin.math.sqrt((1f - k * k).coerceAtLeast(0f))
        return RUG_BASE_Y + RUG_RISE * h
    }

    /** Rug radius, used to keep toys inside the play area. */
    const val PLAY_RADIUS = 2.85f

    class Built(val meshes: List<GpuMesh>)

    private fun m(
        tx: Float, ty: Float, tz: Float,
        sx: Float = 1f, sy: Float = 1f, sz: Float = 1f,
        ry: Float = 0f
    ): FloatArray {
        var out = Mat4.translation(tx, ty, tz)
        if (ry != 0f) out = Mat4.multiply(out, Mat4.rotationY(ry))
        if (sx != 1f || sy != 1f || sz != 1f) out = Mat4.multiply(out, Mat4.scale(sx, sy, sz))
        return out
    }

    /**
     * Floor: a wide slab. Its top face sits exactly at y=0 so toy resting
     * heights are measured from the floor plane with no offset fudge.
     */
    fun floor(): GpuMesh {
        val mb = MeshBuilder()
        mb.add(
            MeshGen.block(HALF_W, 0.22f, HALF_D, ClayPalette.SAND, seed = 101, roundness = 0.22f, rings = 10, segs = 30),
            m(0f, -0.22f, 0f)
        )
        return GpuMesh(mb.build())
    }

    /** Two back walls forming a corner, so the room reads as a diorama. */
    fun walls(): GpuMesh {
        val mb = MeshBuilder()
        val wallH = 1.9f
        // back wall (-z)
        mb.add(
            MeshGen.block(HALF_W, wallH, 0.20f, ClayPalette.CREAM, seed = 102, roundness = 0.16f, rings = 10, segs = 30),
            m(0f, wallH, -HALF_D)
        )
        // left wall (-x)
        mb.add(
            MeshGen.block(0.20f, wallH, HALF_D, ClayPalette.CREAM, seed = 103, roundness = 0.16f, rings = 10, segs = 30),
            m(-HALF_W, wallH, 0f)
        )
        return GpuMesh(mb.build())
    }

    /** Round rug under the toys; gives the play area a visual boundary. */
    fun rug(): GpuMesh {
        val mb = MeshBuilder()
        mb.add(
            MeshGen.superellipsoid(
                PLAY_RADIUS, 0.055f, PLAY_RADIUS, 1f, 1f, 8, 44,
                ClayPalette.SAGE, seed = 104, wobble = 0.006f, colorJitter = 0.02f, bottomShade = 0.02f
            ),
            m(0f, 0.012f, 0f)
        )
        // inner concentric ring for a woven look
        mb.add(
            MeshGen.torus(PLAY_RADIUS * 0.62f, 0.028f, ClayPalette.MOSS, seed = 105, rings = 8, segs = 40),
            m(0f, 0.045f, 0f)
        )
        return GpuMesh(mb.build())
    }

    /** A few small pebbles for scale cues; static, part of the room. */
    fun pebbles(): GpuMesh {
        val mb = MeshBuilder()
        val spots = arrayOf(
            floatArrayOf(-2.45f, -1.75f, 0.13f),
            floatArrayOf(2.30f, 1.95f, 0.10f),
            floatArrayOf(1.55f, -2.45f, 0.085f),
            floatArrayOf(-2.10f, 2.25f, 0.11f),
            floatArrayOf(2.75f, -0.35f, 0.075f)
        )
        spots.forEachIndexed { i, s ->
            mb.add(
                MeshGen.sphere(s[2], ClayPalette.STONE, seed = 110 + i, rings = 10, segs = 14, wobble = 0.05f),
                m(s[0], s[2] * 0.75f, s[1], 1f, 0.62f, 1f)
            )
        }
        return GpuMesh(mb.build())
    }

    fun buildAll(): Built = Built(listOf(floor(), walls(), rug(), pebbles()))
}
