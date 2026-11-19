package com.ctt.healthdisplay.server.command;

import com.ctt.healthdisplay.config.ServerConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * v8.x · 服务端聊天广播 3 档的 per-player 订阅命令。
 *
 * <h2>语义</h2>
 * <p>命令 <b>仅修改执行者自己的订阅状态</b>，开/关广播只影响当前玩家。
 * 不再改写 {@link ServerConfig} 全局字段（那些字段保留作为运维兜底，
 * 通过编辑 JSON 启用时会给所有玩家广播）。
 *
 * <p>Per-player 订阅状态存于 {@link BroadcastSubscribers}（in-memory，断线 / 重启即清空）。
 *
 * <h2>命令清单（requires=true，任意权限玩家可用，控制台不能用）</h2>
 * <pre>
 *   /ctthd broadcast status                       查询 自己的订阅 + 全局兜底状态
 *   /ctthd broadcast damage on|off                自己订阅 / 退订 伤害广播
 *   /ctthd broadcast kill on|off                  自己订阅 / 退订 击杀广播
 *   /ctthd broadcast taken on|off                 自己订阅 / 退订 承伤广播
 *   /ctthd broadcast stage_report on|off          自己订阅 / 退订 每关战绩广播（v8.x 默认关）
 *   /ctthd broadcast all on|off                   自己一键四档
 *   /ctthd broadcast taken_threshold &lt;int&gt;        全局承伤阈值（仍是 ServerConfig 字段，会写盘）
 * </pre>
 *
 * <h2>反馈</h2>
 * <p>所有反馈仅发给执行者，不广播。
 */
public final class BroadcastToggleCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("ctt-stats-cmd");

    private static final SimpleCommandExceptionType ONLY_PLAYERS = new SimpleCommandExceptionType(
            Component.literal("[CTT] /ctthd broadcast 仅玩家可用（per-player 订阅，控制台无意义）"));

    private BroadcastToggleCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ctthd")
                .requires(src -> true);

        LiteralArgumentBuilder<CommandSourceStack> broadcast = Commands.literal("broadcast")
                .requires(src -> true)
                .executes(BroadcastToggleCommand::runStatus);

        broadcast.then(Commands.literal("status")
                .requires(src -> true)
                .executes(BroadcastToggleCommand::runStatus));

        broadcast.then(buildToggle("damage",       BroadcastSubscribers.Channel.DAMAGE));
        broadcast.then(buildToggle("kill",         BroadcastSubscribers.Channel.KILL));
        broadcast.then(buildToggle("taken",        BroadcastSubscribers.Channel.TAKEN));
        broadcast.then(buildToggle("stage_report", BroadcastSubscribers.Channel.STAGE_REPORT));

        broadcast.then(Commands.literal("all")
                .requires(src -> true)
                .then(Commands.literal("on").executes(ctx -> setAll(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setAll(ctx, false))));

        broadcast.then(Commands.literal("taken_threshold")
                .requires(src -> true)
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .executes(BroadcastToggleCommand::runSetTakenThreshold)));

        broadcast.then(Commands.literal("damage_threshold")
                .requires(src -> true)
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .executes(BroadcastToggleCommand::runSetDamageThreshold)));

        root.then(broadcast);
        root.executes(BroadcastToggleCommand::runStatus);
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggle(
            String name, BroadcastSubscribers.Channel ch) {
        return Commands.literal(name)
                .requires(src -> true)
                .executes(ctx -> {
                    UUID uuid = playerUuid(ctx);
                    boolean cur = BroadcastSubscribers.isSubscribed(ch, uuid);
                    feedbackBool(ctx, name, cur);
                    return cur ? 1 : 0;
                })
                .then(Commands.literal("on").executes(ctx -> {
                    UUID uuid = playerUuid(ctx);
                    BroadcastSubscribers.subscribe(ch, uuid);
                    feedbackBoolChanged(ctx, name, true);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    UUID uuid = playerUuid(ctx);
                    BroadcastSubscribers.unsubscribe(ch, uuid);
                    feedbackBoolChanged(ctx, name, false);
                    return 0;
                }));
    }

    private static int setAll(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        UUID uuid = playerUuid(ctx);
        for (BroadcastSubscribers.Channel ch : BroadcastSubscribers.Channel.values()) {
            if (on) BroadcastSubscribers.subscribe(ch, uuid);
            else    BroadcastSubscribers.unsubscribe(ch, uuid);
        }
        Component msg = Component.literal("[CTT] 你的四档广播订阅（damage/kill/taken/stage_report）已统一切到 ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        ctx.getSource().sendSuccess(() -> msg, false);
        return on ? 1 : 0;
    }

    private static int runSetTakenThreshold(CommandContext<CommandSourceStack> ctx) {
        int v = IntegerArgumentType.getInteger(ctx, "value");
        ServerConfig.INSTANCE.broadcastTakenThreshold = v;
        saveServerConfig();
        Component msg = Component.literal("[CTT] 承伤广播阈值（全局）-> " + v
                + (v == 0 ? "（全部显示）" : "（仅显示 ≥ " + v + "）")).withStyle(ChatFormatting.GRAY);
        ctx.getSource().sendSuccess(() -> msg, false);
        return v;
    }

    private static int runSetDamageThreshold(CommandContext<CommandSourceStack> ctx) {
        int v = IntegerArgumentType.getInteger(ctx, "value");
        ServerConfig.INSTANCE.broadcastDamageThreshold = v;
        saveServerConfig();
        Component msg = Component.literal("[CTT] 伤害广播阈值（全局）-> " + v
                + (v == 0 ? "（全部显示）" : "（仅显示 ≥ " + v + "）")).withStyle(ChatFormatting.GRAY);
        ctx.getSource().sendSuccess(() -> msg, false);
        return v;
    }

    private static void saveServerConfig() {
        try { ServerConfig.INSTANCE.save(); }
        catch (Throwable t) { LOGGER.warn("[CTT cmd] save server config failed: {}", t.toString()); }
    }

    private static int runStatus(CommandContext<CommandSourceStack> ctx) {
        ServerConfig c = ServerConfig.INSTANCE;
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        UUID uuid = player == null ? null : player.getUUID();

        Component head = Component.literal("[CTT] 广播订阅状态:").withStyle(ChatFormatting.GRAY);
        src.sendSuccess(() -> head, false);

        if (uuid != null) {
            sendStatusLine(src, "你订阅 damage      ", BroadcastSubscribers.isSubscribed(BroadcastSubscribers.Channel.DAMAGE,       uuid));
            sendStatusLine(src, "你订阅 kill        ", BroadcastSubscribers.isSubscribed(BroadcastSubscribers.Channel.KILL,         uuid));
            sendStatusLine(src, "你订阅 taken       ", BroadcastSubscribers.isSubscribed(BroadcastSubscribers.Channel.TAKEN,        uuid));
            sendStatusLine(src, "你订阅 stage_report", BroadcastSubscribers.isSubscribed(BroadcastSubscribers.Channel.STAGE_REPORT, uuid));
        } else {
            Component note = Component.literal("  （控制台执行：仅显示全局兜底状态）").withStyle(ChatFormatting.DARK_GRAY);
            src.sendSuccess(() -> note, false);
        }

        Component divider = Component.literal("  ----- 全局兜底（编辑 JSON 启用，对所有玩家广播）-----").withStyle(ChatFormatting.DARK_GRAY);
        src.sendSuccess(() -> divider, false);
        sendStatusLine(src, "global damage      ", c.broadcastDamageInChat);
        sendStatusLine(src, "global kill        ", c.broadcastKillsInChat);
        sendStatusLine(src, "global taken       ", c.broadcastTakenInChat);
        sendStatusLine(src, "global stage_report", c.broadcastStageReportInChat);
        Component tDamage = Component.literal("  damage_threshold = " + c.broadcastDamageThreshold
                + (c.broadcastDamageThreshold == 0 ? "（全部）" : "")).withStyle(ChatFormatting.DARK_GRAY);
        src.sendSuccess(() -> tDamage, false);
        Component tTaken = Component.literal("  taken_threshold  = " + c.broadcastTakenThreshold
                + (c.broadcastTakenThreshold == 0 ? "（全部）" : "")).withStyle(ChatFormatting.DARK_GRAY);
        src.sendSuccess(() -> tTaken, false);

        Component help = Component.literal("用法: /ctthd broadcast <damage|kill|taken|stage_report|all> <on|off>  （仅你自己看到）")
                .withStyle(ChatFormatting.DARK_GRAY);
        src.sendSuccess(() -> help, false);
        Component help2 = Component.literal("阈值: /ctthd broadcast <damage_threshold|taken_threshold> <int>  （全局，写盘）")
                .withStyle(ChatFormatting.DARK_GRAY);
        src.sendSuccess(() -> help2, false);
        return 1;
    }

    private static void sendStatusLine(CommandSourceStack src, String name, boolean on) {
        Component line = Component.literal("  " + name + " = ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        src.sendSuccess(() -> line, false);
    }

    private static void feedbackBool(CommandContext<CommandSourceStack> ctx, String name, boolean on) {
        Component msg = Component.literal("[CTT] 你的 broadcast " + name + " = ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        ctx.getSource().sendSuccess(() -> msg, false);
    }

    private static void feedbackBoolChanged(CommandContext<CommandSourceStack> ctx, String name, boolean on) {
        Component msg = Component.literal("[CTT] 你的 broadcast " + name + " -> ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        ctx.getSource().sendSuccess(() -> msg, false);
    }

    /** 命令必须由玩家执行；控制台执行抛 ONLY_PLAYERS 异常。 */
    private static UUID playerUuid(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) throw ONLY_PLAYERS.create();
        return p.getUUID();
    }
}
