package org.valkyrienskies.mod.common.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.server.command.CommandTreeBase;
import net.minecraftforge.server.command.CommandTreeHelp;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.multithreaded.VSWorldPhysicsLoop;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@ParametersAreNonnullByDefault
public class MainCommand extends CommandTreeBase {
    public MainCommand() {
        this.addSubcommand(new ListShipsCommand());
        this.addSubcommand(new ShipPhysicsCommand());
        this.addSubcommand(new GarbageCollectCommand());
        this.addSubcommand(new PhysicsTpsCommand());
        this.addSubcommand(new TeleportToShipCommand());
        this.addSubcommand(new DeconstructShipCommand());
        this.addSubcommand(new DeleteShipCommand());
        this.addSubcommand(new TeleportShipToCommand());
        this.addSubcommand(new TeleportShipHereCommand());
        this.addSubcommand(new CommandTreeHelp(this));
    }

    @Override
    public String getName() {
        return "valkyrienskies";
    }

    @Override
    public List<String> getAliases() {
        return List.of("vs");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/valkyrienskies <subcommand>";
    }

    private static ShipData getShip(ICommandSender sender, String name) throws CommandException {
        return QueryableShipData.get(sender.getEntityWorld()).getShipFromName(name)
            .orElseThrow(() -> new CommandException("That ship, %s, could not be found", name));
    }

    private static List<String> getShipCompletions(ICommandSender sender, String[] args) {
        String currentWord = args.length == 0 ? "" : args[args.length - 1];
        String completedPrefix = args.length <= 1 ? "" : String.join(" ", Arrays.copyOf(args, args.length - 1)) + " ";
        String enteredName = completedPrefix + currentWord;
        String lowercaseEnteredName = enteredName.toLowerCase(Locale.ROOT);

        return QueryableShipData.get(sender.getEntityWorld()).stream()
            .map(ShipData::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowercaseEnteredName))
            .map(name -> name.substring(completedPrefix.length()))
            .sorted()
            .collect(Collectors.toList());
    }

    private static void changeDeconstructState(ICommandSender sender, ShipData shipData,
        PhysicsObject.DeconstructState state) {
        WorldServerShipManager manager = ValkyrienUtils.getServerShipManager(sender.getEntityWorld());
        PhysicsObject physicsObject = manager.getPhysObjectFromUUID(shipData.getUuid());

        if (physicsObject == null) {
            sender.sendMessage(new TextComponentString("That ship is not loaded"));
            return;
        }

        physicsObject.setDeconstructState(state);
        if (state == PhysicsObject.DeconstructState.DECONSTRUCT_NORMAL) {
            sender.sendMessage(new TextComponentString("That ship is being deconstructed"));
        } else {
            sender.sendMessage(new TextComponentString("That ship will be deleted in the next tick."));
        }
    }

    private static void teleportShipToPosition(ShipData ship, Vec3d position, ICommandSender sender) {
        WorldServerShipManager shipManager = ValkyrienUtils.getServerShipManager(sender.getEntityWorld());
        PhysicsObject shipObject = shipManager.getPhysObjectFromUUID(ship.getUuid());
        ShipTransform shipTransform = ship.getShipTransform();
        ShipTransform newTransform = new ShipTransform(JOML.convert(position), shipTransform.getCenterCoord());

        if (shipObject != null) {
            PhysicsCalculations physicsCalculations = shipObject.getPhysicsCalculations();
            physicsCalculations.setForceToUseGameTransform(true);
            shipObject.setForceToUseShipDataTransform(true);
            shipObject.setTicksSinceShipTeleport(0);
        }

        ship.setPhysicsEnabled(false);
        ship.setPrevTickShipTransform(newTransform);
        ship.setShipTransform(newTransform);
        sender.sendMessage(new TextComponentString("Teleported " + ship.getName() + " to " + position));
    }

    private abstract static class SubCommand extends CommandBase {
        private final String name;
        private final String usage;
        private final List<String> aliases;

        private SubCommand(String name, String usage, String... aliases) {
            this.name = name;
            this.usage = usage;
            this.aliases = List.of(aliases);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return usage;
        }

        @Override
        public List<String> getAliases() {
            return aliases;
        }
    }

    private static class DeconstructShipCommand extends SubCommand {
        private DeconstructShipCommand() {
            super("deconstruct-ship", "/vs deconstruct-ship <ship>", "deconstruct");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) {
                throw new WrongUsageException(this.getUsage(sender));
            }
            changeDeconstructState(sender, getShip(sender, String.join(" ", args)),
                PhysicsObject.DeconstructState.DECONSTRUCT_NORMAL);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
            return getShipCompletions(sender, args);
        }
    }

    private static class DeleteShipCommand extends SubCommand {
        private DeleteShipCommand() {
            super("delete-ship", "/vs delete-ship <ship>", "delete");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) throw new WrongUsageException(this.getUsage(sender));
            changeDeconstructState(sender, getShip(sender, String.join(" ", args)),
                PhysicsObject.DeconstructState.DECONSTRUCT_IMMEDIATE_NO_COPY);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
            return getShipCompletions(sender, args);
        }
    }

    private static class TeleportToShipCommand extends SubCommand {
        private TeleportToShipCommand() {
            super("teleport-to", "/vs teleport-to <ship>", "tpto");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) throw new WrongUsageException(this.getUsage(sender));

            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            ShipTransform transform = getShip(sender, String.join(" ", args)).getShipTransform();
            player.setPositionAndUpdate(transform.getPosX(), transform.getPosY(), transform.getPosZ());
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
            return getShipCompletions(sender, args);
        }
    }

    private static class GarbageCollectCommand extends SubCommand {
        private GarbageCollectCommand() {
            super("gc", "/vs gc");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 0) throw new WrongUsageException(this.getUsage(sender));
            System.gc();
            sender.sendMessage(new TextComponentTranslation("commands.vs.gc.success"));
        }
    }

    private static class PhysicsTpsCommand extends SubCommand {
        private PhysicsTpsCommand() {
            super("tps", "/vs tps [--world|-w <world>]");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            World world = sender.getEntityWorld();
            if (args.length != 0) {
                if (args.length != 2 || !(args[0].equals("--world") || args[0].equals("-w"))) {
                    throw new WrongUsageException(this.getUsage(sender));
                }

                Optional<WorldServer> selectedWorld = Arrays.stream(DimensionManager.getWorlds())
                    .filter(candidate -> candidate.provider.getDimensionType().getName().equalsIgnoreCase(args[1]))
                    .findFirst();
                if (selectedWorld.isEmpty()) {
                    String availableWorlds = Arrays.stream(DimensionManager.getWorlds())
                        .map(candidate -> candidate.provider.getDimensionType().getName())
                        .collect(Collectors.joining(", "));
                    throw new CommandException("Unknown world %s. Available worlds: %s", args[1], availableWorlds);
                }
                world = selectedWorld.get();
            }

            IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
            if (shipWorld == null || !(shipWorld.getManager() instanceof WorldServerShipManager serverShipManager)) {
                throw new CommandException("Ship physics are not available in that world");
            }

            VSWorldPhysicsLoop physicsLoop = serverShipManager.getPhysicsLoop();
            if (physicsLoop == null || physicsLoop.getAveragePhysicsTickTimeNano() <= 0L) {
                throw new CommandException("Physics timing data is not available in that world");
            }

            double ticksPerSecond = 1_000_000_000D / physicsLoop.getAveragePhysicsTickTimeNano();
            double roundedTicksPerSecond = Math.floor(ticksPerSecond * 100D) / 100D;
            sender.sendMessage(new TextComponentString(world.provider.getDimensionType().getName() + ": " + roundedTicksPerSecond + " physics ticks per second"));
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
            if (args.length == 1) return getListOfStringsMatchingLastWord(args, "--world", "-w");
            if (args.length == 2 && (args[0].equals("--world") || args[0].equals("-w"))) {
                List<String> worldNames = Arrays.stream(DimensionManager.getWorlds())
                    .map(candidate -> candidate.provider.getDimensionType().getName())
                    .collect(Collectors.toList());
                return getListOfStringsMatchingLastWord(args, worldNames);
            }
            return Collections.emptyList();
        }
    }

    private static class ShipPhysicsCommand extends SubCommand {
        private ShipPhysicsCommand() {
            super("ship-physics", "/vs ship-physics <ship> [true|false]");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) throw new WrongUsageException(this.getUsage(sender));

            String completeName = String.join(" ", args);
            Optional<ShipData> exactShip = QueryableShipData.get(sender.getEntityWorld()).getShipFromName(completeName);
            ShipData ship;
            boolean enabledWasSpecified = false;
            boolean enabled = false;

            if (exactShip.isPresent()) ship = exactShip.get();
            else if (args.length > 1) {
                String possibleName = String.join(" ", Arrays.copyOf(args, args.length - 1));
                ship = getShip(sender, possibleName);
                enabled = parseBoolean(args[args.length - 1]);
                enabledWasSpecified = true;
            }
            else ship = getShip(sender, completeName);

            boolean physicsWasEnabled = ship.isPhysicsEnabled();
            String oldState = physicsWasEnabled ? "enabled" : "disabled";
            if (!enabledWasSpecified) {
                sender.sendMessage(new TextComponentString("That ship's physics are: " + oldState));
                return;
            }

            ship.setPhysicsEnabled(enabled);
            if (physicsWasEnabled == enabled) {
                sender.sendMessage(new TextComponentString("That ship's physics were not changed from " + oldState));
            }
            else {
                String newState = enabled ? "enabled" : "disabled";
                sender.sendMessage(new TextComponentString("That ship's physics were changed from " + oldState + " to " + newState));
            }
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
            if (args.length > 1) {
                String possibleName = String.join(" ", Arrays.copyOf(args, args.length - 1));
                if (QueryableShipData.get(sender.getEntityWorld()).getShipFromName(possibleName).isPresent()) {
                    return getListOfStringsMatchingLastWord(args, "true", "false");
                }
            }
            return getShipCompletions(sender, args);
        }
    }

    private static class ListShipsCommand extends SubCommand {
        private ListShipsCommand() {
            super("list-ships", "/vs list-ships [-v|--verbose]", "ls");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            boolean verbose = false;
            if (args.length == 1 && (args[0].equals("-v") || args[0].equals("--verbose"))) {
                verbose = true;
            }
            else if (args.length != 0) throw new WrongUsageException(this.getUsage(sender));

            QueryableShipData data = ValkyrienUtils.getQueryableData(sender.getEntityWorld());
            if (data.getShips().isEmpty()) {
                sender.sendMessage(new TextComponentTranslation("commands.vs.list-ships.noships"));
                return;
            }

            String listOfShips;
            if (verbose) {
                listOfShips = data.getShips().stream().map(shipData -> {
                    if (shipData.getShipTransform() == null) return shipData.getName() + ", Unknown Location";
                    return String.format("%s [%.1f, %.1f, %.1f]", shipData.getName(),
                        shipData.getShipTransform().getPosX(), shipData.getShipTransform().getPosY(),
                        shipData.getShipTransform().getPosZ());
                }).collect(Collectors.joining(",\n"));
            }
            else {
                listOfShips = data.getShips().stream().map(ShipData::getName).collect(Collectors.joining(",\n"));
            }

            sender.sendMessage(new TextComponentTranslation("commands.vs.list-ships.ships", listOfShips));
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
            if (args.length == 1) return getListOfStringsMatchingLastWord(args, "-v", "--verbose");
            return Collections.emptyList();
        }
    }

    private static class TeleportShipToCommand extends SubCommand {
        private TeleportShipToCommand() {
            super("teleport-ship-to", "/vs teleport-ship-to <ship> <x> <y> <z>", "tp-ship-to");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length < 4) throw new WrongUsageException(this.getUsage(sender));

            int coordinateStart = args.length - 3;
            ShipData ship = getShip(sender, String.join(" ", Arrays.copyOf(args, coordinateStart)));
            Vec3d senderPosition = sender.getPositionVector();
            double x = parseDouble(senderPosition.x, args[coordinateStart], false);
            double y = parseDouble(senderPosition.y, args[coordinateStart + 1], false);
            double z = parseDouble(senderPosition.z, args[coordinateStart + 2], false);
            teleportShipToPosition(ship, new Vec3d(x, y, z), sender);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
            int coordinateStart = -1;
            for (int index = 1; index < args.length; index++) {
                String possibleName = String.join(" ", Arrays.copyOf(args, index));
                if (QueryableShipData.get(sender.getEntityWorld()).getShipFromName(possibleName).isPresent()) {
                    coordinateStart = index;
                }
            }

            if (coordinateStart >= 0 && args.length - coordinateStart <= 3) {
                return getTabCompletionCoordinate(args, coordinateStart, targetPos);
            }
            return getShipCompletions(sender, args);
        }
    }

    private static class TeleportShipHereCommand extends SubCommand {
        private TeleportShipHereCommand() {
            super("teleport-ship-here", "/vs teleport-ship-here <ship>", "tp-ship-here");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) throw new WrongUsageException(this.getUsage(sender));
            teleportShipToPosition(getShip(sender, String.join(" ", args)), sender.getPositionVector(), sender);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
            return getShipCompletions(sender, args);
        }
    }
}
