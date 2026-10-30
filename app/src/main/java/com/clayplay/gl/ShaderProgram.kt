package com.clayplay.gl

import android.opengl.GLES30
import android.util.Log

/** Thin wrapper over a GLES 3.0 program with cached uniform locations. */
class ShaderProgram(vertexSrc: String, fragmentSrc: String, private val tag: String = "Shader") {

    var id: Int = 0
        private set

    private val uniforms = HashMap<String, Int>()
    private val attribs = HashMap<String, Int>()

    val isValid: Boolean get() = id != 0

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) {
            if (vs != 0) GLES30.glDeleteShader(vs)
            if (fs != 0) GLES30.glDeleteShader(fs)
            id = 0
        } else {
            val p = GLES30.glCreateProgram()
            GLES30.glAttachShader(p, vs)
            GLES30.glAttachShader(p, fs)
            GLES30.glLinkProgram(p)
            val status = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            if (status[0] == 0) {
                Log.e(tag, "link failed: " + GLES30.glGetProgramInfoLog(p))
                GLES30.glDeleteProgram(p)
                id = 0
            } else {
                id = p
            }
        }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        if (s == 0) return 0
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val status = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(tag, "compile failed: " + GLES30.glGetShaderInfoLog(s) + "\n---\n" + src)
            GLES30.glDeleteShader(s)
            return 0
        }
        return s
    }

    fun use() { if (id != 0) GLES30.glUseProgram(id) }

    fun uniform(name: String): Int {
        uniforms[name]?.let { return it }
        val loc = GLES30.glGetUniformLocation(id, name)
        uniforms[name] = loc
        return loc
    }

    fun attrib(name: String): Int {
        attribs[name]?.let { return it }
        val loc = GLES30.glGetAttribLocation(id, name)
        attribs[name] = loc
        return loc
    }

    fun setMat4(name: String, m: FloatArray) {
        val loc = uniform(name)
        if (loc >= 0) GLES30.glUniformMatrix4fv(loc, 1, false, m, 0)
    }

    fun setMat3(name: String, m: FloatArray) {
        val loc = uniform(name)
        if (loc >= 0) GLES30.glUniformMatrix3fv(loc, 1, false, m, 0)
    }

    fun setVec3(name: String, x: Float, y: Float, z: Float) {
        val loc = uniform(name)
        if (loc >= 0) GLES30.glUniform3f(loc, x, y, z)
    }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val loc = uniform(name)
        if (loc >= 0) GLES30.glUniform4f(loc, x, y, z, w)
    }

    fun setVec2(name: String, x: Float, y: Float) {
        val loc = uniform(name)
        if (loc >= 0) GLES30.glUniform2f(loc, x, y)
    }

    fun setFloat(name: String, v: Float) {
        val loc = uniform(name)
        if (loc >= 0) GLES30.glUniform1f(loc, v)
    }

    fun setInt(name: String, v: Int) {
        val loc = uniform(name)
        if (loc >= 0) GLES30.glUniform1i(loc, v)
    }

    fun release() {
        if (id != 0) GLES30.glDeleteProgram(id)
        id = 0
        uniforms.clear()
        attribs.clear()
    }
}
