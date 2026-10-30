# BeastDoubleWield

Follow [parent rules](../AGENTS.md).

- Project identity: BeastDoubleWield, package/Maven group `me.mraxetv.beastdoublewield`.
- Original implementation based on documented offhand interaction requirements. Do not copy or translate AvarionMC/Ranull implementation, assets, or branding.
- Compile Java 8 bytecode against Spigot 1.20.2 and provided BeastLib 1.7.3. Do not infer support for legacy servers or Folia from bytecode or scheduler availability.
- Normal package/install exports only this plugin's JAR to the seven parent destinations. Properties: `beastdoublewield.export.root` and `beastdoublewield.export.skip`.
- Verify exported artifact hashes. Never start/reload servers or change client packs during builds.
- Other Beast projects remain read-only unless explicitly included in a future request.
- The owner-supplied bStats 3.2.1 class at `src/main/java/me/mraxetv/beastdoublewield/lib/bstats/Metrics.java` is vendored unchanged except its package. Preserve its formatting, opt-out, submission schedule and code; do not run formatters or obfuscators on it. Use the registered BeastDoubleWield service ID `35021` in the external lifecycle wrapper, not inside the vendor class.
