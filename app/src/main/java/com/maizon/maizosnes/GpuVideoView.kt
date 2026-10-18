package com.maizon.maizosnes

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** OpenGL ES 2 pipeline, opt-in. Snes9x still renders its native 256/512px
 * source; the GPU scales to the display and applies optional fragment effects.
 * No full-screen upload/readback or offscreen FBO needed.
 */
@Composable
internal fun GpuVideoSurface(
    shader: GpuShader,
    modifier: Modifier,
    onReady: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = remember(context) {
        SnesGlesView(context) { ready -> onReady(ready) }
    }
    AndroidView(
        factory = { view },
        modifier = modifier,
        update = { it.shader = shader }
    )
    DisposableEffect(view) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            view.onPause()
            onReady(false)
        }
    }
}

private class SnesGlesView(
    context: Context,
    private val surfaceStatus: (Boolean) -> Unit
) : GLSurfaceView(context) {
    private val snesRenderer = SnesGlRenderer { ok ->
        post { surfaceStatus(ok) }
    }
    var shader: GpuShader
        get() = snesRenderer.shader
        set(value) { snesRenderer.shader = value }

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setRenderer(snesRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }
}

private class SnesGlRenderer(private val status: (Boolean) -> Unit) :
    GLSurfaceView.Renderer {
    @Volatile var shader = GpuShader.NEAREST
    private val rgba565 = ByteBuffer.allocateDirect(1024 * 1024 * 2)
        .order(ByteOrder.nativeOrder())
    private val metadata = IntArray(3)
    private var lastVersion = -1
    private var lastWidth = 0
    private var lastHeight = 0
    private var program = 0
    private var texture = 0
    private var vertexPosition = -1
    private var vertexUv = -1
    private var effectLocation = -1
    private var texelLocation = -1
    private var textureLocation = -1
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(
                -1f, -1f, 0f, 1f,  1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,   1f, 1f, 1f, 0f
            ))
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            program = compileProgram()
            vertexPosition = GLES20.glGetAttribLocation(program, "aPosition")
            vertexUv = GLES20.glGetAttribLocation(program, "aTexCoord")
            effectLocation = GLES20.glGetUniformLocation(program, "uEffect")
            texelLocation = GLES20.glGetUniformLocation(program, "uTexel")
            textureLocation = GLES20.glGetUniformLocation(program, "uTexture")
            require(vertexPosition >= 0 && vertexUv >= 0) { "Atributos GLSL ausentes." }
            val names = IntArray(1)
            GLES20.glGenTextures(1, names, 0)
            texture = names[0]
            require(texture != 0) { "OpenGL não criou a textura RGB565." }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            lastWidth = 0
            lastHeight = 0
            lastVersion = -1
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            status(true)
        } catch (error: Exception) {
            Log.e("MaizoSNES-GL", "Falha na GPU; selecione renderizador CPU.", error)
            program = 0
            status(false)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        val hasFrame = NativeBridge.nativeCopyGpuFrame(rgba565, metadata)
        if (hasFrame && metadata[0] in 1..1024 && metadata[1] in 1..1024) {
            val w = metadata[0]
            val h = metadata[1]
            if (lastVersion != metadata[2] || lastWidth != w || lastHeight != h) {
                rgba565.position(0)
                rgba565.limit(w * h * 2)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
                if (lastWidth != w || lastHeight != h) {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
                        w, h, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, rgba565)
                } else {
                    GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
                        w, h, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, rgba565)
                }
                lastWidth = w
                lastHeight = h
                lastVersion = metadata[2]
            }
        }
        if (lastWidth == 0) return
        val interpolation = shader == GpuShader.LINEAR ||
            shader == GpuShader.SMOOTH || shader == GpuShader.SHARP
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        val glFilter = if (interpolation) GLES20.GL_LINEAR else GLES20.GL_NEAREST
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, glFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, glFilter)
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(textureLocation, 0)
        GLES20.glUniform1i(effectLocation, shader.glId)
        GLES20.glUniform2f(texelLocation, 1f / lastWidth, 1f / lastHeight)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(vertexPosition)
        GLES20.glVertexAttribPointer(vertexPosition, 2, GLES20.GL_FLOAT,
            false, 16, vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(vertexUv)
        GLES20.glVertexAttribPointer(vertexUv, 2, GLES20.GL_FLOAT,
            false, 16, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(vertexPosition)
        GLES20.glDisableVertexAttribArray(vertexUv)
    }

    private fun compileProgram(): Int {
        val vertex = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vUv;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vUv = aTexCoord;
            }
        """.trimIndent()
        val fragment = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTexture;
            uniform int uEffect;
            uniform vec2 uTexel;
            vec3 sampleColor(vec2 uv) {
                return texture2D(uTexture, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
            }
            void main() {
                vec2 uv = vUv;
                if (uEffect == 3) {
                    vec2 centered = uv * 2.0 - 1.0;
                    centered *= 1.0 + 0.08 * dot(centered, centered);
                    uv = centered * 0.5 + 0.5;
                    if (uv.x < 0.0 || uv.x > 1.0 ||
                        uv.y < 0.0 || uv.y > 1.0) {
                        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                        return;
                    }
                }
                vec3 pixel = sampleColor(uv);
                if (uEffect == 2) {
                    pixel = pixel * 0.60 +
                        sampleColor(uv + vec2(uTexel.x, 0.0)) * 0.10 +
                        sampleColor(uv - vec2(uTexel.x, 0.0)) * 0.10 +
                        sampleColor(uv + vec2(0.0, uTexel.y)) * 0.10 +
                        sampleColor(uv - vec2(0.0, uTexel.y)) * 0.10;
                } else if (uEffect == 3) {
                    vec2 d = uv * 2.0 - 1.0;
                    float vignette = 1.0 - 0.22 * dot(d, d);
                    float scanline = 0.84 + 0.16 * sin(gl_FragCoord.y * 2.0);
                    pixel *= vignette * scanline;
                } else if (uEffect == 4) {
                    float grid = step(0.11, fract(gl_FragCoord.y / 3.0));
                    pixel *= 0.77 + 0.23 * grid;
                } else if (uEffect == 5) {
                    vec3 side = sampleColor(uv + vec2(uTexel.x, 0.0)) +
                        sampleColor(uv - vec2(uTexel.x, 0.0));
                    pixel = clamp(pixel * 1.5 - side * 0.25, 0.0, 1.0);
                } else if (uEffect == 6) {
                    float gray = dot(pixel, vec3(0.299, 0.587, 0.114));
                    pixel = vec3(gray);
                } else if (uEffect == 7) {
                    float luma = dot(pixel, vec3(0.299, 0.587, 0.114));
                    pixel = clamp(mix(vec3(luma), pixel, 1.3), 0.0, 1.0);
                }
                gl_FragColor = vec4(pixel, 1.0);
            }
        """.trimIndent()
        fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val status = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
            require(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(id) }
            return id
        }
        val vert = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val frag = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vert)
        GLES20.glAttachShader(result, frag)
        GLES20.glLinkProgram(result)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, linked, 0)
        GLES20.glDeleteShader(vert)
        GLES20.glDeleteShader(frag)
        require(linked[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(result) }
        return result
    }
}
