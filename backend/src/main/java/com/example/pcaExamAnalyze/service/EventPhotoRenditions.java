package com.example.pcaExamAnalyze.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Builds the two web sizes from one downsampled decode of the preserved upload. */
record EventPhotoRenditions(byte[] hero, byte[] thumb) {
    private static final int HERO_EDGE = 920;
    private static final int THUMB_EDGE = 144;

    static EventPhotoRenditions create(byte[] original) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported event photo");
            var reader = readers.next();
            try {
                reader.setInput(input, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int orientation = PhotoOrientation.jpeg(reader);
                int sample = Math.max(1, Math.max(width, height) / HERO_EDGE);
                var params = reader.getDefaultReadParam();
                params.setSourceSubsampling(sample, sample, 0, 0);
                var source = reader.read(0, params);
                try {
                    var hero = resize(source, width, height, HERO_EDGE);
                    var thumb = resize(hero, width, height, THUMB_EDGE);
                    try {
                        return new EventPhotoRenditions(jpeg(hero, 0.64f, orientation), jpeg(thumb, 0.58f, orientation));
                    } finally {
                        hero.flush();
                        thumb.flush();
                    }
                } finally {
                    source.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage resize(BufferedImage input, int width, int height, int maxEdge) {
        double scale = Math.min(1.0, maxEdge / (double) Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        var result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        var graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(input, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static byte[] jpeg(BufferedImage image, float quality, int orientation) throws IOException {
        byte[] encoded = PhotoJpeg.encode(image, quality);
        if (orientation == 1) return encoded;

        // Carry only the orientation tag into the web JPEG, without location or camera details.
        byte[] marker = {
            (byte) 0xff, (byte) 0xe1, 0, 34, 'E', 'x', 'i', 'f', 0, 0,
            'M', 'M', 0, 42, 0, 0, 0, 8,
            0, 1, 1, 18, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, 0, 0, 0, 0
        };
        var oriented = new ByteArrayOutputStream(encoded.length + marker.length);
        // Keep the encoder's JFIF APP0 section first when present.
        int markerOffset = encoded[2] == (byte) 0xff && encoded[3] == (byte) 0xe0
            ? 4 + Short.toUnsignedInt(ByteBuffer.wrap(encoded).getShort(4)) : 2;
        oriented.write(encoded, 0, markerOffset);
        oriented.writeBytes(marker);
        oriented.write(encoded, markerOffset, encoded.length - markerOffset);
        return oriented.toByteArray();
    }
}
