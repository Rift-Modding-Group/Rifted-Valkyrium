package valkyrienwarfare.api;

/**
 * Each mass enum is to have a fixed numerical mass in kg.
 * */
public enum BlockMass {
    NONE(0),
    VERY_LIGHT(500),
    LIGHT(1000),
    HEAVY(8000),
    VERY_HEAVY(20000);

    public final int mass;

    BlockMass(int mass) {
        this.mass = mass;
    }
}
