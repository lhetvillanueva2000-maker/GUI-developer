# The download page

`index.html` is the whole site. One file, no build step, no dependencies, no
external requests — every style, script and image is inside it, including the
favicon and all the illustrations, which are inline SVG and CSS rather than
image files. Opening it from disk works exactly as well as serving it.

## Putting it on Netlify

**The quick way.** Go to <https://app.netlify.com/drop> and drag this `web`
folder onto the page. That is the whole process — no account setup, no build
configuration. Netlify serves `index.html` at the root and reads `_headers`
from inside the folder, so the security headers and caching rules apply too.

Dragging `index.html` on its own also works, and gives up nothing except those
headers.

**From the repository.** Connect the repo in Netlify and leave the base
directory at the repository root; the `netlify.toml` there sets
`publish = "web"` and no build command. Every push to the branch then
redeploys the page.

## What the page does

It works out what it is being read on and changes accordingly, rather than
only changing with the width of the window:

- **Phone.** One column, a sticky download bar pinned to the bottom of the
  screen, the explanations collapsed into accordions, and the phone mock-up.
- **Laptop or desktop.** Two-column hero with the editor window beside it, a
  navigation bar in the header, three-up grids, hover states, and the keyboard
  shortcuts — which are only shown where there is a keyboard.
- **Tablet.** Two-up grids, no bottom bar.

The phone/tablet split uses the *smallest* side of the screen, which does not
change when the device is rotated. That is the same rule the app itself uses
(`DeviceClass.ofSmallestWidth`), so a phone held sideways is still a phone
here, rather than being handed the laptop layout on a screen four inches tall.

The primary download button is aimed at the detected platform — Windows gets
the `.exe`, macOS the `.dmg`, Android the `.apk`, Linux the `.deb` — and that
card is marked and moved to the front of the downloads grid. iOS and iPadOS
are told plainly that no build exists rather than being offered one.

Everything above degrades. With JavaScript turned off the page still renders
completely, every download link still points at a real file, and the button
simply says "Download UILabs" and goes to the releases page. Light and dark
both come from `prefers-color-scheme`, with a manual toggle that overrides it
and is remembered; `prefers-reduced-motion` stops the two animated diagrams.

## Keeping it current

The page ships with the real file names, sizes and links for the version it
was built for — 2.2.0 — so it is correct offline and on first paint. On load
it also asks the GitHub API for the latest release and, if there is a newer
one, rewrites the version, the file names, the sizes and the links to match.
If that request fails, is rate-limited or is blocked, nothing changes and
nothing is reported: the baked-in answer was never wrong, only possibly old.

Cutting a new release therefore needs no edit here. The one thing worth
updating by hand is the `VERSION` constant and the `data-bytes` values, so
that the no-JavaScript and offline case stays accurate too.
