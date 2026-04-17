package dev.quantumcore.memory;

import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Specializes object interning for BlockState because large packs repeatedly create equivalent
 * state instances during chunk decode and model pipeline operations.
 * Thread-safety: thread-safe; delegates to concurrent interner.
 */
public final class BlockStateInterner {
    private final Map<BlockState, WeakReference<BlockState>> statePool = new WeakHashMap<>();
    private final ObjectInterner<Map<?, ?>> propertyMapInterner = new ObjectInterner<>(map -> Map.copyOf(map));

    /**
     * Keeps construction explicit so this interner can be replaced by dependency injection later
     * without API churn.
     */
    public BlockStateInterner() {
    }

    /**
     * Returns canonical blockstate instance to collapse duplicate state objects and lower resident
     * heap usage under dense chunk loads.
     */
    public synchronized BlockState intern(BlockState state) {
        WeakReference<BlockState> existingRef = statePool.get(state);
        if (existingRef != null) {
            BlockState existing = existingRef.get();
            if (existing != null) {
                return existing;
            }
        }
        statePool.put(state, new WeakReference<>(state));
        propertyMapInterner.intern(state.getValues());
        return state;
    }

    /**
     * Exposes canonical-count trend so pack maintainers can verify deduplication impact.
     */
    public int approximateCanonicalCount() {
        return statePool.size();
    }
}
