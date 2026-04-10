package com.openggf.graphics;

import com.openggf.camera.Camera;
import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertSame;

public class TestGraphicsManagerFadeRebinding {

    @After
    public void tearDown() throws Exception {
        RuntimeManager.destroyCurrent();
        GraphicsManager graphicsManager = EngineServices.fromLegacySingletonsForBootstrap().graphics();
        graphicsManager.resetState();
        setPrivateField(graphicsManager, "uiRenderPipeline", null);
    }

    @Test
    public void testGetFadeManagerRebindsRuntimeManagedReferences() throws Exception {
        RuntimeManager.destroyCurrent();
        GraphicsManager graphicsManager = EngineServices.fromLegacySingletonsForBootstrap().graphics();
        graphicsManager.resetState();

        FadeManager bootstrapFade = graphicsManager.getFadeManager();
        Camera bootstrapCamera = (Camera) getPrivateField(graphicsManager, "camera");
        setPrivateField(graphicsManager, "camera", bootstrapCamera);
        UiRenderPipeline pipeline = new UiRenderPipeline(graphicsManager);
        pipeline.setFadeManager(bootstrapFade);
        setPrivateField(graphicsManager, "uiRenderPipeline", pipeline);

        RuntimeManager.createGameplay();
        FadeManager runtimeFade = RuntimeManager.getCurrent().getFadeManager();
        Camera runtimeCamera = RuntimeManager.getCurrent().getCamera();

        FadeManager resolvedFade = graphicsManager.getFadeManager();

        assertSame("GraphicsManager should switch to the runtime FadeManager",
                runtimeFade, resolvedFade);
        assertSame("UiRenderPipeline should also use the runtime FadeManager",
                runtimeFade, pipeline.getFadeManager());
        assertSame("GraphicsManager should switch to the runtime Camera",
                runtimeCamera, getPrivateField(graphicsManager, "camera"));
    }

    @Test
    public void testGetFadeManagerProvidesBootstrapDependenciesBeforeRuntime() throws Exception {
        RuntimeManager.destroyCurrent();
        GraphicsManager graphicsManager = EngineServices.fromLegacySingletonsForBootstrap().graphics();
        graphicsManager.resetState();

        FadeManager resolvedFade = graphicsManager.getFadeManager();
        Camera resolvedCamera = (Camera) getPrivateField(graphicsManager, "camera");

        assertSame("Pre-game rendering should use the bootstrap FadeManager",
                resolvedFade, graphicsManager.getFadeManager());
        assertSame("Pre-game rendering should use the bootstrap Camera",
                resolvedCamera, getPrivateField(graphicsManager, "camera"));
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
