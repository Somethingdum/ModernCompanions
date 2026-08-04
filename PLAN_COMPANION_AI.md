# Companion Intelligence & Perimeter Plan — Modern Companions (1.21.1 / NeoForge 21.1.219)

Target: Minecraft 1.21.1 Java, NeoForge `21.1.219`, Java 21, Parchment `2024.11.17`.
Scope: single-player / personal server. Balance decisions in this document optimise for
*one* player's experience, not for public-server fairness.

> **Note on `TASK.md`.** `TASK.md` currently describes the *Living Tribe Jobs Reliability
> Revamp* and is read-only per `AGENTS.md`. This plan is a separate workstream. Where the
> two touch (job goals, patrol centre, Assignment Wand) this plan reuses the existing job
> contract rather than replacing it, and Section 12 lists the exact overlap points.

---

## 0. What you asked for, mapped to what has to change

| Your ask | Where it lives today | What it becomes |
|---|---|---|
| Kill the taming system | `AbstractHumanCompanionEntity#mobInteract` L2185–2245, `ModConfig` `taming` block | Instant recruit on first interact (§2) |
| Start with one good companion, full iron | Nothing exists; `CompanionData#getSpawnArmor` L545 rolls 40% nothing / leather / chainmail / 10% iron | First-join starter Vanguard, guaranteed iron kit (§3) |
| Stick-like tool, two points define the base | Nothing exists. `AssignmentWandItem` is the closest pattern | **Perimeter Rod** + persistent zone store (§4) |
| Easy "who guards / who protects me" | 3 hand-synchronised booleans + `cycleOrders()` L2854 | Single `CompanionStance` enum + Squad screen + radial (§5) |
| Mad smart, not omniscient | `AlertGoal`/`HuntGoal` = raw `NearestAttackableTargetGoal` | Perception → blackboard → threat → tactics pipeline (§6, §7) |
| Great fighters, not OP | Stats via `assignRpgAttributes` L3416 | Power budget contract: intelligence up, numbers flat (§8) |
| Nothing crosses the perimeter | Nothing exists | Sensor-coverage sentries + honest breach contract (§9) |
| Plans for every situation | — | Situation matrix (§10) |

---

## 1. Findings from the current source that shape the plan

These are real defects/limits I found while reading, not hypotheticals. Several must be
fixed *before* new AI lands on top, or the new AI inherits them.

**1.1 Four movement goals share priority 3 and fight each other.**
`registerGoals()` (L456–501) registers `MoveBackToGuardGoal(3)`, `CustomFollowOwnerGoal(3)`,
then `moveBackGoal(3)` and `patrolGoal(3)`. All four hold `Flag.MOVE`. Within one priority
band, the winner is insertion order, not intent. Guard/follow/patrol therefore resolve by
accident. A single stance enum with exactly one active movement policy (§5) is the fix.

**1.2 Patrol goals are registered two to three times per companion.**
`registerGoals()` adds `patrolGoal`/`moveBackGoal` at L491–494. `readAdditionalSaveData`
constructs **new** instances and adds them again at L2668–2671 without removing the old
ones. `finalizeSpawn` does the same at L2818–2821. A loaded companion runs duplicate
patrol goals, and the stale copies still carry the old radius. Must be fixed with a
`goalSelector.removeGoal(...)` before re-add, or better: keep one goal that reads radius
live from the entity instead of caching it in a field.

**1.3 Targeting is currently semi-omniscient and stateless.**
`AlertGoal` and `HuntGoal` extend `NearestAttackableTargetGoal`, which sweeps an AABB and
picks nearest-valid. There is no memory, no field of view, no hearing, no light/weather
term, no sneak term, no sharing. It is simultaneously *too* powerful (no LOS discipline
across the whole selection) and *too* dumb (forgets a target the instant it breaks LOS).
This is the single biggest lever on "smart but not god".

**1.4 `AlertTargetRules` is `MONSTER && !unsafe && !excluded` — that's the whole model.**
Three booleans. No threat ordering, no focus fire, no ally awareness. It is a good, tested,
dependency-free *shape* though, and the plan keeps that shape (pure rules class + JavaExec
check task) for all new decision logic.

**1.5 There is no squad concept.** Every companion decides alone. Four companions will
chase four different skeletons while a creeper walks into the base.

**1.6 Ranged classes have no fire discipline.** `Archer#performRangedAttack` (L67) shoots
whenever the goal fires — no check for the owner or an ally on the shot line.
`Ideas.md` also logs *"Archer who shoot arrows at other entities gets the server to crash"*.
That crash needs its own reproduction pass (§13) — it is likely an entity-tick reentrancy
or a null projectile from `getProjectile` returning empty — and it should be fixed before
archers get more aggressive.

**1.7 `AbstractHumanCompanionEntity` is 3,763 lines.** It already carries inventory,
equipment, RPG stats, bond/morale, aging, voice, jobs, resources, Curios, chunk tickets.
New AI must **not** be added inside it. Everything below goes into new classes under
`entity/ai/…` with the entity holding one field: a `CompanionBrain`.

**1.8 Epic Fight compat rewrites the goal list.** `CompanionEpicFightPatch` +
`CompanionEpicFightPatchTest` exist specifically because patching stripped mage/ranged AI.
Every new goal must be registered in a way that survives that patch, and the existing test
must be extended to cover the new goals.

---

## 2. Removing the taming system

**Current flow.** `mobInteract` L2188: if `!isTame()`, the companion demands 1–2 random
food/resource items (`assignFoodRequirements`, `CompanionData.getRandomFoodRequirement`),
tracked through synced `FOOD1/FOOD1_AMT/FOOD2/FOOD2_AMT`, and only calls `tame(player)`
once every requirement hits zero.

**Change.**

1. Delete the entire `!isTame()` gating branch. First main-hand interact with an unowned
   companion calls `tame(player)` immediately, plays `Cue.GREETING`, sets
   `FIRST_TAMED_TIME`, and applies the recruit defaults already present at L2216–2223
   (patrol pos cleared, Follow on, radius 4 → change to config default).
2. **Do not delete the `FOOD1/FOOD2` synced fields.** They are read by the Jade plugin,
   the WTHIT provider, the Journal screen, and `getFoodStatus()`. Repurpose them as
   *favourite food* display only. This keeps `CompanionJadeProvider`,
   `CompanionWthitDataProvider`, and `CompanionJournalScreen` compiling untouched.
3. `CompanionData.getRandomFoodRequirement` / `assignFoodRequirements` /
   `syncFoodRequirements` become dead — remove the requirement path, keep
   `assignFavoriteFood`.
4. `ModConfig`: rename the `taming` push-block to `recruitment`. Keep `allFoods`,
   `extraHealConsumables` (they still drive healing via `CompanionData.isFood` and
   `LowHealthGoal`). Retire `commonResourceItems` / `uncommonResourceItems` /
   `rareResourceItems` — but keep the keys defined and unused for one version so existing
   config files do not error on load, then drop them.
5. Add `recruitMode` = `INSTANT` (default) | `HANDSHAKE` (sneak+interact, for when you
   don't want to accidentally recruit every villager-house resident you walk past).
6. `release()` (L2894) is unchanged — it stays the "dismiss" path.
7. Dialogue: `CompanionData.notTamed`, `tameFail`, `WRONG_FOOD`, `ENOUGH_FOOD` pools stop
   being referenced. Repoint the greeting to a new `RECRUIT_GREETING` pool so the
   personality flavour survives.

**Migration.** Existing saves have untamed companions standing in houses. They are simply
recruitable on next interact — no data migration needed. Existing tamed companions are
unaffected.

---

## 3. Starter companion — full iron, on first join

New `StarterCompanionEvents` (`@EventBusSubscriber(bus = GAME)`), on
`PlayerEvent.PlayerLoggedInEvent`:

- Guard flag in `player.getPersistentData()` under `modern_companions:starter_granted`
  so it fires exactly once per player per world.
- Spawn class from config `starterCompanionClass`, default **Vanguard**. Rationale: it is
  the only archetype that already implements shield use, taunt, projectile soak, and a
  defence aura (`Vanguard.java` L60–120), so it is the strongest single "keeps you alive
  at hour zero" pick without adding damage.
- Force-tame to the player, name it, set stance `ESCORT`, alert on.
- **Gear, bypassing `getSpawnArmor`'s RNG entirely:** iron helmet / chestplate / leggings /
  boots, iron sword, shield. Config `starterCompanionGear` = `IRON` | `LEATHER` | `NONE`
  so you can dial it later without a code change.
- Give the player one **Perimeter Rod** (§4) in the same grant, so the tutorial loop is
  self-contained.
- Spawn 2–3 blocks from the player using the same safe-spot search
  `SummoningWandItem#findSafeSpot` already implements — extract that into a shared
  `SafeSpawnUtil` rather than copying it.

Also add `/companion starter` (permission 0, self only, once per player) so existing worlds
can claim it, and `/companion starter force` (permission 2) for testing.

---

## 4. The Perimeter Rod — two points define the base

New item `modern_companions:perimeter_rod`. Stick-like: a stripped stick with a copper cap,
`stacksTo(1)`, no durability, `Rarity.UNCOMMON`.

### 4.1 Interaction model

| Input | Result |
|---|---|
| Right-click block, no pending corner | Sets **corner A**. Particle marker persists at that block. |
| Right-click block, corner A pending | Sets **corner B** → creates the zone, names it `Base 1`, selects it. |
| Sneak + right-click block | Sets **corner A** fresh (restart), discarding a pending A. |
| Right-click air | Cycles the **selected zone** among your zones in this dimension. |
| Sneak + right-click air | Toggles mode: **Define** ⇄ **Assign**. |
| Right-click a companion (Assign mode) | Assigns that companion to the selected zone and sets stance `WARD`. |
| Sneak + right-click a companion | Unassigns; returns it to `ESCORT`. |

### 4.2 Y handling

Two corners give X/Z. Y is the trap — a box that only covers the surface lets anything
tunnel under it. Default: the zone's Y span auto-expands from the lower corner
`-config.zoneDepthBelow` (default 12) to the higher corner `+config.zoneHeightAbove`
(default 24). Sneak-clicking corner B locks the exact Y of the two clicked blocks instead.
Both values are per-zone and editable in the zone screen.

### 4.3 Storage

`PerimeterZoneData extends SavedData`, attached per-`ServerLevel` (so zones are per
dimension, which is correct — a Nether base is a different zone).

```
record PerimeterZone(UUID id, UUID owner, String name,
                     BlockPos cornerA, BlockPos cornerB,
                     int depthBelow, int heightAbove,
                     ZonePolicy policy) {
    AABB box();            // normalised, Y-expanded
    boolean contains(Vec3);
    double distanceToEdge(Vec3);
}
```

`ZonePolicy` holds the per-zone rules of engagement: `hostilesOnly` / `alsoNeutrals` /
`alsoPlayers`, an allow-list of entity ids and player UUIDs, and `lethalForce` on/off.

**Zones live in the level, not on the entity.** Reasons: many companions share one zone;
a zone survives its guards dying; and the existing `PATROL_POS` + `getPatrolRadius`
(a circle) cannot express a rectangle. Companions store only a `zoneId` (nullable UUID) in
synced data + NBT.

The existing circular patrol radius stays for `WORK` stance (jobs already depend on
`getWorkCenter` / `getPatrolRadius` — see `MinerJobGoal`, `LumberjackJobGoal`). Rectangular
zones are for `WARD` only. Two systems, clearly separated, no job breakage.

### 4.4 Visual feedback

While the rod is held, the server sends `ServerLevel#sendParticles` to the holder only,
tracing the selected zone's edges — dense at eye level, sparse on the vertical extents,
every 10 ticks, capped at ~200 particles per pulse with spacing scaled to zone size.
Colour-coded: green = selected zone, white = other zones, yellow = pending corner A.
No new client packet needed; particles are already player-targeted.

Tooltip on the rod shows: mode, selected zone name, size, assigned companion count.

---

## 5. Stances — the "who guards / who protects me" system

### 5.1 Replace three booleans with one enum

Today: `FOLLOWING`, `PATROLLING`, `GUARDING` synced booleans, kept mutually exclusive by
hand in `cycleOrders()` (L2854), `mobInteract`, `release()`, `ModNetwork#handleToggleFlag`,
and `readAdditionalSaveData` — five places that can and do drift.

New: one synced `CompanionStance`:

| Stance | Meaning | Movement policy | Combat policy |
|---|---|---|---|
| `ESCORT` | **Protects you.** | Stay within radius of owner, teleport-leash per `FollowLeashRules` | Engage anything that threatens the owner or itself; never leave owner's radius + 8 to chase |
| `WARD` | **Guards a zone.** | Bound to a `PerimeterZone`; sentry post + boundary patrol | Engage anything breaching the zone under `ZonePolicy`; hard leash at zone edge + 6 |
| `WORK` | Doing a job. | Existing job goals, `getWorkCenter` + radius | Self-defence only; flee-and-resume above a threat threshold |
| `HOLD` | Stand fast here. | None | Engage only within melee/short range; never pursue |

`isAlert` and `isHunting` stay as orthogonal toggles — they are already independent and
that's correct.

### 5.2 Compatibility trick (important — this keeps the diff small)

Keep `isFollowing()` / `isPatrolling()` / `isGuarding()` as **derived** methods over the
enum, and make the setters coerce the enum. Every existing call site — `PatrolGoal`,
`MoveBackToPatrolGoal`, `MoveBackToGuardGoal`, `CustomFollowOwnerGoal`, all five job goals,
`DeliverToChestGoal`, `CompanionJadeProvider`, the WTHIT providers, `CompanionScreen`,
`ModNetwork` — compiles and behaves unchanged. On `readAdditionalSaveData`, if the new
`Stance` tag is absent, derive it from the legacy `Following`/`Patrolling`/`Guarding`
booleans (L2495–2497). One-way migration, no save loss.

### 5.3 The UI — this is the "easy" half of the ask

1. **Stance strip in `CompanionScreen`.** Replace the `patrol`/`guard`/`follow` buttons
   (L99, L102, L116) with four mutually-exclusive icon buttons. One click, unambiguous,
   no more "did I turn patrol off before turning guard on".
2. **Squad screen** — new, opened by a keybind (default `K`) or a button in the companion
   screen. One row per owned companion in the loaded world: portrait, name, class, health
   bar, current stance selector, zone dropdown, and a "locate" button that pings them.
   Bulk actions across the top: *All Escort*, *All Ward → [zone]*, *All Hold*, *Recall*
   (reuses `SummoningWandItem` logic). This is the fastest possible answer to "which ones
   guard and which ones protect me": two clicks for the whole roster.
3. **Radial menu** — hold a keybind (default `R`): if you're looking at a companion, the
   radial targets that one; otherwise it targets the whole squad. Wedges: Escort / Ward /
   Hold / Work / Focus-my-target / Fall-back.
4. **Point-and-order.** With the radial key held, look at a mob and click → squad focus-fires
   it. Look at a block and click → squad moves there and holds. No GUI needed mid-fight.
5. **Chat bubbles instead of chat spam** (already in `Ideas.md`). With squad comms (§6.3)
   companions will have a lot more to say; routing it through overhead bubbles with a
   per-companion cooldown keeps the chat log usable. Config: `bubbles` / `chat` / `both`.

New payloads needed: `SetCompanionStancePayload`, `AssignZonePayload`,
`SquadOrderPayload` (bulk), `FocusTargetPayload`. All follow the existing
`ToggleFlagPayload` shape and register in `ModNetwork#register`. All must re-check
`companion.isOwnedBy(serverPlayer)` server-side exactly as the current handlers do.

---

## 6. The intelligence architecture

Pipeline, per companion, server-side only:

```
Perception  →  Blackboard  →  Threat model  →  Plan selection  →  Goal arbitration
 (what I     (what I         (what matters    (what I intend    (vanilla Goals
  can sense)  remember)       right now)       to do)            executing it)
```

The point of this shape: intelligence comes from the **middle three stages**, which are
pure, testable, and cheap. The first stage is deliberately *lossy* — that's the
anti-omniscience valve. The last stage stays vanilla `Goal`s so Epic Fight, jobs, and
existing behaviour keep working.

### 6.1 `CompanionPerception` — the anti-omniscience layer

A companion may only act on entities it has actually sensed. Three channels:

**Sight.** Range = `Attributes.FOLLOW_RANGE`, modulated:
- FOV cone of 120° forward; full 360° awareness only within 6 blocks.
- Requires `hasLineOfSight`.
- × `0.4` in darkness (light level < 5 at the target) unless the target is on fire,
  glowing, or holding a light source.
- × `0.7` in rain, `× 0.5` in thunderstorm, `× 0.6` in heavy fog biomes / powder snow.
- × `0.5` if the target `isCrouching()` — sneaking works on companions like it works on mobs.
- × `(1 + (INT − 4) × 0.02)`, capped at ±20%. INT buys awareness, not damage.

**Hearing.** Radius ~12 blocks, no LOS needed, **produces a position, not an identity.**
Sources: sprinting/non-sneaking movement, block break/place, explosions, mob ambient
sounds, door use, projectile impacts. Output is an `AudioCue(pos, loudness, tick)` that
seeds an *investigate* plan, never an attack. This is what makes them read as alert
without being clairvoyant.

**Comms.** Contacts propagate to other companions **of the same owner** within 24 blocks,
and to the owner. A relayed contact arrives with reduced confidence and a 1–2 tick delay,
and is tagged with its origin. This is the mechanism that makes a perimeter work at all
(§9) and it is *earned* — someone had to actually see it.

**Memory & decay.** Each contact carries `lastSeenPos`, `lastSeenVelocity`, `firstSeenTick`,
`confidence ∈ [0,1]`. On LOS loss confidence decays over ~200 ticks. A companion pursuing
a lost contact searches the **extrapolated** position, checks the obvious cover, then gives
up and reports. Never a snap-back lock.

**Budget.** One sweep every 10 ticks (phase-offset per entity id so they don't all sweep on
the same tick). Single `getEntitiesOfClass` over an AABB capped at `FOLLOW_RANGE`. Contact
list capped at 16, evicting lowest threat. No chunk scanning, no world queries.

### 6.2 `CompanionBlackboard`

Per-companion scratch state: contact list, last damage source + direction + tick, current
plan + plan age, retreat point, danger cells (lava/fire/cactus/magma/void edge/powder snow)
noted during pathing, ammo/durability state, ally states, owner's last known position and
health, zone breach list.

### 6.3 `SquadBlackboard`

One per owner, per dimension, lifetime-managed with the player. Holds: shared contacts,
the **focus target** (with a claim count so N companions don't all pile on one skeleton
while a creeper walks free), role claims (who is tanking, who is peeling to the owner, who
is holding the north gate), and a distress queue.

Rules that fall straight out of having this:
- Focus fire on the highest-threat contact, but cap claimants at `ceil(squad/2)`.
- Anything attacking the **owner** immediately outranks the focus target for the two
  nearest companions.
- Downed/low companions broadcast distress; the nearest healer-capable ally (Cleric,
  Alchemist, Druid — all already exist) claims the response.

### 6.4 `ThreatAssessment` — pure, testable

Matching the repo's existing convention (`AlertTargetRules`, `FollowLeashRules`,
`WorkerSafetyPredicates`: dependency-free static rules + a JavaExec `check` task):

```
score(contact) =
    w1 · proximityTerm(distance, myRole)
  + w2 · (isAttackingOwner ? 1 : 0)
  + w3 · (isAttackingMe ? 1 : 0)
  + w4 · estimatedDps(type, equipment)
  + w5 · (1 − targetHealthFraction)          // finish wounded things
  + w6 · imminenceTerm(creeperFuse, drawnBow, windup)
  + w7 · zoneBreachDepth(zone, pos)          // deeper into my base = worse
  − w8 · alliesAlreadyEngaged(contact)
  − w9 · (counteredByMe ? 1 : 0)             // e.g. axe-wielder vs my shield
```

All weights in config under `ai.threatWeights` so behaviour is tunable without a rebuild.
`estimatedDps` reads from a data-driven map (`data/modern_companions/ai/threat_profiles.json`)
with a sane fallback, so modded mobs slot in via datapack instead of a hardcoded list —
same lesson `HuntGoal`'s six hardcoded classes already teaches.

### 6.5 `CompanionBrain` and plans

One `CompanionBrain` field on the entity. It ticks the pipeline and selects exactly one
**plan** from a priority-ordered list. A plan is small: preconditions, a utility score, and
the goals it enables. Plans re-evaluate every 10 ticks, with hysteresis (a running plan gets
a +15% score bonus) so they don't thrash.

Plan set: `DefendOwner`, `InterceptBreach`, `EngageTarget`, `SpaceAndShoot`, `Flank`,
`Kite`, `BlockAndHold`, `Peel`, `Investigate`, `Retreat`, `HealSelf`, `HealAlly`,
`Revive`, `Regroup`, `Reposition`, `SentryHold`, `PatrolBoundary`, `Work`, `Idle`.

Goal arbitration: the brain enables/disables a small set of goals it owns rather than
adding and removing them (add/remove during ticking is how the current duplicate-goal bug
happens). One `BrainDrivenMoveGoal` + one `BrainDrivenAttackGoal` at fixed priorities,
each asking the brain what to do. Existing class-specific goals
(`ArcherRangedBowAttackGoal`, `MageRangedAttackGoal`, `ArbalistCrossbowAttackGoal`,
`FirearmAttackGoal`) stay and are gated by the brain rather than replaced — that keeps
Epic Fight compat and the firearms integration intact.

---

## 7. Combat competence — what "extremely good fighter" actually means

Every item here is a *decision* improvement, costing zero extra damage or health.

**Melee**
- Don't walk in a straight line into a ranged attacker. Strafe-approach when the target is
  ranged and has LOS; break LOS behind cover where a cheap raycast finds it.
- Shield timing: raise on projectile-incoming and on target wind-up; drop to swing.
  `Vanguard` has the parts; generalise to anyone holding a shield.
- Never body-block the owner. Soft repulsion from the owner's forward vector within 2
  blocks — this is the single most-hated companion-mod behaviour and it's cheap to fix.
- Back off from a creeper inside fuse range instead of trading; let ranged handle it.
- Don't drop more than 3 blocks in pursuit; don't enter lava/fire/powder snow ever.
- Target switching costs a short commit window so they don't jitter between two mobs.

**Ranged**
- Maintain a preferred band (8–16 blocks); back-pedal if the target closes inside 4.
- **Fire discipline:** no shot if the line from muzzle to target passes within 1.2 blocks
  of the owner or an ally. Pure geometry (point-to-segment distance), a handful of ops.
  This alone eliminates most "my archer keeps shooting me" complaints.
- Lead moving targets using `lastSeenVelocity` — but with the aim error from §8 so it's
  good, not perfect.
- Reposition for LOS instead of standing still plinking a wall.
- Ammo/durability awareness: report and fall back to melee at zero, don't stand there
  miming a bow.

**Magic** (13 mage classes already exist via `AbstractMageCompanion` / `MagicCastingCompat`)
- Spend mana on utility when no good offensive line exists rather than dumping it.
- Don't AoE where the owner is inside the radius.
- Cleric/Druid: triage by ally health fraction, not proximity.

**Squad**
- Focus fire (§6.3), peeling to the owner, and role claims.
- Formation on the move: tanks forward-left/right of the owner, ranged behind, healer
  centred. Recomputed on roster change, not every tick.

**Survival**
- Disengage below a health threshold — retreat *behind an ally* if one exists, else toward
  the owner — heal (`LowHealthGoal` already eats; extend it to potions, which exist:
  `CompanionPotionItem`), then re-engage. Currently they eat mid-swing and die.

---

## 8. The power budget — how they stay smart without becoming gods

This is a written contract for the implementation. Every one of these is a hard rule.

1. **No stat inflation.** Damage, health, armour, and speed stay exactly where
   `assignRpgAttributes` (L3416) and gear put them. Nothing in this plan adds a point.
2. **Reaction latency.** Perception→action carries a delay of `8 − (INT − 4) × 0.4` ticks,
   clamped to `[3, 10]`. They are never frame-perfect. This is the main "not a robot" dial.
3. **Aim error floor.** Ranged spread never reaches zero: `spread = max(0.6°, base − DEX·k)`,
   growing with distance and target speed. A high-DEX Archer is very good, not a hitscan.
4. **Surprise penalty.** Damage from an unperceived direction costs ~20 ticks of degraded
   accuracy and reaction while they orient. Ambushes work on them.
5. **Sense limits are the ones in §6.1** and are never bypassed "because it's convenient".
   No goal may query an entity that isn't in the contact list.
6. **Squad size cap.** `ai.maxActiveCombatants` default 4. Beyond that, extras hold
   position. Prevents a 12-companion deathball trivialising everything.
7. **Difficulty profile.** One config enum `ai.profile` = `RECRUIT` | `VETERAN` | `ELITE`,
   scaling **only** reaction latency, perception ranges, and aim error. Never damage,
   never health. That is your single knob if they end up too strong or too weak.
8. **Tick budget.** `ai.brainTickInterval` default 10, perception phase-offset per entity,
   contact cap 16, plan re-evaluation capped. Target: < 0.15 ms/companion/tick on a
   4-companion squad.

The honest summary: they will out-*think* a vanilla mob badly, and out-*stat* it not at all.

---

## 9. The perimeter guarantee — what can and can't be promised

You asked that nothing cross the perimeter *to their knowledge*. That phrasing is exactly
right, and the design honours it literally rather than faking omniscience.

**9.1 Sensor coverage, not clustering.** When N companions ward one zone, they are placed
by greedy farthest-point selection along the zone boundary so their sight cones maximise
covered perimeter. Recomputed only on roster/zone change. Two guards on a base cover
opposite approaches instead of standing on the same corner.

**9.2 Phase-offset boundary patrol.** Sentries walk their arc of the boundary, offset in
phase, so the swept coverage over time is far greater than the instantaneous coverage.

**9.3 Home-field advantage.** Inside their own zone a warded companion gets a *fair*
sensory bonus, because they know the ground: hearing radius ×1.5, and a low-confidence
directional "something's wrong over there" cue when a hostile is inside the zone but
unseen. It is a **hint, not a target lock** — it seeds an `Investigate` plan toward a
position. This is the mechanism that makes the perimeter feel airtight without granting
x-ray vision.

**9.4 Breach ledger.** A cheap server-side sweep of the zone AABB every 20 ticks (capped
entity count, skipped if the zone's chunks are unloaded) records hostiles inside the zone
as *breaches*. A breach is **not** directly actionable — it only raises the §9.3 hint and
starts a timer. If a breach persists un-perceived for `zoneAlertSeconds` (default 15), you
get a HUD warning and an audible cue. That's the honest contract: they'll catch nearly
everything, and when they genuinely can't, **you are told** rather than quietly losing a
chest room.

**9.5 Intercept, don't chase.** A warded companion moves to cut the intruder off from the
zone *centre*, not toward the intruder. And the leash is hard: never more than 6 blocks
past the zone edge. Guards being kited out of the base is how bases die; this is the fix.

**9.6 Approach vectors.** Zone Y-depth (§4.2) covers tunnelling; the height covers phantoms
and flying mobs. Water and ravine approaches inside the zone are flagged at zone creation
and weighted higher in sentry placement.

**9.7 Spawn-proofing awareness.** Sentries note dark cells (light < 1) inside the zone and
report them ("the north storeroom is dark"). Optional, **off by default**:
`zoneAutoLighting` lets a warded companion place torches from its own inventory. Off by
default because a companion silently editing your base is exactly the kind of thing that
should be opt-in.

**9.8 Chunk reality.** If a zone's chunks aren't loaded, nothing is happening there — no
mobs, no guards, no breaches. `JOB_ASSIGNED_CHESTS_CHUNKLOAD` already establishes the
chunk-ticket pattern; a matching `zoneChunkload` (default **off**) can force-load a zone.
Documented plainly so the behaviour isn't a surprise.

---

## 10. Situation matrix — the "plan for every situation" ask

**Owner-centred (ESCORT)**

| Situation | Response |
|---|---|
| Owner attacked in melee | Two nearest peel and engage; tank taunts; others hold focus |
| Owner attacked at range | Nearest ranged counter-fires; nearest tank interposes on the shot line |
| Owner below 40% health | Healer claims; others tighten formation and pull aggro |
| Owner dies | Squad kills what's present, then holds the death position as a recovery escort until owner returns or 5 min elapse |
| Owner sprinting away | Follow at radius; abort pursuit of anything beyond owner radius + 8 |
| Owner sneaking | Companions reduce chatter, hold fire on unaware targets, tighten spacing |
| Owner mining / building | Stance auto-softens to a wider guard ring; nobody stands in the doorway |
| Owner enters water / boat | Swim-capable follow; do not enter deep water in heavy armour; wait on shore and re-link on exit |
| Owner changes dimension | Existing `CompanionEvents` dimension transfer handles it; carry stance + zone reference (zone stays behind, correctly) |
| Owner goes AFK | Fall to `HOLD` ring after `afkSeconds`; resume on movement |
| Owner attacks a villager/animal | Do **not** auto-assist on non-hostiles unless `Hunt` is on. Prevents accidental village massacres |

**Zone-centred (WARD)**

| Situation | Response |
|---|---|
| Hostile crosses boundary | Nearest sentry with LOS engages, broadcasts contact, adjacent sentry shifts to cover the gap |
| Hostile inside zone, unseen | §9.3 hint → `Investigate` → on visual, normal engagement; §9.4 timer if it persists |
| Mob spawns *inside* the zone (dark cell) | Same as breach; plus a dark-cell report (§9.7) |
| Intruder tunnels in from below | Y-depth covers it; hearing picks up block breaks; sentry investigates |
| Intruder flies in above (phantom/ghast) | Ranged sentry claims; melee holds the ground; height extent covers detection |
| Creeper approaches | Ranged intercepts *outside* the wall; melee explicitly does not trade with it |
| Enderman inside zone | Do not look at it (skip eye-contact targeting) unless it's already aggressive |
| Skeleton/pillager at range outside the zone | Engage only if it has LOS into the zone; otherwise hold — no getting baited out |
| Raid / horde > 5 hostiles | Squad falls back to a chokepoint inside the zone (narrowest boundary gap), fights there instead of scattering |
| Intruder is another player | Governed by `ZonePolicy.alsoPlayers`, default **off**; allow-list for friends |
| Owner's own pets/villagers inside zone | Never targets. Allow-list is automatic for owned tamables |
| Owner builds/mines inside the zone | Zone geometry is static; no reaction. Block edits by the owner never register as threat |
| Fire spreading / lava inside zone | Report to owner; move out of the hazard; optional (off by default) water-bucket response |
| Zone chunks unload | Nothing happens; guards suspend cleanly and resume on load, no phantom breaches |
| Guard dies | Roster change → surviving sentries redistribute coverage immediately |
| Zone deleted or corners invalid | Wards revert to `HOLD` at their post and report, rather than reverting to Follow and wandering off |

**Self-centred (any stance)**

| Situation | Response |
|---|---|
| Below health threshold | Disengage behind an ally → eat/potion → re-engage |
| Weapon breaks | Auto-swap from inventory (`checkSword`/`checkBow` already do this); report if nothing left |
| Out of arrows | Fall back to melee, report |
| Stuck / path blocked | Three replan attempts, then a short unstick nudge, then report and hold — never silently spin |
| Falling / in lava / burning | Highest-priority escape plan, overrides all combat |
| Drowning | Surface plan; existing `boostWaterMovement` and committed-swim support this |
| Night falls, no shelter | Ranged prefer high ground; melee close ranks; no behaviour change beyond spacing |
| Thunderstorm | Perception penalty per §6.1; Stormcaller gets its existing flavour buff |
| Nether / End | Lava and void-edge cells added to danger set; no ghast melee chasing over lava |

---

## 11. New and changed files

**New — AI core** (`entity/ai/brain/`)
`CompanionBrain`, `CompanionPerception`, `PerceivedContact`, `CompanionBlackboard`,
`SquadBlackboard`, `SquadManager`, `ThreatAssessment` *(pure)*, `PerceptionRules` *(pure)*,
`FireLineRules` *(pure)*, `FormationRules` *(pure)*, `Plan` + ~19 plan implementations,
`BrainDrivenMoveGoal`, `BrainDrivenAttackGoal`.

**New — perimeter** (`world/zone/`, `item/`, `client/screen/`)
`PerimeterZone`, `ZonePolicy`, `PerimeterZoneData extends SavedData`, `ZoneCoverageRules`
*(pure)*, `ZoneBreachTracker`, `PerimeterRodItem`, `ZoneScreen`, `SquadScreen`,
`CompanionRadialScreen`.

**New — misc**
`StarterCompanionEvents`, `SafeSpawnUtil`, `CompanionStance`, four network payloads,
`data/modern_companions/ai/threat_profiles.json`.

**Changed**
`AbstractHumanCompanionEntity` — remove taming branch; add `brain` + `stance` + `zoneId`;
fix duplicate goal registration (§1.2); goal priorities (§1.1).
`ModConfig` — `recruitment` rename, new `ai` and `zones` sections.
`ModItems`, `ModCreativeTabs`, `ModNetwork`, `CompanionScreen`, `ModCommands`,
`CompanionEvents`, `AlertGoal` / `HuntGoal` (perception-backed), `LowHealthGoal`
(potions + disengage), `Archer` / `Arbalist` / `FirearmSpecialist` (fire discipline),
`CompanionEpicFightPatch` (preserve new goals), `build.gradle` (new check tasks),
`gradle.properties` (version bump per commit, per `AGENTS.md`).

---

## 12. Interaction with the in-flight Jobs revamp (`TASK.md`)

- `WORK` stance maps 1:1 to today's `getJob() != NONE` checks — the derived
  `isPatrolling()` shim (§5.2) keeps all five job goals working untouched.
- Circular patrol radius stays exclusively for jobs. Rectangular zones are ward-only.
- `DeliverToChestGoal` and `JobReservations` are untouched.
- The brain treats an active job as the `Work` plan and yields movement to the job goals,
  only pre-empting for self-defence and hazard escape.
- The Assignment Wand keeps its chest-binding role; the Perimeter Rod does not overlap it.

---

## 13. Phasing

Each phase = one commit: builds green, `TRACELOG.md` + `SUGGESTIONS.md` entries,
`gradle.properties` version bump, per `AGENTS.md`.

| # | Phase | Delivers | Risk |
|---|---|---|---|
| 0 | **Repairs** — duplicate goals (§1.2), priority collisions (§1.1), archer crash (§1.6) | Stable base | Low |
| 1 | **Taming removal** + `recruitment` config | Instant recruit | Low |
| 2 | **`CompanionStance`** + migration + stance strip in `CompanionScreen` | Coherent orders | Medium — touches many call sites, mitigated by §5.2 |
| 3 | **Starter companion** + iron kit + `/companion starter` | Hour-zero experience | Low |
| 4 | **Perimeter Rod** + `PerimeterZoneData` + zone rendering | Zones exist | Medium |
| 5 | **Perception + blackboard** (`AlertGoal`/`HuntGoal` repointed) | Fair senses, memory | High — this is the core |
| 6 | **Threat model + squad blackboard + focus fire** | Coordination | Medium |
| 7 | **Combat plans** (melee/ranged/magic/survival, §7) | Fighting quality | High |
| 8 | **Ward behaviour** — coverage, patrol, intercept, breach ledger (§9) | Perimeter promise | Medium |
| 9 | **Squad screen + radial + point-and-order + bubbles** | Ease of use | Low |
| 10 | **Tuning pass** — `ai.profile`, weights, perf profiling | Balance | Low |

Ship-usable after phase 4; the AI is real from phase 7; the perimeter promise lands at 8.

---

## 14. Testing

Following the repo's existing convention exactly — pure rules classes + `JavaExec` tasks
wired into `check` (as `workerSafetyCheck`, `alertTargetCheck`, `followLeashCheck`,
`structureSpawnCheck` already are):

- `PerceptionRulesTest` — FOV, LOS, light/weather/sneak modifiers, decay curve, comms relay.
- `ThreatAssessmentTest` — ordering under fixed scenarios; owner-attacker always outranks
  a nearer non-attacker; ally-engaged discount; creeper imminence.
- `FireLineRulesTest` — no-fire when owner/ally within the corridor; fire when clear.
- `ZoneGeometryTest` — corner normalisation, Y expansion, contains/edge-distance,
  boundary sampling.
- `ZoneCoverageRulesTest` — N sentries on a rectangle produce distributed, deterministic posts.
- `StanceMigrationTest` — every legacy boolean combination maps to the right stance.
- `FormationRulesTest` — no companion placed on the owner's forward vector.
- Extend `CompanionEpicFightPatchTest` — brain goals survive the Epic Fight patch.
- Manual matrix: run every row of §10 in a dev world and record results in `TRACELOG.md`.

---

## 15. Open decisions for you

1. **Starter class** — Vanguard (recommended: shield tank, best survivability, zero damage
   creep) vs Knight (plain melee) vs Cleric (healing).
2. **Zone shape** — rectangle from two corners as specified, or should a third click add a
   third point for an L-shaped/polygon base?
3. **`ZonePolicy.alsoPlayers`** — default off. Turn on if you ever play with others.
4. **Squad cap of 4 active combatants** — raise, lower, or make it a slider?
5. **`ai.profile` default** — `VETERAN`, or start at `ELITE` and dial back?
6. **`zoneAutoLighting`** — leave opt-in, or on by default?
