# Armored & Jaw Titans (Fabric, MC 26.3)
Build: JDK 25, Gradle 9.5+ ->  gradle build   (jar in build/libs). Add the Gradle wrapper with: gradle wrapper
Controls: Z / X transform (needs a serum in inventory, or creative) - or right-click a serum item.
R / G / V abilities, Alt dodge, double-tap jump = double jump (Jaw), sprint-jump into a wall + forward = climb (Jaw).
Armored: R Charge, G Slam, V Roar, left-click punch (launch + block break).
Jaw: R Leap, G Bite (execute weakened Titans <30% HP), V Claw combo.
Your models/textures: the Titans are the player entity scaled via attributes (multiplayer-safe, no custom entity).
To show your own model, add a client-side PlayerRenderer override (mixin on AvatarRenderer/PlayerRenderer) that swaps
the model when TitansClient type != NONE. Item textures go in assets/titans/textures/item/. Replace sounds in Fx.java.
Untested: written against 26.3 Mojang mappings without a compiler. Likely spots to adjust: Fx.push (hurtMarked),
KeyMapping.Category, HUD GuiGraphics calls, SoundEvents.GENERIC_EXPLODE.value().
