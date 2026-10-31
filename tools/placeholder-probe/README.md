# Isolated placeholder acceptance fixture

This fixture preserves **version 1.0** acceptance evidence. Its expectations of an automatically created main-hand feather do not apply to current 1.1 development, which removed that behavior. Run it only against the recorded 1.0 artifact; current arm evidence is under `tools/arm-runtime-probe` and current Java regressions under `src/test`.

This test plugin and Mineflayer client exercise the optional placeholder against a real Spigot 1.20.2 server. They are excluded from the production plugin JAR. **Use only a disposable world:** the fixture changes blocks and inventories, kills and reconnects its test player, disables BeastDoubleWield, and shuts the test server down when complete.

The local run uses `target/release-runtime-placeholder`, loopback address `127.0.0.1`, port `25593`, a copied Spigot 1.20.2 binary, public BeastLib 1.7.0, and the current BeastDoubleWield artifact. Copy existing Minecraft EULA acceptance only when the owner has already accepted it. Disable bStats, PluginMetrics, custom metrics and update requests in the fixture. Never copy this probe plugin into normal server export destinations.

Compile `PlaceholderProbe.java` with Java 8 release output and the Spigot API, BeastLib, and current BeastDoubleWield JAR on the classpath; package its classes with this directory's `plugin.yml`. Enable `placeholder.enabled`, `swing.air`, and `attack.allow-empty-hand` only in the fixture config. Keep two other storage slots available.

After starting the isolated server, run `node tools/placeholder-probe/client.js 25593`. It reuses the locked Mineflayer dependency from `tools/runtime-probe` (`npm ci --ignore-scripts` there if needed). An observer client records the actual offhand swing packet while the owner client sends ordinary inventory, swap, drop and air-use packets. Both clients connect only to loopback.

Look for `PH_CHECK`, `PH_RESULT` and `PH_ERROR` in the fixture log. Checks cover native PDC/model metadata, normal-item preservation, villager trading, empty-offhand air input, native attack restoration with cleanup inside its damage callback, a genuine cursor stack, swapping/dropping, personal toggle, config reload, death/respawn, reconnect and plugin-disable cleanup. The world keeps inventory during the deliberate death test. Packet observations do not prove resource-pack rendering or a visible bare fist.

The client has a three-minute timeout. If the run aborts before the fixture shuts down, stop that isolated server through its console; never leave it running after testing.
