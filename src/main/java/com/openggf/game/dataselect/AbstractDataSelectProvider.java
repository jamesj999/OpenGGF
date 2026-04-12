package com.openggf.game.dataselect;

import com.openggf.game.DataSelectProvider;

/**
 * Base class for data select providers with shared state management.
 * Tracks lifecycle state and a pending action to be consumed by the game loop.
 */
public abstract class AbstractDataSelectProvider implements DataSelectProvider {

    protected State state = State.INACTIVE;
    protected DataSelectAction pendingAction = DataSelectAction.none();

    /**
     * Consumes and returns the pending action, resetting it to {@link DataSelectAction#none()}.
     *
     * @return the action that was pending, or a NONE action if nothing was pending
     */
    public DataSelectAction consumePendingAction() {
        DataSelectAction action = pendingAction;
        pendingAction = DataSelectAction.none();
        return action;
    }
}
