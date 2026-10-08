import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Reads the supplied launcher artwork and reports everything the icon generator
 * needs to know: the background colour, whether that background is uniform, the
 * bounding box of the drawn artwork, and the exact colours of the design's parts.
 *
 * <p>Run with: {@code java tools/IconProbe.java icon.png}</p>
 */
public final class IconProbe {

    public static void main(String[] args) throws Exception {
        File file = new File(args.length > 0 ? args[0] : "icon.png");
        BufferedImage image = ImageIO.read(file);
        int w = image.getWidth();
        int h = image.getHeight();
        System.out.println("image " + w + "x" + h + " type=" + image.getType());

        // ---------------------------------------------------------- background
        // The four corners and the edge midpoints decide what "empty" means.
        int[][] probes = {
                {0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1},
                {w / 2, 0}, {w / 2, h - 1}, {0, h / 2}, {w - 1, h / 2},
                {w / 2, h / 2},
        };
        String[] names = {
                "TL", "TR", "BL", "BR", "TOP", "BOTTOM", "LEFT", "RIGHT", "CENTER"
        };
        System.out.println("\n-- probes --");
        for (int i = 0; i < probes.length; i++) {
            int rgb = image.getRGB(probes[i][0], probes[i][1]);
            System.out.printf("%-7s (%4d,%4d) %s  %s%n", names[i], probes[i][0],
                    probes[i][1], hex(rgb), rgb(rgb));
        }

        // Walk the border to see how uniform the background really is: a subtle
        // gradient would leave a visible seam once the art is pasted on a flat
        // colour, so this is worth knowing before choosing that approach.
        int minR = 255, maxR = 0, minG = 255, maxG = 0, minB = 255, maxB = 0;
        for (int x = 0; x < w; x++) {
            int[] two = {image.getRGB(x, 0), image.getRGB(x, h - 1)};
            for (int rgb : two) {
                minR = Math.min(minR, (rgb >> 16) & 0xFF); maxR = Math.max(maxR, (rgb >> 16) & 0xFF);
                minG = Math.min(minG, (rgb >> 8) & 0xFF);  maxG = Math.max(maxG, (rgb >> 8) & 0xFF);
                minB = Math.min(minB, rgb & 0xFF);         maxB = Math.max(maxB, rgb & 0xFF);
            }
        }
        for (int y = 0; y < h; y++) {
            int[] two = {image.getRGB(0, y), image.getRGB(w - 1, y)};
            for (int rgb : two) {
                minR = Math.min(minR, (rgb >> 16) & 0xFF); maxR = Math.max(maxR, (rgb >> 16) & 0xFF);
                minG = Math.min(minG, (rgb >> 8) & 0xFF);  maxG = Math.max(maxG, (rgb >> 8) & 0xFF);
                minB = Math.min(minB, rgb & 0xFF);         maxB = Math.max(maxB, rgb & 0xFF);
            }
        }
        System.out.printf("border R[%d..%d] G[%d..%d] B[%d..%d]  spread=%d%n",
                minR, maxR, minG, maxG, minB, maxB,
                Math.max(maxR - minR, Math.max(maxG - minG, maxB - minB)));

        int bg = image.getRGB(0, 0);

        // ------------------------------------------------------- artwork bbox
        // "Different from the corner" at a tolerance well above the background's
        // own noise but well below the faintest part of the drawing.
        int tolerance = 10;
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (distance(image.getRGB(x, y), bg) > tolerance) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
            }
        }
        System.out.printf("%n-- artwork bbox (tol=%d) --%n", tolerance);
        System.out.printf("x %d..%d   y %d..%d   %dx%d  aspect=%.4f%n",
                x0, x1, y0, y1, x1 - x0 + 1, y1 - y0 + 1,
                (double) (x1 - x0 + 1) / (y1 - y0 + 1));
        System.out.printf("as fraction of canvas: left=%.4f top=%.4f right=%.4f bottom=%.4f%n",
                x0 / (double) w, y0 / (double) h,
                (w - 1 - x1) / (double) w, (h - 1 - y1) / (double) h);

        // ------------------------------------------------- histogram of colour
        Map<Integer, Integer> histogram = new LinkedHashMap<>();
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                histogram.merge(rgb, 1, Integer::sum);
            }
        }
        System.out.println("\n-- dominant colours (sampled every 2px) --");
        histogram.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(16)
                .forEach(entry -> {
                    int rgb = entry.getKey();
                    System.out.printf("  #%06X  %8d  %5.2f%%%n", rgb, entry.getValue(),
                            100.0 * entry.getValue() / (histogram.values().stream()
                                    .mapToInt(Integer::intValue).sum()));
                });

        // ------------------------------------------------------- vertical scan
        // A column through the middle of the calendar shows the band heights.
        System.out.println("\n-- vertical scan at x = " + (w / 2) + " --");
        scan(image, w / 2, 0, h, true, bg);
        System.out.println("\n-- horizontal scan at y = " + (h / 2) + " --");
        scan(image, h / 2, 0, w, false, bg);
    }

    private static void scan(BufferedImage image, int fixed, int from, int to,
            boolean vertical, int bg) {
        int runStart = from;
        int runColor = vertical ? image.getRGB(fixed, from) : image.getRGB(from, fixed);
        for (int i = from + 1; i <= to; i++) {
            int rgb = i == to ? ~runColor
                    : (vertical ? image.getRGB(fixed, i) : image.getRGB(i, fixed));
            if (distance(rgb, runColor) > 12) {
                System.out.printf("  %4d..%-4d  %s%n", runStart, i - 1, hex(runColor));
                runStart = i;
                runColor = rgb;
            }
        }
    }

    private static int distance(int a, int b) {
        return Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)),
                        Math.abs((a & 0xFF) - (b & 0xFF))));
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static String rgb(int rgb) {
        return String.format("rgb(%d,%d,%d)", (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF,
                rgb & 0xFF);
    }
}
