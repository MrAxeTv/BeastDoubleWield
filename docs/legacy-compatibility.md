# Legacy server compatibility

BeastDoubleWield targets Spigot-compatible servers from Minecraft 1.9 onward. The evidence below is specific to named releases; it is not a claim that every patch, fork, Java runtime, integration, or client has been tested. Java 8 bytecode alone does not establish server compatibility. The gameplay results and their build hashes are recorded in [verification.md](verification.md).

## How the native adapter works

The compatibility layer prefers Bukkit operations when they exist. Older servers still execute their own native player attack, mining-progress calculation and block-break operation through reflection. Reflection is a way to call NMS; it does not remove version differences.

Each operation has explicit method names and compatible argument/return types. An absent or ambiguous binding throws a diagnostic error rather than selecting another similarly shaped operation. Covariant compiler bridge methods are ignored. Modern-only Bukkit types are kept behind a separate class so legacy servers do not resolve them while loading the plugin.

The player attack path deliberately uses native `EntityHuman.attack(Entity)` through the versioned 1.9–1.16 generations. Official CraftBukkit 1.15.2/1.16.5 sources route `LivingEntity.attack` through the mob operation; CraftBukkit 1.17.1 adds the player-specific branch. The native path therefore remains necessary even when that earlier API method exists.

## Version-sensitive bindings audited for version 1.0

| Operation | Binding and evidence |
| --- | --- |
| Player attacks | Mapped `EntityHuman.attack(Entity)`; native gameplay covered by the recorded 1.9.4, 1.12.2 and 1.16.5 runs. |
| Block breaks | Mapped `playerInteractManager` and `breakBlock(BlockPosition)`; the obfuscated vanilla method changes from `b` to `a` between releases, but the adapter uses the mapped name. |
| Mining progress | State method with player, world/block-access and position arguments, returning `float`; explicit `a`/`getDamage`/`getDestroyProgress` names. |
| Block state and position | `CraftBlock.getData0()` on older releases; `getNMS()` and `getPosition()` where available; otherwise construct the mapped `BlockPosition(int,int,int)`. |
| Hit sounds, 1.9/1.9.2/1.9.4/1.10.2 | Protected mapped `Block.stepSound` field. These releases do not supply the later mapped no-argument getter. |
| Hit sounds, 1.11.2/1.12.2/1.13.2 | Mapped `Block.getStepSound()` getter. |
| Hit sounds, 1.14.4/1.15.2 | Mapped `Block.getStepSound(IBlockData)` getter, supplied with the actual state. |
| Hit sound selection | Read sound registry identifiers and select the `.hit` entry, rather than depending on an obfuscated sound-field index. Bukkit `SoundGroup` is preferred on newer releases. |
| Tool checks | Native stack speed and harvest checks. Null, AIR and zero-amount stacks are rejected first; legacy `CraftItemStack.asNMSCopy(AIR)` can return null. |
| Item attributes | Native `Multimap`; accepts legacy string keys and later named registry keys, and integer or named-enum operations. |
| Legacy geometry | Mapped `rayTrace`, `getBoundingBox`, `getX/Y/Z`, ray-hit `pos`, vector `x/y/z`; the six 1.9.4/1.12.2 bounding-box coordinates are `a` through `f`. |
| Offhand animation and block cracks | Native hand enum, swing method, `PacketPlayOutBlockBreakAnimation(int,BlockPosition,int)` and mapped connection packet send. |

This audit fixed the missing 1.9/1.10 sound-field fallback, retained state-dependent sounds on 1.14/1.15, and prevented empty tools from becoming null native stacks. Regression tests exercise these cases and refusal of unknown bindings. An isolated native 1.9.4 gameplay run also passed attacks, mining, sounds, correct-tool checks, durability and cancellation with public BeastLib 1.7.0; see the verification record for its artifact hash and exact checks.

## Evidence and reproduction

The official [Spigot version metadata](https://hub.spigotmc.org/versions/1.9.4.json) identifies the exact 1.9.4 sources:

| Repository | Commit |
| --- | --- |
| BuildData | `9b100f8a6f0d86b5fd5f22f65dd6dd9eba6547d3` |
| Bukkit | `c5bf6bc614bfd1e119e7fc067ff30cd8e8221cb2` |
| CraftBukkit | `e1d3516bef9fb294689c3a859b828af54d0f883e` |
| Spigot | `2f340860499157eaad380099d09fbb69948fe218` |

The audit used those official Git histories, their mappings and CraftBukkit sources, and Mojang's 1.9.4 server (`SHA-1 edbb7b1758af33d365bf835eb9d13de005b1e274`). The sound transitions were also checked against each named release's official BuildData/CraftBukkit revision. This work does not use another dual-wield plugin's implementation.

On 2026-10-30, official BuildTools completed a local Spigot 1.9.4 build. The current native adapter also compiled directly against that server's API. A standalone Java 8 native bootstrap probe resolved `minecraft:block.stone.hit`, diamond-pickaxe stone speed `8.0`, harvest eligibility, and the native attack-damage/attack-speed attribute map. This probe initialized native registries without starting a server; it is narrower evidence than a connected gameplay run.

An isolated connected run then passed **17/17 checks** on `git-Spigot-2f34086-e1d3516` (1.9.4), Java 8u44, and the publicly distributed BeastLib 1.7.0. All 17 checks passed again on the final BeastDoubleWield 1.0 artifact with SHA-256 `92D6EF3095458E51CB9F2C4C42CD3E993037E571728ECA33C39A9B117C7C75B7`. The BeastLib JAR's SHA-256 was `B6EA11378E749D0B7AC5AC9FD58E2356E5746EDFC5D7F739D9F379CBC03CF51C`.

The checks covered native axe damage (9.0), preserved main hand, durability, native damage events and cancellation, network right-click sword attack (7.0), held-right-click mining with correct-tool filtering and hit sounds, one final break, block-damage events, mining wear, and native break cancellation. Additional checks confirmed public BeastLib startup, AIR-tool rejection, and safe disabling of the optional placeholder on 1.9.4 even when its configuration was enabled. The optional invisible placeholder requires **1.14+**; its availability is separate from legacy core gameplay.

The fixture used a fresh disposable world, a loopback-only listener, disabled update feeds/custom metrics, the global bStats opt-out, the older Spigot PluginMetrics opt-out and disabled Minecraft snooping. The server was stopped and its port was confirmed closed afterward. Reproducible source and instructions are in [tools/legacy-probe](../tools/legacy-probe/README.md). The final local evidence is under `target/release-runtime-1.9.4/final-artifact-run.log`; test tooling is not included in the plugin JAR.

`LegacyApiLinkageTest` contains an optional real Spigot 1.9.4 test covering attacks, mining, tool/attribute methods, sound fields, geometry, packets and plugin class signatures. It uses a locally built `org.spigotmc:spigot:1.9.4-R0.1-SNAPSHOT` artifact, or the explicit `beastdoublewield.legacy.1_9_4.jar` system property. If the artifact is absent, that test reports a skip; a skip is not a passing compatibility result.

Obtain the server through [official BuildTools](https://www.spigotmc.org/wiki/buildtools/) with `--rev 1.9.4` in an isolated directory using Java 8. No Minecraft/Spigot server binaries or decompiled sources are distributed in this repository. Run the project's tests after building the local artifact, for example:

```powershell
mvn test "-Dbeastdoublewield.legacy.1_9_4.jar=C:/test-servers/spigot-1.9.4.jar"
```

Linkage/mapping checks establish that expected members exist; they do not prove native gameplay outcomes. Live runs must additionally exercise input, damage cancellation, mining, sound playback, durability and inventory restoration with the exact release artifact. The optional invisible-item resource-pack feature also needs client-specific visual acceptance; it is not established by a server linkage check.
