import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Renders the wallpaper that sits behind the editor - four images, one per
 * edition per theme.
 *
 * The scene is the same in all four: a low sun over layered blocky ridges,
 * with a scatter of motes in the air.  What changes is the palette, and that
 * is the point - a glance at the wallpaper should tell you which edition you
 * are working in and whether the app is in light or dark mode, before you have
 * read a single label.
 *
 * Everything is deterministic. Heights come from a fixed hash rather than a
 * random source, so re-running this produces byte-identical files and the
 * artwork never silently changes under a commit that did not intend to touch
 * it.
 *
 * The Compose side draws a simplified version of this same scene as a fallback
 * when the image cannot be loaded - see {@code styles/.../theme/Backdrop.kt}.
 * The two are meant to look like the same place.
 *
 * Usage: {@code java BackdropRenderer.java <repository-root>}
 */
public final class BackdropRenderer {

    /**
     * Output size.
     *
     * 2560x1440 covers a maximised window on the displays this app is used on
     * without the cover-scaling having to upscale. The files land around
     * 1-2 MB each, which is nothing next to a bundled JVM.
     */
    private static final int WIDTH = 2560;
    private static final int HEIGHT = 1440;

    /** One edition-and-theme's colours. */
    private record Palette(
            String name,
            Color skyTop,
            Color skyBottom,
            Color glow,
            Color ridgeFar,
            Color ridgeMid,
            Color ridgeNear,
            Color mote) {
    }

    private static final Palette[] PALETTES = {
            // Java: cool stone and dusk blue, vanilla's emerald as the light.
            new Palette("backdrop-java-dark",
                    new Color(0x14, 0x20, 0x2B), new Color(0x0C, 0x0E, 0x10),
                    new Color(0x54, 0xFB, 0x54), new Color(0x1B, 0x2C, 0x33),
                    new Color(0x12, 0x1E, 0x23), new Color(0x0A, 0x12, 0x16),
                    new Color(0x8C, 0xF3, 0x9A)),
            new Palette("backdrop-java-light",
                    new Color(0xD6, 0xE2, 0xEC), new Color(0xF1, 0xF4, 0xF6),
                    new Color(0x2F, 0x7A, 0x2F), new Color(0xB2, 0xC2, 0xCF),
                    new Color(0x94, 0xA7, 0xB6), new Color(0x74, 0x88, 0x99),
                    new Color(0x3F, 0x8A, 0x50)),
            // Bedrock: warmer violet night, the vivid Pocket green as the light.
            new Palette("backdrop-bedrock-dark",
                    new Color(0x1B, 0x14, 0x30), new Color(0x0A, 0x08, 0x12),
                    new Color(0x5C, 0xE0, 0x5C), new Color(0x24, 0x1B, 0x3B),
                    new Color(0x18, 0x12, 0x2A), new Color(0x0C, 0x08, 0x18),
                    new Color(0x9B, 0xF0, 0xB2)),
            new Palette("backdrop-bedrock-light",
                    new Color(0xE0, 0xE4, 0xD8), new Color(0xF5, 0xF7, 0xF1),
                    new Color(0x2C, 0x6B, 0x2C), new Color(0xC0, 0xC9, 0xB4),
                    new Color(0xA3, 0xAF, 0x94), new Color(0x83, 0x91, 0x75),
                    new Color(0x46, 0x7C, 0x3E)),
    };

    /**
     * Ridge layers, back to front.
     *
     * Each is: baseline as a fraction of height, block width in pixels, how
     * many blocks of extra height the tallest column reaches, and how much of
     * the sky's colour is hazed back over it once it is drawn.
     *
     * The far layer is wide and hazy, the near one narrow and almost black.
     * That spread is what turns three rows of rectangles into distance.
     */
    private static final double[][] RIDGES = {
            { 0.62, 104, 1.9, 0.46 },
            { 0.74, 68, 1.7, 0.22 },
            { 0.88, 44, 1.4, 0.00 },
    };

    public static void main(String[] args) throws IOException {
        File root = new File(args.length > 0 ? args[0] : ".");
        File outputDirectory = new File(root, "assets/backdrop");
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new IOException("Could not create " + outputDirectory);
        }

        for (Palette palette : PALETTES) {
            BufferedImage image = render(palette);
            File target = new File(outputDirectory, palette.name() + ".png");
            ImageIO.write(image, "png", target);
            System.out.println("wrote " + target.getPath() + "  (" + target.length() / 1024 + " KB)");
        }
    }

    private static BufferedImage render(Palette palette) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        drawSky(g, palette);
        drawMotes(g, palette);
        drawGlow(g, palette);
        // Ridges last, over the sun: the far one crops it, which is what puts
        // the light *behind* the landscape instead of floating in front of it.
        for (int layer = 0; layer < RIDGES.length; layer++) {
            drawRidge(g, palette, layer);
        }
        drawVignette(g);

        g.dispose();
        return image;
    }

    private static void drawSky(Graphics2D g, Palette palette) {
        g.setPaint(new GradientPaint(0, 0, palette.skyTop(), 0, HEIGHT, palette.skyBottom()));
        g.fill(new Rectangle2D.Double(0, 0, WIDTH, HEIGHT));
    }

    /** The low sun, setting into the far ridge. */
    private static void drawGlow(Graphics2D g, Palette palette) {
        float cx = WIDTH * 0.27f;
        // Level with the far ridge's tops, so the taller columns crop it and
        // the light reads as coming from behind the landscape.
        float cy = (float) (HEIGHT * 0.47);
        float radius = HEIGHT * 0.78f;
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(cx, cy), radius,
                new float[] { 0f, 0.18f, 0.5f, 1f },
                new Color[] {
                        withAlpha(palette.glow(), 96),
                        withAlpha(palette.glow(), 52),
                        withAlpha(palette.glow(), 16),
                        withAlpha(palette.glow(), 0),
                }));
        g.fill(new Rectangle2D.Double(0, 0, WIDTH, HEIGHT));

        // The disc itself: a Minecraft sun is a square, and drawing it as one
        // is the single cheapest signal that this is a Minecraft tool.
        double size = HEIGHT * 0.085;
        g.setPaint(withAlpha(palette.glow(), 150));
        g.fill(new Rectangle2D.Double(cx - size / 2, cy - size / 2, size, size));
    }

    /**
     * One ridge of square-topped columns.
     *
     * Square on purpose: this is a Minecraft tool, and a smooth mountain
     * silhouette would look like it came from a different application.
     */
    private static void drawRidge(Graphics2D g, Palette palette, int layer) {
        double baseline = HEIGHT * RIDGES[layer][0];
        double step = RIDGES[layer][1];
        double relief = RIDGES[layer][2] * step;
        Color color = switch (layer) {
            case 0 -> palette.ridgeFar();
            case 1 -> palette.ridgeMid();
            default -> palette.ridgeNear();
        };

        int columns = (int) Math.ceil(WIDTH / step) + 1;
        double[] tops = new double[columns];
        for (int i = 0; i < columns; i++) {
            tops[i] = baseline - profile(i, layer) * relief;
        }

        g.setPaint(color);
        for (int i = 0; i < columns; i++) {
            g.fill(new Rectangle2D.Double(i * step, tops[i], step + 1, HEIGHT - tops[i]));
        }

        // A one-block rim along the top edge catches the light and stops the
        // three layers reading as one flat mass.
        g.setPaint(withAlpha(palette.glow(), layer == 2 ? 30 : 18));
        for (int i = 0; i < columns; i++) {
            g.fill(new Rectangle2D.Double(i * step, tops[i], step + 1, Math.max(2, step * 0.07)));
        }

        // Haze the layer back towards the sky. Distance in air is what the eye
        // reads as distance, far more than size is.
        //
        // Painted over the same rectangles rather than as one band across the
        // image: a band leaves a hard horizontal seam wherever it crosses open
        // sky, which is the first thing anyone notices.
        double haze = RIDGES[layer][3];
        if (haze > 0) {
            g.setPaint(withAlpha(palette.skyTop(), (int) (haze * 255)));
            for (int i = 0; i < columns; i++) {
                g.fill(new Rectangle2D.Double(i * step, tops[i], step + 1, HEIGHT - tops[i]));
            }
        }
    }

    /**
     * Height profile of one ridge column, 0..1.
     *
     * Two octaves: a slow one that makes hills and valleys across the whole
     * width, and a fast one that gives each column its own step. One octave
     * alone produces the thing this must not look like - a city skyline.
     */
    private static double profile(int column, int layer) {
        double slow = smoothNoise(column / 5.5 + layer * 31);
        double medium = smoothNoise(column / 2.0 + layer * 71);
        double fast = hash(column + layer * 101);
        return Math.pow(slow * 0.52 + medium * 0.31 + fast * 0.17, 1.2);
    }

    /** Interpolated value noise, so the slow octave has actual slopes. */
    private static double smoothNoise(double position) {
        int index = (int) Math.floor(position);
        double t = position - index;
        // Smoothstep: linear interpolation would give visible creases at every
        // integer, which the flat block tops make very easy to spot.
        double eased = t * t * (3 - 2 * t);
        return hash(index) * (1 - eased) + hash(index + 1) * eased;
    }

    /** Sparse points of light in the air, thinning out towards the ground. */
    private static void drawMotes(Graphics2D g, Palette palette) {
        for (int i = 0; i < 240; i++) {
            double x = hash(i * 7 + 3) * WIDTH;
            double y = Math.pow(hash(i * 13 + 5), 1.6) * HEIGHT * 0.6;
            double size = 2 + hash(i * 17 + 11) * 5;
            int alpha = (int) (28 + hash(i * 19 + 23) * 70);
            g.setPaint(withAlpha(palette.mote(), alpha));
            g.fill(new Rectangle2D.Double(x, y, size, size));
        }
    }

    /** Darkened corners, so panels floating over the middle stay the subject. */
    private static void drawVignette(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(WIDTH / 2f, HEIGHT / 2f),
                Math.max(WIDTH, HEIGHT) * 0.72f,
                new float[] { 0f, 0.6f, 1f },
                new Color[] {
                        new Color(0, 0, 0, 0),
                        new Color(0, 0, 0, 20),
                        new Color(0, 0, 0, 96),
                }));
        g.fill(new Rectangle2D.Double(0, 0, WIDTH, HEIGHT));
    }

    // --- Helpers -----------------------------------------------------------

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, Math.max(0, alpha)));
    }

    /**
     * Deterministic 0..1 from an integer.
     *
     * The same construction the Compose fallback uses, so the two silhouettes
     * come from the same sequence.
     */
    private static double hash(int value) {
        double x = Math.sin(value * 127.1) * 43758.5453;
        return Math.abs(x - Math.floor(x));
    }

    private BackdropRenderer() {
    }
}
