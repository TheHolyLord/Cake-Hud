package com.ctt.healthdisplay.client;

import com.ctt.healthdisplay.config.ModConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * 客户端本地命令 {@code /ctthd reload}：热加载 {@code config/ctt-health-display.json}。
 *
 * <p>不向服务端发包；用于手改 {@link ModConfig#hideHudOnBerserk} 等未进 ModMenu 的字段后即时生效。
 */
public final class ClientConfigReloadCommand {

    private ClientConfigReloadCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(ClientConfigReloadCommand::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                         CommandBuildContext registryAccess) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ctthd")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reload")
                        .executes(ctx -> {
                            boolean hadFile = ModConfig.reload();
                            FabricClientCommandSource src = ctx.getSource();
                            src.sendFeedback(Component.translatable(
                                    "ctt-health-display.command.reload.success",
                                    Component.translatable(ModConfig.INSTANCE.hideHudOnBerserk
                                            ? "ctt-health-display.config.value.on"
                                            : "ctt-health-display.config.value.off")));
                            if (!hadFile) {
                                src.sendFeedback(Component.translatable(
                                        "ctt-health-display.command.reload.no_file"));
                            }
                            return 1;
                        })));
    }
}
