package org.valkyrienskies.mod.common.command;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.NotNull;

@Deprecated
@ParametersAreNonnullByDefault
public class PhysSettingsCommand extends CommandBase {

    private static final String DEPRECATION_MESSAGE =
        "This command is deprecated and will be removed in a later " +
            "release. Please use /vsconfig or, alternatively, go to \"Mod Options\" and then "
            + "\"Valkyrien Skies\" and then \"Config\" to change physics settings. Thanks!";

    @Override
    public @NotNull String getName() {
        return "physsettings";
    }

    @Override
    public @NotNull String getUsage(ICommandSender sender) {
        return DEPRECATION_MESSAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        sender.sendMessage(new TextComponentString(DEPRECATION_MESSAGE));
    }
}
