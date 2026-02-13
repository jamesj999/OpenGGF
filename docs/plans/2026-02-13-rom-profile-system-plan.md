# ROM Profile System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable the engine to load ROM hacks and alternative ROM revisions by replacing hardcoded address constants with a layered resolution system backed by JSON profile files.

**Architecture:** Two-pronged: (1) a runtime `RomAddressResolver` singleton that resolves addresses through a layered chain (user override > checksum-matched profile > pattern scan > hardcoded defaults), and (2) a standalone `RomProfileIntrospector` CLI tool that traces ROM pointer chains to generate profile JSON files for unknown ROMs.

**Tech Stack:** Java 21, Jackson (already in pom.xml), SHA-256 (java.security.MessageDigest)

**Worktree:** `C:\Users\farre\IdeaProjects\sonic-engine\.worktrees\feature-ai-rom-profile-system`
**Branch:** `feature/ai-rom-profile-system`

**Design doc:** `docs/plans/2026-02-13-rom-hack-support-design.md`

---

## Phase 1: Foundation (Non-Breaking)

### Task 1: ROM Profile Data Model

The JSON profile format needs Java POJOs for Jackson serialization. These are pure data classes with no dependencies.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/RomProfile.java`
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/ProfileMetadata.java`
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/AddressEntry.java`
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/ZoneMapping.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/TestRomProfile.java`

**Step 1: Write failing test for profile round-trip serialization**

```java
package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestRomProfile {

    @Test
    void testRoundTripSerialization() throws Exception {
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Sonic 2 REV01", "sonic2",
                "abc123", "shipped", true));

        profile.putAddress("level", "LEVEL_LAYOUT_INDEX_ADDR",
                new AddressEntry(0x045A80, "verified"));
        profile.putAddress("audio", "MUSIC_PTR_TABLE_ADDR",
                new AddressEntry(0x0E2008, "scanned"));

        profile.putZoneMapping(0, new ZoneMapping("EHZ", "EHZ"));
        profile.putZoneMapping(1, new ZoneMapping("CPZ", "CPZ"));
        profile.putZoneMapping(15, new ZoneMapping("Custom Zone", null));

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(profile);
        RomProfile deserialized = mapper.readValue(json, RomProfile.class);

        assertEquals(0x045A80,
                deserialized.getAddress("level", "LEVEL_LAYOUT_INDEX_ADDR").value());
        assertEquals("scanned",
                deserialized.getAddress("audio", "MUSIC_PTR_TABLE_ADDR").confidence());
        assertEquals("EHZ", deserialized.getZoneMapping(0).behaviorMapping());
        assertNull(deserialized.getZoneMapping(15).behaviorMapping());
        assertTrue(deserialized.getMetadata().complete());
    }

    @Test
    void testGetAddressReturnsNullForMissing() {
        RomProfile profile = new RomProfile();
        assertNull(profile.getAddress("level", "NONEXISTENT"));
        assertNull(profile.getZoneMapping(99));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestRomProfile -pl .`
Expected: Compilation failure (classes don't exist)

**Step 3: Implement the data model classes**

`AddressEntry.java` - record for a single address:
```java
package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AddressEntry(
        @JsonProperty("value") int value,
        @JsonProperty("confidence") String confidence
) {
    @JsonCreator
    public AddressEntry {}
}
```

`ZoneMapping.java` - record for a zone behavior mapping:
```java
package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ZoneMapping(
        @JsonProperty("name") String name,
        @JsonProperty("behaviorMapping") String behaviorMapping
) {
    @JsonCreator
    public ZoneMapping {}
}
```

`ProfileMetadata.java` - record for profile header:
```java
package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ProfileMetadata(
        @JsonProperty("name") String name,
        @JsonProperty("game") String game,
        @JsonProperty("checksum") String checksum,
        @JsonProperty("generatedBy") String generatedBy,
        @JsonProperty("complete") boolean complete
) {
    @JsonCreator
    public ProfileMetadata {}
}
```

`RomProfile.java` - top-level container:
```java
package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

public class RomProfile {

    @JsonProperty("profile")
    private ProfileMetadata metadata;

    @JsonProperty("addresses")
    private Map<String, Map<String, AddressEntry>> addresses = new LinkedHashMap<>();

    @JsonProperty("zones")
    private Map<Integer, ZoneMapping> zones = new LinkedHashMap<>();

    public ProfileMetadata getMetadata() { return metadata; }
    public void setMetadata(ProfileMetadata metadata) { this.metadata = metadata; }

    public AddressEntry getAddress(String category, String name) {
        Map<String, AddressEntry> cat = addresses.get(category);
        return cat != null ? cat.get(name) : null;
    }

    public void putAddress(String category, String name, AddressEntry entry) {
        addresses.computeIfAbsent(category, k -> new LinkedHashMap<>()).put(name, entry);
    }

    public Map<String, Map<String, AddressEntry>> getAddresses() { return addresses; }

    public ZoneMapping getZoneMapping(int zoneId) { return zones.get(zoneId); }

    public void putZoneMapping(int zoneId, ZoneMapping mapping) {
        zones.put(zoneId, mapping);
    }

    public Map<Integer, ZoneMapping> getZones() { return zones; }

    /** Count total addresses across all categories. */
    public int addressCount() {
        return addresses.values().stream().mapToInt(Map::size).sum();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestRomProfile -pl .`
Expected: 2 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/profile/ src/test/java/uk/co/jamesj999/sonic/game/profile/
git commit -m "feat: add ROM profile data model (JSON serialization)"
```

---

### Task 2: ROM Checksum Utility

SHA-256 checksum computation for ROM files. Used to match ROMs to known profiles.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/RomChecksumUtil.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/TestRomChecksumUtil.java`

**Step 1: Write failing test**

```java
package uk.co.jamesj999.sonic.game.profile;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestRomChecksumUtil {

    @Test
    void testChecksumFromBytes() {
        byte[] data = "Hello ROM".getBytes();
        String checksum = RomChecksumUtil.sha256(data);
        assertNotNull(checksum);
        assertEquals(64, checksum.length()); // SHA-256 = 64 hex chars
        // Same input = same output
        assertEquals(checksum, RomChecksumUtil.sha256(data));
    }

    @Test
    void testDifferentDataDifferentChecksum() {
        String a = RomChecksumUtil.sha256("ROM A".getBytes());
        String b = RomChecksumUtil.sha256("ROM B".getBytes());
        assertNotEquals(a, b);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestRomChecksumUtil -pl .`
Expected: Compilation failure

**Step 3: Implement**

```java
package uk.co.jamesj999.sonic.game.profile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RomChecksumUtil {
    private RomChecksumUtil() {}

    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestRomChecksumUtil -pl .`
Expected: 2 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/profile/RomChecksumUtil.java src/test/java/uk/co/jamesj999/sonic/game/profile/TestRomChecksumUtil.java
git commit -m "feat: add SHA-256 ROM checksum utility"
```

---

### Task 3: Profile Loader (JSON File + Classpath)

Loads profiles from: (1) filesystem path (user overrides), (2) classpath resources (shipped profiles matched by checksum).

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/ProfileLoader.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/TestProfileLoader.java`

**Step 1: Write failing test**

```java
package uk.co.jamesj999.sonic.game.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class TestProfileLoader {

    @Test
    void testLoadFromFile(@TempDir Path tempDir) throws Exception {
        String json = """
                {
                  "profile": {
                    "name": "Test ROM",
                    "game": "sonic2",
                    "checksum": "abc",
                    "generatedBy": "manual",
                    "complete": false
                  },
                  "addresses": {
                    "level": {
                      "LEVEL_LAYOUT_INDEX_ADDR": { "value": 285312, "confidence": "verified" }
                    }
                  },
                  "zones": {}
                }
                """;
        Path profileFile = tempDir.resolve("test.profile.json");
        Files.writeString(profileFile, json);

        ProfileLoader loader = new ProfileLoader();
        RomProfile profile = loader.loadFromFile(profileFile);

        assertNotNull(profile);
        assertEquals("Test ROM", profile.getMetadata().name());
        assertEquals(285312,
                profile.getAddress("level", "LEVEL_LAYOUT_INDEX_ADDR").value());
    }

    @Test
    void testLoadFromFileReturnsNullWhenMissing(@TempDir Path tempDir) {
        ProfileLoader loader = new ProfileLoader();
        RomProfile result = loader.loadFromFile(tempDir.resolve("nonexistent.json"));
        assertNull(result);
    }

    @Test
    void testLoadFromClasspathReturnsNullWhenNoMatch() {
        ProfileLoader loader = new ProfileLoader();
        RomProfile result = loader.loadFromClasspath("nonexistent_checksum_xyz");
        assertNull(result);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestProfileLoader -pl .`
Expected: Compilation failure

**Step 3: Implement**

```java
package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileLoader {
    private static final Logger LOGGER = Logger.getLogger(ProfileLoader.class.getName());
    private static final String PROFILES_RESOURCE_DIR = "profiles/";
    private final ObjectMapper mapper = new ObjectMapper();

    /** Load a profile from a filesystem path. Returns null if file doesn't exist or is invalid. */
    public RomProfile loadFromFile(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return mapper.readValue(path.toFile(), RomProfile.class);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load profile from " + path, e);
            return null;
        }
    }

    /** Load a shipped profile by ROM checksum. Returns null if no matching profile found. */
    public RomProfile loadFromClasspath(String checksum) {
        String resourcePath = PROFILES_RESOURCE_DIR + checksum + ".profile.json";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            return mapper.readValue(is, RomProfile.class);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load classpath profile: " + resourcePath, e);
            return null;
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestProfileLoader -pl .`
Expected: 3 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/profile/ProfileLoader.java src/test/java/uk/co/jamesj999/sonic/game/profile/TestProfileLoader.java
git commit -m "feat: add profile loader for filesystem and classpath profiles"
```

---

### Task 4: RomAddressResolver Singleton

The core resolver with layered resolution. This task implements layers 1 (user override), 2 (checksum profile), and 4 (hardcoded defaults). Layer 3 (pattern scan) comes in Phase 2.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/RomAddressResolver.java`
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/ResolutionReport.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/TestRomAddressResolver.java`

**Step 1: Write failing test**

```java
package uk.co.jamesj999.sonic.game.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TestRomAddressResolver {

    @BeforeEach
    void reset() {
        RomAddressResolver.resetInstance();
    }

    @Test
    void testResolveFromProfile() {
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "TEST_ADDR", new AddressEntry(0x1234, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, Map.of());

        assertEquals(0x1234, resolver.getAddress("level", "TEST_ADDR"));
    }

    @Test
    void testFallbackToDefaults() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of("level.FALLBACK_ADDR", 0xABCD));

        assertEquals(0xABCD, resolver.getAddress("level", "FALLBACK_ADDR"));
    }

    @Test
    void testProfileOverridesDefault() {
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "ADDR", new AddressEntry(0x1111, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, Map.of("level.ADDR", 0x9999));

        // Profile wins over default
        assertEquals(0x1111, resolver.getAddress("level", "ADDR"));
    }

    @Test
    void testMissingAddressReturnsNegativeOne() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        assertEquals(-1, resolver.getAddress("level", "NONEXISTENT"));
    }

    @Test
    void testConvenienceMethods() {
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "MY_LEVEL", new AddressEntry(0x100, "verified"));
        profile.putAddress("audio", "MY_AUDIO", new AddressEntry(0x200, "verified"));
        profile.putAddress("art", "MY_ART", new AddressEntry(0x300, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, Map.of());

        assertEquals(0x100, resolver.getLevelAddress("MY_LEVEL"));
        assertEquals(0x200, resolver.getAudioAddress("MY_AUDIO"));
        assertEquals(0x300, resolver.getArtAddress("MY_ART"));
    }

    @Test
    void testGetAddressWithDefault() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        assertEquals(0x5678, resolver.getAddress("level", "MISSING", 0x5678));
    }

    @Test
    void testZoneBehavior() {
        RomProfile profile = new RomProfile();
        profile.putZoneMapping(0, new ZoneMapping("EHZ", "EHZ"));
        profile.putZoneMapping(5, new ZoneMapping("Custom", null));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, Map.of());

        assertEquals("EHZ", resolver.getZoneBehavior(0));
        assertNull(resolver.getZoneBehavior(5));
        assertNull(resolver.getZoneBehavior(99)); // unmapped
    }

    @Test
    void testResolutionReport() {
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "FROM_PROFILE", new AddressEntry(0x1, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, Map.of("level.FROM_DEFAULT", 0x2));

        ResolutionReport report = resolver.getReport();
        assertTrue(report.fromProfile() > 0);
        assertTrue(report.fromDefaults() > 0);
        assertTrue(report.totalResolved() >= 2);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestRomAddressResolver -pl .`
Expected: Compilation failure

**Step 3: Implement ResolutionReport**

```java
package uk.co.jamesj999.sonic.game.profile;

public record ResolutionReport(
        int fromProfile,
        int fromScanned,
        int fromDefaults,
        int missing
) {
    public int totalResolved() {
        return fromProfile + fromScanned + fromDefaults;
    }

    public int totalExpected() {
        return totalResolved() + missing;
    }
}
```

**Step 4: Implement RomAddressResolver**

```java
package uk.co.jamesj999.sonic.game.profile;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class RomAddressResolver {
    private static final Logger LOGGER = Logger.getLogger(RomAddressResolver.class.getName());
    private static RomAddressResolver instance;

    // Resolved addresses: "category.name" -> value
    private final Map<String, Integer> resolved = new HashMap<>();
    // Track source for reporting
    private final Map<String, String> sources = new HashMap<>(); // key -> "profile"|"scanned"|"default"
    // Zone behavior mappings
    private final Map<Integer, ZoneMapping> zoneMappings = new HashMap<>();

    private RomAddressResolver() {}

    public static synchronized RomAddressResolver getInstance() {
        if (instance == null) {
            instance = new RomAddressResolver();
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    /**
     * Initialize the resolver with a profile (may be null) and hardcoded defaults.
     * Defaults use "category.name" keys (e.g., "level.LEVEL_LAYOUT_INDEX_ADDR").
     */
    public void initialize(RomProfile profile, Map<String, Integer> defaults) {
        resolved.clear();
        sources.clear();
        zoneMappings.clear();

        // Layer 4: Load defaults first (lowest priority)
        for (var entry : defaults.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue());
            sources.put(entry.getKey(), "default");
        }

        // Layer 1/2: Profile overrides defaults (higher priority)
        if (profile != null) {
            for (var catEntry : profile.getAddresses().entrySet()) {
                String category = catEntry.getKey();
                for (var addrEntry : catEntry.getValue().entrySet()) {
                    String key = category + "." + addrEntry.getKey();
                    resolved.put(key, addrEntry.getValue().value());
                    sources.put(key, "profile");
                }
            }
            zoneMappings.putAll(profile.getZones());
        }

        logResolutionSummary();
    }

    /**
     * Add scanned addresses (called by pattern scanner, Phase 2).
     * Only fills in addresses not already resolved by profile.
     */
    public void addScannedAddresses(Map<String, Integer> scanned) {
        for (var entry : scanned.entrySet()) {
            if (!resolved.containsKey(entry.getKey()) || "default".equals(sources.get(entry.getKey()))) {
                resolved.put(entry.getKey(), entry.getValue());
                sources.put(entry.getKey(), "scanned");
            }
        }
    }

    public int getAddress(String category, String name) {
        return resolved.getOrDefault(category + "." + name, -1);
    }

    public int getAddress(String category, String name, int defaultValue) {
        return resolved.getOrDefault(category + "." + name, defaultValue);
    }

    public int getLevelAddress(String name) { return getAddress("level", name); }
    public int getAudioAddress(String name) { return getAddress("audio", name); }
    public int getArtAddress(String name) { return getAddress("art", name); }
    public int getCollisionAddress(String name) { return getAddress("collision", name); }
    public int getPaletteAddress(String name) { return getAddress("palette", name); }

    public int getLevelAddress(String name, int defaultValue) {
        return getAddress("level", name, defaultValue);
    }

    public String getZoneBehavior(int zoneId) {
        ZoneMapping mapping = zoneMappings.get(zoneId);
        return mapping != null ? mapping.behaviorMapping() : null;
    }

    public ResolutionReport getReport() {
        int fromProfile = 0, fromScanned = 0, fromDefaults = 0;
        for (String source : sources.values()) {
            switch (source) {
                case "profile" -> fromProfile++;
                case "scanned" -> fromScanned++;
                case "default" -> fromDefaults++;
            }
        }
        return new ResolutionReport(fromProfile, fromScanned, fromDefaults, 0);
    }

    private void logResolutionSummary() {
        ResolutionReport report = getReport();
        LOGGER.info(String.format("ROM Profile: %d addresses resolved (profile: %d, scanned: %d, default: %d)",
                report.totalResolved(), report.fromProfile(), report.fromScanned(), report.fromDefaults()));
    }
}
```

**Step 5: Run test to verify it passes**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestRomAddressResolver -pl .`
Expected: 7 tests PASS

**Step 6: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/profile/RomAddressResolver.java src/main/java/uk/co/jamesj999/sonic/game/profile/ResolutionReport.java src/test/java/uk/co/jamesj999/sonic/game/profile/TestRomAddressResolver.java
git commit -m "feat: add RomAddressResolver singleton with layered resolution"
```

---

### Task 5: Profile Generator (Constants to JSON)

A utility that exports the current hardcoded constants into profile JSON files. Used to generate the shipped profiles for known ROMs.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/ProfileGenerator.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/TestProfileGenerator.java`

**Step 1: Write failing test**

```java
package uk.co.jamesj999.sonic.game.profile;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TestProfileGenerator {

    @Test
    void testGenerateFromOffsetMap() {
        // Simulate what Sonic2Constants.getAllOffsets() returns
        Map<String, Integer> offsets = Map.of(
                "ART_NEM_MONITOR_ADDR", 0x40D90,
                "LEVEL_LAYOUT_DIR_ADDR_LOC", 0x42594,
                "COLLISION_LAYOUT_DIR_ADDR", 0x44E8C,
                "MUSIC_PLAYLIST_ADDR", 0x6E70,
                "CYCLING_PAL_EHZ_ARZ_WATER_ADDR", 0x2710
        );

        ProfileGenerator generator = new ProfileGenerator();
        RomProfile profile = generator.generateFromOffsets("Sonic 2 REV01", "sonic2",
                "fake_checksum", offsets);

        assertEquals("Sonic 2 REV01", profile.getMetadata().name());
        assertEquals("sonic2", profile.getMetadata().game());
        assertTrue(profile.getMetadata().complete());

        // Check categorization by prefix
        assertNotNull(profile.getAddress("art", "ART_NEM_MONITOR_ADDR"));
        assertNotNull(profile.getAddress("level", "LEVEL_LAYOUT_DIR_ADDR_LOC"));
        assertNotNull(profile.getAddress("collision", "COLLISION_LAYOUT_DIR_ADDR"));
        assertNotNull(profile.getAddress("audio", "MUSIC_PLAYLIST_ADDR"));
        assertNotNull(profile.getAddress("palette", "CYCLING_PAL_EHZ_ARZ_WATER_ADDR"));

        // All should be "verified" confidence
        assertEquals("verified",
                profile.getAddress("art", "ART_NEM_MONITOR_ADDR").confidence());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestProfileGenerator -pl .`
Expected: Compilation failure

**Step 3: Implement**

```java
package uk.co.jamesj999.sonic.game.profile;

import java.util.Map;

public class ProfileGenerator {

    /**
     * Generate a RomProfile from a flat map of constant names to values.
     * Auto-categorizes by name prefix using the same rules as Sonic2RomOffsetProvider.
     */
    public RomProfile generateFromOffsets(String name, String game, String checksum,
                                          Map<String, Integer> offsets) {
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata(name, game, checksum, "shipped", true));

        for (var entry : offsets.entrySet()) {
            String category = categorize(entry.getKey());
            profile.putAddress(category, entry.getKey(),
                    new AddressEntry(entry.getValue(), "verified"));
        }

        return profile;
    }

    static String categorize(String fieldName) {
        String upper = fieldName.toUpperCase();
        if (upper.startsWith("ART_") || upper.startsWith("MAP_") || upper.startsWith("DPLC_")
                || upper.startsWith("ANI_") || upper.startsWith("ANIM_")
                || upper.contains("_ANIM_") || upper.contains("_MAPPING_")) {
            if (upper.startsWith("ANIM_PAT_") || upper.startsWith("ANI_")) {
                return "animation";
            }
            if (upper.startsWith("MAP_")) return "art";
            return "art";
        }
        if (upper.startsWith("LEVEL_") || upper.startsWith("DEFAULT_LEVEL_")
                || upper.contains("LAYOUT") || upper.startsWith("BG_SCROLL_")
                || upper.startsWith("RING_") || upper.startsWith("START_LOC_")) {
            return "level";
        }
        if (upper.startsWith("COLLISION_") || upper.startsWith("SOLID_")
                || upper.startsWith("ALT_COLLISION_")) {
            return "collision";
        }
        if (upper.startsWith("MUSIC_") || upper.startsWith("SFX_") || upper.startsWith("SOUND_")
                || upper.startsWith("DAC_") || upper.startsWith("Z80_")
                || upper.startsWith("PSG_") || upper.startsWith("SPEED_UP_")
                || upper.startsWith("SEGA_SOUND_")) {
            return "audio";
        }
        if (upper.startsWith("PAL_") || upper.startsWith("CYCLING_PAL_")
                || upper.contains("PALETTE")) {
            return "palette";
        }
        if (upper.startsWith("OBJECT_") || upper.startsWith("OBJ_POS_")
                || upper.startsWith("TOUCH_")) {
            return "objects";
        }
        return "misc";
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestProfileGenerator -pl .`
Expected: 1 test PASS

**Step 5: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/profile/ProfileGenerator.java src/test/java/uk/co/jamesj999/sonic/game/profile/TestProfileGenerator.java
git commit -m "feat: add profile generator for exporting constants to JSON"
```

---

### Task 6: Generate and Ship Known ROM Profiles

Use the ProfileGenerator to export S2 constants to a shipped profile resource file. S1/S3K follow the same pattern but S2 is the most complete.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/tools/ProfileExportTool.java` (CLI tool)
- Create: `src/main/resources/profiles/` directory with generated profile(s)

**Step 1: Write the CLI export tool**

```java
package uk.co.jamesj999.sonic.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import uk.co.jamesj999.sonic.game.profile.ProfileGenerator;
import uk.co.jamesj999.sonic.game.profile.RomProfile;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;

import java.io.File;

/**
 * One-shot tool to export current hardcoded constants as shipped ROM profiles.
 * Run: mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.ProfileExportTool"
 */
public class ProfileExportTool {
    public static void main(String[] args) throws Exception {
        ProfileGenerator generator = new ProfileGenerator();
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Sonic 2 REV01
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
```

**Step 2: Run the export tool to generate the profile**

Run: `mvn exec:java -f "<worktree>/pom.xml" -Dexec.mainClass="uk.co.jamesj999.sonic.tools.ProfileExportTool" -q`
Expected: Console prints address count, file created at `src/main/resources/profiles/sonic2-rev01.profile.json`

**Step 3: Verify the generated profile is valid JSON**

Manually inspect the first few lines of the generated file to confirm it has the expected structure. Spot-check a few known address values against `Sonic2Constants.java`.

**Step 4: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/tools/ProfileExportTool.java src/main/resources/profiles/
git commit -m "feat: generate and ship Sonic 2 REV01 profile"
```

---

### Task 7: Wire Resolver into Startup

Connect the resolver to the engine's ROM loading sequence. The resolver initializes after ROM detection, using the detected game's constants as defaults.

**Files:**
- Modify: `src/main/java/uk/co/jamesj999/sonic/game/GameModuleRegistry.java`
- Modify: `src/main/java/uk/co/jamesj999/sonic/game/GameModule.java` (add `getDefaultOffsets()`)
- Modify: `src/main/java/uk/co/jamesj999/sonic/game/sonic2/Sonic2GameModule.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/TestResolverIntegration.java`

**Step 1: Write failing integration test**

```java
package uk.co.jamesj999.sonic.game.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TestResolverIntegration {

    @BeforeEach
    void reset() {
        RomAddressResolver.resetInstance();
    }

    @Test
    void testSonic2DefaultsPopulateResolver() {
        // Simulate what startup does: load S2 defaults into resolver
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(
                Sonic2Constants.getAllOffsets());

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        // Should find S2's known addresses
        int levelAddr = resolver.getLevelAddress("LEVEL_LAYOUT_DIR_ADDR_LOC");
        assertEquals(Sonic2Constants.LEVEL_LAYOUT_DIR_ADDR_LOC, levelAddr);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestResolverIntegration -pl .`
Expected: Compilation failure (`toResolverDefaults` doesn't exist)

**Step 3: Add `toResolverDefaults` to ProfileGenerator**

Add to `ProfileGenerator.java`:
```java
/**
 * Convert a flat offset map (from constants classes) to resolver-keyed format.
 * Keys become "category.name" (e.g., "level.LEVEL_LAYOUT_DIR_ADDR_LOC").
 */
public static Map<String, Integer> toResolverDefaults(Map<String, Integer> offsets) {
    Map<String, Integer> defaults = new HashMap<>();
    for (var entry : offsets.entrySet()) {
        String category = categorize(entry.getKey());
        defaults.put(category + "." + entry.getKey(), entry.getValue());
    }
    return defaults;
}
```

**Step 4: Add `getDefaultOffsets()` to GameModule interface**

Add default method to `GameModule.java`:
```java
/** Returns all hardcoded offsets for this game as a flat name-to-value map. */
default Map<String, Integer> getDefaultOffsets() {
    return Map.of();
}
```

**Step 5: Implement in Sonic2GameModule**

Add to `Sonic2GameModule.java`:
```java
@Override
public Map<String, Integer> getDefaultOffsets() {
    return Sonic2Constants.getAllOffsets();
}
```

**Step 6: Wire resolver initialization into GameModuleRegistry.detectAndSetModule()**

In `GameModuleRegistry.java`, after `setCurrent()` succeeds, add:
```java
// Initialize address resolver with detected game's defaults
initializeResolver(rom, module);
```

Add private helper:
```java
private static void initializeResolver(Rom rom, GameModule module) {
    try {
        ProfileLoader loader = new ProfileLoader();

        // Compute ROM checksum
        byte[] romData = rom.readAllBytes();
        String checksum = RomChecksumUtil.sha256(romData);

        // Try user override file (rom filename + .profile.json)
        // Then try shipped profile by checksum
        RomProfile profile = loader.loadFromClasspath(checksum);
        // User override would be checked first in future (needs ROM path access)

        // Build defaults from game module
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(
                module.getDefaultOffsets());

        RomAddressResolver.getInstance().initialize(profile, defaults);
    } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to initialize address resolver, using defaults", e);
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(
                module.getDefaultOffsets());
        RomAddressResolver.getInstance().initialize(null, defaults);
    }
}
```

**Step 7: Run test to verify it passes**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestResolverIntegration -pl .`
Expected: PASS

**Step 8: Run full test suite to verify nothing is broken**

Run: `mvn test -f "<worktree>/pom.xml"`
Expected: Same pass/fail counts as baseline (862 run, 0 failures)

**Step 9: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/GameModule.java src/main/java/uk/co/jamesj999/sonic/game/GameModuleRegistry.java src/main/java/uk/co/jamesj999/sonic/game/sonic2/Sonic2GameModule.java src/main/java/uk/co/jamesj999/sonic/game/profile/ProfileGenerator.java src/test/java/uk/co/jamesj999/sonic/game/profile/TestResolverIntegration.java
git commit -m "feat: wire RomAddressResolver into engine startup sequence"
```

---

### Task 8: Constants Migration Shim for Sonic2Constants

Make `Sonic2Constants` fields mutable and add `initFromResolver()` so the resolver can override them at startup. Existing `import static` call sites continue working.

**Files:**
- Modify: `src/main/java/uk/co/jamesj999/sonic/game/sonic2/constants/Sonic2Constants.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/TestConstantsMigrationShim.java`

**Step 1: Write failing test**

```java
package uk.co.jamesj999.sonic.game.profile;

import org.junit.jupiter.api.Test;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import static org.junit.jupiter.api.Assertions.*;

public class TestConstantsMigrationShim {

    @Test
    void testInitFromResolverOverridesConstants() {
        // Save original
        int original = Sonic2Constants.LEVEL_LAYOUT_DIR_ADDR_LOC;

        // Create resolver with an overridden value
        RomAddressResolver.resetInstance();
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "LEVEL_LAYOUT_DIR_ADDR_LOC",
                new AddressEntry(0xDEAD, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, ProfileGenerator.toResolverDefaults(
                Sonic2Constants.getAllOffsets()));

        // Apply resolver to constants
        Sonic2Constants.initFromResolver(resolver);

        assertEquals(0xDEAD, Sonic2Constants.LEVEL_LAYOUT_DIR_ADDR_LOC);

        // Restore original for other tests
        Sonic2Constants.LEVEL_LAYOUT_DIR_ADDR_LOC = original;
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestConstantsMigrationShim -pl .`
Expected: Compilation failure (`initFromResolver` doesn't exist, fields are `final`)

**Step 3: Modify Sonic2Constants**

This is the most delicate change. Remove `final` from all address fields (keep `final` on non-address constants like entry sizes and string constants). Add `initFromResolver()` method at the end of the class.

The key change is:
- `public static final int FIELD = 0x...` becomes `public static int FIELD = 0x...`
- Only for fields that are ROM addresses (not structural constants like entry sizes)

Add at end of class:
```java
/**
 * Override address constants from the resolver.
 * Called at startup after resolver initialization.
 * Fields not present in the resolver keep their hardcoded defaults.
 */
public static void initFromResolver(RomAddressResolver resolver) {
    // Each field uses getAddress with its current value as fallback
    LEVEL_LAYOUT_DIR_ADDR_LOC = resolver.getAddress("level", "LEVEL_LAYOUT_DIR_ADDR_LOC", LEVEL_LAYOUT_DIR_ADDR_LOC);
    LEVEL_SELECT_ADDR = resolver.getAddress("level", "LEVEL_SELECT_ADDR", LEVEL_SELECT_ADDR);
    // ... repeat for all address fields ...
    // (Generate this list mechanically from getAllOffsets keys)
}
```

**Implementation note for the executing agent:** The `initFromResolver()` method should cover every field listed in `getAllOffsets()`. The pattern is mechanical - for each `offsets.put("NAME", NAME)` line in `getAllOffsets()`, add a corresponding `NAME = resolver.getAddress("category", "NAME", NAME)` line in `initFromResolver()`. Use `ProfileGenerator.categorize()` to determine the category for each field name.

**Step 4: Run test to verify it passes**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestConstantsMigrationShim -pl .`
Expected: PASS

**Step 5: Run full test suite**

Run: `mvn test -f "<worktree>/pom.xml"`
Expected: Same baseline (862 run, 0 failures). Removing `final` should not change behavior since the values are identical.

**Step 6: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/sonic2/constants/Sonic2Constants.java src/test/java/uk/co/jamesj999/sonic/game/profile/TestConstantsMigrationShim.java
git commit -m "feat: add migration shim to Sonic2Constants for resolver override"
```

---

## Phase 2: Pattern Scanner Framework

### Task 9: RomPatternScanner Framework

Generalize `Sonic3kRomScanner`'s pattern-matching approach into a reusable framework.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/scanner/ScanPattern.java`
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/scanner/ScanResult.java`
- Create: `src/main/java/uk/co/jamesj999/sonic/game/profile/scanner/RomPatternScanner.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/scanner/TestRomPatternScanner.java`

**Step 1: Write failing test**

```java
package uk.co.jamesj999.sonic.game.profile.scanner;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TestRomPatternScanner {

    @Test
    void testScanFindsPattern() {
        // Build a fake ROM with a known pattern at offset 0x10
        byte[] rom = new byte[256];
        rom[0x10] = (byte) 0xDE;
        rom[0x11] = (byte) 0xAD;
        rom[0x12] = (byte) 0xBE;
        rom[0x13] = (byte) 0xEF;
        // The "address" we want is 4 bytes after the pattern
        rom[0x14] = 0x00;
        rom[0x15] = 0x00;
        rom[0x16] = (byte) 0x80;
        rom[0x17] = 0x00;

        ScanPattern pattern = new ScanPattern(
                "level", "TEST_ADDR",
                new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF},
                4, // pointerOffset: address is 4 bytes after match
                ScanPattern.ReadMode.BIG_ENDIAN_32,
                null // no validator
        );

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(pattern);
        Map<String, ScanResult> results = scanner.scan(rom, java.util.Set.of());

        assertTrue(results.containsKey("level.TEST_ADDR"));
        assertEquals(0x8000, results.get("level.TEST_ADDR").value());
    }

    @Test
    void testScanSkipsAlreadyResolved() {
        byte[] rom = new byte[256];
        rom[0x10] = (byte) 0xAA;

        ScanPattern pattern = new ScanPattern(
                "level", "ALREADY_KNOWN",
                new byte[]{(byte) 0xAA}, 0,
                ScanPattern.ReadMode.BYTE, null);

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(pattern);

        // Tell scanner this address is already resolved
        Map<String, ScanResult> results = scanner.scan(rom,
                java.util.Set.of("level.ALREADY_KNOWN"));

        assertFalse(results.containsKey("level.ALREADY_KNOWN"));
    }
}
```

**Step 2: Run test to verify it fails**

Expected: Compilation failure

**Step 3: Implement scanner framework**

`ScanPattern.java`:
```java
package uk.co.jamesj999.sonic.game.profile.scanner;

import java.util.function.Predicate;

public record ScanPattern(
        String category,
        String name,
        byte[] signature,
        int pointerOffset,
        ReadMode readMode,
        Predicate<Integer> validator
) {
    public enum ReadMode {
        BYTE,
        BIG_ENDIAN_16,
        BIG_ENDIAN_32
    }

    public String key() {
        return category + "." + name;
    }
}
```

`ScanResult.java`:
```java
package uk.co.jamesj999.sonic.game.profile.scanner;

public record ScanResult(int value, int foundAtOffset) {}
```

`RomPatternScanner.java`:
```java
package uk.co.jamesj999.sonic.game.profile.scanner;

import java.util.*;
import java.util.logging.Logger;

public class RomPatternScanner {
    private static final Logger LOGGER = Logger.getLogger(RomPatternScanner.class.getName());
    private final List<ScanPattern> patterns = new ArrayList<>();

    public void registerPattern(ScanPattern pattern) {
        patterns.add(pattern);
    }

    /**
     * Scan the ROM for all registered patterns, skipping already-resolved keys.
     * @param rom the full ROM byte array
     * @param alreadyResolved keys that don't need scanning (e.g., "level.ADDR")
     * @return map of "category.name" -> ScanResult for each found pattern
     */
    public Map<String, ScanResult> scan(byte[] rom, Set<String> alreadyResolved) {
        Map<String, ScanResult> results = new HashMap<>();

        for (ScanPattern pattern : patterns) {
            if (alreadyResolved.contains(pattern.key())) {
                continue;
            }

            int matchOffset = findPattern(rom, pattern.signature(), 0, rom.length);
            if (matchOffset < 0) {
                LOGGER.fine("Pattern not found: " + pattern.key());
                continue;
            }

            int readOffset = matchOffset + pattern.pointerOffset();
            if (readOffset < 0 || readOffset >= rom.length) {
                continue;
            }

            int value = readValue(rom, readOffset, pattern.readMode());

            if (pattern.validator() != null && !pattern.validator().test(value)) {
                LOGGER.fine("Validator rejected value for " + pattern.key() + ": 0x" + Integer.toHexString(value));
                continue;
            }

            results.put(pattern.key(), new ScanResult(value, matchOffset));
        }

        LOGGER.info("Pattern scan found " + results.size() + "/" + patterns.size() + " addresses");
        return results;
    }

    private int findPattern(byte[] rom, byte[] pattern, int start, int end) {
        int limit = Math.min(end, rom.length) - pattern.length;
        for (int i = start; i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (rom[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private int readValue(byte[] rom, int offset, ScanPattern.ReadMode mode) {
        return switch (mode) {
            case BYTE -> rom[offset] & 0xFF;
            case BIG_ENDIAN_16 -> ((rom[offset] & 0xFF) << 8) | (rom[offset + 1] & 0xFF);
            case BIG_ENDIAN_32 -> ((rom[offset] & 0xFF) << 24) | ((rom[offset + 1] & 0xFF) << 16)
                    | ((rom[offset + 2] & 0xFF) << 8) | (rom[offset + 3] & 0xFF);
        };
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestRomPatternScanner -pl .`
Expected: 2 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/profile/scanner/ src/test/java/uk/co/jamesj999/sonic/game/profile/scanner/
git commit -m "feat: add generalized ROM pattern scanner framework"
```

---

### Task 10: Wire Scanner into Resolver Startup

Connect the pattern scanner into the resolver's initialization sequence so it fills in addresses missing from profiles.

**Files:**
- Modify: `src/main/java/uk/co/jamesj999/sonic/game/GameModule.java` (add `registerScanPatterns()`)
- Modify: `src/main/java/uk/co/jamesj999/sonic/game/GameModuleRegistry.java` (call scanner)
- Test: `src/test/java/uk/co/jamesj999/sonic/game/profile/scanner/TestScannerIntegration.java`

**Step 1: Write failing test**

```java
package uk.co.jamesj999.sonic.game.profile.scanner;

import org.junit.jupiter.api.Test;
import uk.co.jamesj999.sonic.game.profile.RomAddressResolver;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

public class TestScannerIntegration {

    @Test
    void testScannedAddressesFillGaps() {
        RomAddressResolver.resetInstance();
        RomAddressResolver resolver = RomAddressResolver.getInstance();

        // Initialize with no profile, one default
        resolver.initialize(null, Map.of("level.KNOWN", 0x1234));

        // Simulate scanner finding a new address
        resolver.addScannedAddresses(Map.of("level.SCANNED_ADDR", 0xABCD));

        assertEquals(0x1234, resolver.getAddress("level", "KNOWN"));
        assertEquals(0xABCD, resolver.getAddress("level", "SCANNED_ADDR"));

        // Verify report counts
        var report = resolver.getReport();
        assertEquals(1, report.fromDefaults());
        assertEquals(1, report.fromScanned());
    }

    @Test
    void testScanDoesNotOverrideProfile() {
        RomAddressResolver.resetInstance();
        RomAddressResolver resolver = RomAddressResolver.getInstance();

        // Profile has a value
        uk.co.jamesj999.sonic.game.profile.RomProfile profile =
                new uk.co.jamesj999.sonic.game.profile.RomProfile();
        profile.putAddress("level", "ADDR",
                new uk.co.jamesj999.sonic.game.profile.AddressEntry(0x1111, "verified"));

        resolver.initialize(profile, Map.of());

        // Scanner tries to override
        resolver.addScannedAddresses(Map.of("level.ADDR", 0x9999));

        // Profile value wins
        assertEquals(0x1111, resolver.getAddress("level", "ADDR"));
    }
}
```

**Step 2: Run test to verify it fails (or passes if addScannedAddresses already works)**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest=TestScannerIntegration -pl .`
Expected: Should pass since `addScannedAddresses` was already implemented in Task 4

**Step 3: Add `registerScanPatterns()` to GameModule**

Add to `GameModule.java`:
```java
/** Register game-specific scan patterns for address discovery. */
default void registerScanPatterns(uk.co.jamesj999.sonic.game.profile.scanner.RomPatternScanner scanner) {
    // Default: no patterns. Override in game-specific modules.
}
```

**Step 4: Add scanner invocation to GameModuleRegistry.initializeResolver()**

Update the `initializeResolver` method to call the scanner between profile loading and finalization:

```java
// After resolver.initialize(profile, defaults):
RomPatternScanner scanner = new RomPatternScanner();
module.registerScanPatterns(scanner);
byte[] romData = rom.readAllBytes(); // reuse if already loaded for checksum
Set<String> alreadyResolved = /* build from resolver's current keys */;
Map<String, ScanResult> scanned = scanner.scan(romData, alreadyResolved);
Map<String, Integer> scannedAddresses = new HashMap<>();
for (var entry : scanned.entrySet()) {
    scannedAddresses.put(entry.getKey(), entry.getValue().value());
}
resolver.addScannedAddresses(scannedAddresses);
```

**Step 5: Run full test suite**

Run: `mvn test -f "<worktree>/pom.xml"`
Expected: Same baseline

**Step 6: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/GameModule.java src/main/java/uk/co/jamesj999/sonic/game/GameModuleRegistry.java src/test/java/uk/co/jamesj999/sonic/game/profile/scanner/TestScannerIntegration.java
git commit -m "feat: wire pattern scanner into resolver startup sequence"
```

---

### Task 11: Migrate S3K Scanner Patterns

Move the existing `Sonic3kRomScanner` patterns into the new `RomPatternScanner` framework so S3K also benefits from the resolver system.

**Files:**
- Modify: `src/main/java/uk/co/jamesj999/sonic/game/sonic3k/Sonic3kGameModule.java`
- Test: Run existing S3K tests to verify no regression

**Step 1: Implement registerScanPatterns in Sonic3kGameModule**

Study `Sonic3kRomScanner.java` and translate each pattern search into a `ScanPattern` registration. The key patterns to migrate:

- `LEVEL_SIZES_ADDR` - AIZ1 boundary pattern `{0x13, 0x08, 0x60, 0x00, 0x00, 0x00, 0x03, 0x90}`
- `SOLID_TILE_ANGLE_ADDR` - 256-byte angle array
- `LEVEL_LOAD_BLOCK_ADDR` - 24-byte entry structure

Note: Some S3K patterns use cascading discovery (one address leads to the next). For these, keep using `Sonic3kRomScanner` for the complex cascaded logic, but feed its results into the resolver.

**Step 2: Add Sonic3kGameModule.getDefaultOffsets()**

Export `Sonic3kConstants` fields as a Map, similar to `Sonic2Constants.getAllOffsets()`.

**Step 3: Run S3K tests**

Run: `mvn test -f "<worktree>/pom.xml" -Dtest="TestS3kAiz1SpawnStability,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils"`
Expected: All pass

**Step 4: Run full suite**

Run: `mvn test -f "<worktree>/pom.xml"`
Expected: Same baseline

**Step 5: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/game/sonic3k/
git commit -m "feat: integrate S3K address scanning with resolver framework"
```

---

## Phase 3: ROM Profile Introspector Tool

### Task 12: Introspector CLI Framework

Build the standalone tool skeleton that accepts a ROM file and outputs a profile JSON.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/tools/introspector/RomProfileIntrospector.java`
- Create: `src/main/java/uk/co/jamesj999/sonic/tools/introspector/IntrospectionChain.java`
- Create: `src/main/java/uk/co/jamesj999/sonic/tools/introspector/IntrospectionResult.java`

**Step 1: Build CLI framework**

```java
package uk.co.jamesj999.sonic.tools.introspector;

/**
 * Standalone CLI tool that traces ROM pointer chains to generate a profile.
 * Usage: mvn exec:java -Dexec.mainClass="...RomProfileIntrospector"
 *        -Dexec.args="--rom 'file.gen' --output 'file.profile.json'"
 */
public class RomProfileIntrospector {
    public static void main(String[] args) throws Exception {
        // Parse args: --rom, --output, --game (optional, auto-detect)
        // Open ROM
        // Detect game type
        // Run introspection chains for detected game
        // Write profile JSON
    }
}
```

**Step 2: Implement IntrospectionChain interface**

```java
package uk.co.jamesj999.sonic.tools.introspector;

import uk.co.jamesj999.sonic.game.profile.RomProfile;

/** A chain traces one category of pointer references through the ROM. */
public interface IntrospectionChain {
    String category();
    void trace(byte[] rom, RomProfile profile);
}
```

**Step 3: Implement IntrospectionResult record**

```java
package uk.co.jamesj999.sonic.tools.introspector;

public record IntrospectionResult(String key, int value, String confidence, String traceLog) {}
```

This is a framework task - the actual chains are implemented in subsequent tasks.

**Step 4: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/tools/introspector/
git commit -m "feat: add ROM profile introspector CLI framework"
```

---

### Task 13: Level Data Introspection Chain (Sonic 2)

Trace the level data pointer chain for Sonic 2 ROMs. This is the highest-value chain since most hacks modify level data.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/tools/introspector/Sonic2LevelChain.java`
- Test: `src/test/java/uk/co/jamesj999/sonic/tools/introspector/TestSonic2LevelChain.java`

**Step 1: Write failing test (requires S2 ROM)**

```java
package uk.co.jamesj999.sonic.tools.introspector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.profile.RomProfile;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import static org.junit.jupiter.api.Assertions.*;

public class TestSonic2LevelChain {

    @Test
    @EnabledIfSystemProperty(named = "s2.rom.available", matches = "true")
    void testTraceFindsKnownAddresses() throws Exception {
        Rom rom = new Rom();
        rom.open("Sonic The Hedgehog 2 (W) (REV01) [!].gen");
        byte[] romData = rom.readAllBytes();
        rom.close();

        Sonic2LevelChain chain = new Sonic2LevelChain();
        RomProfile profile = new RomProfile();
        chain.trace(romData, profile);

        // Should find the level layout directory at the known address
        var layoutAddr = profile.getAddress("level", "LEVEL_LAYOUT_DIR_ADDR_LOC");
        assertNotNull(layoutAddr);
        assertEquals(Sonic2Constants.LEVEL_LAYOUT_DIR_ADDR_LOC, layoutAddr.value());
    }
}
```

**Step 2: Implement the level chain**

The chain follows Sonic 2's level data structure:
1. Find the level data directory (contains per-zone entries with art/layout/collision pointers)
2. For each zone entry, validate the pointers lead to valid compressed data
3. Extract addresses for layout index, collision, object placement

This uses the knowledge from `docs/s2disasm/` and the existing `Sonic2Constants` as reference for expected structure.

**Step 3: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/tools/introspector/Sonic2LevelChain.java src/test/java/uk/co/jamesj999/sonic/tools/introspector/
git commit -m "feat: add Sonic 2 level data introspection chain"
```

---

### Task 14: Audio Introspection Chain (Sonic 2)

Trace the audio pointer chain.

**Files:**
- Create: `src/main/java/uk/co/jamesj999/sonic/tools/introspector/Sonic2AudioChain.java`

The audio chain traces:
1. Find Z80 driver load instruction → driver ROM address
2. From driver, locate music/SFX pointer tables
3. Validate each pointer leads to a valid SMPS header

**Implementation details are similar to Task 13 - follow pointer chains, validate at each hop.**

**Commit:**
```bash
git add src/main/java/uk/co/jamesj999/sonic/tools/introspector/Sonic2AudioChain.java
git commit -m "feat: add Sonic 2 audio introspection chain"
```

---

### Task 15: Wire Introspector CLI Together

Connect all chains to the main CLI tool so it produces a complete profile.

**Files:**
- Modify: `src/main/java/uk/co/jamesj999/sonic/tools/introspector/RomProfileIntrospector.java`

**Step 1: Implement full CLI**

The main method should:
1. Parse `--rom` and `--output` args
2. Open ROM, detect game type via `RomDetectionService`
3. Select appropriate chains for detected game
4. Run each chain, accumulating results in a `RomProfile`
5. Compute SHA-256 checksum and set metadata
6. Write JSON to output file
7. Print summary: "Generated profile with N addresses across M categories"

**Step 2: Test end-to-end with known S2 ROM**

Run: `mvn exec:java -f "<worktree>/pom.xml" -Dexec.mainClass="uk.co.jamesj999.sonic.tools.introspector.RomProfileIntrospector" -Dexec.args="--rom 'Sonic The Hedgehog 2 (W) (REV01) [!].gen' --output test-profile.json" -q`

Expected: Generates a JSON profile. Spot-check addresses against known constants.

**Step 3: Commit**

```bash
git add src/main/java/uk/co/jamesj999/sonic/tools/introspector/RomProfileIntrospector.java
git commit -m "feat: wire introspector CLI with all chains"
```

---

## Phase 4: Zone Behavior Mapping

### Task 16: Zone Behavior Indirection

Add the indirection layer between zone slot IDs and engine behavior selection.

**Files:**
- Modify: Relevant zone loading code (where `ZoneRegistry`/`ScrollHandlerProvider` are queried)
- The profile's `zones` section is already loaded by the resolver (Task 4)

**Step 1: Add zone behavior lookup to relevant call sites**

When the engine loads a zone, it should check `resolver.getZoneBehavior(zoneId)` and use that string to look up the scroll handler, event manager, and object registry instead of using the raw zone ID.

**Step 2: Add generic fallback behaviors**

When `getZoneBehavior()` returns null:
- Use a `GenericScrollHandler` (basic horizontal parallax)
- Skip level event initialization
- Use base object registry (rings, springs, monitors)

**Step 3: Run full test suite**

Run: `mvn test -f "<worktree>/pom.xml"`
Expected: Same baseline

**Step 4: Commit**

```bash
git commit -m "feat: add zone behavior indirection layer for ROM hack support"
```

---

## Phase 5: User Override Support

### Task 17: User Override File Loading

Complete the user override file loading path. The resolver checks for `<rom-filename>.profile.json` next to the ROM file before checking shipped profiles.

**Files:**
- Modify: `src/main/java/uk/co/jamesj999/sonic/game/GameModuleRegistry.java`
- Modify: `src/main/java/uk/co/jamesj999/sonic/data/RomManager.java` (expose ROM file path)

**Step 1: Expose ROM path from RomManager**

Add a `getRomPath()` method to `RomManager` that returns the filesystem path of the loaded ROM.

**Step 2: Check for user override file in initializeResolver**

Before checking classpath profiles:
```java
Path romPath = Path.of(RomManager.getInstance().getRomPath());
Path userOverride = romPath.resolveSibling(romPath.getFileName() + ".profile.json");
RomProfile profile = loader.loadFromFile(userOverride);
if (profile == null) {
    profile = loader.loadFromClasspath(checksum);
}
```

**Step 3: Test with a manually created override file**

Create a test JSON file next to a ROM and verify the engine picks it up at startup.

**Step 4: Commit**

```bash
git commit -m "feat: support user override profile files next to ROM"
```

---

## Final Verification

### Task 18: Full Regression Test

**Step 1:** Run the complete test suite

Run: `mvn test -f "<worktree>/pom.xml"`
Expected: 862 run, 0 failures (same as baseline)

**Step 2:** Manual smoke test - launch the engine with each ROM type and verify gameplay works

**Step 3:** Verify the shipped S2 profile matches all hardcoded constants exactly

```java
// Quick verification: iterate Sonic2Constants.getAllOffsets() and check each
// value matches what the resolver returns
```
