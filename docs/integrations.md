# Offhand integration contracts

BeastDoubleWield runs native server combat and block breaks while the actual offhand item is presented synchronously as the acting hand. The scope preserves normal damage, drop and protection events and then restores the mutated item to its physical offhand. No hand swap spans a tick. A consumer must capture the source during that scope if it schedules delayed effects.

## BeastEnchants

The companion BeastEnchants build now captures the physical combat source from the enabled BeastDoubleWield scope. Delayed lifesteal and frost revalidate the real offhand item, selected hotbar slot, current DoubleWield provider, player life/configuration, final damage/cancellation and the existing exact metadata and bounded native-wear rule.

Fresh BeastEnchants configurations allow `HAND` and `OFF_HAND` for lifesteal and frost. Existing configurations retain their restrictions. Add `OFF_HAND` to each desired combat definition's `ActiveSlots`, then reload the BeastEnchants configuration. No existing server configuration was automatically modified. Passive equipment positions are unaffected.

An old BeastEnchants JAR still has its previous main-hand-only delayed check; replace it with the companion build to receive this integration. `/bdw integrations` reports whether the new source helper is present, not whether a particular definition enables the offhand.

## mcMMO and protection plugins

Mining emits a standard cancellable `BlockDamageEvent` once when a session starts, while the offhand tool is the acting hand. It honors cancellation and instant-break requests, then validates the target/tool again. The final native break still emits its normal break/drop events and handles XP and tool wear. Combat uses ordinary native damage events. BeastDoubleWield never grants mcMMO experience itself or duplicates final break events.

mcMMO's own right-click ability-preparation rules remain in force, including its restrictions when the offhand is occupied. Prepare abilities using mcMMO's ordinary controls, then use the offhand tool. BeastDoubleWield does not replay every plugin's interaction handlers or invoke undocumented skill internals. Compatibility testing should cover the exact mcMMO and server versions in use.

Primary integration contracts: [mcMMO block-event listener](https://github.com/mcMMO-Dev/mcMMO/blob/master/src/main/java/com/gmail/nossr50/listeners/BlockListener.java), [mcMMO player interactions](https://github.com/mcMMO-Dev/mcMMO/blob/master/src/main/java/com/gmail/nossr50/listeners/PlayerListener.java).

## Geyser and Floodgate

Bedrock-origin interactions use the same server-side action path. When locally installed, Floodgate's `isFloodgatePlayer(UUID)` or Geyser's `isBedrockPlayer(UUID)` identifies the client. Detection never guesses from username prefixes or UUID layout; failed/unavailable APIs do not manufacture a positive identification. A proxy deployment must forward the API data as documented by Floodgate.

Geyser remains responsible for Bedrock item placement and input translation. An offhand swing request cannot guarantee a Bedrock animation that the translator/client does not implement. No client pack is automatically installed or enabled.

Primary API contracts: [Floodgate API](https://geysermc.org/wiki/floodgate/api/), [Geyser API](https://geysermc.org/wiki/geyser/api/).

## Evidence boundary

Automated tests cover provider identity/lifecycle fences, physical hand selection, detached item ownership and mining-start event behavior. They do not certify all versions of mcMMO, Geyser or arbitrary custom-enchantment/protection plugins. Record those versions and exercise their effects on the selected server before publishing a compatibility claim.

The 2026-10-30 live fixtures verified delayed BeastEnchants offhand lifesteal/frost on Spigot 1.20.2 and mcMMO 2.1.202 AXES/SWORDS/MINING XP on Paper 1.16.5. See [verification.md](verification.md) for exact builds and scenarios. Geyser detection is unit-tested; no live Bedrock-client session was performed.
