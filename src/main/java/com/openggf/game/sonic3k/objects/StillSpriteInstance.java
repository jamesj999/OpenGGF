package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Object 0x2F - StillSprite.
 * <p>
 * Static decorative sprite using level VRAM patterns. Each of the 51 subtypes
 * maps to a specific art tile base, palette, priority, and mapping frame from
 * the ROM data table at word_2B968.
 * <p>
 * ROM reference: sonic3k.asm lines 60199-60372
 */
public class StillSpriteInstance extends AbstractObjectInstance {

    private static final int MAX_SUBTYPE = 50;

    /**
     * Per-subtype configuration: art key, local frame within sheet,
     * display priority bucket (ROM priority / 0x80), and high priority flag.
     */
    private record SubtypeInfo(String artKey, int localFrame, int priorityBucket, boolean highPriority) {
    }

    private static final SubtypeInfo[] SUBTYPE_TABLE = buildSubtypeTable();

    private final SubtypeInfo info;
    private PlaceholderObjectInstance placeholder;

    public StillSpriteInstance(ObjectSpawn spawn) {
        super(spawn, "StillSprite");
        int sub = spawn.subtype() & 0xFF;
        if (sub >= 0 && sub < SUBTYPE_TABLE.length && SUBTYPE_TABLE[sub] != null) {
            this.info = SUBTYPE_TABLE[sub];
        } else {
            this.info = null;
        }
    }

    @Override
    public int getPriorityBucket() {
        return info != null ? info.priorityBucket : 6;
    }

    @Override
    public boolean isHighPriority() {
        return info != null && info.highPriority;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (info != null) {
            ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
            if (renderManager != null) {
                PatternSpriteRenderer renderer = renderManager.getRenderer(info.artKey);
                if (renderer != null && renderer.isReady()) {
                    renderer.drawFrameIndex(info.localFrame, getX(), getY(), false, false);
                    return;
                }
            }
        }

        if (placeholder == null) {
            placeholder = new PlaceholderObjectInstance(spawn, name);
        }
        placeholder.appendRenderCommands(commands);
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private static SubtypeInfo[] buildSubtypeTable() {
        SubtypeInfo[] t = new SubtypeInfo[MAX_SUBTYPE + 1];

        // AIZ (subtypes 0-5)
        // base 0x2E9 (AIZMisc2, pal 2): subtypes 0,1,2,5
        t[0]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_AIZ_MISC2, 0, 6, false);
        t[1]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_AIZ_MISC2, 1, 6, false);
        t[2]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_AIZ_MISC2, 2, 6, false);
        t[5]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_AIZ_MISC2, 3, 6, true);
        // base 0x001, pal 2: subtype 3
        t[3]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_AIZ_001, 0, 6, false);
        // base 0x001, pal 3: subtype 4 (waterfall)
        t[4]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_AIZ_WATERFALL, 0, 6, false);

        // HCZ (subtypes 6-10, 15-19)
        // base 0x001, pal 2: subtypes 6-10 (waterfalls)
        t[6]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_001, 0, 0, true);
        t[7]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_001, 1, 0, true);
        t[8]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_001, 2, 6, false);
        t[9]  = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_001, 3, 0, true);
        t[10] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_001, 4, 0, true);
        // HCZ tubes (each a separate art key, pal 2)
        t[15] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_TUBE1, 0, 0, true);
        t[16] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_TUBE2, 0, 0, true);
        t[17] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_TUBE3, 0, 0, true);
        t[18] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_TUBE4, 0, 0, true);
        // HCZ post
        t[19] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_HCZ_POST, 0, 6, false);

        // MGZ (subtypes 11-14)
        t[11] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MGZ, 0, 6, false);
        t[12] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MGZ, 1, 6, false);
        t[13] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MGZ, 2, 6, false);
        t[14] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MGZ, 3, 6, false);

        // LBZ (subtypes 20-23)
        t[20] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LBZ_POLE, 0, 6, false);
        t[21] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LBZ_GIRDER, 0, 6, false);
        t[22] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LBZ_GIRDER, 1, 6, false);
        t[23] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LBZ_GIRDER, 2, 1, false);

        // MHZ (subtypes 24-30)
        t[24] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MHZ_CLIFF, 0, 1, true);
        t[25] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MHZ_CLIFF, 1, 1, true);
        t[26] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MHZ_CLIFF, 2, 1, true);
        t[27] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MHZ_COLUMN, 0, 1, true);
        t[28] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MHZ_COLUMN, 1, 1, true);
        t[29] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MHZ_VINE, 0, 4, false);
        t[30] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_MHZ_PEDESTAL, 0, 5, false);

        // LRZ (subtypes 31-38)
        // base 0x3A1, pal 2: subtypes 31-33 (horizontal rails)
        t[31] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LRZ_RAIL, 0, 3, true);
        t[32] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LRZ_RAIL, 1, 3, true);
        t[33] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LRZ_RAIL, 2, 3, true);
        // base 0x0D3, pal 2: subtype 34
        t[34] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LRZ_ROCK, 0, 1, true);
        // base 0x3A1, pal 1: subtypes 35-38 (vertical gear rails)
        t[35] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LRZ_GEAR, 0, 3, true);
        t[36] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LRZ_GEAR, 1, 3, true);
        t[37] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LRZ_GEAR, 2, 3, true);
        t[38] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_LRZ_GEAR, 3, 3, true);

        // FBZ (subtypes 39-45)
        t[39] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_FBZ_HANGER, 0, 1, false);
        t[40] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_FBZ_HANGER, 1, 1, false);
        t[41] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_FBZ_HANGER, 2, 1, false);
        t[42] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_FBZ_HANGER, 3, 1, false);
        t[43] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_FBZ_EXTRA, 0, 6, false);
        t[44] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_FBZ_RAIL, 0, 0, false);
        t[45] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_FBZ_RAIL, 1, 5, false);

        // SOZ (subtypes 46-47)
        t[46] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_SOZ_001, 0, 2, true);
        t[47] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_SOZ_CORK, 0, 0, false);

        // DEZ (subtypes 48-50)
        t[48] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_DEZ_BEAM, 0, 5, false);
        t[49] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_DEZ_BEAM, 1, 5, false);
        t[50] = new SubtypeInfo(Sonic3kObjectArtKeys.STILL_DEZ_POST, 0, 1, false);

        return t;
    }
}
