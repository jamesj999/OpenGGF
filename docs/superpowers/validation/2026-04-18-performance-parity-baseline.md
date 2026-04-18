# Performance Parity Baseline

Captured on `2026-04-18` in isolated worktree `feature/ai-performance-parity-optimization`.

## Baseline Prep

- Fixed pre-existing validation harness drift in:
  - `src/test/java/com/openggf/tests/graphics/FadeManagerTest.java`
  - `src/test/java/com/openggf/tests/TestHTZBossTouchResponse.java`
  - `src/test/java/com/openggf/tests/TestSmpsDriver.java`
- Root cause: these tests called `RuntimeManager.createGameplay()` without first configuring `EngineServices`. They now bootstrap through `TestEnvironment.resetAll()`, matching the current runtime-owned test harness.

## Patch 1 - Pattern hot path

- Render suite command:

```powershell
mvn --% -q -Dmse=off -Dtest=com.openggf.tests.graphics.PatternAtlasFallbackTest,com.openggf.tests.graphics.RenderOrderTest,com.openggf.tests.graphics.FadeManagerTest,com.openggf.tests.TestPatternDesc,com.openggf.tests.TestPaletteCycling,com.openggf.tests.TestSwScrlHtzEarthquakeMode,com.openggf.tests.TestS3kCnzBossScrollHandler test
```

- Result: `PASS` on `2026-04-18`

## Patch 2 - Object bookkeeping allocations

- Object suite command:

```powershell
mvn --% -q -Dmse=off -Dtest=com.openggf.tests.physics.CollisionSystemTest,com.openggf.tests.TestHTZBossTouchResponse,com.openggf.tests.TestSolidOrderingCollisionTraces,com.openggf.tests.TestSolidOrderingSentinelsHeadless,com.openggf.tests.TestS2Htz1Headless,com.openggf.tests.TestS3kAiz1SkipHeadless test
```

- Result: `PASS` on `2026-04-18`

- Trace suite command:

```powershell
mvn --% -q -Dmse=off -Dtest=com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay,com.openggf.tests.trace.s1.TestS1Mz1TraceReplay test
```

- Result: `NOT RUN` in baseline capture

## Patch 3 - Typed provider lists

- Object suite command:

```powershell
mvn --% -q -Dmse=off -Dtest=com.openggf.tests.physics.CollisionSystemTest,com.openggf.tests.TestHTZBossTouchResponse,com.openggf.tests.TestSolidOrderingCollisionTraces,com.openggf.tests.TestSolidOrderingSentinelsHeadless,com.openggf.tests.TestS2Htz1Headless,com.openggf.tests.TestS3kAiz1SkipHeadless test
```

- Result: `PASS` on `2026-04-18`

- Trace suite command:

```powershell
mvn --% -q -Dmse=off -Dtest=com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay,com.openggf.tests.trace.s1.TestS1Mz1TraceReplay test
```

- Result: `NOT RUN` in baseline capture

## Patch 4 - Render/runtime cleanups

- Render suite command:

```powershell
mvn --% -q -Dmse=off -Dtest=com.openggf.tests.graphics.PatternAtlasFallbackTest,com.openggf.tests.graphics.RenderOrderTest,com.openggf.tests.graphics.FadeManagerTest,com.openggf.tests.TestPatternDesc,com.openggf.tests.TestPaletteCycling,com.openggf.tests.TestSwScrlHtzEarthquakeMode,com.openggf.tests.TestS3kCnzBossScrollHandler test
```

- Result: `PASS` on `2026-04-18`

## Patch 5 - Audio block rendering

- Audio suite command:

```powershell
mvn --% -q -Dmse=off -Dtest=com.openggf.tests.TestSmpsDriver,com.openggf.tests.TestRomAudioIntegration,com.openggf.tests.TestPsgChipGpgxParity,com.openggf.tests.TestYm2612ChipBasics,com.openggf.tests.TestYm2612Attack,com.openggf.tests.TestYm2612AlgorithmRouting test
```

- Result: `PASS` on `2026-04-18`

- Manual smoke:
  - `NOT RUN`
