package com.example.titans.client;
import com.example.titans.TitanNet;
import com.example.titans.TitanType;
import com.example.titans.Titans;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public class TitansClient implements ClientModInitializer {
    static TitanType type = TitanType.NONE;
    static int[] cd = new int[4];
    static float shake; static int shakeTicks; static float offX, offY;
    static boolean wasJump;
    static final java.util.Random RNG = new java.util.Random();
    static KeyMapping ARMORED, JAW, AB1, AB2, AB3, DODGE;

    static KeyMapping key(String n, int k, KeyMapping.Category c) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.titans." + n, InputConstants.Type.KEYSYM, k, c));
    }
    @Override public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Titans.id("keys"));
        ARMORED = key("armored", GLFW.GLFW_KEY_Z, cat); JAW = key("jaw", GLFW.GLFW_KEY_X, cat);
        AB1 = key("ability1", GLFW.GLFW_KEY_R, cat); AB2 = key("ability2", GLFW.GLFW_KEY_G, cat);
        AB3 = key("ability3", GLFW.GLFW_KEY_V, cat); DODGE = key("dodge", GLFW.GLFW_KEY_LEFT_ALT, cat);

        ClientPlayNetworking.registerGlobalReceiver(TitanNet.StatePayload.TYPE, (p, ctx) -> {
            type = TitanType.of(p.form()); cd[0] = p.c0(); cd[1] = p.c1(); cd[2] = p.c2(); cd[3] = p.c3(); });
        ClientPlayNetworking.registerGlobalReceiver(TitanNet.ShakePayload.TYPE, (p, ctx) -> {
            float i = p.intensity() / 100f; if (i >= shake || shakeTicks <= 0) { shake = i; shakeTicks = p.ticks(); } });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            var pl = mc.player; if (pl == null) { type = TitanType.NONE; return; }
            // camera shake: undo last tick offset, apply new one
            pl.setXRot(pl.getXRot() - offX); pl.setYRot(pl.getYRot() - offY); offX = offY = 0;
            if (shakeTicks > 0) { shakeTicks--; offX = (RNG.nextFloat() - 0.5f) * shake; offY = (RNG.nextFloat() - 0.5f) * shake; shake *= 0.9f;
                pl.setXRot(pl.getXRot() + offX); pl.setYRot(pl.getYRot() + offY); }
            while (ARMORED.consumeClick()) ClientPlayNetworking.send(new TitanNet.ActionPayload(TitanNet.TRANSFORM, TitanType.ARMORED.ordinal()));
            while (JAW.consumeClick()) ClientPlayNetworking.send(new TitanNet.ActionPayload(TitanNet.TRANSFORM, TitanType.JAW.ordinal()));
            if (type == TitanType.NONE) return;
            while (AB1.consumeClick()) ClientPlayNetworking.send(new TitanNet.ActionPayload(TitanNet.AB1, 0));
            while (AB2.consumeClick()) ClientPlayNetworking.send(new TitanNet.ActionPayload(TitanNet.AB2, 0));
            while (AB3.consumeClick()) ClientPlayNetworking.send(new TitanNet.ActionPayload(TitanNet.AB3, 0));
            while (DODGE.consumeClick()) {
                int dir = mc.options.keyLeft.isDown() ? 1 : mc.options.keyRight.isDown() ? 2 : mc.options.keyUp.isDown() ? 3 : 0;
                ClientPlayNetworking.send(new TitanNet.ActionPayload(TitanNet.DODGE, dir));
            }
            if (type == TitanType.JAW) {
                boolean jump = mc.options.keyJump.isDown();
                if (jump && !wasJump && !pl.onGround()) ClientPlayNetworking.send(new TitanNet.ActionPayload(TitanNet.DOUBLE_JUMP, 0));
                if (jump && mc.options.keyUp.isDown() && pl.horizontalCollision) ClientPlayNetworking.send(new TitanNet.ActionPayload(TitanNet.CLIMB, 0));
                wasJump = jump;
            }
        });
        // HUD (if the GuiGraphics API differs in your exact 26.3 build, this is the only block to adapt)
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Titans.id("titan_hud"), TitansClient::hud);
    }

    static void hud(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (type == TitanType.NONE || mc.player == null) return;
        boolean a = type == TitanType.ARMORED;
        String[] names = a ? new String[]{"[R] Charge","[G] Ground Slam","[V] Roar"," "} : new String[]{"[R] Leap","[G] Bite","[V] Claw Combo","[Alt] Dodge"};
        int x = 10, y = g.guiHeight() - 90;
        g.fill(x - 4, y - 18, x + 150, y + (a ? 42 : 56), 0x90000000);
        g.drawString(mc.font, a ? "ARMORED TITAN" : "JAW TITAN", x, y - 12, a ? 0xFFC8A064 : 0xFFE05040, true);
        float hp = mc.player.getHealth() / mc.player.getMaxHealth();
        g.fill(x, y, x + 140, y + 5, 0xFF301010); g.fill(x, y, x + (int) (140 * hp), y + 5, 0xFFD03030);
        for (int i = 0; i < (a ? 3 : 4); i++) {
            int ry = y + 10 + i * 12; boolean ready = cd[i] <= 0;
            g.drawString(mc.font, names[i] + (ready ? "" : String.format("  %.1fs", cd[i] / 20f)), x, ry, ready ? 0xFF80FF80 : 0xFFAAAAAA, true);
        }
    }
}
