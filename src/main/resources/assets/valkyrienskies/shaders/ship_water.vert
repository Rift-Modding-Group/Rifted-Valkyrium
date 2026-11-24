#version 120
uniform vec4 firstShipData[4];
varying vec2 atlasUv;
varying vec2 lightUv;
varying vec4 vertexColor;
varying vec3 eyePosition;
varying vec3 firstShipPosition;
void main() {
    gl_Position = ftransform();
    atlasUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lightUv = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    vertexColor = gl_Color;
    eyePosition = (gl_ModelViewMatrix * gl_Vertex).xyz;
    vec4 eye = vec4(eyePosition, 1.0);
    firstShipPosition = vec3(dot(firstShipData[0], eye), dot(firstShipData[1], eye), dot(firstShipData[2], eye));
}
