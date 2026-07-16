# Jeef's Wynncraft

A lightweight, client-only Wynncraft modpack for Fabric 1.21.11. Packwiz owns dependency resolution and updates; the included JeefWynn companion mod provides a Wynncraft-specific first-join setup screen, Discord linking, and an optional spell-readiness HUD.

## Default installation

The base intentionally contains only:

- Wynntils
- Sodium, Lithium, ImmediatelyFast, Entity Culling, and Dynamic FPS
- Controlling and Quick Screenshot
- Fabric API, Mod Menu, YOSBR, and required libraries
- JeefWynn Companion (custom features disabled until selected)

New instances start with GUI scale 3 and vanilla render and simulation distances of 8. YOSBR applies these defaults only when Minecraft has no existing `options.txt`, so later player changes and existing instances are respected. Keeping vanilla render distance at 8 avoids Distant Horizons' high-render-distance warning while its LoD distance handles the far view.

Every third-party project currently comes from Modrinth. When adding or updating content, use `packwiz modrinth add` first. Use CurseForge only when no compatible Modrinth release exists, and record the exception in the pull request or commit.

## Optional installer choices

Packwiz Installer presents these before Minecraft starts:

- **Voices of Wynn** — quest voice acting; off by default.
- **Wynncraft map LoDs** — off by default. Select all three marked entries: Distant Horizons, WynnLODGrabber, and WynnVista.

Binary selection must happen before launch because Fabric cannot load or unload mod JARs at runtime. The in-game setup screen detects what was installed and handles the choices that can safely change live (spell HUD and Discord linking). Reopen it with `/jeefwynn setup`.

## Player commands

- `/spellhud` — toggle the four-spell readiness HUD.
- `/d link` — show a short-lived code to redeem with Discord `/link`.
- `/d` — toggle Discord chat mode. While enabled, ordinary chat text goes only to Discord, not Wynncraft.
- `/d send <message>` — send one message without changing chat mode.
- `/d status` — show connection and mode status.
- `/d unlink` — remove the local signed link token.
- `/d server <https-url>` — set the bridge URL (normally preconfigured by the pack owner).

The spell HUD reads current mana from Wynntils. It starts with Wynncraft's class base costs, then learns the character's actual cost for each spell from successful casts, including item/build reductions.

## Development

Build the companion mod and refresh the pack:

```powershell
cd companion-mod
.\gradlew.bat build
cd ..
Copy-Item companion-mod\build\libs\jeefwynn-companion-0.1.0.jar mods\jeefwynn-companion.jar -Force
.\packwiz.exe refresh --build
```

Build and test the Discord bridge:

```powershell
cd bridge-server
npm install
npm run typecheck
npm test
npm run build
```

Export Modrinth first:

```powershell
New-Item -ItemType Directory -Force dist
.\packwiz.exe modrinth export -o dist\jeefwynn-0.1.0.mrpack
```

For Packwiz Installer distribution, host this repository's pack files over HTTPS and point `packwiz-installer-bootstrap.jar` at the hosted `pack.toml`. The installer GUI remembers optional choices and updates the pack on later launches.

Build a Prism Launcher/MultiMC import ZIP after hosting the pack:

```powershell
.\scripts\build-packwiz-installer-zip.ps1 `
  -PackUrl 'https://your-pack-host.example/pack.toml'
```

For local testing, run `.\packwiz.exe serve` and build with `-PackUrl 'http://localhost:8080/pack.toml'`. The resulting ZIP only works while that local server is running; use an HTTPS URL for distribution.

## Discord bridge

See [bridge-server/README.md](bridge-server/README.md). Do not put Discord bot credentials or the signing secret in the modpack. Before release, either change `CompanionConfig.bridgeUrl` and rebuild the companion or distribute a `config/jeefwynn.json` containing the public HTTPS URL.

This is a fan-made project and is not affiliated with Wynncraft, Mojang, Microsoft, Discord, or Modrinth.
