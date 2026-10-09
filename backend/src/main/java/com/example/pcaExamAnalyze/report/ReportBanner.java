package com.example.pcaExamAnalyze.report;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Draws the cover-page artwork (the PDF renderer has no CSS gradients): a deep-blue gradient with soft aurora
 * glows, a fine grid, orbit rings and a few physics symbols - the same look as the website hero.
 */
final class ReportBanner {

    private ReportBanner() {}

    static String dataUri(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(0x060a22), width, height, new Color(0x1636c9)));
                g.fillRect(0, 0, width, height);

                glow(g, (int) (width * 0.12), (int) (height * 0.10), (int) (width * 0.42), new Color(0x5b4bff), 0.55f);
                glow(g, (int) (width * 0.92), (int) (height * 0.30), (int) (width * 0.46), new Color(0x0ea5e9), 0.50f);
                glow(g, (int) (width * 0.55), (int) (height * 1.05), (int) (width * 0.40), new Color(0x22d3ee), 0.35f);

                // fine grid, fading to the right
                g.setStroke(new BasicStroke(1f));
                int step = Math.max(24, width / 28);
                for (int x = 0; x < width; x += step) {
                    g.setColor(new Color(255, 255, 255, 14));
                    g.drawLine(x, 0, x, height);
                }
                for (int y = 0; y < height; y += step) {
                    g.setColor(new Color(255, 255, 255, 14));
                    g.drawLine(0, y, width, y);
                }

                // orbit rings
                g.setStroke(new BasicStroke(2f));
                int cx = (int) (width * 0.80), cy = (int) (height * 0.42);
                for (int i = 1; i <= 6; i++) {
                    int r = i * (height / 7);
                    g.setColor(new Color(255, 255, 255, 26 - i * 2));
                    g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0));
                }
                g.setColor(new Color(103, 232, 249, 190));
                g.fill(new Ellipse2D.Double(cx + height / 7.0 * 2 - 8, cy - 8, 16, 16));
                g.setColor(new Color(165, 180, 252, 170));
                g.fill(new Ellipse2D.Double(cx - height / 7.0 * 4 - 6, cy + height / 7.0 * 1 - 6, 12, 12));

                // symbols
                g.setFont(new Font("SansSerif", Font.BOLD, Math.max(26, height / 12)));
                symbol(g, "E = mc²", (int) (width * 0.60), (int) (height * 0.13));
                symbol(g, "F = ma", (int) (width * 0.70), (int) (height * 0.86));
                symbol(g, "λ", (int) (width * 0.93), (int) (height * 0.16));
                symbol(g, "Δ", (int) (width * 0.50), (int) (height * 0.70));
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException ex) {
            return "";
        }
    }

    private static void glow(Graphics2D g, int cx, int cy, int radius, Color color, float alpha) {
        float[] fractions = {0f, 1f};
        Color[] colors = {new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255)),
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)};
        g.setPaint(new RadialGradientPaint(cx, cy, radius, fractions, colors));
        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0));
    }

    private static void symbol(Graphics2D g, String text, int x, int y) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(255, 255, 255, 70));
        g.drawString(text, x, y);
    }
}
