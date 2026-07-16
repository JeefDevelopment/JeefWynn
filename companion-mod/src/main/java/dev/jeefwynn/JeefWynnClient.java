package dev.jeefwynn;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class JeefWynnClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("JeefWynn");

    @Override
    public void onInitializeClient() {
        CompanionConfig config = CompanionConfig.load();
        DiscordBridgeClient bridge = new DiscordBridgeClient(config);
        SpellHud spellHud = new SpellHud(config);

        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> spellHud.render(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(client -> bridge.tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!config.setupComplete) client.execute(() -> client.setScreen(new SetupWizardScreen(config, bridge)));
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (!bridge.isChatMode()) return true;
            bridge.send(message);
            return false;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("d")
                    .executes(context -> {
                        bridge.toggleChatMode();
                        return 1;
                    })
                    .then(literal("link").executes(context -> {
                        bridge.beginLink();
                        return 1;
                    }))
                    .then(literal("unlink").executes(context -> {
                        bridge.unlink();
                        return 1;
                    }))
                    .then(literal("status").executes(context -> {
                        localMessage("Discord bridge: " + (bridge.isConnected() ? "connected" : "disconnected")
                                + "; chat mode: " + (bridge.isChatMode() ? "on" : "off"), ChatFormatting.AQUA);
                        return 1;
                    }))
                    .then(literal("server").then(argument("url", StringArgumentType.greedyString()).executes(context -> {
                        bridge.setServer(StringArgumentType.getString(context, "url"));
                        return 1;
                    })))
                    .then(literal("send").then(argument("message", StringArgumentType.greedyString()).executes(context -> {
                        bridge.send(StringArgumentType.getString(context, "message"));
                        return 1;
                    }))));

            dispatcher.register(literal("spellhud").executes(context -> {
                config.spellHudEnabled = !config.spellHudEnabled;
                config.save();
                localMessage("Spell HUD " + (config.spellHudEnabled ? "enabled" : "disabled") + ".",
                        config.spellHudEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                return 1;
            }));

            dispatcher.register(literal("jeefwynn")
                    .then(literal("setup").executes(context -> {
                        Minecraft.getInstance().setScreen(new SetupWizardScreen(config, bridge));
                        return 1;
                    })));
        });
    }

    private static void localMessage(String value, ChatFormatting color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(value).withStyle(color), false);
    }
}
