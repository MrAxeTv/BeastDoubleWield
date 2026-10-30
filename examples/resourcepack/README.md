# Optional invisible placeholder pack

These examples hide the BeastDoubleWield placeholder item: a **feather with CustomModelData `35021`**. Ordinary feathers keep their normal model. Install the pack matching the **client** version before enabling the optional placeholder feature on the server. The feature requires Minecraft/Bukkit 1.14 or newer; the examples do not extend the plugin's server compatibility claim.

The item has an empty model, so it needs no texture or downloaded artwork. It remains a real item with a model selector and the plugin's private ownership marker. A blank visible name and no lore do not mean the item has no metadata. The pack only reads its model selector; it does not read the ownership marker or create items.

Holding a real item allows the client to send item-use input when the main hand would otherwise be empty. Hiding its model does **not** make Minecraft render a bare fist or a second skin arm. See [empty-hand input and rendering limits](../../docs/empty-hand-input.md). Without the pack, the placeholder appears as a feather. The plugin does not install or enable this pack for players.

## Build and choose a profile

From the repository root, using Windows PowerShell 5.1:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/build-resourcepacks.ps1
```

The execution-policy option applies only to that command. With PowerShell 7 on Windows, Linux or macOS, use `pwsh -NoProfile -File tools/build-resourcepacks.ps1`. No Python, Maven, image tool, or external library is required.

| Client profile | Pack metadata | Item format |
| --- | --- | --- |
| `1.14` | `pack_format: 4` | Model overrides / integer CustomModelData |
| `1.20.2` | `pack_format: 18` | Model overrides / integer CustomModelData |
| `1.21.4` | `pack_format: 46` | Item definition / custom-model-data float at index 0 |
| `1.21.8` | `pack_format: 64` | Item definition / custom-model-data float at index 0 |
| `26.3` | `min_format: [97, 1]`, `max_format: [97, 1]` | Item definition / custom-model-data float at index 0 |

The generated files are `target/resourcepacks/BeastDoubleWield-placeholder-<profile>.zip`, matching `.sha1` and `.sha256` files, and `checksums.json`. To build one profile:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/build-resourcepacks.ps1 -Profile 1.20.2
```

Each ZIP targets the listed client version. There is no single pack advertised for every Minecraft release. For another client version, add a profile with its verified pack format and the correct item format in [profiles.json](profiles.json). The `cmd` assets are for the pre-1.21.4 model-override system; `item-definitions` uses the system introduced in 1.21.4. A proxy/protocol bridge does not automatically make resource-pack formats interchangeable.

These examples have no 1.9-1.13 profile. Those clients predate CustomModelData. Making every feather invisible would affect normal gameplay items, while a durability-based tool carrier would have different gameplay behavior.

## Use or merge the pack

Players can manually place the matching ZIP in their client `resourcepacks` directory and enable it in Minecraft. Server owners can also merge these assets into their existing pack and distribute that pack through their normal pack system. Keep the ZIP's `pack.mcmeta` and `assets` directory at its root.

For old-model profiles, merge the two entries from `cmd/assets/minecraft/models/item/feather.json` into your existing feather overrides and include the two `beastdoublewield` models. For item-definition profiles, merge the selector from `item-definitions/assets/minecraft/items/feather.json` into your existing feather definition. If another pack already changes feathers, simply putting this pack above it replaces that feather configuration; it does not merge JSON automatically.

Reserve `35021` for the placeholder. Both selectors switch back to the normal feather at `35022`, so a larger CustomModelData integer is not accidentally hidden. In the modern float-based format, values in `[35021, 35022)` select the empty model; the plugin uses exactly `35021`. The ID is fixed in the plugin; changing it in a source fork also requires changing both selector thresholds in the chosen assets. Keep any existing custom feather models when merging.

Use the generated SHA-1 when a pack distribution API requests it. If hosting a pack, use a URL containing its content hash and retain the URL while its bytes remain unchanged. The builder writes stable entry order, timestamps, stored ZIP entries and normalized UTF-8/LF files; an unchanged pack is not rewritten. It never copies files to a Minecraft installation or changes enabled-pack lists.

## Verification and source references

Run the structural, selector-boundary and repeated-build checks with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/test-resourcepacks.ps1
```

These checks validate the archive, the normal-feather fallbacks, and reproducibility. They do not constitute an in-game visual test. Before deploying a merged pack, check the placeholder, an ordinary feather, and your other custom feather models in the actual target client; also test both main-hand and offhand interactions.

Pack-format values `4`, `64`, and `97.1` were checked in the official 1.14, 1.21.8, and 26.3 clients' `version.json`. The 1.14 download was obtained through Mojang's [version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json). Vanilla feather texture paths and head display transforms were checked against those client assets. No vanilla texture files are bundled.

Mojang's [1.14 release notes](https://www.minecraft.net/en-us/article/village---pillage-out-java-) document CustomModelData. The [1.20.2 notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-2) specify format 18. The [1.21.4 notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4) document format 46, item definitions, range dispatch, and the empty item model. The [1.21.9 notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9) explain the later major/minor `min_format` and `max_format` fields used by the 26.3 profile.
