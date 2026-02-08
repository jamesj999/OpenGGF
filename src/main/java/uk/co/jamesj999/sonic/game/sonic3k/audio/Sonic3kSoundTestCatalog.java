package uk.co.jamesj999.sonic.game.sonic3k.audio;

import uk.co.jamesj999.sonic.audio.debug.SoundTestCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Sound test catalog for Sonic 3 &amp; Knuckles. Music titles from S3K disassembly,
 * SFX names from known S3K SFX labels.
 */
public final class Sonic3kSoundTestCatalog implements SoundTestCatalog {
    private static final Sonic3kSoundTestCatalog INSTANCE = new Sonic3kSoundTestCatalog();
    private static final Map<Integer, String> TITLE_MAP = buildTitleMap();
    private static final NavigableSet<Integer> VALID_SONGS = new TreeSet<>(TITLE_MAP.keySet());
    private static final Map<Integer, String> SFX_NAMES = buildSfxNames();

    private Sonic3kSoundTestCatalog() {
    }

    public static Sonic3kSoundTestCatalog getInstance() {
        return INSTANCE;
    }

    @Override
    public String lookupTitle(int songId) {
        return TITLE_MAP.get(songId);
    }

    @Override
    public NavigableSet<Integer> getValidSongs() {
        return Collections.unmodifiableNavigableSet(VALID_SONGS);
    }

    @Override
    public Map<Integer, String> getSfxNames() {
        return Collections.unmodifiableMap(SFX_NAMES);
    }

    @Override
    public int getDefaultSongId() {
        return Sonic3kAudioConstants.MUS_AIZ1;
    }

    @Override
    public int getSfxIdBase() {
        return Sonic3kAudioConstants.SFX_ID_BASE;
    }

    @Override
    public int getSfxIdMax() {
        return Sonic3kAudioConstants.SFX_ID_MAX;
    }

    @Override
    public String getGameName() {
        return "Sonic 3&K";
    }

    private static Map<Integer, String> buildTitleMap() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(Sonic3kAudioConstants.MUS_AIZ1, "Angel Island Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_AIZ2, "Angel Island Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_HCZ1, "Hydrocity Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_HCZ2, "Hydrocity Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_MGZ1, "Marble Garden Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_MGZ2, "Marble Garden Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_CNZ1, "Carnival Night Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_CNZ2, "Carnival Night Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_FBZ1, "Flying Battery Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_FBZ2, "Flying Battery Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_ICZ1, "IceCap Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_ICZ2, "IceCap Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_LBZ1, "Launch Base Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_LBZ2, "Launch Base Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_MHZ1, "Mushroom Hill Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_MHZ2, "Mushroom Hill Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_SOZ1, "Sandopolis Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_SOZ2, "Sandopolis Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_LRZ1, "Lava Reef Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_LRZ2, "Lava Reef Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_SSZ, "Sky Sanctuary Zone");
        m.put(Sonic3kAudioConstants.MUS_DEZ1, "Death Egg Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_DEZ2, "Death Egg Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_MINIBOSS, "Mini-Boss");
        m.put(Sonic3kAudioConstants.MUS_BOSS, "Boss");
        m.put(Sonic3kAudioConstants.MUS_DDZ, "Doomsday Zone");
        m.put(Sonic3kAudioConstants.MUS_PACHINKO, "Bonus Stage - Pachinko");
        m.put(Sonic3kAudioConstants.MUS_SPECIAL_STAGE, "Special Stage");
        m.put(Sonic3kAudioConstants.MUS_SLOTS, "Bonus Stage - Slots");
        m.put(Sonic3kAudioConstants.MUS_GUMBALL, "Bonus Stage - Gumball");
        m.put(Sonic3kAudioConstants.MUS_KNUCKLES, "Knuckles' Theme");
        m.put(Sonic3kAudioConstants.MUS_AZURE_LAKE, "Azure Lake (Competition)");
        m.put(Sonic3kAudioConstants.MUS_BALLOON_PARK, "Balloon Park (Competition)");
        m.put(Sonic3kAudioConstants.MUS_DESERT_PALACE, "Desert Palace (Competition)");
        m.put(Sonic3kAudioConstants.MUS_CHROME_GADGET, "Chrome Gadget (Competition)");
        m.put(Sonic3kAudioConstants.MUS_ENDLESS_MINE, "Endless Mine (Competition)");
        m.put(Sonic3kAudioConstants.MUS_TITLE, "Title Screen");
        m.put(Sonic3kAudioConstants.MUS_CREDITS_S3, "Credits (Sonic 3)");
        m.put(Sonic3kAudioConstants.MUS_GAME_OVER, "Game Over");
        m.put(Sonic3kAudioConstants.MUS_CONTINUE, "Continue");
        m.put(Sonic3kAudioConstants.MUS_ACT_CLEAR, "Act Clear");
        m.put(Sonic3kAudioConstants.MUS_EXTRA_LIFE, "Extra Life");
        m.put(Sonic3kAudioConstants.MUS_EMERALD, "Chaos Emerald");
        m.put(Sonic3kAudioConstants.MUS_INVINCIBILITY, "Invincibility");
        m.put(Sonic3kAudioConstants.MUS_COMPETITION_MENU, "Competition Menu");
        m.put(Sonic3kAudioConstants.MUS_MINIBOSS_S3, "Mini-Boss (Sonic 3)");
        m.put(Sonic3kAudioConstants.MUS_DATA_SELECT, "Data Select");
        m.put(Sonic3kAudioConstants.MUS_FINAL_BOSS, "Final Boss");
        m.put(Sonic3kAudioConstants.MUS_DROWNING, "Drowning");
        m.put(Sonic3kAudioConstants.MUS_ENDING, "Ending");
        return m;
    }

    private static Map<Integer, String> buildSfxNames() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0xA0, "Jump");
        m.put(0xA1, "Checkpoint");
        m.put(0xA2, "Hurt");
        m.put(0xA3, "Skid");
        m.put(0xA4, "Spike Hit");
        m.put(0xA5, "Push Block");
        m.put(0xA6, "Hurt Boss");
        m.put(0xA7, "Bubbles");
        m.put(0xA8, "Smash");
        m.put(0xA9, "Shield");
        m.put(0xAA, "Splash");
        m.put(0xAB, "Drown");
        m.put(0xAC, "Air Ding");
        m.put(0xAD, "Ring");
        m.put(0xAE, "Roll");
        m.put(0xAF, "Destroy");
        m.put(0xB0, "Explosion");
        m.put(0xB1, "Warning");
        m.put(0xB2, "Boss Explode");
        m.put(0xB3, "Tally");
        m.put(0xB4, "Ring Loss");
        m.put(0xB5, "Spring");
        m.put(0xB6, "Signpost");
        m.put(0xB7, "Bubble Shield");
        m.put(0xB8, "Flame Shield");
        m.put(0xB9, "Lightning Shield");
        m.put(0xBA, "Insta-Shield");
        m.put(0xBB, "Bubble Bounce");
        m.put(0xBC, "Flame Dash");
        m.put(0xBD, "Lightning Jump");
        m.put(0xBE, "Spindash Charge");
        m.put(0xBF, "Spindash Release");
        m.put(0xC0, "Super Transform");
        m.put(0xC1, "Rumbling");
        m.put(0xC2, "Flamethrower");
        m.put(0xC3, "Door");
        m.put(0xC4, "Switch");
        m.put(0xC5, "Stomp");
        m.put(0xC6, "Collapse");
        m.put(0xC7, "Electric");
        m.put(0xC8, "Slide");
        m.put(0xC9, "Pop");
        m.put(0xCA, "Giant Ring");
        m.put(0xCB, "Miniboss Hit");
        m.put(0xCC, "SS Ring");
        m.put(0xCD, "Teleport");
        m.put(0xCE, "Wall Smash");
        m.put(0xCF, "Ring (Left)");
        for (int id = 0xD0; id <= 0xFF; id++) {
            if (!m.containsKey(id)) {
                m.put(id, "SFX 0x" + Integer.toHexString(id).toUpperCase());
            }
        }
        return m;
    }
}
