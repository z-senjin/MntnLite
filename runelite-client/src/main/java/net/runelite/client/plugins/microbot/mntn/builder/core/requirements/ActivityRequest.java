package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;

/**
 * "I need some work done of this type, for this reason." The payload is deliberately loose
 * (Object) in the vertical slice - for a SkillRequirement it's the Skill being trained. Once
 * you add ItemRequirement/EquipmentRequirement etc, payload might be an item id + quantity.
 * Feel free to replace this with a proper sealed hierarchy once you have 2-3 real usages to
 * generalize from - don't over-design it before then.
 */
public class ActivityRequest {
    private final ActivityType type;
    private final Object payload;

    public ActivityRequest(ActivityType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public ActivityType type() {
        return type;
    }

    public Object payload() {
        return payload;
    }
}
