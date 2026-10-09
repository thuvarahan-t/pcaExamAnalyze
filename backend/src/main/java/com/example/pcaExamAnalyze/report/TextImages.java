package com.example.pcaExamAnalyze.report;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedString;
import java.util.Base64;
import java.util.Locale;

/**
 * Text that the PDF renderer cannot draw correctly (Tamil needs OpenType shaping, which openhtmltopdf lacks) is
 * rendered to a small transparent PNG with Java2D, whose text layout does shape Tamil, and embedded as an image.
 * Only strings that contain Tamil characters are converted; everything else stays real, selectable text.
 */
final class TextImages {

    private TextImages() {}

    private static final int SCALE = 4; // pixels per point: crisp even when zoomed in
    private static volatile Font[] fonts; // 0 latin regular, 1 latin bold, 2 tamil regular, 3 tamil bold

    static boolean hasTamil(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0B80 && c <= 0x0BFF) return true;
        }
        return false;
    }

    /** Returns null when the text has no Tamil (render it as normal text) or cannot be drawn. */
    static ExamReport.Img render(String text, boolean bold, double sizePt, Color color) {
        if (!hasTamil(text)) return null;
        try {
            Font[] f = loadFonts();
            Font latin = f[bold ? 1 : 0].deriveFont((float) (sizePt * SCALE));
            Font tamil = f[bold ? 3 : 2].deriveFont((float) (sizePt * SCALE));

            AttributedString attributed = new AttributedString(text);
            // Tamil letters use the Tamil font; other characters use Inter. Spaces follow the letter before them.
            boolean[] tam = new boolean[text.length()];
            for (int i = 0; i < tam.length; i++) {
                char c = text.charAt(i);
                tam[i] = isTamilChar(c) || c == ' ' && i > 0 && tam[i - 1];
            }
            int start = 0;
            while (start < tam.length) {
                int end = start + 1;
                while (end < tam.length && tam[end] == tam[start]) end++;
                attributed.addAttribute(TextAttribute.FONT, tam[start] ? tamil : latin, start, end);
                start = end;
            }
            FontRenderContext frc = new FontRenderContext(null, true, true);
            TextLayout layout = new TextLayout(attributed.getIterator(), frc);
            Rectangle2D bounds = layout.getBounds();
            int width = (int) Math.ceil(layout.getAdvance()) + 4;
            int ascent = (int) Math.ceil(layout.getAscent()), descent = (int) Math.ceil(layout.getDescent());
            int height = ascent + descent + 2;

            BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.setColor(color);
                layout.draw(g, 2, ascent + 1);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            double heightPt = height / (double) SCALE;
            return new ExamReport.Img("data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()),
                    String.format(Locale.ROOT, "%.2f", heightPt));
        } catch (IOException | FontFormatException | RuntimeException ex) {
            return null;
        }
    }

    private static boolean isTamilChar(char c) {
        return c >= 0x0B80 && c <= 0x0BFF || c == 0x200C || c == 0x200D;
    }

    private static Font[] loadFonts() throws IOException, FontFormatException {
        Font[] cached = fonts;
        if (cached != null) return cached;
        synchronized (TextImages.class) {
            if (fonts == null) {
                fonts = new Font[]{load("Inter-400.ttf"), load("Inter-700.ttf"), load("NotoSansTamil-400.ttf"), load("NotoSansTamil-400.ttf").deriveFont(Font.BOLD)};
            }
            return fonts;
        }
    }

    private static Font load(String file) throws IOException, FontFormatException {
        try (InputStream in = TextImages.class.getResourceAsStream("/report/fonts/" + file)) {
            if (in == null) throw new IOException("Missing font " + file);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }
}
