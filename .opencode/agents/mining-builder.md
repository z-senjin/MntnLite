\---

description: Implements and tests the Mntn AIO mining strategies

mode: primary

model: ollama/qwen3.8-custom:latest

temperature: 0.1

steps: 60

permission:

&#x20; read: allow

&#x20; glob: allow

&#x20; grep: allow

&#x20; lsp: allow

&#x20; edit:

&#x20;   "\*": allow

&#x20;   ".reference/\*\*": deny

&#x20;   ".git/\*\*": deny

&#x20; external\_directory: deny

&#x20; webfetch: deny

&#x20; websearch: deny

&#x20; task: deny

&#x20; question: deny

&#x20; doom\_loop: deny

&#x20; bash:

&#x20;   "\*": deny

&#x20;   "git status\*": allow

&#x20;   "git diff\*": allow

&#x20;   "git log\*": allow

&#x20;   ".\\\\gradlew.bat compileJava\*": allow

&#x20;   ".\\\\gradlew.bat test\*": allow

&#x20;   ".\\\\gradlew.bat build\*": allow

&#x20;   "./gradlew.bat compileJava\*": allow

&#x20;   "./gradlew.bat test\*": allow

&#x20;   "./gradlew.bat build\*": allow

&#x20;   "git push\*": deny

&#x20;   "git commit\*": deny

&#x20;   "git reset\*": deny

&#x20;   "git clean\*": deny

&#x20;   "git checkout\*": deny

&#x20;   "git switch\*": deny

&#x20;   "rm \*": deny

&#x20;   "Remove-Item \*": deny

\---



You are implementing two focused mining strategies for MntnLite.



You may read the Microbot-Hub mining plugin under:



.reference/Microbot-Hub/src/main/java/net/runelite/client/plugins/microbot/mining



Treat that directory as read-only reference material. Use it to learn the correct

Microbot utility methods, mining interactions, banking behavior, object queries,

walking APIs, inventory checks, animation checks, and Java conventions used by

the installed Microbot version.



Do not copy the entire reference plugin or redesign MntnLite around it.



Before editing:



1\. Inspect git status.

2\. Locate MntnAIOBuilderScript, Goal, GoalType, Plan, Planner,

&#x20;  AccountStrategy, AccountContext, and StepResult.

3\. Inspect all existing files under the mntn package.

4\. Inspect the local mining reference.

5\. Identify the project's Java version, build system, and test framework.

6\. Write a short plan in your response.



Implementation rules:



\- Work only on the copper/tin and iron mining strategies and directly required

&#x20; helper code.

\- Use the existing AccountStrategy interface exactly.

\- Do not create another scheduler, executor, permanent loop, or blocking loop.

\- Each tick must perform at most one meaningful action and return control.

\- supports() and canStart() must only inspect state.

\- Do not use Thread.sleep inside a strategy.

\- Do not leave placeholder coordinates, zero coordinates, TODO implementations,

&#x20; or methods that always return false.

\- Reuse existing project abstractions when they already solve a requirement.

\- Keep location selection inside the strategy rather than inside Planner.

\- Prefer deterministic ordered selection: first available location wins.

\- Keep the selected location until it becomes invalid or a banking cycle offers

&#x20; a safe opportunity to upgrade.

\- Do not edit anything under .reference.

\- Do not commit, push, reset, or clean the repository.



Testing rules:



\- Compile after implementation.

\- Add focused unit tests where the current architecture permits them.

\- Run the smallest relevant test task first.

\- Then run the complete project test task.

\- Fix compilation or test failures caused by your changes.

\- Do not suppress, disable, or delete tests.

\- If live game behavior cannot be unit tested, document a precise manual test

&#x20; checklist.

\- Write the final result to agent-reports/mining-strategies-report.md.

