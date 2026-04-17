package dev.quantumcore.memory;

import net.minecraft.core.BlockPos;

/**
 * Provides the requested `of/free` API so hot loops can avoid new BlockPos allocation per access.
 * Thread-safety: thread-safe because pooling is thread-local.
 */
public final class PooledBlockPos {
    private static final ObjectPool<MutableBlockPosHandle> POOL =
        new ObjectPool<>(4096, MutableBlockPosHandle::new, MutableBlockPosHandle::reset);

    private PooledBlockPos() {
    }

    /**
     * Centralizes pooled BlockPos acquisition so mods can migrate hot paths without redesigning
     * callsites.
     */
    public static MutableBlockPosHandle of(int x, int y, int z) {
        MutableBlockPosHandle handle = POOL.borrow();
        handle.x = x;
        handle.y = y;
        handle.z = z;
        return handle;
    }

    /**
     * Explicit release keeps lifetime control visible in callsites, which avoids accidental
     * retention that would negate pool effectiveness.
     */
    public static void free(MutableBlockPosHandle handle) {
        POOL.release(handle);
    }

    /**
     * Mutable wrapper avoids reallocating immutable BlockPos in tight loops while still providing
     * conversion when APIs need a standard BlockPos object.
     */
    public static final class MutableBlockPosHandle {
        private int x;
        private int y;
        private int z;

        private MutableBlockPosHandle() {
        }

        /**
         * Exposes x directly to avoid temporary object creation in tight loops.
         */
        public int x() {
            return x;
        }

        /**
         * Exposes y directly to avoid temporary object creation in tight loops.
         */
        public int y() {
            return y;
        }

        /**
         * Exposes z directly to avoid temporary object creation in tight loops.
         */
        public int z() {
            return z;
        }

        /**
         * Bridges pooled mutable coordinates to APIs that strictly require immutable BlockPos.
         */
        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }

        private void reset() {
            x = 0;
            y = 0;
            z = 0;
        }
    }
}
