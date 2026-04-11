package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Chunk;
import com.openggf.level.SolidTile;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAiz1SpindashLoopTraversal {
    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;
    private static final short START_X = (short) 8561;
    private static final short START_Y = (short) 1093;
    private static final int PASS_X = 9029;
    private static final int TIMEOUT_FRAMES = 180;
    private static final short SPINDASH_GSPEED = 0x800;

    private static Object oldSkipIntros, oldMainCharacter, oldSidekickCharacter;
    private static SharedLevel sharedLevel;
    private HeadlessTestFixture fixture;
    private Sonic sprite;

    @BeforeAll
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_1);
    }

    @AfterAll
    public static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @BeforeEach
    public void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        sprite = (Sonic) fixture.sprite();
        AizHollowTreeObjectInstance.resetTreeRevealCounter();
        GameServices.level().getObjectManager().reset(0);
    }

    @Test
    public void aiz1SpindashLoop_traversesLoopWithin180Frames() {
        teleportToStart();
        assertTrue(!sprite.getAir(), "Sonic should be grounded after teleport");

        // Dump collision tile heights at the two floor step transitions
        dumpStepTransition(8688, 1152, 8704, 1168, "Step 1 (Y=1152â†’1168)");
        dumpStepTransition(8752, 1168, 8768, 1184, "Step 2 (Y=1168â†’1184)");

        for (int frame = 0; frame < TIMEOUT_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (sprite.getX() >= 8830 || sprite.getAir()) {
                int cx = sprite.getCentreX();
                int cy = sprite.getCentreY();
                var lm = GameServices.level();
                // Check where the RIGHTWALL ground sensor would probe
                int xRad = sprite.getRolling() ? 7 : 9;
                int yRad = sprite.getRolling() ? 14 : 19;
                int probeRightX = cx + xRad; // RIGHTWALL sensor probes at cx + xRadius
                int probeRightY = cy;
                var cdRight = lm.getChunkDescAt((byte) 0, probeRightX, probeRightY);
                String right = cdRight != null
                        ? String.format("chunk=%d pri=%s raw=0x%04X", cdRight.getChunkIndex(),
                                cdRight.getPrimaryCollisionMode(), cdRight.get())
                        : "null";
                System.err.printf("  f%d: x=%d y=%d cx=%d cy=%d gSpd=%d ang=0x%02X mode=%s "
                        + "probeRight=(%d,%d) â†’ %s%n",
                    frame, sprite.getX(), sprite.getY(), cx, cy, sprite.getGSpeed(),
                    sprite.getAngle() & 0xFF, sprite.getGroundMode(),
                    probeRightX, probeRightY, right);
                if (sprite.getAir()) {
                    // Dump the block at the failure position
                    int blockX = probeRightX / 128;
                    int blockY = probeRightY / 128;
                    System.err.printf("  FAILURE block(%d,%d): ", blockX, blockY);
                    var level = lm.getCurrentLevel();
                    int mapVal = level.getMap().getValue(0, blockX, blockY) & 0xFF;
                    System.err.printf("mapVal=%d%n", mapVal);
                    // Dump all chunks in this block
                    if (mapVal < level.getBlockCount()) {
                        var block = level.getBlock(mapVal);
                        if (block != null) {
                            for (int by = 0; by < 8; by++) {
                                for (int bx = 0; bx < 8; bx++) {
                                    var cd = block.getChunkDesc(bx, by);
                                    if (cd != null && cd.getChunkIndex() != 0) {
                                        System.err.printf("    (%d,%d) chunk=%d pri=%s sec=%s%n",
                                                bx, by, cd.getChunkIndex(),
                                                cd.getPrimaryCollisionMode(), cd.getSecondaryCollisionMode());
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
            if (sprite.getX() >= PASS_X) return;
        }

        assertTrue(sprite.getX() >= PASS_X, "Expected Sonic to pass X=" + PASS_X + " within " + TIMEOUT_FRAMES
                + " frames. " + describeState(TIMEOUT_FRAMES));
    }

    private void dumpStepTransition(int exitX, int exitY, int enterX, int enterY, String label) {
        var lm = GameServices.level();
        var level = lm.getCurrentLevel();
        StringBuilder sb = new StringBuilder(label + ":\n");

        // Exit chunk (last chunk of upper floor)
        ChunkDesc exitCd = lm.getChunkDescAt((byte) 0, exitX, exitY);
        if (exitCd != null && exitCd.getChunkIndex() != 0) {
            Chunk exitChunk = level.getChunk(exitCd.getChunkIndex());
            int colIdx = exitChunk.getSolidTileIndex();
            if (colIdx > 0 && colIdx < level.getSolidTileCount()) {
                SolidTile tile = level.getSolidTile(colIdx);
                sb.append(String.format("  Exit (%d,%d) chunk=%d colIdx=%d hFlip=%b vFlip=%b heights: ",
                        exitX, exitY, exitCd.getChunkIndex(), colIdx, exitCd.getHFlip(), exitCd.getVFlip()));
                for (int i = 0; i < 16; i++) sb.append(tile.getHeightAt((byte) i)).append(" ");
                sb.append(String.format("angle=0x%02X%n", tile.getAngle() & 0xFF));
            }
        }

        // Enter chunk (first chunk of lower floor)
        ChunkDesc enterCd = lm.getChunkDescAt((byte) 0, enterX, enterY);
        if (enterCd != null && enterCd.getChunkIndex() != 0) {
            Chunk enterChunk = level.getChunk(enterCd.getChunkIndex());
            int colIdx = enterChunk.getSolidTileIndex();
            if (colIdx > 0 && colIdx < level.getSolidTileCount()) {
                SolidTile tile = level.getSolidTile(colIdx);
                sb.append(String.format("  Enter (%d,%d) chunk=%d colIdx=%d hFlip=%b vFlip=%b heights: ",
                        enterX, enterY, enterCd.getChunkIndex(), colIdx, enterCd.getHFlip(), enterCd.getVFlip()));
                for (int i = 0; i < 16; i++) sb.append(tile.getHeightAt((byte) i)).append(" ");
                sb.append(String.format("angle=0x%02X%n", tile.getAngle() & 0xFF));
            }
        }
        System.err.print(sb);
    }

    private void teleportToStart() {
        sprite.setX(START_X);
        sprite.setY(START_Y);
        sprite.setXSpeed(SPINDASH_GSPEED);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed(SPINDASH_GSPEED);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);
        sprite.setAir(false);
        sprite.setRolling(true);
        sprite.setJumping(false);
        sprite.setControlLocked(false);
        sprite.setObjectControlled(false);
        sprite.setObjectMappingFrameControl(false);
        sprite.setForcedAnimationId(-1);

        Camera camera = fixture.camera();
        camera.updatePosition(true);
        sprite.updateSensors(sprite.getX(), sprite.getY());
        GameServices.collision().resolveGroundAttachment(sprite, 14, () -> false);
        sprite.setAir(false);
        GameServices.level().getObjectManager().reset(camera.getX());
    }

    private String describeState(int frame) {
        return "frame=" + frame + " x=" + sprite.getX() + " y=" + sprite.getY()
                + " gSpeed=" + sprite.getGSpeed() + " air=" + sprite.getAir()
                + " rolling=" + sprite.getRolling()
                + " angle=0x" + Integer.toHexString(sprite.getAngle() & 0xFF);
    }
}


