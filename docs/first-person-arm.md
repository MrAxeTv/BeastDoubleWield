# First-person arm development record

This feature belongs to `1.1-SNAPSHOT` on `feature/first-person-arm`. The public `1.0` release and update feed remain unchanged. The older [verification record](verification.md) and export receipt describe that release; this document records the new optional arm work.

## Setup and behavior

Use **Paper 26.3 and a native Java 26.3 client**, with the [matching arm pack](../examples/firstperson-arm/README.md) manually enabled. Edit these keys in their existing `config.yml` sections, then run `/bdw reload`:

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

An attack material whitelist must include `AIR`. Mining separately requires `mine.allow-empty-hand` and compatible mining rules. Existing permissions, personal/global switches, world rules, Survival/Adventure restrictions, inventory pause and spare-storage requirement still apply. The new setting defaults to `false`.

An otherwise empty offhand receives a privately marked FEATHER carrying the player's existing complete skin profile and the `minecraft:player_head` item model. CustomModelData `35022` selects classic arms; `35023` selects slim arms. Public Paper PROFILE component access preserves the profile on the feather after metadata edits. This avoids the placement and helmet-equipping behavior of a real head. Production code uses public APIs, with reflective discovery isolating newer APIs from the Java 8/legacy core path.

The shared pack reshapes native profile-head geometry and samples the bound skin's arm and sleeve regions. It does not need a generated skin PNG or a separate pack per player. The plugin does not fetch profiles while reconciling inventory. A missing complete skin retains the ordinary input-placeholder fallback and is retried every 100 ticks. Skin changes appear when the carrier is recreated, such as after toggling or reconnecting.

While the offhand arm is present, the main hand stays empty and Minecraft can render its ordinary arm. Real offhand items take priority. The arm counts as AIR for attack/mining filters and custom events; native actions temporarily receive actual AIR. Restoration preserves real items produced by other listeners. Cleanup relies on the private typed ownership tag, never just the item's name or model number.

The pack renders the carrier only in first-person hand contexts. Third-person player geometry and swinging use Minecraft's ordinary animation. Holding an invisible item can still change the native resting arm pose; this is not a promise of an identical empty-hand posture in every camera mode.

## Artifacts and server checks

The final development JAR is `target/BeastDoubleWield-1.1-SNAPSHOT.jar` (136,869 bytes), SHA-256:

```text
338c71d1e0a6927ab7857929c629848a6c1328e3f2f6b1c867e6fa37f8adfee5
```

`mvn package` passed **152 tests**, with no failures, errors or skipped tests. All 47 packaged classes retain Java 8 bytecode (major 52). The supplied bStats source remains unchanged and the service ID remains `35021`. All seven normal JAR export destinations match the build hash; see [the export receipt](first-person-arm-exports.json). Export locations do not establish server compatibility. Builds did not start/reload servers or install/enable a client pack.

A separate isolated loopback fixture ran the exact JAR on **Paper 26.3-188-main@06b855f**, API `26.3.build.188-stable`, Java `26.0.1`, and public BeastLib `1.7.0`. Its [native API probe](../tools/arm-runtime-probe/README.md) passed **18 of 18 checks**:

- Classic/slim selection, retained profile, native item-model selection, ownership and serialization.
- Non-placeable/non-equippable FEATHER carrier, empty main hand and priority for a real offhand item.
- Actual AIR during the native hand transaction, restoration and preservation of a real replacement item.
- Native damage events, equal damage to an ordinary empty fist under identical fixture conditions, cancellation and cleanup. The cancellation case requires a fresh target, exactly one new native AIR-hand damage event, exactly one cancelled event observed at MONITOR, and unchanged target health.

The fixture used a synthetic native player with a complete test profile property, not a connected network client. Both damage paths produced `0.20800018310546875` in its identical cooldown state; this is an equality check, not a claim about fully charged fist damage. The fixture shut down cleanly. Local evidence is in `target/arm-runtime-26.3/final-native.log` and `final-result.json`; downloaded server binaries and worlds are excluded from Git.

## Shader verification

The reproducible native shader/model probe lives in [tools/firstperson-probe](../tools/firstperson-probe). It uses the installed official 26.3 client assets and native rendering classes in an isolated GPU fixture. Its receipt distinguishes this from live gameplay. The standalone [pack builder](../tools/build-firstperson-arm.ps1) normalizes text and ZIP metadata and writes checksums alongside the ZIP; unchanged source produces identical bytes and preserves the existing output timestamp.

On an NVIDIA GeForce RTX 5080, the final pack passed **198 checks at 1920 x 1080 and another 198 at 3840 x 2160**, including 40 rendered arm scenarios at each resolution. Eleven shader variants compiled through the installed native shaderc/SPIRV-Cross pipeline and linked on the GPU, including OIT and GLINT. Native `ClientItem.CODEC` accepted the item definitions.

Wide/slim arms, both physical hands, five swing positions, native Steve/Alex textures and a synthetic transparent-sleeve texture were compared against independent native player-model UVs. Both resolutions had **zero interior pixel mismatches**. Boundary raster/interpolation differences totaled 8 pixels at 1080p (at most 2 per case) and 45 at 4K (at most 5 per case). Ordinary heads, an unmarked stretched head, wide/slim player geometry and an orthographic negative control remained pixel-identical to vanilla; foreground depth occlusion passed. The precision fix uses nonperspective classification interpolants and inclusive UV-edge handling.

The final ZIP is `target/firstperson-arm/BeastDoubleWield-firstperson-arm-26.3.zip`, 57,933 bytes, with SHA-256:

```text
aad341eaee2215902f7a92593f49927627f7fae1c8e6de382b66565c2c7de24f
```

Its SHA-1 is `62827b025a2406730ccd73cab915bc315a9486b4`. Each GPU run verified ZIP entries against the normalized source hashes. The [durable GPU receipt](../tools/firstperson-probe/verification-26.3.json) records both runs; local full receipts and images are under `target/firstperson-probe/1920x1080` and `3840x2160`. This fixture uses fixed FOV and controlled lighting on one GPU; it compiles OIT variants but does not reproduce the complete world OIT composition pass or measure whole-pack/game performance.

## Compatibility limits and live acceptance

The new renderer is deliberately limited to the verified **26.3** API/client combination. It does not extend the shader pack to 1.9 or imply that every server version is supported. Unsupported servers retain the existing input-placeholder behavior where that feature is available. Known local Geyser/Floodgate clients are excluded. With local ViaVersion 5, the plugin requires a successful protocol query matching native 26.3 (`777`); unknown or other protocols fall back. Proxy-only translation cannot always be detected, so server owners must restrict the feature to matching clients.

The pack replaces entity core shaders and the player-head item definition. It **cannot be stacked with the current BeastRPGInventory 26.2 pack**. That pack needs a separately verified 26.3 port and an explicit merge. Other shader replacements and rendering mods need their own checks. BeastRPGInventory sources and the user's installed packs were not changed.

Without the matching pack, the carrier can appear as a player head. The plugin does not automatically offer, install or enable this experimental pack. Shader/model and server fixtures do not establish live client pack loading, packet delivery, or a complete multiplayer visual result. Before enabling it for players, verify both dominant hands, wide/slim skins, sleeves, FOV, air/target swings, real-item changes, inventory cleanup and first-/third-person/other-player views on the intended client and pack combination.
