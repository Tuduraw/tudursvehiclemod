#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

// "Screen-door transparency" (detail: docs/IMPLEMENTATION_NOTES.md
// "半透明テクスチャの描画方式 - ディザリングによるcutout化"): approximates partial alpha
// via a per-pixel discard pattern (4x4 ordered/Bayer dithering) instead of true alpha
// blending. Every surviving fragment is either fully drawn or fully discarded - never
// partially blended - so this can use a normal, depth-WRITING pipeline (same as cutout)
// instead of a translucent one.
float tudursvehiclemod_ditherThreshold(vec2 fragCoord) {
    int x = int(mod(fragCoord.x, 4.0));
    int y = int(mod(fragCoord.y, 4.0));
    int index = y * 4 + x;
    float bayer[16] = float[16](
        0.0, 8.0, 2.0, 10.0,
        12.0, 4.0, 14.0, 6.0,
        3.0, 11.0, 1.0, 9.0,
        15.0, 7.0, 13.0, 5.0
    );
    // Offsetting by 0.5/16 centers each threshold within its own bucket, rather than
    // biasing every comparison towards one side of it.
    return (bayer[index] + 0.5) / 16.0;
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < tudursvehiclemod_ditherThreshold(gl_FragCoord.xy)) {
        discard;
    }
#ifdef PER_FACE_LIGHTING
    color *= (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack) * ColorModulator;
#else
    color *= vertexColor * ColorModulator;
#endif
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    color *= lightMapColor;
#endif
    // Per this shader's own doc: every surviving fragment renders fully opaque - the
    // dithering above is what conveys "how translucent" this looked, not blending.
    color.a = 1.0;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    fragColor.a = 1.0;
}
