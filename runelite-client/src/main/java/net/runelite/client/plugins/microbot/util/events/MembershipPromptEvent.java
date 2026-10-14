package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class MembershipPromptEvent implements BlockingEvent {

    static final int[][] PROMPTS = {
            {InterfaceID.F2PBondRedeem.UNIVERSE, InterfaceID.F2PBondRedeem.CLOSE},
            {InterfaceID.BondPrompt.UNIVERSE, InterfaceID.BondPrompt.CLOSE},
            {InterfaceID.MembershipBenefitsPromptHop.UNIVERSE, InterfaceID.MembershipBenefitsPromptHop.CLOSE_BUTTON},
    };

    @Override
    public boolean validate() {
        return findCloseButton() != null;
    }

    @Override
    public boolean execute() {
        Widget closeButton = findCloseButton();
        if (closeButton == null) return true;

        Rs2Widget.clickWidget(closeButton);
        return Global.sleepUntil(() -> findCloseButton() == null, 10000);
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }

    private static Widget findCloseButton() {
        if (!Microbot.isLoggedIn()) return null;
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> findCloseButton(Microbot.getClient()))
                .orElse(null);
    }

    static Widget findCloseButton(Client client) {
        if (client == null) return null;
        for (int[] prompt : PROMPTS) {
            Widget root = client.getWidget(prompt[0]);
            if (root == null || root.isHidden()) continue;
            Widget close = client.getWidget(prompt[1]);
            if (close != null && !close.isHidden()) return close;
        }
        return null;
    }
}
