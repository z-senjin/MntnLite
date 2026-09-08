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
- Skill targets are the only progression controls. Each local Microbot profile receives a stable, small `45-55` goal-preference variation so accounts can take different routes without exposing per-skill weights or letting preferences outweigh method value.
- Supply Policy config can enable or disable Grand Exchange, shop, and ground-pickup routes, which lets fresh-F2P testing run with self-contained acquisition rules.
- Overlay visibility/detail is configurable, and the overlay now renders from a single read-only script state instead of loose debug fields.
- The overlay shows the active runner state, activity, strategy, commitment time, task status, stop reason, content mode, session flavor, goal, requirement, and score when detailed mode is enabled.
- Supply acquisition is route-aware: bank, Grand Exchange, known shops, and known ground pickups can all be planner candidates.
- Purchasable supply routes now declare their coin need as a planner-visible `MoneyRequirement`, so missing coins can chain into banking or money-making instead of dead-ending the item route.
- Generic Grand Exchange routes use live offer prices when available; catalog shop routes use conservative item-specific estimates.
- Item requirements can also be satisfied by matching item-producing skilling activities such as mining ores, cutting logs, and fishing raw fish.
- `MoneyMakingActivity` is registered with initial F2P methods for chicken feathers, cowhides, raw shrimps, logs, copper ore, iron ore, bronze bars, and iron bars.
- Chicken feathers are the zero-gear, zero-capital F2P money bootstrap when the GE is enabled: fresh accounts can fight chickens unarmed, collect stackable feathers, and sell them through the GE. Lumbridge General Store pays `0 gp` for feathers, so the planner never offers that route.
- When no usable axe or pickaxe exists, woodcutting and mining now explicitly request a bronze starter tool. That lets an empty-bank skill goal chain cleanly through the chicken-feather GE coin route, tool purchase, and the requested activity instead of failing silently before it can ask for supplies.
- Money-making selects an explicit sale route per plan. When shops are enabled it can sell gathered items at Lumbridge General Store; when the GE is enabled it can use the existing GE offer flow. Disabling either supply policy removes the corresponding money-making route.
- Shop sale is a guarded task sequence: walk to the verified store, confirm the shop is open, sell one supported item, and confirm coins increased before continuing. Low-value loot is excluded from shop routes; a zero-value result ends the task with a specific stop reason.
- Shop visits now reset their open/sell guards at the travel boundary, so a failed earlier visit cannot exhaust a later visit immediately. After a bank withdrawal, money-making also waits for the sellable item to appear in inventory before choosing to sell or gather again.
- GE collection no longer blocks the task executor with a static wait. It now observes coin progress between ticks and uses a bounded collect guard.
- GE offer quantity entry now has a bounded utility-level recovery path: an offer form that never exposes its quantity control retries briefly, returns to the overview, and lets the Builder report `GE_OFFER_FAILED` instead of trapping its script thread in a log-spam loop.
- Live runtime diagnosis is ready to use through Microbot's local Agent Server: inspect builder script status, player state, inventory/bank, nearby entities, screenshots, and the visible widget tree at a failure boundary before changing code. The local testing client must enable `Microbot Agent Server` with TCP port `8081` and stealth bind disabled before this loop can connect.
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
- Builder banking now caches the selected bank target, bounds nearest-bank resolution attempts, cools down repeated nearest-bank lookups after resolver/target failures, abandons bad bank targets after walk/open stalls, and falls back to a conservative F2P bank list without modifying `Rs2Bank`.
- If ordinary planning produces no task, or the planner throws a recoverable runtime error, an incomplete F2P combat goal now falls back to a no-supply chicken task. This keeps fresh accounts moving while the normal bank/supply dependency graph recovers.
- F2P planning now avoids full `AccountSnapshot` capture. F2P strategies are inherently reachable, while snapshot capture reads every skill and quest state before task selection and could stall the runner at `Planning / Selecting next task`.
- Parent combat and skilling tasks now propagate every terminal bank result, including `BLOCKED`. Previously they only handled `FAILED` and `REPLAN`, allowing a failed nested bank task to be reported as `RUNNING` forever.
- Plan application now validates task creation. A null task or task-construction runtime failure records `TASK_CREATION_FAILED`, clears the partial plan, and returns to planning on the next tick instead of leaving a plan with no executable task.
- Planner prerequisite handling is now strict: a strategy with an unmet requirement is never selected unless the planner first found a runnable prerequisite task. This prevents repeated selection of tasks that immediately fail for missing tools, gear, items, or coins.
- Prerequisite route discovery now short-circuits on the first runnable route. The catalog intentionally orders bank before shops and GE, so a banked item is selected directly without evaluating unrelated later routes or their price lookups.
- Builder tasks now have a shared non-blocking `TaskActionGuard`: a phase confirms the expected game state, retries only after a cooldown, and ends with a specific recoverable stop reason when its time/attempt budget is exhausted.
- The guard now protects combat walking and attacks; supply walking, shop opening/purchase, and ground-item search/pickup; and the early F2P fishing, mining, and woodcutting walking/gather actions. These flows no longer rely on fixed sleeps to presume a click, walk, shop, or pickup succeeded.
- Banking keeps its existing open/close/deposit/withdraw retry and nearest-bank recovery checks. Shop money-making sales and GE collection now use bounded confirmation; production, cooking, smelting, forging, GE offer placement, and quest interactions are the next action-guard migration pass.
- Banking close cleanup is bounded; after repeated close misses, the completed bank mutation is allowed to finish instead of looping forever in `CLOSE`.
- Startup/shared banking now has bounded recovery for walking to a bank, opening the bank, depositing, and withdrawing. If startup bank warming stalls, it returns `BANK_FAILED` so the startup loop recreates the banking task and retries from walking/opening.
- Startup bank warming now immediately clears the startup task and replans on the same tick after success, instead of briefly sitting in a completed startup state.
- Planner no-candidate states now report a diagnostic string with incomplete goal count, first blocked requirement, provider count, strategy count, executable count, unmet requirement count, and content-filter count.
- Clearing a task now leaves `TaskManager` in a neutral complete/none state instead of reporting `RUNNING` with no task.
- Cook's Assistant and Doric's Quest now expose planner-visible requirements instead of keeping all blockers hidden in task code.
- `TaskStatus.BLOCKED` now clears the current task and asks the planner for a new decision.
- Tasks can report `TaskStopReason`; supply, money-making, skilling, combat, and current quest tasks now report specific reasons for known dead ends.
- [x] General-store sales now use an explicit non-zero value floor. Before every sale, the task calculates the current payout from the item's in-game value and live shop stock; feathers and other low-value loot are GE-only, while every allowed shop sale is one item at a time and must increase coins before the next sale.
- Slow GE offer handling waits for buy/sell completion for a bounded number of ticks before reporting an offer failure.
- `PlannerScorer` now uses richer inputs: bank readiness, travel distance, XP estimate, profit estimate, supply cost, safety, unlock value, repetition, and recent failure memory.
- `SessionFlavor` can bias the same account goals toward balanced, quest-focused, gatherer, combat-heavy, or efficient sessions.
- Quest prerequisite/reward metadata is centralized in `QuestCatalog` for supported quests.
- Focused unit tests cover planner decisions, session flavor, recursive prerequisite fallback, cooldown penalties, item-producing activity selection, combat supply policy, prayer style selection, and quest metadata.
- Focused planner tests also prove the fresh-F2P chicken plan creates an executable task, handles a banked bronze axe, and does not capture a full account snapshot during F2P selection.
- The normal planner is regression-tested to choose the bank-backed `Equip Bronze axe` supply prerequisite before combat when the bank contains a bronze axe and 31 coins.
- Builder antiban integration now uses method-specific activity contexts for starter combat, fishing, woodcutting, mining, smithing, and collecting tasks. It reapplies the builder's configured intensity after each context switch, and only requests user-enabled cooldowns or micro-breaks at successful plan boundaries. Global antiban preferences remain owned by the Antiban panel.
- Script-guard pauses now freeze the active builder task and its commitment clock rather than replacing its goal/strategy/time display or allowing planner recovery to run. The overlay distinguishes generic pauses, antiban cooldowns, and antiban breaks while preserving the active plan until the guard releases.
- Builder now yields to both Break Handler login flows, freezes active task deadlines through logout/login, waits for the handler to finish, and requires two game-ready ticks with a local player before resuming work.
- **Break Handler V2 coexistence audit (2026-09-07):** Builder now yields for the entire active V2 state window, not just logout/login. This includes `BREAK_REQUESTED`, safety checks, no-logout breaks, logout, login retries, break completion, and the profile-switch placeholder. That prevents Builder activity from keeping V2's safety check perpetually busy. The active plan, commitment time, and action-guard deadlines remain frozen; after V2 returns to `WAITING_FOR_BREAK`, Builder requires two game-ready ticks before resuming. The same ownership rule now applies to the original Break Handler when it is enabled. Neither handler is required for normal Builder operation.
- [x] Add authenticated, read-only Agent Server runtime status at `GET /mntn-builder/status`. It publishes the Builder's immutable overlay snapshot after every loop and clears it on shutdown, so live diagnosis can inspect the current plan, task phase/status, stop reason, and score without mutating game state.
- [x] Combat attack retries are now scoped to the individual NPC instead of every monster sharing one retry counter. `TaskActionGuard` also freezes active deadlines during a Builder script-guard pause, preventing an antiban cooldown or other guard pause from converting a single accepted combat action into a timeout.
- [x] Combat style selection now resolves the equipped weapon's live style metadata and confirms the selected `COM_MODE` varp before fighting. It no longer assumes Defence is always the fourth combat widget; this prevents unarmed and axe combat from silently training Strength while a Defence goal is active.
- [x] Money-making now clears Bones from a full inventory before returning to gather. If a full inventory contains no safe cleanup item, the task reports `INVENTORY_FULL` for replanning instead of cycling forever through `CHECK -> GATHER`.
- The supply catalog is intentionally conservative; add item-specific shop, spawn, gather, and quest-reward routes as methods are verified.
- **Core audit completed (2026-09-07):** the Builder has one task owner (`TaskManager`), task-local phases, planner-visible terminal reasons, and bounded action guards. The audit removed blocking waits from banking equipment deposits, supply GE collection/equipment, production widgets, and the two current quest tasks. A failed click, route, station lookup, or confirmation now stays in the owning task until confirmed or ends with a specific planner-visible reason.
- `AccountContext` is now the sole Builder boundary for direct RuneLite client reads. Skill levels, combat level, plane, login readiness, membership checks, and null-safe location checks use the client-thread helper; activities and tasks no longer read the raw client directly.
- Production tasks remain intentionally separate and small. Cooking, smelting, and forging each own their walk, station, widget, production-confirmation, and bank phases instead of introducing a shared workflow framework.
- `AccountMemory` prunes expired selection/failure entries as it is used, keeping cooldown behavior session-local and bounded during long runs.

## Current Execution Order

The next implementation work should favor a few complete, observable F2P loops over adding
more method catalogs or planner heuristics. A task is only considered supported when it can
acquire its inputs, travel, act, verify progress, bank or recover, and hand control back to
the planner with a specific result.

1. **Stabilize the task contract.** Every current Builder action follows `observe -> act -> confirm -> bounded recovery -> replan`, including bank loadouts, gathering, supply, production widgets, and the two supported quests. Keep GE offer placement under runtime observation. Do not add a generic state-machine framework; keep phases inside the owning task and use `TaskActionGuard` only for actions that need retries.
2. **Prove the fresh-F2P bootstrap.** Run the complete path from warm bank cache through a banked tool/equipment withdrawal, no-gear chickens when necessary, travel, fighting, loot/bones, low-health recovery, banking, and the next plan. Maintain a short manual smoke recipe for the exact account state used during testing.
3. **Complete three foundational skill loops.** Make woodcutting, fishing/cooking, and mining/smelting/smithing each work end-to-end: obtain tool/input, travel to a verified location, gather or produce, handle full inventory, bank, and replan. Finish and test one loop before broadening its method list.
4. **Make supplies and coins reliable.** Keep acquisition order simple: bank first, then a verified nearby shop or ground source, then GE only where it is proven useful. The first dependable F2P loop is now unarmed chickens -> feathers -> GE -> starter tool coins. Runtime-test that loop before expanding the money catalog.
5. **Expand progression decisions only after the loops are proven.** The planner should choose among working tasks using configured goals, route availability, basic travel cost, safety, and short failure cooldowns. Avoid simulated personality, deep dependency graphs, persistence, or large scoring systems until actual task data shows they are needed.
6. **Add quests and members content last.** Supported quests need the same observable task contract. Members methods remain behind content flags until their F2P-equivalent loops are stable.

## Next Priorities

1. Runtime-test combat against chickens, goblins, and cows with empty supplies, bank-only supplies, and low-health recovery.
2. Create a fresh-F2P smoke preset/manual test recipe: GE off, shops on, ground pickups on, low skill targets, chickens first.
3. Extract shared helpers for gather loops, bank loadouts, production widgets, combat loadouts, and travel recovery.
4. Expand the F2P combat catalog with verified target variants, safe training areas, loot policies, and minimum loadout profiles.
5. Add runtime tests for banking, supply acquisition, GE slow-offer handling, task recovery, and quest handoff behavior.
6. Expand the F2P supply catalog with more verified shops, ground spawns, simple NPC drops, and quest reward routes.
7. After the chicken bootstrap is runtime-proven, add only verified F2P money methods with a complete gather/sell contract: clay or low-level ore with a banked/shop pickaxe, cowhide with food, and gathered logs/fish with route-aware sales.
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
- [x] Replace configurable skill weights with stable per-profile goal-preference variation.
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
- [x] Keep F2P planner selection independent of full account snapshot capture.

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
- [x] Add zero-gear chicken-feather GE coin bootstrap and guard all general-store sale routes against zero-value items.
- [x] Make money-making sale routes honor the existing shop/GE policy settings.
- [x] Replace money-making GE collection's static wait with bounded tick-based confirmation.
- [x] Expose bronze starter axe/pickaxe requirements when a gathering skill has no usable tool.
- [x] Add bounded recursive prerequisite planning so strategy requirements can chain into supply and money-making tasks.
- [x] Add slow-buy/offer-state handling for GE offers that do not complete immediately.
- [x] Seed item-specific routes for common early F2P shops and spawns.
- [x] Use verified starter-tool shop prices and ordered fallback routes: starter shop, alternate shop, GE, then higher shop-stocked tiers when affordable. Do not offer false shop routes for unsupported F2P tool tiers.
- [x] Add conservative F2P combat upgrades: equip owned gear first, retain a 1,000 gp reserve, buy one best-eligible scimitar before body armour, and use verified Zeke/Horvik shop routes before the GE fallback.
- [ ] Convert fishing, woodcutting, mining, smithing, combat, and quests to rely on supply requirements instead of task-local banking. Combat is partially converted: gear and food are planner-visible, but bank/equip execution still lives in `CombatTask`.
- [ ] Add item-specific routes for more F2P shops, spawns, gathering methods, NPC drops, and quest rewards.

### Supply Route Catalog Notes

- Add item routes in `activities/supply/SupplyCatalog`.
- Always prefer verified sources over broad assumptions. Bank is automatic; GE is the generic tradeable fallback; shop and ground routes should be item-specific.
- Current known route examples: eggs from the Lumbridge farm ground spawn, general-store containers/tools from Lumbridge General Store, fishing tools from Gerrant in Port Sarim, axes from Bob in Lumbridge, pickaxes from Nurmof, bows/arrows from Lowe, staves from Zaff, and scimitars from Zeke.
- Starter-tool policy: request the cheapest usable tool first. For a bronze pickaxe, try Bob in Lumbridge, then Nurmof in the Dwarven Mine, then the GE when permitted. If those routes fail, try higher shop-stocked tiers the account can afford. Shop routes use verified default-stock prices so the planner only raises the coins actually needed; unsupported shop tiers use the GE route instead of trying a shop that cannot stock them.
- Combat-upgrade policy: preserve 1,000 gp across inventory and bank. When a combat strategy is selected, first equip the strongest relevant gear already owned. Otherwise, buy only one upgrade at a time: the highest eligible affordable scimitar first, then a platebody only for non-chicken combat. Zeke supplies bronze through mithril scimitars; Horvik supplies bronze through mithril platebodies. Every listed shop purchase retains the generic GE route as its fallback.
- Future route types are already modeled: `SKILL_ACTIVITY`, `QUEST_REWARD`, and `OTHER`. Use these when an item should be gathered by doing a skilling method, obtained from a quest step, or acquired by a special interaction instead of purchased.
- When a route needs coins and the account is short, the supply strategy emits a money requirement so `MoneyMakingActivity` can choose a F2P method.
- The starter money route deliberately shares the existing shop/GE policy toggles rather than adding separate selling config. That keeps the route surface small: enable shops for the local general-store fallback, GE for market sales, or both for planner choice.
- Mining, woodcutting, and fishing now advertise item production for items already named on their methods. Add more produced-item fields as new activities mature.

## Phase 3: Task Contracts

- [x] Add richer task status/block reasons.
- [x] Centralize terminal task status handling and treat `BLOCKED` as planner-visible.
- [x] Feed task stop reasons into `AccountMemory`.
- [x] Apply reason-specific cooldown penalties in `PlannerScorer`.
- [x] Add pre-tick replan reason hook via `Task.getReplanStopReason`.
- [x] Standardize task phase behavior and stuck recovery across current production, questing, and money-making tasks. Banking, combat, supply, fishing, mining, woodcutting, cooking, smelting, forging, Cook's Assistant, and Doric's Quest now use bounded recovery in their highest-risk phases.
- [ ] Add shared helpers for gather, production widget, combat loadout, and bank loadout tasks.
- [x] Add planner-visible task failure memory so failed methods cool down instead of looping forever.
- [x] Add specific stop reasons for supply and money-making failures.
- [x] Convert non-supply tasks to report specific `TaskStopReason` values for known terminal failures.
- [x] Add bounded combat recovery for combat style setup, attack click failures, and missing target searches.
- [x] Make bank-supply withdrawals recover from a full inventory by depositing first, then withdrawing the required item or coins instead of repeatedly attempting an impossible withdrawal.
- [x] Add no-gear chicken fallback for fresh-F2P combat bootstrap.
- [x] Propagate nested banking `BLOCKED` results to parent combat and skilling tasks.
- [x] Treat task-creation failures as recoverable planner outcomes.
- [x] Reject strategies with unmet prerequisites when no prerequisite route is runnable.
- [x] Keep a prerequisite chain attached to its original objective strategy. After a supply step completes, the planner now advances the same recipe/loadout instead of freely switching to another eligible strategy between ingredients.
- [x] Let an active task finish its own terminal cleanup before the runner advances a satisfied prerequisite. This prevents a withdrawal from being cut off before its banking task closes the bank UI.
- [x] Use a fixed item-acquisition order: banked item, gatherable/ground source, verified shop, then Grand Exchange. A gatherable resource such as coal now uses the owned pickaxe and Mining route before a money-making/GE dependency is considered.
- [x] Money-making now earns only the shortfall after inventory and bank coins are counted. The later supply task combines those coins by withdrawing the banked portion before purchasing.
- [x] Combat closes a leftover bank interface before changing combat style, walking, or attacking.

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
- [x] Let the planner choose among multiple F2P locations for fishing, woodcutting, and mining. Each method/location pair is scored independently by travel distance, receives only a 0-2 point variation, and gets its own cooldown when the resource cannot be found.
- [x] Reduce planner work per pass: evaluate each goal, requirement, activity provider, and strategy once; reuse one open-bank cache snapshot for a short interval; and log only planner passes that exceed 250 ms.
- [x] Bound planner debug output to one summary per pass. The previous per-goal and per-strategy trace caused a measured 56.9-second startup decision with debug logging enabled.
- [ ] Add richer planner explanations to overlay/debug output: top candidate list, lost-candidate reasons, and prerequisite chain.

### F2P Gathering Location Catalog

- Fishing: shrimp at Lumbridge Swamp and Draynor, fly fishing at Lumbridge River and Barbarian Village, and lobster at Musa Point.
- Woodcutting: normal trees at Lumbridge, Varrock, and Draynor Manor; oaks at Varrock, Draynor, and Lumbridge; willows at Draynor, Edgeville, and Port Sarim.
- Mining: tin and copper at Varrock East, Lumbridge Swamp, and Rimmington; iron at Varrock East, Al Kharid, and Rimmington; coal at Falador North.
- A gathering task now keeps its selected location for its entire run. If the expected resource stays unavailable after bounded checks, it reports `RESOURCE_NOT_FOUND`; the planner cooldown applies only to that method/location strategy and can select another valid location.

## Phase 4.5: Config And Overlay

- [x] Group config into General, Overlay, Skill Targets, Money, and Quests sections.
- [x] Add overlay visibility and detailed/compact controls.
- [x] Add level ranges to skill target settings.
- [x] Treat skill target `0` as disabled in goal building.
- [x] Remove per-skill weight controls. Build each configured skill and quest goal with a deterministic profile-specific priority in a narrow range, so user targets drive progression and route quality remains dominant.
- [x] Add route policy controls for GE, shops, and ground pickups.
- [x] Add a read-only `MntnBuilderOverlayState` so overlay rendering does not depend on loose script fields.
- [x] Queue and debounce config changes so planner/cache work runs only on the script worker, not RuneLite's config UI thread.
- [x] Queue overlay skip requests onto the script worker; never run planner or task mutations from the overlay mouse callback.
- [x] Add a direct test-override selector for current gathering, cooking, combat, smithing, and money-making methods; Normal planner restores ordinary account goals.
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
- [x] Add initial F2P methods: chicken feathers, cowhides, raw shrimps, logs, copper ore, iron ore, bronze bars, and iron bars.
- [x] Let money-making run as both fallback and optional account goal.
- [x] Use profit and supply needs in planner scoring.
- [x] Restrict combat loot to self-owned stacks worth more than 100 gp, with an active money-method item exception; collect bones only for unfinished Prayer training and bank full loot inventories before resuming combat.
- [ ] Add more verified F2P methods after the chicken bootstrap smoke test: clay or low-level ore, cowhide with food, and gathered logs/fish with route-aware sales.

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
- [x] `:client:compileJava` passes after replacing skill weights with profile goal preferences.
- [x] `:client:compileJava` passes after fresh-F2P supply route policy controls.
- [x] `:client:compileJava` passes after immediate startup replan and no-gear chicken bootstrap.
- [x] `:client:compileJava` passes after no-runnable-plan diagnostics.
- [x] Focused `:client:runUnitTests --tests net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlannerTest` passes.
- [x] Focused `:client:runUnitTests --tests net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlannerTest --tests net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestCatalogTest` passes.
- [x] Focused `:client:runUnitTests --tests net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlannerTest --tests net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestCatalogTest --tests net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategyTest` passes.
- [x] Focused planner, quest catalog, and combat strategy tests pass after config/overlay cleanup.
- [x] Focused builder script, planner, quest catalog, and combat strategy tests pass after profile goal preferences.
- [x] Focused builder script, planner, quest catalog, combat strategy, and supply route policy tests pass after fresh-F2P route policy controls.
- [x] Focused builder script, planner, quest catalog, combat strategy, and supply route policy tests pass after immediate startup replan and no-gear chicken bootstrap.
- [x] Focused builder script, planner, quest catalog, combat strategy, and supply route policy tests pass after no-runnable-plan diagnostics.
- [x] Planner unit tests cover content filtering, recursive money fallback, cooldown penalties, item-producing activity selection, and session flavor selection.
- [x] Planner unit tests cover fresh-F2P combat targets selecting unarmed chickens when no gear/coins are available.
- [x] Planner unit tests cover the banked-bronze-axe/31-coin fresh-account combat bootstrap fallback.
- [x] `TaskActionGuard` unit tests cover retry cooldowns, attempt exhaustion, and confirmation reset behavior.
- [x] Builder script tests cover skill target disabling and deterministic, bounded profile goal preferences.
- [x] Supply activity tests cover disabling GE and ground-pickup routes by policy.
- [x] Combat strategy unit tests cover no-gear chicken bootstrap, banked weapon requirements, risk-based starter food requirements, missing-food quantities, and prayer melee-style selection.
- [x] Focused combat and money-making unit tests pass after self-owned value-filtered loot, Prayer-only bones, and full-inventory combat banking changes.
- [x] Focused builder config, test-override, combat, and money-making unit tests pass after moving config planning work off the UI event thread.
- [x] Focused supply catalog, supply policy, and planner tests pass after starter-tool shop pricing and fallback corrections.
- [x] Focused combat strategy, supply catalog, supply policy, and planner tests pass after conservative combat gear purchasing changes.
- [x] Focused builder script, planner, and combat tests pass after moving overlay skip/replan handling onto the script worker.
- [x] Focused training-location activity, money-making activity, and planner tests pass after adding multi-location F2P gathering selection.
- [x] Focused planner, builder-script, and training-location tests pass after planner-pass performance cleanup.
- [x] Focused planner and Builder-script tests pass after bounding planner debug output.
- [x] Focused planner, Builder-script, and supply tests pass after preserving the original strategy across multi-item prerequisite chains.
- [x] Focused planner, Builder-script, combat, and money-making tests pass after task-cleanup, source-order, and cash-shortfall cleanup.
- [x] Focused Builder script and planner tests pass after Break Handler login recovery is added.
- [x] `:client:compileJava` and focused Builder-script/TaskActionGuard tests pass after yielding to every active Break Handler V2 state.
- [x] Focused supply, planner, and Builder-script tests pass after full-inventory bank-withdraw recovery is added.
- [x] `:client:compileJava` passes after the core audit: client-thread context reads, non-blocking banking/supply confirmation, production-widget recovery, and quest-task recovery.
- [x] Focused `TaskActionGuard`, supply, planner, combat strategy, and Builder-script tests pass after the core audit.
- [x] Static-wait audit: no `sleep` or `sleepUntil` calls remain in the Builder package; raw RuneLite client reads are contained in `AccountContext` client-thread wrappers.
- [ ] Runtime-test config update responsiveness and every direct test override in the live client.
- [x] Quest metadata unit test covers supported quest requirements and rewards.
- [ ] Runtime tests validate combat banking, supply acquisition, target selection, and task recovery.
- [ ] Retest startup bank warming on a fresh F2P account after the banking recovery fix.

## Next: Bounded Startup Planning

### Runtime Evidence (2026-09-08)

- [x] Observed a live startup for three minutes through the Agent Server. The account was logged in and unpaused, startup banking had completed, and the builder remained at `Planning / Selecting first task after bank cache` with no task or plan selected.
- [x] Confirmed the preceding planner pass took 408,325 ms. The client log contained repeated GE Tracker connection timeouts during the pass.
- [x] Identified synchronous live price fetching in `MoneyMakingStrategy` and zero-price `SupplyStrategy` routes. `Rs2GrandExchange.getOfferPrice` waits on an HTTP request with a 10-second timeout and is currently called while the planner scores candidates.
- [x] Identified repeated synchronous client-thread reads from strategy/goal evaluation as a secondary startup cost. Each read may wait up to 10 seconds when the client thread is unavailable.

### Implementation Plan

- [x] Make every planner `canExecute`, `requirements`, `score`, and estimate calculation local and bounded. Builder planning no longer calls the GE Tracker; remaining game reads use one client-thread snapshot per planner operation.
- [x] Replace live GE values in planner-time money and supply scoring with conservative Builder catalog values. The same bounded estimates now seed Builder GE offers, removing GE Tracker HTTP waits from tasks as well.
- [x] Add a small builder-owned, in-memory planning snapshot captured once per pass: relevant skill levels, combat level, and location. Reuse it for that pass instead of repeatedly crossing to the client thread. It remains F2P-focused; the old full quest snapshot is still avoided on F2P passes.
- [x] Persist one last-known broad goal per local Microbot profile in `~/.runelite/microbot/mntn-builder/`, so startup direction survives plugin, client, and IntelliJ debug restarts. The record contains only a configuration fingerprint, goal name, and timestamp; it expires after 30 days.
- [x] After bank warming, resolve the next valid step for the remembered goal before considering unrelated goals. The targeted evaluation retains normal prerequisite handling (for example, buy or withdraw a missing tool); if the goal is complete, blocked, stale, or incompatible with the current config, immediately use the full planner.
- [x] Add aggregate timing diagnostics for snapshot capture and candidate evaluation, emitted only when a stage exceeds 250 ms. Per-candidate debug logs remain disabled.
- [x] Add unit coverage for local price estimates and durable startup-direction cache reuse/rejection, including mismatched profile/configuration protection. Focused Builder planner, script, guard, bank-view, cache, and supply tests pass with `:client:compileJava`.
- [x] Simplify smelting to one verified production flow: interact with a reachable furnace, wait up to five seconds for the furnace interface, pause for a randomized `800-3000 ms`, press `SPACE`, and wait for an ingredient count reduction. Live Agent Server tracing showed the generic action guard could remain in `WAITING` while the production interface was closed, so smelting now owns two local, explicit recovery counters instead: three failed interface opens or three failed production confirmations replan. This avoids an indefinite `RUNNING` state while preserving bounded recovery.
- [x] Multi-item bank withdrawals now request and confirm one item at a time while keeping the bank open. A copper/tin loadout no longer starts both asynchronous withdrawals in the same tick or closes between items; it advances only after each requested amount is visible in inventory.
- [x] Add a shared pre-task inventory preparation step for fishing, mining, woodcutting, cooking, and smelting. Before travel, it keeps only the selected strategy's declared inputs and uses the bounded banking task to deposit unrelated inventory items. Full inventories are always prepared even if an inventory cache is briefly stale; equipment is intentionally left unchanged.
- [x] Smelting performs a short second animation/movement check after an idle observation before it reopens the furnace or presses `SPACE`. This absorbs brief gaps between bars without interrupting active production.
- [x] Freeze the warmed `BankView` cache for each planner pass. The live thread dump showed recursive candidate scoring repeatedly waiting in `Rs2Bank.isOpen()` through a client-thread bank-pin widget read; planner bank counts are now cache-only, while banking tasks retain explicit refreshes after every bank action.
- [x] Add a `BankView` regression test proving planner reads do not probe the live bank widget. `:client:compileJava` and focused Builder planner, script, bank-view, and guard tests pass.
- [x] Freeze inventory counts, occupied slots, and food presence in the same client-thread snapshot as planner skills and location. Live thread inspection showed a planner stuck in `Rs2Inventory.itemQuantity()` while recursively evaluating money prerequisites; planner-time inventory reads are now map lookups and cannot trigger a second client-thread inventory query.
- [x] Clear Web Walker state when a Builder task ends with `TRAVEL_FAILED` and before every new Builder plan begins. A stale route can no longer bleed from a failed, skipped, or completed task into the next activity; the next task starts a fresh walker route while existing task-level bounded travel guards still decide when to replan.
- [x] Add an `InventoryView` regression test for the frozen planner inventory read.
- [x] Add unit coverage for task-inventory preparation decisions: a matching loadout starts immediately, while unrelated or full inventory enters banking before the productive task starts.
- [ ] Re-run the live Agent Server startup trace. Acceptance target: on a matching cached direction, a visible task/goal selection within two script ticks after bank warming; when it cannot produce a valid next step, one full planner pass follows without a planning loop.
