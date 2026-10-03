package org.valkyrienskies.mod.common.command.config;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.server.command.CommandTreeBase;
import net.minecraftforge.server.command.CommandTreeHelp;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
public class VSConfigCommand extends CommandTreeBase {
    private final String name;
    private final List<String> aliases;

    public VSConfigCommand(String name, Class<?> configClass, String... aliases) {
        this.name = name;
        this.aliases = List.of(aliases);

        Method syncMethod;
        try {
            syncMethod = configClass.getMethod("sync");
        }
        catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException("The config class must define public static void sync()", exception);
        }
        if (!Modifier.isStatic(syncMethod.getModifiers()) || syncMethod.getReturnType() != void.class) {
            throw new IllegalArgumentException("The config class must define public static void sync()");
        }

        List<Class<?>> subcategoryTypes = Arrays.stream(configClass.getDeclaredClasses())
            .filter(type -> !type.isEnum())
            .toList();
        for (Field field : configClass.getFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            if (subcategoryTypes.contains(field.getType())) {
                try {
                    addSubcommand(new ConfigCategoryCommand(getCommandName(field), name, field.getType(), field.get(null), syncMethod));
                }
                catch (IllegalAccessException exception) {
                    throw new IllegalArgumentException("Could not read config category " + field.getName(), exception);
                }
            }
            else if (isSupportedType(field.getType())) {
                addSubcommand(new ConfigValueCommand(getCommandName(field), "/" + name, field, null, syncMethod));
            }
        }
        addSubcommand(new CommandTreeHelp(this));
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public List<String> getAliases() {
        return this.aliases;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + this.name + " <option> [value]";
    }

    private static String getCommandName(Field field) {
        ShortName shortName = field.getAnnotation(ShortName.class);
        return shortName == null ? field.getName() : shortName.value();
    }

    private static boolean isSupportedType(Class<?> type) {
        return type == int.class || type == double.class || type == float.class || type == boolean.class
            || type == byte.class || type == long.class || type == short.class || type == char.class
            || type == String.class || type.isEnum();
    }

    private static class ConfigCategoryCommand extends CommandTreeBase {
        private final String name;
        private final String usage;

        private ConfigCategoryCommand(String name, String rootName, Class<?> categoryClass, Object category,
            Method syncMethod) {
            this.name = name;
            this.usage = "/" + rootName + " " + name + " <option> [value]";

            for (Field field : categoryClass.getFields()) {
                if (Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())
                    && isSupportedType(field.getType())
                ) {
                    this.addSubcommand(new ConfigValueCommand(getCommandName(field), "/" + rootName + " " + name, field, category, syncMethod));
                }
            }
            addSubcommand(new CommandTreeHelp(this));
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return this.usage;
        }
    }

    private static class ConfigValueCommand extends CommandBase {
        private final String name;
        private final String usage;
        private final Field field;
        @Nullable
        private final Object owner;
        private final Method syncMethod;

        private ConfigValueCommand(String name, String parentUsage, Field field, @Nullable Object owner, Method syncMethod) {
            this.name = name;
            this.usage = parentUsage + " " + name + " [value]";
            this.field = field;
            this.owner = owner;
            this.syncMethod = syncMethod;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return this.usage;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) {
                try {
                    sender.sendMessage(new TextComponentString(this.name + " = " + this.field.get(this.owner)));
                    return;
                }
                catch (IllegalAccessException exception) {
                    throw new CommandException("Could not read config option %s", this.name);
                }
            }
            if (args.length > 1 && this.field.getType() != String.class) {
                throw new WrongUsageException(getUsage(sender));
            }

            String value = field.getType() == String.class ? String.join(" ", args) : args[0];
            try {
                Class<?> type = this.field.getType();
                if (type == int.class) {
                    this.field.setInt(this.owner, parseInt(value));
                }
                else if (type == double.class) {
                    this.field.setDouble(this.owner, parseDouble(value));
                }
                else if (type == float.class) {
                    this.field.setFloat(this.owner, (float) parseDouble(value, -Float.MAX_VALUE, Float.MAX_VALUE));
                }
                else if (type == boolean.class) {
                    this.field.setBoolean(this.owner, parseBoolean(value));
                }
                else if (type == byte.class) {
                    this.field.setByte(this.owner, (byte) parseInt(value, Byte.MIN_VALUE, Byte.MAX_VALUE));
                }
                else if (type == long.class) {
                    this.field.setLong(this.owner, parseLong(value, Long.MIN_VALUE, Long.MAX_VALUE));
                }
                else if (type == short.class) {
                    this.field.setShort(this.owner, (short) parseInt(value, Short.MIN_VALUE, Short.MAX_VALUE));
                }
                else if (type == char.class) {
                    if (value.length() != 1) throw new CommandException("Config option %s requires one character", this.name);
                    this.field.setChar(this.owner, value.charAt(0));
                }
                else if (type == String.class) {
                    this.field.set(this.owner, value);
                }
                else if (type.isEnum()) {
                    Object selected = null;
                    for (Object enumValue : type.getEnumConstants()) {
                        if (enumValue.toString().equalsIgnoreCase(value)) {
                            selected = enumValue;
                            break;
                        }
                    }
                    if (selected == null) {
                        throw new CommandException("Unknown value %s for config option %s", value, this.name);
                    }
                    this.field.set(this.owner, selected);
                }

                this.syncMethod.invoke(null);
                sender.sendMessage(new TextComponentString("Set " + this.name + " = " + this.field.get(this.owner)));
            }
            catch (IllegalAccessException | InvocationTargetException exception) {
                throw new CommandException("Could not update config option %s: %s", this.name, exception.getMessage());
            }
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
            if (args.length != 1) {
                return Collections.emptyList();
            }
            if (this.field.getType() == boolean.class) {
                return getListOfStringsMatchingLastWord(args, "true", "false");
            }
            if (this.field.getType().isEnum()) {
                return getListOfStringsMatchingLastWord(args, Arrays.asList(this.field.getType().getEnumConstants()));
            }
            return Collections.emptyList();
        }
    }
}
