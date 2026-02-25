package com.openggf.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.timer.AbstractTimer;
import com.openggf.timer.TimerManager;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * TODO #30 coverage: Timer error reporting.
 *
 * <p>Verifies that when a timer's {@code perform()} returns false, the error is
 * logged at WARNING level with a meaningful message including the timer code
 * and class name. The timer is removed regardless of success/failure.
 */
public class TestTodo30_TimerErrorReporting {
    private TimerManager manager;

    @Before
    public void setUp() {
        manager = TimerManager.getInstance();
        manager.resetState();
    }

    @After
    public void tearDown() {
        manager.resetState();
    }

    /** A timer that always fails (returns false from perform()). */
    private static class FailingTimer extends AbstractTimer {
        boolean performCalled = false;

        FailingTimer(String code) {
            super(code, 1); // expires after 1 tick
        }

        @Override
        public boolean perform() {
            performCalled = true;
            return false; // simulate failure
        }
    }

    /** A timer that always succeeds. */
    private static class SucceedingTimer extends AbstractTimer {
        boolean performCalled = false;

        SucceedingTimer(String code) {
            super(code, 1);
        }

        @Override
        public boolean perform() {
            performCalled = true;
            return true;
        }
    }

    @Test
    public void testFailedTimerIsRemovedAfterPerform() {
        FailingTimer timer = new FailingTimer("FAIL_TEST");
        manager.registerTimer(timer);
        assertNotNull("Timer should be registered", manager.getTimerForCode("FAIL_TEST"));

        // Update to trigger the timer (1 tick -> expires)
        manager.update();

        assertTrue("perform() should have been called", timer.performCalled);
        assertNull("Failed timer should still be removed",
                manager.getTimerForCode("FAIL_TEST"));
    }

    @Test
    public void testSuccessfulTimerIsRemoved() {
        SucceedingTimer timer = new SucceedingTimer("SUCCESS_TEST");
        manager.registerTimer(timer);
        manager.update();

        assertTrue("perform() should have been called", timer.performCalled);
        assertNull("Successful timer should be removed",
                manager.getTimerForCode("SUCCESS_TEST"));
    }

    @Test
    public void testFailedTimerLogsErrorMessage() {
        // Capture log output to verify the error message content
        Logger logger = Logger.getLogger(TimerManager.class.getName());
        Level originalLevel = logger.getLevel();

        final StringBuilder logCapture = new StringBuilder();
        Handler testHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logCapture.append(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        testHandler.setLevel(Level.ALL);
        logger.addHandler(testHandler);
        logger.setLevel(Level.ALL);

        try {
            FailingTimer timer = new FailingTimer("LOG_TEST_CODE");
            manager.registerTimer(timer);
            manager.update();

            String logged = logCapture.toString();
            // The implementation logs at WARNING level with:
            // "Timer failed: " + class.getSimpleName() + " code=" + timer.getCode()
            assertTrue("Log should contain 'failed'",
                    logged.contains("failed"));
            assertTrue("Log should contain the timer code",
                    logged.contains("LOG_TEST_CODE"));
            assertTrue("Log should contain the timer class",
                    logged.contains("FailingTimer"));
        } finally {
            logger.removeHandler(testHandler);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    public void testMultipleTimersIndependentFailure() {
        // If one timer fails, other timers should still execute normally.
        FailingTimer failing = new FailingTimer("MULTI_FAIL");
        SucceedingTimer succeeding = new SucceedingTimer("MULTI_OK");
        failing.setTicks(1);
        succeeding.setTicks(1);

        manager.registerTimer(failing);
        manager.registerTimer(succeeding);

        manager.update();

        assertTrue("Failing timer perform() called", failing.performCalled);
        assertTrue("Succeeding timer perform() called", succeeding.performCalled);
        assertNull("Failing timer removed", manager.getTimerForCode("MULTI_FAIL"));
        assertNull("Succeeding timer removed", manager.getTimerForCode("MULTI_OK"));
    }
}
