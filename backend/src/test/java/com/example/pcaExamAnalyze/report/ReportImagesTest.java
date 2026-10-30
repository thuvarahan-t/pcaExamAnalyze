package com.example.pcaExamAnalyze.report;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ReportImagesTest {
    @Test void largeImageIsDownsampledBeforeOutput() throws Exception {
        var image = new BufferedImage(2400, 1600, BufferedImage.TYPE_INT_RGB);
        try {
            Graphics2D graphics = image.createGraphics();
            try { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 2400, 1600); }
            finally { graphics.dispose(); }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertTrue(ImageIO.write(image, "jpeg", bytes));
            var uri = ReportImages.toDataUri(bytes.toByteArray(), 400);
            assertNotNull(uri);
            assertTrue(uri.startsWith("data:image/jpeg;base64,"));
            var output = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))));
            assertEquals(400, output.getWidth());
            assertEquals(267, output.getHeight());
        } finally { image.flush(); }
    }

    @Test void tallTransparentImageIsCappedAndFlattenedOnWhite() throws Exception {
        var image = new BufferedImage(150, 1200, BufferedImage.TYPE_INT_ARGB);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertTrue(ImageIO.write(image, "png", bytes));
            var uri = ReportImages.toDataUri(bytes.toByteArray(), 200);
            assertNotNull(uri);
            var output = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))));
            assertEquals(400, output.getHeight());
            assertEquals(50, output.getWidth());
            int rgb = output.getRGB(0, 0) & 0xFFFFFF;
            assertEquals(0xFFFFFF, rgb);
        } finally { image.flush(); }
    }

    @Test void invalidImageDoesNotCrashRenderer() {
        assertNull(ReportImages.toDataUri(null, 300));
        assertNull(ReportImages.toDataUri(new byte[]{1, 2, 3}, 300));
        assertNull(ReportImages.toDataUri(new byte[]{1, 2, 3}, 0));
    }
}
