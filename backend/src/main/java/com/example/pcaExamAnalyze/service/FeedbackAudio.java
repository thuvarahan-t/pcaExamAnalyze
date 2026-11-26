package com.example.pcaExamAnalyze.service;

import org.springframework.http.MediaType;

/** Checks the container and audio tracks produced by the browser's MediaRecorder. */
public enum FeedbackAudio {
    WEBM("audio/webm"), OGG("audio/ogg"), MP4("audio/mp4");

    public static final int MAX_BYTES = 2_000_000;

    private final MediaType mediaType;

    FeedbackAudio(String mediaType) {
        this.mediaType = MediaType.parseMediaType(mediaType);
    }

    public MediaType mediaType() { return mediaType; }

    public static FeedbackAudio forContentType(String value) {
        if (value == null) return null;
        MediaType type = MediaType.parseMediaType(value);
        for (var format : values()) {
            if (format.mediaType.equalsTypeAndSubtype(type)) return format;
        }
        return null;
    }

    public boolean matches(byte[] data) {
        if (data.length < 32 || data.length > MAX_BYTES) return false;
        return switch (this) {
            case WEBM -> isWebm(data);
            case OGG -> isOggOpus(data);
            case MP4 -> isAudioMp4(data);
        };
    }

    private static boolean isOggOpus(byte[] data) {
        if (!asciiAt(data, 0, "OggS") || data[4] != 0 || data[5] != 2) return false;
        int segments = Byte.toUnsignedInt(data[26]);
        int body = 27 + segments;
        return segments > 0 && body + 19 <= data.length && Byte.toUnsignedInt(data[27]) == 19
            && asciiAt(data, body, "OpusHead");
    }

    private static boolean isWebm(byte[] data) {
        var header = ebml(data, 0, data.length);
        if (header == null || header.id != 0x1a45dfa3 || header.unknownSize) return false;
        var docType = ebmlChild(data, header, 0x4282);
        if (docType == null || docType.end - docType.body != 4 || !asciiAt(data, docType.body, "webm")) return false;
        var segment = ebml(data, header.end, data.length);
        if (segment == null || segment.id != 0x18538067) return false;
        var tracks = ebmlChild(data, segment, 0x1654ae6b);
        var cluster = ebmlChild(data, segment, 0x1f43b675);
        if (tracks == null || tracks.unknownSize || cluster == null || cluster.end == cluster.body) return false;

        int audioTracks = 0;
        for (int offset = tracks.body; offset < tracks.end;) {
            var track = ebml(data, offset, tracks.end);
            if (track == null || track.unknownSize) return false;
            if (track.id == 0xae) {
                var type = ebmlChild(data, track, 0x83);
                var codec = ebmlChild(data, track, 0x86);
                if (type == null || type.end - type.body != 1 || data[type.body] != 2 || codec == null) return false;
                int codecLength = codec.end - codec.body;
                if (!(codecLength == 6 && asciiAt(data, codec.body, "A_OPUS"))
                        && !(codecLength == 8 && asciiAt(data, codec.body, "A_VORBIS"))) return false;
                audioTracks++;
            }
            offset = track.end;
        }
        return audioTracks == 1;
    }

    private record Element(long id, int body, int end, boolean unknownSize) {}
    private record Vint(long value, int width, boolean unknownSize) {}

    private static Element ebmlChild(byte[] data, Element parent, long id) {
        for (int offset = parent.body; offset < parent.end;) {
            var child = ebml(data, offset, parent.end);
            if (child == null) return null;
            if (child.id == id) return child;
            offset = child.end;
        }
        return null;
    }

    private static Element ebml(byte[] data, int offset, int limit) {
        var id = vint(data, offset, limit, false);
        if (id == null || id.width > 4) return null;
        var size = vint(data, offset + id.width, limit, true);
        if (size == null) return null;
        int body = offset + id.width + size.width;
        if (!size.unknownSize && size.value > limit - body) return null;
        return new Element(id.value, body, size.unknownSize ? limit : body + (int) size.value, size.unknownSize);
    }

    private static Vint vint(byte[] data, int offset, int limit, boolean size) {
        if (offset >= limit || data[offset] == 0) return null;
        int first = Byte.toUnsignedInt(data[offset]);
        int width = Integer.numberOfLeadingZeros(first) - 23;
        if (width > limit - offset) return null;
        long value = size ? first & (0xff >>> width) : first;
        for (int next = offset + 1; next < offset + width; next++) value = (value << 8) | Byte.toUnsignedInt(data[next]);
        return new Vint(value, width, size && value == (1L << (7 * width)) - 1);
    }

    private static boolean isAudioMp4(byte[] data) {
        var fileType = mp4Box(data, 0, data.length);
        if (fileType == null || !asciiAt(data, 4, "ftyp") || fileType.end - fileType.body < 8) return false;
        int tracks = 0;
        boolean hasMedia = false;
        for (int start = fileType.end; start < data.length;) {
            var box = mp4Box(data, start, data.length);
            if (box == null) return false;
            if (asciiAt(data, start + 4, "moov")) {
                int count = mp4AudioTracks(data, box);
                if (count < 0) return false;
                tracks += count;
            }
            hasMedia |= asciiAt(data, start + 4, "mdat") && box.end > box.body;
            start = box.end;
        }
        return tracks == 1 && hasMedia;
    }

    private static int mp4AudioTracks(byte[] data, Box movie) {
        int tracks = 0;
        for (int start = movie.body; start < movie.end;) {
            var track = mp4Box(data, start, movie.end);
            if (track == null) return -1;
            if (asciiAt(data, start + 4, "trak")) {
                var media = mp4Child(data, track, "mdia");
                var handler = media == null ? null : mp4Child(data, media, "hdlr");
                if (handler == null || handler.end - handler.body < 12 || !asciiAt(data, handler.body + 8, "soun")) return -1;
                tracks++;
            }
            start = track.end;
        }
        return tracks;
    }

    private record Box(int body, int end) {}

    private static Box mp4Child(byte[] data, Box parent, String type) {
        for (int start = parent.body; start < parent.end;) {
            var child = mp4Box(data, start, parent.end);
            if (child == null) return null;
            if (asciiAt(data, start + 4, type)) return child;
            start = child.end;
        }
        return null;
    }

    private static Box mp4Box(byte[] data, int offset, int limit) {
        int available = limit - offset;
        if (available < 8) return null;
        long size = unsignedInt(data, offset);
        int header = 8;
        if (size == 1) {
            if (available < 16 || unsignedInt(data, offset + 8) != 0) return null;
            size = unsignedInt(data, offset + 12);
            header = 16;
        } else if (size == 0) {
            size = available;
        }
        return size >= header && size <= available ? new Box(offset + header, offset + (int) size) : null;
    }

    private static long unsignedInt(byte[] data, int offset) {
        long value = 0;
        for (int i = offset; i < offset + 4; i++) value = (value << 8) | Byte.toUnsignedInt(data[i]);
        return value;
    }

    private static boolean asciiAt(byte[] data, int offset, String label) {
        if (data.length - offset < label.length()) return false;
        for (int i = 0; i < label.length(); i++) {
            if (data[offset + i] != label.charAt(i)) return false;
        }
        return true;
    }
}
