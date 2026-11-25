package com.example.pcaExamAnalyze.service;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackAudioTest {
    @Test void identifiesBrowserAudioFormatsWithoutTrustingMime() {
        assertEquals(FeedbackAudio.WEBM, FeedbackAudio.forContentType("audio/webm;codecs=opus"));
        assertEquals(FeedbackAudio.OGG, FeedbackAudio.forContentType("audio/ogg"));
        assertEquals(FeedbackAudio.MP4, FeedbackAudio.forContentType("audio/mp4;codecs=mp4a.40.2"));
        assertNull(FeedbackAudio.forContentType("video/mp4"));

        byte[] webm = HexFormat.of().parseHex("1a45dfa3874282847765626d18538067ff1654ae6b8dae8b8381028686415f4f5055531f43b6758180");
        assertTrue(FeedbackAudio.WEBM.matches(webm));
        assertFalse(FeedbackAudio.OGG.matches(webm));
        assertFalse(FeedbackAudio.WEBM.matches(Arrays.copyOf(webm, webm.length - 1)));
        webm[26] = 1;
        assertFalse(FeedbackAudio.WEBM.matches(webm), "video track in WebM");

        assertTrue(FeedbackAudio.MP4.matches(mp4("soun")));
        assertFalse(FeedbackAudio.MP4.matches(mp4("vide")));
        assertFalse(FeedbackAudio.MP4.matches(new byte[100]));

        byte[] ogg = oggOpus();
        assertTrue(FeedbackAudio.OGG.matches(ogg));
        assertFalse(FeedbackAudio.OGG.matches(Arrays.copyOf(ogg, 35)));
        ogg[28] = 'X';
        assertFalse(FeedbackAudio.OGG.matches(ogg));
        assertFalse(FeedbackAudio.OGG.matches(new byte[FeedbackAudio.MAX_BYTES + 1]));
    }

    static byte[] oggOpus() {
        return HexFormat.of().parseHex("4f676753000200000000000000000000000000000000000000000113"
            + "4f707573486561640101000080bb0000000000");
    }

    private static byte[] mp4(String handler) {
        return ByteBuffer.allocate(69)
            .putInt(16).put(ascii("ftyp")).put(ascii("isom")).putInt(0)
            .putInt(44).put(ascii("moov")).putInt(36).put(ascii("trak"))
            .putInt(28).put(ascii("mdia")).putInt(20).put(ascii("hdlr"))
            .putLong(0).put(ascii(handler)).putInt(9).put(ascii("mdat")).put((byte) 1).array();
    }

    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
}
