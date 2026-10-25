package com.example.pcaExamAnalyze.report;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;

/** High-resolution charts embedded in the PDF, with matching text legends in the template. */
final class StudentReportCharts {
    private StudentReportCharts() {}
    record Slice(String label, int count, String percentage, String color) {}
    static List<Slice> slices(int correct, int wrong, int blank, int free) {
        int total = correct + wrong + blank + free;
        var rows = new java.util.ArrayList<Slice>();
        rows.add(slice("Correct", correct, total, "#12836e"));
        rows.add(slice("Incorrect", wrong, total, "#ce5264"));
        rows.add(slice("Unanswered", blank, total, "#8291a7"));
        if (free > 0) rows.add(slice("Free marks", free, total, "#d59a31"));
        return List.copyOf(rows);
    }
    private static Slice slice(String label, int count, int total, String color) {
        return new Slice(label, count, String.format(java.util.Locale.ENGLISH, "%.1f%%", total == 0 ? 0 : count * 100.0 / total), color);
    }
    static String donut(List<Slice> slices) {
        int total = slices.stream().mapToInt(Slice::count).sum();
        BufferedImage image = new BufferedImage(960, 960, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE); g.fillRect(0, 0, 960, 960);
            g.setColor(new Color(235, 240, 247)); g.fill(new Ellipse2D.Double(40, 40, 880, 880));
            double start = 90;
            for (Slice slice : slices) {
                if (slice.count() == 0 || total == 0) continue;
                double angle = slice.count() * 360.0 / total;
                g.setColor(Color.decode(slice.color()));
                g.fill(new Arc2D.Double(40, 40, 880, 880, start, -angle, Arc2D.PIE)); start -= angle;
            }
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(200, 200, 560, 560));
            Font base;
            try (var in = StudentReportCharts.class.getResourceAsStream("/report/fonts/Inter-700.ttf")) {
                base = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception ex) { base = new Font("SansSerif", Font.BOLD, 12); }
            g.setColor(new Color(15, 35, 65)); g.setFont(base.deriveFont(150f));
            String value = Integer.toString(total);
            g.drawString(value, (960 - g.getFontMetrics().stringWidth(value)) / 2, 490);
            g.setFont(base.deriveFont(34f)); g.setColor(new Color(100, 116, 139));
            String caption = "QUESTIONS"; g.drawString(caption, (960 - g.getFontMetrics().stringWidth(caption)) / 2, 560);
        } finally { g.dispose(); }
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (java.io.IOException ex) { throw new IllegalStateException("Could not draw answer chart", ex); }
    }
}
