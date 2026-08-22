# Donation assets

`donate-qr.png` is the InstaPay / QR Ph code shown on the app's **Support the
designer** page and written out when someone saves it.

## Where it came from

It was rebuilt from the original photo of the code rather than cropped out of
it. The photo was a JPEG with no quiet zone, and both of those make a scanner
work harder than it has to. The rebuild:

1. Located the code in the photo and measured its module pitch from the
   finder patterns — a 69 × 69 grid, so a version 13 symbol.
2. Sampled each module at its centre and checked the result against the
   symbol's own structure: all three finder patterns and both timing patterns
   have to come out exactly right, which they did. That check is what makes
   this a transcription rather than a guess.
3. Redrew the grid with exact squares at 14 px per module and added the
   4-module quiet zone the spec asks for.
4. Copied the InstaPay badge back over the middle, straight from the photo's
   pixels, so the covered modules are damaged in exactly the way they already
   were and the code still looks like itself.

The result is 1078 × 1078 and encodes the same payload as the photo.

## Replacing it

Drop a new `donate-qr.png` in this folder. Both apps read this one file — the
desktop build copies it onto the classpath and the Android build packages it as
an asset — so there is nothing else to update. Keep the quiet zone: without it
some scanners fail on a code that is flush against a card edge.

Payment details that appear as text alongside the code live in
`sharedCore/src/commonMain/kotlin/com/mcguidesigner/core/support/Donation.kt`.
