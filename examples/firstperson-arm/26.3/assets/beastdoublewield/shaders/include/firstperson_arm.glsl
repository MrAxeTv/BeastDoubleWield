// Native profile textures are bound by minecraft:player_head. The item's two
// cuboids have private, rotation-invariant dimensions; no player skin is edited.
// Codes: 1/2 left wide/slim, 3/4 right wide/slim. Bit 3 selects the sleeve;
// bits 5 and above preserve the selected head face at shared UV boundaries.
// This pipeline retains native positions, clipping, depth and OIT phases.
vec2 bdwHeadFaceOrigin(int face) {
    return face < 2 ? vec2(8.0 + float(face) * 8.0, 0)
                    : vec2(float(face - 2) * 8.0, 8.0);
}

int bdwArmMode(vec2 uv, vec3 position) {
    vec2 dx = dFdx(uv * 64.0), dy = dFdy(uv * 64.0);
    vec3 px = dFdx(position), py = dFdy(position);
    float det = dx.x * dy.y - dx.y * dy.x;
    if (bdwArmPerspective == 0 || any(notEqual(textureSize(Sampler0, 0), ivec2(64)))
            || abs(det) < 1e-12) return 0;
    vec2 pixel = uv * 64.0;
    vec2 edges = 8.0 * vec2(length((px * dy.y - py * dx.y) / det),
                            length((py * dx.x - px * dy.x) / det));
    // Inclusive UV bounds matter on native cuboid edges. At x=32 both head
    // and hat are candidates; their actual dimensions select the right one.
    for (int hat = 0; hat < 2; hat++) {
        for (int face = 0; face < 6; face++) {
            vec2 local = pixel - vec2(float(hat) * 32.0, 0) - bdwHeadFaceOrigin(face);
            if (any(lessThan(local, vec2(-0.00001))) || any(greaterThan(local, vec2(8.00001)))) continue;
            for (int mode = 1; mode <= 4; mode++) {
                float width = (mode == 2 || mode == 4) ? 0.1875 : 0.25;
                float marker = 1.0 + float(mode) / 64.0;
                for (int shell = 0; shell < 2; shell++) {
                    vec3 size = (vec3(width, 0.75, 0.25) + float(shell) / 32.0) * marker;
                    if (hat == 1) size *= 1.0625;
                    vec2 expected = face < 2 ? size.xz
                        : ((face == 2 || face == 4) ? size.zy : size.xy);
                    if (all(lessThan(abs(edges - expected), vec2(0.0015))))
                        return hat == 1 ? -1 : mode + shell * 8 + face * 32;
                }
            }
        }
    }
    return 0;
}

vec4 bdwArmSample(int code, vec2 uv) {
    int face = code / 32;
    int layer = code & 31;
    bool shell = (layer & 8) != 0;
    int mode = layer & 7;
    bool left = mode < 3;
    float width = (mode == 2 || mode == 4) ? 3.0 : 4.0;
    vec2 base = left ? (shell ? vec2(48, 48) : vec2(32, 48))
                     : (shell ? vec2(40, 32) : vec2(40, 16));
    vec2 pixel = uv * 64.0;
    vec2 origin, size, source;
    if (face == 0) { origin = vec2(4, 0); size = vec2(width, 4); source = vec2(8, 0); }
    else if (face == 1) { origin = vec2(4 + width, 0); size = vec2(width, 4); source = vec2(16, 0); }
    else if (face == 2) { origin = vec2(0, 4); size = vec2(4, 12); source = vec2(0, 8); }
    else if (face == 3) { origin = vec2(4, 4); size = vec2(width, 12); source = vec2(8, 8); }
    else if (face == 4) { origin = vec2(4 + width, 4); size = vec2(4, 12); source = vec2(16, 8); }
    else { origin = vec2(8 + width, 4); size = vec2(width, 12); source = vec2(24, 8); }
    vec2 texel = clamp(floor((pixel - source) * size / 8.0), vec2(0), size - vec2(1));
    return texelFetch(Sampler0, ivec2(base + origin + texel), 0);
}
