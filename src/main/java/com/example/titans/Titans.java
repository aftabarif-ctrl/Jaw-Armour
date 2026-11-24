package com.example.titans;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;

public class Titans implements ModInitializer {
    public static final String MOD_ID = "titans";
    public static Item ARMORED_SERUM, JAW_SERUM;
    public static Identifier id(String p) { return Identifier.fromNamespaceAndPath(MOD_ID, p); }

    private static Item reg(String name, TitanType t) {
        Identifier id = id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, id, new SerumItem(new Item.Properties().setId(key).stacksTo(1), t));
    }
    @Override public void onInitialize() {
        ARMORED_SERUM = reg("armored_serum", TitanType.ARMORED);
        JAW_SERUM = reg("jaw_serum", TitanType.JAW);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> { e.accept(ARMORED_SERUM); e.accept(JAW_SERUM); });
        TitanNet.init();
        TitanManager.init();
    }
}
