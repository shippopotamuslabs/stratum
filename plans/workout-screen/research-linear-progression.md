# Research: Linear Progression Algorithms for Resistance Training

**Ticket:** `workout-screen`
**Decision targeted:** D8 (Progression algorithms: double primary, linear supported) / Open question Q1
**Date:** 2026-07-05
**Purpose:** Literature-grounded input for the Synthitect Design phase. This file is research only — it does not modify `design.md`, `structure.md`, or any code.

---

## Sources consulted

- **Starting Strength — "The Reset: Why and How"** (Rori Alter, SSC): https://startingstrength.com/article/the-reset-why-and-how — Canonical description of the Starting Strength NLP reset protocol (percentage drop, when to reset, what comes after).
- **Starting Strength — "Programming a Smooth NLP"**: https://startingstrength.com/article/programming-a-smooth-nlp — Describes the novice linear progression (NLP) increment schedule, the light-day modification, and the transition signal to intermediate programming.
- **PowerliftingToWin — Starting Strength review**: https://www.powerliftingtowin.com/starting-strength/ — Independent analysis of SS NLP: 3-consecutive-failure stall trigger, 3-reset threshold before intermediate transition, Texas Method as the canonical next step.
- **PowerliftingToWin — Greyskull LP review**: https://www.powerliftingtowin.com/greyskull-lp/ — Analysis of Greyskull LP: AMRAP final set as the progression driver, 10 % reset, 2-consecutive-failure trigger.
- **Boostcamp — Greyskull Linear Progression (r/Fitness canonical write-up)**: https://www.boostcamp.app/coaches/r-fitness/greyskull-linear-progression — Community-canonical phrasing of the Greyskull protocol: reset on 2 consecutive failures to hit 5 reps on the AMRAP set; 10 % deload.
- **Stronglifts 5×5 — "Failing Reps"**: https://stronglifts.com/stronglifts-5x5/failure/ — Official Stronglifts deload protocol: 10 % weight reduction after 3 consecutive failed workouts at the same weight.
- **Stronglifts — Progression support article**: https://support.stronglifts.com/article/71-progression — Confirms per-session 5 lb / 2.5 kg increment (2.5 lb / 1.25 kg on OHP and Bench); deload is user-customisable.
- **JCDFitness — Bill Starr / Madcow 5×5 (Intermediate)**: https://jcdfitness.com/2010/02/bill-starr-madcow-5x5-intermediate-training/ — Weekly linear progression for intermediates: 2.5–5 lb/week upper body, 5–10 lb/week lower body; stall expected at 8–12 weeks; ~10 % reset.
- **PowerliftingToWin — Madcow 5×5 review**: https://www.powerliftingtowin.com/madcows-5x5/ — Positions Madcow as the intermediate weekly-progression template that novices transition to after per-session linear gains exhaust.
- **LiftVault — Madcow 5×5 spreadsheet / protocol**: https://liftvault.com/programs/strength/madcow-5x5-spreadsheet/ — Concrete weekly increment numbers and stall/reset mechanics.
- **Andy Baker — "Successful Transition from Novice to Intermediate Programming"**: https://www.andybaker.com/successful-transition-from-novice-to-intermediate-programming/ — Practitioner guide to the novice→intermediate transition; describes the signals (repeated stalls, reset fatigue) that indicate linear gains are exhausted.
- **Stronger By Science — "Making Your Novice Strength Training Routine More Effective"**: https://www.strongerbyscience.com/making-your-novice-strength-training-routine-more-effective-two-quick-tips/ — Questions the stock 10 % reset advice; argues for smaller, more frequent resets and microplate-driven increments, especially for upper-body lifts.
- **Helms et al. (2018) — RPE-based vs. percentage-based loading (Frontiers in Physiology)**: https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2018.00247/full — RCT comparing RIR/RPE-autoregulated loading to fixed-percentage loading in matched daily-undulating programs; small 1RM strength advantage for RPE-based autoregulation in the majority of individuals.
- **Helms et al. — RPE as Volume Autoregulation (PDF)**: https://www.researchgate.net/profile/Eric-Helms-2/publication/318030802 — Earlier work establishing RIR-based RPE for autoregulating volume across squat/bench/deadlift.
- **PMC — Methods for Regulating and Monitoring Resistance Training (review)**: https://pmc.ncbi.nlm.nih.gov/articles/PMC7706636/ — Systematic review of autoregulation paradigms (RPE, RIR, VBT); favours autoregulation over fixed prescription for strength outcomes.
- **Sports Medicine – Open — Effect of Load and Volume Autoregulation on Muscular Strength (meta-analysis)**: https://link.springer.com/article/10.1186/s40798-021-00404-9 — Meta-analysis: autoregulatory progressive resistance exercise (APRE) produces greater strength gains than fixed loading.
- **MASS / Stronger By Science — "RPE and RIR: The Complete Guide"**: https://massresearchreview.com/2023/05/22/rpe-and-rir-the-complete-guide/ — Practitioner reference for RIR-based load prescription and progression gating.
- **PMC — Non-linear periodization review**: https://pmc.ncbi.nlm.nih.gov/articles/PMC3588896/ — Evidence that daily/weekly undulating (non-linear) periodization outperforms strict linear periodization over longer training horizons; supports the linear→non-linear transition.
- **Stronger By Science — "How to Choose the Right Load Progression Strategy"**: https://www.strongerbyscience.com/weekly-load-progression/ — Framework for selecting between fixed-weight, RPE/RIR-gated, and weekly load progression; directly addresses when linear per-session increments are appropriate (novice) vs. when to slow down (intermediate).
- **T-Nation — "Progression Models for Strength: Selection Process"**: https://t-nation.com/t/progression-models-for-strength-selection-process/280201 — Positions double progression as the preferred model for beginner/linear blocks and accumulation phases; contrasts with fixed-weight and percentage-based models.
- **Setgraph — "Double Progression: The Smarter Way to Add Reps and Weight"**: https://setgraph.app/articles/double-progression-the-smarter-way-to-add-reps-and-weight — Explains that double progression IS linear load progression operating inside a rep range; argues the range-based variant is more sustainable than rigid fixed-rep targets.
- **Hevy Coach — "Double Progression: Definition and Examples"**: https://hevycoach.com/glossary/double-progression/ — Glossary definition: double progression in 3–8 rep range for compound lifts; reps-first-then-load mechanism.

---

## Findings

### 1. Classic linear models (per-session novice progression)

**Starting Strength Novice Linear Progression (NLP)** is the most-cited per-session linear model. The protocol: train 3 non-consecutive days/week; add a fixed increment to every lift every session. Increments are **5 lb / 2.5 kg per session for lower-body lifts (squat, deadlift, power clean)** and **2.5 lb / 1.25 kg per session for upper-body lifts (press, bench press)**. Work sets are a fixed **3 × 5** (deadlift 1 × 5; power clean 5 × 3). The increment is mandatory — the lifter adds weight every session regardless of perceived effort, on the principle that a novice can adapt to any reasonable stress within 48–72 h.
— https://startingstrength.com/article/programming-a-smooth-nlp

**Stronglifts 5×5** uses the same per-session-mandatory-increment philosophy but with **5 × 5** work sets. Increments: **5 kg / 10 lb per session for squats and deadlifts**, **2.5 kg / 5 lb per session for bench, overhead press, and barbell row**. The app enforces the increment automatically.
— https://support.stronglifts.com/article/71-progression

**Greyskull LP** modifies the fixed-rep model with an **AMRAP (as-many-reps-as-possible) final set**. The prescribed scheme is **2 × 5 + AMRAP**. Load still increases by a fixed increment each session, but the AMRAP set is the performance signal: if the lifter hits 5+ reps on the AMRAP, the increment proceeds; if they fail to reach 5 reps, the stall protocol triggers. This is the key innovation — it introduces a performance gate onto an otherwise-mandatory increment.
— https://www.powerliftingtowin.com/greyskull-lp/

**Consensus increment sizes across novice programs:**

| Lift type | Per-session increment | Source |
|---|---|---|
| Lower body (squat, deadlift) | 5 lb / 2.5 kg (Stronglifts allows up to 10 lb / 5 kg early) | SS, Stronglifts |
| Upper body (bench, OHP, row) | 2.5 lb / 1.25 kg | SS, Stronglifts, Greyskull |

The **upper-body increment is half the lower-body increment** across all three programs. This is not arbitrary: upper-body lifts have smaller absolute loads and stall sooner; a 5 lb jump on an OHP at 95 lb is a 5.3 % increase, versus 1.4 % on a 350 lb squat.

### 2. Stall, reset, and deload protocols

**Stall trigger (when to reset):**

- **Starting Strength NLP**: A stall is **3 consecutive workouts** at the same weight where the lifter fails to complete all 3 × 5 reps. After the third failure, a reset is performed. The first intervention before a full reset is to add a **light day at 80 %** of the work weight (for squat only), which extends the NLP. Only after that fails does the full reset apply.
  — https://startingstrength.com/article/the-reset-why-and-how ; https://www.powerliftingtowin.com/starting-strength/

- **Greyskull LP**: A reset is triggered after **2 consecutive failures** to reach 5 reps on the AMRAP final set. This is a stricter (faster-acting) trigger than SS.
  — https://www.powerliftingtowin.com/greyskull-lp/ ; https://www.boostcamp.app/coaches/r-fitness/greyskull-linear-progression

- **Stronglifts 5×5**: A deload is triggered after **3 consecutive failed workouts** at the same weight (a "failed workout" = missing any of the 5 × 5 reps). The deload is **10 % of the current weight**. This is user-customisable in-app (the user can change the deload percentage and the number of failures that trigger it).
  — https://stronglifts.com/stronglifts-5x5/failure/

**Reset magnitude:**

All three novice programs converge on a **~10 % load reduction** as the standard reset:

- SS NLP: 5–10 % (Rippetoe specifies "5 % for presses, 10 % for squats and deadlifts" in some formulations; the canonical Reset article describes a 5–10 % drop).
  — https://startingstrength.com/article/the-reset-why-and-how
- Greyskull LP: 10 %.
  — https://www.powerliftingtowin.com/greyskull-lp/
- Stronglifts 5×5: 10 %.
  — https://stronglifts.com/stronglifts-5x5/failure/

Stronger By Science pushes back on the universality of the 10 % figure, arguing that **smaller, more frequent resets** (e.g., a single reset increment's worth) are often more productive for upper-body lifts, and that the stock 10 % advice over-corrects for lifts that are already close to their ceiling. The practical takeaway: 10 % is a defensible default, but the reset magnitude should be per-lift-configurable, and upper-body lifts may benefit from a smaller reset (5 %).
— https://www.strongerbyscience.com/making-your-novice-strength-training-routine-more-effective-two-quick-tips/

**Number of resets before programme change:**

SS NLP prescribes **3 resets** before declaring the lifter an intermediate and transitioning away from per-session linear progression. After 3 resets, the lifter moves to an intermediate programme (Texas Method, weekly progression). Andy Baker describes the practical signal: when resets stop producing renewed progress (i.e., the lifter re-stalls at or below their previous PR within a few sessions of the reset), the novice phase is over.
— https://www.powerliftingtowin.com/starting-strength/ ; https://www.andybaker.com/successful-transition-from-novice-to-intermediate-programming/

### 3. Linear → non-linear transition

The literature describes a clear, well-established transition path: **novice per-session linear → intermediate weekly linear (or undulating) → advanced block periodisation.**

- **Novice** (per-session linear): SS NLP, Stronglifts, Greyskull. Load increases every 48 h. Lasts 3–9 months for most lifters.
- **Intermediate** (weekly linear or undulating): Texas Method, Bill Starr/Madcow 5×5. Load increases once per week. The weekly structure introduces light/medium/heavy days (Texas Method) or ramps volume across the week (Madcow). Madcow uses **2.5–5 lb/week for upper body, 5–10 lb/week for lower body**; stall is expected at **8–12 weeks**; reset is ~10 %.
  — https://jcdfitness.com/2010/02/bill-starr-madcow-5x5-intermediate-training/ ; https://liftvault.com/programs/strength/madcow-5x5-spreadsheet/
- **Advanced** (block periodisation): monthly cycles. Out of scope for Stratum's Beginner training-age cohort.

The signal to transition is well-characterised: after **2–3 resets** on the novice programme, further linear gains are not recoverable. The lifter's adaptation rate has slowed below the rate at which per-session increments can be sustained. The transition is to a programme where load increases less frequently (weekly) and where intensity/volume undulates within the week to manage fatigue.
— https://www.andybaker.com/successful-transition-from-novice-to-intermediate-programming/ ; https://www.powerliftingtowin.com/starting-strength/

A systematic review in the *Journal of Strength and Conditioning Research* found that **non-linear (undulating) periodisation produces superior strength and hypertrophy outcomes to linear periodisation** over training horizons beyond ~12 weeks, providing the evidence base for the transition.
— https://pmc.ncbi.nlm.nih.gov/articles/PMC3588896/

**Relevance to Stratum's LiftLab recollection:** The recollection ("after ~2 stall sessions, transition into double progression") conflates two literature-distinct steps. In established practice, the stall → reset cycle happens 2–3 times *before* a programme transition occurs; a single stall (or even a single reset) does not trigger a transition. The "2 stall sessions" figure aligns with **Greyskull's 2-consecutive-failure reset trigger**, but that trigger produces a *reset* (10 % drop + resume linear), not a *programme transition*. The programme transition happens after multiple resets fail to re-establish progress.

### 4. Rep-range / set considerations for linear progression

The classic novice linear programs (SS, Stronglifts) use **fixed rep targets** (3 × 5 or 5 × 5), not rep ranges. Greyskull is the exception that introduces performance variance (AMRAP final set) into an otherwise fixed scheme.

However, **double progression — which IS linear load progression operating inside a rep range — is widely recommended as a more sustainable and autoregulated alternative to rigid fixed-rep linear progression.** Under double progression, the lifter stays at a given load until they reach the *top* of the rep range across all prescribed sets, then adds load and drops back to the *bottom* of the range. This is slower than mandatory per-session increments but more forgiving and better suited to lifters past the pure-novice phase.
— https://setgraph.app/articles/double-progression-the-smarter-way-to-add-reps-and-weight ; https://t-nation.com/t/progression-models-for-strength-selection-process/280201

T-Nation positions double progression as the preferred model specifically for **beginner and linear blocks**, noting it is more forgiving than fixed-rep linear progression while still providing a clear, deterministic progression path.
— https://t-nation.com/t/progression-models-for-strength-selection-process/280201

**Implication for Stratum:** Because Stratum's set structure already prescribes a **rep range + RIR target** on the top set for *all* progression schemes (D8), a linear scheme that ignores the rep range and forces a fixed rep target would be architecturally inconsistent. The literature supports operating linear progression *inside* the rep range: the increment is applied per-session (as in classic linear), but the rep range is preserved (as in double progression). The distinction between Stratum's `Linear` and `Double` schemes then lives entirely in the **increment trigger**, not the set structure:

- **Double**: increment only when the *top* of the rep range is achieved at the RIR target (reps-first).
- **Linear**: increment every session as long as the *bottom* of the rep range is achieved at the RIR target (load-first).

This is consistent with how Greyskull gates a mandatory increment behind an AMRAP performance check, and with how double progression gates a load increase behind reaching the top of a range.

### 5. RIR auto-regulation vs. fixed increments

**Helms et al. (2018)** conducted an RCT comparing RPE/RIR-based autoregulated loading to fixed-percentage-based loading within matched daily-undulating periodised programs over 8 weeks. The result: **both conditions produced significant strength gains, but RPE-based autoregulation produced a small but meaningful 1RM advantage in the majority of individuals** (squat and bench). The study concludes that autoregulation allows load to adapt to daily readiness, avoiding the failure-to-recover spiral that rigid fixed-percentage loading can produce.
— https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2018.00247/full

A meta-analysis in *Sports Medicine – Open* (Arede et al., 2021) pooled autoregulation studies and found that **autoregulatory progressive resistance exercise (APRE) and RPE/RIR-based loading produce greater maximal strength gains than fixed-loading protocols**, with the advantage most pronounced in trained individuals.
— https://link.springer.com/article/10.1186/s40798-021-00404-9

A broader PMC review of monitoring/autoregulation methods reaches the same conclusion: individualised, autoregulated load prescription outperforms fixed prescription for strength over time.
— https://pmc.ncbi.nlm.nih.gov/articles/PMC7706636/

**Synthesis for the increment question:** The literature does not support *replacing* fixed increments with pure RIR autoregulation for novices — novices lack the experience to accurately self-assess RIR, and the deterministic, simple feedback of "add weight every session" is pedagogically valuable. However, the literature strongly supports **gating the fixed increment behind an RIR achievement check**: the increment proceeds only if the lifter demonstrated the target RIR on the work set. This is exactly the Greyskull pattern (AMRAP gate) translated to RIR. It preserves the simplicity and determinism of linear progression while incorporating the readiness-sensitivity that makes autoregulation superior in the research.

This gating is already architecturally present in Stratum's double-progression model (load increases only when the RIR target is met at the top of the rep range). Extending it to the linear scheme — gating the per-session increment behind RIR achievement — is a natural, literature-consistent design.

---

## Synthesis for Stratum D8

### Recommended approach: Greyskull-style hybrid — fixed per-session increment, RIR-gated, with a 2-stall / 10 %-reset / 2-reset-to-double transition

The recommended linear-progression algorithm for Stratum's `Linear` lifts is a **hybrid that preserves Stratum's existing rep-range + RIR top-set structure** while applying classic linear-progression mechanics. It is not a fixed-rep 3 × 5 scheme. The name "Greyskull-style" refers to the core design principle: a mandatory per-session increment that is *gated behind a performance check* (Greyskull uses an AMRAP set; Stratum uses the RIR target on the top set).

**Increment unit and frequency:**

- Load increases **every session** the lift is trained, by a **per-lift fixed increment**.
- Default increments (from SS/Stronglifts/Greyskull consensus — https://startingstrength.com/article/programming-a-smooth-nlp , https://support.stronglifts.com/article/71-progression):
  - Lower-body lifts (squat, deadlift, leg press): **5 lb / 2.5 kg per session**.
  - Upper-body lifts (bench, OHP, row, chin): **2.5 lb / 1.25 kg per session**.
- Increments are **per-lift configurable**, not global. The 2:1 lower-to-upper ratio is the established default, but Stronger By Science argues for even smaller upper-body increments when microplates are available (https://www.strongerbyscience.com/making-your-novice-strength-training-routine-more-effective-two-quick-tips/).

**Increment gate (RIR-based, reconciled with Stratum's top-set model):**

- The increment is applied **only if the top set was completed at or above the RIR target** within the rep range. Concretely: if the prescribed top set is "6–8 reps @ RIR 2," and the lifter achieved ≥ 6 reps at RIR ≥ 2, the increment proceeds next session. If the lifter achieved the reps but at RIR 0–1 (grinded it), the increment does **not** proceed — the lifter repeats the same weight. This is the Greyskull AMRAP gate translated to RIR.
- This gate is what distinguishes `Linear` from `Double` in Stratum's model:
  - **Linear**: increment when the **bottom** of the rep range is met at RIR (load-first; aggressive).
  - **Double**: increment when the **top** of the rep range is met at RIR (reps-first; conservative).
- Both schemes use the same rep-range + RIR top-set structure. The difference is solely the threshold that unlocks the next load.

**Rep/set assumption:**

- The top set uses the **same rep-range + RIR-target prescription** that Stratum already generates for `Double` lifts. No special fixed-rep (3 × 5) scheme is introduced.
- Backoff sets: same weight as the top set, AMRAP-at-RIR (identical to the `Double` backoff model in D8). This maintains architectural consistency — the overlay and snapshot layers do not branch on `ProgressionScheme` for set structure, only for the increment trigger.
- Recommended rep range for `Linear` lifts: a narrow strength-focused band (e.g., **5–8** or **4–6**), consistent with SS/Greyskull strength rep targets but expressed as a range rather than a fixed 5. The generation layer (`program-generation-v2`) owns the actual range; the progression engine consumes whatever range it is given.

**Stall trigger and reset:**

- **Stall = 2 consecutive sessions** where the top-set reps fall **below the bottom of the rep range** at the target RIR (i.e., the lifter could not complete the minimum prescribed reps at the prescribed RIR). This matches both the LiftLab recollection ("~2 stall sessions") and the Greyskull 2-consecutive-failure reset trigger (https://www.powerliftingtowin.com/greyskull-lp/ , https://www.boostcamp.app/coaches/r-fitness/greyskull-linear-progression).
- **Reset = 10 % load reduction**, then resume linear progression from the reduced load. The 10 % figure is the consensus across SS, Greyskull, and Stronglifts (https://startingstrength.com/article/the-reset-why-and-how , https://stronglifts.com/stronglifts-5x5/failure/). The reset percentage is per-lift configurable; upper-body lifts may use 5 % per Stronger By Science's argument (https://www.strongerbyscience.com/making-your-novice-strength-training-routine-more-effective-two-quick-tips/).
- After a reset, the stall counter returns to zero. The lifter must stall 2 consecutive sessions *again* at the post-reset load to trigger the next reset.

**What the algorithm transitions TO:**

- After **2 resets** on the linear scheme, the lift **transitions to double progression** (Stratum's primary model, D8). The `ProgressionScheme` on the lift changes from `Linear` to `Double`.
- This confirms the LiftLab recollection's *direction* (Linear → Double) while correcting its *trigger*: the transition is not after "2 stall sessions" (that triggers a *reset*), but after **2 full reset cycles** fail to re-establish linear progress. This aligns with the literature's principle that a programme change follows multiple failed resets (SS uses 3; Stratum uses 2 because the destination — double progression — is a gentler step than the literature's typical intermediate programme, and because the in-app transition is frictionless). The 2-reset threshold is the point at which per-session linear gains are exhausted: if two 10 % drops cannot re-establish session-to-session progress, the lifter's adaptation rate has dropped below the linear threshold.
- Rationale for 2 rather than SS's 3: SS's third reset buys time because the alternative (Texas Method) is a significant programme change. In Stratum, the alternative is the *same app, same set structure, gentler increment trigger* — the transition is low-friction, so there is no cost to transitioning earlier. Two resets is sufficient signal.
— https://www.andybaker.com/successful-transition-from-novice-to-intermediate-programming/ ; https://www.powerliftingtowin.com/starting-strength/

**Concrete edge cases:**

- **Deload week (scheduled):** treat as a reset-equivalent. Resume at 90 % of pre-deload load (standard 10 % deload). Reset the stall counter. Do not count the deload as one of the 2 resets that trigger a scheme transition — it is a fatigue-management event, not a strength plateau signal.
— https://stronglifts.com/stronglifts-5x5/failure/
- **Missed sessions (1–2 sessions / ≤ 2 weeks):** no change. Resume linear progression at the last successful load. The increment schedule continues from where it left off.
- **Extended break (> 2 weeks / 3+ missed sessions):** apply a one-time **10 % load reduction** to account for detraining, reset the stall counter, and resume. This mirrors the reset protocol — the load the lifter can handle has dropped, and attempting the pre-break load risks a false stall. Andy Baker describes this as standard practice for returning lifters.
— https://www.andybaker.com/successful-transition-from-novice-to-intermediate-programming/
- **Lift-specific stall sensitivity:** upper-body lifts (OHP, bench) stall sooner and more frequently than lower-body lifts (squat, deadlift). This is reflected in the **2:1 increment ratio** (upper-body increments are half the lower-body rate) and should be reflected in the reset percentage (upper-body may reset at 5 %, lower-body at 10 %). The deadlift may sustain the longest linear run and can use a larger initial increment (up to 10 lb / 5 kg per session in the first weeks, per Stronglifts). The progression engine should treat these as per-lift configuration, not hardcoded constants.
— https://startingstrength.com/article/programming-a-smooth-nlp ; https://support.stronglifts.com/article/71-progression ; https://www.strongerbyscience.com/making-your-novice-strength-training-routine-more-effective-two-quick-tips/
- **Plate availability / minimum increment:** if the smallest available plate is 2.5 lb / 1.25 kg (no fractional plates), upper-body linear progression is effectively 5 lb / 2.5 kg per session (the lower-body rate), which is too aggressive for OHP and bench. The engine should support microplate increments (1.25 lb / 0.5 kg) and surface this as a configuration concern. (See Open question 1.)

### Named alternatives (not recommended, for contrast)

1. **Pure Starting Strength NLP (fixed-rep 3 × 5, 3-stall / 3-reset → Texas Method).** This is the most-cited linear protocol. It would require Stratum to support fixed-rep targets (not rep ranges) for `Linear` lifts, introducing a structural branch in the snapshot/overlay layers. It also transitions to an *intermediate weekly programme* (Texas Method) that Stratum does not implement. Rejected because it breaks architectural consistency with the rep-range + RIR top-set model and transitions to a destination outside Stratum's current scheme set.
— https://startingstrength.com/article/the-reset-why-and-how ; https://www.powerliftingtowin.com/starting-strength/

2. **Stronglifts 5 × 5 style (5 × 5, 3-failure / 10 % deload, no transition).** Simpler than the recommended approach (no RIR gate; mandatory increment; deload after 3 failures). But it has no programme-transition mechanism — the lifter deloads indefinitely — and it forces 5 × 5 (fixed reps, high volume) which is inconsistent with Stratum's rep-range model and can produce the unrealistic-rep-range × set-count problem flagged in D10. Rejected for the same architectural-consistency reasons as SS, plus the absence of a transition path.
— https://stronglifts.com/stronglifts-5x5/failure/

---

## Open questions for the human

1. **Microplate / minimum-increment assumption.** The recommended default upper-body increment is 2.5 lb / 1.25 kg, which requires 1.25 lb / 0.5 kg plates (microplates). If Stratum assumes gym-standard 2.5 lb / 1.25 kg as the smallest plate, the effective upper-body increment doubles to 5 lb / 2.5 kg — too aggressive for OHP and bench, and inconsistent with the literature. Decision needed: does the progression engine assume microplate availability (and surface a UI note if the user indicates otherwise), or does it default to the larger increment and accept faster upper-body stalling? This affects the increment-configuration defaults.

2. **Automatic vs. user-confirmed scheme transition.** When a `Linear` lift exhausts 2 resets, the recommended algorithm transitions it to `Double`. Should this transition happen automatically (the engine mutates the lift's `ProgressionScheme`), or should it surface a prompt ("Bench Press has stalled out on linear progression — switch to double progression?") requiring user confirmation? An automatic transition is deterministic and matches the "no cloud, deterministic core" constraint, but it changes the lift's behaviour without explicit user action. This is a UX/policy decision, not a literature question.

3. **Stall definition strictness.** The recommended stall trigger is "top-set reps fall below the bottom of the rep range at the target RIR." There is a stricter alternative: "top-set reps fall below the bottom of the rep range at *any* RIR (i.e., the lifter physically cannot complete the reps, regardless of how many are in reserve)." The looser definition (RIR-based) treats a ground-out set where the lifter hit RIR 0 but completed the reps as a *non-stall* (the increment just doesn't proceed). The stricter definition would count it as a stall. Which does Stratum want? The literature (Greyskull) supports the looser/RIR-based definition, but the choice has user-experience implications for how quickly the reset triggers.

4. **Per-lift increment configuration model.** The recommendation requires per-lift increment sizes (lower-body 5 lb, upper-body 2.5 lb, deadlift possibly larger initially). Does Stratum's program/lift model (`program-generation-v2`) already support per-lift progression configuration, or does this need to be added? If the data model only supports a global increment, the 2:1 ratio cannot be implemented without a schema change. This is a codebase-structure question for the Synthitect to verify against the current domain model before Structure.
