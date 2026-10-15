package org.valkyrienskies.addon.control.container;

import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.addon.control.tileentity.TileEntityPhysicsInfuser;

import java.util.function.Function;

public enum EnumInfuserButton {
    ASSEMBLE_SHIP(
            "gui.assemble_ship", "gui.disassemble_ship",
            tileEntity -> !tileEntity.isCurrentlyInShip(),
            tileEntity -> {
                boolean canInitiateAction = tileEntity.isCurrentlyInShip() || tileEntity.canMaintainShip();
                return !tileEntity.isActionInProgress() && canInitiateAction
                        && tileEntity.canShipBeDeconstructed() && tileEntity.isCenterOfShip();
            }
    ),
    ENABLE_PHYSICS(
            "gui.enable_physics", "gui.disable_physics",
            tileEntity -> !tileEntity.isPhysicsEnabled(),
            tileEntity -> tileEntity.canMaintainShip() && tileEntity.isCurrentlyInShip() && tileEntity.isCenterOfShip()
    ),
    ALIGN_SHIP(
            "gui.align_ship", "gui.stop_align_ship",
            tileEntity -> !tileEntity.isTryingToAlignShip(),
            tileEntity -> tileEntity.canMaintainShip() && tileEntity.isCurrentlyInShip() && tileEntity.isCenterOfShip()
    );

    @NotNull
    private final String trueMessage, falseMessage;
    @NotNull
    private final Function<TileEntityPhysicsInfuser, Boolean> messageDiscriminator, buttonEnabled;

    EnumInfuserButton(
            @NotNull String trueMessage, @NotNull String falseMessage,
            @NotNull Function<TileEntityPhysicsInfuser, Boolean> messageDiscriminator,
            @NotNull Function<TileEntityPhysicsInfuser, Boolean> buttonEnabled
    ) {
        this.trueMessage = trueMessage;
        this.falseMessage = falseMessage;
        this.messageDiscriminator = messageDiscriminator;
        this.buttonEnabled = buttonEnabled;
    }

    public String getButtonText(TileEntityPhysicsInfuser tileEntity) {
        if (this == ASSEMBLE_SHIP) {
            return switch (tileEntity.getActionState()) {
                case ASSEMBLING -> "gui.assembling_ship";
                case DISASSEMBLING -> "gui.disassembling_ship";
                case IDLE -> this.messageDiscriminator.apply(tileEntity) ? this.trueMessage : this.falseMessage;
            };
        }
        return this.messageDiscriminator.apply(tileEntity) ? this.trueMessage : this.falseMessage;
    }

    public boolean buttonEnabled(TileEntityPhysicsInfuser tileEntity) {
        return this.buttonEnabled.apply(tileEntity);
    }
}
