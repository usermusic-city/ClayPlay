package com.clayplay.gl

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Column-major 4x4 matrix helpers, laid out for direct upload to OpenGL
 * (transpose = GL_FALSE).
 *
 * m[0..3]  = column 0
 * m[4..7]  = column 1
 * m[8..11] = column 2
 * m[12..15]= column 3
 */
object Mat4 {

    fun identity(out: FloatArray = FloatArray(16)): FloatArray {
        java.util.Arrays.fill(out, 0f)
        out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f
        return out
    }

    fun copy(src: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        System.arraycopy(src, 0, out, 0, 16)
        return out
    }

    /** out = a * b  (b applied first, then a -> standard column-major semantics) */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        val r = if (out === a || out === b) FloatArray(16) else out
        for (c in 0 until 4) {
            val b0 = b[c * 4]
            val b1 = b[c * 4 + 1]
            val b2 = b[c * 4 + 2]
            val b3 = b[c * 4 + 3]
            r[c * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            r[c * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            r[c * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            r[c * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
        if (r !== out) System.arraycopy(r, 0, out, 0, 16)
        return out
    }

    fun translation(x: Float, y: Float, z: Float, out: FloatArray = FloatArray(16)): FloatArray {
        identity(out)
        out[12] = x; out[13] = y; out[14] = z
        return out
    }

    fun scale(x: Float, y: Float, z: Float, out: FloatArray = FloatArray(16)): FloatArray {
        identity(out)
        out[0] = x; out[5] = y; out[10] = z
        return out
    }

    fun rotationX(rad: Float, out: FloatArray = FloatArray(16)): FloatArray {
        identity(out)
        val c = cos(rad); val s = sin(rad)
        out[5] = c; out[6] = s
        out[9] = -s; out[10] = c
        return out
    }

    fun rotationY(rad: Float, out: FloatArray = FloatArray(16)): FloatArray {
        identity(out)
        val c = cos(rad); val s = sin(rad)
        out[0] = c; out[2] = -s
        out[8] = s; out[10] = c
        return out
    }

    fun rotationZ(rad: Float, out: FloatArray = FloatArray(16)): FloatArray {
        identity(out)
        val c = cos(rad); val s = sin(rad)
        out[0] = c; out[1] = s
        out[4] = -s; out[5] = c
        return out
    }

    /**
     * Perspective projection for a right-handed, OpenGL-style NDC (z in [-1,1]).
     * fovYDeg is the full vertical field of view.
     */
    fun perspective(
        fovYDeg: Float,
        aspect: Float,
        near: Float,
        far: Float,
        out: FloatArray = FloatArray(16)
    ): FloatArray {
        java.util.Arrays.fill(out, 0f)
        val f = 1f / tan(Math.toRadians(fovYDeg.toDouble() / 2.0)).toFloat()
        out[0] = f / aspect
        out[5] = f
        out[10] = (far + near) / (near - far)
        out[11] = -1f
        out[14] = (2f * far * near) / (near - far)
        return out
    }

    /** Right-handed look-at view matrix. */
    fun lookAt(
        eye: FloatArray,
        center: FloatArray,
        up: FloatArray,
        out: FloatArray = FloatArray(16)
    ): FloatArray {
        val fx = center[0] - eye[0]
        val fy = center[1] - eye[1]
        val fz = center[2] - eye[2]
        val fl = sqrt(fx * fx + fy * fy + fz * fz).let { if (it == 0f) 1f else it }
        val sx = fx / fl; val sy = fy / fl; val sz = fz / fl

        // s = normalize(f x up)
        var ux = sy * up[2] - sz * up[1]
        var uy = sz * up[0] - sx * up[2]
        var uz = sx * up[1] - sy * up[0]
        val ul = sqrt(ux * ux + uy * uy + uz * uz).let { if (it == 0f) 1f else it }
        ux /= ul; uy /= ul; uz /= ul

        // u = s x f
        val vx = uy * sz - uz * sy
        val vy = uz * sx - ux * sz
        val vz = ux * sy - uy * sx

        out[0] = ux; out[4] = uy; out[8] = uz; out[12] = -(ux * eye[0] + uy * eye[1] + uz * eye[2])
        out[1] = vx; out[5] = vy; out[9] = vz; out[13] = -(vx * eye[0] + vy * eye[1] + vz * eye[2])
        out[2] = -sx; out[6] = -sy; out[10] = -sz; out[14] = (sx * eye[0] + sy * eye[1] + sz * eye[2])
        out[3] = 0f; out[7] = 0f; out[11] = 0f; out[15] = 1f
        return out
    }

    /**
     * Inverse-transpose of the upper-left 3x3, packed into a 3x3 matrix stored
     * as 9 floats in column-major order. Needed for normals under non-uniform
     * scale (toys are squashed on collision).
     */
    fun normalMatrix(model: FloatArray, out: FloatArray = FloatArray(9)): FloatArray {
        val a00 = model[0]; val a01 = model[1]; val a02 = model[2]
        val a10 = model[4]; val a11 = model[5]; val a12 = model[6]
        val a20 = model[8]; val a21 = model[9]; val a22 = model[10]

        // cofactors of the 3x3
        val c00 = a11 * a22 - a12 * a21
        val c01 = a12 * a20 - a10 * a22
        val c02 = a10 * a21 - a11 * a20
        val c10 = a02 * a21 - a01 * a22
        val c11 = a00 * a22 - a02 * a20
        val c12 = a01 * a20 - a00 * a21
        val c20 = a01 * a12 - a02 * a11
        val c21 = a02 * a10 - a00 * a12
        val c22 = a00 * a11 - a01 * a10

        val det = a00 * c00 + a10 * c01 + a20 * c02
        val invDet = if (det == 0f) 0f else 1f / det

        // inverse = transpose(cofactor) / det ; we need inverse-transpose = cofactor / det
        out[0] = c00 * invDet; out[1] = c01 * invDet; out[2] = c02 * invDet
        out[3] = c10 * invDet; out[4] = c11 * invDet; out[5] = c12 * invDet
        out[6] = c20 * invDet; out[7] = c21 * invDet; out[8] = c22 * invDet
        return out
    }

    /**
     * General 4x4 inverse (cofactor expansion). Used to unproject a screen tap
     * back into a world-space ray for picking.
     */
    fun invert4(m: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        val a00 = m[0];  val a01 = m[1];  val a02 = m[2];  val a03 = m[3]
        val a10 = m[4];  val a11 = m[5];  val a12 = m[6];  val a13 = m[7]
        val a20 = m[8];  val a21 = m[9];  val a22 = m[10]; val a23 = m[11]
        val a30 = m[12]; val a31 = m[13]; val a32 = m[14]; val a33 = m[15]

        val b00 = a00 * a11 - a01 * a10
        val b01 = a00 * a12 - a02 * a10
        val b02 = a00 * a13 - a03 * a10
        val b03 = a01 * a12 - a02 * a11
        val b04 = a01 * a13 - a03 * a11
        val b05 = a02 * a13 - a03 * a12
        val b06 = a20 * a31 - a21 * a30
        val b07 = a20 * a32 - a22 * a30
        val b08 = a20 * a33 - a23 * a30
        val b09 = a21 * a32 - a22 * a31
        val b10 = a21 * a33 - a23 * a31
        val b11 = a22 * a33 - a23 * a32

        var det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06
        if (det == 0f) return identity(out)
        det = 1f / det

        out[0]  = (a11 * b11 - a12 * b10 + a13 * b09) * det
        out[1]  = (a02 * b10 - a01 * b11 - a03 * b09) * det
        out[2]  = (a31 * b05 - a32 * b04 + a33 * b03) * det
        out[3]  = (a22 * b04 - a21 * b05 - a23 * b03) * det
        out[4]  = (a12 * b08 - a10 * b11 - a13 * b07) * det
        out[5]  = (a00 * b11 - a02 * b08 + a03 * b07) * det
        out[6]  = (a32 * b02 - a30 * b05 - a33 * b01) * det
        out[7]  = (a20 * b05 - a22 * b02 + a23 * b01) * det
        out[8]  = (a10 * b10 - a11 * b08 + a13 * b06) * det
        out[9]  = (a01 * b08 - a00 * b10 - a03 * b06) * det
        out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * det
        out[11] = (a21 * b02 - a20 * b04 - a23 * b00) * det
        out[12] = (a11 * b07 - a10 * b09 - a12 * b06) * det
        out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * det
        out[14] = (a31 * b01 - a30 * b03 - a32 * b00) * det
        out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * det
        return out
    }

    /** Transform a point by a 4x4 matrix, with perspective divide. */
    fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        val w = m[3] * x + m[7] * y + m[11] * z + m[15]
        val iw = if (w != 0f) 1f / w else 1f
        out[0] = (m[0] * x + m[4] * y + m[8] * z + m[12]) * iw
        out[1] = (m[1] * x + m[5] * y + m[9] * z + m[13]) * iw
        out[2] = (m[2] * x + m[6] * y + m[10] * z + m[14]) * iw
    }
}

object Vec3 {
    fun of(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

    fun normalize(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (l == 0f) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    fun distance(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float {
        val dx = ax - bx; val dy = ay - by; val dz = az - bz
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
