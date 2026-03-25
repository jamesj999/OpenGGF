package com.openggf.graphics;

import com.openggf.camera.Camera;
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
        GraphicsManager.getInstance().resetState();
        setPrivateField(GraphicsManager.getInstance(), "uiRenderPipeline", null);
    }

    @Test
    public void testRebindRuntimeFadeManagerUpdatesPipelineReference() throws Exception {
        RuntimeManager.destroyCurrent();
        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        graphicsManager.resetState();

        FadeManager bootstrapFade = graphicsManager.getFadeManager();
        setPrivateField(graphicsManager, "camera", Camera.getInstance());
        UiRenderPipeline pipeline = new UiRenderPipeline(graphicsManager);
        pipeline.setFadeManager(bootstrapFade);
        setPrivateField(graphicsManager, "uiRenderPipeline", pipeline);

        RuntimeManager.createGameplay();
        FadeManager runtimeFade = RuntimeManager.getCurrent().getFadeManager();
        Camera runtimeCamera = RuntimeManager.getCurrent().getCamera();

        graphicsManager.rebindRuntimeFadeManager();

        assertSame("GraphicsManager should switch to the runtime FadeManager",
                runtimeFade, graphicsManager.getFadeManager());
        assertSame("UiRenderPipeline should also use the runtime FadeManager",
                runtimeFade, pipeline.getFadeManager());
        assertSame("GraphicsManager should switch to the runtime Camera",
                runtimeCamera, getPrivateField(graphicsManager, "camera"));
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
