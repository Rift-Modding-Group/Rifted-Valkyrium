package org.valkyrienskies.mod.common.piloting;

import org.valkyrienskies.mod.client.VSKeyHandler;

/**
 * helper class that defines the keybinds for piloting as a 7 bit
 * byte. each bit corresponds to a control keybind being used
 */
public class PilotControls {
    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int FORWARD = 2;
    public static final int BACKWARD = 3;
    public static final int LEFT = 4;
    public static final int RIGHT = 5;
    public static final int SPRINT = 6;

    private static int lastControls;

    public static int getUsedControls() {
        int toReturn = 0;

        toReturn |= (toInt(VSKeyHandler.airshipUp.isKeyDown()) << UP) & ~getLastControl(UP);
        toReturn |= (toInt(VSKeyHandler.airshipDown.isKeyDown()) << DOWN) & ~getLastControl(DOWN);
        toReturn |= (toInt(VSKeyHandler.airshipForward.isKeyDown()) << FORWARD) & ~getLastControl(FORWARD);
        toReturn |= (toInt(VSKeyHandler.airshipBackward.isKeyDown()) << BACKWARD) & ~getLastControl(BACKWARD);
        toReturn |= (toInt(VSKeyHandler.airshipLeft.isKeyDown()) << LEFT) & ~getLastControl(LEFT);
        toReturn |= (toInt(VSKeyHandler.airshipRight.isKeyDown()) << RIGHT) & ~getLastControl(RIGHT);
        toReturn |= (toInt(VSKeyHandler.airshipSpriting.isKeyDown()) << SPRINT) & ~getLastControl(SPRINT);

        lastControls = toReturn;

        return toReturn;
    }

    private static int toInt(boolean bool) {
        return bool ? 1 : 0;
    }

    private static int getLastControl(int control) {
        return (lastControls & (1 << control)) >> control;
    }

    public static boolean controlIsPressed(int control) {
        switch (control) {
            case UP -> VSKeyHandler.airshipUp.isKeyDown();
            case DOWN -> VSKeyHandler.airshipDown.isKeyDown();
            case FORWARD -> VSKeyHandler.airshipForward.isKeyDown();
            case BACKWARD -> VSKeyHandler.airshipBackward.isKeyDown();
            case LEFT -> VSKeyHandler.airshipLeft.isKeyDown();
            case RIGHT -> VSKeyHandler.airshipRight.isKeyDown();
            case SPRINT -> VSKeyHandler.airshipSpriting.isKeyDown();
        }
        return false;
    }

    public static boolean controlIsPressed(int controls, int control) {
        return (controls & (1 << control)) >> control > 0;
    }
}
