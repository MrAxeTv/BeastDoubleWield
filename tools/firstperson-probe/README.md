# First-person arm GPU probe

This optional verification tool loads classes and assets from an installed **Minecraft Java 26.3** client. It runs an isolated, hidden OpenGL context. It does not launch Minecraft or a server, install packs, or change enabled resource packs.

Run from this project with a JDK capable of loading that client:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-firstperson-arm.ps1 -SkipClientExport
powershell -NoProfile -ExecutionPolicy Bypass -File tools/firstperson-probe/Verify-FirstPerson.ps1 -Width 1920 -Height 1080
powershell -NoProfile -ExecutionPolicy Bypass -File tools/firstperson-probe/Verify-FirstPerson.ps1 -Width 3840 -Height 2160
```

The build command above uses `-SkipClientExport` to keep verification isolated. Without that switch, the builder copies only the arm ZIP to `C:/Users/krist/AppData/Roaming/.minecraft/resourcepacks` and verifies its SHA-256; `-ClientResourcePackDirectory <directory>` selects another destination. Unchanged ZIP bytes preserve the existing build and destination timestamps. Copying does not enable the pack or reload client resources; both remain manual.

`-MinecraftRoot` and `-PackRoot` can select an isolated installation or extracted pack. Missing matching GLFW artifacts are downloaded from Maven Central into `target/firstperson-probe/dependencies`; no client libraries are changed. Other dependencies come from the installed client metadata. Output images and the success receipt are written under `target/firstperson-probe/<width>x<height>`. A failed run removes its previous success receipt before testing. The receipt records every normalized pack source hash; when the example ZIP exists, it also verifies every ZIP entry against those sources and records the ZIP SHA-256.

The probe checks:

- Minecraft's native `ClientItem.CODEC` accepts the complete item definition; context routing selects the arm in first person, the shield silhouette in GUI and nothing in other contexts, while preserving ordinary heads.
- Native atlas and cuboid parsers accept the GUI icon. The actual `FaceBakery` geometry, UVs and GUI transformation are rendered through the native item shader with native flat-item lighting. The output exactly matches the installed client's 16-by-16 shield-slot sprite at GUI scales 1, 2, 3 and 4, including orientation, dimensions and colors. No texture PNG is copied into the pack. Inventory and HUD share this GUI selection; this fixture does not create a live inventory screen.
- Eleven shader variants compile through the installed shaderc and SPIRV-Cross libraries and link on the local GPU, including the 26.3 OIT phases and GLINT.
- Actual `SkullModel` vertices use the native item-display, JSON transformation and held-item swing helpers. The expected texture coordinates come independently from the native `PlayerModel` arm and sleeve cubes.
- Forty rendered cases cover wide/slim skins, both physical hands, five swing positions, native Steve/Alex textures, and a synthetic UV texture with transparent sleeve pixels.
- Eight additional resting cases compare the carrier against Minecraft's actual `renderPlayerArm` pose and native `PlayerModel` arm/sleeve geometry, including the `AvatarRenderer` resting roll. The reference does not read the item JSON transforms. It applies only the documented private size signature, centered on the native arm, so the comparison verifies orientation and position without pretending the marked dimensions equal an unscaled vanilla arm. `*-native-rest-vanilla.png` also shows the completely unscaled native arm.
- Ordinary heads, an unmarked stretched head, complete wide/slim players, and marked geometry under orthographic projection remain pixel-identical to the vanilla shader. A foreground depth mask occludes the arm.
- Arm comparisons reject any interior mismatch. Small differences at reference texture/geometry boundaries are counted separately as raster/interpolation rounding, with a strict total difference limit.

This is a native mesh and GPU fixture, **not a live client pack-load or gameplay test**. It uses a fixed FOV, controlled lighting and one local GPU. OIT variants are compiled and linked; the fixture does not reproduce the full world OIT composition pass. It does not establish compatibility with other Minecraft versions, graphics mods, shader packs or BeastRPGInventory's separate 26.2 pack. The model correction matches the native resting pose; animated carriers still use Minecraft's held-item swing, which differs from its empty-arm swing.

The current 26.3 ZIP and final 1080p/4K results are recorded in [verification-26.3.json](verification-26.3.json). Each resolution passed 241 checks, with 40 swing/UV scenarios and 8 independent native resting scenarios, all with zero interior pixel mismatches. The GUI icon exactly matches the native sprite at all four tested GUI scales. Swing/UV boundary differences total 13 pixels at 1080p and 40 at 4K (at most 2 and 6 per case). Native resting comparisons have 7 and 33 boundary differences (at most 3 and 13 per case). The normalized source and ZIP hashes bind this receipt to those specific assets. Performance was not benchmarked. The [previous resting-pose receipt](verification-26.3-rest-pose.json) preserves the earlier 222-check pack without the GUI icon. The [original receipt](verification-26.3-original.json) preserves the earlier UV-only evidence; it did not verify native resting alignment.

As a regression control, the strengthened probe was also run against the original ZIP. It rejected the first native resting case at 1080p: 79,458 differing pixels, including 75,910 interior pixels. This confirms the new independent check detects the original upright pose defect.

The item transformations can be derived again without launching OpenGL:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/firstperson-probe/Verify-FirstPerson.ps1 -DeriveRestTransforms
```

This writes `target/firstperson-probe/rest-transforms.json` for review. It captures the native empty-arm pose, includes the baked arm's resting roll, and solves each special-item rotation/translation relative to the native held-item and display transforms. The current item definition keeps the original exact scale values and shader bytes. Derivation does not edit sources or remove an existing verification receipt.
