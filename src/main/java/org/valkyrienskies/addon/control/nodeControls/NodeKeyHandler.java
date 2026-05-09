package org.valkyrienskies.addon.control.nodeControls;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.addon.control.capability.controlNodeUser.ICapabilityControlNodeUser;
import org.valkyrienskies.addon.control.network.VSStoppedUsingControlNodeMessage;
import org.valkyrienskies.mod.client.VSKeyHandler;

public class NodeKeyHandler {
    private static final String VSC_KEYBIND_IDENTIFIER = "Valkyrien Skies Controls";

    //helm keys
    public static final KeyBinding helmLeft = new KeyBinding("Turn Helm Left", Keyboard.KEY_A, VSC_KEYBIND_IDENTIFIER);
    public static final KeyBinding helmRight = new KeyBinding("Turn Helm Right", Keyboard.KEY_D, VSC_KEYBIND_IDENTIFIER);

    //lift lever keys
    public static final KeyBinding liftLeverUp = new KeyBinding("Lift Lever Up", Keyboard.KEY_W, VSC_KEYBIND_IDENTIFIER);
    public static final KeyBinding liftLeverDown = new KeyBinding("Lift Lever Down", Keyboard.KEY_S, VSC_KEYBIND_IDENTIFIER);
    public static final KeyBinding liftLeverSprint = new KeyBinding("Lift Lever Speed Up Input", Keyboard.KEY_LCONTROL, VSC_KEYBIND_IDENTIFIER);

    //speed telegraph keys
    public static final KeyBinding speedTelegraphLeft = new KeyBinding("Turn Speed Telegraph Leftwards", Keyboard.KEY_A, VSC_KEYBIND_IDENTIFIER);
    public static final KeyBinding speedTelegraphRight = new KeyBinding("Turn Speed Telegraph Rightwards", Keyboard.KEY_D, VSC_KEYBIND_IDENTIFIER);

    static {
        ClientRegistry.registerKeyBinding(helmLeft);
        ClientRegistry.registerKeyBinding(helmRight);
        ClientRegistry.registerKeyBinding(liftLeverUp);
        ClientRegistry.registerKeyBinding(liftLeverDown);
        ClientRegistry.registerKeyBinding(speedTelegraphLeft);
        ClientRegistry.registerKeyBinding(speedTelegraphRight);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.side == Side.SERVER) return;
        if (event.phase == TickEvent.Phase.START) {
            ICapabilityControlNodeUser controlNodeUser = event.player.getCapability(ValkyrienSkiesControl.controlNodeUserCapability, null);
            if (controlNodeUser == null) return;

            controlNodeUser.onClientTick();

            if (VSKeyHandler.dismountKey.isKeyDown() && controlNodeUser.getUsedControlNodePos() != null) {
                BlockPos pilotedPos = controlNodeUser.getUsedControlNodePos();
                VSStoppedUsingControlNodeMessage stopPilotingMessage = new VSStoppedUsingControlNodeMessage(
                        pilotedPos
                );
                ValkyrienSkiesControl.controlNodeNetwork.sendToServer(stopPilotingMessage);
                controlNodeUser.stopUsingEverything();
            }

            /*
            if (VSKeyHandler.dismountKey.isKeyDown() && controlNodeUser.getShipIDBeingControlled() != null) {
                VSStoppedUsingControlNodeMessage stopPilotingMessage = new VSStoppedUsingControlNodeMessage(
                        controlNodeUser.getShipIDBeingControlled()
                );
                ValkyrienSkiesControl.controlNodeNetwork.sendToServer(stopPilotingMessage);
                controlNodeUser.stopUsingEverything();
            }
             */
        }
    }
}
