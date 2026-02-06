package uk.co.jamesj999.sonic.graphics;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;

import java.io.File;
import java.util.List;

import static uk.co.jamesj999.sonic.tests.RomTestUtils.ensureRomAvailable;

/**
 * Utility to find checkpoint positions in each level.
 */
public class CheckpointFinder {
    public static void main(String[] args) throws Exception {
        File romFile = ensureRomAvailable();
        Rom rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        GameModuleRegistry.detectAndSetModule(rom);

        // Enable headless mode (no GL context needed)
        GraphicsManager.getInstance().initHeadless();

        // Create player sprite (required by level loading)
        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        Sonic mainSprite = new Sonic(mainCode, (short) 100, (short) 624);
        SpriteManager.getInstance().addSprite(mainSprite);
        
        // Zone/act pairs to check
        int[][] levels = {
            {0, 0}, {0, 1},  // EHZ
            {1, 0}, {1, 1},  // CPZ
            {3, 0}, {3, 1},  // CNZ
            {4, 0}, {4, 1},  // HTZ
            {5, 0}, {5, 1},  // MCZ
        };
        
        LevelManager lm = LevelManager.getInstance();
        
        for (int[] level : levels) {
            int zone = level[0];
            int act = level[1];
            
            lm.loadZoneAndAct(zone, act);
            Level lvl = lm.getCurrentLevel();
            List<ObjectSpawn> objects = lvl.getObjects();
            
            System.out.println("\n=== Zone " + zone + " Act " + (act + 1) + " ===");
            for (ObjectSpawn obj : objects) {
                if (obj.objectId() == Sonic2ObjectIds.CHECKPOINT) {
                    System.out.printf("  Checkpoint at x=%d (0x%04X), y=%d (0x%04X)%n",
                        obj.x(), obj.x(), obj.y(), obj.y());
                }
            }
        }
    }
}
