package com.example.pcaExamAnalyze.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

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
                int orientation = readOrientation(reader);
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
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var bytes = new ByteArrayOutputStream(); var output = new MemoryCacheImageOutputStream(bytes)) {
            var params = (JPEGImageWriteParam) writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            params.setOptimizeHuffmanTables(true);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
            output.flush();
            byte[] encoded = bytes.toByteArray();
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
        } finally {
            writer.dispose();
        }
    }

    private static int readOrientation(ImageReader reader) throws IOException {
        if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) return 1;
        var metadata = reader.getImageMetadata(0);
        if (metadata == null) return 1;
        var tree = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
        var markers = tree.getElementsByTagName("unknown");
        for (int i = 0; i < markers.getLength(); i++) {
            var node = (IIOMetadataNode) markers.item(i);
            if ("225".equals(node.getAttribute("MarkerTag")) && node.getUserObject() instanceof byte[] marker) {
                int value = exifOrientation(marker);
                if (value != 1) return value;
            }
        }
        return 1;
    }

    private static int exifOrientation(byte[] marker) {
        if (marker.length < 14 || marker[0] != 'E' || marker[1] != 'x' || marker[2] != 'i' || marker[3] != 'f'
                || marker[4] != 0 || marker[5] != 0) return 1;
        var tiff = ByteBuffer.wrap(marker, 6, marker.length - 6).slice();
        short order = tiff.getShort(0);
        if (order == 0x4949) tiff.order(ByteOrder.LITTLE_ENDIAN);
        else if (order != 0x4d4d) return 1;
        int directory = tiff.getInt(4);
        if (tiff.getShort(2) != 42 || directory < 8 || directory > tiff.limit() - 2) return 1;
        int count = Short.toUnsignedInt(tiff.getShort(directory));
        for (int i = 0, entry = directory + 2; i < count && entry <= tiff.limit() - 12; i++, entry += 12) {
            if (tiff.getShort(entry) == 0x112 && tiff.getShort(entry + 2) == 3 && tiff.getInt(entry + 4) == 1) {
                int value = tiff.getShort(entry + 8);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }
}
