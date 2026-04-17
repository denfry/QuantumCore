package dev.quantumcore.common.pool;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public final class PooledBlockPos {
    private static final ObjectPool<PooledBlockPos> POOL = new ObjectPool<>(PooledBlockPos::new, 512);

    private int x;
    private int y;
    private int z;

    private PooledBlockPos() {
    }

    public static PooledBlockPos of(int x, int y, int z) {
        PooledBlockPos pos = POOL.borrow();
        pos.x = x;
        pos.y = y;
        pos.z = z;
        return pos;
    }

    public static void free(PooledBlockPos pos) {
        POOL.release(pos);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }
}
