# Using the editor

The desktop and Android apps drive the same editor core, but their interaction
models are deliberately different. This page covers both.

---

## Desktop

### Layout

| Region | Contents |
| --- | --- |
| Toolbar | Edition badge, tools, undo/redo, grid and snap toggles, align tools, zoom, view mode |
| Left dock | **Components** (palette with live thumbnails), **Layers** (tree), **Templates** |
| Centre | Design canvas with rulers, or the live preview, or the code view |
| Right dock | **Properties** (inspector), **Assets** (textures), **Issues** (validation) |
| Status bar | Status message, issue counts, canvas size, selection, selected element bounds |

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
| `Ctrl+S` / `Ctrl+Shift+S` | Save / Save as |
| `Ctrl+E` | Export |
| `Ctrl+Z` / `Ctrl+Y` | Undo / Redo |
| `Ctrl+C` / `Ctrl+V` / `Ctrl+X` | Copy / Paste / Cut elements |
| `Ctrl+D` | Duplicate |
| `Ctrl+A` | Select all |
| `Delete` | Delete selection |
| `Escape` | Deselect, or cancel a pending placement |
| `V` / `H` / `M` | Select / Pan / Marquee tool |
| Arrow keys | Nudge 1px (Shift: 8px) |
| `Ctrl+]` / `Ctrl+[` | Bring forward / send backward |
| `Ctrl+Shift+]` / `Ctrl+Shift+[` | Bring to front / send to back |
| `Ctrl+G` / `Ctrl+R` | Toggle grid / rulers |
| `Ctrl++` / `Ctrl+-` / `Ctrl+0` | Zoom in / out / reset |

Copy and paste go through the real system clipboard as JSON, so you can paste
elements between two running copies of the app - or into a text editor.

---

## Android

Not a shrunken desktop: the navigation, gestures and controls are all built for
a thumb.

### Layout

* **Bottom navigation** (portrait) or a **navigation rail** (landscape and
  tablets, above 600dp wide): Design, Layers, Preview, Code, Add.
* **Modal bottom sheets** replace the desktop docks - one purpose at a time,
  dismissible with a swipe, always leaving the canvas visible behind them.
* A **floating selection bar** over the canvas holds the actions that live in
  menus on desktop: edit properties, duplicate, raise, lower, lock, delete.
* The overflow menu holds save/open, templates, textures, canvas settings,
  project settings, issues, export, and the edition switch.

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
| When | Every 10 seconds while the document is dirty | Whenever the app is backgrounded (`onStop`) |
| On next launch | Offers to recover, naming the project and the time | Restores silently, still marked unsaved |
| Cleared when | The document is saved, or the app exits normally | The session is replaced |

Recovered work is always restored as **unsaved**: it was never written to your
own file, so the editor keeps asking about it until you save it yourself.
Recovering never overwrites anything.

The desktop app data directory is `%APPDATA%\MinecraftGuiDesigner` on Windows,
`~/Library/Application Support/MinecraftGuiDesigner` on macOS and
`$XDG_CONFIG_HOME/MinecraftGuiDesigner` (usually `~/.config/...`) elsewhere. It
holds `preferences.json` and, only after an unclean shutdown, the recovery
snapshot.

### What is remembered between runs

The desktop app restores its window size and position, whether it was
maximised, which docks were open, the last export target and code language, and
the ten most recent projects. Recent entries whose file has since been moved or
deleted are dropped rather than offered. A corrupt or unreadable
`preferences.json` is treated as a first run instead of failing to start.

The welcome screen appears on launch and offers a blank screen in either
edition, the template gallery and the recent list. Turn it off with **Don't
show this on startup**; reopen it any time from **Help › Welcome Screen**.
