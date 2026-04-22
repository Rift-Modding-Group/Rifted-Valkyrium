package org.valkyrienskies.mod.common.command.config;

abstract class ConfigCommandNode {

    private final String name;

    ConfigCommandNode(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

}
