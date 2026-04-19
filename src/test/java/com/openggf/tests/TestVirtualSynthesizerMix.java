package com.openggf.tests;

import com.openggf.audio.synth.VirtualSynthesizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class TestVirtualSynthesizerMix {

    @Test
    public void mixedFmAndPsgOutputRemainsBitExact() {
        VirtualSynthesizer synth = new VirtualSynthesizer();
        configurePsg(synth);
        configureFm(synth);

        short[] buffer = new short[32];
        synth.render(buffer);

        assertArrayEquals(new short[] {
                378, 378, 908, 908, 1373, 1373, 1822, 1822,
                2346, 2346, 2829, 2829, 3287, 3287, 3744, 3744,
                6335, 6335, 6573, 6573, 6569, 6569, 6568, 6568,
                6562, 6562, 6559, 6559, 6554, 6554, 4479, 4479
        }, buffer);
    }

    private static void configurePsg(VirtualSynthesizer synth) {
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x80);
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x20);
        synth.writePsg(TestVirtualSynthesizerMix.class, 0x90);
    }

    private static void configureFm(VirtualSynthesizer synth) {
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xB0, 0x07);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xB4, 0xC0);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xA0, 0x00);
        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0xA4, 0x22);

        int[] slots = {0x00, 0x04, 0x08, 0x0C};
        for (int slot : slots) {
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x30 + slot, 0x01);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x40 + slot, 0x00);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x50 + slot, 0x1F);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x60 + slot, 0x10);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x70 + slot, 0x08);
            synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x80 + slot, 0x05);
        }

        synth.writeFm(TestVirtualSynthesizerMix.class, 0, 0x28, 0xF0);
    }
}
