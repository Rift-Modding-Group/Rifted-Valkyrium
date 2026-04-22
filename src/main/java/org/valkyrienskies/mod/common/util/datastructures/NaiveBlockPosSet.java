package org.valkyrienskies.mod.common.util.datastructures;

import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Naive implementation of IBlockPosSet, basically just a wrapper around a HashSet<BlockPos>.
 * Used for testing purposes only.
 */
public class NaiveBlockPosSet implements IBlockPosSet {

    private final Set<BlockPos> blockPosSet;

    public NaiveBlockPosSet() {
        this.blockPosSet = new HashSet<>();
    }

    @Override
    public boolean add(int x, int y, int z) {
        return blockPosSet.add(new BlockPos(x, y, z));
    }

    @Override
    public boolean remove(int x, int y, int z) {
        return blockPosSet.remove(new BlockPos(x, y, z));
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return blockPosSet.contains(new BlockPos(x, y, z));
    }

    @Override
    public boolean canStore(int x, int y, int z) {
        return true;
    }

    @Override
    public void clear() {
        blockPosSet.clear();
    }

    @Override
    public int size() {
        return blockPosSet.size();
    }

    @Nonnull
    @Override
    public Iterator<BlockPos> iterator() {
        return blockPosSet.iterator();
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
        return blockPosSet.containsAll(c);
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends BlockPos> c) {
        return blockPosSet.addAll(c);
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        return blockPosSet.retainAll(c);
    }

    @Override
    public Object[] toArray() {
        return blockPosSet.toArray();
    }

    @Override
    public <T> T[] toArray(@Nonnull T[] a) {
        return blockPosSet.toArray(a);
    }

}
