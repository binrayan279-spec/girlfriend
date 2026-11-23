package com.girlfriend;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class GirlfriendMod implements ModInitializer {
    public static final String ID = "girlfriend";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    private static final RegistryKey<EntityType<?>> KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(ID, "girlfriend"));
    public static final EntityType<GirlfriendEntity> GIRLFRIEND = Registry.register(
            Registries.ENTITY_TYPE, KEY,
            EntityType.Builder.create(GirlfriendEntity::new, SpawnGroup.CREATURE).dimensions(0.6f, 1.8f).build(KEY));

    @Override
    public void onInitialize() {
        GirlfriendConfig.get();
        FabricDefaultAttributeRegistry.register(GIRLFRIEND, GirlfriendEntity.createAttributes());

        // Any chat message you send near her goes to her; she answers in chat.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            GirlfriendEntity gf = findNearest(sender, GirlfriendConfig.get().talkRange);
            if (gf != null) GirlfriendAI.talk(gf, sender, message.getContent().getString());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
                CommandManager.literal("gf")
                        .then(CommandManager.literal("spawn").executes(c -> spawn(c.getSource())))
                        .then(CommandManager.literal("name").then(
                                CommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> rename(c.getSource(), StringArgumentType.getString(c, "name")))))
                        .then(CommandManager.literal("stay").executes(c -> stay(c.getSource(), true)))
                        .then(CommandManager.literal("follow").executes(c -> stay(c.getSource(), false)))
                        .then(CommandManager.literal("kiss").executes(c -> kiss(c.getSource())))));
    }

    static GirlfriendEntity findNearest(ServerPlayerEntity p, double range) {
        ServerWorld w = (ServerWorld) p.getWorld();
        List<GirlfriendEntity> list = w.getEntitiesByClass(GirlfriendEntity.class,
                p.getBoundingBox().expand(range), e -> p.getUuid().equals(e.getOwnerUuid()));
        return list.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(p))).orElse(null);
    }

    private static int spawn(ServerCommandSource src) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        GirlfriendEntity gf = findNearest(p, 128);
        if (gf != null) {
            gf.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0);
            gf.setStaying(false);
            src.sendFeedback(() -> Text.literal(gf.displayName() + " came over to you."), false);
            return 1;
        }
        ServerWorld w = (ServerWorld) p.getWorld();
        GirlfriendEntity e = new GirlfriendEntity(GIRLFRIEND, w);
        e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw() + 180f, 0);
        e.setOwner(p.getUuid());
        e.setCustomName(Text.literal(GirlfriendConfig.get().defaultName));
        e.setCustomNameVisible(true);
        w.spawnEntity(e);
        src.sendFeedback(() -> Text.literal("She's here! Use /gf name <name> to rename her. Sneak + right-click to kiss her."), false);
        return 1;
    }

    private static int rename(ServerCommandSource src, String name) throws CommandSyntaxException {
        GirlfriendEntity gf = findNearest(src.getPlayerOrThrow(), 128);
        if (gf == null) { src.sendError(Text.literal("No girlfriend nearby. Use /gf spawn first.")); return 0; }
        gf.setCustomName(Text.literal(name));
        gf.setCustomNameVisible(true);
        src.sendFeedback(() -> Text.literal("Her name is now " + name + " \u2665"), false);
        return 1;
    }

    private static int stay(ServerCommandSource src, boolean stay) throws CommandSyntaxException {
        GirlfriendEntity gf = findNearest(src.getPlayerOrThrow(), 128);
        if (gf == null) { src.sendError(Text.literal("No girlfriend nearby. Use /gf spawn first.")); return 0; }
        gf.setStaying(stay);
        src.sendFeedback(() -> Text.literal(gf.displayName() + (stay ? " will stay here." : " will follow you.")), false);
        return 1;
    }

    private static int kiss(ServerCommandSource src) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        GirlfriendEntity gf = findNearest(p, 4);
        if (gf == null) { src.sendError(Text.literal("You need to be right next to her.")); return 0; }
        gf.kiss(p);
        return 1;
    }
}
