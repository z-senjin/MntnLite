# Installation

## Download A Release

Download the latest shaded release jar from https://github.com/chsami/microbot/releases.

Nightly builds are development builds. Use a release build unless you are intentionally testing new changes.

## Java

Install Java 17+ and run the shaded jar:

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar
```

## Jagex Account Flow

1. Log in with the Jagex Launcher once. This creates the account token used by RuneLite-compatible clients.
2. Close the launcher/client after a successful login.
3. Open the Microbot shaded jar. It should prompt with the Jagex account login flow.

Video walkthroughs:
- Jagex account setup: https://www.youtube.com/watch?v=ga-lg1oAnhM
- Java/client launch: https://www.youtube.com/watch?v=EbtdZnxq5iw

## Jagex Launcher Replacement

Replace the official `RuneLite.jar` with the Microbot jar and keep the filename expected by the launcher. Then start the Jagex Launcher and select RuneLite.

## Linux

- Install Java 17+ from your package manager, then run the shaded jar with `java -jar`.
- For Jagex accounts, Bolt can launch a custom RuneLite jar: https://github.com/Adamcake/Bolt
- You can also extract the RuneLite AppImage and replace `RuneLite.jar` with the Microbot jar.

## Reporting A Problem

Open the Microbot side panel (the Community Plugins button), then click **Copy diagnostics** below the version at the bottom. Paste the copied text into your Discord post or GitHub issue.

The report lists the Microbot/RuneLite versions, commit, build origin, Java/OS, safe mode, GPU state, walker planner and settings, and loaded external plugin versions. Builds without official release metadata show `Build origin: unknown` or `custom`. It excludes account and player names, chat, credentials/tokens, and file paths.

## Recovering From A Freeze Or Crash

You do not need to rename or delete `.runelite/microbot-plugins`.

- **After a freeze, crash or force-close:** the next start shows a recovery prompt. It lists what Microbot recorded, such as a plugin that was still starting, plugin start or compatibility failures with their versions, and the external plugins that were running. If nothing was recorded, the prompt says the cause is unknown. You can:
  - **Start normally.** This also happens automatically after 20 seconds.
  - **Start in safe mode.** External Hub and sideloaded plugins and the GPU plugin stay off for this one session.
  - **Disable \<plugin\> and start.** This option appears when a plugin was identified. It turns that plugin off in the plugin list and starts with your other plugins.
  - **Copy details** to share in a bug report.
- **While the client still responds:** click **Safe mode next start** below **Copy diagnostics** in the Microbot side panel, then restart. Click it again to cancel.
- **From the command line:** `--safe-mode` turns on safe mode for every start that uses the option. Unlike the one-time options above, it also switches the GPU plugin off in your settings.

The one-time safe mode keeps your plugin files and settings, including GPU, and the following start is normal. A plugin disabled from the prompt keeps its files; turn it back on in the plugin list. Set `-Dmicrobot.recovery.prompt=false` to skip the prompt, or `-Dmicrobot.recovery.promptSeconds=<n>` to change the countdown.

**Are you stuck? Join our [Discord](https://discord.gg/zaGrfqFEWE) server.**
