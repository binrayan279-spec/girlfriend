package com.girlfriend;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads your skin from .minecraft/config/girlfriend/skin.png (64x64 PNG). */
public final class GirlfriendSkin {
    private static final Identifier CUSTOM = Identifier.of(GirlfriendMod.ID, "custom_skin");
    private static final Identifier FALLBACK = Identifier.ofVanilla("textures/entity/player/slim/alex.png");
    private static boolean tried = false, loaded = false;

    private GirlfriendSkin() {}

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("girlfriend").resolve("skin.png");
    }

    public static Identifier texture() {
        if (!tried) { tried = true; reload(); }
        return loaded ? CUSTOM : FALLBACK;
    }

    public static String reload() {
        tried = true;
        try {
            Files.createDirectories(file().getParent());
            if (!Files.exists(file())) {
                loaded = false;
                return "No skin found. Put a 64x64 PNG at: " + file();
            }
            try (InputStream in = Files.newInputStream(file())) {
                NativeImage img = NativeImage.read(in);
                if (img.getWidth() != 64 || img.getHeight() != 64) {
                    int w = img.getWidth(), h = img.getHeight();
                    img.close();
                    loaded = false;
                    return "Skin must be 64x64 (yours is " + w + "x" + h + ").";
                }
                MinecraftClient.getInstance().getTextureManager()
                        .registerTexture(CUSTOM, new NativeImageBackedTexture(img));
                loaded = true;
                return "Skin loaded!";
            }
        } catch (Exception e) {
            loaded = false;
            return "Could not load skin: " + e;
        }
    }
}
