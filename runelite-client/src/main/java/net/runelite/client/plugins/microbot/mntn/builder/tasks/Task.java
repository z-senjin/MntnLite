package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

/**
 * The execution layer. A Task should be reusable/parameterized (e.g. FishingTask takes a
 * Method enum) rather than a new class per fish/tree/NPC. Tasks may keep their own small
 * internal phase state machine (WALK -> DO -> BANK) - that's fine, that's what a Task is for.
 * The planner should never know or care about a Task's internal phases.
 */
public interface Task {
    TaskStatus tick(AccountContext context);

    boolean needsReplan(AccountContext context);

    default String describe() {
        return getClass().getSimpleName();
    }
}
