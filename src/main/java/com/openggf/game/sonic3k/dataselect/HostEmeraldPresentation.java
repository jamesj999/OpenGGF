package com.openggf.game.sonic3k.dataselect;

import com.openggf.data.Rom;

import java.io.IOException;

public final class HostEmeraldPresentation {
    private HostEmeraldPresentation() {
    }

    public static Result forHost(String hostGameCode, Rom hostRom) {
        if (hostRom == null || hostGameCode == null || hostGameCode.isBlank()) {
            return Result.fallback();
        }
        try {
            return switch (hostGameCode) {
                case "s1" -> buildS1(hostRom);
                case "s2" -> buildS2(hostRom);
                default -> Result.fallback();
            };
        } catch (IOException | IllegalArgumentException e) {
            return Result.fallback();
        }
    }

    private static Result buildS1(Rom hostRom) throws IOException {
        return new Result(
                HostEmeraldPaletteBuilder.composeRetintedPaletteBytes(
                        HostEmeraldPaletteBuilder.extractS1HostTargets(hostRom),
                        HostEmeraldPaletteBuilder.nativeRamp()),
                HostEmeraldLayoutProfile.s1SixRing(),
                6,
                true);
    }

    private static Result buildS2(Rom hostRom) throws IOException {
        return new Result(
                HostEmeraldPaletteBuilder.composeRetintedPaletteBytes(
                        HostEmeraldPaletteBuilder.extractS2HostTargets(hostRom),
                        HostEmeraldPaletteBuilder.nativeRamp()),
                HostEmeraldLayoutProfile.defaultSeven(),
                7,
                true);
    }

    public record Result(byte[] paletteBytes,
                         HostEmeraldLayoutProfile layout,
                         int activeEmeraldCount,
                         boolean usesRetintedRamp) {
        public Result {
            paletteBytes = paletteBytes != null ? paletteBytes.clone() : new byte[0];
            layout = layout != null ? layout : HostEmeraldLayoutProfile.defaultSeven();
        }

        static Result fallback() {
            return new Result(new byte[0], HostEmeraldLayoutProfile.defaultSeven(), 7, false);
        }
    }
}
