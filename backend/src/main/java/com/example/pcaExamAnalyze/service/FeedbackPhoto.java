package com.example.pcaExamAnalyze.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;

record FeedbackPhoto(byte[] bytes, int width, int height) {
    static final String CONTENT_TYPE = "image/jpeg";
    static final int MAX_STORED_BYTES = 100_000;
    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final int MAX_INPUT_EDGE = 12_000;
    private static final long MAX_INPUT_PIXELS = 20_000_000;
    private static final int[] EDGES = {1200, 960, 768, 600, 480};
    private static final float[] QUALITIES = {0.82f, 0.70f, 0.58f, 0.50f};
    private static final String INVALID_PHOTO = "Select a valid JPEG or PNG photo, or send your feedback without a photo.";

    static FeedbackPhoto compress(MultipartFile file) {
        if (file.getSize() > MAX_UPLOAD_BYTES)
            throw new IllegalArgumentException("The photo must be 5 MB or smaller. Select another photo or send feedback without it.");
        try {
            return decodeAndCompress(file.getBytes());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException(INVALID_PHOTO, exception);
        }
    }

    private static FeedbackPhoto decodeAndCompress(byte[] bytes) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException(INVALID_PHOTO);
            var reader = readers.next();
            try {
                boolean jpeg = "jpeg".equalsIgnoreCase(reader.getFormatName());
                if (!jpeg && !"png".equalsIgnoreCase(reader.getFormatName()))
                    throw new IllegalArgumentException(INVALID_PHOTO);
                reader.setInput(stream, true, !jpeg);
                reader.addIIOReadWarningListener((source, warning) -> { throw new IllegalArgumentException(INVALID_PHOTO); });
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int edge = Math.max(width, height);
                if (width <= 0 || height <= 0 || edge > MAX_INPUT_EDGE || (long) width * height > MAX_INPUT_PIXELS)
                    throw new IllegalArgumentException("The photo is too large. Choose one with up to 20 megapixels and 12,000 pixels per side.");

                int orientation = jpeg ? PhotoOrientation.jpeg(reader) : PhotoOrientation.png(bytes);
                int sample = Math.max(1, edge / EDGES[0]);
                var params = reader.getDefaultReadParam();
                params.setSourceSubsampling(sample, sample, 0, 0);
                var source = reader.read(0, params);
                try {
                    return encode(source, orientation);
                } finally {
                    source.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static FeedbackPhoto encode(BufferedImage source, int orientation) throws IOException {
        int sourceEdge = Math.max(source.getWidth(), source.getHeight());
        int lastEdge = -1;
        for (int maxEdge : EDGES) {
            int edge = Math.min(maxEdge, sourceEdge);
            if (edge == lastEdge) continue;
            lastEdge = edge;
            var output = resize(source, orientation, edge / (double) sourceEdge);
            try {
                for (float quality : QUALITIES) {
                    byte[] encoded = PhotoJpeg.encode(output, quality);
                    if (encoded.length <= MAX_STORED_BYTES)
                        return new FeedbackPhoto(encoded, output.getWidth(), output.getHeight());
                }
            } finally {
                output.flush();
            }
        }
        throw new IllegalArgumentException("This photo could not fit the 100 KB limit. Select another photo or send feedback without it.");
    }

    private static BufferedImage resize(BufferedImage source, int orientation, double scale) {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swapAxes = orientation >= 5;
        int orientedWidth = swapAxes ? height : width;
        int orientedHeight = swapAxes ? width : height;
        int targetWidth = Math.max(1, (int) Math.round(orientedWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(orientedHeight * scale));
        var output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        var graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.scale(targetWidth / (double) orientedWidth, targetHeight / (double) orientedHeight);
            var transform = switch (orientation) {
                case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
                case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
                case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
                case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
                case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
                case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
                case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
                default -> new AffineTransform();
            };
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }
}
