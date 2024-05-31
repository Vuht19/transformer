#version 300 es
// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// ES 3 vertex shader that applies the 4 * 4 transformation matrices
// uTransformationMatrix and the uTexTransformationMatrix.

glsl
#version 330

precision mediump float;

// Input attributes
in vec3 a_Position;
in vec2 a_TexCoord;

// Uniforms
uniform mat4 u_ModelMatrix;
uniform mat4 u_ViewMatrix;
uniform mat4 u_ProjectionMatrix;
uniform float u_FadeIn;

// Output variables
out vec2 v_TexCoord;
out float v_FadeIn;

void main() {
    // Pass through the texture coordinate
    v_TexCoord = a_TexCoord;

    // Pass through the fade-in value
    v_FadeIn = u_FadeIn;

    // Calculate the transformed position of the vertex
    gl_Position = u_ProjectionMatrix * u_ViewMatrix * u_ModelMatrix * vec4(a_Position, 1.0);
}