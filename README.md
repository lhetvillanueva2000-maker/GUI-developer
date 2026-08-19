<div align="center">

<img src="assets/icon/icon-256.png" width="128" alt="Minecraft GUI Designer">

# Minecraft GUI Designer

**A standalone visual designer for Minecraft screens — Java Edition and Bedrock
Edition, each with its own widget set, visual identity and export pipeline.**

Windows desktop app · Android app · one shared editor core

</div>

---

## What it does

Build, edit, preview and export Minecraft GUIs without opening the game.

- **Two real editors, not one with a toggle.** Java and Bedrock have different
  widget vocabularies, different layout conventions and completely different
  export formats. Each gets its own skin folder, its own palette, and its own
  drawing code.
- **Desktop and Android are different apps on the same core.** The desktop shell
  is a multi-dock, keyboard-driven design tool. The Android app is bottom sheets,
  a navigation rail and touch gestures built for a thumb. They share the document
  model, the editor logic and the exporters — nothing else.
- **Import your own textures.** Any PNG/JPG/WebP/GIF becomes a button skin, panel
  background, icon or item preview, with nine-slice insets so custom art
  stretches properly. Images live inside the project file, so a `.mcgui` is a
  single self-contained document.
- **Turn a design into code.** A dedicated Code tab renders the current screen as
  a standalone HTML + CSS page (with real `:hover` / `:active` states and
  embedded textures), a plain stylesheet, a Compose `@Composable`, a Minecraft
  Java `Screen` subclass, Bedrock JSON UI, or the raw project JSON.
- **Continuous validation.** Broken sizes, missing textures, out-of-canvas
  elements, unsupported edition-specific properties and undersized touch targets
  are flagged as you work, with a one-click jump to the offending element.

---

## Screens at a glance

| | Desktop | Android |
| --- | --- | --- |
| Navigation | Menu bar + three docks | Bottom nav / rail + modal sheets |
| Placement | Arm from palette, click to drop | Tap a component tile, it lands centred |
| Move | Drag any element | Drag an **already selected** element |
| Pan | Middle/right drag, wheel | One-finger drag on empty space |
| Zoom | Ctrl+wheel | Pinch |
| Multi-select | Shift+click, marquee | Long press |
| Properties | Always-visible inspector dock | Bottom sheet with chip pickers |
| Export | Folder or `.zip` | `.zip` via the Storage Access Framework |

---

## Component library

Available from the first launch, in both editions unless noted.

**Containers** — Chest background panel · Panel frame with shadows · Scroll
container
**Inventory** — Inventory slot · Hotbar strip
**Controls** — Button · Toggle button · Tab button · Icon button · Checkbox ·
Dropdown · Slider · Java-style rectangular button *(Java only)*
**Text & input** — Label · Text box · Search field
**Feedback** — Progress bar · Tooltip box
**Decoration** — Header bar · Decorative separator · Image / texture slot
**Touch controls** — Touchpad button *(Bedrock only)* · Mobile action button
*(Bedrock only)*

Every interactive component supports **normal / hover / pressed / focused /
disabled** states, stored as overrides so only what you actually changed is
saved. Bedrock drops hover and focus — touch has no pointer.

---

## Templates

Seven ready-to-use layouts ship with the app and are regenerated from code on
every build, so they can never drift out of sync with the component catalog.

| Template | Edition | Canvas |
| --- | --- | --- |
| Java Chest Container | Java | 176 × 166 |
| Java Options Menu | Java | 256 × 200 |
| Java Machine UI | Java | 176 × 182 |
| Bedrock Touch HUD | Bedrock | 320 × 180 |
| Bedrock Settings Sheet | Bedrock | 320 × 180 |
| Bedrock Pocket Container | Bedrock | 320 × 180 |
| Bedrock Action Form | Bedrock | 320 × 180 |

The `.mcgui` sources are in [`templates/`](templates), and a full worked example
of every export format is in
[`templates/sample-output/`](templates/sample-output).

---

## Getting started

### Requirements

- **JDK 17** — the Gradle build will download one automatically if you have a
  different version installed.
- **Android SDK** (API 35) — only needed for the Android app. Without it the
  build automatically drops `:androidApp` and every `androidTarget()` so the
  desktop side still builds; pass `-PskipAndroid=true` to force that.

### Run it

```bash
git clone https://github.com/lhetvillanueva2000-maker/gui-developer.git
cd gui-developer

./gradlew :desktopApp:run          # desktop editor
./gradlew :androidApp:installDebug # Android app on a connected device
```

### Build the artifacts

```bash
# Windows .exe + .msi  (must be run on Windows)
./gradlew :desktopApp:packageReleaseExe :desktopApp:packageReleaseMsi

# Native package for whatever OS you are on (.deb / .dmg / .exe)
./gradlew :desktopApp:packageDistributionForCurrentOS

# Portable jar that runs anywhere with a JVM
./gradlew :desktopApp:packageUberJarForCurrentOS

# Android APK
./gradlew :androidApp:assembleRelease
```

### Package a full release ZIP

```bash
./build-scripts/package-release.sh 1.0.0        # Linux / macOS
.\build-scripts\package-release.ps1 -Version 1.0.0   # Windows
```

Produces `dist/minecraft-gui-designer-<version>.zip` containing the desktop
installer, the APK, the templates, the docs and a source archive, plus a
`MANIFEST.txt` listing every file.

Because a Windows `.exe` can only be produced on Windows,
[`.github/workflows/release.yml`](.github/workflows/release.yml) builds the two
halves on their own runners and merges them with
[`build-scripts/bundle-release.sh`](build-scripts/bundle-release.sh) into a
single ZIP, attached to the GitHub Release. Push a `v*` tag to trigger it:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Binaries are not committed to the repository — they are release artifacts.

---

## Other tasks

```bash
./gradlew allTests           # unit tests for the core and the exporters
./gradlew validateProjects   # regenerate + validate every bundled template
./gradlew generateIcons      # re-render the app icon for every platform
./gradlew assembleAll        # everything this host can produce
```

---

## Repository layout

```
sharedCore/     Document model, element catalog, editor state, undo/redo,
                snapping, validation, serialization, templates. No UI.
styles/         Compose rendering and theming
  java/           Java Edition palette + skin  (independent)
  bedrock/        Bedrock Edition palette + skin  (independent)
exporters/      Java resource pack, Bedrock JSON UI, code generators
desktopApp/     Compose Desktop shell: menus, docks, mouse, AWT dialogs
androidApp/     Compose Android shell: sheets, nav, touch, SAF
assets/icon/    App icon, generated from build-scripts/icon
templates/      Bundled .mcgui templates + sample export output (generated)
docs/           Architecture, project format, exporting, editor guide
build-scripts/  Packaging scripts and the icon renderer
.github/        CI and release workflows
```

---

## Documentation

| Document | What it covers |
| --- | --- |
| [Architecture](docs/architecture.md) | Module split, document model, undo strategy, rendering |
| [Project format](docs/project-format.md) | The `.mcgui` JSON format, field by field |
| [Exporting](docs/exporting.md) | Java pack, Bedrock pack, code generation, parity warnings |
| [Editor guide](docs/editor-guide.md) | Desktop and Android interaction models, shortcuts, validation |
| [Extending](docs/extending.md) | Adding components, templates, themes and code targets |

---

## Tech

Kotlin Multiplatform · Compose Multiplatform · Gradle · kotlinx.serialization

---

## Licence

MIT — see [LICENSE](LICENSE).

Not affiliated with Mojang or Microsoft. Minecraft is a trademark of Mojang AB.
