package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameModule;
import uk.co.jamesj999.sonic.game.RomDetector;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * ROM detector for Sonic 3, Sonic &amp; Knuckles, and Sonic 3 &amp; Knuckles
 * (Mega Drive/Genesis).
 *
 * <p>Detection is based on the ROM header domestic/international name
 * containing "SONIC THE HEDGEHOG 3", "SONIC & KNUCKLES", or
 * "SONIC3 & KNUCKLES".
 */
public class Sonic3kRomDetector implements RomDetector {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kRomDetector.class.getName());

    // Checked before S1 (90) and S2 (100)
    private static final int PRIORITY = 80;

    @Override
    public boolean canHandle(Rom rom) {
        if (rom == null || !rom.isOpen()) {
            return false;
        }

        try {
            String domesticName = rom.readDomesticName();
            if (isSonic3kName(domesticName)) {
                LOGGER.fine("Sonic 3K detected via domestic name: " + domesticName);
                return true;
            }

            String intlName = rom.readInternationalName();
            if (isSonic3kName(intlName)) {
                LOGGER.fine("Sonic 3K detected via international name: " + intlName);
                return true;
            }

            LOGGER.fine("ROM names did not match Sonic 3K: domestic='" + domesticName +
                    "', international='" + intlName + "'");
            return false;
        } catch (IOException e) {
            LOGGER.warning("Error reading ROM header: " + e.getMessage());
            return false;
        }
    }

    private boolean isSonic3kName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = normalizeWhitespace(name);
        return normalized.contains("SONIC THE HEDGEHOG 3")
                || normalized.contains("SONIC & KNUCKLES")
                || normalized.contains("SONIC3 & KNUCKLES")
                || normalized.contains("SONIC AND KNUCKLES");
    }

    private String normalizeWhitespace(String input) {
        return input.toUpperCase().replaceAll("\\s+", " ").trim();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public GameModule createModule() {
        return new Sonic3kGameModule();
    }

    @Override
    public String getGameName() {
        return "Sonic 3 & Knuckles";
    }
}
