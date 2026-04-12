---
name: s3k-zone-bring-up
description: Use when bringing up a new S3K zone — orchestrates analysis, parallel feature implementation (events, parallax, animated tiles, palette cycling), merge, and validation for a target zone.
---

# S3K Zone Bring-Up Orchestrator

One-stop skill for full zone bring-up in Sonic 3 & Knuckles. Given a zone abbreviation, this skill runs analysis against the disassembly, dispatches parallel agents to implement each feature category, merges their work, verifies the build, and validates the result. It coordinates 6 other skills and is itself a coordinator -- it contains no implementation details.

## Inputs

$ARGUMENTS: Zone abbreviation (e.g., "HCZ", "LBZ", "CNZ") followed by optional flags:

- `--skip-review` -- skip the human review gate after analysis and proceed directly to feature dispatch
- `--validate-only` -- skip implementation, run only the validation skill against an already-implemented zone

Examples:
```
HCZ
LBZ --skip-review
CNZ --validate-only
```

## Related Skills

| Skill | Path | Purpose |
|-------|------|---------|
| **s3k-zone-analysis** | `.agents/skills/s3k-zone-analysis/SKILL.md` | Read the disassembly and produce a structured zone feature catalogue |
| **s3k-zone-events** | `.agents/skills/s3k-zone-events/SKILL.md` | Implement camera locks, boss arenas, cutscenes, act transitions, palette mutations from Dynamic_Resize |
| **s3k-parallax** | `.agents/skills/s3k-parallax/SKILL.md` | Implement per-line scroll handlers and deform routines |
| **s3k-animated-tiles** | `.agents/skills/s3k-animated-tiles/SKILL.md` | Implement AniPLC script triggers, gating conditions, dynamic art overrides |
| **s3k-palette-cycling** | `.agents/skills/s3k-palette-cycling/SKILL.md` | Implement AnPal handlers with counter/step/limit cycling and validation |
| **s3k-zone-validate** | `.agents/skills/s3k-zone-validate/SKILL.md` | Visual comparison via stable-retro + image recognition for validation |

## Zone Priority Order

Zones listed in recommended bring-up order. AIZ is already implemented and serves as the reference.

| Priority | Zone | Full Name | Existing Features | Complexity Notes |
|----------|------|-----------|-------------------|------------------|
| -- | AIZ | Angel Island Zone | Events, parallax, animated tiles, palette cycling | **Reference zone** -- fully implemented |
| 1 | HCZ | Hydrocity Zone | Palette cycling exists | Water system integration, common zone, validate existing palette cycling |
| 2 | LBZ | Launch Base Zone | Palette cycling exists | Complex events (rising water, dual acts), validate existing palette cycling |
| 3 | LRZ | Lava Reef Zone | Palette cycling exists | Lava mechanics, visual payoff, validate existing palette cycling |
| 4 | CNZ | Carnival Night Zone | Palette cycling exists | Barrel physics, lighting effects, validate existing palette cycling |
| 5 | ICZ | IceCap Zone | Palette cycling exists | Snowboarding intro sequence, validate existing palette cycling |
| 6 | FBZ | Flying Battery Zone | Palette cycling placeholder | Flying Battery mechanics, palette cycling may be stub |
| 7 | MGZ | Marble Garden Zone | Parallax done | Needs events + animated tiles + boss, parallax already implemented |
| 8 | MHZ | Mushroom Hill Zone | None | Time-of-season (act color changes), no existing features |
| 9 | SOZ | Sandopolis Zone | None | Time-of-day system, ghosts, complex zone |
| 10 | SSZ | Sky Sanctuary Zone | None | Short zone, unique sky mechanics |
| 11 | DEZ | Death Egg Zone | None | Death Egg mechanics, complex bosses |
| 12 | DDZ | Doomsday Zone | None | Entirely unique (flight-only boss chase), lowest priority |

Competition zones (ALZ, BPZ, DPZ, CGZ, EMZ) follow after main zones.

## Orchestration Process

### Step 1: Run Zone Analysis

Dispatch an agent with the `s3k-zone-analysis` skill for the target zone. The agent reads the disassembly and produces a structured feature catalogue.

```
Agent prompt: "Use /s3k-zone-analysis {ZONE}"
```

**Output:** `docs/s3k-zones/{zone}-analysis.md` (e.g., `docs/s3k-zones/hcz-analysis.md`)

Wait for the analysis agent to complete before proceeding. The analysis spec is the input to all subsequent steps.

### Step 2: Human Review Gate

Present the analysis spec to the user for review. Display a summary:

```
Zone Analysis Complete: {ZONE} ({Full Name})
  Events:          {N stages Act 1} + {N stages Act 2} (confidence: HIGH/MEDIUM/LOW)
  Parallax:        {N bands} (confidence: HIGH/MEDIUM/LOW)
  Animated Tiles:  {N scripts} (confidence: HIGH/MEDIUM/LOW)
  Palette Cycling: {N channels} (confidence: HIGH/MEDIUM/LOW)
  Cross-cutting:   {water/shake/character paths/...}

Full spec: docs/s3k-zones/{zone}-analysis.md
Proceed with implementation? [Y/n]
```

If `--skip-review` was passed, skip this step and proceed directly to Step 3.

### Step 3: Determine Feature Scope

Read the analysis spec and decide which features apply to this zone. Not every zone needs every feature -- some have `rts` stubs for AnPal (no palette cycling), some share a parallax handler with another zone, some have no animated tiles.

**Decision flowchart:**

```
For each feature category:
  1. Events (Dynamic_Resize)
     - ALWAYS applicable -- every zone has a _Resize routine
     - Check: is it just "rts" or a trivial stub? If so, create a minimal handler

  2. Parallax (Deform)
     - Check: does the zone have a unique _Deform routine?
     - If the analysis says "shares deform with {other zone}" -> SKIP (already implemented)
     - If the analysis says "unique deform" -> DISPATCH

  3. Animated Tiles (AniPLC)
     - Check: does the analysis list any AniPLC scripts?
     - If "no animated tiles" or "AniPLC routine is rts" -> SKIP
     - If scripts listed -> DISPATCH

  4. Palette Cycling (AnPal)
     - Check: does the analysis say "AnPal is rts" or "no palette cycling"?
     - If rts -> SKIP
     - If channels listed AND already implemented -> DISPATCH with --validate-only flag
     - If channels listed AND not implemented -> DISPATCH
```

Display the scope decision to the user:

```
Feature Scope for {ZONE}:
  [DISPATCH] Events       -- {N} stages, {reason}
  [DISPATCH] Parallax     -- {N} bands, unique deform routine
  [SKIP]    Animated Tiles -- AniPLC routine is rts
  [VALIDATE] Palette Cycling -- {N} channels, already implemented
```

### Step 4: Dispatch Feature Agents

Launch one agent per applicable feature in a separate worktree. Each agent receives the zone name and the path to the analysis spec.

**Dispatch prompt templates:**

**Events agent:**
```
Use /s3k-zone-events {ZONE}

Zone analysis spec: docs/s3k-zones/{zone}-analysis.md
Read the Events section of the analysis spec first, then implement the zone event handler.
```

**Parallax agent:**
```
Use /s3k-parallax {ZONE}

Zone analysis spec: docs/s3k-zones/{zone}-analysis.md
Read the Parallax section of the analysis spec first for band counts and data table locations.
```

**Animated tiles agent:**
```
Use /s3k-animated-tiles {ZONE}

Zone analysis spec: docs/s3k-zones/{zone}-analysis.md
Read the Animated Tiles section of the analysis spec first for script addresses and gating conditions.
```

**Palette cycling agent:**
```
Use /s3k-palette-cycling {ZONE}

Zone analysis spec: docs/s3k-zones/{zone}-analysis.md
Read the Palette Cycling section of the analysis spec first for channel definitions and counter addresses.
```

All applicable agents run in parallel. Wait for all to complete before proceeding to Step 5.

### Step 5: Merge Results

Merge the worktree branches from each feature agent into the main working branch. Since all feature agents work on different primary files (event handler class, scroll handler class, pattern animator cases, palette cycler cases), the only conflicts occur in shared files (see Section 6 below).

**Merge sequence:**
1. Merge the events worktree first (it is the most foundational -- other features may reference event state)
2. Merge parallax worktree
3. Merge animated tiles worktree
4. Merge palette cycling worktree
5. For each merge conflict in a shared file, apply the additive resolution strategy (Section 6)

### Step 6: Build Verification

Run the full build to verify compilation:

```bash
mvn package
```

If the build fails:
1. Read the compiler error
2. Identify which shared file has a conflict or which feature agent introduced an incompatibility
3. Fix the issue (most likely a missing import, duplicate constant name, or switch case ordering)
4. Re-run `mvn package`

Also run existing S3K tests to verify no regressions:

```bash
mvn test -Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

### Step 7: Validate

Dispatch an agent with the `s3k-zone-validate` skill for the target zone:

```
Use /s3k-zone-validate {ZONE}
```

The validation agent captures reference screenshots from stable-retro and compares them against the engine's output for feature presence (parallax layers, palette cycling, animated tiles, camera locks). Review the validation report and flag any FAIL results for manual investigation.

## Shared File Conflict Resolution

Five files are touched by multiple feature agents. All changes are additive (new constants, new switch cases, new registrations), so conflicts are mechanical -- resolve by combining the additions from each branch.

| File | What Each Agent Adds | Resolution |
|------|---------------------|------------|
| `Sonic3kConstants.java` | ROM address constants (events adds event-related offsets, parallax adds deform data offsets, etc.) | Combine all new `public static final` declarations. No two agents define the same constant name since they use different prefixes (`DEFORM_`, `ANIPLC_`, `ANPAL_`, etc.) |
| `Sonic3kLevelEventManager.java` | Zone dispatch case in `createEventsForZone()` or equivalent switch | Only the events agent adds a case here. If another agent also touches this file (e.g., to read event state), take both changes. |
| `Sonic3kScrollHandlerProvider.java` | Zone case returning the new scroll handler | Only the parallax agent adds a case. Merge is trivial -- add the new case to the switch. |
| `Sonic3kPatternAnimator.java` | Zone case in `resolveAniPlcAddr()` and/or `update()` | Only the animated tiles agent adds cases. Merge is trivial. |
| `Sonic3kPaletteCycler.java` | Zone case or channel updates in the cycling method | Only the palette cycling agent adds cases. Merge is trivial. |

**If a true conflict occurs** (two agents modified the same line), prefer the events agent's version for event-state-related code, and the feature-specific agent's version for its own feature code.

## Common Mistakes

1. **Skipping analysis.** Never dispatch feature agents without running `s3k-zone-analysis` first. The analysis spec is the contract between analysis and implementation -- without it, feature agents will re-derive information from the disassembly independently, leading to inconsistent interpretations and duplicated work.

2. **Not checking feature applicability.** Some zones have `rts` stubs for AnPal (no palette cycling) or share a Deform routine with another zone (no unique parallax to implement). Dispatching an agent for a non-applicable feature wastes time and may produce incorrect code. Always run Step 3's decision flowchart before dispatching.

3. **Merging without building.** After merging worktree branches, always run `mvn package` before proceeding to validation. Shared file conflicts that silently produce invalid Java (duplicate switch cases, missing imports) will only surface at compile time.

4. **Forgetting palette cycling validation for already-implemented zones.** Zones with priority 1-5 (HCZ, LBZ, LRZ, CNZ, ICZ) already have palette cycling implemented. The palette cycling agent should run in `--validate-only` mode for these zones, verifying the existing code against the disassembly rather than reimplementing from scratch. Skipping this validation misses opportunities to catch discrepancies in the existing implementation.

5. **Dispatching all 4 feature agents unconditionally.** The decision flowchart in Step 3 exists for a reason. MGZ already has parallax implemented -- dispatching a parallax agent will create a conflicting second implementation. FBZ may have an `rts` stub for AniPLC -- dispatching an animated tiles agent will produce a no-op handler that clutters the codebase.

6. **Ignoring cross-cutting concerns from the analysis.** The analysis spec's "Cross-Cutting Concerns" section flags water systems, screen shake, character branching, and dynamic tilemap changes. These affect multiple features (e.g., water level changes in events affect parallax water-split logic). Review cross-cutting concerns before dispatch and include relevant notes in each agent's prompt.

7. **Wrong merge order.** Events should merge first because event state variables (routine counters, boss flags) may be referenced by other features (animated tile gating, parallax mode switches). Merging parallax first and then events can create forward-reference errors if the parallax handler reads an event field that the events agent introduces.
