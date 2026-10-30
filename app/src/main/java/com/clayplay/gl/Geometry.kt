package com.clayplay.gl

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interleaved-free geometry: separate position / normal / color arrays of
 * stride 3, plus a triangle index list. Uploaded as three VBOs + one IBO.
 */
class Geometry(
    val positions: FloatArray,
    val normals: FloatArray,
    val colors: FloatArray,
    val indices: IntArray
) {
    val vertexCount: Int get() = positions.size / 3
}

/**
 * Procedural clay geometry.
 *
 * Everything the toys are made of comes out of one parametric primitive: the
 * superellipsoid. Two exponents control the shape:
 *
 *   e1 = e2 = 1.0  -> ellipsoid / sphere
 *   e1 = e2 = 0.30 -> rounded box (clay block)
 *   e1 = 0.30, e2 = 1.0 -> capsule / cylinder-ish
 *
 * That means a sphere, a cube of clay, a sausage body and a wheel hub are all
 * the same code path with different numbers, which keeps the toy definitions
 * declarative and the shapes visually consistent.
 */
object MeshGen {

    private const val TWO_PI = (Math.PI * 2.0).toFloat()

    // ---------------------------------------------------------------- helpers

    private fun signPow(base: Float, e: Float): Float {
        if (e == 1f) return base
        val a = abs(base)
        val p = if (a == 0f) 0f else a.toDouble().pow(e.toDouble()).toFloat()
        return if (base < 0f) -p else p
    }

    /** Cheap deterministic value-noise in [0,1) from three integers. */
    private fun hash3(x: Int, y: Int, z: Int): Float {
        var h = x * 374761393 + y * 668265263 + z * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
    }

    /**
     * Smooth low-frequency wobble that makes a mathematically perfect surface
     * read as hand-pressed clay. Deterministic per seed so the toy looks the
     * same across relaunches.
     */
    private fun clayWobble(u: Float, v: Float, seed: Int): Float {
        val s = seed.toFloat() * 0.73f
        val a = sin(u * 3.1f + s) * cos(v * 2.7f - s * 1.3f)
        val b = sin(v * 5.3f + u * 1.7f + s * 0.6f)
        val c = cos(u * 7.9f - v * 4.3f + s)
        return a * 0.45f + b * 0.32f + c * 0.23f
    }

    /**
     * Build a UV-grid surface by sampling [point] over the parameter rectangle.
     *
     * Normals are taken as the normalised cross product of central-difference
     * tangents rather than an analytic gradient. The analytic gradient of a
     * superellipsoid blows up as the exponents grow (|x|^(2/e - 1) terms), and
     * it would also ignore the wobble displacement. Finite differences match
     * the actual displaced surface exactly, which is what the lighting needs.
     */
    private fun buildGrid(
        rings: Int,
        segs: Int,
        uStart: Float,
        uEnd: Float,
        vStart: Float,
        vEnd: Float,
        seed: Int,
        color: FloatArray,
        colorJitter: Float,
        bottomShade: Float,
        point: (u: Float, v: Float, out: FloatArray) -> Unit
    ): Geometry {
        val nv = (rings + 1) * (segs + 1)
        val pos = FloatArray(nv * 3)
        val nrm = FloatArray(nv * 3)
        val col = FloatArray(nv * 3)

        val p0 = FloatArray(3)
        val pu1 = FloatArray(3); val pu0 = FloatArray(3)
        val pv1 = FloatArray(3); val pv0 = FloatArray(3)

        val du = (uEnd - uStart) / segs
        val dv = (vEnd - vStart) / rings
        // Half-step central differences: stable at poles and in tight corners.
        val eu = du * 0.5f
        val ev = dv * 0.5f

        var vi = 0
        for (r in 0..rings) {
            val v = vStart + dv * r
            val tv = r.toFloat() / rings      // 0 = bottom, 1 = top
            for (s in 0..segs) {
                val u = uStart + du * s

                point(u, v, p0)
                point(u + eu, v, pu1); point(u - eu, v, pu0)
                point(u, v + ev, pv1); point(u, v - ev, pv0)

                val tu0 = pu1[0] - pu0[0]; val tu1 = pu1[1] - pu0[1]; val tu2 = pu1[2] - pu0[2]
                val tv0 = pv1[0] - pv0[0]; val tv1 = pv1[1] - pv0[1]; val tv2 = pv1[2] - pv0[2]

                // cross(tangent_v, tangent_u) points outward for this parametrisation.
                var nx = tv1 * tu2 - tv2 * tu1
                var ny = tv2 * tu0 - tv0 * tu2
                var nz = tv0 * tu1 - tv1 * tu0
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (nl > 1e-8f) {
                    nx /= nl; ny /= nl; nz /= nl
                } else {
                    nx = 0f; ny = 1f; nz = 0f
                }

                val o = vi * 3
                pos[o] = p0[0]; pos[o + 1] = p0[1]; pos[o + 2] = p0[2]
                nrm[o] = nx; nrm[o + 1] = ny; nrm[o + 2] = nz

                // Per-vertex tonal variation fakes pigment unevenness in the clay.
                val j = (hash3(s, r, seed) - 0.5f) * 2f * colorJitter
                val shade = 1f - bottomShade * (1f - tv)
                col[o] = (color[0] * (1f + j)).coerceIn(0f, 1f) * shade
                col[o + 1] = (color[1] * (1f + j)).coerceIn(0f, 1f) * shade
                col[o + 2] = (color[2] * (1f + j)).coerceIn(0f, 1f) * shade

                vi++
            }
        }

        val idx = IntArray(rings * segs * 6)
        var ii = 0
        for (r in 0 until rings) {
            for (s in 0 until segs) {
                val a = r * (segs + 1) + s
                val b = a + 1
                val c = a + (segs + 1)
                val d = c + 1
                idx[ii++] = a; idx[ii++] = c; idx[ii++] = b
                idx[ii++] = b; idx[ii++] = c; idx[ii++] = d
            }
        }

        return Geometry(pos, nrm, col, idx)
    }

    // ------------------------------------------------------------- primitives

    /**
     * Superellipsoid with independent half-extents. This is the workhorse:
     * spheres, clay blocks, sausage bodies, wheels and dome shapes all come
     * from here.
     */
    fun superellipsoid(
        sx: Float,
        sy: Float,
        sz: Float,
        e1: Float = 1f,
        e2: Float = 1f,
        rings: Int = 20,
        segs: Int = 28,
        color: FloatArray,
        seed: Int = 0,
        wobble: Float = 0.012f,
        colorJitter: Float = 0.035f,
        bottomShade: Float = 0.16f
    ): Geometry {
        val halfPi = (Math.PI / 2.0).toFloat()
        return buildGrid(
            rings = rings,
            segs = segs,
            uStart = -Math.PI.toFloat(),
            uEnd = Math.PI.toFloat(),
            vStart = -halfPi,
            vEnd = halfPi,
            seed = seed,
            color = color,
            colorJitter = colorJitter,
            bottomShade = bottomShade
        ) { u, v, out ->
            val cv = signPow(cos(v), e1)
            val sv = signPow(sin(v), e1)
            val cu = signPow(cos(u), e2)
            val su = signPow(sin(u), e2)
            val w = 1f + wobble * clayWobble(u, v, seed)
            out[0] = sx * cv * cu * w
            out[1] = sy * sv * w
            out[2] = sz * cv * su * w
        }
    }

    /** Rounded clay block. */
    fun block(
        sx: Float, sy: Float, sz: Float,
        color: FloatArray,
        seed: Int = 0,
        roundness: Float = 0.30f,
        rings: Int = 18, segs: Int = 26
    ) = superellipsoid(
        sx, sy, sz, e1 = roundness, e2 = roundness,
        rings = rings, segs = segs, color = color, seed = seed,
        wobble = 0.008f, bottomShade = 0.14f
    )

    /** Sphere in clay colours. */
    fun sphere(
        r: Float,
        color: FloatArray,
        seed: Int = 0,
        rings: Int = 18, segs: Int = 24,
        wobble: Float = 0.015f
    ) = superellipsoid(
        r, r, r, 1f, 1f, rings, segs, color, seed, wobble, 0.03f, 0.12f
    )

    /** Torus, used for wheels, handles and stacking rings. */
    fun torus(
        major: Float,
        minor: Float,
        color: FloatArray,
        seed: Int = 0,
        rings: Int = 16, segs: Int = 24,
        wobble: Float = 0.012f
    ): Geometry {
        return buildGrid(
            rings = rings,
            segs = segs,
            uStart = -Math.PI.toFloat(),
            uEnd = Math.PI.toFloat(),
            vStart = -Math.PI.toFloat(),
            vEnd = Math.PI.toFloat(),
            seed = seed,
            color = color,
            colorJitter = 0.03f,
            bottomShade = 0.10f
        ) { u, v, out ->
            val w = 1f + wobble * clayWobble(u, v, seed)
            val rr = major + minor * cos(v) * w
            out[0] = rr * cos(u)
            out[1] = minor * sin(v) * w
            out[2] = rr * sin(u)
        }
    }

    /** Flat XZ quad centred on the origin, normal +Y. Used for shadows. */
    fun quadXZ(size: Float): Geometry {
        val h = size * 0.5f
        val pos = floatArrayOf(
            -h, 0f, -h,
            h, 0f, -h,
            h, 0f, h,
            -h, 0f, h
        )
        val nrm = floatArrayOf(
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f
        )
        val col = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
        val idx = intArrayOf(0, 2, 1, 0, 3, 2)
        return Geometry(pos, nrm, col, idx)
    }

    /**
     * Quad already in normalised device coordinates (z = 0), for the full-screen
     * backdrop pass. Wound counter-clockwise seen from +Z so it survives the
     * back-face cull that is enabled for the rest of the scene.
     */
    fun fullScreenQuad(): Geometry {
        val pos = floatArrayOf(
            -1f, -1f, 0f,
            1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f
        )
        val nrm = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f
        )
        val col = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
        val idx = intArrayOf(0, 1, 2, 0, 2, 3)
        return Geometry(pos, nrm, col, idx)
    }
}

/**
 * Accumulates several primitives (each with its own local transform and tint)
 * into a single Geometry, so one toy costs one draw call instead of five.
 */
class MeshBuilder {
    private val pos = ArrayList<Float>()
    private val nrm = ArrayList<Float>()
    private val col = ArrayList<Float>()
    private val idx = ArrayList<Int>()

    private val nm = FloatArray(9)

    fun add(geo: Geometry, model: FloatArray, tint: FloatArray? = null): MeshBuilder {
        val base = pos.size / 3
        Mat4.normalMatrix(model, nm)

        for (i in 0 until geo.vertexCount) {
            val i3 = i * 3
            val x = geo.positions[i3]
            val y = geo.positions[i3 + 1]
            val z = geo.positions[i3 + 2]

            // position -> model
            val wx = model[0] * x + model[4] * y + model[8] * z + model[12]
            val wy = model[1] * x + model[5] * y + model[9] * z + model[13]
            val wz = model[2] * x + model[6] * y + model[10] * z + model[14]
            pos.add(wx); pos.add(wy); pos.add(wz)

            // normal -> inverse-transpose, then renormalise
            val ox = geo.normals[i3]
            val oy = geo.normals[i3 + 1]
            val oz = geo.normals[i3 + 2]
            var nx = nm[0] * ox + nm[3] * oy + nm[6] * oz
            var ny = nm[1] * ox + nm[4] * oy + nm[7] * oz
            var nz = nm[2] * ox + nm[5] * oy + nm[8] * oz
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l > 1e-8f) { nx /= l; ny /= l; nz /= l } else { ny = 1f }
            nrm.add(nx); nrm.add(ny); nrm.add(nz)

            if (tint != null) {
                col.add(geo.colors[i3] * tint[0])
                col.add(geo.colors[i3 + 1] * tint[1])
                col.add(geo.colors[i3 + 2] * tint[2])
            } else {
                col.add(geo.colors[i3])
                col.add(geo.colors[i3 + 1])
                col.add(geo.colors[i3 + 2])
            }
        }

        for (k in geo.indices) idx.add(k + base)
        return this
    }

    fun build(): Geometry {
        val p = FloatArray(pos.size)
        val n = FloatArray(nrm.size)
        val c = FloatArray(col.size)
        val i = IntArray(idx.size)
        for (k in p.indices) p[k] = pos[k]
        for (k in n.indices) n[k] = nrm[k]
        for (k in c.indices) c[k] = col[k]
        for (k in i.indices) i[k] = idx[k]
        return Geometry(p, n, c, i)
    }
}
