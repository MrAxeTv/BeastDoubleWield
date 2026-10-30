# Spigot 1.9.4 acceptance fixture

This test plugin performs the 17 legacy checks recorded in [legacy-compatibility.md](../../docs/legacy-compatibility.md). It is excluded from the production JAR. It changes blocks and spawns test mobs, so install it only in a disposable test world.

Use an official BuildTools Spigot 1.9.4 server, Java 8, public BeastLib **1.7.0**, and the exact BeastDoubleWield release JAR being tested. Compile `RuntimeProbe.java` with Java 8 bytecode against those three JARs, then package the resulting classes with this directory's `plugin.yml` as `BeastDoubleWieldProbe.jar` in that isolated server's `plugins` directory. No optional integration plugin is needed.

Required fixture settings:

- Bind `server-ip=127.0.0.1`, `server-port=25594`, `online-mode=false`, with an isolated `level-name`, `allow-flight=true`, `spawn-protection=0`, and `snooper-enabled=false`.
- Disable BeastDoubleWield update feeds and custom metrics. Set `plugins/bStats/config.yml` to `enabled: false`, and the old Spigot `plugins/PluginMetrics/config.yml` to `opt-out: true` before starting.
- Enable `placeholder.enabled: true` deliberately: the probe verifies it is safely unavailable on 1.9.4 and no carrier is inserted. Set both `attack.correct-tool-only` and `mine.correct-tool-only` to `true`; retain the ordinary gameplay defaults including mining sounds.
- Use the server's normal EULA acceptance process. This fixture must never be started by Maven or installed in a production server.

The client reuses the locked dependencies and loopback-only script from `tools/runtime-probe`. After the disposable server finishes starting, run from that directory:

```powershell
npm ci --ignore-scripts
node client.js 25594 1.9.4
```

The probe recognizes only the local client named `BDWProbe`. It reports each `BDW_CHECK` and a final `BDW_PROBE_RESULT passed=17 failed=0`. Checks cover public BeastLib startup, legacy placeholder refusal, hit sounds, AIR-tool rejection, native and network combat/mining, durability, events and cancellation. The probe does not perform a visual resource-pack test or establish compatibility with every 1.9 patch.

Save the server log, exact server/Java/dependency versions and tested JAR hashes. Stop the server through its console after the result, and verify port 25594 has closed. Existing worlds and unrelated server processes must remain untouched.
