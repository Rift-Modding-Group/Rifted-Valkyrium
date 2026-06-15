package org.valkyrienskies.mod.common.physics.physx;

import physx.physics.PxFilterData;
import physx.physics.PxShape;

/**
 * Deals with the PhysX collision groups used by the VS projection backend.
 * PhysX filter word0 is the shape's own category bit, and word1 is the mask of
 * categories it may collide with. CollisionGroup bits are derived from enum
 * ordinal order, so append new groups instead of reordering existing ones.
 */
public class PhysXCollisionFilters {
    public enum CollisionGroup {
        //projected dynamic ship rigid bodies, collide with all PhysX-owned categories.
        SHIP,
        //static solid world block actors, collide with ships and entity proxies.
        WORLD,
        //liquid trigger actors, only participate with ships.
        LIQUID,
        //minecraft entities, collide with ships and world blocks.
        ENTITY;

        //category bit written to PhysX filter word0.
        public int bit() {
            return 1 << this.ordinal();
        }

        //collision mask written to PhysX filter word1.
        public int mask() {
            return switch (this) {
                case SHIP -> PhysXCollisionFilters.mask(SHIP, WORLD, LIQUID, ENTITY);
                case WORLD -> PhysXCollisionFilters.mask(SHIP, ENTITY);
                case LIQUID -> PhysXCollisionFilters.mask(SHIP);
                case ENTITY -> PhysXCollisionFilters.mask(SHIP, WORLD);
            };
        }

        //applies this group to both simulation and query filter data.
        public void setFilter(PxShape shape) {
            PxFilterData filterData = new PxFilterData(this.bit(), this.mask(), 0, 0);
            shape.setSimulationFilterData(filterData);
            shape.setQueryFilterData(filterData);
            filterData.destroy();
        }
    }

    //helper function to OR multiple collision group bits into one PhysX mask.
    private static int mask(CollisionGroup... groups) {
        int toReturn = 0;
        for (CollisionGroup group : groups) toReturn |= group.bit();
        return toReturn;
    }
}
