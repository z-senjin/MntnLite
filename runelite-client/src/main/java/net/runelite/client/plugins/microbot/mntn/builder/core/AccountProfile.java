package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.QuestGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.SkillGoal;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "What does this account want to become?" - doc section 4.
 *
 * Deliberately dumb/data-only: this class doesn't know how to plan, and it doesn't touch
 * Microbot/AccountContext directly - it just describes a target end-state. Keeping it
 * separate from AccountPlanner is what lets the same engine run different account builds
 * (doc: "the same engine can support different account builds").
 *
 * Priority isn't specified per-skill by the caller - it's randomized within a range at
 * build() time via Rs2Random, so two accounts built from the same profile definition don't
 * end up with identical goal-weighting behavior. If you need one goal to reliably win over
 * another regardless of randomness (e.g. "never let Combat outrank starving-off food"),
 * use the explicit-priority overload instead of leaving it randomized.
 */
public class AccountProfile {

    // Default priority jitter range applied to every .skill(...) call unless a caller
    // supplies an explicit priority. Tune with Builder.priorityRange(...) per-profile if one
    // account build should feel more/less "spread out" than another.
    private static final int DEFAULT_PRIORITY_MIN = 40;
    private static final int DEFAULT_PRIORITY_MAX = 60;

    private final Map<Skill, SkillTarget> skillTargets;
    private final List<Quest> quests;

    private AccountProfile(Map<Skill, SkillTarget> skillTargets, List<Quest> quests) {
        this.skillTargets = skillTargets;
        this.quests = quests;
    }

    public Map<Skill, SkillTarget> skillTargets() {
        return skillTargets;
    }

    public List<Quest> quests() {
        return quests;
    }

    /**
     * Converts this profile into the planner's Goal vocabulary.
     */
    public List<Goal> toGoals() {
        List<Goal> goals = new ArrayList<>();
        for (Map.Entry<Skill, SkillTarget> entry : skillTargets.entrySet()) {
            SkillTarget target = entry.getValue();
            goals.add(new SkillGoal(entry.getKey(), target.level, target.priority));
        }
        for (Quest quest : quests) {
            int priority = Rs2Random.between(DEFAULT_PRIORITY_MIN, DEFAULT_PRIORITY_MAX);
            goals.add(new QuestGoal(quest, priority));
        }
        return goals;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Pairs a target level with its (possibly randomized) priority for one skill. */
    public static final class SkillTarget {
        public final int level;
        public final double priority;

        SkillTarget(int level, double priority) {
            this.level = level;
            this.priority = priority;
        }
    }

    public static final class Builder {
        private final Map<Skill, SkillTarget> skillTargets = new LinkedHashMap<>();
        private final List<Quest> quests = new ArrayList<>();
        private int priorityMin = DEFAULT_PRIORITY_MIN;
        private int priorityMax = DEFAULT_PRIORITY_MAX;

        /** Random priority within this profile's configured range (or the default range). */
        public Builder skill(Skill skill, int targetLevel) {
            int priority = Rs2Random.between(priorityMin, priorityMax);
            skillTargets.put(skill, new SkillTarget(targetLevel, priority));
            return this;
        }

        /** Explicit priority - use this when a goal must reliably outrank/underrank others. */
        public Builder skill(Skill skill, int targetLevel, double priority) {
            skillTargets.put(skill, new SkillTarget(targetLevel, priority));
            return this;
        }

        public Builder quest(Quest quest) {
            quests.add(quest);
            return this;
        }

        /** Narrows or widens the random priority spread used by the no-explicit-priority skill(...) overload. */
        public Builder priorityRange(int min, int max) {
            this.priorityMin = min;
            this.priorityMax = max;
            return this;
        }

        public AccountProfile build() {
            return new AccountProfile(skillTargets, quests);
        }
    }
}
