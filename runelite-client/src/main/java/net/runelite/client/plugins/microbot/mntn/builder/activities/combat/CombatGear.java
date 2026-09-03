package net.runelite.client.plugins.microbot.mntn.builder.activities.combat;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.ArrayList;
import java.util.List;

public class CombatGear {

    public static class GearItem {
        public final String name;
        public final int requiredLevel;
        public final Skill skill;

        public GearItem(String name, int requiredLevel, Skill skill) {
            this.name = name;
            this.requiredLevel = requiredLevel;
            this.skill = skill;
        }
    }

    /*
     * Weapons ordered by tier descending (Rune -> Adamant -> Mithril -> Black -> Steel -> Iron -> Bronze)
     * and weapon preference: Scimitar > Sword > Longsword > Dagger > Battleaxe > Axe > Mace > 2h sword.
     */
    public static final GearItem[] WEAPONS = {
            // Rune (40 Attack)
            new GearItem("Rune scimitar", 40, Skill.ATTACK),
            new GearItem("Rune sword", 40, Skill.ATTACK),
            new GearItem("Rune longsword", 40, Skill.ATTACK),
            new GearItem("Rune dagger", 40, Skill.ATTACK),
            new GearItem("Rune battleaxe", 40, Skill.ATTACK),
            new GearItem("Rune axe", 40, Skill.ATTACK),
            new GearItem("Rune mace", 40, Skill.ATTACK),
            new GearItem("Rune 2h sword", 40, Skill.ATTACK),

            // Adamant (30 Attack)
            new GearItem("Adamant scimitar", 30, Skill.ATTACK),
            new GearItem("Adamant sword", 30, Skill.ATTACK),
            new GearItem("Adamant longsword", 30, Skill.ATTACK),
            new GearItem("Adamant dagger", 30, Skill.ATTACK),
            new GearItem("Adamant battleaxe", 30, Skill.ATTACK),
            new GearItem("Adamant axe", 30, Skill.ATTACK),
            new GearItem("Adamant mace", 30, Skill.ATTACK),
            new GearItem("Adamant 2h sword", 30, Skill.ATTACK),

            // Mithril (20 Attack)
            new GearItem("Mithril scimitar", 20, Skill.ATTACK),
            new GearItem("Mithril sword", 20, Skill.ATTACK),
            new GearItem("Mithril longsword", 20, Skill.ATTACK),
            new GearItem("Mithril dagger", 20, Skill.ATTACK),
            new GearItem("Mithril battleaxe", 20, Skill.ATTACK),
            new GearItem("Mithril axe", 20, Skill.ATTACK),
            new GearItem("Mithril mace", 20, Skill.ATTACK),
            new GearItem("Mithril 2h sword", 20, Skill.ATTACK),

            // Black (10 Attack)
            new GearItem("Black scimitar", 10, Skill.ATTACK),
            new GearItem("Black sword", 10, Skill.ATTACK),
            new GearItem("Black longsword", 10, Skill.ATTACK),
            new GearItem("Black dagger", 10, Skill.ATTACK),
            new GearItem("Black battleaxe", 10, Skill.ATTACK),
            new GearItem("Black axe", 10, Skill.ATTACK),
            new GearItem("Black mace", 10, Skill.ATTACK),
            new GearItem("Black 2h sword", 10, Skill.ATTACK),

            // Steel (5 Attack)
            new GearItem("Steel scimitar", 5, Skill.ATTACK),
            new GearItem("Steel sword", 5, Skill.ATTACK),
            new GearItem("Steel longsword", 5, Skill.ATTACK),
            new GearItem("Steel dagger", 5, Skill.ATTACK),
            new GearItem("Steel battleaxe", 5, Skill.ATTACK),
            new GearItem("Steel axe", 5, Skill.ATTACK),
            new GearItem("Steel mace", 5, Skill.ATTACK),
            new GearItem("Steel 2h sword", 5, Skill.ATTACK),

            // Iron (1 Attack)
            new GearItem("Iron scimitar", 1, Skill.ATTACK),
            new GearItem("Iron sword", 1, Skill.ATTACK),
            new GearItem("Iron longsword", 1, Skill.ATTACK),
            new GearItem("Iron dagger", 1, Skill.ATTACK),
            new GearItem("Iron battleaxe", 1, Skill.ATTACK),
            new GearItem("Iron axe", 1, Skill.ATTACK),
            new GearItem("Iron mace", 1, Skill.ATTACK),
            new GearItem("Iron 2h sword", 1, Skill.ATTACK),

            // Bronze (1 Attack)
            new GearItem("Bronze scimitar", 1, Skill.ATTACK),
            new GearItem("Bronze sword", 1, Skill.ATTACK),
            new GearItem("Bronze longsword", 1, Skill.ATTACK),
            new GearItem("Bronze dagger", 1, Skill.ATTACK),
            new GearItem("Bronze battleaxe", 1, Skill.ATTACK),
            new GearItem("Bronze axe", 1, Skill.ATTACK),
            new GearItem("Bronze mace", 1, Skill.ATTACK),
            new GearItem("Bronze 2h sword", 1, Skill.ATTACK)
    };

    /*
     * Helmets ordered by tier descending (Rune -> Adamant -> Mithril -> Black -> Steel -> Iron -> Bronze).
     */
    public static final GearItem[] HELMETS = {
            new GearItem("Rune full helm", 40, Skill.DEFENCE),
            new GearItem("Rune med helm", 40, Skill.DEFENCE),
            new GearItem("Adamant full helm", 30, Skill.DEFENCE),
            new GearItem("Adamant med helm", 30, Skill.DEFENCE),
            new GearItem("Mithril full helm", 20, Skill.DEFENCE),
            new GearItem("Mithril med helm", 20, Skill.DEFENCE),
            new GearItem("Black full helm", 10, Skill.DEFENCE),
            new GearItem("Black med helm", 10, Skill.DEFENCE),
            new GearItem("Steel full helm", 5, Skill.DEFENCE),
            new GearItem("Steel med helm", 5, Skill.DEFENCE),
            new GearItem("Iron full helm", 1, Skill.DEFENCE),
            new GearItem("Iron med helm", 1, Skill.DEFENCE),
            new GearItem("Bronze full helm", 1, Skill.DEFENCE),
            new GearItem("Bronze med helm", 1, Skill.DEFENCE),
            new GearItem("Leather cowl", 1, Skill.DEFENCE)
    };

    /*
     * Bodies ordered by tier descending.
     */
    public static final GearItem[] BODIES = {
            new GearItem("Rune platebody", 40, Skill.DEFENCE),
            new GearItem("Rune chainbody", 40, Skill.DEFENCE),
            new GearItem("Adamant platebody", 30, Skill.DEFENCE),
            new GearItem("Adamant chainbody", 30, Skill.DEFENCE),
            new GearItem("Mithril platebody", 20, Skill.DEFENCE),
            new GearItem("Mithril chainbody", 20, Skill.DEFENCE),
            new GearItem("Black platebody", 10, Skill.DEFENCE),
            new GearItem("Black chainbody", 10, Skill.DEFENCE),
            new GearItem("Steel platebody", 5, Skill.DEFENCE),
            new GearItem("Steel chainbody", 5, Skill.DEFENCE),
            new GearItem("Iron platebody", 1, Skill.DEFENCE),
            new GearItem("Iron chainbody", 1, Skill.DEFENCE),
            new GearItem("Bronze platebody", 1, Skill.DEFENCE),
            new GearItem("Bronze chainbody", 1, Skill.DEFENCE),
            new GearItem("Hardleather body", 10, Skill.DEFENCE),
            new GearItem("Leather body", 1, Skill.DEFENCE)
    };

    /*
     * Legs ordered by tier descending.
     */
    public static final GearItem[] LEGS = {
            new GearItem("Rune platelegs", 40, Skill.DEFENCE),
            new GearItem("Rune plateskirt", 40, Skill.DEFENCE),
            new GearItem("Adamant platelegs", 30, Skill.DEFENCE),
            new GearItem("Adamant plateskirt", 30, Skill.DEFENCE),
            new GearItem("Mithril platelegs", 20, Skill.DEFENCE),
            new GearItem("Mithril plateskirt", 20, Skill.DEFENCE),
            new GearItem("Black platelegs", 10, Skill.DEFENCE),
            new GearItem("Black plateskirt", 10, Skill.DEFENCE),
            new GearItem("Steel platelegs", 5, Skill.DEFENCE),
            new GearItem("Steel plateskirt", 5, Skill.DEFENCE),
            new GearItem("Iron platelegs", 1, Skill.DEFENCE),
            new GearItem("Iron plateskirt", 1, Skill.DEFENCE),
            new GearItem("Bronze platelegs", 1, Skill.DEFENCE),
            new GearItem("Bronze plateskirt", 1, Skill.DEFENCE),
            new GearItem("Leather chaps", 1, Skill.DEFENCE)
    };

    /*
     * Shields ordered by tier descending.
     */
    public static final GearItem[] SHIELDS = {
            new GearItem("Rune kiteshield", 40, Skill.DEFENCE),
            new GearItem("Rune sq shield", 40, Skill.DEFENCE),
            new GearItem("Adamant kiteshield", 30, Skill.DEFENCE),
            new GearItem("Adamant sq shield", 30, Skill.DEFENCE),
            new GearItem("Mithril kiteshield", 20, Skill.DEFENCE),
            new GearItem("Mithril sq shield", 20, Skill.DEFENCE),
            new GearItem("Black kiteshield", 10, Skill.DEFENCE),
            new GearItem("Black sq shield", 10, Skill.DEFENCE),
            new GearItem("Steel kiteshield", 5, Skill.DEFENCE),
            new GearItem("Steel sq shield", 5, Skill.DEFENCE),
            new GearItem("Iron kiteshield", 1, Skill.DEFENCE),
            new GearItem("Iron sq shield", 1, Skill.DEFENCE),
            new GearItem("Bronze kiteshield", 1, Skill.DEFENCE),
            new GearItem("Bronze sq shield", 1, Skill.DEFENCE),
            new GearItem("Wooden shield", 1, Skill.DEFENCE)
    };

    public static GearItem findBestWeapon(AccountContext context, boolean checkBank) {
        int attackLevel = context.getRealLevel(Skill.ATTACK);
        for (GearItem weapon : WEAPONS) {
            if (attackLevel < weapon.requiredLevel) continue;
            boolean has = context.equipment().hasItem(weapon.name)
                    || context.inventory().hasItem(weapon.name)
                    || (checkBank && context.bank().hasItem(weapon.name));
            if (has) {
                return weapon;
            }
        }
        return null;
    }

    public static GearItem findBestArmor(AccountContext context, GearItem[] items, boolean checkBank) {
        int defLevel = context.getRealLevel(Skill.DEFENCE);
        for (GearItem item : items) {
            if (defLevel < item.requiredLevel) continue;
            boolean has = context.equipment().hasItem(item.name)
                    || context.inventory().hasItem(item.name)
                    || (checkBank && context.bank().hasItem(item.name));
            if (has) {
                return item;
            }
        }
        return null;
    }

    public static boolean isTwoHanded(GearItem weapon) {
        return weapon != null && weapon.name.contains("2h");
    }

    /**
     * Collects all gear upgrades from the bank that should be withdrawn and equipped.
     */
    public static List<String> getBankGearUpgrades(AccountContext context) {
        List<String> toWithdraw = new ArrayList<>();

        // Check weapon
        GearItem bestWeapon = findBestWeapon(context, true);
        if (bestWeapon != null && !context.equipment().hasItem(bestWeapon.name)
                && !context.inventory().hasItem(bestWeapon.name)
                && context.bank().hasItem(bestWeapon.name)) {
            toWithdraw.add(bestWeapon.name);
        }

        boolean usingTwoHanded = (bestWeapon != null && isTwoHanded(bestWeapon));

        // Check shield if not using 2h
        if (!usingTwoHanded) {
            GearItem bestShield = findBestArmor(context, SHIELDS, true);
            if (bestShield != null && !context.equipment().hasItem(bestShield.name)
                    && !context.inventory().hasItem(bestShield.name)
                    && context.bank().hasItem(bestShield.name)) {
                toWithdraw.add(bestShield.name);
            }
        }

        // Check helmet
        GearItem bestHelm = findBestArmor(context, HELMETS, true);
        if (bestHelm != null && !context.equipment().hasItem(bestHelm.name)
                && !context.inventory().hasItem(bestHelm.name)
                && context.bank().hasItem(bestHelm.name)) {
            toWithdraw.add(bestHelm.name);
        }

        // Check body
        GearItem bestBody = findBestArmor(context, BODIES, true);
        if (bestBody != null && !context.equipment().hasItem(bestBody.name)
                && !context.inventory().hasItem(bestBody.name)
                && context.bank().hasItem(bestBody.name)) {
            toWithdraw.add(bestBody.name);
        }

        // Check legs
        GearItem bestLegs = findBestArmor(context, LEGS, true);
        if (bestLegs != null && !context.equipment().hasItem(bestLegs.name)
                && !context.inventory().hasItem(bestLegs.name)
                && context.bank().hasItem(bestLegs.name)) {
            toWithdraw.add(bestLegs.name);
        }

        return toWithdraw;
    }

    public static List<String> getAllKnownGearNames() {
        List<String> names = new ArrayList<>();
        for (GearItem w : WEAPONS) names.add(w.name);
        for (GearItem h : HELMETS) names.add(h.name);
        for (GearItem b : BODIES) names.add(b.name);
        for (GearItem l : LEGS) names.add(l.name);
        for (GearItem s : SHIELDS) names.add(s.name);
        return names;
    }
}
