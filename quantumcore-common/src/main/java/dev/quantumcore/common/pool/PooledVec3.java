package dev.quantumcore.common.pool;

import dev.quantumcore.common.concurrent.ThreadSafe;

@ThreadSafe
public final class PooledVec3 {
    private static final ObjectPool<PooledVec3> POOL = new ObjectPool<>(PooledVec3::new, 512);

    private double x;
    private double y;
    private double z;

    private PooledVec3() {
    }

    public static PooledVec3 of(double x, double y, double z) {
        PooledVec3 vec = POOL.borrow();
        vec.x = x;
        vec.y = y;
        vec.z = z;
        return vec;
    }

    public static void free(PooledVec3 vec) {
        POOL.release(vec);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }
}
