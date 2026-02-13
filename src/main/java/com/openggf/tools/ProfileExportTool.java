package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openggf.game.profile.ProfileGenerator;
import com.openggf.game.profile.RomProfile;
import com.openggf.game.sonic2.constants.Sonic2Constants;

import java.io.File;

/**
 * CLI tool that exports Sonic 2 constants to a shipped profile JSON file.
 * The generated profile is stored in src/main/resources/profiles/ and included
 * in the JAR at build time, enabling ROM-checksum-based profile matching.
 *
 * <p>Usage: {@code mvn exec:java -Dexec.mainClass="com.openggf.tools.ProfileExportTool"}
 */
public class ProfileExportTool {
    public static void main(String[] args) throws Exception {
        ProfileGenerator generator = new ProfileGenerator();
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        RomProfile s2 = generator.generateFromOffsets(
                "Sonic The Hedgehog 2 (W) (REV01)",
                "sonic2",
                "placeholder_compute_from_rom",
                Sonic2Constants.getAllOffsets());

        File outputDir = new File("src/main/resources/profiles");
        outputDir.mkdirs();
        mapper.writeValue(new File(outputDir, "sonic2-rev01.profile.json"), s2);
        System.out.println("Exported " + s2.addressCount() + " Sonic 2 addresses");
    }
}
