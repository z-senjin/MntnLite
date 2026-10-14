package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class BankJagexPopupEvent implements BlockingEvent {

    static final String NOT_NOW_TEXT = "Not now";

    @Override
    public boolean validate() {
        return findNotNowButton() != null;
    }

    @Override
    public boolean execute() {
        Widget notNowButton = findNotNowButton();
        if (notNowButton == null) return true;

        Rs2Widget.clickWidget(notNowButton);
        return Global.sleepUntil(() -> findNotNowButton() == null, 10000);
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }

    private static Widget findNotNowButton() {
        if (!Microbot.isLoggedIn()) return null;
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> findNotNowButton(Microbot.getClient()))
                .orElse(null);
    }

    static Widget findNotNowButton(Client client) {
        if (client == null) return null;
        Widget popup = client.getWidget(InterfaceID.Popupoverlay.CONTAINER);
        if (popup == null || popup.isHidden()) return null;
        return Rs2Widget.searchChildren(NOT_NOW_TEXT, popup, false);
    }
}
