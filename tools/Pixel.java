import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Prints the colour of named points in a screenshot, so a claim like "the grid
 * uses the new palette" can be checked against pixels instead of against an
 * impression of a downscaled preview.
 *
 * <p>Usage: {@code java tools/Pixel.java shot.png 300,600 400,700 ...}</p>
 */
public final class Pixel {

    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[0]));
        System.out.println(args[0] + "  " + image.getWidth() + "x" + image.getHeight());
        // A 40x40 patch is averaged, because a single pixel on an antialiased edge
        // says nothing about the fill colour behind it.
        for (int i = 1; i < args.length; i++) {
            String[] parts = args[i].split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            int count = 0;
            for (int dy = -20; dy <= 20; dy++) {
                for (int dx = -20; dx <= 20; dx++) {
                    int px = x + dx;
                    int py = y + dy;
                    if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
                        continue;
                    }
                    int rgb = image.getRGB(px, py);
                    sumR += (rgb >> 16) & 0xFF;
                    sumG += (rgb >> 8) & 0xFF;
                    sumB += rgb & 0xFF;
                    count++;
                }
            }
            System.out.printf("(%4d,%4d)  #%02X%02X%02X%n", x, y,
                    sumR / count, sumG / count, sumB / count);
        }
    }
}
