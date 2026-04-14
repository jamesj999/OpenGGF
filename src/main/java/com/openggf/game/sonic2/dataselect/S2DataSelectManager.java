package com.openggf.game.sonic2.dataselect;

import com.openggf.game.dataselect.DataSelectSessionController;
import com.openggf.game.dataselect.SimpleDataSelectManager;

public final class S2DataSelectManager extends SimpleDataSelectManager {
    public S2DataSelectManager() {
        this(new DataSelectSessionController(new S2DataSelectProfile()));
    }

    public S2DataSelectManager(DataSelectSessionController controller) {
        super(controller.hostProfile(), controller);
    }
}
