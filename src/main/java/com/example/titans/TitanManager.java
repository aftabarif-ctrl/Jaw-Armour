package com.example.titans;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class TitanManager {
    private static final Map<UUID, TitanState> STATES = new HashMap<>();
    private static final int TRANSFORM_TICKS = 60;

    public static TitanState st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), k -> new TitanState()); }
    public static boolean isTitan(LivingEntity e) { return e instanceof ServerPlayer p && st(p).type != TitanType.NONE; }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(s -> { for (ServerPlayer p : s.getPlayerList().getPlayers()) tick(p); });
        // invulnerable during the transformation cinematic
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> !(e instanceof ServerPlayer p && st(p).transformTicks > 0));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer sp && st(sp).type == TitanType.ARMORED && entity instanceof LivingEntity le)
                TitanAbilities.armoredPunch(sp, le);
            return InteractionResult.PASS;
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((o, n, alive) -> STATES.remove(n.getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> STATES.remove(h.getPlayer().getUUID()));
    }

    // ---------- transformation ----------
    public static void toggle(ServerPlayer p, TitanType t) {
        TitanState s = st(p);
        if (s.transformTicks > 0 || t == TitanType.NONE) return;
        s.pending = (s.type == t) ? TitanType.NONE : t;
        s.transformTicks = TRANSFORM_TICKS;
        s.chargeTicks = s.comboTicks = s.leapTicks = 0;
    }
    private static boolean mayUseKeybind(ServerPlayer p) {
        if (p.isCreative()) return true;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it.is(Titans.ARMORED_SERUM) || it.is(Titans.JAW_SERUM)) return true;
        }
        return false;
    }

    private static void transformTick(ServerPlayer p, TitanState s, ServerLevel lv) {
        int t = s.transformTicks--;           // 60 -> 1
        Vec3 pos = p.position();
        Fx.push(p, new Vec3(0, Math.min(p.getDeltaMovement().y, 0), 0));
        if (t == TRANSFORM_TICKS) Fx.sound(lv, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.7f);
        if (t % 10 == 0) {
            double a = lv.getRandom().nextDouble() * Math.PI * 2, d = 3 + lv.getRandom().nextDouble() * 5;
            Fx.lightning(lv, pos.add(Math.cos(a) * d, 0, Math.sin(a) * d));
        }
        if (t % 6 == 0) {
            Fx.ring(lv, pos, (TRANSFORM_TICKS - t) * 0.25, ParticleTypes.CLOUD, 48);
            Fx.smoke(lv, pos, 1.5, 12);
            Fx.sound(lv, pos, SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
        }
        if (t % 4 == 0) Fx.debris(lv, pos, 2.5, 20);
        if (t % 5 == 0) Fx.shake(lv, pos, 1.5f + (TRANSFORM_TICKS - t) * 0.05f, 6);
        if (s.transformTicks == 0) {           // finish
            s.type = s.pending;
            applyStats(p, s.type);
            Fx.lightning(lv, pos);
            Fx.ring(lv, pos, 3, ParticleTypes.EXPLOSION, 24);
            Fx.ring(lv, pos, 8, ParticleTypes.CLOUD, 96);
            Fx.smoke(lv, pos, 4, 80);
            Fx.debris(lv, pos, 5, 120);
            Fx.sound(lv, pos, SoundEvents.ENDER_DRAGON_GROWL, 5f, 0.6f);
            Fx.sound(lv, pos, SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.8f);
            Fx.shake(lv, pos, 8f, 30);
            TitanAbilities.shockwave(p, lv, 10, 6, 1.6, 0.9);
        }
    }

    // ---------- attributes ----------
    private static final List<Holder<Attribute>> ATTRS = List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ARMOR,
        Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH,
        Attributes.STEP_HEIGHT, Attributes.SAFE_FALL_DISTANCE, Attributes.FALL_DAMAGE_MULTIPLIER,
        Attributes.ENTITY_INTERACTION_RANGE, Attributes.BLOCK_INTERACTION_RANGE, Attributes.ATTACK_DAMAGE);

    private static void mod(ServerPlayer p, int idx, double v, AttributeModifier.Operation op) {
        AttributeInstance i = p.getAttribute(ATTRS.get(idx));
        if (i != null) i.addOrUpdateTransientModifier(new AttributeModifier(Titans.id("titan_" + idx), v, op));
    }
    public static void applyStats(ServerPlayer p, TitanType t) {
        for (int i = 0; i < ATTRS.size(); i++) { AttributeInstance a = p.getAttribute(ATTRS.get(i)); if (a != null) a.removeModifier(Titans.id("titan_" + i)); }
        if (t == TitanType.NONE) { if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth()); return; }
        var ADD = AttributeModifier.Operation.ADD_VALUE;
        mod(p, 0, t.scale - 1, ADD);  mod(p, 1, t.health, ADD); mod(p, 2, t.armor, ADD); mod(p, 3, t.toughness, ADD);
        mod(p, 4, t.kbRes, ADD);      mod(p, 5, t.speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(p, 6, t.jump, ADD);       mod(p, 7, 2.5, ADD);       mod(p, 8, 1000, ADD);
        mod(p, 9, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);   // fall damage immunity
        mod(p, 10, t.scale * 1.5, ADD); mod(p, 11, t.scale * 1.5, ADD); mod(p, 12, t.damage, ADD);
        p.setHealth(p.getMaxHealth());
    }

    // ---------- tick ----------
    private static void tick(ServerPlayer p) {
        TitanState s = st(p);
        ServerLevel lv = (ServerLevel) p.level();
        if (s.transformTicks > 0) { transformTick(p, s, lv); return; }
        if (s.type == TitanType.NONE) return;
        p.resetFallDistance();
        if (p.onGround()) s.usedDouble = false;
        // heavy footsteps
        Vec3 dm = p.getDeltaMovement();
        if (p.onGround() && dm.horizontalDistanceSqr() > 0.004 && s.chargeTicks == 0 && ++s.stepTimer >= (s.type == TitanType.ARMORED ? 10 : 7)) {
            s.stepTimer = 0;
            Fx.debris(lv, p.position(), 1.2, 12);
            Fx.smoke(lv, p.position(), 0.8, 4);
            Fx.sound(lv, p.position(), SoundEvents.IRON_GOLEM_STEP, 3f, 0.4f);
            Fx.shake(lv, p.position(), s.type == TitanType.ARMORED ? 1.2f : 0.6f, 4);
        }
        if (s.type == TitanType.ARMORED) TitanAbilities.armoredTick(p, s, lv); else TitanAbilities.jawTick(p, s, lv);
        if (p.tickCount % 4 == 0) {
            long now = lv.getGameTime();
            ServerPlayNetworking.send(p, new TitanNet.StatePayload(s.type.ordinal(), rem(s,0,now), rem(s,1,now), rem(s,2,now), rem(s,3,now)));
        }
    }
    private static int rem(TitanState s, int i, long now) { return (int) Math.max(0, s.cd[i] - now); }

    // ---------- input ----------
    public static void handleAction(ServerPlayer p, TitanNet.ActionPayload a) {
        TitanState s = st(p);
        if (s.transformTicks > 0) return;
        if (a.action() == TitanNet.TRANSFORM) { if (mayUseKeybind(p)) toggle(p, TitanType.of(a.arg())); return; }
        if (s.type == TitanType.NONE) return;
        TitanAbilities.perform(p, s, a.action(), a.arg());
    }
}
