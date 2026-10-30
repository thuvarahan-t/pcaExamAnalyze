package com.example.pcaExamAnalyze.report;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

/** Prepares uploaded question images for embedding in the PDF: decoded, flattened on white, down-scaled, JPEG-encoded. */
final class ReportImages {

    private ReportImages() {}

    /** Returns a {@code data:image/jpeg;base64,...} URI, or null when the bytes are not a readable raster image. */
    static String toDataUri(byte[] bytes, int maxWidth) {
        if (bytes == null || bytes.length == 0 || maxWidth < 1) return null;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1) return null;
                int maxHeight = maxWidth > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : maxWidth * 2;
                double scale = Math.min(1.0, Math.min(maxWidth / (double) width, maxHeight / (double) height));
                int targetWidth = Math.max(1, (int) Math.round(width * scale));
                int targetHeight = Math.max(1, (int) Math.round(height * scale));

                // Decode downsampled pixels rather than allocating the full source bitmap.
                int sample = Math.max(1, (int) Math.floor(Math.min(
                        width / (double) targetWidth, height / (double) targetHeight)));
                ImageReadParam params = reader.getDefaultReadParam();
                params.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage source = reader.read(0, params);
                if (source == null) return null;
                BufferedImage out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = out.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, targetWidth, targetHeight);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
                } finally {
                    g.dispose();
                    source.flush();
                }
                try {
                    return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg(out, 0.88f));
                } finally {
                    out.flush();
                }
            } finally {
                reader.dispose();
            }
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
