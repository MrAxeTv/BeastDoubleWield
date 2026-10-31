# BeastDoubleWield

BeastDoubleWield is a free plugin that adds right-click offhand attacks and progressive offhand mining to a Bukkit-family Minecraft server. The plugin uses BeastLib for configuration files, messages, and task scheduling. It is an original implementation; no AvarionMC/Ranull plugin code or assets are bundled.

## Downloads

- [Stable 1.0](https://github.com/MrAxeTv/BeastDoubleWield/releases/tag/v1.0): the existing stable release.
- [1.1 preview 1](https://github.com/MrAxeTv/BeastDoubleWield/releases/tag/v1.1-preview.1): the current `1.1-SNAPSHOT` JAR, optional Java 26.3 skin-arm pack and checksums. This is an experimental prerelease; stable update notifications continue to point to 1.0.
- [Resource-pack examples and supported versions](examples/README.md): choose the skin-arm pack or read the historical input-only examples. One 26.3 arm pack covers classic/slim skins, sleeves and both dominant hands.

The plugin is free to download and run on your servers. Source is published for inspection; the original code retains the terms in [LICENSE](LICENSE). Third-party components retain their own licenses and notices.

## Installation

1. Install [BeastLib](https://modrinth.com/plugin/beastlib). Public runtime version **1.7.0** passed the release's 1.9.4 core and 1.20.2 placeholder gameplay fixtures; the earlier integration runs used 1.7.3. See the exact tested builds in the verification record below.
2. Download `BeastDoubleWield-1.0.jar` from [GitHub Releases](https://github.com/MrAxeTv/BeastDoubleWield/releases/tag/v1.0) and place it in the server's `plugins` directory.
3. Start the server yourself and configure `plugins/BeastDoubleWield/config.yml` and `Lang/English.yml`.

These steps describe stable 1.0. The language selector below belongs to current 1.1 development; stable 1.0 continues to use `Lang/English.yml`.

The compilation baseline is Spigot 1.20.2 with Java 8 bytecode. The legacy bridge invokes **NMS through reflection** for native attack, mining, tool, animation and targeting operations where newer Bukkit methods are missing. It resolves specific signatures and version-dependent alternatives; reflection does not make every release interchangeable. Startup logs report the selected backend. Consult [the verification record](docs/verification.md) and [legacy compatibility notes](docs/legacy-compatibility.md) for measured support and remaining gaps. Use the Java version required by your server. Folia is currently rejected during startup. Core gameplay needs no client mod or resource pack; the optional skin arm below requires a pack.

Current 1.1 development examples use PascalCase settings and message keys, with a capital first letter in every segment, such as `Attack.AllowEmptyHand` and `NoPermission`. Startup and `/bdw reload` migrate older hyphenated and lower camelCase names while preserving saved values, custom messages and unknown keys; PascalCase sections and keys take priority when multiple forms exist. Existing files receive a `.pre-PascalCase.bak` backup before a rewrite, with numbered backups when needed. An unchanged reload leaves file bytes and timestamps intact. Historical release and development records retain their original key names.

## Languages (1.1 development)

Set the existing top-level `Language` option in `config.yml`, then run `/bdw reload`:

```yaml
Language: 'en'
```

The selection applies to everyone on the server. Startup and reload create all 50 language files under `plugins/BeastDoubleWield/Lang/<code>.yml`, each with 20 message keys. Edit those files to customize messages. Missing keys are filled from that locale's own bundled translation. These are the supported codes, including regional variants and LOLCAT (`lol-US`):

```text
ar     bg     bs     ca     cs     da     de     el     en     en-GB
en-US  es     et     eu     fi     fil-PH fr     ga-IE  gv-IM  he
hi-IN  hr     hu     is-IS  it     ja     ko     lol-US lt     lv-LV
mn-MN  nl     no     pl     pt     pt-BR  ro     ru     si-LK  sk
sr     sr-CS  sv     th     tr     uk     vi     zh     zh-HK  zh-TW
```

Selector values accept case differences and underscores, so `PT_br` selects `pt-BR`; `English` is an alias for `en`. Other codes are rejected. All locale files are validated on every reload, including unselected ones. Invalid YAML or a non-string message in any file rejects the reload and keeps the previous active settings and files. File updates use backups and rollback if a write or service activation fails.

An existing `Lang/English.yml` is left untouched. Its custom messages fill missing entries in `Lang/en.yml`, while entries already saved in `en.yml` win. Other locales use their own translations. Continue editing `en.yml` after migration.

Messages, help text and the on/off status labels are translated. Commands such as `/bdw toggle [on|off]` stay unchanged. Technical service/integration status values and server logs remain in English. Technical validation of translations does not constitute native-speaker proofreading.

## Using the offhand

Put a tool or weapon in the offhand and right-click the target. Hold right-click while looking at a block to continue mining. Changing held equipment, looking away, moving out of range, teleporting, dying, or opening an inventory stops the mining session. The server supplies the mining speed, drops, tool wear, enchantment behavior, and final break through native server operations.

Ordinary right-click mining has no exact button-release notification. By default `Mine.InputProgressTicks: 4` permits progress for four ticks after the latest input, then freezes progress, swings and hit sounds. `Mine.InputTimeoutTicks: 8` retains that partial progress briefly for delayed input before clearing it. Late input resumes without catching up missed mining ticks. A fast block can still finish within the four-tick window after release. Progressive breaks wait at least five ticks before accepting another mining session; true instant Survival breaks omit that pause. The next start also depends on incoming input. This reproduces the held-left-click pause but cannot distinguish separate taps from held right-click repeats.

On modern APIs the mining calculation scopes the held items' mining-speed modifiers and vanilla Efficiency bonus to the offhand tool, then restores the original attributes. This prevents an enchanted main-hand tool from lending its cached Efficiency bonus to a plain offhand tool, or an enchanted offhand tool from losing its bonus. The namespaced Efficiency correction targets vanilla rules on the verified 26.3 API; custom enchantment/data-pack effects are not automatically reproduced.

Offhand attacks likewise use the server's native attack operation so combat mechanics remain under server control. The configured offhand cooldown is an additional input limit; turning it off does not remove the server's native attack cooldown. Normal protection plugins can cancel the native damage or block-break events.

Successful offhand breaks play the original block's break sound to the mining player. Native Minecraft already sends the break effect to nearby players; the plugin supplies the actor's missing local feedback without broadcasting a duplicate. Cancelled or failed breaks do not receive a completion sound. `Mine.Sounds` controls the repeated mining hit sounds only.

The default interaction rules preserve main-hand item use and ordinary interaction with blocks or entities. Sneaking permits the configured offhand action on interactive targets. Material filters, permitted game modes, standing/sneaking rules, reach, empty-hand actions, correct-tool checks, and interaction cancellation can be configured separately for attacking and mining. See the comments in `config.yml` for the precise switches.

With `Interaction.PreserveMainHandUse: true`, an axe in the main hand takes priority when the clicked block can be stripped, scraped or unwaxed. The plugin stops ongoing offhand mining and leaves that native action unchanged. Holding the axe over air, stone, already-stripped wood or another target with no axe action still allows normal offhand input. Sneaking does not disable this axe-use priority. Detection covers vanilla transformations available in the server's material set; custom or removed 26.3 block-transformer rules cannot be inspected through the current public Paper API.

`Attack.AllowEmptyHand` permits an empty **offhand** to attack on right-click; ordinary main-hand punches still use left-click. `Swing.Air` requests an offhand swing when right-clicking without a target. With a weapon/tool in the offhand and an empty main hand, the plugin accepts the offhand air-use callback. When **both hands are empty**, vanilla Minecraft sends no right-click-air input, so a server plugin cannot detect that click. Right-clicking an entity still sends an interaction; holding a non-use item such as a stick in the main hand also permits an empty offhand swing request when both settings are enabled. Vanilla 26.3 does not render an empty offhand arm in first person, even when a swing is requested; empty-offhand combat can therefore work without a visible first-person fist animation. Mining has its own `Mine.AllowEmptyHand` switch.

## Empty main hand (1.1 development)

Version 1.1 no longer inserts an invisible input feather into the main hand. An offhand weapon or tool already supplies right-click input with an empty main hand, so ordinary offhand gameplay needs no placeholder or texture pack. The empty main hand remains available for Minecraft's normal arm rendering; the appearance of the held tool remains native client behavior.

The old main-hand carrier is still recognized by its private tag and cleaned up after an upgrade. Genuine feathers and other player items are preserved. Version 1.0's input-only pack and verification records remain available as historical release artifacts.

When both hands are truly empty, right-clicking air still produces no item-use input. The optional offhand arm below supplies that input through its own carrier. If that feature is disabled or unavailable, the plugin leaves both hands empty.

## Experimental first-person skin arm (1.1 development)

The optional `OffhandArm.Enabled: true` setting adds a skin-textured fist when the offhand is empty. It also requires `Swing.Air` and `Attack.AllowEmptyHand`, with `Attack.CorrectToolOnly: false`. This feature requires Paper 26.3's public component APIs, a native 26.3 Java client and the separate [first-person arm pack](examples/firstperson-arm/README.md). It is disabled by default. The published 1.0 release does not contain it.

`OffhandArm.Enabled` replaces the old `Placeholder.Enabled` and `Placeholder.FirstPersonArm` pair. Migration enables the new switch only when both old switches were `true`; a missing old switch counts as `false`. An explicitly saved `OffhandArm.Enabled` value takes priority.

The protected carrier uses the player's already available complete skin profile, with separate classic/slim models. A shared shader pack samples that profile's native skin texture; the plugin does not download or bake a skin PNG for each player. The skin arm renders in first person, while the normal player entity and its swing remain in use in third person. In inventory GUI contexts the carrier shows Minecraft's empty shield-slot silhouette without a tooltip. Minecraft shares that context with the offhand HUD, so the silhouette appears there too. An empty main hand stays empty so its ordinary arm can render normally; genuine main-hand items are preserved. Combat and filtering treat the owned carrier as AIR, and native actions receive an actual empty acting hand.

Build the matching pack, then enable or reload it manually before enabling this setting. The [arm pack builder](examples/firstperson-arm/README.md) copies its ZIP to the owner's Minecraft resourcepacks folder by default; it does not change the enabled-pack list. Missing profiles and unsupported versions leave the offhand empty; there is no main-hand input fallback. The client protocol must positively match Java 26.3 (`777`), using local ViaVersion 5 when present or Paper's public player protocol API otherwise. Known Bedrock clients, unavailable protocol checks and disabled/incompatible local ViaVersion do not receive the arm. A translating proxy can hide the original client protocol, so server owners must still restrict this feature to matching clients. Without the pack's shaders, the carrier can appear as a stretched head. A real offhand item always takes priority. Skin changes refresh when the carrier is recreated, such as after toggling or reconnecting. The normal third-person swing stays in use, though holding an invisible item can still affect the native resting arm pose.

The arm respects attack permissions, personal/global switches, world and attack rules, and only runs in Survival/Adventure. It requires at least one free storage slot, including the selected hotbar slot. Scrolling the hotbar, ordinary drops/pickups, and unrelated inventory clicks/drags preserve an eligible offhand arm without recreating it. Empty clicks on the carrier and empty-source inventory swaps are cancelled without removing it. Genuine cursor placement, number-key/F swaps and personal-inventory equipment shift-clicks can replace the carrier through native inventory handling. A denied or ineffective replacement restores the existing carrier only if its slot remains empty and eligible. Opening a container or clicking/dragging in an inventory pauses creation of new arms until close; the cursor must also be empty. Startup/reload clears stale interaction tracking and rechecks online players, while actual container views still pause insertion. Lifecycle cleanup and guards against transfer to vanilla item frames, armor stands and allays remain active. `/bdw status` reports the feature state.

The standalone pack overrides Minecraft's entity shaders. It cannot be stacked with the current BeastRPGInventory 26.2 shader pack; that pack needs a separately verified 26.3 port and merge. Other shader packs and client rendering mods also require compatibility checks. Native shader/model fixtures and an isolated server API probe are described in [the development verification record](docs/first-person-arm.md); those checks do not establish a full live multiplayer visual result.

## Commands and permissions

The primary command is `/beastdoublewield`; `/bdw` and `/doublewield` are aliases.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/bdw help` | `beastdoublewield.use` | Show help |
| `/bdw status` | `beastdoublewield.use` | Show version and global/personal switches |
| `/bdw integrations` | `beastdoublewield.use` | Show installed integration and client-detection capabilities |
| `/bdw toggle [on\|off]` | `beastdoublewield.toggle` | Persist your personal preference |
| `/bdw reload` | `beastdoublewield.reload` | Validate and reload configuration and messages |
| `/bdw update` | `beastdoublewield.update` | Request an asynchronous check of the configured release feed |

`beastdoublewield.attack` and `beastdoublewield.mine` control the corresponding actions. Use, toggle, attack, and mine default to everyone; reload and update default to operators. The update permission also grants automatic update notifications. `beastdoublewield.admin` grants the listed permissions and defaults to operators.

Personal preferences are stored atomically in `players.yml` and survive reconnects and server restarts, including on servers without the Persistent Data Container API. Preferences from the initial PDC-based build migrate on first use. The global `Enabled` switch overrides every player's preference. A malformed or invalid configuration reload retains the previously active settings. A successful reload stops current mining and clears combat input state before new actions use the new settings.

## Integration

Other plugins can cancel `me.mraxetv.beastdoublewield.api.OffhandAttackEvent` or `OffhandBlockBreakEvent` before the corresponding native operation. Native `EntityDamageByEntityEvent` and `BlockBreakEvent` remain the final protection hooks.

The native operation temporarily presents the offhand stack as the acting hand within the same synchronous call. It then restores the hands while preserving item consumption, wear, and breakage. Synchronous listeners can query `HandAccess.isOffhandAction(player)` to identify that scope. Never retain a hand-scope assumption across a scheduled task or tick.

Custom Bukkit item metadata remains attached to the actual item. The accompanying BeastEnchants integration captures the physical source during the native event and revalidates that same main-hand or offhand item before applying its delayed effects. This prevents a different weapon from receiving or triggering the effect after a hand change. Install the updated BeastEnchants build to use this integration. Existing enchantment definitions must include `OFF_HAND` in their `ActiveSlots` list where offhand activation is intended, for example `ActiveSlots: [HAND, OFF_HAND]`; the integration does not override the owner's slot policy.

Mining now dispatches a cancellable `BlockDamageEvent` with the acting tool before starting its progress loop. Its instant-break result is honored, while the final native break still runs normal protection and loot hooks. This supplies the mining-start hook used by skill plugins such as mcMMO. Do not grant XP manually alongside these native events, as that can duplicate rewards.

Geyser and Floodgate identity APIs are detected optionally; player names and UUID patterns are not used to guess client type. `/bdw integrations` reports available APIs and, for a player, client detection. Bedrock offhand placement and animation remain client/Geyser behavior. A proxy-only Geyser installation may have no local detection API while still forwarding ordinary interaction packets correctly. Third-party versions, hybrid server builds, anticheats, and client-specific behavior still require the live checks documented in the verification record.

## Updates and telemetry

The supplied bStats 3.2.1 client is bundled under the BeastDoubleWield package and initialized with the registered plugin ID **35021**. It uses the standard shared `plugins/bStats/config.yml` opt-out: an existing `enabled: false` is respected; a new bStats configuration defaults to enabled. Set that file's `enabled` to `false` and restart the server to opt out. The client retains its official randomized startup delay and 30-minute submission schedule. `/bdw reload` does not recreate it, and disabling the plugin shuts its scheduler down. See [bStats setup and data](docs/bstats.md).

`/bdw status` identifies bStats separately from custom metrics. An initialized bStats client does not establish successful submission or dashboard publication. New installations use this repository's `update.yml` as their update feed. Existing configurations keep their saved URL; set `Updates.ManifestUrl` to `https://raw.githubusercontent.com/MrAxeTv/BeastDoubleWield/main/update.yml` to use this feed, or leave it blank to disable feed requests.

To publish update notices, host a small UTF-8 manifest over HTTPS and set `Updates.ManifestUrl` to that address. The manifest accepts only these two scalar fields:

```yaml
version: '1.0'
download-url: 'https://github.com/MrAxeTv/BeastDoubleWield/releases/tag/v1.0'
```

Release comparison uses numeric version components and prerelease ordering. `Updates.IntervalMinutes` controls automatic checks; `Updates.NotifyAdmins` controls join/online notices. `/bdw update` performs a manual check with a short rate limit. The checker displays the link and never downloads or installs a plugin JAR. HTTPS redirects are rejected, responses are limited to 8 KiB, requests run off the server thread with timeouts, and failures retry with backoff. Reload and shutdown discard stale callbacks.

The separate **custom metrics** service requires both `Metrics.Enabled: true` and an owner-controlled HTTPS `Metrics.Endpoint` that accepts JSON POST requests. `Metrics.IntervalMinutes` controls only that custom service. These settings do not configure bStats; leave them disabled when only bStats is wanted. The custom payload contains schema/plugin version, Bukkit version, Java major version, current player count, and cumulative offhand attack-attempt and successful-block-break counts since plugin startup. That custom payload contains no player names, UUIDs, persistent server identifier, or address field. As with any HTTPS connection, the receiving host can observe the connection's source address. No Avarion/Ranull telemetry identity is reused.

## Building

The developer dependency `me.mraxetv.beastlib:Core:1.7.3:shaded` must be available in Maven's local repository or a configured repository. It has `provided` scope and is not shaded into this plugin. This is a separate developer SDK, not the public runtime JAR: obtain it from the BeastLib maintainer before building. The repository does not bundle that SDK or claim a fresh clone can build without it. Do not install the shaded SDK in a server's plugins folder.

```powershell
mvn test
mvn package '-Dbeastdoublewield.export.skip=true'
```

A normal `mvn package` or `mvn install` also exports this plugin JAR to seven owner-defined destinations listed in `pom.xml`, under `beastdoublewield.export.root` (default `F:/McServers`). Other developers should use the export-skip command above. Set `-Dbeastdoublewield.export.root=<directory>` to use a separate export root. Export destinations do not establish server-version compatibility. Maven builds never start/reload servers or install/enable resource packs.

Build the arm example without a local client export:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/build-firstperson-arm.ps1 -SkipClientExport
```

The separate builder exports only the completed arm ZIP to the maintainer's `C:/Users/krist/AppData/Roaming/.minecraft/resourcepacks` by default and verifies its SHA-256 against the build artifact. Other developers should use `-SkipClientExport` above, or `-ClientResourcePackDirectory <directory>` for their own existing folder. Identical bytes preserve the existing ZIP timestamps. Enabling the pack and reloading client resources remain manual.

## Runtime verification

Automated tests cover action eligibility, cancellation, inventory restoration, attribute substitution, mining sessions, commands, configuration failures, optional integrations, service lifecycle, response limits, and update/metrics data handling. See [the feature matrix](docs/feature-matrix.md), the [1.0 verification record](docs/verification.md), and the [1.1 arm development record](docs/first-person-arm.md) for test totals, artifact checksums, legacy linkage checks, and native-server evidence for each version.

Before deployment, exercise attacks against mobs and players, cancelled PvP damage, enchanted weapons, weapon breakage, protected mining, Silk Touch/Fortune, slow/instant mining, both hand changes, item-use interactions, disconnects, and configuration reloads on the intended server version. Automated tests do not replace these native-server integration checks.
