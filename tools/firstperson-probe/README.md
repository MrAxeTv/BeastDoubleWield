# First-person arm GPU probe

This optional verification tool loads classes and assets from an installed **Minecraft Java 26.3** client. It runs an isolated, hidden OpenGL context. It does not launch Minecraft or a server, install packs, or change enabled resource packs.

Run from this project with a JDK capable of loading that client:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-firstperson-arm.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/firstperson-probe/Verify-FirstPerson.ps1 -Width 1920 -Height 1080
powershell -NoProfile -ExecutionPolicy Bypass -File tools/firstperson-probe/Verify-FirstPerson.ps1 -Width 3840 -Height 2160
```

`-MinecraftRoot` and `-PackRoot` can select an isolated installation or extracted pack. Missing matching GLFW artifacts are downloaded from Maven Central into `target/firstperson-probe/dependencies`; no client libraries are changed. Other dependencies come from the installed client metadata. Output images and the success receipt are written under `target/firstperson-probe/<width>x<height>`. A failed run removes its previous success receipt before testing. The receipt records every normalized pack source hash; when the example ZIP exists, it also verifies every ZIP entry against those sources and records the ZIP SHA-256.

The probe checks:

- Minecraft's native `ClientItem.CODEC` accepts the complete item definition; context routing hides the carrier outside first person and preserves ordinary heads.
- Eleven shader variants compile through the installed shaderc and SPIRV-Cross libraries and link on the local GPU, including the 26.3 OIT phases and GLINT.
- Actual `SkullModel` vertices use the native item-display, JSON transformation and held-item swing helpers. The expected texture coordinates come independently from the native `PlayerModel` arm and sleeve cubes.
- Forty rendered cases cover wide/slim skins, both physical hands, five swing positions, native Steve/Alex textures, and a synthetic UV texture with transparent sleeve pixels.
- Ordinary heads, an unmarked stretched head, complete wide/slim players, and marked geometry under orthographic projection remain pixel-identical to the vanilla shader. A foreground depth mask occludes the arm.
- Arm comparisons reject any interior mismatch. Small differences at reference texture/geometry boundaries are counted separately as raster/interpolation rounding, with a strict total difference limit.

This is a native mesh and GPU fixture, **not a live client pack-load or gameplay test**. It uses a fixed FOV, controlled lighting and one local GPU. OIT variants are compiled and linked; the fixture does not reproduce the full world OIT composition pass. It does not establish compatibility with other Minecraft versions, graphics mods, shader packs or BeastRPGInventory's separate 26.2 pack.

The checked 26.3 ZIP and final 1080p/4K results are recorded in [verification-26.3.json](verification-26.3.json). Each resolution passed 198 checks and 40 arm scenarios with no interior pixel mismatches. The remaining differences were adjacent to texture/geometry boundaries: 8 pixels total at 1080p and 45 at 4K (at most 2 and 5 per case). The normalized source and ZIP hashes bind this receipt to those specific assets. Performance was not benchmarked.
