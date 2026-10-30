# Experimental first-person skin arm (Java 26.3)

This optional pack draws the otherwise empty offhand as a skin-textured arm. The plugin supplies a disposable feather with the player's existing complete profile, the `minecraft:player_head` item model, and CustomModelData `35022` (wide) or `35023` (slim). The native head renderer binds the full skin texture; the shader remaps it onto arm and sleeve cuboids. No player-specific skin PNG or separate generated skin pack is needed.

The pack draws these reserved items only in `firstperson_lefthand` and `firstperson_righthand` contexts. GUI, third-person, ground, fixed, head and shelf contexts select an empty model. Ordinary head items outside the reserved model-data interval retain the installed vanilla 26.3 head definition. The existing invisible input feather (`35021`) is included too.

Build it from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/build-firstperson-arm.ps1
```

The tool writes `target/firstperson-arm/BeastDoubleWield-firstperson-arm-26.3.zip`, SHA-1/SHA-256 sidecars and `checksums.json`. ZIP order, timestamps and text encoding are deterministic; identical bytes preserve the existing file's modification time. Building does not install a pack, change client settings or start a server.

Install and enable the pack manually in a **26.3 Java client**, then set the existing configuration keys:

```yaml
placeholder:
  enabled: true
  first-person-arm: true
swing:
  air: true
attack:
  allow-empty-hand: true
  correct-tool-only: false
```

Do not create duplicate YAML sections. An attack material whitelist must allow `AIR`. The plugin requires its supported modern profile/model component API and a complete existing skin profile; it does not fetch a skin while reconciling inventory. Real offhand items keep their original appearance and gameplay. The optional carrier is treated as an empty fist by BeastDoubleWield and retains its private cleanup marker.

## Rendering protocol

Each first-person model contains two native profile-head draws. Their base cuboids become the arm (4×12×4 or 3×12×4 pixels) and the sleeve (an additional 0.25 pixels on each side). The original head hat meshes are discarded for these carriers. The model scales include a small private multiplier: `1 + mode/64`, where modes 1/2 are left wide/slim and 3/4 are right wide/slim. The same multiplier applies to both body and sleeve, changing dimensions by at most 6.25%.

The fragment shader recognizes the pair of physical face-edge lengths from derivatives of native vertex positions and UVs. It additionally requires perspective projection, a 64×64 texture and the exact head UV rectangles. It then samples the corresponding arm/sleeve region. Native positions, hand/swing transforms, clipping, depth and transparency passes remain in use. Normal player entities are not masked or replaced. Third-person arm motion remains Minecraft's ordinary animation; holding a decorative item may still affect the native resting hand pose and needs multiplayer visual acceptance.

This dimension signature is a rendering protocol, not an ownership/security check. A custom pack that deliberately produces the same dimensions, UVs and texture type can collide; server cleanup relies on the private item tag instead. The test fixture must include ordinary heads and player geometry as negative controls.

## Version and pack compatibility

This source targets the installed vanilla **26.3 / resource format 97.1** pipeline. It is not a 1.9–26.3 universal shader pack. The native shader template changed from BeastRPGInventory's 26.2 implementation, including include syntax, varying locations and transparency phases.

The pack overrides `minecraft/shaders/core/entity.vsh`, `entity.fsh`, and the `minecraft:player_head` item definition. It cannot be made compatible with BeastRPGInventory 26.2, Iris, OptiFine or another entity-shader replacement just by choosing a pack load order: those shader branches and model selectors require an explicit version-matched merge and new rendering checks. This pack does not modify BeastRPGInventory or a user's installed pack.

GPU fixture results and remaining visual checks are recorded in the plugin's verification documentation. Native shader compilation and isolated GPU output are not a live first-person gameplay test. Keep this feature optional until its appearance has been checked on the intended client, including both dominant hands, FOV, sleeves, swings, camera modes and other packs.

## Provenance

The profile-bound skin approach is based on the owner's BeastRPGInventory implementation. The focused arm UV/signature logic is original BeastDoubleWield code. The `entity.vsh`/`entity.fsh` templates and ordinary player-head fallback were taken from the installed official Mojang 26.3 client and retain its native pipeline behavior outside the marked carrier. No third-party shader pack, downloaded skin or Minecraft texture artwork is bundled.
