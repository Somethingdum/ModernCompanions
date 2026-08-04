# Modern Companions — Total Overhaul Implementation Plan

**Target:** Minecraft 1.21.1 Java · NeoForge `21.1.219` · Java 21 · Parchment `2024.11.17`
**Audience:** the implementing agent (Fable 5). This document is a build spec, not a pitch.
**Scope:** single-player / personal server. Balance is tuned for one player's experience.
**Supersedes:** the previous revision of this file.

---

## §0 — How to use this document

### 0.1 Ground rules (from `AGENTS.md`, non-negotiable)

- Edit only `src/**`, root docs, and build configs. Everything else is read-only.
- **Confirm the project builds before every commit.** `./gradlew check build --console=plain --no-daemon`.
- Every commit: `TRACELOG.md` entry (prompt, steps, rationale, build/test) + `SUGGESTIONS.md`
  entry + `gradle.properties` version bump + clear code comments.
- `TASK.md` and `AGENTS.md` are read-only. Do not edit them.

> **Build note:** the authoring environment could not compile — its network policy returns
> 403 on CONNECT to `maven.neoforged.net`, `maven.blamejared.com`, and
> `maven.theillusivec4.top`, so `neoform-runtime` will not resolve. Nothing in the source
> tree is broken by this. The implementer **must** build locally; no phase below is
> complete without a green `check build`.

### 0.2 Definition of done, per phase

1. `./gradlew check build` green.
2. New pure-logic classes have a `*Test` with a registered `JavaExec` task wired into `check`
   (the existing pattern: `workerSafetyCheck`, `alertTargetCheck`, `followLeashCheck`,
   `structureSpawnCheck`, `epicFightCombatAiCheck`).
3. The phase's **acceptance criteria** in §20 are demonstrated in a dev world and the result
   recorded in `TRACELOG.md`.
4. No regression in the §17 performance budget.

### 0.3 Naming conventions used below

- Package root: `com.majorbonghits.moderncompanions`.
- New AI: `entity.ai.brain.*`. New squad code: `squad.*`. New zones: `world.zone.*`.
- Pure rules classes are `final`, have a private constructor, take primitives/records only,
  and never import `net.minecraft.world.level.Level`.

### 0.4 The three hard constraints

| Constraint | Meaning |
|---|---|
| **Not omniscient** | A companion may only act on information it sensed (§6) or was told by an ally who sensed it. No goal may query an entity that is not in its contact list. |
| **Not overpowered** | Damage, health, armour, attack speed, and movement speed stay exactly where gear and `assignRpgAttributes` put them. Every improvement is a decision-quality improvement. §17.3 is the audit list. |
| **Not cowardly** | They withdraw tactically, they never rout. §9 states the absolute rules. |

---

## §1 — Defect register

Every item below was found by reading the current source. Fix all of them. Severity:
**S1** = corruption/crash/major perf, **S2** = wrong behaviour, **S3** = fragility/debt.

| ID | Sev | Location | Defect |
|---|---|---|---|
| D-01 | S1 | `AbstractHumanCompanionEntity.java:491-494`, `:2668-2671`, `:2818-2821` | Patrol goals registered up to 3× per entity |
| D-02 | S2 | `:462`, `:463`, `:493`, `:494` | Four `Flag.MOVE` goals share priority 3 |
| D-03 | S1 | `CompanionProtectionEvents.java:65-82`, `:159-167` | Reflection + exception per mob per tick |
| D-04 | S1 | `CompanionVoice.java:60-88` | Full loaded-level entity scan per callout |
| D-05 | S2 | `AlertExclusionDefaults.java:8`, `AvoidCreeperGoal.java` | Companions never fight creepers |
| D-06 | S2 | `AlertGoal.java`, `HuntGoal.java` | Stateless, memoryless nearest-target selection |
| D-07 | S2 | `Archer.java:67`, `Beastmaster.java:154`, `Arbalist`, `FirearmAttackGoal`, `MageRangedAttackGoal` | No fire discipline — allies on the shot line |
| D-08 | S1 | `Ideas.md` | Unreproduced archer crash |
| D-09 | S1 | `AbstractHumanCompanionEntity.java:358` | `FOLLOW_RANGE = 20` caps pathfinding to ~20 blocks |
| D-10 | S2 | `:343-344` | Water malus 0, no hazard maluses at all |
| D-11 | S2 | `CompanionGroundPathNavigation.java:28-36` | Node evaluator only knows pointed dripstone |
| D-12 | S1 | `MoveBackToGuardGoal.java:37`, `MoveBackToPatrolGoal.java:41` | `moveTo()` every tick — repath storm |
| D-13 | S2 | `MoveBackToGuardGoal.java:24-33` | `canContinueToUse() == canUse()`, no hysteresis |
| D-14 | S2 | `CustomFollowOwnerGoal.java:49-56` | Goal stops the instant the path completes |
| D-15 | S2 | `CustomFollowOwnerGoal.java:78-84`, `SummoningWandItem` | Teleport-first leash — the immersion break |
| D-16 | S3 | `CustomHurtByTargetGoal.java:28-30`, `:83-91` | `toIgnoreAlert` always null; `toIgnoreDamage` always empty |
| D-17 | S2 | `CustomHurtByTargetGoal.java:40-58` | A companion can retaliate against its own owner |
| D-18 | S2 | `EatGoal.java` | No goal flags; `stop()` self-called from `tick()` |
| D-19 | S2 | `LowHealthGoal.java` | No flags, no `canContinueToUse`; eats without disengaging |
| D-20 | S3 | `AbstractHumanCompanionEntity.java:461` | `AvoidCreeperGoal` at priority 2 collides with attack goals |
| D-21 | S1 | `Knight.java:26`, `Vanguard.java:50-52`, `Archer.java:30` | Goals added in subclass constructors; **Vanguard gets two `MeleeAttackGoal`s** |
| D-22 | S3 | `ModConfig.java:284-291` | Config `save()` during a config-load event |
| D-23 | S3 | `PatrolGoal.radius`, `MoveBackToPatrolGoal.radius` | Radius cached in goal fields → drives D-01 |
| D-24 | S3 | `AbstractHumanCompanionEntity.java` | 3,763-line god object |
| D-25 | S2 | `Archer.java:88-92`, `Knight.java:66-70` | Mainhand cleared before `checkBow`/`checkSword` on load |
| D-26 | S3 | `HuntGoal.java:22-25` | Hardcoded/config-only hunt list; no tags, no modded animals |
| D-27 | S2 | `MageRangedAttackGoal.java:70-86` | Two conflicting `moveTo` calls in one tick |
| D-28 | S2 | `TASK.md` (entire audit) | Jobs system defects, unresolved |
| D-29 | S3 | `src/test/**` | 8 pure predicates only; no state machine or navigation coverage |
| D-30 | S3 | `ArcherRangedBowAttackGoal.java:41-43` | Runs on with a null target while the nav drains |

### 1.1 Notes on the non-obvious ones

**D-03 — the worst performance bug in the repo.**
`enforceSummonTarget` subscribes to `EntityTickEvent.Pre`, which fires for **every mob in
the world**. For any mob that currently has a target it runs every tick, and calls
`summonerOf()` → `entity.getClass().getMethod("getSummoner")`. For the ~99% of mobs with no
such method this **constructs and throws a `NoSuchMethodException` every tick**. On a busy
world that is thousands of exception allocations per second.
*Fix:* a `ClassValue<MethodHandle>` cache resolved once per class (storing a null sentinel
for misses), plus an early `if (!MagicCastingCompat.available()) return;` guard, plus
raising the idle poll to 40 ticks. Also apply the same cache inside `ownerOf`.

**D-05 — companions are configured to run from creepers, by default, twice.**
`ModConfig.migrateAlertExclusions` force-adds `minecraft:creeper` to `ALERT_EXCLUDED_MOBS`
on first load, so `AlertGoal` refuses to target them; and `AvoidCreeperGoal` actively flees.
Both must go. See §10 for the replacement, and §16.3 for the one-time reverse migration
that removes the injected default without stomping a deliberate player re-add.

**D-09 — the root cause of all the teleporting.**
Vanilla `PathNavigation.createPath(Set, int, boolean, int)` uses
`mob.getAttributeValue(Attributes.FOLLOW_RANGE)` as the **search radius**, and
`PathNavigation`'s constructor sizes the node budget at `FOLLOW_RANGE × 16`. At
`FOLLOW_RANGE = 20` a companion physically cannot compute a path longer than ~20 blocks or
visit more than 320 nodes. Everything past that fails, and the only remaining tool is the
teleport in `CustomFollowOwnerGoal`. Fixing this is §5 and it is the highest-value work in
this document.

**D-21 — every Vanguard runs two melee goals.**
`Mob`'s constructor calls `registerGoals()` server-side; subclass constructors then add
more goals afterwards. `Knight` adds `MeleeAttackGoal(this, 1.0D, true)` at priority 2;
`Vanguard extends Knight` and adds `MeleeAttackGoal(this, 0.95D, true)` at priority 2 on top.
*Fix:* move all subclass goal registration into an overridden `registerGoals()` that calls
`super.registerGoals()` first, and give `AbstractHumanCompanionEntity` a
`protected void registerCombatGoals()` hook subclasses override instead.

**D-25 — verify before fixing, then fix.**
`Archer.readAdditionalSaveData` calls `setItemSlot(MAINHAND, EMPTY)` and then `checkBow()`.
`retainPreferredMainHand` (`:1454`) reads `getMainHandItem()`, which is now empty, so it
returns `EMPTY` and the method falls back to scanning `inventory`. A weapon that exists
**only** in the mainhand equipment slot — e.g. placed there through the GUI without a copy
in `inventory` — is destroyed on reload. Repro: give a companion a named/enchanted sword
directly into the hand slot, save, reload, check the item. Fix by reading the saved hand
stack *before* clearing, or by not clearing at all.

**D-08 — reproduce before you touch archer aggression.**
Do not guess. Build a dev world, spawn 6 archers + 20 hostiles, and run 10 minutes with
`-ea`. Hypotheses to instrument, in order of likelihood:
1. Re-entrancy: `performRangedAttack` → `addFreshEntity` during the entity-tick loop while
   `CompanionProtectionEvents.enforceSummonTarget` (`EntityTickEvent.Pre`) mutates targets.
2. `ProjectileUtil.getWeaponHoldingHand` returning `OFF_HAND` while `EatGoal` has a
   temporary offhand stack installed (`EatGoal.start` → `setTemporaryOffhandItem`).
3. Bow breaking inside `performRangedAttack` while `checkBow()` runs in the same tick.
4. `getOwner()` resolving across dimensions.
Capture the stack trace into `TRACELOG.md` before fixing.

---

## §2 — Target architecture

```
                       ┌──────────────────────────────┐
   player input ───────►   Command layer (§3, §4)      │  squads, orders, UI, keybinds
                       └───────────────┬──────────────┘
                                       │ SquadOrder
                       ┌───────────────▼──────────────┐
                       │   CompanionBrain (per mob)    │
                       │                               │
   world ──► Perception│  §6 senses ──► Blackboard      │
             (lossy)   │       │            │           │
                       │       ▼            ▼           │
                       │  §7 Threat ──► Plan selection  │
                       └───────────────┬──────────────┘
                                       │ intent
                       ┌───────────────▼──────────────┐
                       │  Goal adapters (vanilla Goals)│  §5 navigation, §8 tactics
                       └──────────────────────────────┘
```

Four rules for the implementer:

1. **`AbstractHumanCompanionEntity` gains exactly two fields**: `CompanionBrain brain` and
   `UUID squadId`. Everything else lives in new classes. Do not grow the god object (D-24).
2. **Goals are never added or removed after registration.** The brain enables/disables a
   fixed set. Dynamic add/remove is what causes D-01.
3. **The brain runs server-side only**, on a phase-offset tick (`entityId % interval`).
4. **Pure decision logic is separated from world access** so it can be tested headlessly.

---

## §3 — Squads: the command layer

This is the "interaction must be EASY" requirement. Design target: **re-task your entire
force in under two seconds without opening a screen.**

### 3.1 Data model

```java
public record Squad(
        UUID id,
        UUID ownerId,
        String name,              // "Alpha", renameable
        int colorIndex,           // 0-15, maps to DyeColor for chips/particles/HUD
        int iconIndex,            // banner-style glyph
        List<UUID> memberIds,     // ordered; index 0 is the squad lead
        SquadOrder order,         // current order, never null (defaults to FOLLOW_ME)
        Formation formation,
        EngagementPolicy policy   // free-fire / hold-fire / defensive
) {}
```

- Storage: `CompanionSquadData extends SavedData`, attached to the **overworld's**
  `DimensionDataStorage` so squads are global rather than per-dimension.
- The companion stores only `squadId` (synced `EntityDataAccessor<Optional<UUID>>` + NBT).
  Squad membership is authoritative in `CompanionSquadData`; the entity field is a cache
  that is reconciled on load and on squad edits.
- Default squads: on first recruit, auto-create **Alpha** and put the recruit in it.
  Config `squads.maxSquads` default 8, `squads.autoAssignToActive` default true.
- Deleting a squad moves its members to the lowest-numbered remaining squad, never orphans.

### 3.2 The active squad

A per-player, synced field (`SquadSelectionAttachment` via NeoForge data attachments, or a
simple server map keyed by player UUID + a sync packet). Everything in the command layer
acts on the **active squad** unless a bulk modifier is held.

### 3.3 Control surfaces — all four, because different moments want different tools

**A. RTS control groups (primary, fastest).**
While holding the Command Baton (§3.4) **or** with the "command mode" keybind held:

| Input | Effect |
|---|---|
| `1` … `8` | Select squad N as active |
| `Ctrl` + `1`…`8` | Move the companion you are looking at into squad N |
| `` ` `` (backquote) | Select **all** squads (bulk mode) |
| Mouse wheel | Cycle active squad |
| `Tab` | Cycle formation of the active squad |

This is the idiom every RTS player already knows. It is the single highest-leverage
interaction decision in this document.

**B. Command Baton item** (`modern_companions:command_baton`) — the in-world director.

| Input | Effect |
|---|---|
| Right-click ground | **`MOVE_TO`** — active squad walks there and holds. Beam marker + squad-colour particles at the destination. |
| Right-click hostile | `ATTACK` — active squad focus-fires it |
| Right-click companion | Add to active squad (or remove if already a member) |
| Sneak + right-click ground | `WARD_POINT` — active squad guards *here* (radius order, distinct from a zone) |
| Sneak + right-click air | Cycle order preset: Follow / Hold / Move / Ward / Work |
| Left-click air | `FALL_BACK` — active squad disengages to the owner |

`MOVE_TO` is the "direct them to a location and they go there" requirement, and it is
backed by the §5 navigation work — a squad ordered 300 blocks away **walks** the whole way.

**C. Radial menu** (hold `R`): 8 wedges — Follow, Move To, Hold, Ward, Attack My Target,
Fall Back, Work, Scout. Applies to the active squad, or to the looked-at companion's squad.
Wedges show the squad colour so you always know who you're commanding.

**D. Squad Command Screen** (`K`): the hub for setup rather than combat.
- Left rail: squad list — colour chip, icon, name, member count, aggregate health bar,
  current order, distance to owner. Click selects; double-click renames.
- Centre: roster grid of the selected squad — portrait, name, class, level, health, stance,
  job, zone. **Drag a portrait onto another squad's chip to reassign.**
- Right: order panel with large buttons + parameters (radius, formation, engagement policy).
- Bottom: bulk bar — *All Follow*, *All Hold*, *All Ward → [zone]*, *All Recall*.
- Filter/sort by class, level, health, squad, dimension.

### 3.4 Persistent squad HUD

Small configurable overlay (corner selectable, toggle key, off-switch). One row per squad:
colour chip · name · `4/4` alive · aggregate health bar · current order icon · distance.
Rows flash on: member below 30% health, member downed, order completed, squad blocked
(no route). This is how you know what's happening without opening anything.

### 3.5 Squad-level behaviour

- **Formations** (`Formation`): `LOOSE` (default, spread 3–5 blocks), `COLUMN` (single file,
  for caves/corridors), `WEDGE` (tanks forward), `LINE` (ranged abreast), `CIRCLE`
  (surround the owner — for sieges/hordes). Formation is advisory: it defines preferred
  offsets from the anchor, and the navigator gets a target *slot*, not a hard position.
- **Squad lead**: member index 0. Leads pace the squad (others match the slowest member so
  the squad arrives together), issue the single voice acknowledgement, and hold the squad's
  focus-fire claim.
- **Cohesion**: on `MOVE_TO`, if a member falls more than `squads.cohesionRange` (default 24)
  behind, the squad slows rather than fragmenting. If a member is stuck, the lead reports it
  by name and the squad continues after `squads.stragglerGraceTicks`.
- **Squad comms**: contacts are shared squad-wide instantly and cross-squad within 24 blocks
  (§6.4). One acknowledgement per order, from the lead only — never four "yes sir"s.

---

## §4 — Orders

```java
public sealed interface SquadOrder {
    record FollowOwner(int spacing)                          implements SquadOrder {}
    record MoveTo(ResourceKey<Level> dim, BlockPos pos,
                  int arriveRadius, boolean holdOnArrival)   implements SquadOrder {}
    record Hold(BlockPos anchor, int radius)                 implements SquadOrder {}
    record WardZone(UUID zoneId)                             implements SquadOrder {}
    record WardPoint(BlockPos centre, int radius)            implements SquadOrder {}
    record Attack(UUID targetId, boolean pursue)             implements SquadOrder {}
    record FallBack(BlockPos rally)                          implements SquadOrder {}
    record Scout(BlockPos pos, boolean returnAfter)          implements SquadOrder {}
    record Work()                                            implements SquadOrder {}
}
```

Contract for every order:

- **Issue** stamps `issuedTick` and (optionally) `expiryTick`.
- **Progress** is reported to the HUD: `MOVING` / `ARRIVED` / `ENGAGED` / `BLOCKED` / `DONE`.
- **Interruption** by combat suspends, never cancels. On combat end, resume the suspended
  order from its checkpoint. This mirrors the resumable-job contract in `TASK.md` and should
  reuse the same shape.
- **`BLOCKED`** is a first-class outcome: no route after 3 coarse attempts → report by name
  in the HUD and chat/bubble, hold position, retry every 200 ticks. Never silent, never spin.
- Orders are per-squad; an individual companion can be given a personal override that expires
  and falls back to the squad order.

### 4.1 Stances (per-companion, subordinate to orders)

Replace the three hand-synchronised booleans (`FOLLOWING`/`PATROLLING`/`GUARDING`) with one
synced enum `CompanionStance { ESCORT, WARD, WORK, HOLD }`. The squad order sets it; the
per-companion screen can override.

**Compatibility shim (do this — it keeps the diff small).** Keep `isFollowing()`,
`isPatrolling()`, `isGuarding()` as *derived* methods over the enum, and make the legacy
setters coerce it. Every existing call site — all five job goals, `DeliverToChestGoal`,
`PatrolGoal`, `MoveBackToPatrolGoal`, `MoveBackToGuardGoal`, `CustomFollowOwnerGoal`,
`CompanionJadeProvider`, both WTHIT providers, `CompanionScreen`, `ModNetwork` — compiles
and behaves unchanged. On load, if the `Stance` tag is absent, derive it from the legacy
booleans at `AbstractHumanCompanionEntity:2495-2497`.

---

## §5 — Navigation overhaul

**Goal: they walk everywhere, reliably, and you almost never see a teleport.**

### 5.1 Layer 1 — lift the budget (fixes D-09)

In `CompanionGroundPathNavigation`:

```java
// Vanilla sizes both the search radius and the node budget from FOLLOW_RANGE.
// Companions need a long walking range without a matching aggro range, so the
// navigator uses its own radius and leaves FOLLOW_RANGE to targeting.
@Override
protected Path createPath(Set<BlockPos> targets, int regionOffset,
                          boolean offsetUpward, int accuracy) {
    // Copy of the vanilla body, substituting navigationRange for FOLLOW_RANGE.
}
```

- `navigationRange` from config `nav.searchRange`, default **64** (range 16–128).
- `setMaxVisitedNodesMultiplier(config nav.nodeBudgetMultiplier)`, default **3.0**
  (vanilla 1.0). Node budget becomes `64 × 16 × 3 ≈ 3072`.
- Leave `Attributes.FOLLOW_RANGE = 20` alone — targeting range is owned by §6, not by this.
- Cost control: §5.6 throttling plus the §17 budget. Measure before and after.

### 5.2 Layer 2 — long-range route planning

For destinations beyond `nav.searchRange` (a `MOVE_TO` 300 blocks out, a `RECALL` from
another biome), fine A* will never succeed. Add `CompanionRoutePlanner`.

- **Coarse graph**: 8×8×8 cells. For each cell, probe a walkable surface height with a
  bounded column scan. Cache in a per-level LRU (`nav.routeCacheSize`, default 4096),
  invalidated on `BlockEvent.BreakEvent` / `EntityPlaceEvent` touching the cell.
- **Search**: A* over cells, octile heuristic, penalising cells that contain hazards or are
  unloaded. Runs on the server thread with a **resumable per-tick node budget**
  (`nav.coarseNodesPerTick`, default 200) via a `RouteRequest` queue. It must never block
  a tick, and it must survive being spread over many ticks.
- **Execution**: the resulting waypoint corridor is consumed one waypoint at a time by the
  normal fine navigator. Recompute the corridor only on failure or a large destination move.
- **Fallback ladder** when no coarse route exists:
  1. Path to the furthest reachable waypoint and re-plan from there ("make progress").
  2. If still stuck, walk the straight-line bearing with local avoidance for 100 ticks.
  3. If still stuck, report `BLOCKED` and hold. (Teleport only per §5.5.)

### 5.3 Layer 3 — node evaluation (fixes D-10, D-11)

Extend `CompanionWalkNodeEvaluator`:

| Block / condition | Treatment |
|---|---|
| Lava, fire, campfire, soul campfire, magma block | `BLOCKED` |
| Cactus, sweet berry bush, wither rose, powder snow | `DANGER_OTHER`, high malus |
| Pointed dripstone (existing) | `BLOCKED` |
| Any cell **adjacent to or above** lava | high malus (don't skirt lava lakes) |
| Drop > 3 blocks, when `FALL_DAMAGE` is on | malus scaled by drop height |
| `DIRT_PATH`, slabs, stairs | small malus **reduction** — prefer roads |
| Ladders, vines, scaffolding | traversable, so they can climb out of caves |
| Doors / fence gates | already enabled via `setCanPassDoors(true)`; keep |
| Water | `WATER` malus **8** while wearing ≥3 armour pieces, `0` otherwise; `WATER_BORDER` 0 |

Replaces the blanket `setPathfindingMalus(PathType.WATER, 0.0F)` at `:343-344`, which is
why armoured companions currently walk into lakes.

### 5.4 Layer 4 — movement quality

- **Path smoothing (string-pulling).** After a path is produced, walk the node list and drop
  intermediate nodes when a direct traversability trace between node `i` and node `i+k`
  succeeds. Cuts the vanilla zig-zag and is the single biggest visual improvement.
- **Step height.** Set `Attributes.STEP_HEIGHT` to `nav.stepHeight`, default **1.0**
  (players are 0.6 + autojump). One-block steps stop being jump-stall points. Not a combat
  stat; safe under §17.3.
- **Stuck detection.** Track displacement over a 40-tick window against expected path
  progress. Escalate: repath → jump/strafe nudge → coarse reroute → report `BLOCKED` + hold.
  Never spin silently.
- **Repath throttling (fixes D-12).** Recompute at most every `nav.repathInterval` ticks
  (default 10), or when the target moves > 3 blocks, or when the nav completes. Rewrite
  `MoveBackToGuardGoal.tick` and `MoveBackToPatrolGoal.tick` to obey this.
- **Hysteresis (fixes D-13, D-14).** Guard/patrol/follow goals get separate enter and exit
  radii (exit = enter × 0.6) so they stop oscillating at the boundary; and
  `CustomFollowOwnerGoal.canContinueToUse` must stop requiring `!nav.isDone()`.
- **Catch-up sprint.** Out of combat and more than 24 blocks behind the owner, allow a
  temporary +30% movement modifier that decays on arrival. This is what replaces teleporting:
  they *run* to you. Cap via `nav.catchUpMultiplier`.
- **Door etiquette.** Close doors behind them while in `WARD`.

### 5.5 Layer 5 — teleport policy (fixes D-15)

New config `nav.teleportPolicy`:

| Value | Behaviour |
|---|---|
| `NEVER` | No leash teleport ever. They walk or they report `BLOCKED`. |
| `LAST_RESORT` *(default)* | Teleport only when **all** of: distance > 64; no route for > 300 ticks; out of combat; **outside the owner's view frustum**; 1200-tick per-companion cooldown. |
| `LEGACY` | Current behaviour, for anyone who wants it. |

The view-frustum condition is the important one: a teleport you never *see* does not break
immersion. Implement as a dot-product test against the owner's look vector plus an
occlusion check, with a hard "never within 32 blocks in front of the owner" rule.

Also in scope:
- `SummoningWandItem` keeps teleporting — it is an explicit magic item, that's fine. Add a
  particle/sound arrival so it reads as deliberate.
- Dimension transfer (`CompanionEvents`) keeps teleporting — unavoidable — but should place
  companions at the portal and let them walk in, not snap them onto the player.
- Remove the teleport path from `CustomFollowOwnerGoal` entirely; it now delegates to the
  policy above.

### 5.6 Cost control

- Path requests go through a per-level `NavigationScheduler` with a global cap
  (`nav.maxPathsPerTick`, default 8). Requests are prioritised: combat > order > idle.
- Idle companions repath at 20-tick intervals, engaged at 10, never every tick.
- Fine paths are cached for 40 ticks keyed by (start cell, goal cell).

---

## §6 — Perception: what they are allowed to know

This is the anti-omniscience valve. It replaces `AlertGoal`/`HuntGoal`'s raw
`NearestAttackableTargetGoal` selection (D-06).

### 6.1 Sight

Base range = `perception.sightRange` (default 24), then modulated multiplicatively:

| Factor | Multiplier |
|---|---|
| Outside a 120° forward cone (and > 6 blocks away) | 0 — not seen |
| No `hasLineOfSight` | 0 |
| Target light level < 5 and not burning/glowing/holding a light | × 0.4 |
| Rain | × 0.7 · Thunder × 0.5 |
| Target `isCrouching()` | × 0.5 |
| Intelligence | × `1 + (INT − 4) × 0.02`, clamped ±20% |

Sneaking working on companions is deliberate — it is what keeps them fair.

### 6.2 Hearing

Radius `perception.hearingRange` (default 12), no LOS required. Produces an
`AudioCue(BlockPos pos, float loudness, long tick)` — **a position, not an entity**.
Sources: sprinting/non-sneaking movement, block break/place, explosions, mob ambient sounds,
door use, projectile impacts. An `AudioCue` can only seed an `Investigate` plan. It can
never produce an attack target directly.

### 6.3 Memory and decay

```java
public record PerceivedContact(
        UUID entityId, EntityType<?> type,
        Vec3 lastSeenPos, Vec3 lastSeenVelocity,
        long firstSeenTick, long lastSeenTick,
        float confidence,          // 1.0 fresh, decays after LOS loss
        ContactSource source       // SIGHT, HEARING, RELAYED
) {}
```

Confidence decays to 0 over `perception.memoryTicks` (default 200) after LOS loss. A
companion pursuing a lost contact searches the **extrapolated** position, checks the obvious
occluder, then gives up and reports. No snap-back tracking, ever.

### 6.4 Squad comms

Contacts propagate:
- Instantly to every member of the same squad (that is what a squad *is*).
- To other squads of the same owner within 24 blocks.
- To the owner (HUD ping + one voice line from the nearest member).

Relayed contacts arrive as `ContactSource.RELAYED` with confidence × 0.8 and a 1–2 tick
delay. This is how the perimeter works (§11) and it is earned — somebody actually saw it.

### 6.5 Budget

One sweep every `perception.sweepInterval` ticks (default 10), phase-offset by
`entityId % interval`. One `getEntitiesOfClass` over an AABB capped at `sightRange`. Contact
list capped at `perception.maxContacts` (default 16), evicting lowest threat. No chunk
scanning, no world-wide queries — which also means **fixing D-04** by replacing
`CompanionVoice.playEnemySpotted`'s full-level scan with the squad roster we now maintain.

---

## §7 — Threat assessment and target selection

`ThreatAssessment` — pure, headless-testable, dependency-free (the `AlertTargetRules` shape).

```
score(contact) =
    w_prox     · proximityTerm(distance, myRole)
  + w_owner    · (isAttackingOwner ? 1 : 0)
  + w_self     · (isAttackingMe ? 1 : 0)
  + w_dps      · estimatedDps(type, equipment)
  + w_wounded  · (1 − targetHealthFraction)
  + w_imminent · imminenceTerm(creeperFuse, drawnBow, windup)
  + w_zone     · zoneBreachDepth(zone, pos)
  + w_squad    · squadFocusBonus(contact)
  − w_engaged  · alliesAlreadyEngaged(contact)
  − w_counter  · (counteredByMe ? 1 : 0)
```

- All weights live in config `ai.threatWeights` — tunable without a rebuild.
- `estimatedDps` reads `data/modern_companions/ai/threat_profiles.json` (datapack-overridable)
  with a category-based fallback, so modded mobs work without code. Same fix applies to the
  hunt list (D-26): entity **tags** plus datapack JSON, not a hardcoded six.
- **Focus fire** with a claim cap of `ceil(squadSize / 2)` so a squad never all-piles one
  skeleton while something else walks in.
- Anything attacking the **owner** outranks the focus target for the two nearest members.
- `AlertGoal` and `HuntGoal` are rewritten to select from the contact list via this scorer.
  Keep the `alert_unsafe` tag and the config exclusion list — they still apply as filters.

---

## §8 — Combat tactics

Every item is a decision improvement. None of them adds a stat (§17.3).

### 8.1 Melee

- Strafe-approach ranged attackers instead of walking a straight line into arrows; break LOS
  behind cover where a cheap trace finds it.
- Shield timing: raise on incoming projectile or target wind-up, drop to swing.
  `Vanguard` already has the parts (`:60-120`) — generalise to anyone holding a shield.
- **Never body-block the owner.** Soft repulsion out of the owner's forward vector within
  2 blocks. This is the most-hated companion-mod behaviour and it is cheap to fix.
- Target-switch commit window (~20 ticks) so they don't jitter between two mobs.
- Never drop > 3 blocks in pursuit; never enter lava/fire/powder snow (enforced by §5.3).

### 8.2 Ranged

- Preferred band 8–16 blocks; back-pedal inside 4.
- **Fire discipline (fixes D-07).** `FireLineRules.isLaneClear(shooter, target, allies)` —
  point-to-segment distance from the shot line to every ally and the owner; no shot if any
  is within `combat.fireLaneClearance` (default 1.2 blocks). Applies to `Archer`,
  `Arbalist`, `Beastmaster`, `FirearmAttackGoal`, and mage projectiles.
- Lead moving targets from `lastSeenVelocity`, with the §17.3 aim error so it is good, not
  perfect.
- Reposition for LOS instead of plinking a wall.
- Ammo/durability awareness: report and fall back to melee at zero.

### 8.3 Magic

- Never AoE with the owner inside the radius.
- Spend mana on utility when no offensive line exists.
- Cleric/Druid triage by ally health fraction, not proximity.
- Fix D-27: one movement decision per tick, not two competing `moveTo` calls.

### 8.4 Squad tactics

- Focus fire and peeling (§7).
- Formation-aware spacing so ranged aren't behind a wall of tanks.
- Crowd control: at 5+ hostiles, fall back to the nearest chokepoint and hold it.
- Flanking: when 2+ melee engage one target, the second circles rather than stacking.

---

## §9 — Survival: not dying, without cowardice

Both halves of this are requirements. The design is a **Resolve** model.

### 9.1 Resolve

`resolve ∈ [0,1]`, recomputed every brain tick from: bond level, morale (existing system),
health fraction, ally proximity, owner proximity, whether the owner is engaged, and traits
(`trait_brave`, `trait_cautious`, `trait_reckless`, `trait_stalwart` already exist).

### 9.2 The anti-cowardice guarantees — implement these as hard rules, and test them

1. **They never disengage while the owner is in combat within 12 blocks.** No exceptions,
   at any health. They will die for you.
2. **They never turn their back and sprint away.** Withdrawal is *backwards, facing the
   enemy*, shield up if held. Fleeing animation-wise is what reads as cowardice.
3. **They never withdraw from a creeper** (§10) — they space and re-engage.
4. **Cornered with no ally and no route = fight to the death**, with a last-stand voice line.
5. **Owner downed or killed → `AVENGE`**: resolve pinned to 1.0, withdrawal disabled for
   600 ticks, focus everything that damaged the owner.

### 9.3 The not-dying half

- **Fighting withdrawal** below `combat.withdrawHealthFraction` (default 0.30) *and* rule 1
  not in force: back off 6–10 blocks to behind an ally or toward the owner, heal
  (`EatGoal` + `consumeUsefulCompanionPotion`, both exist), then re-engage. Currently they
  eat mid-swing and die — fixing D-18/D-19 is a prerequisite.
- **Second Wind**: once per combat, below 15% health, 40 ticks of −30% incoming damage
  *while withdrawing only*. Not a damage buff; it exists so a withdrawal isn't a death
  sentence. Config-disableable.
- **Hazard reflex** outranks everything: on fire, in lava, drowning, falling → escape plan
  first. Existing `boostWaterMovement` / `tickCommittedSwim` feed this.
- **Ally rescue**: a companion below 20% within 8 blocks broadcasts distress; the nearest
  healer-capable ally (Cleric/Alchemist/Druid all exist) claims it, others cover.
- `combat.survivalProfile`: `RECKLESS` (never withdraw) / `DISCIPLINED` (default) /
  `CAUTIOUS` (withdraw at 45%).

---

## §10 — Creepers, specifically

They must attack creepers. Currently they are configured *twice* not to (D-05).

### 10.1 Remove the blocks

1. Delete `minecraft:creeper` from `AlertExclusionDefaults`; keep the class for the
   migration helper only.
2. Add a one-time reverse migration (§16.3) removing the previously injected default.
3. Delete `AvoidCreeperGoal` and its registration at `AbstractHumanCompanionEntity:461`
   (which also clears D-20). Retire `ModConfig.CREEPER_WARNING`'s avoidance meaning; keep
   it as "call out creepers on sight".

### 10.2 `CreeperEngagementPlan`

| Role | Behaviour |
|---|---|
| Ranged (Archer, Arbalist, Beastmaster, firearms, mages) | Creeper gets a large `w_imminent` bonus — kill it at range. Highest-priority target when it is closing on the owner. |
| Melee | **Hit-and-space**: close, strike, and step outside the blast radius while the fuse is swelling (`Creeper#getSwellDir()`, swell ticks), then re-enter. This is how a competent player fights a creeper. Never flee, never trade into the detonation. |
| Shield bearer (Vanguard) | May tank the blast with the shield raised, if health > 60%. |
| Charged creeper | Treat as double blast radius; ranged only; melee holds. |

### 10.3 Owner protection

- A creeper is **never** allowed within 4 blocks of the owner: nearest companion intercepts
  on the approach vector, ahead of any other order.
- A creeper swelling inside blast range of the owner → nearest companion **body-blocks**
  between owner and creeper. Heroic, and consistent with §9.2.
- `ai.creeperPolicy`: `ENGAGE` (default) / `RANGED_ONLY` / `AVOID` (legacy behaviour).

---

## §11 — Perimeter zones

### 11.1 The Perimeter Rod

`modern_companions:perimeter_rod` — stick-like, `stacksTo(1)`, no durability.

| Input | Effect |
|---|---|
| Right-click block (no pending corner) | Set **corner A**; persistent particle marker |
| Right-click block (A pending) | Set **corner B** → create + select the zone |
| Sneak + right-click block | Restart from a fresh corner A |
| Right-click air | Cycle selected zone in this dimension |
| Sneak + right-click air | Toggle mode Define ⇄ Assign |
| Right-click companion (Assign) | Assign to the selected zone, stance `WARD` |
| Right-click companion + `Ctrl` (Assign) | Assign that companion's whole **squad** to the zone |

**Y handling.** Two corners give X/Z. The zone auto-expands from the lower corner
`−zones.depthBelow` (default 12) to the higher corner `+zones.heightAbove` (default 24), so
tunnelling and flying approaches are both covered. Sneak-clicking corner B locks exact Y.
Both editable per zone.

### 11.2 Storage

`PerimeterZoneData extends SavedData`, per `ServerLevel` (zones are correctly per-dimension).

```java
record PerimeterZone(UUID id, UUID owner, String name,
                     BlockPos cornerA, BlockPos cornerB,
                     int depthBelow, int heightAbove,
                     ZonePolicy policy) {
    AABB box(); boolean contains(Vec3); double distanceToEdge(Vec3);
    List<BlockPos> boundarySamples(int spacing);
}
```

`ZonePolicy`: `hostilesOnly` / `alsoNeutrals` / `alsoPlayers` (default off), entity-id and
player-UUID allow-lists, `lethalForce`.

Zones live in the level, not on entities: many companions share one, and it survives their
deaths. The existing circular `PATROL_POS` + `getPatrolRadius` stays for `WORK` (all five job
goals depend on it) — two systems, cleanly separated, no job breakage.

### 11.3 Making the perimeter actually hold

- **Sensor coverage, not clustering.** N warded companions are placed by greedy
  farthest-point selection over `boundarySamples()` so their sight cones maximise covered
  perimeter. Recomputed on roster/zone change only.
- **Phase-offset boundary patrol** so swept coverage over time far exceeds instantaneous
  coverage.
- **Home-field advantage.** Inside their own zone: hearing × 1.5, plus a low-confidence
  directional "something's wrong" cue when a hostile is in the zone but unseen. A **hint
  that seeds `Investigate`**, never a target lock. This is the fair version of "nothing gets
  past them".
- **Breach ledger.** Cheap AABB sweep every 20 ticks (entity-count capped, skipped when
  chunks are unloaded) records hostiles inside the zone. A breach only becomes actionable
  once perceived; if it persists unperceived for `zones.alertSeconds` (default 15) **you get
  a HUD warning and an audible cue**. That is the honest contract — near-total coverage, and
  when they genuinely can't see something, you are told rather than quietly losing a base.
- **Intercept, don't chase.** Move to cut the intruder off from the zone centre. Hard leash:
  never more than 6 blocks past the zone edge. Guards being kited out is how bases die.
- **Dark-cell reporting** inside the zone; optional `zones.autoLighting` (default **off**,
  because a companion silently editing your base should be opt-in).
- **Chunk reality.** Unloaded chunks mean nothing is happening — no mobs, no guards, no
  breaches. Optional `zones.chunkload` (default off) reuses the existing chunk-ticket pattern
  from `JOB_ASSIGNED_CHESTS_CHUNKLOAD`.

---

## §12 — Recruitment and the starter companion

### 12.1 Delete taming

- Remove the entire `!isTame()` branch at `AbstractHumanCompanionEntity:2188-2245`. First
  main-hand interact recruits: `tame(player)`, `Cue.GREETING`, `FIRST_TAMED_TIME`, recruit
  defaults, auto-join the active squad.
- **Keep the `FOOD1`/`FOOD2` synced fields** — the Jade plugin, both WTHIT providers and the
  Journal screen read them. Repurpose as favourite-food display. This keeps four compat
  classes untouched.
- Remove `getRandomFoodRequirement` / `assignFoodRequirements` / `syncFoodRequirements`;
  keep `assignFavoriteFood`.
- `ModConfig`: rename `taming` → `recruitment`. Keep `allFoods` and `extraHealConsumables`
  (healing depends on them). Retire the three resource-item lists, but leave the keys defined
  and unused for one version so existing configs don't fail validation, then drop them.
- Add `recruitment.mode` = `INSTANT` (default) | `HANDSHAKE` (sneak + interact).
- Repoint dialogue to a new `RECRUIT_GREETING` pool; `notTamed` / `tameFail` / `WRONG_FOOD`
  / `ENOUGH_FOOD` become unused.
- No save migration needed: existing untamed companions simply become recruitable.

### 12.2 Starter companion

`StarterCompanionEvents` on `PlayerEvent.PlayerLoggedInEvent`, guarded by a once-per-player
flag in `player.getPersistentData()`:

- Class from `starter.companionClass`, default **Vanguard** — the only archetype that
  already implements shield use, taunt, projectile soak and a defence aura, i.e. the best
  hour-zero survivability with zero added damage.
- Force-tamed, named, squad **Alpha**, stance `ESCORT`, alert on.
- **Gear bypasses `CompanionData.getSpawnArmor`'s RNG entirely** (it rolls 40% *nothing*):
  full iron armour + iron sword + shield. `starter.gear` = `IRON` (default) | `LEATHER` | `NONE`.
- Also grants the **Perimeter Rod** and **Command Baton**, so the whole loop is discoverable.
- Spawn placement via a shared `SafeSpawnUtil` extracted from
  `SummoningWandItem.findSafeSpot` — extract, don't copy.
- `/companion starter` (self, once) for existing worlds; `/companion starter force` (perm 2).

---

## §13 — Ambition: features that make this a full companion addon

Ordered by value-to-effort. Everything here is post-phase-8 unless noted.

1. **Chat bubbles** (`Ideas.md`) — overhead speech with per-companion cooldown; `bubbles` /
   `chat` / `both`. Prerequisite for everything below, because squad comms will otherwise
   flood chat.
2. **Companion-to-companion dialogue** (`Ideas.md`) — callouts, mourning a squadmate's
   death, trash talk, asking each other for food, sharing food. Squad comms already carries
   the events; this is presentation.
3. **Contextual barks** — biome/structure lines, class-vs-mob lines (Cleric vs undead,
   Beastmaster near wolves), hazard warnings ("ravine edge", "deep dark"), weather reactions.
4. **Squad identity** — name, colour, banner glyph, and a rendered squad pennant on members.
5. **Veterancy** — squads that fight together accrue a small shared cohesion stat that
   improves *coordination latency only* (§17.3 forbids stat gains).
6. **Barracks / rally banner** (`Wanted Features`) — a block that acts as a squad home and
   rally point; off-duty companions return there. Slots naturally into zones.
7. **Expeditions** — send a squad to a coordinate, they walk there, report, and return.
   Trivial once §5 works; it is just a long `Scout` order.
8. **Personal quests, professions, skill trees, titles, elite variants** — the long tail from
   `Wanted Features.md`. Out of scope here; do not start them before phase 10.

---

## §14 — Config surface (complete)

New sections. All keys use the existing `builder.translation(...)` + `comment(...)` pattern.

```
[squads]        maxSquads=8  autoAssignToActive=true  cohesionRange=24
                stragglerGraceTicks=200  hudEnabled=true  hudCorner=TOP_LEFT

[nav]           searchRange=64  nodeBudgetMultiplier=3.0  stepHeight=1.0
                repathInterval=10  maxPathsPerTick=8  coarseNodesPerTick=200
                routeCacheSize=4096  catchUpMultiplier=1.30
                teleportPolicy=LAST_RESORT  teleportMinDistance=64
                teleportNoRouteTicks=300  teleportCooldownTicks=1200

[perception]    sightRange=24  hearingRange=12  memoryTicks=200
                sweepInterval=10  maxContacts=16  relayRange=24

[ai]            profile=VETERAN  brainTickInterval=10  maxActiveCombatants=4
                creeperPolicy=ENGAGE  threatWeights={...}

[combat]        withdrawHealthFraction=0.30  survivalProfile=DISCIPLINED
                secondWindEnabled=true  fireLaneClearance=1.2
                targetCommitTicks=20

[zones]         depthBelow=12  heightAbove=24  alertSeconds=15
                autoLighting=false  chunkload=false  maxZones=16

[recruitment]   mode=INSTANT
[starter]       enabled=true  companionClass=VANGUARD  gear=IRON  grantTools=true
```

**`ai.profile`** is the single balance knob. It scales **only** reaction latency, perception
ranges, and aim error. It must never touch damage, health, armour, or speed.

---

## §15 — Networking

New payloads, all following the `ToggleFlagPayload` shape, registered in
`ModNetwork#register`, and **all re-validating `companion.isOwnedBy(serverPlayer)` and squad
ownership server-side** exactly as the current handlers do.

| Payload | Direction | Purpose |
|---|---|---|
| `SetActiveSquadPayload` | C→S | Control-group selection |
| `SquadOrderPayload` | C→S | Issue an order (carries the sealed `SquadOrder`) |
| `SquadMembershipPayload` | C→S | Add/remove/move a companion between squads |
| `SquadEditPayload` | C→S | Rename, recolour, set formation/policy |
| `AssignZonePayload` | C→S | Companion or squad → zone |
| `SetCompanionStancePayload` | C→S | Per-companion stance override |
| `SquadSnapshotPayload` | S→C | HUD state: per-squad health, order, progress, distance |
| `ZoneSyncPayload` | S→C | Zone list + geometry for the holder of a Perimeter Rod |
| `BreachAlertPayload` | S→C | §11.3 unperceived-breach warning |

Rate-limit `SquadOrderPayload` server-side (max ~5/sec/player) so a stuck key can't be used
to spam path requests.

---

## §16 — Persistence and migration

1. **Stance.** Write `Stance` (string). On read, if absent, derive from legacy
   `Following`/`Patrolling`/`Guarding` (`:2495-2497`). One-way, no data loss.
2. **Squads.** `CompanionSquadData` on the overworld. On first load after upgrade, create
   squad **Alpha** per owner and enrol every existing tamed companion. Reconcile the
   entity-side `squadId` cache on load; a companion whose squad no longer exists rejoins the
   owner's lowest-numbered squad.
3. **Creeper exclusion (D-05).** Add `creeperExclusionRemovedMigrated`. On first load, if
   `creeperDefaultMigrated` is true and `creeperExclusionRemovedMigrated` is false, remove
   `minecraft:creeper` from `ALERT_EXCLUDED_MOBS` and set the new marker. This undoes the
   injected default exactly once and never touches a deliberate re-add. While fixing this,
   also fix D-22: perform config writes on the next tick rather than re-entrantly inside the
   `ModConfigEvent.Loading` handler.
4. **Zones** are new; nothing to migrate.
5. **Retired config keys** stay defined-but-unused for one version, then are removed.

---

## §17 — Performance budget

### 17.1 Targets

| Metric | Budget |
|---|---|
| Brain tick, per companion | < 0.15 ms |
| Perception sweep, per companion | < 0.10 ms, once per 10 ticks |
| Fine path requests | ≤ 8 / tick / level, globally scheduled |
| Coarse route search | ≤ 200 cells / tick, resumable, never blocking |
| Zone breach sweep | 1 AABB query / zone / 20 ticks |
| 4-companion squad, total | < 1 ms / tick |

### 17.2 Regressions to fix first

D-03 (reflection storm), D-04 (full-level scan), D-12 (repath storm) all land in phase 0.
Profile before and after with a 200-mob dev world and record numbers in `TRACELOG.md`.

### 17.3 The "not OP" audit list

Before each commit, confirm the diff adds **no** modifier to: `ATTACK_DAMAGE`, `MAX_HEALTH`,
`ARMOR`, `ARMOR_TOUGHNESS`, `ATTACK_SPEED`, `MOVEMENT_SPEED` (the §5.4 catch-up sprint is the
single audited exception: out-of-combat only, decays on arrival), `KNOCKBACK_RESISTANCE`.
Permitted knobs are only: reaction latency, perception range, aim error, decision quality,
and `STEP_HEIGHT`.

Also keep in place, and verify by test:
- Reaction latency `8 − (INT − 4) × 0.4` ticks, clamped `[3, 10]`.
- Aim error floor `max(0.6°, base − DEX·k)`, growing with distance and target speed.
- Surprise penalty: ~20 ticks of degraded accuracy and reaction after damage from an
  unperceived direction.
- `ai.maxActiveCombatants` = 4; extras hold position.

---

## §18 — Compatibility

| Integration | Requirement |
|---|---|
| **Epic Fight** | `CompanionEpicFightPatch` rewrites the goal list — that is why `CompanionEpicFightPatchTest` exists. Register brain goals so the patch preserves them, and **extend that test** to cover them. |
| **TacZ firearms** | `FirearmAttackGoal` and `FirearmSupport` must adopt §8.2 fire discipline and the brain gate. `TacZEpicFightCompanionModelMixin` untouched. |
| **Curios** | Untouched. |
| **Jade / WTHIT** | Both read the legacy booleans and `FOOD1/FOOD2` — preserved by the §4.1 and §12.1 shims. Add stance/squad/zone rows to the tooltips. |
| **Sophisticated Backpacks** | Untouched. |
| **JEI** | Untouched. |
| **Magic mods** (`MagicCastingCompat`) | Mage goals stay; brain gates them. Fix D-27 while there. |
| **Jobs** | §22. |

---

## §19 — Testing

Pure rules + `JavaExec` tasks wired into `check`, matching the existing convention:

| Test | Covers |
|---|---|
| `PerceptionRulesTest` | FOV, LOS, light/weather/sneak/INT modifiers, decay curve, relay confidence |
| `ThreatAssessmentTest` | Ordering; owner-attacker beats a nearer non-attacker; creeper imminence; ally-engaged discount |
| `FireLineRulesTest` | No-fire with an ally in the corridor; fire when clear; edge cases at exactly the clearance |
| `SquadOrderRulesTest` | Order state machine: issue → move → arrive → interrupt → resume → complete |
| `SquadMembershipTest` | Add/remove/move/delete; no orphans; lead reassignment |
| `NavigationBudgetTest` | Throttling, repath interval, stuck escalation ladder |
| `RoutePlannerTest` | Coarse A* on a synthetic grid: correctness, resumability, budget respect |
| `TeleportPolicyTest` | `LAST_RESORT` conditions — all five must hold; view-frustum rejection |
| `ResolveRulesTest` | The five §9.2 guarantees, each as a named assertion |
| `CreeperEngagementTest` | Role-appropriate response; body-block trigger; charged handling |
| `ZoneGeometryTest` | Corner normalisation, Y expansion, contains/edge distance, boundary sampling |
| `ZoneCoverageRulesTest` | N sentries → distributed, deterministic posts |
| `StanceMigrationTest` | Every legacy boolean combination → correct stance |
| `FormationRulesTest` | No companion placed on the owner's forward vector |
| `CompanionEpicFightPatchTest` *(extend)* | Brain goals survive the Epic Fight patch |

Plus in-world verification of every row of §21, recorded in `TRACELOG.md`.

---

## §20 — Phases and acceptance criteria

One commit per phase. Each needs a green build, log entries, and a version bump.

| # | Phase | Acceptance criteria |
|---|---|---|
| **0** | **Repairs** — D-01, D-02, D-03, D-04, D-12, D-16, D-17, D-18, D-19, D-20, D-21, D-22, D-25, D-27, D-30 | Each companion has exactly one of each goal (assert in a dev command). Reflection-storm profile shows the D-03 hot path gone. A companion hit by its owner does not retaliate. Vanguard has one melee goal. |
| **1** | **Navigation core** — §5.1, §5.3, §5.4 | A companion paths 64 blocks around a wall with no teleport. Armoured companions route around water. Nothing walks into lava. Zig-zag visibly reduced. |
| **2** | **Long-range routing** — §5.2, §5.5, §5.6 | `MOVE_TO` 300 blocks across varied terrain completes on foot. `teleportPolicy=NEVER` never teleports. `LAST_RESORT` never fires within the owner's view. |
| **3** | **Taming removal + starter** — §12 | First interact recruits. New world → Vanguard in full iron + both tools. Jade/WTHIT/Journal unbroken. |
| **4** | **Stances + squads data model** — §4.1, §3.1, §3.2, §16.1, §16.2 | Legacy saves migrate; all job goals still work; squads persist across restart. |
| **5** | **Command layer** — §3.3, §3.4, §4, §15 | Control groups, baton, radial, screen, HUD all functional. Re-tasking the whole force takes under two seconds without opening a screen. |
| **6** | **Perception + blackboard** — §6, and D-06, D-26 | Sneaking past a companion works. They search the last known position, not the live one. Squad-shared contacts verified. No entity is acted on that isn't in a contact list. |
| **7** | **Threat + focus fire** — §7 | A squad focuses correctly, caps claimants, and always peels to the owner. |
| **8** | **Combat tactics + creepers + survival** — §8, §9, §10, and D-07, D-08 | Companions kill creepers without dying to them. Fire discipline verified with the owner directly on the shot line. All five §9.2 guarantees hold. Archer crash reproduced and fixed. |
| **9** | **Zones + ward behaviour** — §11 | Two-corner zone; sentries distribute; intruder intercepted before the centre; unperceived breach raises the warning; guards cannot be kited out. |
| **10** | **Ambition pass** — §13 items 1–5 | Bubbles, squad chatter, squad identity. |
| **11** | **Tuning + profiling** — §17 | Budgets met with 8 companions and 200 mobs. `ai.profile` demonstrably changes only latency/senses/accuracy. |

Usable after phase 5. The AI is real from phase 8. The perimeter promise lands at 9.

---

## §21 — Situation matrix

**Owner-centred (ESCORT / FOLLOW_ME)**

| Situation | Response |
|---|---|
| Owner attacked in melee | Two nearest peel and engage; tank taunts; the rest hold focus |
| Owner attacked at range | Nearest ranged counter-fires; nearest tank interposes on the shot line |
| Owner below 40% | Healer claims; squad tightens and pulls aggro |
| Owner dies | Kill what's present, hold the death position as recovery escort until the owner returns or 5 min |
| Owner sprints away | Follow at spacing; catch-up sprint if >24 behind; abort any pursuit beyond owner + 8 |
| Owner sneaking | Reduce chatter, hold fire on unaware targets, tighten spacing |
| Owner mining/building | Widen the ring; never stand in a doorway or on the owner's forward vector |
| Owner in water/boat | Swim-capable follow; no deep water in heavy armour; wait on shore and re-link |
| Owner changes dimension | Existing transfer handles it; place at the portal and walk in; squad and stance preserved, zone stays behind |
| Owner AFK | Drop to a `HOLD` ring after `afkSeconds`; resume on movement |
| Owner attacks a villager/animal | Do **not** auto-assist on non-hostiles unless Hunt is on |

**Squad orders**

| Situation | Response |
|---|---|
| `MOVE_TO` 300 blocks | Coarse route → waypoint corridor → walk it; squad paces to the slowest; arrival report |
| `MOVE_TO` unreachable | 3 coarse attempts → `BLOCKED` in HUD, named straggler, hold, retry every 200 ticks |
| Ambushed en route | Suspend order, fight, resume from checkpoint |
| One member stuck | Squad waits `stragglerGraceTicks`, then continues; stuck member reports and keeps trying |
| `ATTACK` on a dead target | Order completes; revert to previous order |
| `FALL_BACK` | Withdraw facing the enemy (§9.2 rule 2) to the rally point |
| `SCOUT` | Travel, sweep perception, report contacts, return |
| Squad split across dimensions | Each subset acts on the order locally; HUD shows the split |

**Zone-centred (WARD)**

| Situation | Response |
|---|---|
| Hostile crosses the boundary | Nearest sentry with LOS engages and broadcasts; the adjacent sentry shifts to cover the gap |
| Hostile inside, unseen | Home-field hint → `Investigate` → engage on visual; §11.3 timer if it persists |
| Mob spawns inside a dark cell | Same as a breach, plus a dark-cell report |
| Tunnelling in from below | Y-depth covers it; hearing catches the block breaks |
| Flying in above | Height extent covers detection; ranged sentry claims |
| Creeper approaching | Ranged intercepts outside the wall; melee spaces; never allowed within 4 of the owner |
| Enderman | Do not trigger eye contact unless it is already aggressive |
| Skeleton plinking from outside | Engage only if it has LOS into the zone; otherwise hold — never get baited out |
| 5+ hostiles | Fall back to the narrowest interior chokepoint and hold it |
| Intruding player | `ZonePolicy.alsoPlayers`, default off; allow-list for friends |
| Owner's pets/villagers inside | Never targets; owned tamables auto-allow-listed |
| Owner edits blocks inside | No reaction; owner block changes never register as threat |
| Fire/lava inside the zone | Report, move out of the hazard; optional water-bucket response (off by default) |
| Zone chunks unload | Guards suspend cleanly; no phantom breaches on reload |
| A guard dies | Roster change → surviving sentries redistribute coverage immediately |
| Zone deleted / corners invalid | Wards revert to `HOLD` **at their post** and report — they do not wander off |

**Self-centred (any order)**

| Situation | Response |
|---|---|
| Below withdraw threshold, owner not fighting | Fighting withdrawal → heal → re-engage |
| Below threshold, owner fighting within 12 | **Do not withdraw** (§9.2 rule 1) |
| Cornered, no ally, no route | Fight to the death, last-stand line |
| Weapon breaks | Auto-swap from inventory; report if nothing remains |
| Out of arrows | Fall back to melee, report |
| Stuck | Repath → nudge → coarse reroute → report + hold |
| Falling / burning / lava / drowning | Hazard reflex outranks all combat |
| Night, no shelter | Ranged prefer high ground; melee close ranks |
| Thunderstorm | Perception penalty per §6.1; Stormcaller keeps its existing flavour |
| Nether / End | Lava and void-edge cells in the danger set; no melee chasing over lava |

---

## §22 — The Jobs workstream (`TASK.md`)

`TASK.md` contains a detailed, still-unresolved audit of Lumberjack, Miner, Fisher, Hunter
and Chef (D-28). It is read-only and remains the authority for that work. Two notes for
whoever schedules it:

- **It composes cleanly with this plan.** `WORK` stance maps to today's
  `getJob() != NONE` checks; the §4.1 shim keeps all five job goals compiling; circular
  patrol radius stays job-only while rectangular zones are ward-only; `DeliverToChestGoal`
  and `JobReservations` are untouched; the Assignment Wand keeps its chest-binding role and
  does not overlap the Perimeter Rod.
- **Two pieces should be shared rather than duplicated**: the resumable lifecycle
  (`SEARCHING → TRAVELLING → WORKING → COLLECTING → DELIVERING → RETURNING`) is the same
  shape as the §4 order lifecycle, and the §5 navigation work fixes a large fraction of the
  travel/stall problems `TASK.md` describes. **Do phases 0–2 of this plan before starting the
  jobs revamp** — much of its travel section becomes trivial afterwards.

---

## §23 — Open decisions

| # | Decision | Default if you don't answer |
|---|---|---|
| 1 | Starter class: Vanguard / Knight / Cleric | **Vanguard** |
| 2 | Zone shape: 2-corner rectangle, or multi-point polygon | **Rectangle** (polygon is a phase-9 stretch) |
| 3 | `ZonePolicy.alsoPlayers` default | **Off** |
| 4 | `ai.maxActiveCombatants` = 4 — fixed, or a slider? | **4, config-exposed** |
| 5 | `ai.profile` default | **VETERAN** |
| 6 | `nav.teleportPolicy` default | **LAST_RESORT** (`NEVER` if you want zero teleports and accept occasional stragglers) |
| 7 | `nav.stepHeight` 1.0 — players are 0.6; do you mind them being slightly better climbers? | **1.0** |
| 8 | `zones.autoLighting` | **Off** |
| 9 | Squad cap 8 — enough? | **8** |
| 10 | Should `MOVE_TO` chunk-load the corridor so long marches don't stall? | **No** (opt-in via `zones.chunkload` pattern later) |
