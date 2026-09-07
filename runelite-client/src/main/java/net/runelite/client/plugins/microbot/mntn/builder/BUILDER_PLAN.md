# Mntn Account Builder Plan

This document tracks the work to turn the builder into a simple, well-defined account planning system. The initial implementation is F2P-only, but the core model should support members content later without rewriting the planner.

## Design Boundaries

- Focus on coherent player-like behavior: sensible goals, preparation, recovery, and varied task choice.
- Do not design around ban detection or evasion.
- When learning from other account builders, copy product/architecture ideas only: clear toggles, weights, profiles, supported-method catalogs, requirements, recovery, and UI feedback.
- Keep task systems small, explicit, and reusable.
- Prefer planner-visible requirements over hidden task-local supply logic.
- Use Microbot singleton caches and Rs2 utility APIs; never instantiate queryables directly.

## Target Architecture

- `AccountSnapshot`: one read-only view of account state per planning pass.
- `Goal`: desired account outcome, such as skill level, quest completion, items, gear, or money.
- `Requirement`: typed blocker or need, such as item, equipment, money, skill, quest, location, or safety.
- `Activity`: category that can satisfy requirements, such as fishing, mining, supply, questing, combat, or money-making.
- `Strategy`: one concrete way to perform an activity, with declared content access, requirements, score inputs, and task creation.
- `Task`: small phase machine that executes a chosen strategy and returns clear status.
- `PlannerScorer`: centralized scoring for readiness, distance, XP, profit, risk, unlock value, and repetition.
- `AccountMemory`: recent history, failures, preferences, and current session flavor.

## Current Status

- F2P is the active content mode; members content should be added behind `ContentAccess.MEMBERS`.
- Real testing should assume mostly fresh F2P accounts: low/no coins, empty or poor bank, no teleports, starter stats, and short Lumbridge/Varrock/Port Sarim progression paths.
- The planner now sees content access, account snapshots, supply requirements, and session-local memory.
- The planner can recursively resolve prerequisite chains up to a bounded depth, so a combat task can request gear/food, supply can request coins, and money-making can become the chosen next step.
- Config is grouped into General, Overlay, Skill Targets, Money, and Quests sections. Skill target `0` now means that skill goal is ignored.
- Skill Weights are configurable from 1-9 and directly drive skill goal priority, matching the useful public pattern from Aeglen-style account-builder interfaces while keeping behavior explicit.
- Supply Policy config can enable or disable Grand Exchange, shop, and ground-pickup routes, which lets fresh-F2P testing run with self-contained acquisition rules.
- Overlay visibility/detail is configurable, and the overlay now renders from a single read-only script state instead of loose debug fields.
- The overlay shows the active runner state, activity, strategy, commitment time, task status, stop reason, content mode, session flavor, goal, requirement, and score when detailed mode is enabled.
- Supply acquisition is route-aware: bank, Grand Exchange, known shops, and known ground pickups can all be planner candidates.
- Purchasable supply routes now declare their coin need as a planner-visible `MoneyRequirement`, so missing coins can chain into banking or money-making instead of dead-ending the item route.
- Generic Grand Exchange routes use live offer prices when available; catalog shop routes use conservative item-specific estimates.
- Item requirements can also be satisfied by matching item-producing skilling activities such as mining ores, cutting logs, and fishing raw fish.
- `MoneyMakingActivity` is registered with initial F2P methods for cowhides, raw shrimps, logs, copper ore, and iron ore.
- Money-making now also supports bronze bars and iron bars when the account has the ores and Smithing level.
- Money-making can run as a fallback for missing coins and as an optional account-level money goal via `moneyTarget`.
- Prayer target is now a real planner goal and routes through combat methods that bury bones while training.
- Combat strategy now exposes starter weapon and risk-based food requirements to the planner; chickens do not require food, while goblins/cows request food only when the inventory is below the monster-specific target.
- Chickens are now the fresh-F2P no-gear combat bootstrap fallback: if the account has no weapon and no coins, the planner can still choose chickens and `CombatTask` can fight them unarmed.
- Combat prayer training now chooses the lowest melee style to keep account combat stats balanced while burying bones.
- Combat task recovery is bounded for style setup, failed attack clicks, and missing target searches, with clear replan reasons instead of spinning indefinitely.
- Supply equipment steps now require a confirmed equipped item before completing; failed wield/equipment confirmation returns `EQUIP_FAILED` and goes back through the planner.
- Terminal task handling now verifies whether `COMPLETE` actually satisfied the active goal or requirement before recording the outcome, and replans on the next tick instead of ticking an empty task.
- Startup bank warming can now finish once the bank cache is refreshed and inventory/equipment are empty, even if the bank UI remains open after depositing worn gear.
- Startup completion now hands off through a short explicit `Planning` state before the first normal planner tick, so stale `Startup / Warm bank cache` overlay text should not survive after banking finishes.
- The script loop now treats `Planning` with no active task as a planner-only recovery state before the shared Microbot guard, so post-startup planning cannot remain passive forever while logged in.
- Shared script-guard pauses now show a visible `Paused / Waiting for Microbot script guard` overlay state instead of leaving the previous planner label on screen.
- Script-loop throwables now surface as a `Script error` overlay state with the throwable type/message instead of silently killing future scheduler ticks.
- Overlay rendering is guarded and has a fallback render-error panel, so overlay UI problems no longer hide the runner/planner state.
- Startup completion and replanning now produce always-on Microbot log breadcrumbs even when debug logging is disabled.
- Plugin start now clears any previous scheduled builder loop and resets runner state before creating a fresh planner, making repeated runtime retests cleaner.
- Banking close cleanup is bounded; after repeated close misses, the completed bank mutation is allowed to finish instead of looping forever in `CLOSE`.
- Startup/shared banking now has bounded recovery for walking to a bank, opening the bank, depositing, and withdrawing. If startup bank warming stalls, it returns `BANK_FAILED` so the startup loop recreates the banking task and retries from walking/opening.
- Startup bank warming now immediately clears the startup task and replans on the same tick after success, instead of briefly sitting in a completed startup state.
- Planner no-candidate states now report a diagnostic string with incomplete goal count, first blocked requirement, provider count, strategy count, executable count, unmet requirement count, and content-filter count.
- Clearing a task now leaves `TaskManager` in a neutral complete/none state instead of reporting `RUNNING` with no task.
- Cook's Assistant and Doric's Quest now expose planner-visible requirements instead of keeping all blockers hidden in task code.
- `TaskStatus.BLOCKED` now clears the current task and asks the planner for a new decision.
- Tasks can report `TaskStopReason`; supply, money-making, skilling, combat, and current quest tasks now report specific reasons for known dead ends.
- Slow GE offer handling waits for buy/sell completion for a bounded number of ticks before reporting an offer failure.
- `PlannerScorer` now uses richer inputs: bank readiness, travel distance, XP estimate, profit estimate, supply cost, safety, unlock value, repetition, and recent failure memory.
- `SessionFlavor` can bias the same account goals toward balanced, quest-focused, gatherer, combat-heavy, or efficient sessions.
- Quest prerequisite/reward metadata is centralized in `QuestCatalog` for supported quests.
- Focused unit tests cover planner decisions, session flavor, recursive prerequisite fallback, cooldown penalties, item-producing activity selection, combat supply policy, prayer style selection, and quest metadata.
- The supply catalog is intentionally conservative; add item-specific shop, spawn, gather, and quest-reward routes as methods are verified.

## Next Priorities

1. Runtime-test combat against chickens, goblins, and cows with empty supplies, bank-only supplies, and low-health recovery.
2. Create a fresh-F2P smoke preset/manual test recipe: GE off, shops on, ground pickups on, low skill targets, chickens first.
3. Extract shared helpers for gather loops, bank loadouts, production widgets, combat loadouts, and travel recovery.
4. Expand the F2P combat catalog with verified target variants, safe training areas, loot policies, and minimum loadout profiles.
5. Add runtime tests for banking, supply acquisition, GE slow-offer handling, task recovery, and quest handoff behavior.
6. Expand the F2P supply catalog with more verified shops, ground spawns, simple NPC drops, and quest reward routes.
7. Add more F2P money-making methods for crafted items, shop routes, and simple NPC drops.
8. Add richer planner explanations to overlay/debug output: top candidate list, lost-candidate reasons, and prerequisite chain.
9. Add account-profile presets and save/load support for common build styles.
10. Add route policy toggles for item selling and looting.
11. Decide whether config should use only target `0` disables, or add explicit per-skill enable toggles/profiles.
12. Add members methods behind content access flags after F2P task contracts are stable.

## Fresh F2P Testing Baseline

Assumptions:

- Account has completed tutorial and can use normal F2P game systems.
- Bank may be empty except tutorial leftovers.
- Coins may be zero or very low.
- No teleports should be required for the first smoke tests.
- Preferred initial area is Lumbridge, then nearby Varrock/Port Sarim/Falador only after walking and banking are stable.

Core systems that must be reliable before adding many new methods:

- Startup bank warming: populate bank cache and leave account in a known state. This now retries walking/opening/depositing on stalls and reports `BANK_FAILED` after bounded attempts.
- Requirement-to-supply chain: missing item/equipment/money should lead to bank, shop, ground pickup, skilling, or money-making rather than dead-ending.
- Banking/loadout behavior: deposit, withdraw, keep tools, equip gear, and preserve supplies.
- Travel recovery: walk to bank, shop, spawn, or task area and report failure if not arriving.
- Task phase contract: every task should have clear phases, bounded retries, stop reasons, and replan behavior.
- Terminal task contract: `COMPLETE` should mean either the goal or active requirement is verifiably satisfied; otherwise the task should return a specific failed/replan reason.
- Combat baseline: chickens first, then goblins/cows once gear/food/safety rules are proven.
- Overlay/debug feedback: show active goal, missing requirement, chosen route, task phase, status, and last stop reason.

Suggested first manual runtime recipe:

- F2P mode.
- Fresh account after tutorial.
- GE disabled, shops enabled, ground pickups enabled.
- Combat targets enabled with low Attack/Strength/Defence/Prayer goals.
- Money target disabled at first unless testing supply coin fallback.
- Verify startup bank cache, immediate post-startup plan selection, no-gear chicken combat, starter gear handling when available, bones/loot handling, level completion, and clean replan.

## Aeglen-Inspired Feature Map

Public references used for product inspiration:

- [Aeglen Getting Started](https://wiki.aeglen.net/Getting_Started): one-click start, toggleable features, skill weights, profiles, and mid-run settings changes.
- [Aeglen Settings](https://wiki.aeglen.net/Settings): GE usage, item selling, looting, strict goals, streamer mode, breaks, and membership/teleport preferences.
- [Aeglen Skill Options](https://wiki.aeglen.net/Skill_Options): per-skill modifiers, Slayer/combat preference routing, skilling reward claims, and activity-specific options.
- [Aeglen Quests](https://wiki.aeglen.net/Quests): resumable quests, quest priority, higher buy prices, and quest-specific requirements.
- [Aeglen Supported Gear](https://wiki.aeglen.net/Supported_Gear): explicit supported gear/consumable catalogs.
- [Aeglen Supported Teleports](https://wiki.aeglen.net/Supported_Teleports): explicit travel item/spell/fast-travel catalog.

Ideas to adopt:

- [x] Toggle skills by setting target `0`.
- [x] Add 1-9 skill weights to bias account progression.
- [x] Add route policy toggles for Grand Exchange, shops, and ground pickups.
- [x] Keep requirements visible in planner/overlay instead of hidden inside task code.
- [x] Keep explicit supported supply, gear, quest, and method catalogs.
- [ ] Add saved build profiles/presets.
- [x] Add route policy toggles: allow Grand Exchange, allow shops, allow ground pickups.
- [ ] Add route policy toggles: allow item selling and allow looting.
- [ ] Add strict-goals option: stop immediately at target or allow small useful overleveling.
- [ ] Add new-account pacing mode focused on low-level setup, bank warming, nearby starter routes, and simple starter tasks. This should be framed as progression quality, not ban evasion.
- [ ] Add per-skill modifiers, starting with combat: prefer Slayer later, use prayer supplies when affordable, defensive style options, loot policy, and food policy.
- [ ] Add supported gear/loadout preference model that can prefer user-owned gear while still respecting required items.
- [ ] Add teleport/travel catalog for F2P first, then members.
- [ ] Add resumable quest-state contracts and quick give-up behavior when optional quest items turn out to be required.

## Phase 1: Foundation

- [x] Document the builder roadmap.
- [x] Fix config/plugin text that still says Fishing-only.
- [x] Stop overriding configured antiban intensity to `MODERATE`.
- [x] Remove priority double-counting from skill and quest requirements.
- [x] Add content availability model for F2P now and members later.
- [x] Add first `AccountSnapshot` and expose it from `AccountContext`.
- [x] Filter strategies by allowed content before scoring.

## Phase 2: Requirements And Supplies

- [x] Implement `ItemRequirement`.
- [x] Implement `EquipmentRequirement`.
- [x] Implement `MoneyRequirement`.
- [x] Add a `SupplyActivity`.
- [x] Add a conservative `SupplyTask` for bank withdrawal and gear equip.
- [x] Let strategies expose planner-visible supply requirements.
- [x] Add route-aware supply acquisition model.
- [x] Add Grand Exchange supply route.
- [x] Add shop supply route.
- [x] Add ground-item pickup supply route using `Microbot.getRs2TileItemCache().query()`.
- [x] Let item requirements ask item-producing skilling activities for matching methods.
- [x] Replan when the active requirement is satisfied, not only when the top-level goal is complete.
- [x] Let purchasable supply routes expose missing coins as `MoneyRequirement`.
- [x] Add money-making fallback when a GE/shop supply route is affordable in theory but the account lacks coins.
- [x] Add bounded recursive prerequisite planning so strategy requirements can chain into supply and money-making tasks.
- [x] Add slow-buy/offer-state handling for GE offers that do not complete immediately.
- [x] Seed item-specific routes for common early F2P shops and spawns.
- [ ] Convert fishing, woodcutting, mining, smithing, combat, and quests to rely on supply requirements instead of task-local banking. Combat is partially converted: gear and food are planner-visible, but bank/equip execution still lives in `CombatTask`.
- [ ] Add item-specific routes for more F2P shops, spawns, gathering methods, NPC drops, and quest rewards.

### Supply Route Catalog Notes

- Add item routes in `activities/supply/SupplyCatalog`.
- Always prefer verified sources over broad assumptions. Bank is automatic; GE is the generic tradeable fallback; shop and ground routes should be item-specific.
- Current known route examples: eggs from the Lumbridge farm ground spawn, general-store containers/tools from Lumbridge General Store, fishing tools from Gerrant in Port Sarim, axes from Bob in Lumbridge, pickaxes from Nurmof, bows/arrows from Lowe, staves from Zaff, and scimitars from Zeke.
- Future route types are already modeled: `SKILL_ACTIVITY`, `QUEST_REWARD`, and `OTHER`. Use these when an item should be gathered by doing a skilling method, obtained from a quest step, or acquired by a special interaction instead of purchased.
- When a route needs coins and the account is short, the supply strategy emits a money requirement so `MoneyMakingActivity` can choose a F2P method.
- Mining, woodcutting, and fishing now advertise item production for items already named on their methods. Add more produced-item fields as new activities mature.

## Phase 3: Task Contracts

- [x] Add richer task status/block reasons.
- [x] Centralize terminal task status handling and treat `BLOCKED` as planner-visible.
- [x] Feed task stop reasons into `AccountMemory`.
- [x] Apply reason-specific cooldown penalties in `PlannerScorer`.
- [x] Add pre-tick replan reason hook via `Task.getReplanStopReason`.
- [ ] Standardize task phase behavior and stuck recovery.
- [ ] Add shared helpers for gather, production widget, combat loadout, and bank loadout tasks.
- [x] Add planner-visible task failure memory so failed methods cool down instead of looping forever.
- [x] Add specific stop reasons for supply and money-making failures.
- [x] Convert non-supply tasks to report specific `TaskStopReason` values for known terminal failures.
- [x] Add bounded combat recovery for combat style setup, attack click failures, and missing target searches.
- [x] Add no-gear chicken fallback for fresh-F2P combat bootstrap.

## Phase 4: Human-Like Planning

- [x] Add `PlannerScorer` as the central scoring home.
- [x] Add `AccountMemory`.
- [x] Add initial repetition penalty and failed-strategy cooldown scoring.
- [x] Expand `PlannerScorer` with travel cost, bank readiness, supply cost, XP estimate, profit estimate, safety, and unlock value.
- [x] Add session flavor profiles such as balanced, quest-focused, gatherer, combat-heavy, and efficient.
- [x] Add controlled commitment durations per strategy.
- [x] Add profile-driven commitment duration preferences.
- [x] Add first planner/task status overlay with active goal, requirement, activity, strategy, score, mode, flavor, status, and stop reason.
- [x] Add first no-runnable-plan diagnostic for overlay/debug output.
- [ ] Add richer planner explanations to overlay/debug output: top candidate list, lost-candidate reasons, and prerequisite chain.

## Phase 4.5: Config And Overlay

- [x] Group config into General, Overlay, Skill Targets, Money, and Quests sections.
- [x] Add overlay visibility and detailed/compact controls.
- [x] Add level ranges to skill target settings.
- [x] Treat skill target `0` as disabled in goal building.
- [x] Add 1-9 skill weights and use them as explicit skill goal priorities.
- [x] Add route policy controls for GE, shops, and ground pickups.
- [x] Add a read-only `MntnBuilderOverlayState` so overlay rendering does not depend on loose script fields.
- [ ] Decide whether to keep target `0` as the only per-skill disable control or add explicit enable toggles.
- [ ] Add saved profiles/presets for common account builds.
- [x] Add route policy toggles for GE, shops, and ground pickups.
- [ ] Add route policy toggles for selling and looting.
- [ ] Add combat-specific overlay details such as target monster, food target, loot mode, and current recovery counter.
- [ ] Add top-candidate / prerequisite-chain explanation lines once planner explanation objects exist.

## Phase 5: Quests And Unlocks

- [x] Represent quest prerequisites and rewards declaratively for supported quests.
- [x] Let supported quests request prerequisite skills/items from metadata instead of hard blocking inside strategy code.
- [ ] Integrate with Quest Helper execution where possible.
- [x] Convert Cook's Assistant and Doric's Quest to planner-visible requirements.
- [x] Route prayer training through combat while choosing the lowest melee attack style for the active fight.

## Phase 6: Money-Making

- [x] Add `MoneyMakingActivity`.
- [x] Add initial F2P methods: cowhides, raw shrimps, logs, copper ore, iron ore, bronze bars, and iron bars.
- [x] Let money-making run as both fallback and optional account goal.
- [x] Use profit and supply needs in planner scoring.
- [ ] Add more verified F2P methods such as crafted items, shop routes, and simple NPC drops.

## Phase 7: Members-Ready Expansion

- [ ] Add members methods behind content access flags.
- [ ] Add membership/world support to travel and method selection.
- [ ] Add members quests, transport unlocks, better training methods, and broader gear/loadouts.

## Validation

- [x] `:client:compileJava` passed before foundation edits.
- [x] `:client:compileJava` passes after foundation slice.
- [x] `:client:compileJava` passes after supply requirement slice.
- [x] `:client:compileJava` passes after scorer extraction.
- [x] `:client:compileJava` passes after AccountMemory and Doric prerequisite slice.
- [x] `:client:compileJava` passes after centralized task status handling.
- [x] `:client:compileJava` passes after route-aware supply acquisition slice.
- [x] `:client:compileJava` passes after item-producing activity supply slice.
- [x] `:client:compileJava` passes after task stop-reason memory slice.
- [x] `:client:compileJava` passes after money-making activity, richer scorer metrics, GE offer waiting, prayer goal wiring, and optional money target.
- [x] `:client:compileJava` passes after task stop-reason expansion, bar money-making methods, and F2P catalog expansion.
- [x] `:client:compileJava` passes after recursive prerequisite planning and combat task/policy fixes.
- [x] `:client:compileJava` passes after config grouping and overlay state cleanup.
- [x] `:client:compileJava` passes after Aeglen-style skill weights.
- [x] `:client:compileJava` passes after fresh-F2P supply route policy controls.
- [x] `:client:compileJava` passes after immediate startup replan and no-gear chicken bootstrap.
- [x] `:client:compileJava` passes after no-runnable-plan diagnostics.
- [x] Focused `:client:runUnitTests --tests net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlannerTest` passes.
- [x] Focused `:client:runUnitTests --tests net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlannerTest --tests net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestCatalogTest` passes.
- [x] Focused `:client:runUnitTests --tests net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlannerTest --tests net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestCatalogTest --tests net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategyTest` passes.
- [x] Focused planner, quest catalog, and combat strategy tests pass after config/overlay cleanup.
- [x] Focused builder script, planner, quest catalog, and combat strategy tests pass after skill weights.
- [x] Focused builder script, planner, quest catalog, combat strategy, and supply route policy tests pass after fresh-F2P route policy controls.
- [x] Focused builder script, planner, quest catalog, combat strategy, and supply route policy tests pass after immediate startup replan and no-gear chicken bootstrap.
- [x] Focused builder script, planner, quest catalog, combat strategy, and supply route policy tests pass after no-runnable-plan diagnostics.
- [x] Planner unit tests cover content filtering, recursive money fallback, cooldown penalties, item-producing activity selection, and session flavor selection.
- [x] Planner unit tests cover fresh-F2P combat targets selecting unarmed chickens when no gear/coins are available.
- [x] Builder script tests cover skill target disabling and weight-to-priority behavior.
- [x] Supply activity tests cover disabling GE and ground-pickup routes by policy.
- [x] Combat strategy unit tests cover no-gear chicken bootstrap, banked weapon requirements, risk-based starter food requirements, missing-food quantities, and prayer melee-style selection.
- [x] Quest metadata unit test covers supported quest requirements and rewards.
- [ ] Runtime tests validate combat banking, supply acquisition, target selection, and task recovery.
- [ ] Retest startup bank warming on a fresh F2P account after the banking recovery fix.
