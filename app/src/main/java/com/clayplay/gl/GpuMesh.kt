package com.clayplay.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * GPU-side buffers for one [Geometry]. Three float VBOs (position / normal /
 * colour) plus an index buffer, uploaded once at creation.
 *
 * Buffer contents are uploaded and released in the same call, so we never hold
 * a mapped pointer and there is nothing to leak besides the buffer names.
 */
class GpuMesh(geo: Geometry) {

    private val vboPos = IntArray(1)
    private val vboNrm = IntArray(1)
    private val vboCol = IntArray(1)
    private val ibo = IntArray(1)

    val indexCount: Int = geo.indices.size
    val vertexCount: Int = geo.vertexCount

    init {
        GLES30.glGenBuffers(1, vboPos, 0)
        GLES30.glGenBuffers(1, vboNrm, 0)
        GLES30.glGenBuffers(1, vboCol, 0)
        GLES30.glGenBuffers(1, ibo, 0)

        uploadFloats(vboPos[0], geo.positions)
        uploadFloats(vboNrm[0], geo.normals)
        uploadFloats(vboCol[0], geo.colors)

        val ib: IntBuffer = ByteBuffer
            .allocateDirect(geo.indices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        ib.put(geo.indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            geo.indices.size * 4,
            ib,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun uploadFloats(bufferId: Int, data: FloatArray) {
        val fb: FloatBuffer = ByteBuffer
            .allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        fb.put(data).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufferId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Binds attribs 0/1/2 and the element buffer, then draws. */
    fun draw() {
        if (indexCount == 0) return

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboPos[0])
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboNrm[0])
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboCol[0])
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            indexCount,
            GLES30.GL_UNSIGNED_INT,
            0
        )

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES30.glDeleteBuffers(4, intArrayOf(vboPos[0], vboNrm[0], vboCol[0], ibo[0]), 0)
    }
}
