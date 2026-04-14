package com.openggf.game.animation.strategies;

import com.openggf.game.GameServices;
import com.openggf.game.animation.ApplyStrategy;
import com.openggf.game.animation.ChannelContext;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.animation.AniPlcScriptState;

import java.util.Objects;

public final class ScriptFramesApplyStrategy implements ApplyStrategy {

    private final AniPlcScriptState script;

    public ScriptFramesApplyStrategy(AniPlcScriptState script) {
        this.script = Objects.requireNonNull(script, "script");
    }

    public ScriptFramesApplyStrategy(AniPlcScriptState script, GraphicsManager graphicsManager) {
        this(script);
        Objects.requireNonNull(graphicsManager, "graphicsManager");
    }

    @Override
    public void apply(ChannelContext context) {
        script.tick(context.level(), GameServices.graphics());
    }
}
