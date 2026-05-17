package org.valkyrienskies.addon.control.nodeControls;

import java.util.Map;
import java.util.function.Supplier;

public class NodeControl {
    private final Map<Enum, Integer> controlBitMap;
    private final Map<Enum, Supplier<Boolean>> controlKeyMap;
    private final InputMode inputMode;
    private int lastControls;

    public NodeControl(Map<Enum, Integer> controlBitMap, Map<Enum, Supplier<Boolean>> controlKeyMap, InputMode inputMode) {
        this.controlBitMap = controlBitMap;
        this.controlKeyMap = controlKeyMap;
        this.inputMode = inputMode;
    }

    public int getControls() {
        int currentControls = 0;

        for (Map.Entry<Enum, Integer> controlBitMapEntry : this.controlBitMap.entrySet()) {
            Enum enumToGet = controlBitMapEntry.getKey();
            int enumBitIndex = controlBitMapEntry.getValue();
            currentControls |= toInt(this.controlKeyMap.get(enumToGet).get()) << enumBitIndex;
        }

        int toReturn = switch (this.inputMode) {
            case HELD -> currentControls;
            case NEW_PRESS -> currentControls & ~this.lastControls;
        };
        this.lastControls = currentControls;

        return toReturn;
    }

    public boolean controlIsPressed(int controls, Enum enumToGet) {
        if (!this.controlBitMap.containsKey(enumToGet)) return false;
        int control = this.controlBitMap.get(enumToGet);
        return (controls & (1 << control)) >> control > 0;
    }

    private static int toInt(boolean bool) {
        return bool ? 1 : 0;
    }

    public enum InputMode {
        HELD,
        NEW_PRESS
    }

    public enum Enum {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        SPRINT;
    }
}
