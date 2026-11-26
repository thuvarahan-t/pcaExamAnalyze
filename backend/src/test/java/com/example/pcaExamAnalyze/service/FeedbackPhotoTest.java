package com.example.pcaExamAnalyze.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackPhotoTest {
    @Test void reencodesAPhotoBelowTheExactStorageCap() throws Exception {
        var source = new BufferedImage(1100, 850, BufferedImage.TYPE_INT_RGB);
        var random = new Random(42);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) source.setRGB(x, y, random.nextInt());
        }
        byte[] png = encode(source, "png");
        assertTrue(png.length > 100_000);
        var result = FeedbackPhoto.compress(upload(png));
        assertTrue(result.bytes().length <= 100_000);
        assertTrue(Math.max(result.width(), result.height()) >= 480);
        assertEquals(0xff, Byte.toUnsignedInt(result.bytes()[0]));
        assertEquals(0xd8, Byte.toUnsignedInt(result.bytes()[1]));
        var decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertEquals(result.width(), decoded.getWidth());
        assertEquals(result.height(), decoded.getHeight());
    }

    @Test void rejectsForgedTypesCorruptionAndDimensionsBeforeStorage() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> FeedbackPhoto.compress(upload(new byte[]{1, 2, 3})));
        assertThrows(IllegalArgumentException.class, () -> FeedbackPhoto.compress(upload(new byte[5 * 1024 * 1024 + 1])));
        var source = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        byte[] gif = encode(source, "gif");
        assertThrows(IllegalArgumentException.class, () -> FeedbackPhoto.compress(upload(gif)));
        byte[] jpeg = encode(source, "jpeg");
        assertThrows(IllegalArgumentException.class, () -> FeedbackPhoto.compress(upload(Arrays.copyOf(jpeg, jpeg.length - 22))));
        byte[] png = encode(source, "png");
        var header = ByteBuffer.wrap(png);
        header.putInt(16, 5000).putInt(20, 5000);
        header.putInt(29, crc(png, 12, 17));
        var failure = assertThrows(IllegalArgumentException.class, () -> FeedbackPhoto.compress(upload(png)));
        assertTrue(failure.getMessage().contains("20 megapixels"));
    }

    @Test void appliesJpegAndPngOrientationToPixelsWithoutKeepingMetadata() throws Exception {
        var source = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 100, 30);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 30, 100, 30);
        graphics.dispose();
        byte[] jpeg = encode(source, "jpeg");
        byte[] png = encode(source, "png");
        byte[] tiff = tiffExif(6);
        byte[] exifIdentifier = {'E', 'x', 'i', 'f', 0, 0};
        byte[] jpegMarker = ByteBuffer.allocate(4 + exifIdentifier.length + tiff.length)
            .put((byte) 0xff).put((byte) 0xe1).putShort((short) (2 + exifIdentifier.length + tiff.length))
            .put(exifIdentifier).put(tiff).array();
        byte[] pngMarker = ByteBuffer.allocate(12 + tiff.length).putInt(tiff.length).putInt(0x65584966).put(tiff).array();
        ByteBuffer.wrap(pngMarker).putInt(8 + tiff.length, crc(pngMarker, 4, 4 + tiff.length));

        for (byte[] original : new byte[][]{insert(jpeg, 20, jpegMarker), insert(png, 33, pngMarker)}) {
            var result = FeedbackPhoto.compress(upload(original));
            assertEquals(60, result.width());
            assertEquals(100, result.height());
            var decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
            assertTrue(new Color(decoded.getRGB(10, 50)).getBlue() > 200);
            assertTrue(new Color(decoded.getRGB(50, 50)).getRed() > 200);
            String stored = new String(result.bytes(), StandardCharsets.ISO_8859_1);
            assertFalse(stored.contains("Exif"));
            assertFalse(stored.contains("private-metadata"));
        }
    }

    private static MockMultipartFile upload(byte[] data) {
        return new MockMultipartFile("photo", "photo.jpg", "image/jpeg", data);
    }

    private static byte[] encode(BufferedImage image, String format) throws Exception {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private static byte[] tiffExif(int orientation) {
        byte[] extra = "private-metadata".getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(26 + extra.length).putShort((short) 0x4d4d).putShort((short) 42).putInt(8)
            .putShort((short) 1).putShort((short) 0x112).putShort((short) 3).putInt(1)
            .putShort((short) orientation).putShort((short) 0).putInt(0).put(extra).array();
    }

    private static byte[] insert(byte[] bytes, int offset, byte[] metadata) {
        return ByteBuffer.allocate(bytes.length + metadata.length).put(bytes, 0, offset).put(metadata)
            .put(bytes, offset, bytes.length - offset).array();
    }

    private static int crc(byte[] bytes, int offset, int count) {
        var crc = new CRC32();
        crc.update(bytes, offset, count);
        return (int) crc.getValue();
    }
}
