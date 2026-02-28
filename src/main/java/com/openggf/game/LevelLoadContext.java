package com.openggf.game;

import com.openggf.data.Rom;
import com.openggf.level.Level;

/**
 * Mutable context accumulated during level load step execution.
 * <p>
 * Each {@link InitStep} in a {@link LevelInitProfile#levelLoadSteps(LevelLoadContext)} list
 * captures a reference to this context. Steps populate fields as they execute;
 * later steps read values set by earlier ones. This replaces the local variables
 * that were previously threaded through {@code LevelManager.loadLevel()}.
 * <p>
 * The context is created fresh for each level load and discarded afterward.
 */
public class LevelLoadContext {
    private Rom rom;
    private int levelIndex = -1;
    private int zone = -1;
    private int act = -1;
    private Level level;
    private GameModule gameModule;

    public Rom getRom() { return rom; }
    public void setRom(Rom rom) { this.rom = rom; }

    public int getLevelIndex() { return levelIndex; }
    public void setLevelIndex(int levelIndex) { this.levelIndex = levelIndex; }

    public int getZone() { return zone; }
    public void setZone(int zone) { this.zone = zone; }

    public int getAct() { return act; }
    public void setAct(int act) { this.act = act; }

    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }

    public GameModule getGameModule() { return gameModule; }
    public void setGameModule(GameModule gameModule) { this.gameModule = gameModule; }
}
