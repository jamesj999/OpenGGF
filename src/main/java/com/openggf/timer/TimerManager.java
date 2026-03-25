package com.openggf.timer;

import com.openggf.game.RuntimeManager;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by James on 26/03/15.
 */
public class TimerManager {
    private static final Logger LOGGER = Logger.getLogger(TimerManager.class.getName());
    private static TimerManager bootstrapInstance;

    private Map<String, Timer> timers = new HashMap<String, Timer>();

    public void registerTimer(Timer timer) {
        timers.put(timer.getCode(), timer);
    }

    public void removeTimerForCode(String code) {
        timers.remove(code);
    }

    public Timer getTimerForCode(String code) {
        return timers.get(code);
    }

    public void update() {
        // Iterate all our timers:
        // Iterate all our timers:
        var iterator = timers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Timer> timerEntry = iterator.next();
            Timer timer = timerEntry.getValue();
            // Decrement the tick value to indicate a tick has passed:
            timer.decrementTick();

            // Check if the tick is less than 1.
            if (timer.getTicks() < 1) {
                if (timer.perform()) {
                    iterator.remove();
                } else {
                    LOGGER.warning("Timer failed: " + timer.getClass().getSimpleName()
                            + " code=" + timer.getCode());
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     */
    public void resetState() {
        timers.clear();
    }

    public synchronized static TimerManager getInstance() {
        var runtime = RuntimeManager.getCurrent();
        if (runtime != null) {
            return runtime.getTimers();
        }
        if (bootstrapInstance == null) {
            bootstrapInstance = new TimerManager();
        }
        return bootstrapInstance;
    }
}
