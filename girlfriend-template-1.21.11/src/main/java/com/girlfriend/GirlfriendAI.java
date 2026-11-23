package com.girlfriend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Sends your chat to Claude and prints her reply in the in-game chat. */
public final class GirlfriendAI {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<UUID, List<String[]>> HISTORY = new ConcurrentHashMap<>();
    private static final String[] OFFLINE = {
            "Hehe, I love spending time with you \u2665", "Mhm? Tell me more!", "You're the best, you know that?",
            "I'm so happy we're here together.", "Aww, you're so sweet!"};
    private static boolean hintShown = false;
    private static final Random RANDOM = new Random();

    private GirlfriendAI() {}

    public static void talk(GirlfriendEntity gf, ServerPlayerEntity player, String userText) {
        GirlfriendConfig cfg = GirlfriendConfig.reload();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String name = gf.displayName();

        if (cfg.key().isBlank()) {
            if (!hintShown) {
                hintShown = true;
                player.sendMessage(Text.literal("[Girlfriend] No API key set. Put your Anthropic key in config/girlfriend.json (\"apiKey\") to enable real AI chat.")
                        .formatted(Formatting.GRAY), false);
            }
            deliver(player, name, OFFLINE[RANDOM.nextInt(OFFLINE.length)]);
            return;
        }

        List<String[]> hist = HISTORY.computeIfAbsent(gf.getUuid(), k -> new ArrayList<>());
        List<String[]> snapshot;
        synchronized (hist) {
            hist.add(new String[]{"user", userText});
            while (hist.size() > 20) hist.remove(0);
            while (!hist.isEmpty() && !hist.get(0)[0].equals("user")) hist.remove(0);
            snapshot = new ArrayList<>(hist);
        }

        long t = gf.getWorld().getTimeOfDay() % 24000L;
        String system = cfg.personality.replace("{name}", name)
                + "\n\nCurrent situation: the player is called " + player.getName().getString()
                + ". It is " + (t >= 13000 && t < 23000 ? "night" : "daytime") + " in the game"
                + (gf.getWorld().isRaining() ? " and it is raining" : "")
                + ". The player's health is " + (int) player.getHealth() + "/20."
                + " Reply in plain text only, no markdown, no stage directions longer than a few words.";

        CompletableFuture.runAsync(() -> {
            String reply;
            try {
                reply = callApi(cfg, system, snapshot);
                synchronized (hist) { hist.add(new String[]{"assistant", reply}); }
            } catch (Exception e) {
                GirlfriendMod.LOGGER.warn("Girlfriend AI request failed: {}", e.toString());
                synchronized (hist) { if (!hist.isEmpty()) hist.remove(hist.size() - 1); }
                server.execute(() -> player.sendMessage(Text.literal("[Girlfriend] " + name
                        + " couldn't answer. Check your API key / model in config/girlfriend.json and the game log.")
                        .formatted(Formatting.RED), false));
                return;
            }
            final String r = reply;
            server.execute(() -> deliver(player, name, r));
        });
    }

    private static void deliver(ServerPlayerEntity player, String name, String text) {
        player.sendMessage(Text.literal("<" + name + "> ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(text).formatted(Formatting.WHITE)), false);
    }

    private static String callApi(GirlfriendConfig cfg, String system, List<String[]> msgs) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model);
        body.addProperty("max_tokens", cfg.maxTokens);
        body.addProperty("system", system);
        JsonArray arr = new JsonArray();
        for (String[] m : msgs) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m[0]);
            o.addProperty("content", m[1]);
            arr.add(o);
        }
        body.add("messages", arr);

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json")
                .header("x-api-key", cfg.key())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());

        StringBuilder sb = new StringBuilder();
        for (JsonElement e : JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("content")) {
            JsonObject o = e.getAsJsonObject();
            if ("text".equals(o.get("type").getAsString())) sb.append(o.get("text").getAsString());
        }
        String out = sb.toString().trim();
        if (out.isEmpty()) throw new RuntimeException("empty reply");
        return out;
    }
}
