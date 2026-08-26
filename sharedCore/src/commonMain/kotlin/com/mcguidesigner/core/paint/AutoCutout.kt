package com.mcguidesigner.core.paint

import com.mcguidesigner.core.paint.Pixels.alpha
import com.mcguidesigner.core.paint.Pixels.argb
import com.mcguidesigner.core.paint.Pixels.blue
import com.mcguidesigner.core.paint.Pixels.green
import com.mcguidesigner.core.paint.Pixels.red
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Removing a background properly.
 *
 * **What this is not.** There is no neural network here and nothing is
 * downloaded. Nothing in this app was ever trained - a language model writing
 * Kotlin does not leave a trained segmentation model behind it, and shipping a
 * button labelled "AI" over a colour threshold would be a lie that the first
 * difficult photograph exposes. What is here instead is the classical pipeline
 * that image editors used before learned models and still fall back to: build a
 * colour model of the background, label every pixel against it, clean up the
 * labelling with a smoothness term so it follows edges rather than noise, and
 * then *matte* the boundary so that hair, fur and motion blur come out as
 * partial alpha instead of a cut-out silhouette.
 *
 * That last stage is the one that decides whether a cutout looks done by hand
 * or done by a threshold, and it is the one cheap implementations skip. A hard
 * in-or-out mask on a photograph leaves a jagged fringe carrying the old
 * background's colour, which is instantly recognisable no matter how good the
 * segmentation underneath it was.
 *
 * **What it is good at**: a subject against a background that is different from
 * it - a product shot, a person against a wall, a drawing on paper, a logo on
 * white. **What it is not good at**: a subject the same colour as what is
 * behind it, and heavy transparency like glass. For those the magic eraser and
 * the brush are the honest answer, which is why all three exist.
 *
 * ### The stages
 *
 * 1. Work at a reduced resolution. Segmentation decisions are about regions,
 *    not pixels, and running the expensive part on a 12-megapixel photograph
 *    buys nothing but a minute of waiting.
 * 2. Seed from the border. Whatever is in the outer band is background: it is
 *    the single most reliable assumption available and it needs nothing from
 *    the user.
 * 3. Cluster both sides in colour space and label by distance.
 * 4. Smooth the labelling with contrast-sensitive neighbour terms, so the
 *    boundary snaps to real edges and speckle disappears.
 * 5. Upsample, build a trimap, and matte the uncertain band.
 * 6. Decontaminate the boundary colours so no background tint is left behind.
 */
object AutoCutout {

    /** How hard to push. Higher keeps more; lower cuts more aggressively. */
    data class Options(
        /** 0..100. Raising it keeps more of the image. */
        val keep: Int = 50,
        /** Width of the uncertain band that gets matted, in output pixels. */
        val edgeSoftness: Int = 2,
        /** Remove the background's colour cast from partly-transparent edges. */
        val decontaminate: Boolean = true,
        /**
         * Longest side used for the segmentation stage.
         *
         * 512 rather than the 320 this started at. The segmentation is the only
         * stage that runs reduced, and halving the reduction roughly doubles
         * how finely the label boundary can follow a thin feature - a raised
         * arm, a chair leg, a strand of hair thick enough to matter. It costs
         * about two and a half times the arithmetic, which was unaffordable
         * when this ran on the frame thread and is unremarkable now that it
         * does not.
         */
        val workingSize: Int = 512,
    )

    /**
     * The alpha mask for [source], 0..255 per pixel, foreground = 255.
     *
     * Returned rather than applied so the caller can preview it, invert it, or
     * feather it further without redoing the work.
     */
    fun mask(source: IntArray, width: Int, height: Int, options: Options = Options()): ByteArray {
        if (width <= 2 || height <= 2) return ByteArray(width * height) { 255.toByte() }

        // 1. Reduce.
        val scale = max(1, max(width, height) / max(32, options.workingSize))
        val sw = max(2, width / scale)
        val sh = max(2, height / scale)
        val small = downsample(source, width, height, sw, sh)

        // 2, 3 and 4, alternating until they agree - see [segment].
        val border = max(1, min(sw, sh) / 12)
        var labels = segment(small, sw, sh, border, options.keep) ?: return ByteArray(width * height) { 255.toByte() }
        labels = keepLargestComponent(labels, sw, sh)

        // 5. Back to full resolution, then matte the uncertain band.
        val upscaled = upsampleLabels(labels, sw, sh, width, height)
        val band = max(1, options.edgeSoftness + scale)
        val trimap = buildTrimap(upscaled, width, height, band)
        val matted = matte(source, width, height, trimap, band)

        // 6. Pull the matte onto the image's own edges.
        return guided(source, matted, width, height, max(2, band))
    }

    /**
     * Applies [mask] to [layer], with an optional colour decontamination pass.
     *
     * Separate from [mask] so an undo step can be opened around exactly this,
     * and so a caller that wants to invert or edit the mask first can.
     */
    fun apply(layer: PaintLayer, mask: ByteArray, options: Options = Options(), invert: Boolean = false) {
        if (layer.locked) return
        val pixels = layer.pixels
        if (options.decontaminate) decontaminate(pixels, mask, layer.width, layer.height)
        for (i in pixels.indices) {
            val m = mask[i].toInt() and 0xFF
            val keep = if (invert) 255 - m else m
            val existing = alpha(pixels[i])
            if (existing == 0) continue
            pixels[i] = Pixels.withAlpha(pixels[i], Pixels.mul(existing, keep))
        }
    }

    // -- Stage 1: reduce ---------------------------------------------------

    /** Box-average down to [tw] x [th]. Averaging, not sampling: a nearest
     *  neighbour reduction keeps the noise it is supposed to be removing. */
    private fun downsample(source: IntArray, width: Int, height: Int, tw: Int, th: Int): IntArray {
        val out = IntArray(tw * th)
        for (ty in 0 until th) {
            val y0 = ty * height / th
            val y1 = max(y0 + 1, (ty + 1) * height / th)
            for (tx in 0 until tw) {
                val x0 = tx * width / tw
                val x1 = max(x0 + 1, (tx + 1) * width / tw)
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                var n = 0
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) {
                        val p = source[row + x]
                        r += red(p); g += green(p); b += blue(p); a += alpha(p)
                        n++
                    }
                }
                out[ty * tw + tx] = argb(a / n, r / n, g / n, b / n)
            }
        }
        return out
    }

    // -- Stages 2 and 3: colour models -------------------------------------

    private const val K = 5

    private fun sampleBorder(pixels: IntArray, w: Int, h: Int, band: Int): List<Int> {
        val out = ArrayList<Int>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val onBorder = x < band || y < band || x >= w - band || y >= h - band
                if (onBorder) out.add(pixels[y * w + x])
            }
        }
        return out
    }

    /**
     * The background colour model, with the subject's intrusions thrown out.
     *
     * "Whatever is at the border is background" is the assumption the whole
     * pipeline rests on, and it is *mostly* right - but a subject cropped at
     * the frame edge breaks it, and that is not a rare photograph, it is a
     * portrait. When it happens the naive model absorbs the subject's own
     * colour, every pixel then looks like background, and the cut comes out
     * arbitrary or inverted.
     *
     * The fix is to notice that an intrusion is *small*. Clustering the border
     * and discarding any cluster holding less than a twelfth of it removes the
     * shoulder crossing the bottom edge while keeping every real part of a
     * background, however varied - a gradient sky spreads its samples across
     * several clusters, all of them substantial.
     *
     * Never returns empty: if every cluster is minor the border is genuinely
     * that varied, and all of them are kept.
     */
    private fun robustBorderModel(samples: List<Int>): List<Cluster> {
        val clusters = kmeans(samples, K)
        if (clusters.size <= 1) return clusters
        val total = clusters.sumOf { it.count }
        if (total == 0) return clusters
        val floor = total / 12
        val kept = clusters.filter { it.count > floor }
        return kept.ifEmpty { clusters }
    }

    private fun sampleLabelled(pixels: IntArray, labels: ByteArray, want: Byte): List<Int> {
        val out = ArrayList<Int>()
        for (i in labels.indices) if (labels[i] == want) out.add(pixels[i])
        return out
    }

    private class Cluster(var r: Float, var g: Float, var b: Float) {
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var count = 0
        fun reset() { sr = 0.0; sg = 0.0; sb = 0.0; count = 0 }
        fun add(p: Int) { sr += red(p); sg += green(p); sb += blue(p); count++ }
        fun settle() {
            if (count == 0) return
            r = (sr / count).toFloat(); g = (sg / count).toFloat(); b = (sb / count).toFloat()
        }
        fun distanceTo(p: Int): Float {
            val dr = red(p) - r
            val dg = green(p) - g
            val db = blue(p) - b
            // The same channel weighting as Pixels.distance, so the tolerance
            // in one part of the app means the same as in another.
            return sqrt(2f * dr * dr + 4f * dg * dg + 3f * db * db)
        }
    }

    /**
     * k-means over colours, seeded by spreading the initial centres apart.
     *
     * Random seeding gives a different cutout every time the button is pressed,
     * which reads as the tool being unreliable even when the average quality is
     * identical. Furthest-point seeding is deterministic and lands in a better
     * place anyway.
     */
    private fun kmeans(samples: List<Int>, k: Int, iterations: Int = 8): List<Cluster> {
        if (samples.isEmpty()) return emptyList()
        val step = max(1, samples.size / 4000)
        val data = ArrayList<Int>(samples.size / step + 1)
        var i = 0
        while (i < samples.size) { data.add(samples[i]); i += step }

        val centres = ArrayList<Cluster>(k)
        centres.add(Cluster(red(data[0]).toFloat(), green(data[0]).toFloat(), blue(data[0]).toFloat()))
        while (centres.size < k && centres.size < data.size) {
            var bestPixel = data[0]
            var bestDistance = -1f
            for (p in data) {
                var nearest = Float.MAX_VALUE
                for (c in centres) nearest = min(nearest, c.distanceTo(p))
                if (nearest > bestDistance) { bestDistance = nearest; bestPixel = p }
            }
            centres.add(Cluster(red(bestPixel).toFloat(), green(bestPixel).toFloat(), blue(bestPixel).toFloat()))
        }

        repeat(iterations) {
            centres.forEach { it.reset() }
            for (p in data) {
                var best = centres[0]
                var bestD = Float.MAX_VALUE
                for (c in centres) {
                    val d = c.distanceTo(p)
                    if (d < bestD) { bestD = d; best = c }
                }
                best.add(p)
            }
            centres.forEach { it.settle() }
        }
        return centres.filter { it.count > 0 }.ifEmpty { centres }
    }

    private fun nearest(clusters: List<Cluster>, pixel: Int): Float {
        var best = Float.MAX_VALUE
        for (c in clusters) best = min(best, c.distanceTo(pixel))
        return best
    }

    // -- Stage 4: labelling ------------------------------------------------

    private const val FOREGROUND: Byte = 1
    private const val BACKGROUND: Byte = 0

    /**
     * Labels every pixel foreground or background, alternating between fitting
     * colour models and relabelling until the two agree.
     *
     * This is the expectation-maximisation loop at the heart of GrabCut, and
     * doing it *once* rather than to convergence was the bug that produced a
     * wedge of sky attached to the subject. The first foreground model is
     * necessarily contaminated - it is fitted to "everything that is not the
     * border", which on any real photograph is mostly background - so a single
     * pass classifies a lot of background as subject. Refitting the models to
     * what was actually kept, and relabelling against those, converges within
     * two or three rounds onto models that describe the two regions rather than
     * the two initial guesses.
     *
     * Returns null when there is nothing to work with.
     */
    private fun segment(pixels: IntArray, w: Int, h: Int, border: Int, keep: Int): ByteArray? {
        val pinned = max(1, border / 2)
        val borderSamples = sampleBorder(pixels, w, h, border)
        if (borderSamples.isEmpty()) return null

        val bias = 1f + (keep - 50) / 100f

        // The starting split comes from distance to the *background* model
        // alone, with the threshold chosen by Otsu's method.
        //
        // Not from "the interior is the subject", which was the previous start
        // and does not work: on any ordinary photograph most of the interior is
        // background too, so the foreground model fitted to it lands several of
        // its clusters on background colours, the two models overlap, and the
        // loop converges on calling the whole picture foreground. Distance to
        // the background model has no such problem - it is one model, fitted to
        // pixels that really are background - and Otsu finds the split in that
        // distribution without being told where to look.
        val bg0 = robustBorderModel(borderSamples)
        val distance = IntArray(w * h)
        for (i in distance.indices) {
            distance[i] = if (alpha(pixels[i]) == 0) 0 else nearest(bg0, pixels[i]).toInt().coerceIn(0, 255)
        }
        val threshold = otsu(distance)

        var current = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                current[i] = when {
                    alpha(pixels[i]) == 0 -> BACKGROUND
                    x < border || y < border || x >= w - border || y >= h - border -> BACKGROUND
                    distance[i] > threshold -> FOREGROUND
                    else -> BACKGROUND
                }
            }
        }

        repeat(3) { round ->
            val bgSamples = sampleLabelled(pixels, current, BACKGROUND)
            val fgSamples = sampleLabelled(pixels, current, FOREGROUND)
            if (bgSamples.isEmpty() || fgSamples.isEmpty()) return current
            val bg = kmeans(bgSamples, K)
            val fg = kmeans(fgSamples, K)

            // The data term: how much better one model explains this pixel than
            // the other, as a signed score. Positive means foreground.
            // Normalised, so the smoothness weight below means the same thing
            // whatever the image's own contrast happens to be.
            val data = FloatArray(w * h)
            for (i in data.indices) {
                val p = pixels[i]
                val dBg = nearest(bg, p)
                val dFg = nearest(fg, p) / bias
                data[i] = (dBg - dFg) / (dBg + dFg + 1f)
            }

            current = refine(current, data, pixels, w, h, pinned, passes = if (round == 2) 6 else 3)
        }
        return enforceBorderIsBackground(current, w, h, border)
    }

    /**
     * Flips the labelling if it has come out inside-out.
     *
     * "The border is background" is the one thing this algorithm is told rather
     * than infers, so it is also the one thing worth checking at the end. If
     * most of the border band has ended up labelled foreground, the two labels
     * have swapped roles somewhere - which is what a badly conditioned colour
     * model does, and what produced a cutout that kept the sky and deleted the
     * subject - and swapping them back is strictly better than shipping the
     * inverse of what was asked for.
     *
     * A check rather than a constraint during the loop, because pinning the
     * whole band every pass stops the boundary from reaching a subject that
     * genuinely touches the edge.
     */
    private fun enforceBorderIsBackground(labels: ByteArray, w: Int, h: Int, border: Int): ByteArray {
        var foreground = 0
        var total = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x >= border && y >= border && x < w - border && y < h - border) continue
                total++
                if (labels[y * w + x] == FOREGROUND) foreground++
            }
        }
        if (total == 0 || foreground * 2 <= total) return labels
        val flipped = ByteArray(labels.size)
        for (i in labels.indices) {
            flipped[i] = if (labels[i] == FOREGROUND) BACKGROUND else FOREGROUND
        }
        return flipped
    }

    /**
     * Iterated conditional modes: the data term against a contrast-sensitive
     * smoothness term.
     *
     * Each pass every pixel takes whichever label wins a vote between what its
     * own colour says (the data term) and what its neighbours say, weighted by
     * how similar it is to each of them. The similarity weighting is what makes
     * the boundary follow real edges: across a strong edge a neighbour barely
     * votes, so the label may change there and may not in the middle of a flat
     * region.
     *
     * Keeping the data term in *every* pass is the part that matters. An
     * earlier version dropped it after initialisation and smoothed on
     * neighbours alone, which is a majority filter with no memory of the image:
     * it will happily grow a region across a gentle gradient until it reaches
     * something it cannot cross, and on a graded background that is exactly
     * what it did.
     */
    private fun refine(
        labels: ByteArray,
        data: FloatArray,
        pixels: IntArray,
        w: Int,
        h: Int,
        pinned: Int,
        passes: Int,
    ): ByteArray {
        var current = labels
        repeat(passes) {
            val next = current.copyOf()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    // The outermost ring stays background. It is the one piece
                    // of ground truth there is, and letting the refinement
                    // argue with it is how a cutout inverts itself entirely.
                    if (x < pinned || y < pinned || x >= w - pinned || y >= h - pinned) {
                        next[i] = BACKGROUND
                        continue
                    }
                    if (alpha(pixels[i]) == 0) {
                        next[i] = BACKGROUND
                        continue
                    }

                    val p = pixels[i]
                    // Colour evidence, scaled to be comparable with the four
                    // neighbour votes below.
                    var score = data[i] * 2.2f
                    for (d in FOUR) {
                        val nx = x + d[0]
                        val ny = y + d[1]
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val j = ny * w + nx
                        val similarity = 1f / (1f + Pixels.distance(p, pixels[j]) / 10f)
                        score += if (current[j] == FOREGROUND) similarity else -similarity
                    }
                    next[i] = if (score > 0f) FOREGROUND else BACKGROUND
                }
            }
            current = next
        }
        return current
    }

    /**
     * Otsu's threshold: the split of a histogram that best separates it in two.
     *
     * Chosen over a fixed cut-off because there is no fixed cut-off that is
     * right. How far the subject sits from the background in colour space
     * depends entirely on the photograph, and any constant is either too
     * generous on a high-contrast image or too mean on a subtle one. Otsu asks
     * the histogram instead: it picks the threshold that maximises the variance
     * *between* the two groups, which is the same as minimising the variance
     * within them.
     */
    private fun otsu(values: IntArray): Int {
        val histogram = IntArray(256)
        for (v in values) histogram[v.coerceIn(0, 255)]++
        val total = values.size
        if (total == 0) return 0

        var sum = 0.0
        for (i in 0 until 256) sum += i.toDouble() * histogram[i]

        var sumBelow = 0.0
        var countBelow = 0
        var best = 0
        var bestVariance = -1.0

        for (t in 0 until 256) {
            countBelow += histogram[t]
            if (countBelow == 0) continue
            val countAbove = total - countBelow
            if (countAbove == 0) break
            sumBelow += t.toDouble() * histogram[t]
            val meanBelow = sumBelow / countBelow
            val meanAbove = (sum - sumBelow) / countAbove
            val difference = meanBelow - meanAbove
            val variance = countBelow.toDouble() * countAbove * difference * difference
            if (variance > bestVariance) {
                bestVariance = variance
                best = t
            }
        }
        return best
    }

    private val FOUR = arrayOf(
        intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1),
    )

    /**
     * Drops every foreground blob but the biggest.
     *
     * Almost every photograph has one subject, and the leftovers are specks of
     * background that happened to resemble it. Keeping them means the user's
     * next job is erasing confetti by hand, which is worse than the tool having
     * been slightly too aggressive.
     */
    private fun keepLargestComponent(labels: ByteArray, w: Int, h: Int): ByteArray {
        val component = IntArray(labels.size) { -1 }
        var best = -1
        var bestSize = 0
        var id = 0
        val queue = ArrayDeque<Int>()

        for (start in labels.indices) {
            if (labels[start] != FOREGROUND || component[start] != -1) continue
            var size = 0
            queue.addLast(start)
            component[start] = id
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                size++
                val x = i % w
                val y = i / w
                for (d in FOUR) {
                    val nx = x + d[0]
                    val ny = y + d[1]
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (labels[j] == FOREGROUND && component[j] == -1) {
                        component[j] = id
                        queue.addLast(j)
                    }
                }
            }
            if (size > bestSize) { bestSize = size; best = id }
            id++
        }

        if (best < 0) return labels
        val out = ByteArray(labels.size)
        for (i in labels.indices) {
            out[i] = if (labels[i] == FOREGROUND && component[i] == best) FOREGROUND else BACKGROUND
        }
        return out
    }

    // -- Stage 5: trimap and matting ---------------------------------------

    private fun upsampleLabels(labels: ByteArray, sw: Int, sh: Int, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val sy = (y * sh / h).coerceIn(0, sh - 1)
            for (x in 0 until w) {
                val sx = (x * sw / w).coerceIn(0, sw - 1)
                out[y * w + x] = labels[sy * sw + sx]
            }
        }
        return out
    }

    private const val UNKNOWN: Byte = 2

    /** Definite foreground, definite background, and a band of neither. */
    private fun buildTrimap(labels: ByteArray, w: Int, h: Int, band: Int): ByteArray {
        val out = labels.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val self = labels[i]
                var boundary = false
                var dy = -band
                loop@ while (dy <= band) {
                    var dx = -band
                    while (dx <= band) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h && labels[ny * w + nx] != self) {
                            boundary = true
                            break@loop
                        }
                        dx++
                    }
                    dy++
                }
                if (boundary) out[i] = UNKNOWN
            }
        }
        return out
    }

    /**
     * Alpha for the uncertain band, by projecting onto the local
     * foreground-background colour line.
     *
     * For each unknown pixel this finds the nearest confident foreground colour
     * F and background colour B within a small window, and asks where the
     * pixel's own colour I sits between them:
     *
     *     alpha = ((I - B) . (F - B)) / |F - B|^2
     *
     * That is the closed-form solution to the compositing equation for one
     * pair of samples, and it is what produces the partial pixels along a
     * strand of hair - a pixel that is 30% hair and 70% wall comes out at
     * alpha 0.3 rather than being forced to one side.
     *
     * Where F and B are too close to tell apart the equation is degenerate, and
     * the honest answer is to fall back to the label rather than divide by
     * nearly zero and produce noise.
     */
    private fun matte(source: IntArray, w: Int, h: Int, trimap: ByteArray, band: Int): ByteArray {
        val out = ByteArray(w * h)
        for (i in trimap.indices) {
            out[i] = if (trimap[i] == FOREGROUND) 255.toByte() else 0
        }

        val window = max(2, band * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (trimap[i] != UNKNOWN) continue
                val p = source[i]

                var fg = -1
                var bg = -1
                var fgDistance = Int.MAX_VALUE
                var bgDistance = Int.MAX_VALUE

                val y0 = max(0, y - window)
                val y1 = min(h - 1, y + window)
                val x0 = max(0, x - window)
                val x1 = min(w - 1, x + window)
                for (ny in y0..y1) {
                    val row = ny * w
                    for (nx in x0..x1) {
                        val j = row + nx
                        val label = trimap[j]
                        if (label == UNKNOWN) continue
                        val d = (nx - x) * (nx - x) + (ny - y) * (ny - y)
                        if (label == FOREGROUND) {
                            if (d < fgDistance) { fgDistance = d; fg = source[j] }
                        } else {
                            if (d < bgDistance) { bgDistance = d; bg = source[j] }
                        }
                    }
                }

                if (fg == -1 || bg == -1) {
                    out[i] = if (fg != -1) 255.toByte() else 0
                    continue
                }

                val fr = red(fg) - red(bg)
                val fgr = green(fg) - green(bg)
                val fb = blue(fg) - blue(bg)
                val denominator = fr * fr + fgr * fgr + fb * fb
                if (denominator < 48) {
                    // F and B are the same colour to within noise; the equation
                    // has nothing to say and the label is the better answer.
                    out[i] = if (fgDistance <= bgDistance) 255.toByte() else 0
                    continue
                }
                val numerator =
                    (red(p) - red(bg)) * fr + (green(p) - green(bg)) * fgr + (blue(p) - blue(bg)) * fb
                val alpha = (numerator.toFloat() / denominator).coerceIn(0f, 1f)
                out[i] = (alpha * 255f).roundToInt().toByte()
            }
        }
        return out
    }

    // -- Stage 6: edge-aware refinement ------------------------------------

    /**
     * Pulls the matte onto the image's own edges: a guided filter.
     *
     * The segmentation ran at a fraction of the resolution, so its boundary is
     * only ever as accurate as that reduction allowed - upsampling it gives a
     * matte whose edge is a few pixels away from where the subject's edge
     * actually is, and on a downscale of eight that is eight pixels of wrong.
     * The matting stage softens that band but cannot move it.
     *
     * The guided filter can. For every window it fits the alpha as a *linear
     * function of the image's own luminance*, `a = A * luma + B`, and rebuilds
     * alpha from that fit. Where the image has an edge, luma jumps, so the
     * fitted alpha jumps with it in the same place - the matte snaps onto the
     * real boundary. Where the image is flat, the fit degenerates to the local
     * average and the matte is simply smoothed. It is one pass of box sums and
     * it does what a much more expensive matting solve would do to the edge.
     *
     * [epsilon] is the regularisation: how much luminance variation counts as
     * noise rather than an edge. Too small and the matte follows film grain;
     * too large and it stops following anything.
     */
    private fun guided(
        source: IntArray,
        alphaMask: ByteArray,
        width: Int,
        height: Int,
        radius: Int,
        epsilon: Float = 1e-3f,
    ): ByteArray {
        val size = width * height
        val luma = FloatArray(size)
        val alpha = FloatArray(size)
        for (i in 0 until size) {
            val p = source[i]
            luma[i] = (red(p) * 0.299f + green(p) * 0.587f + blue(p) * 0.114f) / 255f
            alpha[i] = (alphaMask[i].toInt() and 0xFF) / 255f
        }

        val meanI = boxMean(luma, width, height, radius)
        val meanA = boxMean(alpha, width, height, radius)
        val corrI = boxMean(FloatArray(size) { luma[it] * luma[it] }, width, height, radius)
        val corrIA = boxMean(FloatArray(size) { luma[it] * alpha[it] }, width, height, radius)

        val coefficientA = FloatArray(size)
        val coefficientB = FloatArray(size)
        for (i in 0 until size) {
            val variance = corrI[i] - meanI[i] * meanI[i]
            val covariance = corrIA[i] - meanI[i] * meanA[i]
            val a = covariance / (variance + epsilon)
            coefficientA[i] = a
            coefficientB[i] = meanA[i] - a * meanI[i]
        }

        val smoothA = boxMean(coefficientA, width, height, radius)
        val smoothB = boxMean(coefficientB, width, height, radius)

        val out = ByteArray(size)
        for (i in 0 until size) {
            val value = smoothA[i] * luma[i] + smoothB[i]
            out[i] = (value.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
        }

        // The filter is a local fit, so it can pull the solid interior a few
        // percent off full and the far background a few percent off empty.
        // Anything that was fully committed before stays committed.
        for (i in 0 until size) {
            val before = alphaMask[i].toInt() and 0xFF
            if (before == 255) out[i] = 255.toByte()
            if (before == 0) out[i] = 0
        }
        return out
    }

    /** Mean over a (2r+1) square, as two separable running sums. */
    private fun boxMean(values: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val temp = FloatArray(values.size)
        val out = FloatArray(values.size)
        val span = radius * 2 + 1

        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (x in -radius..radius) sum += values[row + x.coerceIn(0, width - 1)]
            for (x in 0 until width) {
                temp[row + x] = sum / span
                sum += values[row + (x + radius + 1).coerceIn(0, width - 1)] -
                    values[row + (x - radius).coerceIn(0, width - 1)]
            }
        }
        for (x in 0 until width) {
            var sum = 0f
            for (y in -radius..radius) sum += temp[y.coerceIn(0, height - 1) * width + x]
            for (y in 0 until height) {
                out[y * width + x] = sum / span
                sum += temp[(y + radius + 1).coerceIn(0, height - 1) * width + x] -
                    temp[(y - radius).coerceIn(0, height - 1) * width + x]
            }
        }
        return out
    }

    // -- Stage 7: decontamination ------------------------------------------

    /**
     * Removes the background's colour from partly transparent edge pixels.
     *
     * A pixel that was 40% subject over a green wall is 60% green. Lowering its
     * alpha to 40% and stopping leaves that green in the colour channels, and
     * the cutout keeps a green fringe when it is placed on anything else - the
     * single most recognisable artefact of a cheap background removal.
     *
     * Inverting the compositing equation, `F = (I - (1 - a) * B) / a`, recovers
     * what the subject's colour must have been. It is unstable at low alpha,
     * where a small error in B is divided by a small number, so below a
     * threshold the nearest confident foreground colour is used instead.
     */
    private fun decontaminate(pixels: IntArray, mask: ByteArray, w: Int, h: Int) {
        val original = pixels.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val a = mask[i].toInt() and 0xFF
                if (a == 0 || a == 255) continue

                var bg = -1
                var fg = -1
                var bgD = Int.MAX_VALUE
                var fgD = Int.MAX_VALUE
                val y0 = max(0, y - 3)
                val y1 = min(h - 1, y + 3)
                val x0 = max(0, x - 3)
                val x1 = min(w - 1, x + 3)
                for (ny in y0..y1) {
                    for (nx in x0..x1) {
                        val j = ny * w + nx
                        val m = mask[j].toInt() and 0xFF
                        val d = (nx - x) * (nx - x) + (ny - y) * (ny - y)
                        if (m == 0 && d < bgD) { bgD = d; bg = original[j] }
                        if (m == 255 && d < fgD) { fgD = d; fg = original[j] }
                    }
                }
                if (bg == -1) continue

                val af = a / 255f
                if (af < 0.25f) {
                    if (fg != -1) pixels[i] = Pixels.withAlpha(fg, alpha(pixels[i]))
                    continue
                }

                fun recover(channelI: Int, channelB: Int): Int =
                    ((channelI - (1f - af) * channelB) / af).roundToInt().coerceIn(0, 255)

                val p = original[i]
                pixels[i] = argb(
                    alpha(pixels[i]),
                    recover(red(p), red(bg)),
                    recover(green(p), green(bg)),
                    recover(blue(p), blue(bg)),
                )
            }
        }
    }

    /**
     * How confident the cutout is, 0..100.
     *
     * Shown next to the result so a bad case announces itself instead of the
     * user assuming the tool is broken. Confidence is low when the boundary is
     * long relative to the area it encloses (a ragged mask), and when most of
     * the mask is mid-alpha rather than committed either way.
     */
    fun confidence(mask: ByteArray, w: Int, h: Int): Int {
        var boundary = 0
        var inside = 0
        var uncertain = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val a = mask[i].toInt() and 0xFF
                if (a > 127) inside++
                if (a in 40..215) uncertain++
                if (a > 127) {
                    for (d in FOUR) {
                        val j = (y + d[1]) * w + (x + d[0])
                        if ((mask[j].toInt() and 0xFF) <= 127) { boundary++; break }
                    }
                }
            }
        }
        if (inside == 0) return 0
        // A circle is the tightest a boundary can be for a given area; the
        // ratio against it is a decent raggedness measure.
        val ideal = max(1.0, 2.0 * sqrt(3.14159 * inside))
        val raggedness = (boundary / ideal).coerceIn(1.0, 6.0)
        val uncertainty = uncertain.toDouble() / inside
        val score = 100.0 / raggedness * (1.0 - uncertainty.coerceIn(0.0, 0.7))
        return score.roundToInt().coerceIn(0, 100)
    }
}

/**
 * The other two erasers.
 *
 * The brush eraser lives in [PaintOps.erase] because it is the brush engine
 * with the arithmetic flipped. These are the ones that take a region rather
 * than a path.
 */
object MagicEraser {

    /**
     * Erases everything within [tolerance] of the colour at a point.
     *
     * [contiguous] false is "remove every white pixel on this layer"; true is
     * "remove this patch of white". Both are wanted constantly and neither is
     * a good default for the other.
     *
     * [feather] softens the result's edge, which matters more here than
     * anywhere else: a hard magic-erase against anti-aliased line art leaves a
     * one-pixel outline of half-erased pixels behind, and that outline is the
     * exact shape of the thing that was supposed to be gone.
     */
    fun erase(
        layer: PaintLayer,
        x: Int,
        y: Int,
        tolerance: Int,
        contiguous: Boolean = true,
        feather: Int = 1,
        strength: Float = 1f,
    ): Boolean {
        if (layer.locked) return false
        val mask = RegionFill.flood(
            source = layer.pixels,
            width = layer.width,
            height = layer.height,
            startX = x,
            startY = y,
            tolerance = tolerance,
            contiguous = contiguous,
            feather = feather,
        )
        var touched = false
        val ceiling = (strength.coerceIn(0f, 1f) * 255f).roundToInt()
        for (i in mask.indices) {
            val m = Pixels.mul(mask[i].toInt() and 0xFF, ceiling)
            if (m == 0) continue
            val existing = alpha(layer.pixels[i])
            if (existing == 0) continue
            layer.pixels[i] = Pixels.withAlpha(layer.pixels[i], Pixels.mul(existing, 255 - m))
            touched = true
        }
        return touched
    }

    /**
     * Erases whatever is *not* within tolerance - "keep only this colour".
     *
     * The inverse is not a novelty. Isolating one ink colour out of a scan, or
     * pulling a logo off a photograph, is the same operation from the other
     * side, and doing it by hand means erasing everything else.
     */
    fun keepOnly(layer: PaintLayer, x: Int, y: Int, tolerance: Int, feather: Int = 1): Boolean {
        if (layer.locked) return false
        val mask = RegionFill.flood(
            layer.pixels, layer.width, layer.height, x, y, tolerance,
            contiguous = false, feather = feather,
        )
        var touched = false
        for (i in mask.indices) {
            val keep = mask[i].toInt() and 0xFF
            val existing = alpha(layer.pixels[i])
            if (existing == 0) continue
            val next = Pixels.mul(existing, keep)
            if (next != existing) touched = true
            layer.pixels[i] = Pixels.withAlpha(layer.pixels[i], next)
        }
        return touched
    }

    /**
     * Erases pure white (or any near-uniform paper colour) from a scan, keeping
     * the ink's anti-aliasing as partial alpha.
     *
     * The specific case of photographing a drawing, which no amount of general
     * background removal handles well because the "background" is everywhere
     * and interleaved with the subject at pixel scale. Here alpha is taken
     * directly from how dark each pixel is relative to the paper, which is
     * exactly what a pencil line's coverage was in the first place.
     */
    fun liftLineArt(layer: PaintLayer, paper: Int, contrast: Float = 1f): Boolean {
        if (layer.locked) return false
        val pr = red(paper)
        val pg = green(paper)
        val pb = blue(paper)
        val paperLuma = (pr * 299 + pg * 587 + pb * 114) / 1000
        if (paperLuma == 0) return false
        var touched = false
        for (i in layer.pixels.indices) {
            val p = layer.pixels[i]
            if (alpha(p) == 0) continue
            val luma = (red(p) * 299 + green(p) * 587 + blue(p) * 114) / 1000
            // How far below the paper this pixel is, as a fraction.
            var coverage = (paperLuma - luma).toFloat() / paperLuma
            coverage = (coverage * contrast).coerceIn(0f, 1f)
            val a = (coverage * 255f).roundToInt()
            if (a != alpha(p)) touched = true
            layer.pixels[i] = Pixels.withAlpha(p, a)
        }
        return touched
    }
}

/** Shared small helper so the cutout and the eraser agree on what "near" is. */
internal fun colourDelta(a: Int, b: Int): Int = abs(red(a) - red(b)) + abs(green(a) - green(b)) + abs(blue(a) - blue(b))
