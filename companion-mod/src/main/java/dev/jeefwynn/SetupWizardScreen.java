package dev.jeefwynn;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SetupWizardScreen extends Screen {
    private final CompanionConfig config;
    private final DiscordBridgeClient bridge;
    private Button spellHudButton;

    SetupWizardScreen(CompanionConfig config, DiscordBridgeClient bridge) {
        super(Component.literal("Jeef's Wynncraft Setup"));
        this.config = config;
        this.bridge = bridge;
    }

    @Override
    protected void init() {
        int center = width / 2;
        spellHudButton = addRenderableWidget(Button.builder(spellHudLabel(), button -> {
            config.spellHudEnabled = !config.spellHudEnabled;
            config.save();
            spellHudButton.setMessage(spellHudLabel());
        }).bounds(center - 155, 112, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal(config.bridgeToken.isBlank() ? "Link Discord" : "Discord linked"), button -> {
            if (config.bridgeToken.isBlank()) {
                onClose();
                bridge.beginLink();
            }
        }).bounds(center + 5, 112, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Finish"), button -> finish())
                .bounds(center - 75, height - 42, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, title, center, 24, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "A lightweight base is already installed. Choose the personal features you want.", center, 46, 0xFFB8B8B8);

        drawStatus(graphics, center - 155, 72, "Wynncraft map LoDs", FabricLoader.getInstance().isModLoaded("distanthorizons"));
        drawStatus(graphics, center + 5, 72, "Voices of Wynn", FabricLoader.getInstance().isModLoaded("wynnvp"));
        graphics.drawString(font, "Heavy modules are selected in the Packwiz installer and require a restart to add or remove.", center - 155, 92, 0xFF999999, false);

        graphics.drawCenteredString(font, "Discord setup opens a secure guild link code. Use /d later to toggle chat mode.", center, 146, 0xFFB8B8B8);
        graphics.drawCenteredString(font, "Reopen this screen any time with /jeefwynn setup.", center, 162, 0xFF888888);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawStatus(GuiGraphics graphics, int x, int y, String label, boolean installed) {
        Component status = Component.literal((installed ? "✓ " : "– ") + label + (installed ? " installed" : " not selected"))
                .withStyle(installed ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        graphics.drawString(font, status, x, y, 0xFFFFFFFF, false);
    }

    private Component spellHudLabel() {
        return Component.literal("Spell HUD: " + (config.spellHudEnabled ? "On" : "Off"));
    }

    private void finish() {
        config.setupComplete = true;
        config.save();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        config.setupComplete = true;
        config.save();
        super.onClose();
    }
}
