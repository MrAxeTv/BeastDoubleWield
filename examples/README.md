# Resource-pack examples

The [first-person arm pack](firstperson-arm/README.md) uses each player's own skin to draw an otherwise empty offhand. One pack covers classic/wide and slim skins, both physical hands and sleeves. Ordinary offhand weapons and tools need no resource pack.

## Player-skin arm compatibility

| Example | Plugin and server | Client / resource format | Status |
| --- | --- | --- | --- |
| [First-person skin arm](firstperson-arm/README.md) ([source](firstperson-arm/26.3/)) | BeastDoubleWield `1.1-SNAPSHOT`, BeastLib and Paper **26.3** | Native Java **26.3** / **97.1** | Experimental; native API and isolated GPU fixtures, with live gameplay acceptance still outstanding |

This is the only BeastDoubleWield skin-arm target currently supplied and verified. Download the [ready-to-use 26.3 ZIP](https://github.com/MrAxeTv/BeastDoubleWield/releases/download/v1.1-preview.1/BeastDoubleWield-firstperson-arm-26.3.zip) from [preview release v1.1-preview.1](https://github.com/MrAxeTv/BeastDoubleWield/releases/tag/v1.1-preview.1), then follow the [installation and configuration steps](firstperson-arm/README.md#install). The plugin and pack are separate downloads; clients must enable the pack manually.

Minecraft introduced the `minecraft:player_head` special item model and its profile-texture loading in [Java 1.21.6](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6). That foundation predates this arm implementation. It does not make our shader, model transforms or server component access compatible with every 1.21.6+ version. The supplied port targets [26.3's resource format 97.1](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3); other versions need their own port and checks.

## Historical input-only examples

| Examples | Client profiles | Status |
| --- | --- | --- |
| [Invisible feather packs](resourcepack/README.md) | 1.14, 1.20.2, 1.21.4, 1.21.8, 26.3 | Historical plugin 1.0 input placeholder; these hide an item and **do not render a skin arm** |

The current plugin no longer inserts the old main-hand input feather. Those archived profiles demonstrate item-model selectors, not additional skin-arm compatibility.

## Building and combining packs

Build the skin-arm ZIP from the repository root without copying it into a local Minecraft installation:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/build-firstperson-arm.ps1 -SkipClientExport
```

The arm pack overrides the entity shaders and player-head item definition. It conflicts with the current BeastRPGInventory 26.2 and BeastMounts/ObjCubed 26.3 shader packs; pack priority alone cannot merge their renderers. Other shader replacements and rendering mods remain unverified. See [version and pack compatibility](firstperson-arm/README.md#version-and-pack-compatibility) before combining assets. Existing fixtures do not certify a merged pack, complete world transparency rendering or performance.
