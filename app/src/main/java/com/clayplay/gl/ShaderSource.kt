package com.clayplay.gl

/**
 * Shader sources.
 *
 * The clay look rests on three choices, each fighting one thing that makes CG
 * read as "plastic" instead of "clay":
 *
 *  1. Wrapped diffuse (`(dot+w)/(1+w)`) instead of Lambert. Real clay is
 *     translucent near the terminator, so it never goes fully dark on the light
 *     side. This is what removes the hard shadow line.
 *  2. Warm-tinted fill from the sky opposite the key light, saturated toward
 *     orange. Clay is a warm material; grey fill instantly reads as CG.
 *  3. Broad, very low specular plus a rim term. Matte surfaces still catch
 *     grazing light and show a soft sheen at silhouettes.
 */
object ShaderSource {

    val CLAY_VS = """
        #version 300 es
        precision highp float;

        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec3 aColor;

        uniform mat4 uMVP;
        uniform mat4 uModel;
        uniform mat3 uNormalMat;

        out vec3 vNormal;
        out vec3 vWorld;
        out vec3 vColor;

        void main() {
            vec4 world = uModel * vec4(aPos, 1.0);
            vWorld = world.xyz;
            vNormal = normalize(uNormalMat * aNormal);
            vColor = aColor;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    val CLAY_FS = """
        #version 300 es
        precision highp float;

        in vec3 vNormal;
        in vec3 vWorld;
        in vec3 vColor;

        uniform vec3 uLightDir;      // direction TOWARD the light, normalised
        uniform vec3 uLightColor;
        uniform vec3 uSkyColor;      // upper hemisphere ambient
        uniform vec3 uGroundColor;   // lower hemisphere ambient (bounce)
        uniform vec3 uCamPos;
        uniform vec3 uFogColor;
        uniform float uFogNear;
        uniform float uFogFar;
        uniform vec3 uTint;          // runtime tint multiplier
        uniform float uAlpha;
        uniform float uHighlight;    // 0..1 selection glow
        uniform float uWrap;         // diffuse wrap amount

        out vec4 fragColor;

        void main() {
            vec3 N = normalize(vNormal);
            vec3 V = normalize(uCamPos - vWorld);
            vec3 L = normalize(uLightDir);

            // --- wrapped diffuse: no hard terminator
            float ndl = dot(N, L);
            float wrap = uWrap;
            float diff = max(0.0, (ndl + wrap) / (1.0 + wrap));
            diff *= diff; // slightly faster falloff so the wrap does not flatten form

            // --- hemispheric ambient: sky above, warm bounce below
            float up = N.y * 0.5 + 0.5;
            vec3 ambient = mix(uGroundColor, uSkyColor, up);

            // --- back-fill: warm light bleeding around the silhouette away from key
            float back = max(0.0, -ndl) * 0.35;
            vec3 backFill = uGroundColor * back;

            // --- broad matte sheen
            vec3 H = normalize(L + V);
            float spec = pow(max(0.0, dot(N, H)), 18.0) * 0.055;

            // --- silhouette rim
            float rim = pow(1.0 - max(0.0, dot(N, V)), 3.0) * 0.16;

            vec3 base = vColor * uTint;
            vec3 lit = base * (ambient + uLightColor * diff + backFill)
                     + uLightColor * spec
                     + uSkyColor * rim;

            // selection glow: lift toward a warm highlight, keep it subtle
            lit += base * uHighlight * 0.55 + vec3(0.20, 0.15, 0.06) * uHighlight;

            // --- distance fog blends the room back wall into the background
            float dist = length(uCamPos - vWorld);
            float fog = clamp((dist - uFogNear) / max(0.001, uFogFar - uFogNear), 0.0, 1.0);
            fog = fog * fog;
            lit = mix(lit, uFogColor, fog);

            fragColor = vec4(lit, uAlpha);
        }
    """.trimIndent()

    val SHADOW_VS = """
        #version 300 es
        precision highp float;

        layout(location = 0) in vec3 aPos;

        uniform mat4 uMVP;

        out vec2 vUv;

        void main() {
            // quad spans [-1,1] in x/z, so remap straight to 0..1
            vUv = aPos.xz * 0.5 + 0.5;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    val SHADOW_FS = """
        #version 300 es
        precision highp float;

        in vec2 vUv;

        uniform float uStrength;
        uniform vec3 uShadowColor;

        out vec4 fragColor;

        void main() {
            vec2 d = vUv - 0.5;
            float r = length(d) * 2.0;
            // soft core with a wide, gentle falloff -> contact shadow, not a hard disc
            float a = smoothstep(1.0, 0.0, r);
            a = a * a * uStrength;
            if (a <= 0.002) discard;
            fragColor = vec4(uShadowColor, a);
        }
    """.trimIndent()

    /** Full-screen untextured clear, used for the room backdrop gradient. */
    val BACKDROP_VS = """
        #version 300 es
        precision highp float;
        layout(location = 0) in vec3 aPos;
        out vec2 vUv;
        void main() {
            vUv = aPos.xy * 0.5 + 0.5;
            gl_Position = vec4(aPos.xy, 0.0, 1.0);
        }
    """.trimIndent()

    val BACKDROP_FS = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform vec3 uTop;
        uniform vec3 uBottom;
        out vec4 fragColor;
        void main() {
            // vertical gradient with a slight vignette so the room feels lit from above
            vec3 c = mix(uBottom, uTop, pow(vUv.y, 0.85));
            vec2 d = vUv - 0.5;
            float vig = 1.0 - dot(d, d) * 0.35;
            fragColor = vec4(c * vig, 1.0);
        }
    """.trimIndent()
}
