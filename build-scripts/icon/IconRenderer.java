import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Renders every form of the application icon from one description of the art.
 *
 * The mark is a GUI panel (the thing the app edits) with a pixel-art pointer
 * over it and a code caret underneath - the two halves of what the designer
 * does: lay a screen out, then turn it into code.
 *
 * Geometry is declared once, on a 0..100 grid, and consumed by two back ends:
 * Java2D (for the desktop PNG/ICO) and an Android VectorDrawable writer. That
 * is the whole point of this file - a launcher icon and a Windows icon that
 * can never drift apart.
 *
 * Usage: {@code java IconRenderer.java <repository-root>}
 */
public final class IconRenderer {

    // --- Palette -----------------------------------------------------------

    private static final Color BACKGROUND = new Color(0x1A, 0x1A, 0x1E);
    private static final Color FOREGROUND = new Color(0xF2, 0xF2, 0xF0);
    private static final Color ACCENT = new Color(0x8B, 0x7C, 0xE8);

    // --- Geometry, on a 0..100 grid ----------------------------------------

    /** Outer window: x, y, w, h, corner radius. */
    private static final double[] PANEL = { 20, 27, 48, 35, 4 };

    /** Body knocked out of the window, leaving a title bar and a frame. */
    private static final double[] PANEL_BODY = { 23.5, 34.5, 41, 24, 0 };

    /** The widgets drawn inside the window body. */
    private static final double[][] WIDGETS = {
        { 26.5, 37.5, 15.5, 15.5, 2 },   // large preview pane
        { 46.0, 37.5, 7.5, 7.0, 1.5 },   // 2x2 widget grid
        { 55.5, 37.5, 7.5, 7.0, 1.5 },
        { 46.0, 46.5, 7.5, 7.0, 1.5 },
        { 55.5, 46.5, 7.5, 7.0, 1.5 },
        { 46.0, 55.5, 17.0, 3.5, 1.5 },  // footer bar
    };

    /**
     * The pointer, declared as a real bitmap so it stays believable pixel art
     * instead of a smooth vector arrow.
     */
    private static final String[] CURSOR = {
        "X.........",
        "XX........",
        "XXX.......",
        "XXXX......",
        "XXXXX.....",
        "XXXXXX....",
        "XXXXXXX...",
        "XXXXXXXX..",
        "XXXXXXXXX.",
        "XXXXXX....",
        "XX.XXX....",
        "X...XXX...",
        ".....XXX..",
        "......XXX.",
        ".......XX.",
    };

    private static final double CURSOR_UNIT = 2.1;
    private static final double CURSOR_X = 55.5;
    private static final double CURSOR_Y = 47.0;

    /** How far the knock-out behind the pointer extends, in grid units. */
    private static final double CURSOR_HALO = 1.6;

    /** The `</>` caret, as polylines. */
    private static final double[][] CARET = {
        { 31, 68, 24, 75, 31, 82 },
        { 42, 66.5, 35, 83.5 },
        { 46, 68, 53, 75, 46, 82 },
    };

    private static final double CARET_STROKE = 3.2;

    /** Bounding box of the whole mark: x, y, w, h - used to centre it. */
    private static final double[] MARK_BOUNDS = { 20, 26.5, 64, 58 };

    // --- Entry point -------------------------------------------------------

    public static void main(String[] args) throws IOException {
        File root = new File(args.length > 0 ? args[0] : ".").getAbsoluteFile();
        File iconDir = new File(root, "assets/icon");
        File androidDrawables = new File(root, "androidApp/src/main/res/drawable");
        mkdirs(iconDir);
        mkdirs(androidDrawables);

        for (int size : new int[] { 1024, 512, 256, 128 }) {
            ImageIO.write(render(size, true), "png", new File(iconDir, "icon-" + size + ".png"));
        }
        ImageIO.write(render(512, true), "png", new File(iconDir, "icon.png"));

        // Windows .ico. Vista and later accept PNG-compressed entries, which
        // keeps the file small even with a 256px frame inside it.
        writeIco(new File(iconDir, "app-icon.ico"), new int[] { 16, 24, 32, 48, 64, 128, 256 });

        write(new File(iconDir, "icon.svg"), svg());
        write(new File(androidDrawables, "ic_launcher_foreground.xml"), androidForeground());
        write(new File(androidDrawables, "ic_launcher_background.xml"), androidBackground());

        System.out.println("Icons written to " + iconDir + " and " + androidDrawables);
    }

    // --- Java2D back end ---------------------------------------------------

    static BufferedImage render(int size, boolean withBackground) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double s = size / 100.0;

        if (withBackground) {
            g.setColor(BACKGROUND);
            g.fill(new RoundRectangle2D.Double(0, 0, size, size, 22 * s, 22 * s));
        }

        g.setColor(FOREGROUND);
        g.fill(roundRect(PANEL, s));

        // Fill the body rather than clearing it: this icon is also rendered
        // standalone (Windows .ico, Linux PNG) where a transparent hole would
        // let the desktop wallpaper show through the middle of the window.
        g.setColor(BACKGROUND);
        g.fill(roundRect(PANEL_BODY, s));

        g.setColor(FOREGROUND);
        for (double[] widget : WIDGETS) {
            g.fill(roundRect(widget, s));
        }

        // Knock a gap out of the panel first so the pointer reads as being on
        // top of the window rather than merged into it.
        g.setColor(BACKGROUND);
        g.fill(cursorPath(s, CURSOR_HALO));
        g.setColor(FOREGROUND);
        g.fill(cursorPath(s, 0));

        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(
            (float) (CARET_STROKE * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] polyline : CARET) {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(polyline[0] * s, polyline[1] * s);
            for (int i = 2; i < polyline.length; i += 2) {
                path.lineTo(polyline[i] * s, polyline[i + 1] * s);
            }
            g.draw(path);
        }

        g.dispose();
        return image;
    }

    private static RoundRectangle2D.Double roundRect(double[] r, double s) {
        return new RoundRectangle2D.Double(
            r[0] * s, r[1] * s, r[2] * s, r[3] * s, r[4] * 2 * s, r[4] * 2 * s);
    }

    private static Path2D.Double cursorPath(double s, double inflate) {
        double u = CURSOR_UNIT * s;
        double ox = CURSOR_X * s - inflate * s;
        double oy = CURSOR_Y * s - inflate * s;
        double grow = inflate * 2 * s;

        Path2D.Double path = new Path2D.Double();
        for (int row = 0; row < CURSOR.length; row++) {
            String line = CURSOR[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) != 'X') {
                    continue;
                }
                path.append(
                    new Rectangle2D.Double(ox + column * u, oy + row * u, u + grow, u + grow),
                    false);
            }
        }
        return path;
    }

    // --- Vector back ends --------------------------------------------------

    /** SVG path data for a rounded rectangle. */
    private static String roundRectPath(double[] r) {
        double x = r[0];
        double y = r[1];
        double w = r[2];
        double h = r[3];
        double radius = Math.min(r[4], Math.min(w, h) / 2);
        if (radius <= 0) {
            return String.format(
                Locale.ROOT, "M%s,%sh%sv%sh%sz", n(x), n(y), n(w), n(h), n(-w));
        }
        StringBuilder path = new StringBuilder();
        path.append("M").append(n(x + radius)).append(",").append(n(y));
        path.append("h").append(n(w - radius * 2));
        path.append("a").append(n(radius)).append(",").append(n(radius))
            .append(" 0 0 1 ").append(n(radius)).append(",").append(n(radius));
        path.append("v").append(n(h - radius * 2));
        path.append("a").append(n(radius)).append(",").append(n(radius))
            .append(" 0 0 1 ").append(n(-radius)).append(",").append(n(radius));
        path.append("h").append(n(-(w - radius * 2)));
        path.append("a").append(n(radius)).append(",").append(n(radius))
            .append(" 0 0 1 ").append(n(-radius)).append(",").append(n(-radius));
        path.append("v").append(n(-(h - radius * 2)));
        path.append("a").append(n(radius)).append(",").append(n(radius))
            .append(" 0 0 1 ").append(n(radius)).append(",").append(n(-radius));
        path.append("z");
        return path.toString();
    }

    /** Path data for the whole window: outer shape with the body cut out. */
    private static String panelPath() {
        // Two subpaths wound the same way; both back ends use even-odd/
        // non-zero fill in a way that leaves the inner rectangle hollow only
        // if it is wound in reverse, so the body is emitted reversed.
        double x = PANEL_BODY[0];
        double y = PANEL_BODY[1];
        double w = PANEL_BODY[2];
        double h = PANEL_BODY[3];
        String reversedBody = String.format(
            Locale.ROOT, "M%s,%sv%sh%sv%sz", n(x), n(y), n(h), n(w), n(-h));
        return roundRectPath(PANEL) + reversedBody;
    }

    private static String cursorPathData(double inflate) {
        StringBuilder path = new StringBuilder();
        double u = CURSOR_UNIT;
        for (int row = 0; row < CURSOR.length; row++) {
            String line = CURSOR[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) != 'X') {
                    continue;
                }
                double x = CURSOR_X - inflate + column * u;
                double y = CURSOR_Y - inflate + row * u;
                double size = u + inflate * 2;
                path.append(String.format(
                    Locale.ROOT, "M%s,%sh%sv%sh%sz", n(x), n(y), n(size), n(size), n(-size)));
            }
        }
        return path.toString();
    }

    private static String caretPathData() {
        StringBuilder path = new StringBuilder();
        for (double[] polyline : CARET) {
            path.append("M").append(n(polyline[0])).append(",").append(n(polyline[1]));
            for (int i = 2; i < polyline.length; i += 2) {
                path.append("L").append(n(polyline[i])).append(",").append(n(polyline[i + 1]));
            }
        }
        return path.toString();
    }

    private static String svg() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\" width=\"512\" height=\"512\">\n"
            + "  <!-- Generated by build-scripts/icon/IconRenderer.java - do not edit by hand. -->\n"
            + "  <path d=\"" + roundRectPath(new double[] { 0, 0, 100, 100, 22 }) + "\" fill=\"" + hex(BACKGROUND) + "\"/>\n"
            + "  <path d=\"" + panelPath() + "\" fill=\"" + hex(FOREGROUND) + "\" fill-rule=\"evenodd\"/>\n"
            + widgetSvg()
            + "  <path d=\"" + cursorPathData(CURSOR_HALO) + "\" fill=\"" + hex(BACKGROUND) + "\"/>\n"
            + "  <path d=\"" + cursorPathData(0) + "\" fill=\"" + hex(FOREGROUND) + "\"/>\n"
            + "  <path d=\"" + caretPathData() + "\" fill=\"none\" stroke=\"" + hex(ACCENT)
            + "\" stroke-width=\"" + n(CARET_STROKE) + "\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n"
            + "</svg>\n";
    }

    private static String widgetSvg() {
        StringBuilder out = new StringBuilder();
        for (double[] widget : WIDGETS) {
            out.append("  <path d=\"").append(roundRectPath(widget))
                .append("\" fill=\"").append(hex(FOREGROUND)).append("\"/>\n");
        }
        return out.toString();
    }

    /**
     * Adaptive-icon foreground.
     *
     * Android crops the 108dp layer aggressively - only the central 72dp is
     * guaranteed visible - so the mark is scaled and centred into that safe
     * zone rather than drawn at its natural size.
     */
    private static String androidForeground() {
        double safeZone = 68.0;
        double scale = safeZone / Math.max(MARK_BOUNDS[2], MARK_BOUNDS[3]);
        double centreX = MARK_BOUNDS[0] + MARK_BOUNDS[2] / 2;
        double centreY = MARK_BOUNDS[1] + MARK_BOUNDS[3] / 2;
        double translateX = 54 - centreX * scale;
        double translateY = 54 - centreY * scale;

        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        out.append("<!-- Generated by build-scripts/icon/IconRenderer.java - do not edit by hand. -->\n");
        out.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        out.append("    android:width=\"108dp\"\n");
        out.append("    android:height=\"108dp\"\n");
        out.append("    android:viewportWidth=\"108\"\n");
        out.append("    android:viewportHeight=\"108\">\n");
        out.append("    <group\n");
        out.append("        android:scaleX=\"").append(n(scale)).append("\"\n");
        out.append("        android:scaleY=\"").append(n(scale)).append("\"\n");
        out.append("        android:translateX=\"").append(n(translateX)).append("\"\n");
        out.append("        android:translateY=\"").append(n(translateY)).append("\">\n");
        out.append(androidPath(panelPath(), hex(FOREGROUND), true));
        for (double[] widget : WIDGETS) {
            out.append(androidPath(roundRectPath(widget), hex(FOREGROUND), false));
        }
        out.append(androidPath(cursorPathData(CURSOR_HALO), hex(BACKGROUND), false));
        out.append(androidPath(cursorPathData(0), hex(FOREGROUND), false));
        out.append("        <path\n");
        out.append("            android:pathData=\"").append(caretPathData()).append("\"\n");
        out.append("            android:strokeColor=\"").append(hex(ACCENT)).append("\"\n");
        out.append("            android:strokeWidth=\"").append(n(CARET_STROKE)).append("\"\n");
        out.append("            android:strokeLineCap=\"round\"\n");
        out.append("            android:strokeLineJoin=\"round\" />\n");
        out.append("    </group>\n");
        out.append("</vector>\n");
        return out.toString();
    }

    private static String androidPath(String data, String fill, boolean evenOdd) {
        return "        <path\n"
            + "            android:pathData=\"" + data + "\"\n"
            + (evenOdd ? "            android:fillType=\"evenOdd\"\n" : "")
            + "            android:fillColor=\"" + fill + "\" />\n";
    }

    private static String androidBackground() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- Generated by build-scripts/icon/IconRenderer.java - do not edit by hand. -->\n"
            + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:width=\"108dp\"\n"
            + "    android:height=\"108dp\"\n"
            + "    android:viewportWidth=\"108\"\n"
            + "    android:viewportHeight=\"108\">\n"
            + "    <path\n"
            + "        android:pathData=\"M0,0h108v108h-108z\"\n"
            + "        android:fillColor=\"" + hex(BACKGROUND) + "\" />\n"
            + "</vector>\n";
    }

    // --- ICO container -----------------------------------------------------

    private static void writeIco(File file, int[] sizes) throws IOException {
        byte[][] payloads = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(render(sizes[i], true), "png", buffer);
            payloads[i] = buffer.toByteArray();
        }

        try (OutputStream out = new FileOutputStream(file);
             DataOutputStream data = new DataOutputStream(out)) {

            writeLE16(data, 0);              // reserved
            writeLE16(data, 1);              // type: icon
            writeLE16(data, sizes.length);

            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int dimension = sizes[i] >= 256 ? 0 : sizes[i]; // 0 encodes 256
                data.writeByte(dimension);
                data.writeByte(dimension);
                data.writeByte(0);           // palette size (0 = truecolour)
                data.writeByte(0);           // reserved
                writeLE16(data, 1);          // colour planes
                writeLE16(data, 32);         // bits per pixel
                writeLE32(data, payloads[i].length);
                writeLE32(data, offset);
                offset += payloads[i].length;
            }
            for (byte[] payload : payloads) {
                data.write(payload);
            }
        }
    }

    // --- Small helpers -----------------------------------------------------

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void mkdirs(File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
    }

    /** Trims trailing zeros so the generated path data stays readable. */
    private static String n(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "");
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static void writeLE16(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
    }

    private static void writeLE32(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
        out.writeByte((value >> 24) & 0xFF);
    }

    private IconRenderer() {}
}
