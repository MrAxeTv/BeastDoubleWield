# Experimental first-person skin arm (Java 26.3)

This optional pack draws an otherwise empty offhand using the player's own skin. One shared pack covers classic/wide and slim skins, both physical hands and sleeves. It needs no generated skin PNG or separate pack for each player. Real offhand items retain their normal appearance and need no pack for ordinary gameplay.

| Required plugin | Server | Client / resource format | Status |
| --- | --- | --- | --- |
| BeastDoubleWield `1.1-SNAPSHOT` and BeastLib | Paper **26.3** | Native Java **26.3** / **97.1** | Experimental; isolated native/GPU checks, with live gameplay acceptance still outstanding |

## Install

1. Download the `1.1-SNAPSHOT` plugin from [preview release v1.1-preview.1](https://github.com/MrAxeTv/BeastDoubleWield/releases/tag/v1.1-preview.1) and its [BeastLib dependency](https://modrinth.com/plugin/beastlib). Put both JARs in your Paper 26.3 server's `plugins` folder. Restart the server when replacing the plugin JAR; `/bdw reload` only reloads settings.
2. Download [BeastDoubleWield-firstperson-arm-26.3.zip](https://github.com/MrAxeTv/BeastDoubleWield/releases/download/v1.1-preview.1/BeastDoubleWield-firstperson-arm-26.3.zip). Leave it zipped, put it in your native Java 26.3 client's `resourcepacks` folder, and enable it through Minecraft's resource-pack menu. The plugin does not automatically send or enable this pack.
3. Edit these keys in the server's existing `plugins/BeastDoubleWield/config.yml` sections, then run `/bdw reload`:

```yaml
OffhandArm:
  Enabled: true
Swing:
  Air: true
Attack:
  AllowEmptyHand: true
  CorrectToolOnly: false
```

Do not create duplicate YAML sections. `OffhandArm.Enabled` is the only arm switch. Older configurations migrate to enabled only if both `Placeholder.Enabled` and `Placeholder.FirstPersonArm` were true; missing old switches count as false, and an explicit new value wins.

Test with an empty offhand in Survival or Adventure and at least one free storage slot. Existing permissions, world rules and personal/global switches still apply. An attack material whitelist must allow `AIR`; empty-hand mining separately requires `Mine.AllowEmptyHand` and compatible mining rules. A real offhand item always takes priority. After replacing the ZIP, reload or reselect the pack manually in the client.

The current ZIP is 60,462 bytes, SHA-256 `889048677fc4774741d4b3c6dfef52c7c20fabf17a23a724dcae3af5e3ccc26a`. The [verification record](../../docs/first-person-arm.md) separates pack/GPU evidence from the exact plugin builds and native API probes.

## Build from source

The pack source is in [26.3](26.3/). From the repository root, use PowerShell without a local client export:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/build-firstperson-arm.ps1 -SkipClientExport
```

The tool writes `target/firstperson-arm/BeastDoubleWield-firstperson-arm-26.3.zip`, SHA-1/SHA-256 sidecars and `checksums.json`. ZIP order, timestamps and text encoding are deterministic; identical bytes preserve the existing output timestamp. Copy the completed ZIP to your client and enable it manually. Building the pack does not start/reload a server or change client settings.

### Owner-local export

Without `-SkipClientExport`, the builder uses the repository owner's configured path, `C:/Users/krist/AppData/Roaming/.minecraft/resourcepacks`, and verifies the copied ZIP's SHA-256. Other users should keep `-SkipClientExport` or explicitly choose their own `-ClientResourcePackDirectory <directory>`. An unchanged installed ZIP retains its timestamp. Local copying does not enable the pack or reload client resources.

## Rendering protocol

The plugin supplies a disposable feather with the player's existing complete profile, the `minecraft:player_head` item model, and CustomModelData `35022` (wide) or `35023` (slim). The native head renderer binds the full skin texture; the shader remaps it onto arm and sleeve cuboids. The plugin does not fetch a skin while reconciling inventory. Its owned carrier is treated as an empty fist by BeastDoubleWield and retains its private cleanup marker.

The pack draws the skin arm in `firstperson_lefthand` and `firstperson_righthand` contexts. In GUI contexts it draws the client's own empty-offhand shield silhouette, with no tooltip, so the occupied carrier slot resembles the vanilla empty slot. Minecraft uses the same GUI item context for inventory slots and the HUD; the silhouette therefore also appears in the offhand HUD box. Third-person, ground, fixed, head and other contexts select an empty model. Ordinary head items outside the reserved model-data interval retain the installed vanilla 26.3 head definition. The old invisible input feather selector (`35021`) remains for compatibility, but current development code no longer creates main-hand input feathers.

The GUI icon references `minecraft:gui/sprites/container/slot/shield` through one additional item-atlas sprite source. Its flat model retains the native 16-by-16 dimensions and colors without bundling any texture PNG. Atlas sources merge with the native atlas; this pack does not replace its regular item source list. Another pack can change the referenced native sprite. The arm shaders and first-person transforms are unchanged by this inventory icon.

Each first-person model contains two native profile-head draws. Their base cuboids become the arm (4×12×4 or 3×12×4 pixels) and the sleeve (an additional 0.25 pixels on each side). The original head hat meshes are discarded for these carriers. The model scales include a small private multiplier: `1 + mode/64`, where modes 1/2 are left wide/slim and 3/4 are right wide/slim. The same multiplier applies to both body and sleeve, changing dimensions by at most 6.25%.

The fragment shader recognizes the pair of physical face-edge lengths from derivatives of native vertex positions and UVs. It additionally requires perspective projection, a 64×64 texture and the exact head UV rectangles. It then samples the corresponding arm/sleeve region. Native positions, hand/swing transforms, clipping, depth and transparency passes remain in use. Normal player entities are not masked or replaced. Third-person arm motion remains Minecraft's ordinary animation; holding a decorative item may still affect the native resting hand pose and needs multiplayer visual acceptance.

The special-item rotations and translations align the resting carrier with Minecraft's native empty-arm position and angle, for both physical hands and classic/slim skins. The size signature above remains, so its dimensions are not exactly those of an unscaled vanilla arm. The native GPU probe now compares that placement against the actual empty-arm renderer and model independently of the item JSON. Animated carriers retain native held-item swings, which differ from empty-arm swings. Hotbar scrolling preserves the existing carrier; ordinary offhand tools still need no pack.

This dimension signature is a rendering protocol, not an ownership/security check. A custom pack that deliberately produces the same dimensions, UVs and texture type can collide; server cleanup relies on the private item tag instead. The test fixture must include ordinary heads and player geometry as negative controls.

## Version and pack compatibility

This source targets the installed vanilla **26.3 / resource format 97.1** pipeline. It is not a 1.9–26.3 universal shader pack. The native shader template changed from BeastRPGInventory's 26.2 implementation, including include syntax, varying locations and transparency phases.

The underlying `minecraft:player_head` special model and profile-texture loading arrived in [Java 1.21.6](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6). They are not new to 26.3. This arm also depends on version-specific entity shaders, item definitions and public Paper components, so that earlier model support does not establish compatibility with every 1.21.6+ client. Only the supplied [26.3 / format 97.1](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) port has the recorded BeastDoubleWield checks.

The plugin requires Paper 26.3's supported profile/model component API and a complete existing skin profile. The client must positively report Java 26.3 protocol `777` through local ViaVersion 5 or, when ViaVersion is absent, Paper's public API. Unknown/mismatched protocols, failed checks, disabled/incompatible local ViaVersion and known Bedrock clients receive no arm. A translating proxy can hide the original client protocol, so restrict the feature to matching clients.

The pack overrides `minecraft/shaders/core/entity.vsh`, `entity.fsh`, and the `minecraft:player_head` item definition. The current BeastRPGInventory 26.2 and BeastMounts/ObjCubed 26.3 packs also replace the entity shaders and require an explicit version-matched merge with new rendering checks; pack order alone cannot preserve both renderers. Other shader replacements and Iris/OptiFine combinations remain unverified. If the item model loads but another pack replaces these shaders, the arm can appear as a stretched head/hat. The builder updates only this arm ZIP; it does not merge or modify other packs or projects.

GPU fixture results and remaining visual checks are recorded in the plugin's verification documentation. Native shader compilation and isolated GPU output are not a live first-person gameplay test. Keep this feature optional until its appearance has been checked on the intended client, including both dominant hands, FOV, sleeves, swings, camera modes and other packs.

## Provenance

The profile-bound skin approach is based on the owner's BeastRPGInventory implementation. The focused arm UV/signature logic is original BeastDoubleWield code. The `entity.vsh`/`entity.fsh` templates and ordinary player-head fallback were taken from the installed official Mojang 26.3 client and retain its native pipeline behavior outside the marked carrier. The GUI icon references Mojang's installed shield-slot sprite without copying it into this pack. No third-party shader pack, downloaded skin or Minecraft texture artwork is bundled.
