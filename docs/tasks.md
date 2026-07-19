# Gravity Flux – Task List

## Epic 1 – Project Setup
- [ ] Create Android module (Gradle + Kotlin, View stack) at `projects/gravity-flux`.
- [ ] Reuse proven Gradle wrapper (AGP 8.2.2, Kotlin 1.9.10, Gradle 8.4).
- [ ] Set minSdk 21, targetSdk 34, compileSdk 34, Java 17.
- [ ] Configure `settings.gradle.kts`, root + app `build.gradle.kts`, `gradle.properties`.

## Epic 2 – Core Gameplay Engine (`GravityFluxEngine`)
- [ ] Define chamber cell types: EMPTY, WALL, NODE, EXIT, HAZARD, START.
- [ ] Model gravity vector (DOWN/LEFT/UP/RIGHT) with clockwise rotate.
- [ ] Implement `slideUntilBlocked` with wall stop + out‑of‑bounds stop.
- [ ] Collect NODEs along the slide path.
- [ ] Detect win (EXIT reached + all nodes) and fail (HAZARD entered).
- [ ] Implement single‑token undo (snapshot push/pop) and reset.
- [ ] Daily seed selection (`DAY_OF_YEAR % count`) and star rating (par‑based).
- [ ] Pre‑validate every campaign chamber solvable at construction.

## Epic 3 – UI & Animation
- [ ] Implement `GravityFluxView` custom Canvas `View` (grid, orb glow, nodes, hazards, portal).
- [ ] Wire `MainActivity` controls: Bend Gravity, Undo, Reset, Next.
- [ ] Slide tween via `ValueAnimator` (≤16ms/frame, 60fps).
- [ ] Show move count, par, stars, daily/streak status overlay.

## Epic 4 – Audio & Feedback
- [ ] `FluxAudioManager` wrapping `SoundPool`.
- [ ] Synthesize whoosh (rotate), chime (collect), hum (win), thud (hazard).
- [ ] Trigger sounds on relevant events; respect mute.

## Epic 5 – Persistence & Daily Logic
- [ ] `FluxProgressManager` storing best‑star per level (SharedPreferences).
- [ ] Daily solve streak (consecutive days) — streak‑safe (re‑solve same day = no break).
- [ ] On launch, select daily chamber via seeded index.

## Epic 6 – Testing & CI
- [ ] JUnit4 `GravityFluxEngineTest`: slide, wall, collect, hazard, win, undo, reset, daily seed, stars.
- [ ] Validate all campaign chambers solvable in a test.
- [ ] Run `./gradlew test assembleDebug`; ensure green.

## Epic 7 – Release & Documentation
- [ ] Write README with build/run instructions + codex CLI evidence.
- [ ] Commit, push to `iamconanpeter/gravity-flux`.
- [ ] Update `research/game_factory_status.json` + `research/ios_android_game_pipeline.md`.
- [ ] Tag latest commit `v1.0.0`.

## Milestones
- **MVP Complete**: playable chamber with rotate/slide/collect/win + undo.
- **CI Pass**: `./gradlew test assembleDebug` green, no lint blockers.
- **GitHub Push**: repo `iamconanpeter/gravity-flux` updated with full history.

---
*Use Git issues to track feature/bug tickets if expanding scope.*
