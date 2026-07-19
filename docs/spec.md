# Gravity Flux – Game Specification

## Core Fantasy & 10‑second Hook
- **Fantasy**: You are a "flux marshal" who bends gravity itself to shepherd a glowing orb of
  contained energy ("flux") through shifting chambers and into the exit portal.
- **Hook (10s)**: One thumb, one tap rotates gravity 90°. The orb slides until it hits a wall.
  Tap to re‑orient the whole chamber and route the orb through every flux node into the portal.

## Q&A Discovery (Assumptions Derived)
> No direct user interview was available. The answers below are derived from iOS‑hot minimalist
> reflex/puzzle references and marked as **[assumption]** where applicable.

- **Why users come back (daily/weekly loop) [assumption]**
  A deterministic *daily seeded chamber* (same board for everyone, picked by day‑of‑year) plus a
  streak‑safe daily‑solve counter and per‑level best‑star mastery create a "beat yesterday / don't
  break the streak" loop. Weekly interest comes from campaign progression and newly unlocked
  larger chambers.

- **Session length targets [assumption]**
  - Micro: 30s – 2min per chamber.
  - Mid: 5–10min for a campaign segment of several chambers.
  - The loop is instant‑restart: a failed run costs only a tap.

- **Skill vs luck balance [assumption]**
  Pure skill. Movement is fully deterministic (no RNG in physics). The daily chamber is a seeded
  deterministic pick, so leaderboards/streaks are comparable across players.

- **Fail‑state fairness & frustration controls [assumption]**
  - One **undo token** per chamber reverts the last slide (restores orb position, gravity, collected
    nodes, move count).
  - Explicit **Reset** restarts the chamber instantly (<300ms).
  - No soft‑locks: every shipped chamber is pre‑validated solvable in the engine's build step.
  - Hazards are clearly flickered/colored so the fail cause is readable.

- **Difficulty ramp & onboarding [assumption]**
  - Chamber 0 is a 5×5 tutorial with one node and no hazards, plus an on‑screen hint ("Tap to bend
    gravity").
  - Campaign then grows grid size (5→9) and hazard/node density gradually.
  - Daily chamber reuses campaign chambers, so difficulty tracks progress.

- **Distinctive mechanic vs common Android clones [assumption]**
  Most Android maze/sokoban titles use step‑tap or swipe movement. Gravity Flux uses **whole‑board
  gravity re‑orientation**: the orb is always "falling" toward the active gravity vector, and a tap
  rotates that vector 90°, producing a continuous slide. This "flux" feel is distinct and more
  physical than cell‑by‑cell movement.

- **Art / animation scope (small team) [assumption]**
  Neon vector aesthetic drawn on a custom Canvas `View`: radial‑gradient orb glow, pulsing nodes,
  flickering hazards, a tweened slide (ValueAnimator), and a swirling portal. No sprite sheets;
  assets are vector drawables + canvas primitives. Reusable across 2–3 background themes.

- **Audio / feedback plan [assumption]**
  `SoundPool` synth blips (all original/tiny): soft "whoosh" on gravity rotate, bright "chime" on
  node collect, deep "hum" on win, low "thud" on hazard. No copyrighted audio. Haptic‑light.

- **Monetization‑safe design (no dark patterns) [assumption]**
  Optional rewarded‑ad for +1 undo and a single "remove ads" IAP are *future* hooks. No pay‑to‑win;
  all core gameplay is free; no streaks are punished by monetization.

- **Technical constraints & performance budgets [assumption]**
  - Kotlin + Android `View` (AppCompatActivity + custom `GravityFluxView`), reusing the factory's
    proven Gradle/Kotlin stack (NOT Compose) for reliable `./gradlew test assembleDebug`.
  - minSdk 21, targetSdk 34, compileSdk 34, Java 17.
  - Target 60fps (≤16ms frame); APK < 30MB; offline‑capable; no heavy native libs.

## Differentiation & Retention Checklist
- **USP (1 line)**: One tap bends gravity; slide the flux orb through every node into the portal in
  as few rotations as possible.
- **3 Differentiators**
  1. Whole‑board gravity re‑orientation instead of step/swipe movement.
  2. Deterministic seeded daily chamber for a fair global challenge.
  3. Juice‑rich neon flux visual + audio feedback on every interaction.
- **3 Retention Hooks**
  1. Per‑level best‑star mastery (meta progression).
  2. Streak‑safe daily solve counter (return value, never punished for replay).
  3. Variable campaign difficulty + daily seed (variable challenge).
- **3 Quality Bars**
  1. Smoothness: ≤16ms slide tween at 60fps.
  2. Readability: high‑contrast neon palette, clear hazard/node/portal states.
  3. Responsiveness: instant restart <300ms, undo always available until used.

---
*All assumptions are based on iOS‑hot references and are marked as assumptions where no direct user
input was provided.*
