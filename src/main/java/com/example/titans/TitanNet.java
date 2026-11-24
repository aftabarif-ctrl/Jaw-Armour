package com.example.titans;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class TitanNet {
    public static final int TRANSFORM = 0, AB1 = 1, AB2 = 2, AB3 = 3, DODGE = 4, DOUBLE_JUMP = 5, CLIMB = 6;

    /** client -> server */
    public record ActionPayload(int action, int arg) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(Titans.id("action"));
        public static final StreamCodec<ByteBuf, ActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ActionPayload::action, ByteBufCodecs.VAR_INT, ActionPayload::arg, ActionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** server -> client: form + remaining cooldown ticks */
    public record StatePayload(int form, int c0, int c1, int c2, int c3) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(Titans.id("state"));
        public static final StreamCodec<ByteBuf, StatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StatePayload::form, ByteBufCodecs.VAR_INT, StatePayload::c0,
            ByteBufCodecs.VAR_INT, StatePayload::c1, ByteBufCodecs.VAR_INT, StatePayload::c2,
            ByteBufCodecs.VAR_INT, StatePayload::c3, StatePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** server -> client: camera shake (intensity in 1/100 degrees, duration ticks) */
    public record ShakePayload(int intensity, int ticks) implements CustomPacketPayload {
        public static final Type<ShakePayload> TYPE = new Type<>(Titans.id("shake"));
        public static final StreamCodec<ByteBuf, ShakePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShakePayload::intensity, ByteBufCodecs.VAR_INT, ShakePayload::ticks, ShakePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShakePayload.TYPE, ShakePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, ctx) -> TitanManager.handleAction(ctx.player(), p));
    }
}
