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

    public static int getUsedControls() {
        int toReturn = 0;

        toReturn |= toInt(VSKeyHandler.airshipUp.isKeyDown()) << UP;
        toReturn |= toInt(VSKeyHandler.airshipDown.isKeyDown()) << DOWN;
        toReturn |= toInt(VSKeyHandler.airshipForward.isKeyDown()) << FORWARD;
        toReturn |= toInt(VSKeyHandler.airshipBackward.isKeyDown()) << BACKWARD;
        toReturn |= toInt(VSKeyHandler.airshipLeft.isKeyDown()) << LEFT;
        toReturn |= toInt(VSKeyHandler.airshipRight.isKeyDown()) << RIGHT;
        toReturn |= toInt(VSKeyHandler.airshipSpriting.isKeyDown()) << SPRINT;

        return toReturn;
    }

    private static int toInt(boolean bool) {
        return bool ? 1 : 0;
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
