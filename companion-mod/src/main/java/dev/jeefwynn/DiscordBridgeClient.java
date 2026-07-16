package dev.jeefwynn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class DiscordBridgeClient implements WebSocket.Listener {
    private final CompanionConfig config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jeefwynn-bridge");
        thread.setDaemon(true);
        return thread;
    });
    private volatile WebSocket socket;
    private volatile boolean chatMode;
    private volatile long lastConnectAttempt;
    private final StringBuilder incoming = new StringBuilder();

    DiscordBridgeClient(CompanionConfig config) {
        this.config = config;
    }

    boolean isChatMode() {
        return chatMode;
    }

    boolean isConnected() {
        return socket != null && !socket.isOutputClosed();
    }

    void tick() {
        if (!config.discordEnabled || config.bridgeToken.isBlank() || isConnected()) return;
        if (System.currentTimeMillis() - lastConnectAttempt >= 10_000) connect();
    }

    void toggleChatMode() {
        if (config.bridgeToken.isBlank()) {
            message("Not linked. Run /d link first.", ChatFormatting.YELLOW);
            return;
        }
        config.discordEnabled = true;
        chatMode = !chatMode;
        config.save();
        if (chatMode) connect();
        message("Discord chat mode " + (chatMode ? "enabled" : "disabled") + ".", chatMode ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    boolean send(String content) {
        WebSocket current = socket;
        if (current == null || current.isOutputClosed()) {
            message("Discord bridge is not connected yet.", ChatFormatting.RED);
            connect();
            return false;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "chat");
        payload.addProperty("content", content);
        current.sendText(payload.toString(), true);
        message("[Discord] You: " + content, ChatFormatting.AQUA);
        return true;
    }

    void setServer(String value) {
        try {
            URI uri = URI.create(value);
            if (!Objects.equals(uri.getScheme(), "https") || uri.getHost() == null) throw new IllegalArgumentException();
            config.bridgeUrl = value.replaceAll("/+$", "");
            config.bridgeToken = "";
            config.discordEnabled = false;
            config.save();
            close();
            message("Bridge server saved. Run /d link.", ChatFormatting.GREEN);
        } catch (IllegalArgumentException exception) {
            message("Server must be a valid https:// URL.", ChatFormatting.RED);
        }
    }

    void unlink() {
        config.bridgeToken = "";
        config.discordEnabled = false;
        chatMode = false;
        config.save();
        close();
        message("Discord account unlinked on this client.", ChatFormatting.GRAY);
    }

    void beginLink() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (config.bridgeUrl.contains("replace-me")) {
            message("The pack owner must configure the bridge URL first (/d server <https-url>).", ChatFormatting.RED);
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("minecraftUuid", minecraft.player.getUUID().toString());
        body.addProperty("minecraftName", minecraft.player.getGameProfile().name());
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.bridgeUrl + "/api/link/start"))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        message("Requesting a Discord link code...", ChatFormatting.GRAY);
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 201) {
                message("Could not start linking (HTTP " + response.statusCode() + ").", ChatFormatting.RED);
                return;
            }
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            String code = result.get("code").getAsString();
            String pollId = result.get("pollId").getAsString();
            message("In your Discord guild, run /link code:" + code, ChatFormatting.GOLD);
            pollLink(pollId, 0);
        }).exceptionally(error -> {
            message("Could not reach the bridge: " + error.getMessage(), ChatFormatting.RED);
            return null;
        });
    }

    private void pollLink(String pollId, int attempt) {
        if (attempt >= 150) {
            message("Link code expired. Run /d link to try again.", ChatFormatting.RED);
            return;
        }
        scheduler.schedule(() -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.bridgeUrl + "/api/link/status?id=" + pollId))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                if (response.statusCode() == 404) {
                    message("Link code expired. Run /d link to try again.", ChatFormatting.RED);
                    return;
                }
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                if ("linked".equals(result.get("status").getAsString())) {
                    config.bridgeToken = result.get("token").getAsString();
                    config.discordEnabled = true;
                    config.save();
                    message("Discord linked. Run /d to toggle Discord chat mode.", ChatFormatting.GREEN);
                    connect();
                } else {
                    pollLink(pollId, attempt + 1);
                }
            }).exceptionally(error -> {
                pollLink(pollId, attempt + 1);
                return null;
            });
        }, 2, TimeUnit.SECONDS);
    }

    private synchronized void connect() {
        if (config.bridgeToken.isBlank() || isConnected()) return;
        lastConnectAttempt = System.currentTimeMillis();
        String webSocketUrl = config.bridgeUrl.replaceFirst("^https://", "wss://") + "/ws";
        http.newWebSocketBuilder()
                .header("Authorization", "Bearer " + config.bridgeToken)
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(webSocketUrl), this)
                .thenAccept(created -> socket = created)
                .exceptionally(error -> null);
    }

    private synchronized void close() {
        WebSocket current = socket;
        socket = null;
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "client disabled");
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        webSocket.request(1);
        message("Discord bridge connected.", ChatFormatting.DARK_GREEN);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        incoming.append(data);
        if (last) {
            try {
                JsonObject payload = JsonParser.parseString(incoming.toString()).getAsJsonObject();
                if ("discord_message".equals(payload.get("type").getAsString())) {
                    message("[Discord] " + payload.get("author").getAsString() + ": " + payload.get("content").getAsString(), ChatFormatting.AQUA);
                }
            } catch (RuntimeException ignored) {
            } finally {
                incoming.setLength(0);
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        socket = null;
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        socket = null;
    }

    private static void message(String value, ChatFormatting color) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(value).withStyle(color), false);
        });
    }
}
