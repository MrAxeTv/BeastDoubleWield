# BeastDoubleWield feature inventory

This inventory records the observable behavior used to plan an original Beast plugin. It distinguishes documented upstream behavior from implementation and verification evidence. A completed build alone does not establish gameplay parity.

## Origin and research boundary

Research on 2026-10-30 identified AvarionMC/dualwield as the project referenced by the owner. Research examined its README, public configuration and plugin descriptor, release notes, issue descriptions, and the original author's public marketplace discussion. No upstream Java implementation, decompiled code, binary assets, or artwork was used to write BeastDoubleWield. The configuration was read to identify user-facing capabilities; BeastDoubleWield's implementation and documentation are original. This is an engineering provenance record, not a legal conclusion about licensing.

The similarly named Modrinth DualWield project links to a different GitLab fork. Its newer compatibility claims are not evidence about this implementation.

## Feature matrix

Implementation evidence below records source review. Runtime claims are limited to the exact scenarios and server builds recorded in [verification.md](verification.md); the presence of a compatibility adapter does not establish blanket support.

| Capability | Public behavior or requirement | Implementation evidence | Runtime verification |
| --- | --- | --- | --- |
| Offhand attacks | Right-click uses the offhand weapon against a target | `OffhandInput` and `OffhandCombat`: native player attack inside a synchronous offhand transaction | Automated coverage; see live verification matrix |
| Offhand mining | Right-click uses an offhand tool; mining has progress, animation and hit sounds | `OffhandMining`: progress via Bukkit break speed, lease-based input, native final break, swings and optional hit sounds | Automated coverage; see live verification matrix |
| Empty hand | Independently configurable for attacks and mining | `ItemRules`: `allow-empty-hand` | Automated coverage; see live verification matrix |
| Standing and sneaking | Independently configurable for each action | `ItemRules`: stance checks | Automated coverage; see live verification matrix |
| Game modes | Separate allowlists for attacks and mining | `ItemRules`: validated `game-modes` lists | Automated coverage; see live verification matrix |
| Material filters | Blacklist/whitelist and regular-expression matching | `ItemRules`: validated compiled regex filters | Automated coverage; see live verification matrix |
| Appropriate tools | Optional restriction to suitable attack/mining tools | Mining uses the Bukkit/native preferred-tool adapter; attack classification uses vanilla weapon/tool families and positive custom Bukkit attack-damage modifiers | See verification record |
| Right-click cancellation | Separate per-action control | `OffhandInput`: cancellation only after a service claims the interaction; prior item/block denials respected | Automated coverage; see live verification matrix |
| Air swings | Optional animation when no target is found | `OffhandInput`: `swing.air`, action eligibility and permission checks; offhand air-use input accepted when the main hand is empty; paired callbacks deduplicated | Automated coverage; both-empty vanilla air clicks send no input |
| Invisible input placeholder | Optional owner-requested addition for an otherwise empty main hand | `PlaceholderService` and `PlaceholderItem`: owned feather with model ID 35021, eligibility rules, inventory/lifecycle cleanup; matching pack examples | Requires 1.14+ and a client pack; see verification record; no empty offhand skin-arm rendering claim |
| Potion effects | Strength affects attacks; haste/mining fatigue affect mining | Bukkit/native attack and break-speed adapters delegate effect calculation to the server | See verification record |
| Enchantments and durability | Native-like tool/weapon behavior expected; upstream documentation does not enumerate all enchantments | Native attack/break operations, scoped item/attribute substitution, and restoration of mutated stacks | Automated coverage; see live verification matrix |
| Damage protection | Other plugins may cancel ordinary entity damage | `OffhandCombat` uses the Bukkit/native player-attack adapter; additional PvP-world, NPC, reach and line-of-sight guards | Automated coverage; see live verification matrix |
| Block protection | Other plugins may cancel ordinary block breaks | `OffhandMining` calls the Bukkit/native block-break adapter, which dispatches ordinary break/drop events | Automated coverage; see live verification matrix |
| Player permissions | Separate attack, mine and administrator reload permissions | Beast-prefixed descriptor, command and gameplay permission checks | Automated coverage; see live verification matrix |
| Commands | Plugin information and configuration reload | `DoubleWieldCommand`: help, status, integrations, reload, personal toggle and update; replacement config validated before publication | Automated coverage; see live verification matrix |
| Public event API | Cancellable offhand attack and block-break hooks | `api.OffhandAttackEvent`, `api.OffhandBlockBreakEvent` | Automated coverage; see live verification matrix |
| Offhand rate limit | Improvement addressing a reported upstream main-hand-speed issue | `AttackLimiter` and `WeaponAttributes`: additional admission limit based on swapped attack speed; native cooldown remains in force, so independent full-strength attacks are not claimed | Automated coverage; see live verification matrix |
| NPC protection | Improvement addressing an upstream Citizens damage report | `OffhandCombat`: configurable default protection for the `NPC` metadata marker | Automated coverage; see live verification matrix |
| Interaction priority | Improvement addressing mounting and ordinary right-click conflicts | `OffhandInput` preserves main-hand use and interactive blocks; `OffhandCombat` preserves listed mount/trade/tame entity interactions unless sneaking | Automated coverage; see live verification matrix |
| Player toggle | BeastDoubleWield usability addition | `PlayerPreferences`: atomic `players.yml` persistence, with migration from the initial build's PDC value | Automated persistence tests; live status in verification record |
| BeastLib integration | Owner-required BeastLib API integration | BeastLib API, YAML/language services, message rendering and scheduler services | Automated coverage; see live verification matrix |
| Legacy server operations | Upstream advertised offhand-era releases beginning at 1.9 | `ServerAccess` selects modern Bukkit operations or isolated reflective native legacy paths for attack, break, speed, attributes, progress, sounds and targeting; legacy-safe preferences and type references | Exact API linkage/runtime results in verification record |
| Skill-plugin mining hook | Upstream claimed third-party skill compatibility including mcMMO | Cancellable `BlockDamageEvent` dispatch with the actual acting tool; instant-break result honored; native final break supplies normal XP/drop listeners | mcMMO 2.1.202 XP verified on Paper 1.16.5; ability-start rules documented |
| Delayed BeastEnchants effects | Owner-requested Beast integration | BeastEnchants `CombatSource` captures HAND/OFF_HAND and later validates the actual physical source; slot policies remain authoritative | BeastEnchants regression tests passed; live evidence in verification record |
| Bedrock clients | Upstream documented Geyser attack behavior and missing Bedrock swing animation | Optional Geyser/Floodgate API identity detection, integration diagnostics, ordinary server interaction handling | Detection unit-tested; client gameplay requires live verification |
| Update checking | Upstream release notes mention an update checker | Asynchronous bounded HTTPS owner-manifest checks, numeric/prerelease comparison, admin notices, manual command and retry backoff; no automatic installation | Parsers/settings/lifecycle unit-tested; new configs point to this repository's update.yml |
| bStats telemetry | Upstream README displays bStats statistics | Owner-supplied bStats 3.2.1 client, package-only change, registered BeastDoubleWield ID 35021; standard global opt-out and cadence; once-per-enable lifecycle | Lifecycle, opt-out and reload tests; dashboard receipt not verified |
| Custom telemetry | Optional additional owner-hosted service | Opt-in asynchronous aggregate JSON submissions to an owner-controlled HTTPS endpoint, with timeouts/backoff and no player or persistent-server identifiers | Payload/lifecycle unit-tested; custom collection endpoint not supplied |

## Scope and compatibility

- Compile baseline: Spigot API 1.20.2, Java 8 bytecode. The server's Java requirement still applies. The compatibility bridge isolates newer Bukkit types from legacy call paths rather than requiring those methods at plugin class load.
- Compile SDK: BeastLib 1.7.3; exact tested runtime dependencies are listed in the verification record.
- Mining release detection uses a configurable short input timeout rather than an exact client release packet; a break can finish within that timeout after the last click. Real client interaction packets verified held-input mining on the three recorded server builds; this does not establish exact release timing for every client.
- Native attack cooldown is shared with ordinary attacks. The added offhand rate limit prevents excessively frequent admitted offhand attacks; it does not provide two independent fully charged vanilla attacks.
- `attack.allow-empty-hand` concerns the offhand. An empty main hand with a held offhand item generates an offhand air-use callback; both hands empty generate no vanilla air-use packet. The server can process empty-offhand entity interactions, but cannot synthesize a right-click-air signal that the client did not send. See [empty-hand-input.md](empty-hand-input.md).
- The optional attack `correct-tool-only` setting recognizes vanilla weapon/tool material families and items with positive custom attack-damage modifiers for the acting hand. Material filters and all other action restrictions still apply.
- Historical server versions, hybrid servers, Geyser clients, anticheat plugins, mcMMO, and arbitrary custom enchantment plugins are not verified simply because the reference plugin advertised them. Folia is explicitly rejected because cross-entity combat ownership has not been implemented for it; the reference README did not advertise Folia.
- Updated BeastEnchants code resolves delayed offhand source validation. Existing definitions must allow `OFF_HAND` in `ActiveSlots`; no configuration or protection policy is bypassed to force an effect.
- Public documentation establishes no crafting recipes, special dual-wield enchantment, classes, skill trees, economy, or menus. These are not recorded as missing parity features.
- The update checker and custom telemetry use owner-configured HTTPS destinations and remain inactive with blank URLs. bStats is independently configured through its standard global file and uses the owner's registered BeastDoubleWield ID 35021; a fresh global bStats configuration defaults to enabled.
- No Avarion/Ranull update endpoint, telemetry identifier, branding, or artwork is reused. The supplied bStats client sends its standard platform/plugin statistics and persistent server UUID. The separate custom metrics payload contains aggregate counts and versions without a server identifier. Receiving network hosts can necessarily observe the connection's source address.

## Sources

- [AvarionMC README: behavior, permissions, events and compatibility claims](https://github.com/AvarionMC/dualwield/blob/master/README.md)
- [Public configuration: action settings and material filtering](https://github.com/AvarionMC/dualwield/blob/master/core/src/main/resources/config.yml)
- [Public descriptor: commands, permissions and dependency declarations](https://github.com/AvarionMC/dualwield/blob/master/core/src/main/resources/plugin.yml)
- [Release v3.1.1: update checker and platform changes](https://github.com/AvarionMC/dualwield/releases/tag/v3.1.1)
- [NPC damage report](https://github.com/AvarionMC/dualwield/issues/9)
- [Mounting conflict report](https://github.com/AvarionMC/dualwield/issues/8)
- [1.20.4 runtime failure report](https://github.com/AvarionMC/dualwield/issues/10)
- [Author discussion: status effects, mining and attack-speed issue](https://www.spigotmc.org/threads/dualwield.456165/page-3)
- [Author discussion: Geyser interactions and no persistent player-data edits](https://www.spigotmc.org/threads/dualwield.456165/page-5)
