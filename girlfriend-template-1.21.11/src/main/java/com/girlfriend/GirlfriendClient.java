package com.girlfriend;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.text.Text;

public class GirlfriendClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(GirlfriendMod.GIRLFRIEND, GirlfriendRenderer::new);

        // /gfskin reloads config/girlfriend/skin.png without restarting
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("gfskin").executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal("[Girlfriend] " + GirlfriendSkin.reload()));
                    return 1;
                })));
    }
}
