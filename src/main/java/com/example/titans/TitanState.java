package com.example.titans;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
public class TitanState {
    public TitanType type = TitanType.NONE, pending = TitanType.NONE;
    public int transformTicks, chargeTicks, comboTicks, leapTicks, climbTicks, stepTimer;
    public boolean usedDouble;
    public long[] cd = new long[4];            // 0 ab1, 1 ab2, 2 ab3, 3 dodge
    public final Set<UUID> leapHits = new HashSet<>();
}
