# Native 26.3 server API probe

This destructive test plugin belongs only in a disposable offline loopback fixture. It creates test blocks and a mob, exercises a synthetic native ServerPlayer through a Bukkit proxy, and shuts the fixture down. Never copy it into a normal server's plugins directory.

Compile against the exact 26.3 Paper server and its declared libraries, the built BeastDoubleWield JAR, and BeastLib. The probe uses NMS only in this test tool; it is excluded from the production plugin. Its local fixture is `target/arm-runtime-26.3`, bound to `127.0.0.1:25592`, with telemetry and update feeds disabled. EULA acceptance was copied from the owner's already accepted disposable fixture.

The checked development artifact and final event counts are retained in [verification-26.3.json](verification-26.3.json). The named logs are local fixture outputs under `target/arm-runtime-26.3`, not bundled server files.

Enable the three placeholder/air/fist settings and `placeholder.first-person-arm` in the fixture. The synthetic profile has a complete test texture property; no profile download or network client is used. Look for `ARM_CHECK`, `ARM_PROBE_ERROR`, and `ARM_RESULT` in the server log. This validates actual server metadata, inventory transactions and native combat, not network packet reception or client rendering. The graphical fixture is separate under `tools/firstperson-probe`.

The cancellation check attacks a fresh target with zero damage-invulnerability ticks. It requires exactly one native damage event with an empty acting hand, one cancelled event observed at MONITOR, and unchanged target health. `ARM_RESULT` includes native, empty-hand and cancelled-event counters, so unchanged health alone cannot pass this check.
