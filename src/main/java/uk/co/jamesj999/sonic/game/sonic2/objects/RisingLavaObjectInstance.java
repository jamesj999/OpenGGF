package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.LevelEventManager;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.ParallaxManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.objects.SlopedSolidProvider;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;

/**
 * Object 30 - Large rising lava platform during earthquake in HTZ.
 * <p>
 * This is an INVISIBLE solid platform whose Y position is controlled by the
 * Camera_BG_Y_offset value from the HTZ earthquake system. Players can stand
 * on these platforms, and for subtype 6, they get hurt while standing.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 49027-49191 (Obj30)
 *
 * <h3>Subtypes</h3>
 * <table border="1">
 *   <tr><th>Subtype</th><th>Width</th><th>Height D2/D3</th><th>Behavior</th></tr>
 *   <tr><td>0</td><td>0xC0 (192)</td><td>0x80/0x81</td><td>Regular solid</td></tr>
 *   <tr><td>2</td><td>0xC0 (192)</td><td>0x80/0x81</td><td>Regular solid</td></tr>
 *   <tr><td>4</td><td>0xC0 (192)</td><td>0x78/0x79</td><td>Lower height solid</td></tr>
 *   <tr><td>6</td><td>0xE0 (224)</td><td>0x78/0x79</td><td>Hurts players standing on it</td></tr>
 *   <tr><td>8</td><td>0x5F (95)</td><td>0x2E</td><td>Sloped solid</td></tr>
 * </table>
 *
 * <h3>Usage Statistics (from OBJECT_CHECKLIST.md)</h3>
 * <ul>
 *   <li>HTZ1: 3 instances [0x00, 0x02, 0x04]</li>
 *   <li>HTZ2: 4 instances [0x06, 0x08]</li>
 * </ul>
 *
 * @see LevelEventManager#getCameraBgYOffset() For earthquake Y offset
 */
public class RisingLavaObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SlopedSolidProvider, SolidObjectListener {

    // ========================================================================
    // ROM Constants - Width table from Obj30_Widths (line 49042)
    // ========================================================================

    /** Width values indexed by subtype. */
    private static final int[] SUBTYPE_WIDTHS = {
            0xC0,  // Subtype 0
            0x00,  // Subtype 1 (padding)
            0xC0,  // Subtype 2
            0x00,  // Subtype 3 (padding)
            0xC0,  // Subtype 4
            0x00,  // Subtype 5 (padding)
            0xE0,  // Subtype 6
            0x00,  // Subtype 7 (padding)
            0xC0,  // Subtype 8
            0x00   // Subtype 9 (padding)
    };

    // ========================================================================
    // ROM Constants - Collision parameters
    // ========================================================================

    /** Standard D1 (half-width) for most subtypes: 0xCB */
    private static final int HALF_WIDTH_STANDARD = 0xCB;

    /** D1 (half-width) for subtype 6: 0xEB */
    private static final int HALF_WIDTH_SUBTYPE_6 = 0xEB;

    /** D2/D3 height values for subtypes 0, 2 */
    private static final int HEIGHT_D2_NORMAL = 0x80;
    private static final int HEIGHT_D3_NORMAL = 0x81;

    /** D2/D3 height values for subtypes 4, 6 */
    private static final int HEIGHT_D2_LOWER = 0x78;
    private static final int HEIGHT_D3_LOWER = 0x79;

    /** D2 height for sloped subtype 8 */
    private static final int HEIGHT_D2_SLOPED = 0x2E;

    // ========================================================================
    // ROM Constants - Slope data from Obj30_SlopeData (lines 49169-49187)
    // 192 bytes: heights from +48 to -48 for the sloped platform
    // ========================================================================

    private static final byte[] SLOPE_DATA = {
            48, 48, 48, 48, 48, 48, 48, 48, 47, 47, 46, 46,
            45, 45, 44, 44, 43, 43, 42, 42, 41, 41, 40, 40,
            39, 39, 38, 38, 37, 37, 36, 36, 35, 35, 34, 34,
            33, 33, 32, 32, 31, 31, 30, 30, 29, 29, 28, 28,
            27, 27, 26, 26, 25, 25, 24, 24, 23, 23, 22, 22,
            21, 21, 20, 20, 19, 19, 18, 18, 17, 17, 16, 16,
            15, 15, 14, 14, 13, 13, 12, 12, 11, 11, 10, 10,
            9, 9, 8, 8, 7, 7, 6, 6, 5, 5, 4, 4,
            3, 3, 2, 2, 1, 1, 0, 0, -1, -1, -2, -2,
            -3, -3, -4, -4, -5, -5, -6, -6, -7, -7, -8, -8,
            -9, -9, -10, -10, -11, -11, -12, -12, -13, -13, -14, -14,
            -15, -15, -16, -16, -17, -17, -18, -18, -19, -19, -20, -20,
            -21, -21, -22, -22, -23, -23, -24, -24, -25, -25, -26, -26,
            -27, -27, -28, -28, -29, -29, -30, -30, -31, -31, -32, -32,
            -33, -33, -34, -34, -35, -35, -36, -36, -37, -37, -38, -38,
            -39, -39, -40, -40, -41, -41, -42, -42, -43, -43, -44, -44,
            -45, -45, -46, -46, -47, -47, -48, -48, -48, -48, -48, -48
    };

    // ========================================================================
    // Instance State
    // ========================================================================

    private final int subtype;
    private final int widthPixels;
    private final int baseY;
    private final int baseX;
    private int currentY;

    /** Dynamic spawn for Y position updates. */
    private ObjectSpawn dynamicSpawn;

    /** Cached frame counter from last update for use in onSolidContact. */
    private int lastFrameCounter;

    public RisingLavaObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);

        this.subtype = spawn.subtype() & 0xFF;
        this.baseY = spawn.y();
        this.baseX = spawn.x();
        this.currentY = baseY;

        // Get width from table (subtype is index)
        int widthIndex = Math.min(subtype, SUBTYPE_WIDTHS.length - 1);
        this.widthPixels = SUBTYPE_WIDTHS[widthIndex];

        updateDynamicSpawn();
    }

    // ========================================================================
    // Update Logic
    // ========================================================================

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Store frame counter for use in onSolidContact callback
        this.lastFrameCounter = frameCounter;

        // ROM: Obj30_Main (line 49083)
        // Y position = base Y + Camera_BG_Y_offset
        int bgYOffset = LevelEventManager.getInstance().getCameraBgYOffset();

        // During screen shake, visual terrain also shifts by shakeOffsetY from ripple data.
        // The collision platform must include this offset to stay synchronized with where
        // the visual terrain appears, preventing invisible walls.
        // ROM: The visual scroll uses vscrollFactorFG = cameraY + shakeOffsetV
        int shakeOffsetY = 0;
        if (GameServices.gameState().isScreenShakeActive()) {
            shakeOffsetY = ParallaxManager.getInstance().getShakeOffsetY();
        }

        currentY = baseY + bgYOffset + shakeOffsetY;

        updateDynamicSpawn();

        // Note: Hurt check for subtype 6 is handled in onSolidContact callback
        // when the player is standing on this platform
    }

    /**
     * Apply hurt to the player (from lava contact).
     * ROM: JmpTo_Touch_ChkHurt (line 49136)
     */
    private void applyHurt(AbstractPlayableSprite player) {
        if (player.getInvulnerable()) {
            return;
        }

        // Check if player has rings
        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield()) {
            LevelManager.getInstance().spawnLostRings(player, lastFrameCounter);
        }
        // Apply hurt - lava is not spikes, so spikeHit = false
        player.applyHurtOrDeath(getX(), false, hadRings);
    }

    // ========================================================================
    // Position Accessors
    // ========================================================================

    @Override
    public int getX() {
        return baseX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
    }

    private void updateDynamicSpawn() {
        if (dynamicSpawn == null || dynamicSpawn.y() != currentY) {
            dynamicSpawn = new ObjectSpawn(
                    baseX,
                    currentY,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord());
        }
    }

    // ========================================================================
    // SolidObjectProvider Implementation
    // ========================================================================

    @Override
    public SolidObjectParams getSolidParams() {
        // Select parameters based on subtype
        int halfWidth;
        int airHalfHeight;
        int groundHalfHeight;

        switch (subtype) {
            case 0:
            case 2:
                // loc_23972: d1=$CB, d2=$80, d3=$81
                halfWidth = HALF_WIDTH_STANDARD;
                airHalfHeight = HEIGHT_D2_NORMAL;
                groundHalfHeight = HEIGHT_D3_NORMAL;
                break;
            case 4:
                // loc_2398A: d1=$CB, d2=$78, d3=$79
                halfWidth = HALF_WIDTH_STANDARD;
                airHalfHeight = HEIGHT_D2_LOWER;
                groundHalfHeight = HEIGHT_D3_LOWER;
                break;
            case 6:
                // loc_239D0: d1=$EB, d2=$78, d3=$79
                halfWidth = HALF_WIDTH_SUBTYPE_6;
                airHalfHeight = HEIGHT_D2_LOWER;
                groundHalfHeight = HEIGHT_D3_LOWER;
                break;
            case 8:
                // loc_239EA: d1=SlopeData.end-SlopeData-1=191, d2=$2E
                // For sloped solids, the width is derived from slope data length
                halfWidth = SLOPE_DATA.length - 1; // 191
                airHalfHeight = HEIGHT_D2_SLOPED;
                groundHalfHeight = HEIGHT_D2_SLOPED + 1;
                break;
            default:
                // Fallback to normal
                halfWidth = HALF_WIDTH_STANDARD;
                airHalfHeight = HEIGHT_D2_NORMAL;
                groundHalfHeight = HEIGHT_D3_NORMAL;
                break;
        }

        return new SolidObjectParams(halfWidth, airHalfHeight, groundHalfHeight);
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // ROM: tst.b (Screen_Shaking_Flag_HTZ).w at line 49091
        // Only solid when HTZ earthquake sequence is active.
        // Uses the HTZ-specific flag which stays on during delay periods,
        // unlike the general Screen_Shaking_Flag which gets cleared.
        return GameServices.gameState().isHtzScreenShakeActive();
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    // ========================================================================
    // SlopedSolidProvider Implementation (for subtype 8)
    // ========================================================================

    @Override
    public byte[] getSlopeData() {
        if (subtype == 8) {
            return SLOPE_DATA;
        }
        return null;
    }

    @Override
    public boolean isSlopeFlipped() {
        return false;
    }

    // ========================================================================
    // SolidObjectListener Implementation
    // ========================================================================

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // For subtype 6, hurt players that land on top
        if (subtype == 6 && contact.standing()) {
            if (!player.getInvulnerable()) {
                applyHurt(player);
            }
        }

        // ROM: JmpTo_DropOnFloor is called for all subtypes after solid collision
        // This is handled by the physics system's normal platform interaction
    }

    // ========================================================================
    // Rendering (Debug only - this is an invisible object)
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible during normal gameplay - only render in debug mode
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        if (!config.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED)) {
            return;
        }

        // Only render when HTZ earthquake is active
        if (!GameServices.gameState().isHtzScreenShakeActive()) {
            return;
        }

        // Debug rendering: red/orange box showing collision area
        SolidObjectParams params = getSolidParams();
        int halfWidth = params.halfWidth();
        int halfHeight = params.groundHalfHeight();

        int x1 = baseX - halfWidth;
        int y1 = currentY - halfHeight;
        int x2 = baseX + halfWidth;
        int y2 = currentY + halfHeight;

        // Red for hurt subtypes, orange for normal
        float r = (subtype == 6) ? 1.0f : 1.0f;
        float g = (subtype == 6) ? 0.0f : 0.5f;
        float b = 0.0f;

        // Semi-transparent fill
        commands.add(new GLCommand(
                GLCommand.CommandType.RECTI,
                GL_TRIANGLE_FAN,
                GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                r, g, b, 0.3f,
                x1, y1, x2, y2));

        // Solid border
        commands.add(new GLCommand(
                GLCommand.CommandType.RECTI,
                GL_TRIANGLE_FAN,
                r, g, b,
                x1, y1, x2, y1 + 1));
        commands.add(new GLCommand(
                GLCommand.CommandType.RECTI,
                GL_TRIANGLE_FAN,
                r, g, b,
                x1, y2 - 1, x2, y2));
        commands.add(new GLCommand(
                GLCommand.CommandType.RECTI,
                GL_TRIANGLE_FAN,
                r, g, b,
                x1, y1, x1 + 1, y2));
        commands.add(new GLCommand(
                GLCommand.CommandType.RECTI,
                GL_TRIANGLE_FAN,
                r, g, b,
                x2 - 1, y1, x2, y2));
    }

    @Override
    public int getPriorityBucket() {
        return 0; // Low priority since usually invisible
    }
}
