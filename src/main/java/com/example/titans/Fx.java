package com.example.titans;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** All particles/sound/shake helpers. Swap SoundEvents for your own custom sounds here. */
public final class Fx {
    public static void sound(ServerLevel l, Vec3 p, SoundEvent s, float vol, float pitch) {
        l.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }
    public static void shake(ServerLevel l, Vec3 at, float intensity, int ticks) {
        for (ServerPlayer sp : l.players()) {
            double d = Math.sqrt(sp.position().distanceToSqr(at));
            if (d < 48) ServerPlayNetworking.send(sp, new TitanNet.ShakePayload((int) (intensity * (1.0 - d / 48.0) * 100), ticks));
        }
    }
    public static void ring(ServerLevel l, Vec3 c, double r, ParticleOptions o, int pts) {
        for (int i = 0; i < pts; i++) {
            double a = Math.PI * 2 * i / pts;
            l.sendParticles(o, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0.03);
        }
    }
    public static void debris(ServerLevel l, Vec3 c, double spread, int count) {
        BlockState st = l.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (st.isAir()) return;
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), c.x, c.y + 0.3, c.z, count, spread, 0.4, spread, 0.35);
    }
    public static void smoke(ServerLevel l, Vec3 c, double spread, int count) {
        l.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, count, spread, 0.6, spread, 0.04);
        l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.5, c.z, Math.max(1, count / 3), spread, 0.3, spread, 0.06);
    }
    public static void lightning(ServerLevel l, Vec3 p) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(l, EntitySpawnReason.EVENT);
        if (b == null) return;
        b.setPos(p.x, p.y, p.z);
        b.setVisualOnly(true);
        l.addFreshEntity(b);
    }
    /** Single place to fix if the velocity-sync API changes between versions. */
    public static void push(Entity e, Vec3 v) { e.setDeltaMovement(v); e.hurtMarked = true; }
}
