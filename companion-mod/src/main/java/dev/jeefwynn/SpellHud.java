package dev.jeefwynn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class SpellHud {
    private static final int WIDTH = 84;
    private static final int ITEM_HEIGHT = 13;
    private final CompanionConfig config;
    private final WynntilsState state = new WynntilsState();

    SpellHud(CompanionConfig config) {
        this.config = config;
    }

    void render(GuiGraphics graphics) {
        if (!config.spellHudEnabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        WynntilsState.Snapshot snapshot = state.snapshot();
        if (snapshot == null) return;

        int x = config.hudX >= 0 ? config.hudX : graphics.guiWidth() - WIDTH - 8;
        int y = config.hudY >= 0 ? config.hudY : graphics.guiHeight() / 2 - (ITEM_HEIGHT * 2);
        for (int index = 0; index < 4; index++) {
            boolean ready = snapshot.mana() >= snapshot.costs()[index];
            int top = y + index * ITEM_HEIGHT;
            int background = ready ? 0xB0203B2B : 0xB02A2A2A;
            int foreground = ready ? 0xFFFFFFFF : 0xFF777777;
            graphics.fill(x, top, x + WIDTH, top + ITEM_HEIGHT - 1, background);
            String label = (index + 1) + "  " + snapshot.spellNames()[index];
            graphics.drawString(minecraft.font, label, x + 4, top + 2, foreground, false);
            String cost = Integer.toString(snapshot.costs()[index]);
            graphics.drawString(minecraft.font, cost, x + WIDTH - minecraft.font.width(cost) - 4, top + 2, foreground, false);
        }
    }
}
