package com.openggf.tests.graphics;

import com.openggf.graphics.pipeline.RenderCommand;
import com.openggf.graphics.pipeline.RenderOrderRecorder;
import com.openggf.graphics.pipeline.RenderPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for render order compliance.
 * These tests ensure the rendering pipeline maintains correct ordering:
 * SCENE â†’ OVERLAY â†’ FADE_PASS
 */
public class RenderOrderTest {
    
    private RenderOrderRecorder recorder;
    
    @BeforeEach
    public void setUp() {
        recorder = RenderOrderRecorder.getInstance();
        recorder.clear();
        recorder.setEnabled(true);
    }
    
    @Test
    public void testRecorderDisabledByDefault() {
        RenderOrderRecorder fresh = new RenderOrderRecorder() {
            // Create new instance to test default state
        };
        assertFalse(fresh.isEnabled(), "New RenderOrderRecorder should be disabled by default");
        assertNotNull(RenderOrderRecorder.getInstance());
    }
    
    @Test
    public void testRecordingWhenEnabled() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<RenderCommand> commands = recorder.getCommands();
        assertEquals(3, commands.size());
        assertEquals(RenderPhase.SCENE, commands.get(0).phase());
        assertEquals(RenderPhase.OVERLAY, commands.get(1).phase());
        assertEquals(RenderPhase.FADE_PASS, commands.get(2).phase());
    }
    
    @Test
    public void testNoRecordingWhenDisabled() {
        recorder.setEnabled(false);
        recorder.record(RenderPhase.SCENE, "Level");
        
        assertTrue(recorder.getCommands().isEmpty());
    }
    
    @Test
    public void testClearRemovesAllCommands() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        assertFalse(recorder.getCommands().isEmpty());
        
        recorder.clear();
        assertTrue(recorder.getCommands().isEmpty());
    }
    
    @Test
    public void testOrderIndexIncrementsCorrectly() {
        recorder.clear();
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<RenderCommand> commands = recorder.getCommands();
        assertEquals(0, commands.get(0).orderIndex());
        assertEquals(1, commands.get(1).orderIndex());
        assertEquals(2, commands.get(2).orderIndex());
    }
    
    @Test
    public void testVerifyOrder_correctOrder() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<String> violations = recorder.verifyOrder();
        assertTrue(violations.isEmpty(), "Correct order should have no violations");
    }
    
    @Test
    public void testVerifyOrder_fadeBeforeOverlay() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.FADE_PASS, "Fade"); // Wrong order!
        recorder.record(RenderPhase.OVERLAY, "HUD");
        
        List<String> violations = recorder.verifyOrder();
        assertFalse(violations.isEmpty(), "Should detect order violation");
        assertTrue(violations.get(0).contains("HUD"), "Should mention HUD");
    }
    
    @Test
    public void testVerifyOrder_overlayBeforeScene() {
        recorder.record(RenderPhase.OVERLAY, "HUD"); // Wrong order!
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<String> violations = recorder.verifyOrder();
        assertFalse(violations.isEmpty(), "Should detect order violation");
    }
    
    @Test
    public void testFadeRenderedLast_correct() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        assertTrue(recorder.fadeRenderedLast());
    }
    
    @Test
    public void testFadeRenderedLast_incorrect() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        recorder.record(RenderPhase.OVERLAY, "HUD"); // HUD after fade is wrong
        
        assertFalse(recorder.fadeRenderedLast());
    }
    
    @Test
    public void testFadeRenderedLast_emptyIsTrue() {
        assertTrue(recorder.fadeRenderedLast(), "Empty recorder should return true");
    }
    
    @Test
    public void testFadeRenderedLast_noFade() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        
        // No fade recorded - last is OVERLAY
        assertFalse(recorder.fadeRenderedLast());
    }
    
    @Test
    public void testRenderCommandRecord() {
        RenderCommand cmd = RenderCommand.of(RenderPhase.SCENE, "Level", 0);
        
        assertEquals(RenderPhase.SCENE, cmd.phase());
        assertEquals("Level", cmd.component());
        assertEquals(0, cmd.orderIndex());
    }
    
    @Test
    public void testRenderPhaseOrdering() {
        // Verify enum ordinals are correct for comparison
        assertTrue(RenderPhase.SCENE.ordinal() < RenderPhase.OVERLAY.ordinal());
        assertTrue(RenderPhase.OVERLAY.ordinal() < RenderPhase.FADE_PASS.ordinal());
    }
    
    @Test
    public void testMultipleSceneComponentsAllowed() {
        recorder.record(RenderPhase.SCENE, "Background");
        recorder.record(RenderPhase.SCENE, "Sprites");
        recorder.record(RenderPhase.SCENE, "Foreground");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<String> violations = recorder.verifyOrder();
        assertTrue(violations.isEmpty(), "Multiple SCENE components should be allowed");
    }
    
    @Test
    public void testMultipleOverlayComponentsAllowed() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.OVERLAY, "Debug");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<String> violations = recorder.verifyOrder();
        assertTrue(violations.isEmpty(), "Multiple OVERLAY components should be allowed");
    }
}


