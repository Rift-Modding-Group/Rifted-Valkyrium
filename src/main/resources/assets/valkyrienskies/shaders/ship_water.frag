#version 120
uniform sampler2D atlas;
uniform sampler2D lightmap;
uniform sampler2D volumes;
uniform vec2 volumeTextureSize;
uniform int shipCount;
uniform vec4 firstShipData[4];
uniform vec4 waterSprites[3];
uniform int fogMode;
uniform bool fogEnabled;
uniform float alphaThreshold;
varying vec2 atlasUv;
varying vec2 lightUv;
varying vec4 vertexColor;
varying vec3 eyePosition;
varying vec3 firstShipPosition;
vec4 dataAt(float index) {
    vec2 pixel = vec2(mod(index, volumeTextureSize.x), floor(index / volumeTextureSize.x));
    return texture2D(volumes, (pixel + 0.5) / volumeTextureSize);
}
bool masked(vec3 local, vec4 size) {
    if (any(lessThan(local, vec3(0.0))) || any(greaterThanEqual(local, size.xyz))) return false;
    float index = local.x + size.x * (local.y + size.y * local.z);
    vec4 cells = dataAt(size.w + floor(index / 4.0));
    float channel = mod(index, 4.0);
    float dry = cells.r;
    if (channel == 1.0) dry = cells.g;
    if (channel == 2.0) dry = cells.b;
    if (channel == 3.0) dry = cells.a;
    return dry > 0.5;
}
void main() {
    bool water = false;
    for (int sprite = 0; sprite < 3; sprite++) {
        vec4 bounds = waterSprites[sprite];
        if (atlasUv.x > bounds.x && atlasUv.y > bounds.y && atlasUv.x < bounds.z && atlasUv.y < bounds.w) water = true;
    }
    if (water) {
        for (int ship = 0; ship < shipCount; ship++) {
            vec3 position;
            vec4 size;
            if (ship == 0) {
                position = firstShipPosition;
                size = firstShipData[3];
            }
            else {
                float base = float(ship * 4);
                vec4 eye = vec4(eyePosition, 1.0);
                position = vec3(dot(dataAt(base), eye), dot(dataAt(base + 1.0), eye), dot(dataAt(base + 2.0), eye));
                size = dataAt(base + 3.0);
            }
            if (any(lessThan(position, vec3(-0.001))) || any(greaterThan(position, size.xyz + vec3(0.001)))) continue;
            vec3 local = floor(position);
            if (masked(local, size)) discard;
            vec3 fraction = position - local;
            if (all(greaterThanEqual(fraction, vec3(0.001))) && all(lessThanEqual(fraction, vec3(0.999)))) continue;
            // Include touching voxels within one millimetre of a face to absorb terrain transform rounding.
            vec3 minimum = max(floor(position - vec3(0.001)), vec3(0.0));
            vec3 maximum = min(floor(position + vec3(0.001)), size.xyz - vec3(1.0));
            if (any(greaterThan(minimum, maximum))) continue;
            for (float z = minimum.z; z <= maximum.z; z += 1.0) {
                for (float y = minimum.y; y <= maximum.y; y += 1.0) {
                    for (float x = minimum.x; x <= maximum.x; x += 1.0) {
                        vec3 neighbor = vec3(x, y, z);
                        if (any(notEqual(neighbor, local)) && masked(neighbor, size)) discard;
                    }
                }
            }
        }
    }
    vec4 color = texture2D(atlas, atlasUv) * texture2D(lightmap, lightUv) * vertexColor;
    if (color.a <= alphaThreshold) discard;
    if (fogEnabled) {
        float distance = length(eyePosition);
        float fog = clamp((gl_Fog.end - distance) * gl_Fog.scale, 0.0, 1.0);
        if (fogMode == 2048) fog = exp(-gl_Fog.density * distance);
        if (fogMode == 2049) fog = exp(-pow(gl_Fog.density * distance, 2.0));
        color.rgb = mix(gl_Fog.color.rgb, color.rgb, clamp(fog, 0.0, 1.0));
    }
    gl_FragColor = color;
}
