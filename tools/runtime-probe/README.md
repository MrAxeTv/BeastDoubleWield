# Isolated gameplay fixture

These files are test tooling, excluded from the production JAR. Maven builds never start servers.

`RuntimeProbe.java` is a small Bukkit plugin requiring BeastDoubleWield. It creates a disposable platform and test mobs for the local `BDWProbe` player, exercises native and network-driven offhand actions, verifies cancellation and item wear, and reports `BDW_CHECK` and `BDW_PROBE_RESULT` records to the isolated server log. It must only be installed in a disposable world.

The optional BeastEnchants check creates genuine enchanted weapons through BeastLib's `ItemEnchantmentService`, then verifies delayed lifesteal and frost. The optional mcMMO check listens to its actual XP-gain events for AXES, SWORDS and MINING; the fixture sets the non-natural-mob XP multiplier to one because its test mobs are created by a plugin.

`client.js` connects only to `127.0.0.1`. It uses normal entity/block interaction packets in response to fixture instructions. Install its locked dependencies with `npm ci --ignore-scripts`; then run `node client.js <port> <minecraft-version>` after the isolated test server is ready. The client disconnects after completion and has a two-minute deadline.

Compile the Java fixture with Java 8 release output using the Spigot API, BeastLib developer dependency, and BeastDoubleWield classes on its classpath. Package its classes together with this directory's `plugin.yml`. Do not copy the fixture to normal export destinations.

The recorded local runs used separate directories beneath `target/runtime-probe`, loopback ports 25595–25597, copied server binaries and BeastLib libraries, disposable worlds, and the owner's existing Minecraft EULA acceptance. Existing server processes and worlds were not used for gameplay tests. Stop the isolated server with its console `stop` command after testing. Results and exact builds are in `docs/verification.md`.
