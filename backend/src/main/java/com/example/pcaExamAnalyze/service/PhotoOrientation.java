package com.example.pcaExamAnalyze.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;

final class PhotoOrientation {
    private static final int PNG_EXIF = 0x65584966;

    private PhotoOrientation() {}

    static int jpeg(ImageReader reader) throws IOException {
        if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) return 1;
        var metadata = reader.getImageMetadata(0);
        if (metadata == null) return 1;
        var tree = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
        var markers = tree.getElementsByTagName("unknown");
        for (int i = 0; i < markers.getLength(); i++) {
            var node = (IIOMetadataNode) markers.item(i);
            if ("225".equals(node.getAttribute("MarkerTag")) && node.getUserObject() instanceof byte[] marker
                    && marker.length >= 14 && marker[0] == 'E' && marker[1] == 'x' && marker[2] == 'i'
                    && marker[3] == 'f' && marker[4] == 0 && marker[5] == 0) {
                int orientation = tiffOrientation(ByteBuffer.wrap(marker, 6, marker.length - 6).slice());
                if (orientation != 1) return orientation;
            }
        }
        return 1;
    }

    static int png(byte[] data) {
        // PNG eXIf carries the TIFF directory without the JPEG Exif prefix. Skip other chunks
        // directly so the image reader can leave compressed text and profiles untouched.
        var chunks = ByteBuffer.wrap(data);
        chunks.position(8);
        while (chunks.remaining() >= 12) {
            int length = chunks.getInt();
            int type = chunks.getInt();
            if (length < 0 || length > chunks.remaining() - 4) break;
            if (type == PNG_EXIF) return tiffOrientation(chunks.slice(chunks.position(), length));
            chunks.position(chunks.position() + length + 4);
        }
        return 1;
    }

    private static int tiffOrientation(ByteBuffer tiff) {
        if (tiff.remaining() < 8) return 1;
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
