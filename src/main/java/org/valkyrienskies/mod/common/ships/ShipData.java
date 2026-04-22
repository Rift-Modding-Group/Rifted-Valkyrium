package org.valkyrienskies.mod.common.ships;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueAttribute;
import com.googlecode.cqengine.query.option.QueryOptions;
import net.minecraft.util.math.AxisAlignedBB;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ships.chunk_claims.VSChunkClaim;
import org.valkyrienskies.mod.common.ships.physics_data.ShipInertiaData;
import org.valkyrienskies.mod.common.ships.physics_data.ShipPhysicsData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.util.cqengine.ConcurrentUpdatableIndexedCollection;
import org.valkyrienskies.mod.common.util.datastructures.IBlockPosSet;
import org.valkyrienskies.mod.common.util.datastructures.IBlockPosSetAABB;
import org.valkyrienskies.mod.common.util.datastructures.SmallBlockPosSet;
import org.valkyrienskies.mod.common.util.datastructures.SmallBlockPosSetAABB;
import org.valkyrienskies.mod.common.util.jackson.annotations.PacketIgnore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.googlecode.cqengine.query.QueryFactory.attribute;
import static com.googlecode.cqengine.query.QueryFactory.nullableAttribute;

/**
 * One of these objects will represent a ship. You can obtain a physics object for that ship (if one
 * is available), by calling {@link IPhysObjectWorld#getPhysObjectFromUUID(UUID)}.
 */
public class ShipData {

    /**
     * The {@link QueryableShipData} that manages this
     */
    private final transient ConcurrentUpdatableIndexedCollection<ShipData> owner;

    // region Data Fields

    /**
     * Physics information -- mutable but final. References to this <strong>should be guaranteed to
     * never change</strong> for the duration of a game.
     */
    private final ShipPhysicsData physicsData;

    private final ShipInertiaData inertiaData;

    /**
     * Do not use this for anything client side! Contains all of the non-air block positions on the ship.
     * This is used for generating AABBs and deconstructing the ship.
     */
    @PacketIgnore
    @Nullable
    @JsonSerialize(as = SmallBlockPosSetAABB.class)
    @JsonDeserialize(as = SmallBlockPosSetAABB.class)
    public IBlockPosSetAABB blockPositions;

    /**
     * Do not use this for anything client side! Contains all the positions of force producing blocks on the ship.
     */
    @PacketIgnore
    @Nullable
    @JsonSerialize(as = SmallBlockPosSet.class)
    @JsonDeserialize(as = SmallBlockPosSet.class)
    public IBlockPosSet activeForcePositions;

    private ShipTransform shipTransform;

    private ShipTransform prevTickShipTransform;

    private AxisAlignedBB shipBB;

    /**
     * Whether or not physics are enabled on this physo
     */
    private boolean physicsEnabled;

    /**
     * The chunks claimed by this physo
     */
    private final VSChunkClaim chunkClaim;

    /**
     * This ships UUID
     */
    private final UUID uuid;

    /**
     * The (unique) name of the physo as displayed to players
     */
    private String name;

    // endregion

    private ShipData() {
        this.owner = null;
        this.physicsData = null;
        this.inertiaData = null;
        this.shipTransform = null;
        this.prevTickShipTransform = null;
        this.shipBB = null;
        this.physicsEnabled = false;
        this.chunkClaim = null;
        this.uuid = null;
        this.name = null;
        this.blockPositions = null;
        this.activeForcePositions = null;
    }

    private ShipData(ConcurrentUpdatableIndexedCollection<ShipData> owner,
                    ShipPhysicsData physicsData, @Nonnull ShipInertiaData inertiaData, ShipTransform shipTransform, ShipTransform prevTickShipTransform, AxisAlignedBB shipBB,
                    boolean physicsEnabled, VSChunkClaim chunkClaim, UUID uuid, String name) {
        this.owner = owner;
        this.physicsData = physicsData;
        this.inertiaData = Objects.requireNonNull(inertiaData, "inertiaData");
        this.shipTransform = Objects.requireNonNull(shipTransform, "shipTransform");
        this.prevTickShipTransform = Objects.requireNonNull(prevTickShipTransform, "prevTickShipTransform");
        this.shipBB = Objects.requireNonNull(shipBB, "shipBB");
        this.physicsEnabled = physicsEnabled;
        this.chunkClaim = Objects.requireNonNull(chunkClaim, "chunkClaim");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");

        this.blockPositions = new SmallBlockPosSetAABB(chunkClaim.getCenterPos().getXStart(), 0,
                chunkClaim.getCenterPos().getZStart(), 1024, 1024, 1024);
        this.activeForcePositions = new SmallBlockPosSet(chunkClaim.getCenterPos().getXStart(), chunkClaim.getCenterPos().getZStart());
    }

    public static ShipData createData(ConcurrentUpdatableIndexedCollection<ShipData> owner,
        String name, VSChunkClaim chunkClaim, UUID shipID,
        ShipTransform shipTransform,
        AxisAlignedBB aabb) {

        return new ShipData(owner, new ShipPhysicsData(new Vector3d(), new Vector3d()), new ShipInertiaData(), shipTransform, shipTransform, aabb,
            false, chunkClaim, shipID, name);
    }

    // region Setters

    public ShipData setName(String name) {
        this.name = name;
        owner.updateObjectIndices(this, NAME);
        return this;
    }

    public ShipPhysicsData getPhysicsData() {
        return physicsData;
    }

    public ShipInertiaData getInertiaData() {
        return inertiaData;
    }

    @Nullable
    public IBlockPosSetAABB getBlockPositions() {
        return blockPositions;
    }

    @Nullable
    public IBlockPosSet getActiveForcePositions() {
        return activeForcePositions;
    }

    public ShipTransform getShipTransform() {
        return shipTransform;
    }

    public void setShipTransform(ShipTransform shipTransform) {
        this.shipTransform = shipTransform;
    }

    public ShipTransform getPrevTickShipTransform() {
        return prevTickShipTransform;
    }

    public void setPrevTickShipTransform(ShipTransform prevTickShipTransform) {
        this.prevTickShipTransform = prevTickShipTransform;
    }

    public AxisAlignedBB getShipBB() {
        return shipBB;
    }

    public void setShipBB(AxisAlignedBB shipBB) {
        this.shipBB = shipBB;
    }

    public boolean isPhysicsEnabled() {
        return physicsEnabled;
    }

    public void setPhysicsEnabled(boolean physicsEnabled) {
        this.physicsEnabled = physicsEnabled;
    }

    public VSChunkClaim getChunkClaim() {
        return chunkClaim;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    // endregion

    // region Attributes

    public static final Attribute<ShipData, String> NAME = nullableAttribute(ShipData::getName);
    public static final Attribute<ShipData, UUID> UUID = attribute(ShipData::getUuid);
    public static final Attribute<ShipData, Long> CHUNKS = new MultiValueAttribute<ShipData, Long>() {
        @Override
        public Set<Long> getValues(ShipData physo, QueryOptions queryOptions) {
            return physo.getChunkClaim().getClaimedChunks();
        }
    };

    // endregion
}
