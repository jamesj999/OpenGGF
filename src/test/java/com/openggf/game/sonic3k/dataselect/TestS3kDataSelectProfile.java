package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.DataSelectProvider;
import com.openggf.game.EngineServices;
import com.openggf.game.NoOpDataSelectProvider;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kDataSelectProfile {

    @BeforeAll
    static void configureEngineServices() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void resetRuntime() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void s3kGameModule_exposesDataSelectProvider() {
        Sonic3kGameModule module = new Sonic3kGameModule();
        DataSelectProvider provider = module.getDataSelectProvider();
        assertNotNull(provider);
        assertNotSame(NoOpDataSelectProvider.INSTANCE, provider,
                "S3K should provide a real DataSelectProvider, not the no-op stub");
        assertTrue(provider.getClass().getSimpleName().contains("DataSelect"));
    }
}
