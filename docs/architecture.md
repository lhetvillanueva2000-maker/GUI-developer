# Architecture

## Why it is split this way

The app has to be two things at once: a precise, mouse-driven desktop tool and
a comfortable touch app. Those two front-ends want genuinely different
interaction models, so the split is drawn at the point where "what the editor
does" ends and "how you tell it to" begins.

```
┌──────────────────────────┐   ┌──────────────────────────┐
│  :desktopApp             │   │  :androidApp             │
│  menus, docks, shortcuts │   │  sheets, nav bar, touch  │
│  mouse gestures          │   │  pinch / drag gestures   │
└─────────────┬────────────┘   └────────────┬─────────────┘
              │                             │
              └──────────────┬──────────────┘
                             ▼
                   ┌───────────────────┐
                   │  :styles          │  Compose rendering
                   │  java/ + bedrock/ │  one skin per edition
                   └─────────┬─────────┘
                             ▼
        ┌────────────────────────────────────────┐
        │  :sharedCore                           │
        │  model · catalog · editor · validation │
        │  no UI toolkit dependency at all       │
        └────────────────┬───────────────────────┘
                         ▼
                ┌──────────────────┐
                │  :exporters      │
                │  Java · Bedrock  │
                │  code generation │
                └──────────────────┘
```

## Modules

| Module | Depends on | Contains |
| --- | --- | --- |
| `:sharedCore` | – | `GuiProject` and the element tree, the element catalog, `EditorController`, editor settings, undo/redo, snapping, validation, serialization, templates, and the image pipeline (GIF decode, DEFLATE, PNG write). **No Compose, and no platform APIs at all.** |
| `:styles` | `:sharedCore`, Compose | The `EditionSkin` contract, pixel-art drawing primitives, the shared canvas widget, and the two edition skins in `styles/java` and `styles/bedrock`. |
| `:exporters` | `:sharedCore` | Java resource-pack export, Bedrock JSON-UI export, the native `.mcmeta`/atlas definitions, and the code generators (HTML/CSS, SVG, Compose, Java `Screen`, JSON). |
| `:desktopApp` | all of the above | Compose Desktop shell: menu bar, docks, mouse input, AWT file dialogs. |
| `:androidApp` | all of the above | Compose Android shell: bottom navigation, modal sheets, touch input, Storage Access Framework. |

`:sharedCore` deliberately has no UI dependency. That is what lets the export
pipeline, the validator and every unit test run headless, and it is why
`./gradlew validateProjects` can check the bundled templates in CI without
starting a window.

## The document model

A project is an immutable value:

```kotlin
GuiProject(
    edition = Edition.JAVA,
    canvas  = CanvasSpec(width = 176, height = 166, guiScale = 3, gridSize = 8),
    elements = listOf(/* ordered tree; index 0 paints first */),
    textures = listOf(/* imported images, base64, travel with the project */),
    meta = ProjectMeta(namespace = "mcgui", screenId = "custom_chest"),
)
```

Elements store their configuration in a `Map<String, PropValue>` rather than in
a per-type data class. That one decision buys a lot:

* the property inspector is fully data-driven - adding a property to a widget
  never touches UI code;
* the validator can reason about "this property only exists on Bedrock"
  generically;
* new widget types are a catalog entry plus a skin, not a new serializer.

`ElementCatalog` is the single source of truth for what a widget is: its
default and minimum sizes, which editions it exists in, whether it accepts
children, and its full property schema.

## Editor state and undo

`EditorController` owns a `StateFlow<EditorState>`. Both front-ends collect it
and call the same methods; the only thing they own is which gesture maps to
which call.

Undo is **snapshot-based**, not inverse-command. Because the document is a
persistent immutable tree, a snapshot shares almost all of its structure with
its neighbours - only the spine of the changed subtree is re-allocated. That
makes whole-document snapshots both cheaper and far less error-prone than
getting the inverse of every mutation exactly right.

Continuous gestures pass a *coalesce key*, so one drag collapses into one
Ctrl+Z instead of forty.

## Rendering

The canvas is a single Compose `Canvas`. The whole element tree, the grid,
guides, selection chrome and marquee are painted in one pass rather than as a
composable per element - a vanilla chest screen alone is ~90 slots, and they
all move together during a drag.

Everything snaps to whole device pixels before painting. Minecraft UI is
nearest-neighbour pixel art, and a half-pixel rectangle edge shows up as a
blurred seam the moment the canvas is zoomed in, which in a GUI designer is
most of the time.

## Edition separation

`styles/java` and `styles/bedrock` share nothing but the `EditionSkin`
interface and the primitives in `PixelDraw.kt`. The two files deliberately
duplicate structure so either edition can be redesigned without regressing the
other. The palette also drives the *editor chrome*, so switching edition
visibly changes the whole application rather than just the canvas.

## Platform-specific code

Only two things are `expect`/`actual`:

| Declaration | Desktop | Android |
| --- | --- | --- |
| `decodeImageBitmap` | Skia `Image.makeFromEncoded` | `BitmapFactory` with `inScaled = false` |
| `readImageSize` | Skia header read | `inJustDecodeBounds` |

Everything else that differs between platforms - file dialogs, gestures,
navigation - lives in the platform module, not behind an abstraction, because
those genuinely should not be the same.

The image pipeline in `core/image` is a deliberate counter-example. Decoding a
GIF and writing a PNG both have perfectly good platform APIs, and using them
would have meant two implementations, two sets of edge cases and no way to test
either from shared code. Writing the GIF decoder, a DEFLATE compressor and a
PNG writer in common Kotlin instead - about 700 lines - means importing a GIF
does *provably* the same thing on a phone and on a desktop, and the tests that
prove it run against `java.util.zip` and `javax.imageio` rather than against
themselves.
