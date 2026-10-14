package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class BankTutorialEvent implements BlockingEvent {

    static final String CLOSE_TEXT = "Close";

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
        return BlockingEventPriority.HIGH;
    }

    private static Widget findCloseButton() {
        if (!Microbot.isLoggedIn()) return null;
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> findCloseButton(Microbot.getClient()))
                .orElse(null);
    }

    static Widget findCloseButton(Client client) {
        if (client == null) return null;
        Widget informationBox = client.getWidget(InterfaceID.Screenhighlight.INFORMATION_BOX);
        if (informationBox == null || informationBox.isHidden()) return null;
        return Rs2Widget.searchChildren(CLOSE_TEXT, informationBox, false);
    }
}
