\# Implement Mntn mining strategies



\## Objective



Implement and test these two classes:



1\. CopperTinMiningStrategy.java

2\. IronMiningStrategy.java



Expected package:



net.runelite.client.plugins.microbot.mntn.builder.tasks



Expected directory:



src/main/java/net/runelite/client/plugins/microbot/mntn/aio/strategies/skilling/mining/



Before implementation, inspect the repository. If the existing project uses a

slightly different source root or package, follow the existing project rather

than creating duplicate packages.



\## Existing planning architecture



The strategies must integrate with:



\- MntnAIOBuilderScript

\- Goal

\- GoalType

\- AccountStrategy

\- StepResult

\- AccountContext

\- Plan

\- Planner



Do not replace this architecture.



\## Reference implementation



Study the read-only Microbot mining plugin here:



.reference/Microbot-Hub/src/main/java/net/runelite/client/plugins/microbot/mining



Use the reference to determine the correct current APIs for:



\- Finding rocks

\- Interacting with rocks

\- Detecting whether the player is mining

\- Checking animations

\- Walking

\- Checking inventory capacity

\- Opening and using banks

\- Depositing ores

\- Preserving a pickaxe

\- Selecting available pickaxes

\- Waiting for actions without blocking the scheduler

\- Handling unavailable or depleted rocks



Adapt relevant patterns to AccountStrategy. Do not copy the entire plugin.



\## Shared behavior



Both strategies must:



\- Implement AccountStrategy.

\- Support only SKILL\_LEVEL goals for Skill.MINING.

\- Return COMPLETE when goal.isComplete(context) becomes true.

\- Return REPLAN if required equipment or every configured location becomes

&#x20; unavailable.

\- Use a small internal state machine.

\- Implement reset() by restoring the initial state and clearing transient

&#x20; location or target state.

\- Avoid separate threads and schedulers.

\- Avoid blocking loops.

\- Avoid Thread.sleep.

\- Perform one small action per tick.

\- Include a useful getName() value containing the selected method/location.

\- Be compatible with the project's configured Java version.

\- Use actual Microbot APIs available in this repository's dependencies.



\## Copper/tin requirements



CopperTinMiningStrategy must:



\- Support a Mining skill-level goal.

\- Be available from Mining levels 1 through 14.

\- Require a usable pickaxe.

\- Return REPLAN when Mining reaches level 15, unless the configured Mining goal

&#x20; has already completed.

\- Mine copper and/or tin using the same inventory policy already established by

&#x20; this project.

\- Bank or drop ores according to the existing Mntn configuration. If no such

&#x20; configuration exists, default to banking and clearly isolate that decision so

&#x20; it can become configurable later.

\- Have at least one real usable mining location.

\- If the reference plugin contains compatible location definitions, reuse or

&#x20; adapt those definitions rather than inventing coordinates.



\## Iron requirements



IronMiningStrategy must:



\- Support a Mining skill-level goal.

\- Be available at Mining level 15 and higher while the goal remains incomplete.

\- Require a usable pickaxe.

\- Mine iron.

\- Bank or drop ore according to existing Mntn configuration, defaulting to

&#x20; banking when no option currently exists.

\- Support multiple real iron locations when the reference plugin provides them.

\- Store locations in preference order.

\- Choose the first location whose requirements currently pass.

\- Retain the selected location rather than recalculating it on every tick.

\- Reevaluate location selection after banking or when the selected location

&#x20; becomes invalid.



\## State-machine expectation



Use states similar to:



\- PREPARE

\- TRAVEL\_TO\_MINE

\- MINE

\- TRAVEL\_TO\_BANK

\- BANK



You may adjust the names to match existing project conventions.



PREPARE:



\- Validate the pickaxe.

\- Select a location.

\- Withdraw or equip required items if the existing utilities support this.



TRAVEL\_TO\_MINE:



\- Walk toward the selected mining location.

\- Transition only after reaching the area.



MINE:



\- Do nothing if the player is already mining.

\- Find a valid nearby rock using current Microbot APIs.

\- Interact with no more than one target per tick.

\- Transition toward banking when the inventory is full.



TRAVEL\_TO\_BANK:



\- Walk toward the selected bank.



BANK:



\- Open the bank if necessary.

\- Deposit mined ores without depositing the required pickaxe.

\- Reevaluate the preferred location after banking.

\- Return to TRAVEL\_TO\_MINE.



\## Pickaxe handling



Inspect AccountContext.hasUsablePickaxe() and the reference plugin.



If AccountContext does not yet implement this correctly, make the smallest

necessary improvement so it:



\- Checks inventory and equipped items.

\- Checks relevant Mining requirements.

\- Does not claim an unusable pickaxe is usable.

\- Does not require the pickaxe to be equipped when it can be used from inventory.



Do not create a complete equipment-management framework.



\## Location helper



First inspect whether the project already has a location abstraction.



If an appropriate helper exists, reuse it.



If none exists, you may create one small MiningLocation.java helper containing:



\- Location name

\- Mine area or destination

\- Bank area or destination

\- A pure availability requirement

\- Any rock identifiers required for that location



Do not introduce a generic dependency graph or large location framework.



\## Planner integration



Confirm that MntnAIOBuilderScript registers strategies in this order:



1\. IronMiningStrategy

2\. CopperTinMiningStrategy



For a Mining level 60 goal, expected behavior is:



\- Levels 1-14 select CopperTinMiningStrategy.

\- At level 15 CopperTinMiningStrategy returns REPLAN.

\- Planner then selects IronMiningStrategy.

\- IronMiningStrategy continues until level 60.

\- At level 60 the goal completes.



Only modify MntnAIOBuilderScript if registration is absent or incorrect.



\## Automated tests



Use the project's existing test framework.



Add focused tests for as many of these behaviors as the current architecture

allows without requiring a logged-in game client:



\- Copper/tin supports Mining goals.

\- Copper/tin rejects non-Mining goals.

\- Iron supports Mining goals.

\- Iron rejects non-Mining goals.

\- Copper/tin availability ends at level 15.

\- Iron availability begins at level 15.

\- A completed goal produces COMPLETE.

\- Copper/tin produces REPLAN at level 15 for a higher target.

\- reset() returns each strategy to its initial state.

\- Planner selects copper/tin below level 15.

\- Planner selects iron at level 15.

\- Planner selects nothing after the Mining goal completes.

\- Location selection uses the first available location.



Do not add a new mocking dependency unless there is no reasonable existing

testing mechanism. Do not substantially redesign AccountContext just to make a

single test possible.



If live Microbot static APIs prevent safe unit tests, test the pure selection

and support behavior that can be isolated, ensure the project compiles, and

write the remaining checks as a manual test plan.



\## Required verification



Determine the appropriate Gradle tasks from the repository, then run the

equivalent of:



.\\gradlew.bat compileJava

.\\gradlew.bat test



Run a broader build only if it is safe and appropriate:



.\\gradlew.bat build



Inspect:



git status --short

git diff --check

git diff



\## Deliverables



\- CopperTinMiningStrategy.java

\- IronMiningStrategy.java

\- MiningLocation.java only if needed

\- Minimal AccountContext or script registration changes only if needed

\- Focused automated tests

\- agent-reports/mining-strategies-report.md



The report must include:



\- Files created

\- Files changed

\- Reference mining classes consulted

\- Strategy state transitions

\- Locations implemented

\- Commands executed

\- Compilation result

\- Test result

\- Remaining manual in-game test steps

\- Any blockers or assumptions

