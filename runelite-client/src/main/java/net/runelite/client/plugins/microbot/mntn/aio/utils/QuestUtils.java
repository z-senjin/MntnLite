package net.runelite.client.plugins.microbot.mntn.aio.utils;

import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

public final class QuestUtils {


    public static boolean canContinue(){
        if(!Rs2Dialogue.isInCutScene() || !Rs2Dialogue.isInDialogue()){
            return false;
        }

        if(Rs2Dialogue.isInCutScene()){
            sleep(Rs2Random.between(800, 8000));
            return false;
        }


        return true;
    }

    public static boolean selectOption(){
        return true;
    }
}
