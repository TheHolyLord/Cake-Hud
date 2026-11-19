package com.ctt.healthdisplay.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

public final class LoreTextDecorator {
    private static final String PREFIX = "/ctt_tooltip_lore_open ";

    private LoreTextDecorator() {}

    public static Component decorate(Component original) {
        MutableComponent result = Component.empty();
        boolean[] changed = {false};
        original.visit((style, text) -> {
            if (style.getClickEvent() != null) {
                result.append(Component.literal(text).withStyle(style));
                return Optional.empty();
            }
            int from = 0;
            for (LoreRegistry.Match match : LoreRegistry.findMatches(text)) {
                if (from < match.start()) {
                    result.append(Component.literal(text.substring(from, match.start())).withStyle(style));
                }
                result.append(Component.literal(match.word()).withStyle(style.withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(PREFIX + match.word()))));
                changed[0] = true;
                from = match.end();
            }
            if (from < text.length()) result.append(Component.literal(text.substring(from)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? result : original;
    }

    public static boolean intercept(ClickEvent event, int rgb) {
        return event instanceof ClickEvent.RunCommand run && intercept(run.command(), rgb);
    }

    public static boolean intercept(String command, int rgb) {
        if (command == null) return false;
        if (!command.startsWith("/")) command = "/" + command;
        if (!command.startsWith(PREFIX)) return false;
        String word = command.substring(PREFIX.length());
        if (LoreRegistry.get(word) == null) return true;
        // A chat-feed link starts a new chain; links inside popups call openEntry directly.
        if (Minecraft.getInstance().gui.screen() instanceof ChatScreen) LorePopupManager.closeAll();
        LorePopupManager.openEntry(word, LorePopupManager.scaledMouseX(), LorePopupManager.scaledMouseY(), rgb);
        return true;
    }
}
