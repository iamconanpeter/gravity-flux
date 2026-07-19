# Gravity Flux – Technical Plan

## Architecture Overview
- **Engine (`GravityFluxEngine`, pure Kotlin, zero Android deps)** — owns the chamber grid, gravity
  vector, orb position, collected nodes, move count, undo history, and win/lose state. Fully
  unit‑testable on the JVM (JUnit4), matching the factory's proven pattern from Switchboard Spark.
- **Persistence (`FluxProgressManager`)** — `SharedPreferences` for per‑level best‑star and the
  streak‑safe daily‑solve counter + daily seed selection.
- **UI (`MainActivity` + `GravityFluxView`)** — `AppCompatActivity` hosting a custom Canvas `View`
  that renders the chamber, orb glow, nodes, hazards, and portal, plus control buttons
  (Bend Gravity, Undo, Reset, Next).
- **Audio (`FluxAudioManager`)** — wraps Android `SoundPool` for synthesized blips.

## Core Modules
| Module | Responsibility |
|--------|----------------|
| `engine` | Chamber model, gravity rotate, slide‑until‑blocked, node collection, win/lose, undo, daily seed |
| `ui` | `GravityFluxView` canvas rendering + `MainActivity` controls/animations |
| `data` | `FluxProgressManager`: best‑star + daily streak + daily index |
| `audio` | `FluxAudioManager`: load/play synth blips |

## Key Algorithms
1. **Gravity rotate** — tap → rotate active gravity 90° clockwise
   (`DOWN→LEFT→UP→RIGHT→DOWN`).
2. **slideUntilBlocked** — from the orb cell, step one cell per iteration in the gravity direction:
   - if next cell is `WALL` or out‑of‑bounds → stop in current cell;
   - if next cell is `HAZARD` → enter it and set `state = FAILED`;
   - else move into it, and if it is `NODE` mark collected; if it is `EXIT` and all nodes collected →
     `state = WON`.
3. **collectAlongPath** — nodes are collected as the orb passes over them during the slide.
4. **Undo** — push a snapshot (orb pos, gravity, collected set, moves) before each slide; undo pops
   and restores, consuming the single token.
5. **Daily seed selection** — `Calendar.DAY_OF_YEAR % chamberCount` yields the deterministic daily
   chamber index (same for all players on a given day).
6. **Star rating** — `3` if `moves ≤ par` and undos unused; `2` if `moves ≤ par+2`; else `1`.

## Performance Targets
- **Frame time**: ≤16ms (60fps) for the slide tween (ValueAnimator, no per‑frame allocations).
- **Memory**: ≤40MB heap; small chamber grids (≤81 cells).
- **APK**: < 30MB; vector/canvas only, no large assets.
- **Battery**: engine/physics only run on interaction; no background work.

## Development Milestones
1. **Scaffold** — Gradle/Kotlin/View project, minSdk 21, reuse proven wrapper.
2. **Engine** — chamber model, rotate, slide, collect, win/lose, undo, daily seed, stars.
3. **UI** — `GravityFluxView` canvas + controls + slide tween + glow.
4. **Audio** — `SoundPool` synth blips on events.
5. **Persistence** — best‑star + daily streak + daily index.
6. **Tests & CI** — JUnit4 engine tests; `./gradlew test assembleDebug` green.
7. **Release** — README, commit, push to `iamconanpeter/gravity-flux`.

## Testing Strategy
- **Unit (JUnit4)**: `GravityFluxEngineTest` covers slide‑until‑blocked, wall stop, node collection,
  hazard fail, win condition, undo restore, reset, daily‑index determinism, star rating, and
  full‑campaign solvability validation.
- **UI**: manual + optional Compose‑free interaction smoke (tap → orb slides).
- **Build**: `./gradlew test assembleDebug` must be green before push.

## Risk Mitigation
- **Determinism**: no RNG in movement; daily is seeded; every chamber pre‑validated solvable at
  engine construction.
- **Asset size**: vector + canvas only; audio synthesized, <50KB total.
- **Platform stability**: stable Gradle 8.4 + Kotlin 1.9.10 + AGP 8.2.2 (proven in factory).
- **Build reliability**: reuse the shipped `switchboard-spark` Gradle wrapper so `./gradlew` works
  offline‑friendly.

---
*All timelines are estimates; adjustments may be needed based on team velocity.*
