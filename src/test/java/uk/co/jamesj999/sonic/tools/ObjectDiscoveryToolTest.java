package uk.co.jamesj999.sonic.tools;

import org.junit.Assume;
import org.junit.Test;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Test for ObjectDiscoveryTool - generates the OBJECT_CHECKLIST.md report.
 * <p>
 * Run with: mvn test -Dtest=ObjectDiscoveryToolTest
 */
public class ObjectDiscoveryToolTest {

    private static final Path ROM_PATH = Path.of("Sonic The Hedgehog 2 (W) (REV01) [!].gen");
    private static final Path OUTPUT_PATH = Path.of("OBJECT_CHECKLIST.md");

    @Test
    public void generateObjectImplementationReport() throws IOException {
        Assume.assumeTrue("ROM not found at " + ROM_PATH, Files.exists(ROM_PATH));

        try (Rom rom = new Rom()) {
            assertTrue("Failed to open ROM", rom.open(ROM_PATH.toString()));

            RomByteReader reader = RomByteReader.fromRom(rom);
            ObjectDiscoveryTool tool = new ObjectDiscoveryTool(reader, new Sonic2ObjectProfile());

            ObjectDiscoveryTool.DiscoveryReport report = tool.scan();

            // Basic assertions
            assertFalse("Should have zone reports", report.zoneReports().isEmpty());
            assertTrue("Should find some unimplemented objects", report.unimplemented() > 0);
            assertTrue("Should find some implemented objects", report.implemented() > 0);

            // Generate markdown
            String markdown = tool.toMarkdown(report);
            assertNotNull("Markdown should not be null", markdown);
            assertTrue("Markdown should have content", markdown.length() > 100);

            // Write to file
            try (PrintWriter writer = new PrintWriter(OUTPUT_PATH.toFile())) {
                writer.print(markdown);
            }

            System.out.println("Report written to: " + OUTPUT_PATH.toAbsolutePath());
            System.out.printf("Found %d unique objects: %d implemented, %d unimplemented%n",
                    report.implemented() + report.unimplemented(),
                    report.implemented(),
                    report.unimplemented());
        }
    }

    @Test
    public void scanSingleZone() throws IOException {
        Assume.assumeTrue("ROM not found at " + ROM_PATH, Files.exists(ROM_PATH));

        try (Rom rom = new Rom()) {
            assertTrue("Failed to open ROM", rom.open(ROM_PATH.toString()));

            RomByteReader reader = RomByteReader.fromRom(rom);
            ObjectDiscoveryTool tool = new ObjectDiscoveryTool(reader, new Sonic2ObjectProfile());

            ObjectDiscoveryTool.DiscoveryReport report = tool.scan();

            // Find EHZ1 report
            ObjectDiscoveryTool.ZoneReport ehz1 = report.zoneReports().stream()
                    .filter(r -> r.level().shortName().equals("EHZ") && r.level().act() == 1)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("EHZ1 report not found"));

            // EHZ1 should have rings, monitors, springs, badniks, etc.
            assertTrue("EHZ1 should have at least 5 object types", ehz1.objects().size() >= 5);
            assertTrue("EHZ1 should have total objects > 20", ehz1.totalObjects() > 20);

            System.out.println("EHZ Act 1:");
            System.out.printf("  Total objects: %d%n", ehz1.totalObjects());
            System.out.printf("  Unique types: %d%n", ehz1.objects().size());
            System.out.printf("  Implemented: %d%n", ehz1.implementedCount());
            System.out.printf("  Unimplemented: %d%n", ehz1.unimplementedCount());
        }
    }

    private static ObjectDiscoveryTool.ObjectStats findStatsByObjectId(
            ObjectDiscoveryTool.DiscoveryReport report, int objectId) {
        return report.globalStats().values().stream()
                .filter(s -> s.objectId == objectId)
                .findFirst().orElse(null);
    }

    @Test
    public void verifyKnownBadniksPresent() throws IOException {
        Assume.assumeTrue("ROM not found at " + ROM_PATH, Files.exists(ROM_PATH));

        try (Rom rom = new Rom()) {
            assertTrue("Failed to open ROM", rom.open(ROM_PATH.toString()));

            RomByteReader reader = RomByteReader.fromRom(rom);
            ObjectDiscoveryTool tool = new ObjectDiscoveryTool(reader, new Sonic2ObjectProfile());

            ObjectDiscoveryTool.DiscoveryReport report = tool.scan();

            // Check known EHZ badniks are found (global stats uses "id:name" composite keys)
            ObjectDiscoveryTool.ObjectStats buzzer = findStatsByObjectId(report, 0x4B);
            ObjectDiscoveryTool.ObjectStats masher = findStatsByObjectId(report, 0x5C);
            ObjectDiscoveryTool.ObjectStats coconuts = findStatsByObjectId(report, 0x9D);

            assertNotNull("Should find Buzzer (0x4B)", buzzer);
            assertNotNull("Should find Masher (0x5C)", masher);
            assertNotNull("Should find Coconuts (0x9D)", coconuts);

            // EHZ badniks should be marked as implemented
            assertTrue("Buzzer should be implemented", buzzer.implemented);
            assertTrue("Masher should be implemented", masher.implemented);
            assertTrue("Coconuts should be implemented", coconuts.implemented);
        }
    }
}
