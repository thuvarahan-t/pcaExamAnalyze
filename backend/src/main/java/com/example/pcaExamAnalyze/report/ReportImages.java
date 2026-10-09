package com.example.pcaExamAnalyze.report;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Prepares uploaded question images for embedding in the PDF: decoded, flattened on white, down-scaled, JPEG-encoded. */
final class ReportImages {

    private ReportImages() {}

    /** Returns a {@code data:image/jpeg;base64,...} URI, or null when the bytes are not a readable raster image. */
    static String toDataUri(byte[] bytes, int maxWidth) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) return null;
            int w = source.getWidth(), h = source.getHeight();
            double scale = w > maxWidth ? maxWidth / (double) w : 1.0;
            int tw = Math.max(1, (int) Math.round(w * scale)), th = Math.max(1, (int) Math.round(h * scale));
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, tw, th);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source, 0, 0, tw, th, null);
            } finally {
                g.dispose();
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg(out, 0.88f));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
            stream.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
