# BeastDoubleWield

BeastDoubleWield adds right-click offhand attacks and progressive offhand mining to a Bukkit-family Minecraft server. The plugin uses BeastLib for configuration files, messages, and task scheduling. It is an original implementation; no AvarionMC/Ranull plugin code or assets are bundled.

## Installation

1. Install [BeastLib](https://modrinth.com/plugin/beastlib). Public runtime version **1.7.0** passed the release's 1.9.4 core and 1.20.2 placeholder gameplay fixtures; the earlier integration runs used 1.7.3. See the exact tested builds in the verification record below.
2. Download `BeastDoubleWield-1.0.jar` from [GitHub Releases](https://github.com/MrAxeTv/BeastDoubleWield/releases/tag/v1.0) and place it in the server's `plugins` directory.
3. Start the server yourself and configure `plugins/BeastDoubleWield/config.yml` and `Lang/English.yml`.

The compilation baseline is Spigot 1.20.2 with Java 8 bytecode. The legacy bridge invokes **NMS through reflection** for native attack, mining, tool, animation and targeting operations where newer Bukkit methods are missing. It resolves specific signatures and version-dependent alternatives; reflection does not make every release interchangeable. Startup logs report the selected backend. Consult [the verification record](docs/verification.md) and [legacy compatibility notes](docs/legacy-compatibility.md) for measured support and remaining gaps. Use the Java version required by your server. Folia is currently rejected during startup. Core gameplay needs no client mod or resource pack; the optional invisible placeholder below requires a pack.

## Using the offhand

Put a tool or weapon in the offhand and right-click the target. Hold right-click while looking at a block to continue mining. Changing held equipment, looking away, moving out of range, teleporting, dying, or opening an inventory stops the mining session. The server supplies the mining speed, drops, tool wear, enchantment behavior, and final break through native server operations.

Offhand attacks likewise use the server's native attack operation so combat mechanics remain under server control. The configured offhand cooldown is an additional input limit; turning it off does not remove the server's native attack cooldown. Normal protection plugins can cancel the native damage or block-break events.

The default interaction rules preserve main-hand item use and ordinary interaction with blocks or entities. Sneaking permits the configured offhand action on interactive targets. Material filters, permitted game modes, standing/sneaking rules, reach, empty-hand actions, correct-tool checks, and interaction cancellation can be configured separately for attacking and mining. See the comments in `config.yml` for the precise switches.

`attack.allow-empty-hand` permits an empty **offhand** to attack on right-click; ordinary main-hand punches still use left-click. `swing.air` requests an offhand swing when right-clicking without a target. With a weapon/tool in the offhand and an empty main hand, the plugin accepts the offhand air-use callback. When **both hands are empty**, vanilla Minecraft sends no right-click-air input, so a server plugin cannot detect that click. Right-clicking an entity still sends an interaction; holding a non-use item such as a stick in the main hand also permits an empty offhand swing request when both settings are enabled. Vanilla 26.3 does not render an empty offhand arm in first person, even when a swing is requested; empty-offhand combat can therefore work without a visible first-person fist animation. Mining has its own `mine.allow-empty-hand` switch.

## Optional invisible main-hand item

On **Minecraft 1.14+**, the plugin can supply a disposable placeholder in an otherwise empty main-hand slot. It gives the client an item to send right-click-air input for, including when the offhand is empty. Install the matching [example resource pack](examples/resourcepack/README.md) from the release, then set these existing configuration keys:

```yaml
swing:
  air: true
attack:
  allow-empty-hand: true
  correct-tool-only: false
placeholder:
  enabled: true
```

Edit those keys in their existing sections; do not add duplicate YAML sections. Keep `attack.correct-tool-only: false` for fist attacks, and allow `AIR` if using an attack material whitelist. Run `/bdw reload` after changing settings. The placeholder defaults to disabled and remains unavailable below 1.14. Core legacy gameplay works independently.

The carrier is a feather with fixed CustomModelData `35021`, a blank visible name, no lore, and a private ownership tag. Hidden metadata is necessary to select the model and clean up only the plugin's disposable item. Ordinary feathers remain visible. Without the pack, the carrier appears as a feather; a pack cannot make vanilla draw an empty offhand skin arm. Advanced tooltips on older clients can still expose item information.

It respects attack permissions, personal/global switches, world and attack rules, and only runs in Survival/Adventure. It pauses for real main-hand items, inventory use and full storage; keep another storage slot free. Only tagged placeholders are removed on slot changes, drops, swaps, death, disconnect, reload or disable. The first inventory click involving a placeholder is cancelled while it is removed; repeat that click to move a real item into the cleared slot. `/bdw status` reports its state. Packs are offered as downloads and never installed or enabled automatically. The pack examples have structural tests; native visual rendering remains a manual acceptance check.

The placeholder cannot be given to vanilla item frames, armor stands, or allays. Normal trading and mounting remain available. Plugins that directly copy, transform, or transfer held items need their own compatibility checks; arbitrary custom transfer logic is not certified by these guards.

## Experimental first-person skin arm (1.1 development)

The optional `placeholder.first-person-arm: true` setting adds a skin-textured fist when the offhand is empty. It also requires `placeholder.enabled`, `swing.air`, and `attack.allow-empty-hand`, with `attack.correct-tool-only: false`. This feature requires Paper 26.3's public component APIs, a native 26.3 Java client and the separate [first-person arm pack](examples/firstperson-arm/README.md). It is disabled by default. The published 1.0 release does not contain it.

The protected carrier uses the player's already available complete skin profile, with separate classic/slim models. A shared shader pack samples that profile's native skin texture; the plugin does not download or bake a skin PNG for each player. The item renders only in first person, while the normal player entity and its swing remain in use in third person. When the arm carrier is present, the main hand stays empty so its ordinary arm can render normally. Combat and filtering treat the owned carrier as AIR, and native actions receive an actual empty acting hand.

Install the matching pack manually before enabling this setting. Missing profiles and unsupported versions retain the ordinary input placeholder behavior. Known Bedrock clients and detected ViaVersion clients using another protocol do not receive the arm. Proxy-only translation cannot always be detected: server owners must restrict this feature to matching clients. Without the pack, the carrier can appear as a head. A real offhand item always takes priority. Skin changes refresh when the carrier is recreated, such as after toggling or reconnecting. The normal third-person swing stays in use, though holding an invisible item can still affect the native resting arm pose.

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

Personal preferences are stored atomically in `players.yml` and survive reconnects and server restarts, including on servers without the Persistent Data Container API. Preferences from the initial PDC-based build migrate on first use. The global `enabled` switch overrides every player's preference. A malformed or invalid configuration reload retains the previously active settings. A successful reload stops current mining and clears combat input state before new actions use the new settings.

## Integration

Other plugins can cancel `me.mraxetv.beastdoublewield.api.OffhandAttackEvent` or `OffhandBlockBreakEvent` before the corresponding native operation. Native `EntityDamageByEntityEvent` and `BlockBreakEvent` remain the final protection hooks.

The native operation temporarily presents the offhand stack as the acting hand within the same synchronous call. It then restores the hands while preserving item consumption, wear, and breakage. Synchronous listeners can query `HandAccess.isOffhandAction(player)` to identify that scope. Never retain a hand-scope assumption across a scheduled task or tick.

Custom Bukkit item metadata remains attached to the actual item. The accompanying BeastEnchants integration captures the physical source during the native event and revalidates that same main-hand or offhand item before applying its delayed effects. This prevents a different weapon from receiving or triggering the effect after a hand change. Install the updated BeastEnchants build to use this integration. Existing enchantment definitions must include `OFF_HAND` in their `ActiveSlots` list where offhand activation is intended, for example `ActiveSlots: [HAND, OFF_HAND]`; the integration does not override the owner's slot policy.

Mining now dispatches a cancellable `BlockDamageEvent` with the acting tool before starting its progress loop. Its instant-break result is honored, while the final native break still runs normal protection and loot hooks. This supplies the mining-start hook used by skill plugins such as mcMMO. Do not grant XP manually alongside these native events, as that can duplicate rewards.

Geyser and Floodgate identity APIs are detected optionally; player names and UUID patterns are not used to guess client type. `/bdw integrations` reports available APIs and, for a player, client detection. Bedrock offhand placement and animation remain client/Geyser behavior. A proxy-only Geyser installation may have no local detection API while still forwarding ordinary interaction packets correctly. Third-party versions, hybrid server builds, anticheats, and client-specific behavior still require the live checks documented in the verification record.

## Updates and telemetry

The supplied bStats 3.2.1 client is bundled under the BeastDoubleWield package and initialized with the registered plugin ID **35021**. It uses the standard shared `plugins/bStats/config.yml` opt-out: an existing `enabled: false` is respected; a new bStats configuration defaults to enabled. Set that file's `enabled` to `false` and restart the server to opt out. The client retains its official randomized startup delay and 30-minute submission schedule. `/bdw reload` does not recreate it, and disabling the plugin shuts its scheduler down. See [bStats setup and data](docs/bstats.md).

`/bdw status` identifies bStats separately from custom metrics. An initialized bStats client does not establish successful submission or dashboard publication. New installations use this repository's `update.yml` as their update feed. Existing configurations keep their saved URL; set `updates.manifest-url` to `https://raw.githubusercontent.com/MrAxeTv/BeastDoubleWield/main/update.yml` to use this feed, or leave it blank to disable feed requests.

To publish update notices, host a small UTF-8 manifest over HTTPS and set `updates.manifest-url` to that address. The manifest accepts only these two scalar fields:

```yaml
version: '1.0'
download-url: 'https://github.com/MrAxeTv/BeastDoubleWield/releases/tag/v1.0'
```

Release comparison uses numeric version components and prerelease ordering. `updates.interval-minutes` controls automatic checks; `updates.notify-admins` controls join/online notices. `/bdw update` performs a manual check with a short rate limit. The checker displays the link and never downloads or installs a plugin JAR. HTTPS redirects are rejected, responses are limited to 8 KiB, requests run off the server thread with timeouts, and failures retry with backoff. Reload and shutdown discard stale callbacks.

The separate **custom metrics** service requires both `metrics.enabled: true` and an owner-controlled HTTPS `metrics.endpoint` that accepts JSON POST requests. `metrics.interval-minutes` controls only that custom service. These settings do not configure bStats; leave them disabled when only bStats is wanted. The custom payload contains schema/plugin version, Bukkit version, Java major version, current player count, and cumulative offhand attack-attempt and successful-block-break counts since plugin startup. That custom payload contains no player names, UUIDs, persistent server identifier, or address field. As with any HTTPS connection, the receiving host can observe the connection's source address. No Avarion/Ranull telemetry identity is reused.

## Building

The developer dependency `me.mraxetv.beastlib:Core:1.7.3:shaded` must be available in Maven's local repository or a configured repository. It has `provided` scope and is not shaded into this plugin. This is a separate developer SDK, not the public runtime JAR: obtain it from the BeastLib maintainer before building. The repository does not bundle that SDK or claim a fresh clone can build without it. Do not install the shaded SDK in a server's plugins folder.

```powershell
mvn test
mvn package '-Dbeastdoublewield.export.skip=true'
```

A normal `mvn package` or `mvn install` also exports this plugin JAR to seven owner-defined destinations listed in `pom.xml`, under `beastdoublewield.export.root` (default `F:/McServers`). Other developers should use the export-skip command above. Set `-Dbeastdoublewield.export.root=<directory>` to use a separate export root. Export destinations do not establish server-version compatibility. Builds never start/reload servers or install/enable resource packs.

## Runtime verification

Automated tests cover action eligibility, cancellation, inventory restoration, attribute substitution, mining sessions, commands, configuration failures, optional integrations, service lifecycle, response limits, and update/metrics data handling. See [the feature matrix](docs/feature-matrix.md), the [1.0 verification record](docs/verification.md), and the [1.1 arm development record](docs/first-person-arm.md) for test totals, artifact checksums, legacy linkage checks, and native-server evidence for each version.

Before deployment, exercise attacks against mobs and players, cancelled PvP damage, enchanted weapons, weapon breakage, protected mining, Silk Touch/Fortune, slow/instant mining, both hand changes, item-use interactions, disconnects, and configuration reloads on the intended server version. Automated tests do not replace these native-server integration checks.
