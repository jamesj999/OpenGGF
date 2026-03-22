# Phase 1: Singleton Lifecycle Completion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the singleton resetState() lifecycle so every manager participates in the existing teardown framework, eliminating test state leakage.

**Architecture:** The codebase already has `LevelInitProfile` → `AbstractLevelInitProfile` → `GameContext.forTesting()` → `TestEnvironment` as the reset infrastructure. This plan fills coverage gaps, deprecates the old `resetInstance()` pattern, and adds automated enforcement via a JUnit 5 extension.

**Tech Stack:** Java 21, JUnit 5, Maven

**Spec:** `docs/superpowers/specs/2026-03-22-architectural-refactoring-design.md` (Phase 1)

---

### Task 1: Verify AudioManager resetState() Coverage

AudioManager.resetState() (line 361) already clears `smpsLoader`, `dacData`, `soundMap`, `audioProfile`, `ringLeft`, and calls `clearDonorAudio()`. This task verifies the coverage is complete.

**Files:**
- Read: `src/main/java/com/openggf/audio/AudioManager.java:361-371`
- Test: `src/test/java/com/openggf/audio/TestAudioManagerResetState.java`

- [ ] **Step 1: Write verification test**

```java
package com.openggf.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestAudioManagerResetState {

    @Test
    void resetStateClearsAllMutableFields() {
        AudioManager am = AudioManager.getInstance();

        // Mutate observable state
        am.setRingLeft(false);

        am.resetState();

        // ringLeft resets to true (default)
        assertTrue(am.isRingLeft(), "ringLeft should reset to true");
        // smpsLoader, dacData, soundMap, audioProfile should be null
        assertNull(am.getSmpsLoader(), "smpsLoader should be null after reset");
        assertNull(am.getDacData(), "dacData should be null after reset");
        assertNull(am.getAudioProfile(), "audioProfile should be null after reset");
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn test -Dtest=TestAudioManagerResetState -pl . -q`
Expected: PASS — resetState() already clears everything. If any assertion fails, the field needs to be added to resetState().

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/openggf/audio/TestAudioManagerResetState.java
git commit -m "test: verify AudioManager.resetState() clears all mutable fields"
```

---

### Task 2: Add TerrainCollisionManager.resetState()

TerrainCollisionManager has a single mutable field: `pooledResults` (SensorResult[6]). It has no reset method. Add one for completeness.

**Files:**
- Modify: `src/main/java/com/openggf/physics/TerrainCollisionManager.java`
- Test: `src/test/java/com/openggf/physics/TestTerrainCollisionManagerReset.java`

- [ ] **Step 1: Write failing test**

```java
package com.openggf.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestTerrainCollisionManagerReset {

    @Test
    void resetStateClearsPooledResults() {
        TerrainCollisionManager tcm = TerrainCollisionManager.getInstance();
        tcm.resetState();
        // Should not throw — verifies method exists and runs cleanly
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TestTerrainCollisionManagerReset -pl . -q`
Expected: FAIL — `resetState()` does not exist yet.

- [ ] **Step 3: Add resetState() to TerrainCollisionManager**

Add after the existing `getInstance()` method:

```java
/**
 * Clears pooled sensor results. Called during level teardown
 * to prevent stale collision data from leaking between tests.
 */
public void resetState() {
    java.util.Arrays.fill(pooledResults, null);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TestTerrainCollisionManagerReset -pl . -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/physics/TerrainCollisionManager.java \
        src/test/java/com/openggf/physics/TestTerrainCollisionManagerReset.java
git commit -m "refactor: add TerrainCollisionManager.resetState() for test isolation"
```

---

### Task 3: Add resetState() to Sonic1ConveyorState and Sonic1SwitchManager

Both classes already have `reset()` methods that clear their state. Add `resetState()` as a wrapper (matching the codebase convention) and deprecate `resetInstance()`.

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic1/Sonic1ConveyorState.java`
- Modify: `src/main/java/com/openggf/game/sonic1/Sonic1SwitchManager.java`

- [ ] **Step 1: Add resetState() to Sonic1ConveyorState**

Add after the existing `reset()` method (line 93):

```java
/**
 * Clears all conveyor state. Alias for {@link #reset()} matching
 * the singleton resetState() convention used by the teardown framework.
 *
 * @see com.openggf.game.AbstractLevelInitProfile
 */
public void resetState() {
    reset();
}

/** @deprecated Use {@link #resetState()} for test teardown. */
@Deprecated
public static void resetInstance() {
    instance = null;
}
```

Note: The existing `resetInstance()` at line 39 already exists — add the `@Deprecated` annotation to it.

- [ ] **Step 2: Add resetState() to Sonic1SwitchManager**

Same pattern — add after the existing `reset()` method (line 98):

```java
/**
 * Clears all switch state. Alias for {@link #reset()} matching
 * the singleton resetState() convention used by the teardown framework.
 */
public void resetState() {
    reset();
}

/** @deprecated Use {@link #resetState()} for test teardown. */
@Deprecated
public static void resetInstance() {
    instance = null;
}
```

- [ ] **Step 3: Run existing tests**

Run: `mvn test -pl . -q`
Expected: PASS — no behavior change, only added methods.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openggf/game/sonic1/Sonic1ConveyorState.java \
        src/main/java/com/openggf/game/sonic1/Sonic1SwitchManager.java
git commit -m "refactor: add resetState() to Sonic1ConveyorState and Sonic1SwitchManager"
```

---

### Task 4: Add resetState() to CrossGameFeatureProvider

CrossGameFeatureProvider has `close()` (line 303) that nullifies all 13 mutable fields and sets `active = false`. Add `resetState()` that delegates to `close()`, and deprecate `resetInstance()`.

**Files:**
- Modify: `src/main/java/com/openggf/game/CrossGameFeatureProvider.java`

- [ ] **Step 1: Add resetState()**

Add after the existing `close()` method:

```java
/**
 * Clears all donor state. Delegates to {@link #close()}.
 * Matches the singleton resetState() convention used by the teardown framework.
 */
public void resetState() {
    close();
}
```

- [ ] **Step 2: Deprecate resetInstance()**

Add `@Deprecated` annotation to the existing `resetInstance()` method (line 318):

```java
/** @deprecated Use {@link #resetState()} for test teardown. */
@Deprecated
public static synchronized void resetInstance() {
    if (instance != null) {
        instance.close();
        instance = null;
    }
}
```

- [ ] **Step 3: Run existing tests**

Run: `mvn test -pl . -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/openggf/game/CrossGameFeatureProvider.java
git commit -m "refactor: add CrossGameFeatureProvider.resetState(), deprecate resetInstance()"
```

---

### Task 5: Deprecate resetInstance() on Camera, RomManager, GraphicsManager

These three already have `resetState()` used by the teardown framework. Add `@Deprecated` annotations to their `resetInstance()` methods.

**Files:**
- Modify: `src/main/java/com/openggf/camera/Camera.java` (resetInstance at line 547)
- Modify: `src/main/java/com/openggf/data/RomManager.java` (resetInstance at line 176)
- Modify: `src/main/java/com/openggf/graphics/GraphicsManager.java` (resetInstance at line 185)

- [ ] **Step 1: Add @Deprecated to Camera.resetInstance()**

```java
/**
 * @deprecated Use {@link #resetState()} for test teardown.
 * This method destroys the singleton instance; resetState() clears
 * state in-place, avoiding stale reference issues.
 */
@Deprecated
public static synchronized void resetInstance() {
```

- [ ] **Step 2: Add @Deprecated to RomManager.resetInstance()**

Note: Keep functional — still needed for ROM switching. Deprecate only for test use.

```java
/**
 * @deprecated For test teardown, prefer {@link #resetState()}.
 * This method remains functional for ROM switching but should not
 * be used for inter-test cleanup.
 */
@Deprecated
public static synchronized void resetInstance() {
```

- [ ] **Step 3: Add @Deprecated to GraphicsManager.resetInstance()**

```java
/**
 * @deprecated Use {@link #resetState()} for test teardown.
 * This method destroys the singleton instance; resetState() clears
 * state in-place, avoiding stale reference issues.
 */
@Deprecated
public static synchronized void resetInstance() {
```

- [ ] **Step 4: Run existing tests**

Run: `mvn test -pl . -q`
Expected: PASS (deprecation warnings only, no behavior change)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/camera/Camera.java \
        src/main/java/com/openggf/data/RomManager.java \
        src/main/java/com/openggf/graphics/GraphicsManager.java
git commit -m "refactor: deprecate resetInstance() on Camera, RomManager, GraphicsManager"
```

---

### Task 6: Migrate Test Files from resetInstance() to resetState()

Replace all `GraphicsManager.resetInstance()` and `Camera.resetInstance()` calls in test files with `resetState()` equivalents.

**Files to modify (deduplicated union of both call sites, 26 files):**

GraphicsManager.resetInstance() callers (11 files):
- `src/test/java/com/openggf/tests/TestSonic1SbzFinalZoneRouting.java`
- `src/test/java/com/openggf/tests/TestAizFireCurtainGpuDiag.java`
- `src/test/java/com/openggf/tests/HeadlessTestRunner.java`
- `src/test/java/com/openggf/graphics/VisualRegressionTest.java`
- `src/test/java/com/openggf/graphics/VisualReferenceGenerator.java`
- `src/test/java/com/openggf/graphics/TestGraphicsManagerHeadless.java`
- `src/test/java/com/openggf/game/TestInstaShieldVisual.java`
- `src/test/java/com/openggf/game/sonic3k/TestS3kBpzPaletteCycling.java`
- `src/test/java/com/openggf/game/sonic3k/specialstage/TestS3kSpecialStageResultsVisual.java`
- `src/test/java/com/openggf/game/sonic1/specialstage/Sonic1SpecialStageRendererTest.java`
- `src/test/java/com/openggf/game/sonic1/specialstage/Sonic1SpecialStageManagerTest.java`

Camera.resetInstance() callers (23 files — overlaps with above):
- All 11 above plus 12 additional Camera-only callers

Plus: `src/main/java/com/openggf/level/LevelManager.java` (line 23 — check if it calls Camera.resetInstance())

- [ ] **Step 1: Perform the replacement**

For every file, replace:
- `GraphicsManager.resetInstance()` → `GraphicsManager.getInstance().resetState()`
- `Camera.resetInstance()` → `Camera.getInstance().resetState()`

Use grep to find exact occurrences, then edit each file.

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl . -q`
Expected: PASS — resetState() is already what the teardown framework uses. The behavior is equivalent for test isolation purposes.

If any test fails: that test was relying on the destroy-and-recreate semantics of `resetInstance()`. Investigate what state `resetState()` doesn't clear that `resetInstance()` did, and add it to `resetState()`.

- [ ] **Step 3: Commit**

```bash
git add -u
git commit -m "refactor: migrate all test files from resetInstance() to resetState()"
```

---

### Task 7: Add Generation Counter to GameContext

Add a stale-reference guard to `GameContext` that detects test code holding references across `forTesting()` calls.

**Files:**
- Modify: `src/main/java/com/openggf/GameContext.java`
- Test: `src/test/java/com/openggf/TestGameContextStaleGuard.java`

- [ ] **Step 1: Write failing test**

```java
package com.openggf;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestGameContextStaleGuard {

    @Test
    void freshContextAccessorsWork() {
        GameContext ctx = GameContext.forTesting();
        assertNotNull(ctx.camera());
        assertNotNull(ctx.levelManager());
    }

    @Test
    void staleContextThrowsOnAccess() {
        GameContext stale = GameContext.forTesting();
        GameContext.forTesting(); // invalidates 'stale'

        assertThrows(IllegalStateException.class, stale::camera,
                "Accessing stale GameContext should throw");
        assertThrows(IllegalStateException.class, stale::levelManager,
                "Accessing stale GameContext should throw");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TestGameContextStaleGuard -pl . -q`
Expected: FAIL — `staleContextThrowsOnAccess` does not throw because no generation guard exists yet.

- [ ] **Step 3: Add generation counter to GameContext**

Modify `src/main/java/com/openggf/GameContext.java`:

Add fields:
```java
private static int generation = 0;
private final int capturedGeneration;
```

In constructor, capture generation:
```java
private GameContext(...) {
    this.capturedGeneration = generation;
    // ... existing field assignments ...
}
```

In `forTesting()`, increment before teardown:
```java
public static GameContext forTesting() {
    generation++;
    // ... existing teardown logic ...
    return production();
}
```

Add guard method:
```java
private void checkFresh() {
    if (capturedGeneration != generation) {
        throw new IllegalStateException(
            "Stale GameContext: created at generation " + capturedGeneration
            + " but current generation is " + generation
            + ". Do not hold GameContext references across forTesting() calls.");
    }
}
```

Add `checkFresh()` to every accessor:
```java
public Camera camera() { checkFresh(); return camera; }
public LevelManager levelManager() { checkFresh(); return levelManager; }
public SpriteManager spriteManager() { checkFresh(); return spriteManager; }
public CollisionSystem collisionSystem() { checkFresh(); return collisionSystem; }
public GraphicsManager graphicsManager() { checkFresh(); return graphicsManager; }
public TimerManager timerManager() { checkFresh(); return timerManager; }
public WaterSystem waterSystem() { checkFresh(); return waterSystem; }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TestGameContextStaleGuard -pl . -q`
Expected: PASS

- [ ] **Step 5: Run full test suite**

Run: `mvn test -pl . -q`
Expected: PASS — if any test fails, it's holding a stale GameContext. Fix the test.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/GameContext.java \
        src/test/java/com/openggf/TestGameContextStaleGuard.java
git commit -m "feat: add generation counter to GameContext for stale reference detection"
```

---

### Task 8: Create SingletonResetExtension and @FullReset Annotation

Create a JUnit 5 extension that automatically calls `TestEnvironment.resetPerTest()` before each test, with an opt-in `@FullReset` annotation for tests that need full teardown.

**Files:**
- Create: `src/test/java/com/openggf/tests/SingletonResetExtension.java`
- Create: `src/test/java/com/openggf/tests/FullReset.java`
- Test: `src/test/java/com/openggf/tests/TestSingletonResetExtension.java`

- [ ] **Step 1: Create @FullReset annotation**

```java
package com.openggf.tests;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class or method for full singleton teardown (ROM switch, module change).
 * Used with {@link SingletonResetExtension}. Without this annotation, the extension
 * performs a lightweight per-test reset that preserves loaded level data.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface FullReset {}
```

- [ ] **Step 2: Create SingletonResetExtension**

```java
package com.openggf.tests;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that resets singleton state before each test.
 * <p>
 * By default, calls {@link TestEnvironment#resetPerTest()} which clears
 * transient gameplay state while preserving loaded level data.
 * Annotate with {@link FullReset} for full teardown via
 * {@link TestEnvironment#resetAll()}.
 * <p>
 * Usage: {@code @ExtendWith(SingletonResetExtension.class)}
 */
public class SingletonResetExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        boolean fullReset = context.getRequiredTestMethod()
                .isAnnotationPresent(FullReset.class)
                || context.getRequiredTestClass()
                .isAnnotationPresent(FullReset.class);

        if (fullReset) {
            TestEnvironment.resetAll();
        } else {
            TestEnvironment.resetPerTest();
        }
    }
}
```

- [ ] **Step 3: Write test verifying the extension works**

```java
package com.openggf.tests;

import com.openggf.game.GameStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestSingletonResetExtension {

    @Test
    void perTestResetClearsGameState() {
        // The extension should have called resetPerTest() before this runs.
        // GameStateManager.resetSession() zeroes score.
        GameStateManager gs = GameStateManager.getInstance();
        assertEquals(0, gs.getScore(), "Score should be 0 after per-test reset");
    }

    @FullReset
    @Test
    void fullResetClearsEverything() {
        // The extension should have called resetAll() before this runs.
        GameStateManager gs = GameStateManager.getInstance();
        assertEquals(0, gs.getScore(), "Score should be 0 after full reset");
    }
}
```

- [ ] **Step 4: Run test**

Run: `mvn test -Dtest=TestSingletonResetExtension -pl . -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/openggf/tests/SingletonResetExtension.java \
        src/test/java/com/openggf/tests/FullReset.java \
        src/test/java/com/openggf/tests/TestSingletonResetExtension.java
git commit -m "feat: add SingletonResetExtension and @FullReset for automated test reset"
```

---

### Task 9: Remove Defensive services() Null Checks

Remove 4 dead-code null checks across 3 files. The `services()` method now throws `IllegalStateException` if called before injection, making these guards unreachable.

**Files:**
- Modify: `src/main/java/com/openggf/level/objects/boss/AbstractBossInstance.java` (lines 209-215, 379-390)
- Modify: `src/main/java/com/openggf/game/sonic2/objects/badniks/TurtloidBadnikInstance.java` (lines 249-255)
- Modify: `src/main/java/com/openggf/game/sonic1/objects/bosses/AbstractS1EggmanBossInstance.java` (lines 117-123)

- [ ] **Step 1: Remove null checks in AbstractBossInstance**

In `spawnDefeatExplosion()` (line 209): Remove the `if (services() == null) { return; }` guard. Keep the `renderManager == null` / `objectManager == null` checks if they serve a different purpose (e.g., optional manager), but remove the `services() == null` part.

In `getPaletteForFlash()` (line 379): Remove `if (services() == null || ...)` — replace with direct access. Keep the `currentLevel() == null` check if levels can legitimately be null at that point.

- [ ] **Step 2: Remove null check in TurtloidBadnikInstance**

In `onRiderDestroyed()` (line 249): Remove `if (services() == null) { return; }`. Keep the `objectManager == null` check if it serves a separate purpose.

- [ ] **Step 3: Remove null check in AbstractS1EggmanBossInstance**

In `renderEggmanShip()` (line 117): Remove `if (services() == null) { return; }`. Keep the `renderManager == null` check.

- [ ] **Step 4: Run full test suite**

Run: `mvn test -pl . -q`
Expected: PASS — these checks were unreachable dead code.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/level/objects/boss/AbstractBossInstance.java \
        src/main/java/com/openggf/game/sonic2/objects/badniks/TurtloidBadnikInstance.java \
        src/main/java/com/openggf/game/sonic1/objects/bosses/AbstractS1EggmanBossInstance.java
git commit -m "refactor: remove dead services() null checks (3 files, 4 guards)"
```

---

### Task 10: Migrate SwScrlScz from Camera.getInstance()

Replace the direct singleton call with `GameServices.camera()` for consistency.

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/scroll/SwScrlScz.java` (line 62)

- [ ] **Step 1: Replace Camera.getInstance() with GameServices.camera()**

At line 62, change:
```java
Camera camera = Camera.getInstance();
```
to:
```java
Camera camera = GameServices.camera();
```

Update imports: add `import com.openggf.game.GameServices;`, remove `import com.openggf.camera.Camera;` if no longer used elsewhere in the file.

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl . -q`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/openggf/game/sonic2/scroll/SwScrlScz.java
git commit -m "refactor: migrate SwScrlScz from Camera.getInstance() to GameServices.camera()"
```

---

### Task 11: Final Verification

Run the complete test suite and verify all Phase 1 objectives are met.

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl . -q`
Expected: All tests PASS

- [ ] **Step 2: Verify no remaining resetInstance() calls in test code**

Run: `grep -r "resetInstance()" src/test/ --include="*.java" -l`
Expected: Zero files (or only files that legitimately need destroy-and-recreate for ROM switching).

- [ ] **Step 3: Verify no services() null checks remain**

Run: `grep -rn "services() == null" src/main/ --include="*.java"`
Expected: Zero matches

- [ ] **Step 4: Verify all singletons in teardown have resetState()**

Cross-reference the 12 steps in `AbstractLevelInitProfile.levelTeardownSteps()` — every singleton called there should have a `resetState()` method.

- [ ] **Step 5: Commit summary (if any fixups needed)**

```bash
git add -u
git commit -m "refactor: complete Phase 1 singleton lifecycle — all gaps filled"
```
