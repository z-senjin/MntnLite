# Account Builder Core Guide

This is the short version of how the Mntn AIO Account Builder thinks and runs.

The current method catalog is F2P-focused. The core is intentionally generic so
members methods can use the same model later.

## The Main Idea

The Builder does not follow one long, fixed route.

Instead, it repeats this small loop:

1. Read the current account state.
2. Find an unfinished goal.
3. Choose one useful activity for that goal.
4. Run one concrete task.
5. Read the account state again and choose again.

The important rule is that a task does **one piece of work**. It does not remember
that it is working on a larger chain. For example:

```text
Goal: Smithing 20
  -> no ore available
  -> choose a mining task
  -> mining finishes
  -> planner runs again from the live account state
  -> choose smelting if it is now the best next task
```

This keeps recovery simple. A task that fails, runs out of supplies, or completes
never needs to resume a hidden parent task.

## The Five Layers

```text
Config -> Goals -> Requirements -> Activities / Strategies -> Tasks
```

### 1. Config

`MntnBuilderConfig.java` is the user-facing setup.

The player sets targets such as Attack 30, Mining 50, or Firemaking 52. A target
of `0` means that goal is disabled. The config also controls F2P mode, supply
routes, session flavor, overlay options, and direct test overrides.

Crafting uses the same target model. The current F2P catalog covers gem cutting,
gold and sapphire jewellery, and silver tiaras; leather tanning/crafting and
pottery are intentionally separate future additions.

### 2. Goals

Goals live in `core/goals/`.

A goal answers two questions:

```text
Is this complete yet?
What requirement must be satisfied to make progress?
```

Examples:

- `SkillGoal`: reach a configured skill level.
- `QuestGoal`: complete a supported quest.

`MntnBuilderScript.buildGoals()` creates the active list from config. Each local
account profile gets small, stable goal-priority variation so two accounts do not
always choose goals in the same order.

### 3. Requirements

Requirements live in `core/requirements/`.

A requirement describes the next missing thing, not the whole account plan.

Examples:

- Need a level.
- Need an item.
- Need equipped gear.
- Need a quest state.

Requirements expose possible ways to satisfy themselves. An item requirement can
be satisfied by a producer activity, a bank withdrawal, a shop, the Grand
Exchange, or a ground pickup when those routes are enabled.

### 4. Activities And Strategies

Activities live in `activities/` and group related ways to make progress.

Examples:

- `CombatActivity`
- `MiningActivity`
- `CraftingActivity`
- `WoodcuttingActivity`
- `SupplyActivity`

A strategy is one specific method inside an activity. It knows:

```text
What it is called
Whether the account can use it now
What it needs
How good it is
Which Task runs it
```

Examples:

- Fight chickens while training Attack.
- Mine coal north of Falador.
- Smelt mithril bars.
- Cut uncut sapphires or make gold rings.
- Tend a Forester's Campfire with willow logs.

Strategies should make choices. They should not contain click-by-click game play.

### 5. Tasks

Tasks live in `tasks/` and perform the game actions.

A task owns a small state machine such as:

```text
BANKING -> WALKING -> DOING -> BANKING
```

Examples:

- `CombatTask` handles loadout, equipping, combat style, travel, fighting, food,
  and eligible loot.
- `BankingTask` handles walking to a bank, opening it, deposits, withdrawals, and
  confirmation.
- `FiremakingTask` handles one clean log loadout, starter fire/campfire creation,
  and tending.

Tasks return a `TaskStatus` each tick:

| Status | Meaning |
| --- | --- |
| `RUNNING` | The task is still working. |
| `COMPLETE` | The task reached its normal end. |
| `REPLAN` | The task cannot continue; choose a fresh task. |
| `BLOCKED` | An external condition prevents progress, such as being logged out. |

Each unsuccessful result also carries a `TaskStopReason`. The planner remembers
recent failures briefly so it does not recreate the same broken choice every tick.

## Runtime Flow

`MntnBuilderScript.java` is the top-level driver. Its normal flow is:

```text
Start
  -> wait for a stable login
  -> warm the bank cache
  -> build a fresh account snapshot
  -> ask AccountPlanner for a plan
  -> clean inventory/equipment before the selected task
  -> tick that task until it completes, replans, blocks, or its commitment ends
  -> plan again from current account state
```

The startup bank warm is important. It gives the planner a reliable view of
banked tools, gear, food, ores, and logs before it starts choosing work.

Before a normal task begins, `TaskInventoryPreparationTask` clears unrelated
inventory and equipment. The selected task then owns its own loadout. This avoids
starting mining, fishing, or woodcutting with a full inventory of unrelated items.

Supply purchases are also self-contained. A shop or Grand Exchange `SupplyTask`
checks the total coins first, withdraws the available bank coin stack when needed,
then completes the purchase and collection. The planner selects that one supply task;
it never creates a disconnected `withdraw coins` task that could reroll into another
goal before the purchase happens.

`MoneyRequirement` remains available only for a future direct cash-in-inventory goal.
It is not a purchase-budget mechanism.

## How Planning Chooses Work

`core/planner/AccountPlanner.java` does one bounded planning pass.

It follows this order:

1. Ignore completed goals.
2. Look for useful productive work that can start now.
3. Score and rank those choices.
4. Only when no productive work is ready, choose one missing prerequisite.
5. Run that acquisition task, then plan again.

The planner does not try to acquire every future item in advance. It only solves
the next missing need.

`PlannerScorer.java` applies the common scoring rules. A strategy's own `score()`
adds method-specific value. Session flavor adds a small preference, but it cannot
override a missing requirement or make an unusable task valid.

`AccountSnapshot`, `InventoryView`, and `BankView` make planning reads cheap and
consistent for one pass. Do not put waits, web requests, or slow live lookups in
strategy scoring or requirement checks.

## Banking And Equipment

The Builder uses a clean-loadout contract:

```text
Before a new task: bank unrelated inventory and equipment.
Inside a task: withdraw only what that task needs.
After a bank action: use a fresh tick before judging the new loadout.
```

Combat is intentionally flexible:

- A weapon is required except for the chicken bootstrap route.
- Armor is optional, but the task equips the best owned, level-valid F2P gear.
- Gear selection supports bronze through rune.
- Missing upgrades should not prevent a safe combat task from running.

## Recovery Rules

The Builder should recover through a small number of clear rules:

- A bank task checks that the bank is open and retries bounded actions.
- A travel task has bounded walk attempts, then returns `TRAVEL_FAILED`.
- A production task waits for a real state change, then returns a specific failure
  instead of looping forever.
- An active animation or movement means “do nothing this tick.” It never means
  “click again.”
- A task that cannot continue returns `REPLAN`; it does not make a new planner
  decision itself.
- The next plan is always based on the live account state.

`TaskActionGuard` is the standard helper for bounded retry/confirmation behavior.
Use it for actions that need a short retry window. Reset it when the expected game
state change is observed.

## Where To Make Changes

| You want to change... | Start here |
| --- | --- |
| Config option or target | `MntnBuilderConfig.java` |
| Which goals exist | `MntnBuilderScript.buildGoals()` and `core/goals/` |
| What an activity can provide | its class in `activities/` |
| Which method is best | the strategy's `canExecute()` and `score()` |
| A method's location, items, or level | that strategy's method data/enum |
| Clicks, banking, travel, or recovery | the relevant class in `tasks/` |
| Common planner priority | `core/planner/PlannerScorer.java` |
| Supply routes | `activities/supply/SupplyCatalog.java` |
| Overlay/status text | `ui/` and `MntnBuilderRuntimeStatus` |

## Adding A New Method

For another method within an existing skill, prefer this order:

1. Add the method data to the existing strategy enum or catalog.
2. Make its level, inputs, location, and score explicit.
3. Reuse the existing task if the behavior is the same.
4. Add a small task phase only when its game interaction is genuinely different.
5. Add a focused unit test for selection or method metadata.
6. Test it through the config test override before relying on the normal planner.

Create a new task class only when the game loop is materially different. For
example, a different tree location belongs in `WoodcuttingStrategy`; it does not
need a new `WoodcuttingTask`.

## Practical Debugging

Use the Builder overlay first. It shows the current goal, requirement, activity,
strategy, task phase, status, stop reason, and score.

With the Agent Server enabled, this read-only endpoint mirrors the same state:

```text
GET /mntn-builder/status
```

When a task is stuck, check these in order:

1. Is the player logged in and not paused by a break handler?
2. What exact task phase is shown?
3. Is the bank/interface/object actually open or visible?
4. Is the player already moving or animating?
5. Did the needed inventory/equipment change after a bank action?
6. Did the task return a stop reason that explains the replan?

## Keep It Simple

Good Builder changes usually follow these rules:

- Put **what to do** in strategies and **how to do it** in tasks.
- Let one task complete one piece of work, then reroll from live state.
- Prefer one verified game-state check over many speculative retries.
- Keep banking, travel, and production phases explicit.
- Do not make new global planner state to solve a local task problem.
- Do not add a new abstraction until an existing task/strategy pattern cannot
  express the behavior clearly.

For the running roadmap and completed work, see `BUILDER_PLAN.md` in this same
directory.
