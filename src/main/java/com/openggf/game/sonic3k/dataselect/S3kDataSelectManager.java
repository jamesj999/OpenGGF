package com.openggf.game.sonic3k.dataselect;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.RuntimeManager;
import com.openggf.game.dataselect.DataSelectSessionController;
import com.openggf.game.save.SaveManager;

import java.nio.file.Path;
import java.util.function.IntConsumer;

public final class S3kDataSelectManager extends S3kDataSelectPresentation {
    public S3kDataSelectManager() {
        this(new DataSelectSessionController(new S3kDataSelectProfile()),
                Path.of("saves"),
                RuntimeManager.currentEngineServices().configuration(),
                createDefaultAssets(),
                new S3kDataSelectRenderer(),
                S3kDataSelectPresentation::playMusicSafely);
    }

    public S3kDataSelectManager(Path saveRoot, SonicConfigurationService config) {
        this(new DataSelectSessionController(new S3kDataSelectProfile()), saveRoot, config);
    }

    public S3kDataSelectManager(DataSelectSessionController controller) {
        this(controller,
                Path.of("saves"),
                RuntimeManager.currentEngineServices().configuration(),
                createDefaultAssets(),
                new S3kDataSelectRenderer(),
                S3kDataSelectPresentation::playMusicSafely);
    }

    public S3kDataSelectManager(DataSelectSessionController controller,
                                Path saveRoot, SonicConfigurationService config) {
        super(controller, saveRoot, config);
    }

    S3kDataSelectManager(DataSelectSessionController controller,
                         Path saveRoot,
                         SonicConfigurationService config,
                         S3kDataSelectAssetSource assets,
                         S3kDataSelectRenderer renderer,
                         IntConsumer musicPlayer) {
        super(controller, new SaveManager(saveRoot), config, assets, renderer, musicPlayer);
    }
}
