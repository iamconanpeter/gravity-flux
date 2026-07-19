# Gravity Flux 🌌

One tap bends gravity. Slide the flux orb through every glowing NODE and into the
exit portal — in as few rotations as possible. A minimalist neon puzzle built for
short, satisfying mobile sessions.

## Core mechanic
- Tap **Bend Gravity** to rotate the active gravity vector 90° clockwise
  (DOWN → LEFT → UP → RIGHT). The orb is *always falling* toward that vector and
  slides until it hits a wall.
- Route the orb over every **NODE**, then into the **EXIT** portal to win.
- Touching a **HAZARD** ends the run (instant retry, no punishment).
- One **undo** token per chamber + instant **reset**.

## Why it's different
Most Android maze/sokoban titles use step-tap or swipe movement. Gravity Flux uses
**whole-board gravity re-orientation**: the orb is a bead of contained energy that
slides continuously, producing a physical "flux" feel rather than cell-by-cell hops.
A deterministic **daily seeded chamber** (same board for everyone, by day-of-year)
plus a streak-safe daily counter give a fair global challenge.

## Controls
- **Bend Gravity** – rotate gravity 90° and slide the orb.
- **Undo** – revert the last slide (single token).
- **Reset** – instantly restart the chamber.
- **Next** – advance to the next campaign chamber.

## Build & run
Requires the Android SDK and **JDK 17**. A `local.properties` pointing at the SDK is
required (already present in this repo's working copy; create your own locally).

Run all unit tests and assemble a debug APK:

```bash
./gradlew test assembleDebug
```

Install on a device/emulator:

```bash
./gradlew installDebug
```

## Architecture
- `engine/GravityFluxEngine.kt` — **pure Kotlin, zero Android deps**. Chamber model,
  gravity rotation, slide-until-blocked, node collection, win/fail, single-token
  undo, reset, star rating, daily seed, and a BFS `isSolvable` validator. Fully
  unit-testable on the JVM.
- `data/FluxProgressManager.kt` — `SharedPreferences` best-star-per-level + streak-safe
  daily-solve counter.
- `audio/FluxAudioManager.kt` — `SoundPool` with in-code synthesized PCM blips
  (whoosh / chime / hum / thud). No audio asset files.
- `ui/GravityFluxView.kt` – custom `Canvas` `View` with neon orb glow, pulsing nodes,
  flickering hazards, swirling portal, and a `ValueAnimator` slide tween.
- `ui/MainActivity.kt` – `AppCompatActivity` hosting the view, HUD, and controls.

## Testing
`GravityFluxEngineTest` (JUnit4) covers slide-until-blocked, wall/out-of-bounds stop,
node collection along the path, hazard failure, win condition, undo restore, reset,
`dailyIndex` determinism, star-rating boundaries, and asserts **every campaign
chamber is solvable** via the BFS validator.

```bash
./gradlew test
```

## Built with the Codex CLI
This project's source was generated and validated with the **OpenAI Codex CLI**
(`codex exec`) driving the `tencent/hy3:free` model through OpenRouter — satisfying
the factory's mandatory plan-mode-first + codex-implemented workflow.

Evidence artifacts:
- `docs/spec.md`, `docs/technical-plan.md`, `docs/tasks.md` — plan-mode gate output.
- `codex_plan_evidence.txt` — codex probe/run log (including this implementation run).
- Codex run summary: `codex exec -m tencent/hy3:free -c approval_mode=never
  --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox` produced the full
  `app/src` tree (engine, data, audio, ui, tests, resources) in a single session
  (~150k tokens), then Gradle validated `test assembleDebug` green.

No licensed assets, no dark patterns. Core gameplay is fully free.
