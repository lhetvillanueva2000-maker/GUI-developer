# The site

Two things live here, and both are static files with no build step at deploy
time:

- **`index.html`** — the landing page. One file, no dependencies, no external
  requests. Every style, script and image is inside it, including the favicon
  and all the illustrations, which are inline SVG and CSS rather than image
  files. Opening it from disk works exactly as well as serving it.
- **`app/`** — the editor itself, compiled to WebAssembly. The same Kotlin that
  builds the Windows, macOS, Linux and Android apps, running in the page.

## Putting it on Netlify

**The quick way.** Go to <https://app.netlify.com/drop> and drag this `web`
folder onto the page. That is the whole process — no account setup, no build
configuration. Netlify serves `index.html` at the root, the editor at `/app/`,
and reads `_headers` from inside the folder, so the security headers and
caching rules apply too.

Dragging `index.html` on its own also works and gives up two things: the
headers, and the editor.

**From the repository.** Connect the repo in Netlify and leave the base
directory at the repository root; the `netlify.toml` there sets
`publish = "web"` and no build command. Every push to the branch then
redeploys.

## Rebuilding the editor

`app/` is committed output. Rebuild it after changing anything in `:webApp`,
`:styles`, `:exporters` or `:sharedCore`:

```
./gradlew :webApp:publishWebApp
```

That compiles the wasm module, runs `wasm-opt` over it, and syncs the result
into `app/`. It is committed rather than built on deploy because the
alternative is asking Netlify to provision a JDK, Node, Yarn and Binaryen to
produce files that are byte-identical every time the source has not changed.

The first run downloads Node, Yarn and Binaryen; they are declared as ordinary
dependencies in `settings.gradle.kts`, which is why that file has three `ivy`
repositories in it.

## What the landing page does

It works out what it is being read on and changes accordingly, rather than only
changing with the width of the window:

- **Phone.** One column, a sticky bar pinned to the bottom of the screen, the
  explanations collapsed into accordions, and the phone mock-up.
- **Laptop or desktop.** Two-column hero with the editor window beside it, a
  navigation bar in the header, three-up grids, hover states, and the keyboard
  shortcuts — which are only shown where there is a keyboard.
- **Tablet.** Two-up grids, no bottom bar.

The phone/tablet split uses the *smallest* side of the screen, which does not
change when the device is rotated. That is the same rule the app itself uses
(`DeviceClass.ofSmallestWidth`), so a phone held sideways is still a phone
here, rather than being handed the laptop layout on a screen four inches tall.

The download button is aimed at the detected platform — Windows gets the
`.exe`, macOS the `.dmg`, Android the `.apk`, Linux the `.deb` — and that card
is marked and moved to the front of the downloads grid. iOS and iPadOS are told
plainly that there is nothing to install, and pointed at the browser build,
which is the one that does run there.

Everything above degrades. With JavaScript turned off the page still renders
completely, every download link still points at a real file, and the button
simply says "Download UILabs" and goes to the releases page. Light and dark
both come from `prefers-color-scheme`, with a manual toggle that overrides it
and is remembered; `prefers-reduced-motion` stops the two animated diagrams.

## What the browser build is, and is not

It is the app. The screens, the design canvas with its drag-resize-rotate
gestures, the paint engine with its layers and its magic eraser, and every
exporter are all the same code the desktop and Android builds run — `:styles`,
`:exporters` and `:sharedCore` simply gained a `wasmJs` target. What is
specific to the browser is one module, `:webApp`, and it is the same size and
shape as the other two shells: file handling, preferences, and the editor's
own furniture.

What is genuinely different is what a page is allowed to do:

- **Save is a download** and **Open is a file picker**, because a page cannot
  write to a path. An export of more than one file arrives as a `.zip` rather
  than as fourteen separate downloads that would lose their folder layout.
- **The document is kept in the tab**, in `localStorage`, so closing it and
  coming back finds the design where it was. Anything over about a megabyte is
  refused with a message rather than silently dropped.
- **The wallpaper behind the editor is the procedural one**, not the four
  painted PNGs the installed builds ship, which would be a megabyte added to
  the first paint of a page.

It needs a browser with WebAssembly garbage collection — Chrome or Edge 119+,
Firefox 120+, Safari 18.4+. `app/index.html` checks for it before downloading
anything and says so plainly rather than failing somewhere inside the module.

## What has and has not been checked

Honestly, because the difference matters.

**Verified.** The whole app compiles for `wasmJs`; the module loads in a real
browser; `main()` runs; the composition builds; the frame clock ticks; the
canvas is created and sized; and the loading screen comes down from inside the
first composed frame, which it could not do if any of that had failed.

**Not verified: that it puts pixels on the screen.** The machine this was built
on has no GPU. Its only WebGL is SwiftShader, Chromium's software rasteriser,
and Skia's GL backend throws inside its first frame there. The thing that
settles what that means: a stock three-line Compose page — a `Box` with a green
background and nothing else — fails there in exactly the same way, at exactly
the same point. So the fault is the software rasteriser, not this app. A real
GPU is the ordinary case and there is every reason to expect it to draw, but
"every reason to expect" is not "watched it happen", so **open it once on a
real machine before handing the link to anybody.**

Two things were added because of what that investigation turned up, and both
matter on real hardware:

- **`WEBGL_debug_renderer_info` is polyfilled** in `app/index.html`. Skia asks
  the WebGL context which GPU it is running on during start-up, and that
  extension is also a fingerprinting vector, so a growing list of browsers
  withhold it — Firefox with `resistFingerprinting`, Brave on default shields,
  Safari in Lockdown Mode, Android's WebView. Where it is missing, the probe
  raises `INVALID_ENUM` and the render pipeline falls over on its first frame,
  which the reader sees as a page that loads and then stays blank. The shim
  answers `"Unknown"` only when the browser will not answer at all, so it
  changes nothing where the extension exists and defeats no privacy
  protection. It did *not* rescue the software rasteriser here — that failure
  is something else — but it is a real failure mode on real browsers.
- **A render watchdog.** If the app starts and then something throws, a panel
  explains that the browser could not give the page a graphics context,
  suggests turning hardware acceleration back on, and points at the
  installers. It can be dismissed, because the diagnosis is a guess and must
  not be able to hide an editor that is in fact working.

## Keeping the downloads current

The landing page ships with the real file names, sizes and links for the
version it was built for — 2.2.0 — so it is correct offline and on first paint.
On load it also asks the GitHub API for the latest release and, if there is a
newer one, rewrites the version, the file names, the sizes and the links to
match. If that request fails, is rate-limited or is blocked, nothing changes
and nothing is reported: the baked-in answer was never wrong, only possibly
old.

Cutting a new release therefore needs no edit here. The one thing worth
updating by hand is the `VERSION` constant and the `data-bytes` values, so the
no-JavaScript and offline case stays accurate too.
