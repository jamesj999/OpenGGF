package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.RomDetector;
import com.openggf.game.GameModule;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * ROM detector for Sonic the Hedgehog 1 (Mega Drive/Genesis).
 *
 * <p>Detection is based on the ROM header domestic/international name
 * containing "SONIC THE HEDGEHOG" but NOT "HEDGEHOG 2" or "HEDGEHOG 3".
 */
public class Sonic1RomDetector implements RomDetector {
    private static final Logger LOGGER = Logger.getLogger(Sonic1RomDetector.class.getName());

    private static final String SONIC_NAME = "SONIC THE HEDGEHOG";
    private static final String SONIC_2_SUFFIX = "HEDGEHOG 2";
    private static final String SONIC_3_SUFFIX = "HEDGEHOG 3";

    // Lower priority number = checked first (before Sonic 2's priority of 100)
    private static final int PRIORITY = 90;

    @Override
    public boolean canHandle(Rom rom) {
        if (rom == null || !rom.isOpen()) {
            return false;
        }

        try {
            String domesticName = rom.readDomesticName();
            if (isSonic1Name(domesticName)) {
                LOGGER.fine("Sonic 1 detected via domestic name: " + domesticName);
                return true;
            }

            String intlName = rom.readInternationalName();
            if (isSonic1Name(intlName)) {
                LOGGER.fine("Sonic 1 detected via international name: " + intlName);
                return true;
            }

            LOGGER.fine("ROM names did not match Sonic 1: domestic='" + domesticName +
                    "', international='" + intlName + "'");
            return false;
        } catch (IOException e) {
            LOGGER.warning("Error reading ROM header: " + e.getMessage());
            return false;
        }
    }

    private boolean isSonic1Name(String name) {
        if (name == null) {
            return false;
        }
        String normalized = normalizeWhitespace(name);
        return normalized.contains(SONIC_NAME)
                && !normalized.contains(SONIC_2_SUFFIX)
                && !normalized.contains(SONIC_3_SUFFIX);
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
        return new Sonic1GameModule();
    }

    @Override
    public String getGameName() {
        return "Sonic the Hedgehog";
    }
}
