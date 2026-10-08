import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Builds every launcher-icon resource the app needs from one source artwork.
 *
 * <p>Produces, for each density:</p>
 * <ul>
 *   <li>{@code ic_launcher_background.png} -- the adaptive icon's background layer,
 *       a 108dp canvas that is entirely background colour;</li>
 *   <li>{@code ic_launcher_foreground.png} -- the adaptive icon's foreground layer,
 *       a 108dp canvas holding the artwork scaled into the safe zone so that no
 *       launcher mask can clip it;</li>
 *   <li>{@code ic_launcher.png} and {@code ic_launcher_round.png} -- the legacy
 *       icons, which are their own shape because nothing masks them.</li>
 * </ul>
 *
 * <p>It also writes a contact sheet of the adaptive icon under the masks real
 * launchers use, which is the only way to see whether the safe-zone maths was
 * actually right.</p>
 *
 * <p>Usage: {@code java tools/IconForge.java <source.png> <resDir> <previewDir>}</p>
 */
public final class IconForge {

    /** Adaptive icons are always laid out on a 108dp canvas. */
    private static final float ADAPTIVE_CANVAS_DP = 108f;
    /**
     * Diameter of the circle a launcher mask is guaranteed to show, in the same
     * canvas units. Anything inside it survives every mask, including a circle.
     */
    private static final float SAFE_DIAMETER_DP = 66f;
    /** The central square a launcher actually masks and displays. */
    private static final float VIEWPORT_DP = 72f;
    /**
     * How much of the safe circle's radius the artwork's outermost ink may use.
     *
     * <p>1.0 puts the drawing exactly on the guaranteed-visible circle: nothing
     * any launcher draws can clip it. Raising it trades clipping risk on an
     * aggressive circular mask for a bolder icon, and the contact sheet is what
     * decides whether that trade is worth taking.</p>
     */
    private static final float SAFE_RADIUS_FILL = 1f;
    /** Legacy icons are 48dp on the baseline density. */
    private static final float LEGACY_CANVAS_DP = 48f;

    private static final String[] DENSITIES =
            {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    /**
     * Density multipliers against the baseline. hdpi is 1.5x, not 2x -- a whole
     * number array here silently produces icons 33% too large on three of the
     * five densities, which only shows up as a blurry icon on a real phone.
     */
    private static final float[] DENSITY_SCALE = {1f, 1.5f, 2f, 3f, 4f};

    // Brand colours, sampled from the supplied artwork. The backdrop is a soft
    // sage, deliberately a step darker than the calendar's own cream body: with a
    // near-white ground the body has nothing to separate from and the whole icon
    // reads as one pale blob at launcher sizes.
    private static final Color BACKDROP_TOP = new Color(0xE6F0EA);
    private static final Color BACKDROP_BOTTOM = new Color(0xD2E3DB);

    /** How much of a legacy round tile's radius the artwork's ink may use.     *
     * <p>The tile is a circle and the artwork is fitted to a circle inside it, so
     * this is a straight margin: 0.80 leaves a fifth of the radius as breathing
     * room, which is what stops a circular icon looking like it is bursting out of
     * its own edge.</p>
     */
    private static final float LEGACY_ROUND_FILL = 0.80f;
    /** The same for a rounded-square legacy tile, which can afford a little more. */
    private static final float LEGACY_SQUARE_FILL = 0.86f;
    /** Corner radius of the legacy tile, as a fraction of its size. */
    private static final float LEGACY_CORNER_FRACTION = 0.24f;

    /**
     * Luminance window the monochrome layer's silhouette is cut with.
     *
     * <p>The themed icon is one flat colour whose alpha is the whole drawing, so
     * the layer has to be a two-tone reduction of a soft-shaded illustration. The
     * window sits between the pale sage of the timetable's empty cells and the
     * cream of the calendar's page: everything brighter than the window stays, so
     * the page becomes the shape and the cells, the header band and the clock's
     * ring are punched out of it as holes. Thresholding at the source resolution
     * and scaling afterwards is what keeps those holes from filling in.</p>
     */
    private static final double MONO_LOW = 0.855;
    private static final double MONO_HIGH = 0.925;

    public static void main(String[] args) throws Exception {
        File source = new File(args[0]);
        File res = new File(args[1]);
        File previews = new File(args[2]);

        BufferedImage full = ImageIO.read(source);
        int background = full.getRGB(0, 0);
        int[] box = artworkBox(full, background, 6);
        // A few pixels of slack so the artwork's own soft shadow is not sliced off
        // at the bounding box, which would leave a visible straight edge.
        int pad = Math.round(Math.min(full.getWidth(), full.getHeight()) * 0.010f);
        BufferedImage art = full.getSubimage(
                Math.max(0, box[0] - pad), Math.max(0, box[1] - pad),
                Math.min(full.getWidth() - Math.max(0, box[0] - pad), box[2] - box[0] + 1 + 2 * pad),
                Math.min(full.getHeight() - Math.max(0, box[1] - pad), box[3] - box[1] + 1 + 2 * pad));
        System.out.printf("source %dx%d, artwork %dx%d cropped to %dx%d%n",
                full.getWidth(), full.getHeight(), box[2] - box[0] + 1,
                box[3] - box[1] + 1, art.getWidth(), art.getHeight());

        // Size the artwork by its real ink radius rather than by its bounding box.
        // The box's corners are empty on this design -- the calendar's corners are
        // rounded and the artist left a soft shadow around everything -- so fitting
        // the box's diagonal inside the safe circle throws away about a third of
        // the icon's size for nothing.
        double inkRadius = inkRadius(art, 40);
        double boxHalfDiagonal = Math.hypot(art.getWidth() / 2.0, art.getHeight() / 2.0);
        System.out.printf("ink radius %.1fpx, bounding-box half-diagonal %.1fpx (%.0f%%)%n",
                inkRadius, boxHalfDiagonal, 100.0 * inkRadius / boxHalfDiagonal);
        float allowedRadiusDp = SAFE_DIAMETER_DP / 2f * SAFE_RADIUS_FILL;
        float dpPerPixel = (float) (allowedRadiusDp / inkRadius);
        float artWidthDp = art.getWidth() * dpPerPixel;
        float artHeightDp = art.getHeight() * dpPerPixel;
        System.out.printf("adaptive foreground: %.2f x %.2f dp inside a %.0fdp canvas "
                        + "(%.1f%% of the %.0fdp masked viewport)%n",
                artWidthDp, artHeightDp, ADAPTIVE_CANVAS_DP,
                100f * artWidthDp / VIEWPORT_DP, VIEWPORT_DP);

        for (int i = 0; i < DENSITIES.length; i++) {
            float scale = DENSITY_SCALE[i];
            File dir = new File(res, "mipmap-" + DENSITIES[i]);
            dir.mkdirs();
            int canvas = Math.round(ADAPTIVE_CANVAS_DP * scale);
            int legacy = Math.round(LEGACY_CANVAS_DP * scale);

            write(adaptiveBackground(canvas), new File(dir, "ic_launcher_background.png"));
            write(adaptiveForeground(art, canvas, artWidthDp * scale, artHeightDp * scale),
                    new File(dir, "ic_launcher_foreground.png"));
            write(adaptiveForeground(monochromeArt(art), canvas,
                            artWidthDp * scale, artHeightDp * scale),
                    new File(dir, "ic_launcher_monochrome.png"));
            write(legacyIcon(art, legacy, false), new File(dir, "ic_launcher.png"));
            write(legacyIcon(art, legacy, true), new File(dir, "ic_launcher_round.png"));
            System.out.printf("  %-8s adaptive %3dpx  legacy %3dpx%n",
                    DENSITIES[i], canvas, legacy);
        }

        previews.mkdirs();
        contactSheet(art, artWidthDp, artHeightDp, new File(previews, "icon-masks.png"));
        System.out.println("preview written to " + new File(previews, "icon-masks.png"));
    }

    // ------------------------------------------------------------------ layers

    /** The adaptive background: a near-flat sage wash, light enough to stay quiet. */
    private static BufferedImage adaptiveBackground(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(image);
        g.setPaint(new GradientPaint(0, 0, BACKDROP_TOP, 0, size, BACKDROP_BOTTOM));
        g.fillRect(0, 0, size, size);
        g.dispose();
        return image;
    }

    /**
     * The adaptive foreground: the artwork alone, centred on a transparent canvas.
     *
     * <p>Centred on the canvas rather than on its own bounding box, because the
     * mask is centred too -- an off-centre logo shows up immediately under a
     * circular mask.</p>
     */
    private static BufferedImage adaptiveForeground(BufferedImage art, int size,
            float width, float height) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(image);
        int w = Math.round(width);
        int h = Math.round(height);
        g.drawImage(downscale(art, w, h), (size - w) / 2, (size - h) / 2, null);
        g.dispose();
        return image;
    }

    /** A pre-adaptive icon: its own tile, with the artwork sitting on it. */
    private static BufferedImage legacyIcon(BufferedImage art, int size, boolean round) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(image);

        g.setPaint(new GradientPaint(0, 0, BACKDROP_TOP, 0, size, BACKDROP_BOTTOM));
        if (round) {
            g.fill(new Ellipse2D.Float(0, 0, size, size));
        } else {
            float corner = size * LEGACY_CORNER_FRACTION;
            g.fill(new RoundRectangle2D.Float(0, 0, size, size, corner, corner));
        }

        // Same rule as the adaptive icon: fit the artwork's real ink radius inside
        // a circle the tile can always show. A round tile can show the inscribed
        // circle, a square one a little more; nothing here relies on the bounding
        // box's empty corners.
        double ink = inkRadius(art, 40);
        double allowed = size / 2.0 * (round ? LEGACY_ROUND_FILL : LEGACY_SQUARE_FILL);
        double dpPerPixel = allowed / ink;
        int w = (int) Math.round(art.getWidth() * dpPerPixel);
        int h = (int) Math.round(art.getHeight() * dpPerPixel);
        g.drawImage(downscale(art, w, h), (size - w) / 2, (size - h) / 2, null);
        g.dispose();
        return image;
    }

    /**
     * The themed-icon layer: one flat colour whose alpha carries the drawing.
     *
     * <p>Android 13 and later recolour this layer from the wallpaper, so it has to
     * survive losing every hue. What is kept is the light half of the artwork --
     * the calendar's page and the clock's face -- and what is punched out of it is
     * the sage of the empty cells, the deep teal band across the top and the
     * clock's own ring. That leaves a calendar whose grid, header and clock are
     * holes, which is how a single-colour icon has to say all three things.</p>
     *
     * <p>Cut at the source resolution, not after scaling: at 48px a threshold
     * applied to an already-shrunk image finds half-lit pixels everywhere and the
     * punched-out cells fill in.</p>
     */
    private static BufferedImage monochromeArt(BufferedImage art) {
        BufferedImage out = new BufferedImage(art.getWidth(), art.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < art.getHeight(); y++) {
            for (int x = 0; x < art.getWidth(); x++) {
                int argb = art.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                double mask = smoothstep(MONO_LOW, MONO_HIGH, luminance(argb));
                int outAlpha = (int) Math.round(alpha * mask);
                out.setRGB(x, y, (outAlpha << 24) | 0xFFFFFF);
            }
        }
        return out;
    }

    private static double smoothstep(double low, double high, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - low) / (high - low)));
        return t * t * (3 - 2 * t);
    }

    private static double luminance(int argb) {
        return 0.2126 * channel((argb >> 16) & 0xFF)
                + 0.7152 * channel((argb >> 8) & 0xFF)
                + 0.0722 * channel(argb & 0xFF);
    }

    private static double channel(int value) {
        double ratio = value / 255.0;
        return ratio <= 0.03928 ? ratio / 12.92 : Math.pow((ratio + 0.055) / 1.055, 2.4);
    }

    // ---------------------------------------------------------------- previews
    /** The adaptive icon as circle, squircle, rounded-square and full-bleed masks. */
    private static void contactSheet(BufferedImage art, float artWidthDp,
            float artHeightDp, File target) throws Exception {
        int cell = 260;
        int pad = 24;
        String[] labels = {"circle", "squircle", "roundedsquare", "fullbleed"};
        int rowHeight = cell + 46;
        int sheetW = pad + labels.length * (cell + pad);
        int sheetH = pad + rowHeight + pad + cell + 46 + pad + cell + 46 + pad
                + 70 + cell + 46 + pad;
        BufferedImage sheet = new BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = quality(sheet);
        g.setColor(new Color(0x2E3331));
        g.fillRect(0, 0, sheetW, sheetH);

        // Render the composite at high resolution, then sample the part a launcher
        // actually shows. A launcher masks the central 72dp of the 108dp canvas --
        // masking the whole canvas instead makes every icon look far smaller than
        // it does on a home screen, which is exactly the mistake this preview is
        // supposed to catch.
        int hi = 432;
        BufferedImage composite = adaptiveBackground(hi);
        Graphics2D cg = quality(composite);
        int w = Math.round(artWidthDp / ADAPTIVE_CANVAS_DP * hi);
        int h = Math.round(artHeightDp / ADAPTIVE_CANVAS_DP * hi);
        cg.drawImage(downscale(art, w, h), (hi - w) / 2, (hi - h) / 2, null);
        cg.dispose();

        float viewport = 72f;
        int crop = Math.round(hi * viewport / ADAPTIVE_CANVAS_DP);
        int cropOffset = (hi - crop) / 2;
        BufferedImage view = composite.getSubimage(cropOffset, cropOffset, crop, crop);

        g.setColor(Color.WHITE);
        g.drawString("what a launcher shows: the masked central 72dp of the 108dp canvas",
                pad, 18);
        for (int i = 0; i < labels.length; i++) {
            int x = pad + i * (cell + pad);
            int y = pad + 8;
            BufferedImage tile = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = quality(tile);
            tg.setClip(mask(labels[i], cell));
            tg.drawImage(view, 0, 0, cell, cell, null);
            tg.dispose();
            g.drawImage(tile, x, y, null);
            g.setColor(Color.WHITE);
            g.drawString(labels[i], x + 4, y + cell + 22);
            if (labels[i].equals("circle")) {
                // Draw the guaranteed-visible 66dp circle over the 72dp mask that
                // was just applied. Ink that stays inside the cyan ring cannot be
                // clipped by any launcher; ink outside it is a judgement call, and
                // this is where that call gets made.
                float safeInset = (viewport - SAFE_DIAMETER_DP) / 2f / viewport * cell;
                g.setColor(new Color(0x00E5FF));
                g.setStroke(new java.awt.BasicStroke(1.5f));
                g.draw(new Ellipse2D.Float(x + safeInset, y + safeInset,
                        cell - 2 * safeInset, cell - 2 * safeInset));
            }
        }

        // The full canvas with the viewport and the guaranteed-safe circle drawn on
        // it, which is what the artwork's headroom actually looks like.
        int y2 = pad + rowHeight + pad;
        g.setColor(Color.WHITE);
        g.drawString("full canvas, with the masked viewport and the 66dp safe circle",
                pad, y2 - 6);
        int square = Math.min(cell, sheetW - 2 * pad);
        g.setColor(new Color(0x1B1F1D));
        g.fillRect(pad, y2, square, square);
        g.drawImage(composite, pad, y2, square, square, null);
        g.setStroke(new java.awt.BasicStroke(1.5f));
        g.setColor(new Color(0x7FD4C4));
        float inset = (ADAPTIVE_CANVAS_DP - viewport) / 2f / ADAPTIVE_CANVAS_DP * square;
        g.drawRect(Math.round(pad + inset), Math.round(y2 + inset),
                Math.round(square - 2 * inset), Math.round(square - 2 * inset));
        g.setColor(new Color(0x00E5FF));
        float safeInset = (ADAPTIVE_CANVAS_DP - SAFE_DIAMETER_DP) / 2f
                / ADAPTIVE_CANVAS_DP * square;
        g.draw(new Ellipse2D.Float(pad + safeInset, y2 + safeInset,
                square - 2 * safeInset, square - 2 * safeInset));

        // Both legacy icons at the size a xxhdpi phone actually shows them.
        int y3 = y2 + square + 34;
        g.setColor(Color.WHITE);
        g.drawString("legacy icons (144px) and a small-size check (48px)",
                pad, y3 - 6);
        int legacyCell = 144;
        g.drawImage(legacyIcon(art, legacyCell, false), pad, y3, null);
        g.drawImage(legacyIcon(art, legacyCell, true), pad + legacyCell + 16, y3, null);
        int smallY = y3 + legacyCell + 14;
        g.drawImage(legacyIcon(art, 48, false), pad, smallY, null);
        g.drawImage(legacyIcon(art, 48, true), pad + 64, smallY, null);

        // The themed icon, which has to read with every hue taken away.
        int monoY = smallY + 70;
        g.setColor(Color.WHITE);
        g.drawString("themed (monochrome) layer, as the system would tint it",
                pad, monoY - 6);
        BufferedImage monoComposite = new BufferedImage(hi, hi, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = quality(monoComposite);
        mg.setColor(new Color(0xF2F5F3));
        mg.fillRect(0, 0, hi, hi);
        int mw = Math.round(artWidthDp / ADAPTIVE_CANVAS_DP * hi);
        int mh = Math.round(artHeightDp / ADAPTIVE_CANVAS_DP * hi);
        mg.drawImage(downscale(monochromeArt(art), mw, mh), (hi - mw) / 2, (hi - mh) / 2, null);
        mg.dispose();
        BufferedImage monoView = monoComposite.getSubimage(cropOffset, cropOffset, crop, crop);
        BufferedImage monoTile = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mtg = quality(monoTile);
        mtg.setClip(mask("circle", cell));
        mtg.drawImage(monoView, 0, 0, cell, cell, null);
        mtg.dispose();
        g.drawImage(monoTile, pad, monoY, null);
        BufferedImage monoSmall = new BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB);
        Graphics2D msg = quality(monoSmall);
        msg.setClip(mask("circle", 72));
        msg.drawImage(monoView, 0, 0, 72, 72, null);
        msg.dispose();
        g.drawImage(monoSmall, pad + cell + 24, monoY + 40, null);
        g.dispose();
        ImageIO.write(sheet, "png", target);
    }

    private static java.awt.Shape mask(String kind, int size) {
        switch (kind) {
            case "circle":
                return new Ellipse2D.Float(0, 0, size, size);
            case "squircle":
                return squircle(size, 4.2);
            case "roundedsquare":
                float corner = size * 0.20f;
                return new RoundRectangle2D.Float(0, 0, size, size, corner, corner);
            default:
                return new java.awt.Rectangle(0, 0, size, size);
        }
    }

    /** A superellipse |x|^n + |y|^n = 1, which is the shape most masks approximate. */
    private static java.awt.Shape squircle(int size, double n) {
        Path2D.Double path = new Path2D.Double();
        double r = size / 2.0;
        int steps = 720;
        for (int i = 0; i <= steps; i++) {
            double t = 2 * Math.PI * i / steps;
            double cos = Math.cos(t);
            double sin = Math.sin(t);
            double px = r + r * Math.signum(cos) * Math.pow(Math.abs(cos), 2.0 / n);
            double py = r + r * Math.signum(sin) * Math.pow(Math.abs(sin), 2.0 / n);
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.closePath();
        return path;
    }

    // ------------------------------------------------------------------- utils

    /**
     * Scales an image down in halving steps.
     *
     * <p>A single bicubic step from 1200px to 48px samples far too few of the
     * source pixels and the result shimmers; halving repeatedly keeps every
     * source pixel contributing.</p>
     */
    private static BufferedImage downscale(BufferedImage source, int width, int height) {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage current = source;
        while (w / 2 >= width && h / 2 >= height && w > 2 && h > 2) {
            w = Math.max(width, w / 2);
            h = Math.max(height, h / 2);
            current = draw(current, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(current, width, height, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height,
            Object interpolation) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = new Graphics2DConfigurator(out).with(interpolation);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static Graphics2D quality(BufferedImage image) {
        return new Graphics2DConfigurator(image)
                .with(RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    /** Small helper so every surface is configured the same way. */
    private static final class Graphics2DConfigurator {
        private final Graphics2D g;

        Graphics2DConfigurator(BufferedImage image) {
            this.g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                    RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        }

        Graphics2D with(Object interpolation) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            return g;
        }
    }

    private static void write(BufferedImage image, File target) throws Exception {
        ImageIO.write(image, "png", target);
    }

    /** Bounding box of everything that differs from the artwork's background. */
    private static int[] artworkBox(BufferedImage image, int background, int tolerance) {
        int x0 = image.getWidth();
        int y0 = image.getHeight();
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >>> 24) & 0xFF) > 4 && differs(rgb, background, tolerance)) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
            }
        }
        return new int[] {x0, y0, x1, y1};
    }

    private static boolean differs(int a, int b, int tolerance) {
        return Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)),
                        Math.abs((a & 0xFF) - (b & 0xFF)))) > tolerance;
    }

    /**
     * Distance from the artwork's centre to its outermost solid pixel.
     *
     * <p>Semi-transparent pixels below {@code alphaThreshold} are ignored: the
     * artist's drop shadow fades out over a wide area, and counting its faintest
     * tail as "ink" would shrink the icon to make room for something nobody can
     * see.</p>
     */
    private static double inkRadius(BufferedImage art, int alphaThreshold) {
        double centreX = (art.getWidth() - 1) / 2.0;
        double centreY = (art.getHeight() - 1) / 2.0;
        double max = 0;
        for (int y = 0; y < art.getHeight(); y++) {
            for (int x = 0; x < art.getWidth(); x++) {
                if (((art.getRGB(x, y) >>> 24) & 0xFF) >= alphaThreshold) {
                    max = Math.max(max, Math.hypot(x - centreX, y - centreY));
                }
            }
        }
        return max;
    }
}
