package com.clayplay.toys

import com.clayplay.gl.GpuMesh
import com.clayplay.gl.Mat4
import com.clayplay.gl.MeshBuilder
import com.clayplay.gl.MeshGen
import com.clayplay.scene.ClayPalette
import com.clayplay.scene.Toy

/**
 * Builds the eight toys.
 *
 * Every toy is authored in a local space centred on its own origin with a
 * nominal radius of about 0.5, then positioned by the room layout. Keeping
 * authorship centred means the squash-and-stretch animation can scale around
 * the toy's middle without the toy sinking through the floor.
 *
 * Each toy is baked into ONE geometry (one draw call) by MeshBuilder, which
 * matters on mid-range phones where per-draw overhead dominates at this
 * triangle count.
 */
object ToyFactory {

    private fun m(
        tx: Float, ty: Float, tz: Float,
        sx: Float = 1f, sy: Float = 1f, sz: Float = 1f,
        rx: Float = 0f, ry: Float = 0f, rz: Float = 0f
    ): FloatArray {
        var out = Mat4.translation(tx, ty, tz)
        if (rx != 0f) out = Mat4.multiply(out, Mat4.rotationX(rx))
        if (ry != 0f) out = Mat4.multiply(out, Mat4.rotationY(ry))
        if (rz != 0f) out = Mat4.multiply(out, Mat4.rotationZ(rz))
        if (sx != 1f || sy != 1f || sz != 1f) out = Mat4.multiply(out, Mat4.scale(sx, sy, sz))
        return out
    }

    // ------------------------------------------------------------------ 1 blob

    /** 黏土团子: a plain pressed ball with two dimple eyes. The starting toy. */
    fun blob(): Toy {
        val mb = MeshBuilder()
        mb.add(
            MeshGen.superellipsoid(
                0.5f, 0.46f, 0.5f, 0.85f, 0.85f,
                rings = 24, segs = 32,
                color = ClayPalette.TERRA, seed = 11, wobble = 0.030f
            ),
            m(0f, 0f, 0f)
        )
        // two inset eyes, pushed slightly into the surface
        for (sgn in intArrayOf(-1, 1)) {
            mb.add(
                MeshGen.sphere(0.062f, ClayPalette.INK, seed = 40 + sgn, rings = 12, segs = 16, wobble = 0.02f),
                m(0.155f * sgn, 0.10f, 0.435f)
            )
        }
        // tiny mouth dent
        mb.add(
            MeshGen.superellipsoid(
                0.075f, 0.028f, 0.030f, 1f, 1f, 10, 14,
                ClayPalette.TERRADARK, seed = 77, wobble = 0.01f
            ),
            m(0f, -0.075f, 0.445f)
        )
        return Toy("blob", "黏土团子", GpuMesh(mb.build()), 0.52f, 0.46f)
    }

    // -------------------------------------------------------------- 2 mushroom

    /** 小蘑菇: cream stem, terracotta cap with lighter spots. */
    fun mushroom(): Toy {
        val mb = MeshBuilder()
        // stem: slightly tapered capsule
        mb.add(
            MeshGen.superellipsoid(
                0.17f, 0.26f, 0.17f, 0.55f, 1f, 16, 22,
                ClayPalette.CREAM, seed = 21, wobble = 0.02f
            ),
            m(0f, -0.18f, 0f)
        )
        // cap: flattened dome
        mb.add(
            MeshGen.superellipsoid(
                0.46f, 0.30f, 0.46f, 0.95f, 1f, 20, 28,
                ClayPalette.RUST, seed = 22, wobble = 0.025f
            ),
            m(0f, 0.10f, 0f)
        )
        // three cream spots on the cap
        val spots = arrayOf(
            floatArrayOf(0.20f, 0.30f, 0.12f),
            floatArrayOf(-0.22f, 0.26f, -0.10f),
            floatArrayOf(0.02f, 0.34f, -0.24f)
        )
        spots.forEachIndexed { i, s ->
            mb.add(
                MeshGen.sphere(0.072f, ClayPalette.WHITE, seed = 60 + i, rings = 10, segs = 14, wobble = 0.02f),
                m(s[0], s[1], s[2], 1f, 0.45f, 1f)
            )
        }
        return Toy("mushroom", "小蘑菇", GpuMesh(mb.build()), 0.50f, 0.34f)
    }

    // ------------------------------------------------------------------- 3 cat

    /** 陶土小猫: seated body, round head, two ear wedges, tail curl. */
    fun cat(): Toy {
        val mb = MeshBuilder()
        // body: pear shaped
        mb.add(
            MeshGen.superellipsoid(
                0.30f, 0.32f, 0.26f, 0.90f, 1f, 20, 26,
                ClayPalette.OCHRE, seed = 31, wobble = 0.025f
            ),
            m(0f, -0.16f, 0f)
        )
        // head
        mb.add(
            MeshGen.superellipsoid(
                0.27f, 0.25f, 0.25f, 0.92f, 1f, 20, 26,
                ClayPalette.OCHRE, seed = 32, wobble = 0.025f
            ),
            m(0f, 0.20f, 0.02f)
        )
        // ears
        for (sgn in intArrayOf(-1, 1)) {
            mb.add(
                MeshGen.superellipsoid(
                    0.10f, 0.15f, 0.06f, 0.45f, 0.45f, 12, 16,
                    ClayPalette.OCHRE, seed = 33 + sgn, wobble = 0.02f
                ),
                m(0.150f * sgn, 0.395f, 0.02f, rz = -0.32f * sgn)
            )
        }
        // eyes
        for (sgn in intArrayOf(-1, 1)) {
            mb.add(
                MeshGen.sphere(0.050f, ClayPalette.INK, seed = 35 + sgn, rings = 10, segs = 14, wobble = 0.02f),
                m(0.098f * sgn, 0.245f, 0.225f)
            )
        }
        // muzzle
        mb.add(
            MeshGen.superellipsoid(
                0.070f, 0.048f, 0.045f, 1f, 1f, 10, 14,
                ClayPalette.BLUSH, seed = 36, wobble = 0.02f
            ),
            m(0f, 0.155f, 0.245f)
        )
        // tail curling around the base
        mb.add(
            MeshGen.torus(0.20f, 0.043f, ClayPalette.OCHRE, seed = 37, rings = 14, segs = 20),
            m(0.10f, -0.40f, -0.12f, rx = 1.25f, rz = 0.35f)
        )
        return Toy("cat", "陶土小猫", GpuMesh(mb.build()), 0.42f, 0.44f)
    }

    // ------------------------------------------------------------------- 4 top

    /** 泥巴陀螺: cone body, knob on top, ring at the waist. */
    fun top(): Toy {
        val mb = MeshBuilder()
        // body: cone -> superellipsoid with e1 < 1 and y offset gives a tapered form
        mb.add(
            MeshGen.superellipsoid(
                0.40f, 0.34f, 0.40f, 0.62f, 1f, 24, 30,
                ClayPalette.PLUM, seed = 41, wobble = 0.02f
            ),
            m(0f, 0.02f, 0f)
        )
        // conical tip pointing down
        mb.add(
            MeshGen.superellipsoid(
                0.16f, 0.20f, 0.16f, 0.35f, 1f, 14, 20,
                ClayPalette.PLUM, seed = 42, wobble = 0.015f
            ),
            m(0f, -0.30f, 0f)
        )
        // cream ring around the waist
        mb.add(
            MeshGen.torus(0.385f, 0.042f, ClayPalette.CREAM, seed = 43, rings = 12, segs = 24),
            m(0f, -0.02f, 0f)
        )
        // top knob
        mb.add(
            MeshGen.sphere(0.085f, ClayPalette.SAND, seed = 44, rings = 12, segs = 16, wobble = 0.02f),
            m(0f, 0.38f, 0f)
        )
        return Toy("top", "泥巴陀螺", GpuMesh(mb.build()), 0.44f, 0.44f)
    }

    // ------------------------------------------------------------------- 5 pot

    /** 陶罐: a thrown pot profile built from stacked superellipsoid sections. */
    fun pot(): Toy {
        val mb = MeshBuilder()
        // belly
        mb.add(
            MeshGen.superellipsoid(
                0.36f, 0.30f, 0.36f, 0.95f, 1f, 22, 30,
                ClayPalette.TERRA, seed = 51, wobble = 0.022f
            ),
            m(0f, -0.08f, 0f)
        )
        // neck
        mb.add(
            MeshGen.superellipsoid(
                0.18f, 0.16f, 0.18f, 0.85f, 1f, 16, 22,
                ClayPalette.TERRA, seed = 52, wobble = 0.015f
            ),
            m(0f, 0.22f, 0f)
        )
        // rim
        mb.add(
            MeshGen.torus(0.175f, 0.045f, ClayPalette.TERRADARK, seed = 53, rings = 12, segs = 24),
            m(0f, 0.355f, 0f)
        )
        // foot
        mb.add(
            MeshGen.superellipsoid(
                0.17f, 0.055f, 0.17f, 0.5f, 1f, 10, 20,
                ClayPalette.TERRADARK, seed = 54, wobble = 0.01f
            ),
            m(0f, -0.36f, 0f)
        )
        // decorative band
        mb.add(
            MeshGen.torus(0.355f, 0.030f, ClayPalette.CREAM, seed = 55, rings = 10, segs = 24),
            m(0f, -0.05f, 0f)
        )
        return Toy("pot", "小陶罐", GpuMesh(mb.build()), 0.40f, 0.42f)
    }

    // ---------------------------------------------------------------- 6 blocks

    /** 积木塔: three stacked clay blocks, each rotated a little. */
    fun blocks(): Toy {
        val mb = MeshBuilder()
        val cols = arrayOf(ClayPalette.SAGE, ClayPalette.OCHRE, ClayPalette.BLUSH)
        val sizes = floatArrayOf(0.30f, 0.24f, 0.18f)
        var y = -0.34f
        for (i in 0 until 3) {
            val s = sizes[i]
            mb.add(
                MeshGen.block(s, s * 0.85f, s, cols[i], seed = 60 + i),
                m(0f, y + s * 0.85f, 0f, ry = 0.22f * i - 0.2f)
            )
            y += s * 1.70f
        }
        return Toy("blocks", "积木塔", GpuMesh(mb.build()), 0.40f, 0.40f)
    }

    // ---------------------------------------------------------------- 7 planet

    /** 小星球: sphere with a tilted torus ring and a small moon. */
    fun planet(): Toy {
        val mb = MeshBuilder()
        mb.add(
            MeshGen.superellipsoid(
                0.36f, 0.36f, 0.36f, 1f, 1f, 22, 30,
                ClayPalette.SKYDUST, seed = 71, wobble = 0.028f
            ),
            m(0f, 0f, 0f)
        )
        // surface patches
        val patches = arrayOf(
            floatArrayOf(0.18f, 0.16f, 0.26f, 0.55f),
            floatArrayOf(-0.22f, -0.10f, 0.24f, 0.42f),
            floatArrayOf(0.05f, -0.26f, -0.22f, 0.38f)
        )
        patches.forEachIndexed { i, p ->
            mb.add(
                MeshGen.sphere(p[3] * 0.22f, ClayPalette.SAGE, seed = 72 + i, rings = 10, segs = 14, wobble = 0.02f),
                m(p[0], p[1], p[2], 1f, 0.55f, 1f)
            )
        }
        // ring
        mb.add(
            MeshGen.torus(0.56f, 0.045f, ClayPalette.OCHRE, seed = 76, rings = 12, segs = 34),
            m(0f, 0f, 0f, rx = 1.28f, rz = 0.42f)
        )
        return Toy("planet", "小星球", GpuMesh(mb.build()), 0.60f, 0.46f)
    }

    // ------------------------------------------------------------------ 8 duck

    /** 陶土小鸭: body, head, beak and a tuft. */
    fun duck(): Toy {
        val mb = MeshBuilder()
        // body
        mb.add(
            MeshGen.superellipsoid(
                0.40f, 0.30f, 0.32f, 0.95f, 1f, 20, 28,
                ClayPalette.SAND, seed = 81, wobble = 0.025f
            ),
            m(0f, -0.16f, 0f)
        )
        // head
        mb.add(
            MeshGen.superellipsoid(
                0.22f, 0.22f, 0.22f, 1f, 1f, 18, 24,
                ClayPalette.SAND, seed = 82, wobble = 0.022f
            ),
            m(0.16f, 0.22f, 0f)
        )
        // beak
        mb.add(
            MeshGen.superellipsoid(
                0.10f, 0.045f, 0.065f, 0.6f, 0.7f, 10, 14,
                ClayPalette.RUST, seed = 83, wobble = 0.015f
            ),
            m(0.355f, 0.18f, 0f, ry = 0.25f)
        )
        // eyes
        for (sgn in intArrayOf(-1, 1)) {
            mb.add(
                MeshGen.sphere(0.040f, ClayPalette.INK, seed = 84 + sgn, rings = 10, segs = 12, wobble = 0.02f),
                m(0.235f, 0.275f, 0.145f * sgn)
            )
        }
        // tail tuft
        mb.add(
            MeshGen.superellipsoid(
                0.13f, 0.10f, 0.10f, 0.4f, 0.4f, 10, 14,
                ClayPalette.SAND, seed = 86, wobble = 0.02f
            ),
            m(-0.38f, 0.02f, 0f, rz = 0.55f)
        )
        return Toy("duck", "陶土小鸭", GpuMesh(mb.build()), 0.44f, 0.42f)
    }

    /** All eight, in room-placement order. */
    fun buildAll(): List<Toy> = listOf(
        blob(), mushroom(), cat(), top(), pot(), blocks(), planet(), duck()
    )
}
