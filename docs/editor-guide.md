# Using the editor

The desktop and Android apps drive the same editor core, but their interaction
models are deliberately different. This page covers both.

---

## Desktop

### Layout

| Region | Contents |
| --- | --- |
| Edition header | The **Java Edition / Bedrock Edition** tabs, the document title, and the component library, pack import, theme and appearance buttons |
| Toolbar | Tools, undo/redo, grid size and snap toggles, align and distribute, zoom, view mode |
| Left dock | **Palette** (live thumbnails), **Prefabs**, **Library** (textures), **Layers**, **Templates** |
| Centre | Design canvas with rulers, or the live preview, or the code view — plus the **move pad** whenever something is selected |
| Right dock | **Properties** (inspector), **Assets** (textures in this project), **Issues** (validation) |
| Bottom bar | **＋ Add anything**, one-click shape buttons, animated image, custom element, and **⋯** for the editor settings |
| Status bar | Status message, issue counts, canvas size, selection, selected element bounds |

### The edition tabs

The two tabs across the top are the most consequential control in the editor:
the edition decides which components exist, how they are drawn, which
properties are legal, and what an export produces. Whichever tab is lit,
everything below it belongs to that edition - the palette, the templates, the
skin and the export targets all follow.

Switching keeps the document rather than starting a new one, and re-runs
validation immediately, so anything the new edition cannot express is reported
in **Issues** instead of being silently dropped. Switching back restores it.

### Placing components

Click a component in the palette to *arm* it, then click the canvas to drop it
exactly where the pointer is. Double-click the palette entry instead to drop it
straight into the middle of the canvas.

Dropping onto a container (chest panel, panel frame, scroll container)
automatically parents the new element to it.

### Mouse

| Gesture | Action |
| --- | --- |
| Click | Select the topmost element under the pointer |
| Shift+click | Add to / remove from the selection |
| Drag an element | Move it (with snapping) |
| Drag a handle | Resize (single selection only) |
| Drag empty canvas | Rubber-band select |
| Middle or right drag | Pan |
| Wheel | Pan vertically; Shift+wheel pans horizontally |
| Ctrl+wheel | Zoom towards the pointer |

### Keyboard

| Keys | Action |
| --- | --- |
| `Ctrl+N` / `Ctrl+O` | New project / Open |
| `Ctrl+S` | Save |
| `Ctrl+Shift+S` / `Ctrl+E` | Export — every format, including the ones Minecraft reads |
| `Ctrl+Shift+E` | Export everything at once |
| `Ctrl+Shift+P` | Save the selection as a prefab |
| `F1` | Browse the component library |
| `Ctrl+Z` / `Ctrl+Y` | Undo / Redo |
| `Ctrl+C` / `Ctrl+V` / `Ctrl+X` | Copy / Paste / Cut elements |
| `Ctrl+D` | Duplicate |
| `Ctrl+A` | Select all |
| `Delete` | Delete selection |
| `Escape` | Deselect, or cancel a pending placement |
| `V` / `H` / `M` | Select / Pan / Marquee tool |
| Arrow keys | Move the selection by the step set in Editor Settings (Shift: the big step) |
| `Ctrl+]` / `Ctrl+[` | Bring forward / send backward |
| `Ctrl+Shift+]` / `Ctrl+Shift+[` | Bring to front / send to back |
| `Ctrl+G` / `Ctrl+R` | Toggle grid / rulers |
| `Ctrl++` / `Ctrl+-` / `Ctrl+0` | Zoom in / out / reset |

Copy and paste go through the real system clipboard as JSON, so you can paste
elements between two running copies of the app - or into a text editor.

### Prefabs

Select a group of elements - a header and its buttons, a row of slots, a whole
settings block - and save it from the **Prefabs** dock or `Ctrl+Shift+P`. It
comes back as one piece in any later project, with the textures it uses
travelling alongside it, so a prefab built in one document arrives fully skinned
in the next.

Prefabs are stored outside any project (`prefabs.json` in the app's data
directory), which is the entire point: they are yours, not the document's. A
prefab built for the other edition is still listed and still insertable, marked
so you know to check it afterwards.

### The texture library

Every image you import - by hand or out of a resource pack - joins a library
that outlives the project. The **Library** dock searches it, filters by which
pack an image came from, and copies an entry into the open document with one
click.

It is always a *copy*: a `.mcgui` stays a single self-contained file, so a
project can never break because the library moved on without it. Deleting a
library entry never touches projects that already use it.

### Importing a resource pack

**File → Import Resource Pack…** reads any Minecraft pack archive (`.zip`,
`.mcpack`, `.jar`) - Java or Bedrock, and a plain folder of images works too.
The importer lists what it found grouped by role, with the GUI art pre-selected
and the thousands of block textures left unticked. Only the entries you tick
are decoded, so opening a full vanilla pack costs a directory listing rather
than a hundred megabytes of bitmaps.

### The component library

`F1`, or the **Components** button in the header, opens the whole catalog at
once: a live preview of every component with its default size, whether it
resizes, whether it accepts children, how many states it supports and which
editions it exists in. Clicking one drops it in the middle of the canvas.

### Appearance

The theme button in the header cycles **system / dark / light**, and
**View → Appearance…** exposes the same choice plus the wallpaper.

The theme only ever changes the application *around* the canvas. The canvas
itself keeps drawing exactly what the game will draw - a vanilla button is the
same grey in a light-themed editor as in a dark one - because a design surface
that recoloured itself to match the app would be lying about the result.

---

## Android

Not a shrunken desktop: the navigation, gestures and controls are all built for
a thumb.

### Layout

* **Bottom navigation** (portrait) or a **navigation rail** (landscape and
  tablets, above 600dp wide): Design, Layers, Preview, Code, Add, Custom.
* **Modal bottom sheets** replace the desktop docks - one purpose at a time,
  dismissible with a swipe, always leaving the canvas visible behind them.
* A **floating selection bar** over the canvas holds the actions that live in
  menus on desktop: edit properties, duplicate, raise, lower, lock, delete.
* A **move pad** sits above it whenever something is selected - four arrows and
  the step they move by. A one-pixel drag is not something a fingertip can do,
  so on a phone this is the only way to place something exactly.
* **Edition tabs** sit directly under the title bar, exactly as on desktop.
* The overflow menu holds save, export, open, templates, the texture library,
  prefabs, the component library, resource-pack import, shapes and custom
  elements, arrange, canvas settings, editor settings, appearance, project
  settings and issues.

**Add** and **Custom** are separate on purpose: the first lists Minecraft's own
widgets, the second lists shapes, GIFs and anything the catalog does not have.
Mixing them would bury both.
* The **Arrange** sheet carries alignment, distribution, z-order, nudge and the
  grid controls that live in a toolbar row on desktop - lining elements up by
  dragging on a touchscreen is exactly the job alignment tools exist to remove.

### Touch

| Gesture | Action |
| --- | --- |
| Tap | Select |
| Long press | Add to / remove from the selection |
| Drag a **selected** element | Move it |
| Drag anything else, or empty space | Pan the canvas |
| Drag a handle | Resize |
| Pinch | Zoom, panning with the centroid at the same time |

Dragging only moves an element that is *already* selected. That one rule makes
accidental drags essentially impossible, which matters far more on a touchscreen
than on a desktop where a mis-drag is one Ctrl+Z away.

Resize handles are sized in dp, so they stay thumb-sized on every screen density.

### Files

Everything goes through the Storage Access Framework, so the app needs no
storage permissions: you pick exactly which document to read or create. Exports
are always written as a single `.zip`.

---

## Concepts that apply to both

### Editions

The edition decides the widget set, the canvas defaults, the visual style *and*
the export format, so it is stored in the project rather than being a view
toggle. Switching edition keeps every element (so the change is reversible) and
lets the validator flag whatever no longer fits.

Java mode is cool stone greys with an emerald accent; Bedrock mode is warmer
near-black with a vivid green. The whole application re-tints, not just the
canvas.

### Interaction states

Interactive widgets support Normal / Hover / Pressed / Focused / Disabled -
Bedrock drops Hover and Focused, since touch has no pointer.

Pick a state in the inspector and every edit is stored as an *override* for that
state only; the base appearance is untouched. Only properties that actually
differ are stored. The Preview tab renders every interactive widget in a chosen
state, which is the only way to check hover and pressed skins without launching
the game.

### Snapping

Snapping works in canvas-space GUI pixels, never screen pixels, so it behaves
identically at every zoom level. The tolerance is derived from the current zoom
so it feels constant: about seven screen pixels.

Three sources feed it, and each can be toggled independently:

* the grid;
* sibling element edges and centres, plus the canvas edges and centre lines;
* user-placed guides.

Alignment lines are drawn live while dragging so you can see what it locked to.

The grid pitch is set from the toolbar (`− 8px +`), from **View → Grid Size**,
or from the Canvas sheet on Android. The ladder is the one Minecraft's own art
is built on - 16 for a block texture, 8 for the Java container grid, 4 for
Bedrock's finer layout, 2 and 1 for detail work - and `0` turns the grid off
without touching the snap setting.

### Aligning and distributing

The align buttons work on whatever is selected. With **two or more** elements
they align to each other; with **one**, to its container (or the canvas). The
two distribute buttons need three or more and space them evenly between the
outermost two.

On Android these live in the **Arrange** sheet, reachable from the selection bar
or the overflow menu.

### Validation

The validator runs after every edit. It reports:

* duplicate ids, unknown element types;
* elements or properties that do not exist in the current edition;
* sizes below or above a widget's declared limits;
* elements outside the canvas, or inside a mobile safe-area margin;
* Bedrock touch targets under 24x24 (except fixed-size widgets, which you cannot
  grow anyway);
* Java `widgets.png` buttons that are not 20px tall;
* missing texture references and unused imported textures;
* overlapping inventory slots;
* an invalid export namespace.

Elements inside a scroll container are exempt from the canvas-bounds checks -
overflowing is the entire point of a scroll container.

Errors and warnings appear as coloured corner markers on the canvas, badges in
the layer tree, and rows in the Issues panel. Clicking a row selects the element
it is about.

### Importing textures

Import PNG, JPG, WebP or GIF through the Assets tab. Images are stored inside
the `.mcgui` document as base64, so a project is always a single self-contained
file.

Set **nine-slice insets** on an imported texture to use it as a stretchable
panel or button skin: the corners stay pixel-exact while the edges and centre
stretch. Without insets, custom art can only be used at its native size.

Any element with a `texture` property can use an imported image - buttons,
panels, the hotbar, icon buttons, tab icons, item previews in slots, and the
dedicated Image / Texture Slot component.

**GIFs become animations.** An imported GIF is decoded and rewritten as a
vertical frame strip - a single PNG holding every frame, which is the one
animation format both editions understand. Drop it onto an **Animated image**
element and it plays in the editor, in the exported HTML, and in the game, with
the `.mcmeta` timing sidecar written alongside it in the resource pack.

Long or large GIFs are trimmed on import: at most 128 frames, sampled evenly
across the whole animation so a trimmed one still plays end to end, and scaled
down past 512px on the longer side. A GIF authored with uneven frame delays
keeps them frame by frame rather than being flattened to one rate.

**Anything that is not a GIF** goes through **Build an animation from images**
(File menu on desktop, overflow menu on Android). Pick two or more images and
they are stacked into the same frame strip, in file-name order - which is how
frame sequences are always numbered. The first image sets the size and the rest
are scaled to match, and the animation is named after the shared part of the
file names, so `walk_01.png` … `walk_12.png` becomes `walk`.

That is also the route for video: the app does not decode video files, because
doing so needs a codec it would have to ship. Export the frames with any video
tool - `ffmpeg -i clip.mp4 frame_%03d.png` will do it - and import those.

### Shapes and custom elements

**＋ Add anything** in the desktop bottom bar, or the **Custom** tab in the
Android bottom navigation, opens a picker with three groups:

* **Shapes** - seventeen of them, from a plain rectangle to a star with an
  adjustable point count and notch depth. Each takes a solid or gradient fill,
  an outline, rotation and a label, and resizes like anything else.
* **Animated & imagery** - the animated image described above, and a plain
  still.
* **Anything else** - a **Custom element** with a type name of your choosing
  and free-form `key=value` properties that every exporter passes straight
  through, for a widget the catalog does not have.

Shapes are deliberately not in the component palette. That palette lists
Minecraft's own widgets; a hexagon is a drawing primitive, and filing it beside
"Chest background panel" would misrepresent both.

### Moving things exactly

Four arrows appear over the canvas whenever something is selected. The number
in the middle is the step they move by; tapping it switches between the small
and big step, and both are set in **Editor settings**.

On desktop the arrow keys use the same step, so the buttons and the keyboard
can never disagree - Shift gives the big one. Turning
*Arrow keys use the same steps* off restores the classic fixed 1px / Shift+8px
behaviour for anyone who wants coarse buttons and fine keys.

With *Snap moves to the grid* on, a move goes to the next grid line rather than
a fixed number of pixels, so repeated presses walk the grid instead of drifting
off it.

### Editor settings

**View ▸ Editor Settings** (or **⋯** in the bottom bar) on desktop, **⋮ ▸ Editor
settings** on Android. Settings belong to you rather than to the document, so
they survive opening another project, and each platform stores them in its own
preferences.

| Setting | Default | What it does |
| --- | --- | --- |
| Small step | 1px | How far one press of a move arrow shifts the selection |
| Big step | 8px | The step with Shift held, or the pad's step button lit |
| Arrow keys use the same steps | On | Keyboard and buttons move by the same amount |
| Snap moves to the grid | Off | Move to the next grid line instead of by a fixed amount |
| Show the move pad | On | The four arrows over the canvas |
| Move pad corner | Bottom right | Which corner it sits in |
| Duplicate offset | 8px | How far a copy lands from its original |
| Ask before deleting | Off | A confirmation before a delete; undo already covers it |
| Play animated images | On | Off pins every animation to its first frame |
| Autosave | 10s | How often unsaved work is snapshotted; Off disables it |

### Not losing work

Nothing replaces the open document without asking. **New**, **Open**, **Open
Recent**, loading a template, quitting the desktop app and the Android back
gesture all check for unsaved edits first and offer **Save**, **Discard** or
**Cancel**. Cancelling a save dialog cancels the whole action rather than
falling through and discarding the document anyway.

On top of that, each platform guards against being killed rather than closed:

| | Desktop | Android |
| --- | --- | --- |
| What is written | A recovery snapshot in the app data directory | The working document in internal storage |
| When | On the autosave interval while the document is dirty | On the same interval, and whenever the app is backgrounded (`onStop`) |
| On next launch | Offers to recover, naming the project and the time | Restores silently, still marked unsaved |
| Cleared when | The document is saved, or the app exits normally | The session is replaced |

Recovered work is always restored as **unsaved**: it was never written to your
own file, so the editor keeps asking about it until you save it yourself.
Recovering never overwrites anything.

The desktop app data directory is `%APPDATA%\MinecraftGuiDesigner` on Windows,
`~/Library/Application Support/MinecraftGuiDesigner` on macOS and
`$XDG_CONFIG_HOME/MinecraftGuiDesigner` (usually `~/.config/...`) elsewhere.

That folder keeps the old name deliberately. Renaming it in 1.6.0 alongside the
app would have orphaned every existing install's preferences, recent files and
crash-recovery snapshot — a cosmetic gain paid for with the user's data. It
holds `preferences.json` and, only after an unclean shutdown, the recovery
snapshot, plus `prefabs.json` and `texture-library.json`.

### What is remembered between runs

The desktop app restores its window size and position, whether it was
maximised, which docks were open, the theme and wallpaper settings, every
editor setting, the last export target and code language, and the ten most
recent projects. Recent entries whose file has since been moved or
deleted are dropped rather than offered. A corrupt or unreadable
`preferences.json` is treated as a first run instead of failing to start.

The welcome screen appears on launch and offers a blank screen in either
edition, the template gallery and the recent list. Turn it off with **Don't
show this on startup**; reopen it any time from **Help › Welcome Screen**.
