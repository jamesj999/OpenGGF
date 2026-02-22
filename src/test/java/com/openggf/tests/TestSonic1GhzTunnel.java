package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic1.Sonic1Level;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.*;

/**
 * Headless integration test for Sonic 1 GHZ S-tube tunnel traversal.
 *
 * Reproduces a bug where Sonic enters the tunnel, rolls, but then moves
 * upward and gets stuck in the ceiling of the tunnel instead of following
 * the terrain through the narrow curved pipe.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1GhzTunnel {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner testRunner;
    private Sonic1Level s1Level;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

        // Place Sonic at a temporary position; we'll move him after finding the tunnel
        sprite = new Sonic(mainCode, (short) 0x0050, (short) 0x0300);

        SpriteManager spriteManager = SpriteManager.getInstance();
        spriteManager.addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        // Load GHZ Act 1 (zone 0, act 0)
        LevelManager.getInstance().loadZoneAndAct(0, 0);
        GroundSensor.setLevelManager(LevelManager.getInstance());
        Camera.getInstance().updatePosition(true);

        Level level = LevelManager.getInstance().getCurrentLevel();
        assertTrue("Level should be Sonic1Level", level instanceof Sonic1Level);
        s1Level = (Sonic1Level) level;

        testRunner = new HeadlessTestRunner(sprite);
    }

    /**
     * Finds the first tunnel tile (GHZ_ROLL1 or GHZ_ROLL2) in the FG layout.
     * Returns {mapX, mapY} or null if not found.
     */
    private int[] findFirstTunnelTile() {
        // Scan through the FG layout looking for tunnel tiles
        // The layout is indexed by 256x256 block cells
        for (int mapY = 0; mapY < 8; mapY++) {
            for (int mapX = 0; mapX < 64; mapX++) {
                int rawValue = s1Level.getRawFgValue(mapX, mapY);
                if (rawValue == Sonic1Constants.GHZ_ROLL1 || rawValue == Sonic1Constants.GHZ_ROLL2) {
                    return new int[]{mapX, mapY};
                }
            }
        }
        return null;
    }

    /**
     * Diagnostic test: prints the state of Sonic as he approaches and enters
     * the first GHZ S-tube tunnel. Helps identify what goes wrong.
     */
    @Test
    public void testTunnelTraversal() throws Exception {
        int[] tunnelCell = findFirstTunnelTile();
        assertNotNull("Should find a tunnel tile in GHZ1 layout", tunnelCell);

        int tunnelMapX = tunnelCell[0];
        int tunnelMapY = tunnelCell[1];
        int blockSize = Sonic1Constants.BLOCK_WIDTH_PX; // 256

        // Calculate pixel position of the tunnel block
        int tunnelPixelX = tunnelMapX * blockSize;
        int tunnelPixelY = tunnelMapY * blockSize;

        System.out.println("=== GHZ1 Tunnel Test ===");
        System.out.printf("First tunnel tile at map cell (%d, %d) = pixel (%d, %d)%n",
                tunnelMapX, tunnelMapY, tunnelPixelX, tunnelPixelY);

        // Place Sonic ~128px to the left of the tunnel block, at a Y that should be
        // on the ground surface above the tunnel entrance.
        // The tunnel is underground, so the ground level above is typically around
        // tunnelPixelY - some offset. We'll place Sonic near the tunnel Y and let
        // the terrain following settle him.
        short startX = (short) (tunnelPixelX - 128);
        // The tunnel is at tunnelMapY blocks; the ground surface should be nearby.
        // Use a Y slightly above the tunnel block center.
        short startY = (short) (tunnelPixelY + 80);

        sprite.setX(startX);
        sprite.setY(startY);
        sprite.setAir(true); // Let him fall to find ground
        Camera.getInstance().updatePosition(true);

        System.out.printf("Initial position: X=%d, Y=%d%n", sprite.getX(), sprite.getY());

        // Let Sonic settle onto the ground (fall and land)
        for (int frame = 0; frame < 30; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            if (!sprite.getAir() && frame > 5) break;
        }

        System.out.printf("After settling: X=%d, Y=%d, air=%b, angle=0x%02X%n",
                sprite.getX(), sprite.getY(), sprite.getAir(), sprite.getAngle() & 0xFF);

        // If still in air, try a different Y
        if (sprite.getAir()) {
            System.out.println("WARNING: Sonic did not land. Trying different Y...");
            sprite.setX(startX);
            sprite.setY((short) (tunnelPixelY - 32));
            sprite.setAir(true);
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) 0);
            sprite.setGSpeed((short) 0);
            for (int frame = 0; frame < 30; frame++) {
                testRunner.stepFrame(false, false, false, false, false);
                if (!sprite.getAir() && frame > 5) break;
            }
            System.out.printf("After re-settling: X=%d, Y=%d, air=%b%n",
                    sprite.getX(), sprite.getY(), sprite.getAir());
        }

        assertFalse("Sonic should be on the ground before approaching tunnel", sprite.getAir());

        short groundY = sprite.getY();
        System.out.printf("Ground Y level: %d (centre Y: %d)%n", groundY, sprite.getCentreY());

        // Give Sonic rightward speed to approach the tunnel
        sprite.setGSpeed((short) 0x600); // ~6 pixels/frame
        sprite.setXSpeed((short) 0x600);

        // Now step frames and log every frame as Sonic approaches and enters the tunnel
        System.out.println("\n=== Frame-by-frame log ===");
        System.out.println("Frame | X      | Y      | CentreY | GSpd  | Angle | Air   | Roll  | Push  | Mode    | TunnelTile");

        short prevY = sprite.getY();
        int framesInTunnel = 0;
        int framesAirborne = 0;
        boolean enteredTunnel = false;
        boolean failedInTunnel = false;

        for (int frame = 0; frame < 200; frame++) {
            testRunner.stepFrame(false, false, false, true, false);

            short x = sprite.getX();
            short y = sprite.getY();
            short centreY = (short) sprite.getCentreY();
            short gSpeed = sprite.getGSpeed();
            int angle = sprite.getAngle() & 0xFF;
            boolean air = sprite.getAir();
            boolean rolling = sprite.getRolling();
            boolean pushing = sprite.getPushing();
            String mode = sprite.getGroundMode().name();

            // Check what tile Sonic's centre is on
            int cx = sprite.getCentreX() & 0xFFFF;
            int cy = sprite.getCentreY() & 0xFFFF;
            int cellX = cx / blockSize;
            int cellY = cy / blockSize;
            int rawTile = s1Level.getRawFgValue(cellX, cellY);
            boolean onTunnelTile = (rawTile == Sonic1Constants.GHZ_ROLL1 || rawTile == Sonic1Constants.GHZ_ROLL2);
            String tileStr = onTunnelTile ? String.format("YES(0x%02X)", rawTile) : String.format("no(0x%02X)", rawTile);

            // Calculate Y delta
            short yDelta = (short) (y - prevY);

            System.out.printf("%5d | %6d | %6d | %7d | %5d | 0x%02X  | %-5b | %-5b | %-5b | %-7s | %s (dY=%d)%n",
                    frame, x, y, centreY, gSpeed, angle, air, rolling, pushing, mode, tileStr, yDelta);

            // Log raw sensor results at critical frames around the curve
            if (frame >= 25 && frame <= 50 && !air) {
                Sensor[] groundSensors = sprite.getGroundSensors();
                if (groundSensors != null && groundSensors.length >= 2) {
                    SensorResult sA = groundSensors[0].scan();
                    SensorResult sB = groundSensors[1].scan();
                    System.out.printf("       Sensors: A(d=%d a=0x%02X tid=%d) B(d=%d a=0x%02X tid=%d)%n",
                            sA != null ? sA.distance() : -99, sA != null ? sA.angle() & 0xFF : 0, sA != null ? sA.tileId() : -1,
                            sB != null ? sB.distance() : -99, sB != null ? sB.angle() & 0xFF : 0, sB != null ? sB.tileId() : -1);
                }
            }

            if (onTunnelTile) {
                enteredTunnel = true;
                framesInTunnel++;
            }

            // Detect the failure: Sonic goes airborne or gets stuck (gSpeed near 0) in the tunnel
            if (enteredTunnel && onTunnelTile) {
                if (air) {
                    framesAirborne++;
                    if (framesAirborne > 5) {
                        System.out.println("*** FAILURE: Sonic went airborne in tunnel for 5+ frames ***");
                        failedInTunnel = true;
                        break;
                    }
                }
                if (!air && Math.abs(gSpeed) < 0x80 && pushing) {
                    System.out.println("*** FAILURE: Sonic stuck pushing in tunnel (gSpeed~0, pushing=true) ***");
                    failedInTunnel = true;
                    break;
                }
            }

            // If Sonic has passed well beyond the tunnel, we can stop
            if (enteredTunnel && !onTunnelTile && framesInTunnel > 10 && x > tunnelPixelX + blockSize + 128) {
                System.out.println("Sonic passed through the tunnel successfully.");
                break;
            }

            // If X hasn't moved in 10 frames, Sonic is stuck
            if (frame > 50 && Math.abs(gSpeed) < 0x40 && !air) {
                System.out.println("*** Sonic appears stuck (very low speed) at frame " + frame + " ***");
                break;
            }

            prevY = y;
        }

        if (enteredTunnel) {
            System.out.printf("%nTunnel frames: %d, Airborne in tunnel: %d%n", framesInTunnel, framesAirborne);
        }

        assertFalse("Sonic should traverse the tunnel without getting stuck or going airborne", failedInTunnel);
        assertTrue("Sonic should have entered the tunnel", enteredTunnel);
    }

}
