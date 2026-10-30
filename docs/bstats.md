# bStats integration

BeastDoubleWield uses bStats plugin ID **35021**. The owner supplied this ID in the integration screenshot. The public [bStats plugin registration](https://bstats.org/api/v1/plugins/35021) was also checked: plugin `BeastDoubleWield`, owner `mraxetv`, software ID `1` (Bukkit).

The owner-supplied standalone `Metrics` class identifies itself as version **3.2.1**. It is included at `src/main/java/me/mraxetv/beastdoublewield/lib/bstats/Metrics.java`. Only its package declaration was changed; the original header, formatting, opt-out, payload, endpoint and timing remain intact. No extra Maven bStats dependency is necessary because this supplied file contains the client and its nested supporting classes. Do not add a second copy or modify the vendored client.

`BStatsTelemetry` initializes that client once with ID 35021 when the plugin enables, and calls its `shutdown()` when the plugin disables. A gameplay/configuration reload does not recreate the client or reset its schedule. Initialization failure is logged without disabling gameplay. `/bdw status` reports client initialization separately from custom telemetry; it does not claim successful submission or dashboard publication.

## Server configuration

The standard shared file is `plugins/bStats/config.yml`. Existing opt-out settings and server UUIDs are preserved. If bStats has never been initialized, the supplied class creates this file with metrics enabled and a generated server UUID.

To opt out, set `enabled: false` in that file and restart the server. To enable it, use `enabled: true` and restart. The plugin build does not start/reload a server or modify a deployed bStats configuration.

The `metrics.enabled`, `metrics.endpoint` and `metrics.interval-minutes` keys in BeastDoubleWield's own config still control the older, optional **custom HTTPS telemetry** service. They do not configure bStats. Leave that custom block disabled when using only bStats; no custom endpoint is required for bStats.

## Data and timing

The supplied client sends its standard data: player count, online-mode flag, server version/name, Java/OS information and processor count, plugin version, bStats client version, plugin ID and persistent server UUID. No custom charts are registered by BeastDoubleWield. The standard client controls collection and transmission; the plugin's custom telemetry interval is not applied to it.

The first submission is delayed randomly by 3-6 minutes. The client's subsequent scheduling retains its official randomization and 30-minute interval. Dashboard publication can take additional time; see the [official troubleshooting guide](https://bstats.org/docs/troubleshooting). Test/build success is not evidence that the public dashboard has received a submission.

## Vendor integrity

The package-adjusted source file SHA-256 recorded on import is `9123C438836761D574B8B45BE53D52AC04B7913E1470354B076D76BC06866DCF`. Keep this vendor file out of formatting and obfuscation passes. Future official updates require a deliberate replacement and a new provenance record.
