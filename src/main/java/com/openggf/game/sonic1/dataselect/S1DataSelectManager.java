package com.openggf.game.sonic1.dataselect;

import com.openggf.game.dataselect.DataSelectSessionController;
import com.openggf.game.dataselect.SimpleDataSelectManager;

public final class S1DataSelectManager extends SimpleDataSelectManager {
    public S1DataSelectManager() {
        this(new DataSelectSessionController(new S1DataSelectProfile()));
    }

    public S1DataSelectManager(DataSelectSessionController controller) {
        super(controller.hostProfile(), controller);
    }
}
