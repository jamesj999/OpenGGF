package com.openggf.game.sonic3k.audio;

import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.data.Rom;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

/**
 * Diagnostic test to trace voice resolution for S3K tracks with
 * known audio issues: Mini-Boss (0x12E) and Knuckles' Theme (0x11F).
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kVoiceResolution {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic3kSmpsLoader loader;

    @Before
    public void setUp() {
        Rom rom = romRule.rom();
        loader = new Sonic3kSmpsLoader(rom);
    }

    @Test
    public void traceMiniBossVoices() {
        System.out.println("=== Mini-Boss (S3) - music ID 0x2E via S3 tables ===");
        AbstractSmpsData data = loader.loadS3Music(0x2E);
        if (data == null) {
            System.out.println("FAILED TO LOAD S3 Mini-Boss");
            return;
        }

        System.out.println("Voice pointer: 0x" + Integer.toHexString(data.getVoicePtr()));
        System.out.println("FM channels: " + data.getChannels());
        System.out.println("PSG channels: " + data.getPsgChannels());

        for (int v = 0; v < 20; v++) {
            byte[] voice = data.getVoice(v);
            if (voice != null) {
                System.out.println("Voice " + v + ": " + hexDump(voice));
            }
        }
    }

    @Test
    public void traceKnucklesVoices() {
        System.out.println("=== Knuckles' Theme (S3) - music ID 0x1F via S3 tables ===");
        AbstractSmpsData data = loader.loadS3Music(0x1F);
        if (data == null) {
            System.out.println("FAILED TO LOAD S3 Knuckles");
            return;
        }

        System.out.println("Voice pointer: 0x" + Integer.toHexString(data.getVoicePtr()));
        System.out.println("FM channels: " + data.getChannels());
        System.out.println("PSG channels: " + data.getPsgChannels());

        for (int v = 0; v < 20; v++) {
            byte[] voice = data.getVoice(v);
            if (voice != null) {
                System.out.println("Voice " + v + ": " + hexDump(voice));
            }
        }
    }

    @Test
    public void compareS3VsSkMiniBoss() {
        System.out.println("=== Comparing Mini-Boss: S&K (0x18) vs S3 (0x2E) ===");

        AbstractSmpsData sk = loader.loadMusic(0x18);
        AbstractSmpsData s3 = loader.loadS3Music(0x2E);

        if (sk == null || s3 == null) {
            System.out.println("Could not load both versions (sk=" + sk + ", s3=" + s3 + ")");
            return;
        }

        System.out.println("S&K voice ptr: 0x" + Integer.toHexString(sk.getVoicePtr()));
        System.out.println("S3  voice ptr: 0x" + Integer.toHexString(s3.getVoicePtr()));

        for (int v = 0; v < 10; v++) {
            byte[] skVoice = sk.getVoice(v);
            byte[] s3Voice = s3.getVoice(v);
            boolean skNull = (skVoice == null);
            boolean s3Null = (s3Voice == null);
            if (skNull && s3Null) continue;

            System.out.println("\nVoice " + v + ":");
            if (!skNull) System.out.println("  S&K: " + hexDump(skVoice));
            else System.out.println("  S&K: null");
            if (!s3Null) System.out.println("  S3:  " + hexDump(s3Voice));
            else System.out.println("  S3:  null");

            if (!skNull && !s3Null) {
                System.out.println("  Match: " + java.util.Arrays.equals(skVoice, s3Voice));
            }
        }
    }

    @Test
    public void compareS3VsSkKnuckles() {
        System.out.println("=== Comparing Knuckles: S&K (0x1F) vs S3 (0x1F via S3 tables) ===");

        AbstractSmpsData sk = loader.loadMusic(0x1F);
        AbstractSmpsData s3 = loader.loadS3Music(0x1F);

        if (sk == null || s3 == null) {
            System.out.println("Could not load both versions (sk=" + sk + ", s3=" + s3 + ")");
            return;
        }

        System.out.println("S&K voice ptr: 0x" + Integer.toHexString(sk.getVoicePtr()));
        System.out.println("S3  voice ptr: 0x" + Integer.toHexString(s3.getVoicePtr()));

        for (int v = 0; v < 10; v++) {
            byte[] skVoice = sk.getVoice(v);
            byte[] s3Voice = s3.getVoice(v);
            boolean skNull = (skVoice == null);
            boolean s3Null = (s3Voice == null);
            if (skNull && s3Null) continue;

            System.out.println("\nVoice " + v + ":");
            if (!skNull) System.out.println("  S&K: " + hexDump(skVoice));
            else System.out.println("  S&K: null");
            if (!s3Null) System.out.println("  S3:  " + hexDump(s3Voice));
            else System.out.println("  S3:  null");

            if (!skNull && !s3Null) {
                System.out.println("  Match: " + java.util.Arrays.equals(skVoice, s3Voice));
            }
        }
    }

    @Test
    public void traceVoiceResolutionPath() {
        // For debugging: check what path voices take through getVoice()
        System.out.println("=== Voice Resolution Path Tracing ===\n");

        // S3 Mini-Boss
        AbstractSmpsData s3mb = loader.loadS3Music(0x2E);
        if (s3mb != null) {
            System.out.println("S3 Mini-Boss voicePtr=0x" + Integer.toHexString(s3mb.getVoicePtr()));
            // Check if voicePtr is in bank range (0x8000+) or global table range (0x17D8)
            int ptr = s3mb.getVoicePtr();
            if (ptr >= 0x8000) {
                System.out.println("  -> Bank-relative voice pointer (0x8000+ range)");
            } else if (ptr >= 0x1300 && ptr < 0x2000) {
                System.out.println("  -> Global instrument table range");
            } else {
                System.out.println("  -> Unexpected range: 0x" + Integer.toHexString(ptr));
            }
        }

        // S3 Knuckles
        AbstractSmpsData s3kn = loader.loadS3Music(0x1F);
        if (s3kn != null) {
            System.out.println("S3 Knuckles voicePtr=0x" + Integer.toHexString(s3kn.getVoicePtr()));
            int ptr = s3kn.getVoicePtr();
            if (ptr >= 0x8000) {
                System.out.println("  -> Bank-relative voice pointer (0x8000+ range)");
            } else if (ptr >= 0x1300 && ptr < 0x2000) {
                System.out.println("  -> Global instrument table range");
            } else {
                System.out.println("  -> Unexpected range: 0x" + Integer.toHexString(ptr));
            }
        }

        // S&K versions for comparison
        AbstractSmpsData skmb = loader.loadMusic(0x18);
        if (skmb != null) {
            System.out.println("S&K Mini-Boss voicePtr=0x" + Integer.toHexString(skmb.getVoicePtr()));
        }
        AbstractSmpsData skkn = loader.loadMusic(0x1F);
        if (skkn != null) {
            System.out.println("S&K Knuckles voicePtr=0x" + Integer.toHexString(skkn.getVoicePtr()));
        }

        // Check a known good track for reference
        AbstractSmpsData aiz1 = loader.loadMusic(0x01);
        if (aiz1 != null) {
            System.out.println("AIZ1 voicePtr=0x" + Integer.toHexString(aiz1.getVoicePtr()));
        }
    }

    private static String hexDump(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }
}
