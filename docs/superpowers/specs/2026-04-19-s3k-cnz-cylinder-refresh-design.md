# S3K CNZ Cylinder Refresh Design

## Goal

Refresh the `CnzCylinderInstance` design after the new solid-ordering work so the next implementation pass targets the actual remaining ROM drift instead of stale pre-fix assumptions.

The target remains ROM-accurate behavior for `Obj_CNZCylinder`, especially `sub_321E2` and `sub_324C0`, while keeping shared solid-pipeline cleanup available if refreshed cylinder regressions prove it is still required.

## Why This Refresh Exists

The earlier CNZ cylinder spec and plan were written before the current solid-ordering fix landed. That older design correctly identified several cylinder approximations, but it treated cylinder-local fallback behavior as likely evidence that the shared solid/contact pipeline was still the main blocker.

That assumption is no longer safe.

The branch now has new solid-ordering behavior and dedicated headless coverage around same-frame solid/contact publication. The cylinder refresh therefore needs to:

- re-evaluate the object against the current engine behavior
- remove stale “engine is probably still wrong” framing
- keep shared solid-pipeline cleanup in scope only when refreshed failing tests prove the cylinder can no longer solve the mismatch locally

## Current Findings

### Design/Code Review Findings

The current `CnzCylinderInstance` is no longer a trivial stub. It already contains:

- split motion-family handling
- rider slot state for player and sidekick
- twist frame handling using the ROM tables
- directed CNZ headless tests that currently pass on this branch

However, it still contains several approximation seams that are too heuristic for a literal ROM port:

- `canFallbackCapture()` uses narrow overlap logic as a rider-entry seam
- slot ownership is inferred from camera focus / first sidekick heuristics rather than a more literal ROM-shaped rider contract
- the circular route logic is still engine-shaped rather than fully locked down by literal quadrant-transition parity tests
- release behavior is still modeled around the current engine abstraction rather than the exact ROM branches

### Delegated Review Findings

Parallel reviews of the current code, tests, and disassembly identified these concrete drift points:

- mode `0` live velocity starts “hot” in Java, while the ROM only seeds the speed cap and leaves live velocity untouched at init
- rider entry still accepts heuristic contact paths that are not the intended primary ROM capture contract
- standing-loss and forced-release behavior are still too conflated
- twist-frame tables match, but surrounding state transitions for roll/radii/priority are still only approximate
- current tests are mostly smoke-level and do not yet lock exact early motion progression, exact rider-state seams, or exact twist/priority windows

## Scope

Included:

- refreshing the cylinder design to fit the post-solid-ordering engine state
- stronger TDD coverage for motion, rider entry/release, and twist/render-state parity
- cylinder-local refactoring toward a more literal `sub_321E2` / `sub_324C0` port
- shared solid-pipeline cleanup if, and only if, refreshed cylinder regressions prove it is still required

Excluded:

- unrelated CNZ traversal object work
- speculative shared-engine rewrites before the refreshed cylinder tests exist
- broad renderer refactors unless cylinder parity specifically proves they are needed

## Decision Rule For Shared Engine Cleanup

Shared solid-pipeline cleanup is explicitly in scope, but it is not the default first move.

The implementation should assume:

1. The cylinder is still the primary source of remaining drift until refreshed tests prove otherwise.
2. Shared solid/contact pipeline work becomes justified only when stricter cylinder regressions still fail after the obvious cylinder-local heuristics have been removed, tightened, or replaced.

Shared engine work is justified if the refreshed cylinder tests show that:

- valid ROM-like capture still cannot happen without a `canFallbackCapture()`-style seam
- standing-bit timing still cannot be observed in a way that allows a more literal `sub_324C0` port
- release semantics still require non-ROM cylinder-local hacks that other traversal full-solids would also likely need

Shared engine work is not justified if the failure is still fully explained by cylinder-local state, motion, slot, or release logic.

## Selected Approach

The refreshed implementation approach is:

`cylinder-first, engine-fixes-if-proven`

This means:

- rewrite the spec/plan around the remaining cylinder-local ROM drift
- front-load stricter failing tests
- make cylinder-local changes first
- only widen into shared solid-pipeline cleanup if the refreshed tests still prove the object cannot be made ROM-like locally

This is preferred over a shared-pipeline-first approach because the current code still contains obvious object-level approximations, and broad engine changes would be harder to justify while those remain.

## Refreshed Design

### 1. Motion Parity Slice

The first TDD slice should lock down the motion state that still differs materially from the ROM.

Required focus:

- mode `0` must not preload live velocity from the speed cap
- early-frame mode `0` behavior should be asserted more tightly than “it moved vertically”
- representative circular subtype tests should assert literal quadrant evolution more directly than the current “visited both sides” smoke checks

The object state should remain organized around explicit ROM-like motion fields:

- saved origin
- motion selector
- speed cap
- live vertical velocity
- route quadrant
- angle
- angle step

If a field only exists to preserve an approximation rather than a ROM state transition, it should be removed or replaced.

### 2. Rider Slot Parity Slice

The second TDD slice should target the capture/release seam directly.

Required focus:

- capture should be proven from valid standing-state conditions, not just broad overlap or side/push contact
- tests should distinguish:
  - valid capture entry
  - no-capture on invalid contact paths
  - standing-loss behavior
  - jump-triggered release
  - forced-release / invalid-rider-state behavior
- player and sidekick slot behavior should remain independent and explicitly covered

The goal is to delete, replace, or narrowly justify the current heuristic seams:

- `canFallbackCapture()`
- slot inference from camera focus / first-sidekick assumptions
- release behavior that collapses multiple ROM cases into one engine-style branch

If the object still needs fallback capture after these tests are tightened, that becomes concrete evidence for shared solid-pipeline follow-up.

### 3. Twist And Render-State Parity Slice

The third TDD slice should keep the ROM twist tables but tighten everything around them.

Required focus:

- exact twist-frame sequence windows
- priority changes across the twist cycle
- capture-state radii / roll / animation ownership rules
- visible cylinder frame cadence, not only initial frame selection

The current object should not be treated as done merely because the frame table values themselves match the ROM. The surrounding state transitions matter just as much.

## Test Strategy

This refresh should remain headless-first and TDD-driven.

The refreshed tests should be added before production changes for each slice:

- motion-init / motion-family parity regressions
- rider-entry / standing-loss / release-path regressions
- twist / priority / visible-frame cadence regressions

Current tests that are only smoke-level should be strengthened rather than discarded.

Key principle:

- outcome-only assertions are not enough when the remaining disagreement is about literal ROM control flow

## Implementation Sequence

1. Refresh the design doc
   - record the post-solid-ordering decision rule
   - replace stale architectural assumptions

2. Refresh the implementation plan
   - split the work into motion, rider slot, and twist/render slices
   - explicitly mark shared solid-pipeline cleanup as conditional, not presumed

3. Execute TDD slices in order
   - motion parity
   - rider slot parity
   - twist/render-state parity
   - optional shared solid-pipeline cleanup if still proven necessary

4. Verify in layers
   - focused CNZ cylinder tests
   - related CNZ traversal tests
   - then full `mvn test`, accepting the known trace exception context until that separate work is addressed

## Files Expected To Change Later

Primary:

- `src/main/java/com/openggf/game/sonic3k/objects/CnzCylinderInstance.java`
- `src/test/java/com/openggf/tests/TestS3kCnzDirectedTraversalHeadless.java`
- `src/test/java/com/openggf/game/sonic3k/objects/TestCnzTraversalRegistry.java`

Conditional shared-engine follow-up:

- `src/main/java/com/openggf/level/objects/ObjectManager.java`
- related solid/contact-publication classes touched by the new ordering work

## Success Criteria

This refresh is successful when the next implementation pass is forced to answer the right question:

- can the cylinder now pass stricter ROM-facing tests with a more literal object-local design?

And the resulting implementation is successful when one of these is true:

1. `CnzCylinderInstance` passes the refreshed parity tests without needing the current heuristic capture/release seams, or
2. a narrowly scoped shared solid-pipeline cleanup is proven necessary by refreshed failing cylinder tests and is covered by dedicated regressions

Either way, the work should end with a cylinder design that is more literal than the current one and a plan that no longer relies on stale pre-solid-ordering assumptions.
