package com.openggf.game.save;

import com.openggf.game.GameRuntime;

public record RuntimeSaveContext(GameRuntime runtime, SaveSessionContext saveSessionContext) {}
