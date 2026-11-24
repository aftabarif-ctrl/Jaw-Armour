package com.example.titans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import java.util.List;

public final class TitanAbilities {
    // cooldown ticks (20 = 1s)
    static final int CD_CHARGE = 160, CD_SLAM = 100, CD_ROAR = 200, CD_LEAP = 30, CD_BITE = 40, CD_COMBO = 20, CD_DODGE = 20;

    static boolean ready(ServerPlayer p, TitanState s, int i, int cd) {
        long now = p.level().getGameTime();
        if (now < s.cd[i]) return false;
        s.cd[i] = now + cd; return true;
    }
    static void hit(ServerPlayer p, LivingEntity t, float dmg) {
        t.hurtServer((ServerLevel) p.level(), p.damageSources().playerAttack(p), dmg);
    }
    static Vec3 flatLook(ServerPlayer p) {
        Vec3 l = p.getLookAngle(); Vec3 f = new Vec3(l.x, 0, l.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }
    static List<LivingEntity> near(ServerPlayer p, AABB box) {
        return p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator());
    }
    /** Breaks blocks in box; skips unbreakable and blocks below feet level. Returns count. */
    static int smash(ServerPlayer p, ServerLevel lv, AABB box, float maxHardness, int limit) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (n >= limit) break;
            if (pos.getY() < p.getBlockY()) continue;
            BlockState b = lv.getBlockState(pos);
            if (b.isAir()) continue;
            float h = b.getDestroySpeed(lv, pos);
            if (h < 0 || h > maxHardness) continue;
            if (n % 6 == 0) Fx.debris(lv, Vec3.atCenterOf(pos).add(0, -0.5, 0), 0.5, 6);
            lv.destroyBlock(pos.immutable(), false);
            n++;
        }
        if (n > 0) Fx.sound(lv, p.position(), SoundEvents.WITHER_BREAK_BLOCK, 1.5f, 0.6f);
        return n;
    }
    /** Radial shockwave: damage + launch + ring particles. */
    static void shockwave(ServerPlayer p, ServerLevel lv, double r, float dmg, double outward, double up) {
        Vec3 c = p.position();
        Fx.ring(lv, c, r * 0.4, ParticleTypes.CLOUD, 40); Fx.ring(lv, c, r * 0.75, ParticleTypes.CLOUD, 64); Fx.ring(lv, c, r, ParticleTypes.POOF, 90);
        for (LivingEntity e : near(p, p.getBoundingBox().inflate(r, 2, r))) {
            Vec3 d = e.position().subtract(c).multiply(1, 0, 1);
            d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
            if (dmg > 0) hit(p, e, dmg);
            Fx.push(e, d.scale(outward).add(0, up, 0));
        }
    }

    // ---------- dispatch ----------
    static void perform(ServerPlayer p, TitanState s, int action, int arg) {
        ServerLevel lv = (ServerLevel) p.level();
        boolean armored = s.type == TitanType.ARMORED;
        switch (action) {
            case TitanNet.AB1 -> { if (armored) { if (ready(p, s, 0, CD_CHARGE)) { s.chargeTicks = 70; Fx.sound(lv, p.position(), SoundEvents.RAVAGER_ROAR, 4f, 0.5f); } }
                                    else if (ready(p, s, 0, CD_LEAP)) leap(p, s, lv); }
            case TitanNet.AB2 -> { if (armored) { if (ready(p, s, 1, CD_SLAM)) slam(p, lv); }
                                    else if (ready(p, s, 1, CD_BITE)) bite(p, lv); }
            case TitanNet.AB3 -> { if (armored) { if (ready(p, s, 2, CD_ROAR)) roar(p, lv); }
                                    else if (ready(p, s, 2, CD_COMBO)) { s.comboTicks = 8; } }
            case TitanNet.DODGE -> { if (!armored && ready(p, s, 3, CD_DODGE)) dodge(p, lv, arg); }
            case TitanNet.DOUBLE_JUMP -> { if (!armored && !p.onGround() && !s.usedDouble) {
                s.usedDouble = true; Vec3 m = p.getDeltaMovement(); Fx.push(p, new Vec3(m.x, 0.95, m.z));
                Fx.ring(lv, p.position(), 1.5, ParticleTypes.CLOUD, 20); Fx.sound(lv, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.4f); } }
            case TitanNet.CLIMB -> { if (!armored) s.climbTicks = 3; }
            default -> {}
        }
    }

    // ---------- Armored ----------
    static void armoredPunch(ServerPlayer p, LivingEntity target) {
        ServerLevel lv = (ServerLevel) p.level();
        Vec3 look = p.getLookAngle();
        hit(p, target, 10f);
        Fx.push(target, new Vec3(look.x * 2.4, 0.9, look.z * 2.4));
        Vec3 c = p.getEyePosition().add(look.scale(3.5));
        smash(p, lv, new AABB(c, c).inflate(2.0), 10f, 60);
        Fx.sound(lv, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.6f);
        Fx.shake(lv, p.position(), 2.5f, 8);
        lv.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
    }
    static void slam(ServerPlayer p, ServerLevel lv) {
        Fx.sound(lv, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.5f);
        Fx.sound(lv, p.position(), SoundEvents.ANVIL_LAND, 4f, 0.4f);
        shockwave(p, lv, 12, 14f, 1.8, 1.1);
        Fx.debris(lv, p.position(), 5, 150); Fx.smoke(lv, p.position(), 4, 60);
        smash(p, lv, p.getBoundingBox().inflate(3, 0, 3).move(0, 0.5, 0), 4f, 80);
        Fx.shake(lv, p.position(), 7f, 20);
    }
    static void roar(ServerPlayer p, ServerLevel lv) {
        Fx.sound(lv, p.position(), SoundEvents.ENDER_DRAGON_GROWL, 6f, 0.5f);
        shockwave(p, lv, 16, 0f, 1.2, 0.4);
        Fx.smoke(lv, p.position(), 3, 50); Fx.shake(lv, p.position(), 5f, 40);
    }
    static void armoredTick(ServerPlayer p, TitanState s, ServerLevel lv) {
        if (s.chargeTicks <= 0) return;
        s.chargeTicks--;
        Vec3 dir = flatLook(p), m = p.getDeltaMovement();
        Fx.push(p, new Vec3(dir.x * 1.3, m.y, dir.z * 1.3));          // never slowed by terrain
        AABB front = p.getBoundingBox().move(dir.scale(2.0)).inflate(0.8, 0, 0.8);
        smash(p, lv, front, 50f, 250);
        for (LivingEntity e : near(p, front.inflate(1.5))) { hit(p, e, 16f); Fx.push(e, new Vec3(dir.x * 2.2, 0.8, dir.z * 2.2)); }
        Fx.debris(lv, p.position(), 2, 14); Fx.smoke(lv, p.position(), 1.5, 6);
        if (s.chargeTicks % 4 == 0) { Fx.sound(lv, p.position(), SoundEvents.IRON_GOLEM_STEP, 3f, 0.3f); Fx.shake(lv, p.position(), 2.5f, 5); }
    }

    // ---------- Jaw ----------
    static void leap(ServerPlayer p, TitanState s, ServerLevel lv) {
        Vec3 l = p.getLookAngle();
        Fx.push(p, new Vec3(l.x * 2.0, 0.7 + Math.max(0, l.y) * 0.8, l.z * 2.0));
        s.leapTicks = 14; s.leapHits.clear();
        Fx.sound(lv, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.8f);
        Fx.ring(lv, p.position(), 2, ParticleTypes.CLOUD, 24);
    }
    static void dodge(ServerPlayer p, ServerLevel lv, int arg) {
        Vec3 f = flatLook(p), right = new Vec3(-f.z, 0, f.x);
        Vec3 d = switch (arg) { case 1 -> right.scale(-1); case 2 -> right; case 3 -> f; default -> f.scale(-1); };
        Fx.push(p, d.scale(1.8).add(0, 0.2, 0));
        Fx.ring(lv, p.position(), 1.5, ParticleTypes.POOF, 16);
        Fx.sound(lv, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 1.6f);
    }
    static void bite(ServerPlayer p, ServerLevel lv) {
        double reach = 13;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), end = eye.add(look.scale(reach));
        EntityHitResult r = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(look.scale(reach)).inflate(2.0),
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), reach * reach);
        Fx.sound(lv, p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 2f, 0.5f);
        if (r == null || !(r.getEntity() instanceof LivingEntity t)) return;
        boolean titan = TitanManager.isTitan(t);
        boolean weak = t.getHealth() / t.getMaxHealth() <= 0.30f;
        if (titan && weak) {                      // neck-bite execution
            t.hurtServer(lv, p.damageSources().genericKill(), Float.MAX_VALUE);
            Fx.shake(lv, t.position(), 6f, 20);
            Fx.sound(lv, t.position(), SoundEvents.WARDEN_SONIC_BOOM, 3f, 1.2f);
        } else if (titan) hit(p, t, 45f);
        else if (t.getMaxHealth() <= 300f) hit(p, t, 10000f);   // instakill most mobs
        else hit(p, t, 80f);
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
        lv.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.4);
        lv.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 12, 0.4, 0.4, 0.4, 0.1);
        Fx.push(p, look.scale(0.6));
    }
    static void jawTick(ServerPlayer p, TitanState s, ServerLevel lv) {
        Vec3 m = p.getDeltaMovement();
        if (s.climbTicks > 0) { s.climbTicks--; if (p.horizontalCollision) { Fx.push(p, new Vec3(m.x * 0.1, 0.5, m.z * 0.1)); Fx.debris(lv, p.position(), 0.4, 3); } }
        if (s.leapTicks > 0) {
            s.leapTicks--;
            for (LivingEntity e : near(p, p.getBoundingBox().inflate(1.5)))
                if (s.leapHits.add(e.getUUID())) { hit(p, e, 12f); Fx.push(e, new Vec3(m.x * 0.5, 0.5, m.z * 0.5)); }
            if (p.onGround() && s.leapTicks < 10) { s.leapTicks = 0; shockwave(p, lv, 5, 4f, 0.8, 0.5); }
        }
        if (s.comboTicks > 0) {
            s.comboTicks--;
            if (s.comboTicks % 2 == 0) {                       // 4 rapid sweeps
                Vec3 f = flatLook(p);
                AABB box = p.getBoundingBox().move(f.scale(2.2)).inflate(1.2);
                for (LivingEntity e : near(p, box)) { hit(p, e, 6f); Fx.push(e, new Vec3(f.x * 0.5, 0.2, f.z * 0.5)); }
                Vec3 c = p.getEyePosition().add(f.scale(2.5));
                lv.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y - 0.5, c.z, 3, 0.8, 0.4, 0.8, 0);
                Fx.sound(lv, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 1.3f + 0.1f * s.comboTicks);
            }
        }
    }
}
