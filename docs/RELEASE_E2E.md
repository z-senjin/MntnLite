# Release E2E Smoke Test

A live smoke test that runs the release candidate client against a real logged-in account before `development` is merged into `main`. That merge triggers `release.yml`, which publishes the jar. The test needs a display and a logged-in game account, so it cannot run in GitHub CI. It is a local gate.

The test reuses the existing test-mode plumbing: `TestRunnerPlugin` logs the account in, starts the hidden `Release Smoke Harness` plugin, and the harness writes its results under `~/.runelite/test-results/`. That is the same flow the [F2P web walker harness](F2P_WEBWALKER_HARNESS.md) uses.

## Account requirements

- An F2P account that has finished Tutorial Island, saved as the active Microbot profile.
- A few coins and a few junk items in the inventory. The bank check deposits one item and withdraws it again. It looks for, in order: Burnt meat, Burnt fish, Burnt bread, Cowhide, Egg, Bones, Ashes, Bowl, Pot, Jug, Feather. It prefers an item that already has a stack in the bank, because a full F2P bank (400 stacks) refuses new item types.
- No bank PIN, or the PIN saved in the profile.
- Any F2P location is fine to start from. The harness walks to each start point itself, using Lumbridge castle and bank, the Lumbridge castle doors, Varrock west bank and the Grand Exchange.
- Log in to an F2P world. Set `MICROBOT_E2E_WORLD` to one, for example `383`. Otherwise AutoLogin uses its own world settings, and it defaults to members worlds.

All actions are reversible and safe. The harness does no trading, drops nothing, places no Grand Exchange offers, does no combat and stays out of the Wilderness. Each check registers cleanups that put back what it changed: doors, bank contents, the open tab, the world, and Hub plugins it installed. If a cleanup fails, the check is marked failed.

## Run

```bash
DISPLAY=:0 MICROBOT_E2E_WORLD=383 scripts/run-release-e2e.sh quick
DISPLAY=:0 MICROBOT_E2E_WORLD=383 scripts/run-release-e2e.sh full
DISPLAY=:0 MICROBOT_E2E_WORLD=383 scripts/run-release-e2e.sh quick bank npc-dialogue
DISPLAY=:0 scripts/run-release-e2e.sh full --jar /path/to/microbot-2.6.30.jar
scripts/run-release-e2e.sh --list
```

By default the script builds `:client:shadowJar` and runs that jar, so the test exercises the shaded artifact rather than the Gradle classpath. Use `--jar` or `MICROBOT_E2E_JAR` to test the exact release candidate jar. It exits non-zero when any check fails or does not run.

| Variable | Meaning |
|---|---|
| `MICROBOT_E2E_HOME` | `user.home` for the client, for an isolated runtime home that holds the saved profile |
| `MICROBOT_E2E_WORLD` | World to log in to. `TestRunnerPlugin` logs in with the active profile instead of enabling AutoLogin |
| `MICROBOT_E2E_EXPECTED_VERSION` | Version the client must report. Defaults to `microbot.version` from `gradle.properties`; set it empty to skip the comparison |
| `MICROBOT_E2E_TOTAL_TIMEOUT_MS` | Total budget for all checks. Defaults to 15 min (quick) and 35 min (full) |
| `MICROBOT_E2E_HOP_WORLD` | Target world for `world-hop`. Defaults to the least crowded plain F2P world in the same region |
| `MICROBOT_E2E_HUB_PLUGIN` | Hub plugin for `hub-plugins`. Defaults to `AttackRangesPlusPlugin`, which only draws an overlay |
| `MICROBOT_E2E_GRADLE` | Build command, for example `flock <lock> systemd-run --user --scope -p MemoryMax=6G ./gradlew` |
| `MICROBOT_E2E_OUTPUT_DIR` | Result directory. Defaults to `<home>/.runelite/test-results/release-e2e` |
| `--fail-fast` | Stop at the first failure. `logout` still runs |

The client runs with telemetry disabled, so test runs do not end up in the production error reports.

## Checks

| ID | Tier | Covers |
|---|---|---|
| `boot` | quick | `microbot.version` matches the expected version, every built-in always-on plugin is active, and `client.log` has no unexpected `ERROR` lines since startup |
| `login` | quick | `LOGGED_IN`, player location known, world known, login finished within `loginTimeoutMs` of JVM start |
| `state-caches` | quick | At the Lumbridge castle hall: all skills plausible; inventory cache equals the client container; cached varp 173 and a varbit equal the client values; NPC, object, ground item and player queryables return results |
| `widgets` | quick | Clicks the Skills and Inventory tab stones (widget click, verified with `Rs2Tab`) and finds the "Total level" text with `Rs2Widget.findWidget` |
| `walker-transport` | quick | Short walk in the castle courtyard; walk through a door the harness closes first (castle entrance tower), which must end with `ARRIVED` on the far side; walk up the castle stairs to the bank on plane 2 |
| `bank` | quick | Nearest bank, `openBank`, `depositOne` and `withdrawOne` on one junk item, `closeBank`, inventory identical to before |
| `npc-dialogue` | quick | Talks to a Lumbridge banker, reads the text, picks "What is this place?", continues, then walks away to close the dialogue. The bank must not open |
| `object-interaction` | quick | Opens and closes (or closes and opens) a Lumbridge castle door and confirms it is back in its original state |
| `agent-server` | quick | `/state`, `/inventory`, `/widgets/describe` and `/skills` with the token, plus the opaque 404 for a request without it. Starts the plugin for the check if it is off |
| `walker-medium` | full | Lumbridge courtyard to Varrock west bank. The walker may teleport, and `maxStepJump` records any jump |
| `grand-exchange` | full | Opens the Grand Exchange through the clerk and closes it. No offers |
| `world-hop` | full | `Microbot.hopToWorld` to another plain F2P world and back |
| `hub-plugins` | full | Plugin Hub manifest loaded with valid entries; install, start, stop and uninstall the Hub plugin (start and stop only if it is already installed) |
| `script-lifecycle` | full | Starts and stops `StateMachineExamplePlugin` twice through `Microbot.startPlugin`/`stopPlugin`: the loop runs, no thread is still running script code after stop, the pool stays at the `Script` core size, and the script is in `StateMachineScript.getRegistry()` while running |
| `logout` | quick, always runs | Stops AutoLogin, logs out with `Rs2Player.logout`, then scans the whole run's `client.log` for unexpected `ERROR` lines |

The quick tier runs checks 1-9 and `logout`. The full tier adds the rest. Check ids passed on the command line run regardless of tier, in catalog order.

## Reading results

The script prints `summary.md`, a Markdown table you can paste into the release PR, and leaves `result.json` next to it:

- `verdict`, `exitCode`, plus `passed`, `failed`, `skipped` and `notRun` counts.
- `results[]`: `id`, `description`, `tier`, `status` (`PASSED`, `FAILED`, `SKIPPED` or `NOT_RUN`), `durationMs`, `detail` (why it failed or was skipped), `details` (what the check observed, such as walk states, door ids, counts and dialogue transcripts) and `cleanupErrors`.
- `checks[]`, `errors` and `logTail` keep the generic `TestResult` format used by the other harnesses.

`SKIPPED` means the account cannot exercise the check, for example the bank is full and no junk item has a bank stack, the profile has no bank PIN, or the Grand Exchange clerk refuses this account. It is not a failure, but say so in the PR. `NOT_RUN` comes from `--fail-fast` or from running out of the total budget, and it fails the run.

When a check fails, look at `detail`, then at `details` in `result.json`, then at `<home>/.runelite/logs/client.log`. Lines from the harness are tagged `[ReleaseE2E]` and run on `ReleaseE2E-<check>` threads. To rerun one check, pass its id: `scripts/run-release-e2e.sh quick bank`.

## Log allowlist

`boot` and `logout` fail on any `ERROR` line in the current run's `client.log`, from the last `starting up, args:` banner onward. Known harmless lines are listed in `ClientLogScanner.DEFAULT_ALLOWLIST`, and each entry has a reason. Keep the list short. Add an entry only for a line that is expected and harmless during a healthy run, never to hide a real failure, and give it a unit test in `ClientLogScannerTest`.

## Adding a check

1. Add a method to `ReleaseSmokeChecks` taking a `CheckContext`. Walk to a fixed start point first with `walkToStart`. Use `context.require(condition, message)` for assertions, `context.skip(reason)` when the account cannot run it, and `context.detail(key, value)` to record what you observed.
2. Register a cleanup with `context.onCleanup(name, action)` before changing anything in game. Cleanups run in reverse order after the body, including after a timeout, and a cleanup that throws fails the check.
3. Add it to `all()` with `ReleaseCheck.quick(...)` or `ReleaseCheck.full(...)`, an id, a one-line description and a timeout long enough for its setup walk. Keep `logout` last.
4. Add the id to `CHECK_IDS` in `scripts/run-release-e2e.sh` and to the table above.
5. Follow the client-thread rules: game reads go through `Rs2*` helpers or `runOnClientThreadOptional`, queryables use the `*OnClientThread` terminals, and waits use `sleepUntil` with a timeout.

## Release checklist

Before merging the release PR (`development` -> `main`), build the release candidate, run `scripts/run-release-e2e.sh full` (or `quick` at minimum) against it, and paste `summary.md` into the release PR. A failing check blocks the release until it is fixed or written up in the PR as a known issue.
