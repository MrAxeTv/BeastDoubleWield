# Verification and acceptance

This checklist separates build checks from live gameplay evidence. Do not describe unchecked scenarios as verified compatibility or complete gameplay parity.

## Current evidence

- Source implementation: reviewed for input denial, NPC/PvP/reach guards, native damage/break delegation, post-event state revalidation and inventory restoration.
- Compilation and automated tests: passed on 2026-10-30, Maven 3.9.16 / JDK 26.0.1, Spigot API 1.20.2 and BeastLib 1.7.3; 141 BeastDoubleWield tests, zero failures/errors/skips. The unchanged companion BeastEnchants build previously passed 25 tests.
- Artifact inspection: all 45 BeastDoubleWield classes and 23 companion BeastEnchants classes have Java 8 major version 52. BeastDoubleWield includes 13 compiled bStats classes (including the compiler's synthetic helper) and the upstream MIT license; no unrelocated `org/bstats` classes are present. Test fixtures, Spigot API and BeastLib are not bundled. The supplied Metrics.java source remains unchanged except its original package relocation, SHA-256 `9123C438836761D574B8B45BE53D52AC04B7913E1470354B076D76BC06866DCF`.
- Export skip: package with `beastdoublewield.export.skip=true` created no isolated export destination.
- Exported JAR checksums: BeastDoubleWield 1.0 was exported to all seven destinations; those and the seven unchanged BeastEnchants copies matched their artifacts (14 copies checked). BeastDoubleWield SHA-256: `92D6EF3095458E51CB9F2C4C42CD3E993037E571728ECA33C39A9B117C7C75B7`, size 129,417 bytes; BeastEnchants SHA-256: `78193028D4FC370B232DAB966A31E038E54F89A1A0D391D35E9E46780300620F`. See [export-verification.json](export-verification.json).
- API evidence: isolated linkage checks against 1.9.4, 1.12.2, 1.13.2, 1.16.4, 1.18.2 and 1.20.2 APIs; a direct Bukkit reference audit against the official 1.9.4 API. These do not substitute for native gameplay tests on every release.
- Live gameplay evidence: 41 checks passed across the three fixtures below before the bStats addition, using a connected Mineflayer 4.39.0 client and real server events. That gameplay build's SHA-256 was `7D8034990FE879916D8950AB467B200CF2B735E84406D92474211C267CAD4CAD`. These fixtures precede both bStats and the empty-main-hand air-input fix; they are not evidence of live telemetry delivery or a live run of the input fix.
- bStats evidence: the public registration for ID 35021 returned BeastDoubleWield, owner mraxetv. Seven added tests cover registered-ID construction, once-only lifecycle, nonfatal initialization failure, the actual supplied client's preservation of an existing global opt-out and absence of scheduled submissions, shutdown, reload continuity and command status separation. No external telemetry submissions or server starts were performed for this addition. Dashboard receipt remains unverified. See [bstats.md](bstats.md).
- Empty-main-hand input fix: eight additional input tests pass (18 input tests total), covering the offhand air callback, paired-event suppression, denied input, permissions/settings, empty-offhand allowance, ray-target attacks and disconnect cleanup. The installed vanilla 26.3 client's input and first-person rendering branches were inspected; both-empty air clicks send no input and empty offhand arms are not rendered in first person. Server configuration was read but not changed. See [empty-hand-input.md](empty-hand-input.md).
- Resource-pack examples: all five profiles passed archive structure, selector boundaries, ordinary-feather fallback and repeated-build hash/timestamp checks. These are pack-format tests, not native visual rendering. No client pack was installed or enabled by this work.
- Geyser/Bedrock clients, hybrid servers, unlisted versions, and other third-party plugins have not been runtime-certified. The separate update checker and custom metrics service have parser/lifecycle/transport-bound tests. New configs use the repository's release feed; native automatic update notification has not been live-tested. No custom metrics endpoint is configured.

## Version 1.0 live runs, 2026-10-30

The release JAR is `BeastDoubleWield-1.0.jar`, SHA-256 `92D6EF3095458E51CB9F2C4C42CD3E993037E571728ECA33C39A9B117C7C75B7`. The release fixtures use the publicly downloadable BeastLib 1.7.0 runtime, SHA-256 `B6EA11378E749D0B7AC5AC9FD58E2356E5746EDFC5D7F739D9F379CBC03CF51C`. The compile SDK remains BeastLib 1.7.3; the SDK is not included in the plugin or repository.

| Fixture | Runtime | Checks passed | Evidence |
| --- | --- | --- | --- |
| Spigot 1.9.4, git-Spigot-2f34086-e1d3516 | Java 8u44, public BeastLib 1.7.0 | 17/17 | Native axe/sword damage, correct-tool gate, legacy hit-sound binding, AIR-tool rejection, main-hand restoration, tool wear, native damage/mining events, network attack/held mining, cancelled damage/break, and safe refusal of the unsupported placeholder |
| Spigot 1.20.2, 3926-Spigot-dba3cdc-ffb1319 | Java 17.0.0.1, public BeastLib 1.7.0 | 37/37 | Native PDC/model metadata, ordinary feather/item preservation, actual villager trading, observer-confirmed empty-offhand swing packet, native attack restoration and cleanup inside its damage event, genuine cursor stack, F-key swap/drop packets, personal toggle, reload, keepInventory death/respawn, quit/reconnect and plugin-disable cleanup |

The modern fixture exposed a native Spigot hand-swap path that dereferences event item stacks despite nullable API annotations. Using explicit AIR stacks fixed that failure. The final run had zero probe errors or exceptions. Fixture sources are in `tools/placeholder-probe`; its final log is `target/release-runtime-placeholder/final-run.log`. The server stopped cleanly and port 25593 was closed.

The legacy fixture's source is in `tools/legacy-probe`; final evidence is `target/release-runtime-1.9.4/final-artifact-run.log` and `final-result.json`. It passed all 17 checks on the exact final release hash, then stopped with port 25594 closed. The native 1.9.4 server was built from official Spigot revisions; its SHA-256 is `76654AC6A5BCA3A7E42A7B5F03A20C5D1CF63CB8768B2C7079AD57F0F4060636`. No server binaries or decompiled sources are distributed. Version 1.9.4 is tested; untested 1.9 patch releases are not implied certified.

Global bStats/legacy PluginMetrics opt-outs, custom metrics and update requests were disabled in these disposable fixtures. They used isolated worlds and loopback connections, and did not start or reload any existing server. Packet observations verify server input and animation requests; no native client resource-pack rendering or bare-offhand fist rendering is claimed.

## Earlier gameplay runs, 2026-10-30

| Fixture | Runtime | Checks passed | Evidence |
| --- | --- | --- | --- |
| Spigot 1.12.2, git-Spigot-dcd1643-e60fc34 | Java 8u44, BeastLib 1.7.3 | 12/12 | Native offhand axe damage, main-hand preservation, tool wear, normal damage event, cancelled damage, network right-click attack, network held-right-click mining, single final break, mining-start event, mining wear, cancelled native break and unchanged protected block |
| Spigot 1.20.2, 3926-Spigot-dba3cdc-ffb1319 | Java 17.0.0.1, BeastLib 1.7.3, companion BeastEnchants | 14/14 | Same core checks plus genuine BeastEnchants offhand lifesteal and frost after the deferred tick |
| Paper 1.16.5, git-Paper-592 | Java 11.0.0.2, BeastLib 1.7.3, mcMMO 2.1.202 | 15/15 | Same core checks plus actual mcMMO AXES, SWORDS and MINING XP-gain events |

Paper's final observed axe attribute and native damage were both 9.0, reducing the test mob from 40 to 31 health. Its sword damage was 7.0. Earlier failed probes exposed the 1.16 mob-only Bukkit attack path, native attribute key/operation changes, and a 1.12 covariant-method reflection ambiguity; the corrected paths passed the final runs and have regression tests.

The mcMMO fixture set `Experience_Formula.Eggs.Multiplier: 1` only in its disposable configuration so plugin-created test mobs could earn XP. The plugin itself does not change mcMMO configuration or grant XP.

Reproducible fixture sources are in `tools/runtime-probe`. Full per-run logs remain under `target/runtime-probe/<fixture>/logs/latest.log`. Final result records were `passed=12 failed=0`, `passed=14 failed=0`, and `passed=15 failed=0`. All isolated servers were stopped and ports 25595–25597 were confirmed closed. Existing server processes and worlds were not used by the test client.

Building must never start or reload a server, install a resource pack, or change client pack lists. The seven parent-defined JAR export destinations are build distribution paths, not compatibility claims. Use the project export-skip property when an isolated build is required. After export changes, compare SHA-256 of every exported JAR with the final build artifact.

## Build review

- Compile against Spigot API 1.20.2 and BeastLib 1.7.3; inspect produced classes for Java 8 bytecode.
- Confirm plugin name, main class, package, command aliases and permission prefixes use BeastDoubleWield naming.
- Confirm BeastLib remains a runtime dependency and upstream implementation/assets are absent from the JAR.
- Run meaningful automated tests for state transitions, filtering, validation and protection-sensitive behavior where available.
- Inspect JAR contents for the correct descriptor, default configuration and messages.
- Verify all seven configured export hashes. Verify an export-skip build does not write to the destinations.

## Manual acceptance on an explicitly selected test server

Record the exact server version/build, Java runtime, BeastLib version, plugin JAR hash, installed integrations and client version before testing. Builds do not start or reload servers; the live runs above were separate disposable test sessions.

### Input and inventory integrity

1. Put different weapons in each hand. Left-click and right-click separately, then alternate rapidly. Only the intended hand should act; one input must not produce two offhand actions from the two Bukkit hand callbacks.
2. Verify offhand damage and cooldown with an empty main hand, a slower main-hand axe, and a faster main-hand item. Main-hand item choice must not bypass offhand pacing.
3. Swap hands, change hotbar slots, drop the tool, replace it through inventory, teleport, change worlds, die, disconnect and disable the plugin while an action is active. No item may duplicate, disappear, revert, or continue acting after invalidation.
4. Use a nearly broken enchanted tool and repeat the above. Verify correct stack count and durability, including when another plugin edits inventory during events.
5. Hold right-click on a block, release it, and move the crosshair to another block. Mining must stop/reset predictably. Looking away must invalidate the old target; release is detected through the configured short input timeout, so completion within that timeout is possible and must match documented behavior.

### Combat

1. Compare offhand attacks across material tiers, swords, axes, other allowed tools and optional empty-hand mode.
2. Check strength, weakness, attack-speed attributes, critical attacks, knockback, fire aspect, sharpness, smite, bane, sweeping, looting, unbreaking and item break behavior wherever implemented. Record any unsupported behavior explicitly.
3. Verify reach and line of sight: an entity behind a solid wall or outside the configured distance must not be attacked.
4. Verify PvP-disabled worlds, friendly-fire restrictions and another plugin cancelling damage. Cancellation must also prevent inappropriate fire, knockback, durability loss and cooldown side effects according to the documented contract.
5. Test invulnerable entities, Citizens NPCs, armor stands, mounts and ordinary mobs. NPCs must remain protected by default.

### Mining

1. Compare suitable/unsuitable tools on stone, logs, dirt, leaves, cobwebs and instant-break blocks. Test survival and explicitly enabled creative/adventure modes separately.
2. Test efficiency, haste, mining fatigue, underwater/airborne mining, fortune, silk touch and unbreaking. Confirm correct drops, experience and durability with no duplicate rewards.
3. Attempt bedrock, barriers, fluids, protected regions and blocks beyond reach or behind an obstruction. These attempts must not break blocks or produce rewards.
4. Cancel the custom event, standard block-break event and item-damage event independently. Verify world state, rewards, experience and tools remain consistent with each cancellation.
5. Change the block externally while mining and put a different block at the same coordinates. An old session must not break the replacement unexpectedly.
6. Mine simultaneously with two players. Each completed block must produce at most one native break/drop sequence; progress visuals must clear for nearby observers.

### Ordinary interactions

1. Right-click chests, doors, crafting tables, buttons and other interactive blocks with tools in the offhand. Confirm documented interaction priority and sneaking override.
2. Mount horses/boats, trade with villagers, feed/tame animals, and use leads without accidental offhand damage.
3. Use shields, bows/crossbows, buckets, maps, potions, pearls, eggs, spawn eggs, seeds, fireworks and flint-and-steel. Excluded items must retain ordinary behavior.
4. Check shears both as a mining tool and against shearable entities; document any conflict and configured override.

### Configuration and access

1. Test every attack/mine permission combination as a non-operator; verify commands and tab completion expose only permitted actions.
2. Toggle offhand functionality, reconnect and confirm documented persistence. Test another player's toggle only where explicitly supported and authorized.
3. Exercise stance settings, game-mode/world restrictions, empty-hand mode, material allow/deny modes, regex matching, correct-tool mode and optional air swings.
4. Reload a valid configuration while mining or during an attack cooldown. Confirm state cleanup and application of the new settings.
5. Try malformed YAML, invalid regex, invalid game modes, nonfinite/out-of-range numbers and invalid materials. Reload must fail clearly and preserve the previous working configuration without partial replacement.
6. Confirm configurable messages render through BeastLib and no upstream plugin branding remains.

### Integration claims

Test each named protection, skill, custom-enchantment, anticheat or Geyser integration separately with exact versions before claiming support. Bukkit event compatibility alone is not evidence that every integration interprets offhand attacks and tool state correctly. Bedrock offhand item placement and animation behavior must be checked through the selected Geyser version.
