package com.ctt.healthdisplay.client;

import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.hud.ClientStageLocation;
import com.ctt.healthdisplay.hud.StageLocation;
import com.ctt.healthdisplay.mixin.BossBarHudAccessor;
import com.ctt.healthdisplay.network.StagePayload;
import com.ctt.healthdisplay.server.StageKey;
import net.minecraft.client.Minecraft;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v7.0.10 · 客户端关卡位置探测器（{@link com.ctt.healthdisplay.server.StageProbeServer}
 * 的<b>客户端镜像</b>）。
 *
 * <h2>定位</h2>
 * 服务端没装 mod 时，{@link StageLocation#probe()} 永远收不到 {@code StagePayload} 推送 →
 * {@link ClientStageLocation#current()} 永远是 {@code Snapshot.unknown()} → HUD 显示
 * "位置: 未知"，{@link ClientStatsCache#representativeStageKey()} 永远是 {@code null} →
 * {@link ClientDamageProbe} 的 stageTotal 永远不切关。
 *
 * <p>优先读取连接层收到的原版计分包，再用客户端 {@link Scoreboard} 补缺；
 * 用与 {@link com.ctt.healthdisplay.server.StageProbeServer} 等价的判定规则算出位置和 StageKey。
 *
 * <h2>与服务端版的差异</h2>
 * <ul>
 *   <li><b>无 {@code CTT} command tag 检查</b>：vanilla 不同步玩家 commandTags 到客户端
 *       (v6.5.5 教训)，所以本类总是按"已在 CTT 局内"处理；holders 全 0 时退化到
 *       {@code BREAK_ROOM}（而非 {@code LOBBY}）。<b>不影响统计</b>——主大厅时玩家不打怪，
 *       BREAK_ROOM 与 LOBBY 在 stage stat 维度都不算战斗关。</li>
 *   <li><b>按包更新</b>：相关分数变动时在游戏刻末一起读取；只有 bossbar 兜底和诊断保留每秒扫描。</li>
 *   <li><b>仅在缺少服务端 payload 时生效</b>：{@link ClientStageLocation#current()}
 *       优先返回服务端版（不是 unknown 时），客户端版只在 fallback 路径上被读。</li>
 * </ul>
 *
 * <h2>客户端 scoreboard 可见性前提</h2>
 * vanilla 协议只会同步"被显示在 slot（sidebar / list / belowname）"的 objective 给客户端。
 * CTT 地图通常在 sidebar 上展示 stage info → {@code CTT} / {@code GameID} 等 objective
 * 大概率可读。读不到时本类无声退化到 {@code UNKNOWN}（与原行为一致，无副作用）。
 */
public final class ClientStageProbe {

    private ClientStageProbe() {}

    // 与服务端 byte 编码保持一致（见 StageProbeServer K_* 常量）。
    private static final byte K_BREAK_ROOM    = 1;
    private static final byte K_STAGE_BOSS    = 2;
    private static final byte K_STAGE_MBOSS   = 3;
    private static final byte K_STAGE_DUNGEON = 4;
    private static final byte K_STAGE_SHOP    = 5;
    private static final byte K_STAGE_ALLY    = 6;
    private static final byte K_STAGE_MISC    = 7;
    private static final byte K_MINIGAME      = 8;
    private static final byte K_GAME_OVER     = 9;

    private static final byte GO_NONE      = 0;
    private static final byte GO_COUNTDOWN = 1;
    private static final byte GO_CONTINUE  = 2;
    private static final byte GO_LOCKED    = 3;

    /** 扫描间隔 = 20 tick (1s)。位置变化频率本来就低，无需逐 tick。 */
    private static final int SCAN_INTERVAL_TICKS = 20;

    private static long tickCounter = 0L;
    private static Scoreboard lastScoreboard;
    private static ClientScorePackets.StageSnapshot lastPacketStage;

    public static void reset() {
        lastScoreboard = null;
        lastPacketStage = null;
    }

    /** debugChat 开关从关到开的边沿检测——开启瞬间在 dump 文件写一行 session header。 */
    private static boolean lastDebugChatState;

    /** v7.0.12 · 上次 dump 写入的"指纹"——内容相同就跳过，避免日志爆炸。 */
    private static String lastDumpFingerprint = "";

    /** 由 {@code CttHealthDisplay} 在 {@code ClientTickEvents.END_CLIENT_TICK} 调用。 */
    public static void tick(Minecraft client) {
        tickCounter++;
        if (client == null || client.level == null || client.player == null) return;

        Scoreboard sb = client.level.getScoreboard();
        boolean scan = tickCounter % SCAN_INTERVAL_TICKS == 0;
        var packetStage = ClientScorePackets.stage(client);
        boolean refresh = packetStage != lastPacketStage || sb != lastScoreboard || scan;
        lastPacketStage = packetStage;
        lastScoreboard = sb;
        if (scan) feedBossbarsToDetector(client);
        boolean debug = ModConfig.INSTANCE.clientDamageDebugChat;
        if (!debug) lastDebugChatState = false;
        boolean diagnostics = scan && debug;
        if (!refresh && !diagnostics) return;

        Objective ctt = sb.getObjective("CTT");
        Objective game = sb.getObjective("GameID");
        Integer receivedGameId = packetStage.gameId();
        ReadOnlyScoreInfo gameIdScore = readScoreInfo(sb, "#CTT", game);
        var received = packetStage.scores();
        // A positive room/encounter flag plus Tier and Floor can identify a stage without GameID.
        boolean hasStageScores = ((receivedGameId != null || gameIdScore != null)
                && (ctt != null || !received.isEmpty()))
                || (received.containsKey("#Tier") && received.containsKey("#Floor")
                && received.entrySet().stream().anyMatch(e -> switch (e.getKey()) {
                    case "#Tier", "#Floor", "#CheckPoint" -> false;
                    default -> e.getValue() > 0;
                }));
        if (!hasStageScores) {
            if (refresh) ClientStageLocation.setFromScoreboard(null, null);
            if (!diagnostics) return;
        }

        // 关卡计分项与 StageProbeServer 使用相同的内部名称。
        int tier        = readStageScore(received, sb, ctt, "#Tier");
        int floor       = readStageScore(received, sb, ctt, "#Floor");
        int boss        = readStageScore(received, sb, ctt, "#Boss");
        int mboss       = readStageScore(received, sb, ctt, "#MBoss");
        int dungeon     = readStageScore(received, sb, ctt, "#Dungeon");
        int shop        = readStageScore(received, sb, ctt, "#Shop");
        int ally        = readStageScore(received, sb, ctt, "#Ally");
        int misc        = readStageScore(received, sb, ctt, "#Misc");
        int breakRoomId = readStageScore(received, sb, ctt, "#BreakRoomID");
        int checkpoint  = readStageScore(received, sb, ctt, "#CheckPoint");
        int gameOver    = readStageScore(received, sb, ctt, "#GameOver");
        int miniGame    = readStageScore(received, sb, ctt, "#LobbyMiniGame");
        int gameId      = receivedGameId != null ? receivedGameId : gameIdScore != null ? gameIdScore.value() : 0;

        StagePayload payload = computePayload(
                tier, floor, boss, mboss, dungeon, shop, ally, misc,
                breakRoomId, gameOver, miniGame, checkpoint);
        StageLocation.Snapshot snap = StageLocation.Snapshot.fromPayload(payload);
        StageKey stageKey = computeStageKey(snap, gameId);

        if (refresh && hasStageScores) {
            ClientStageLocation.setFromScoreboard(snap, stageKey);
        }
        if (diagnostics) {
            maybeDumpDiagnostics(client, sb, packetStage, snap, stageKey,
                    tier, floor, boss, mboss, dungeon, shop, ally, misc,
                    breakRoomId, gameOver, miniGame, checkpoint, gameId);
        }
    }

    /** v7.0.14 · 单独抽出一次 bossbar 扫描，给 {@link ClientStageDetector} 用。 */
    private static void feedBossbarsToDetector(Minecraft client) {
        try {
            BossBarHudAccessor accessor = (BossBarHudAccessor) client.gui.hud.getBossOverlay();
            Map<UUID, BossEvent> bars = accessor.getBossBars();
            if (bars == null || bars.isEmpty()) {
                ClientStageDetector.onBossbarsScanned(java.util.Collections.emptyList());
                return;
            }
            List<String> texts = new ArrayList<>(bars.size());
            for (BossEvent bar : bars.values()) {
                if (bar == null) continue;
                try {
                    Component name = bar.getName();
                    if (name != null) texts.add(name.getString());
                } catch (Throwable ignored) {}
            }
            ClientStageDetector.onBossbarsScanned(texts);
        } catch (Throwable ignored) {}
    }

    /**
     * v7.0.10 / v7.0.12 · 客户端关卡探测诊断写入（{@code logs/ctt-csp-dump.log}）。
     *
     * <p>开关：复用 {@link ModConfig#clientDamageDebugChat}（与 CDP 共享一个调试开关）。
     * 节流：每秒一次扫描，但<b>仅在内容指纹变化时才真正写文件</b>（避免休息室期间刷屏）。
     * debugChat 开关从关切到开瞬间在聊天栏提示一次文件路径。
     *
     * <p>诊断内容（v7.0.12 大幅扩展，目标：摸清服务端到底通过 vanilla 协议给客户端发了哪些数据）：
     * <ul>
     *   <li>客户端可见 {@link Objective} 全列表（含 {@code displayName}）</li>
     *   <li>{@code CTT} / {@code GameID} 这两个关键 objective 是否存在（已知缺失，留作历史对照）</li>
     *   <li>每个可见 objective 的 holders + score（前 8 个 + 总数）</li>
     *   <li>玩家自己在每个 objective 上的 score（如有）</li>
     *   <li>display slot 当前绑定的 objective（sidebar / list / belowname / team_color）</li>
     *   <li>玩家自己的 {@code commandTags}（验证 vanilla 是否同步玩家自己的 tag 给客户端）</li>
     *   <li>14 个 CTT fake-player 当前 score（已知 0，留作对照）</li>
     *   <li>本次推断结果：{@link StageLocation.Kind} + {@link StageKey}</li>
     * </ul>
     */
    private static void maybeDumpDiagnostics(Minecraft client, Scoreboard sb,
                                             ClientScorePackets.StageSnapshot packetStage,
                                             StageLocation.Snapshot snap, StageKey stageKey,
                                             int tier, int floor, int boss, int mboss,
                                             int dungeon, int shop, int ally, int misc,
                                             int breakRoomId, int gameOver, int miniGame,
                                             int checkpoint, int gameId) {
        ModConfig cfg = ModConfig.INSTANCE;
        if (!cfg.clientDamageDebugChat) {
            lastDebugChatState = false;
            return;
        }
        if (!lastDebugChatState) {
            CspDumpWriter.INSTANCE.resetFailureFlag();
            CspDumpWriter.INSTANCE.writeSessionHeader("debugChat opened");
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(
                        "[CSP] dump \u5199\u5165 " + CspDumpWriter.INSTANCE.getLogPath()));
            }
            lastDebugChatState = true;
            lastDumpFingerprint = ""; // 强制下次写完整 dump
        }

        Objective cttObj = sb.getObjective("CTT");
        Objective gidObj = sb.getObjective("GameID");

        // ---- 1. 收集所有可见 objective + holder + score ----
        List<Objective> allObjs = new ArrayList<>();
        try {
            for (Objective o : sb.getObjectives()) allObjs.add(o);
        } catch (Throwable ignored) {}

        List<net.minecraft.world.scores.ScoreHolder> allHolders = new ArrayList<>();
        try {
            allHolders.addAll(sb.getTrackedPlayers());
        } catch (Throwable ignored) {}

        // ---- 2. display slot 绑定 ----
        String slotSidebar  = describeSlot(sb, DisplaySlot.SIDEBAR);
        String slotList     = describeSlot(sb, DisplaySlot.LIST);
        String slotBelow    = describeSlot(sb, DisplaySlot.BELOW_NAME);

        // ---- 3. 玩家自己的 commandTags（vanilla 客户端通常拿不到，但万一可读？）----
        String selfName = client.player.getScoreboardName();
        java.util.Set<String> selfTags;
        try {
            selfTags = client.player.entityTags();
            if (selfTags == null) selfTags = Collections.emptySet();
        } catch (Throwable t) {
            selfTags = Collections.emptySet();
        }

        // ---- 4. 收集 bossbar 列表（vanilla 协议同步给客户端的所有 bossbar）----
        // CTT 地图通过 bossbar 显示 floor 进度（如 "The Tower (12/30)"）+ 玩家专属
        // p1001_onlyup 等血条，是客户端能拿到的最丰富的关卡线索。
        List<String[]> barLines = new ArrayList<>();  // {oneLine, normalizedFingerprintBit}
        try {
            BossBarHudAccessor accessor = (BossBarHudAccessor) client.gui.hud.getBossOverlay();
            Map<UUID, BossEvent> bars = accessor.getBossBars();
            if (bars != null) {
                for (Map.Entry<UUID, BossEvent> e : bars.entrySet()) {
                    BossEvent bar = e.getValue();
                    if (bar == null) continue;
                    String text;
                    try { text = bar.getName().getString(); } catch (Throwable t) { text = "<ex>"; }
                    if (text == null) text = "";
                    String color, style;
                    try { color = String.valueOf(bar.getColor()); } catch (Throwable t) { color = "?"; }
                    try { style = String.valueOf(bar.getColor()); } catch (Throwable t) { style = "?"; }
                    float pct = 0f;
                    try { pct = bar.getProgress(); } catch (Throwable ignored) {}
                    String shortText = text.length() > 80 ? text.substring(0, 80) + "..." : text;
                    String uuidShort = e.getKey().toString().substring(0, 8);
                    String line = String.format("    [%s] color=%s style=%s pct=%.2f text=\"%s\"",
                            uuidShort, color, style, pct, shortText);
                    // 指纹位：去掉所有数字以避免血条波动反复写
                    String fpBit = text.replaceAll("\\d+", "#");
                    barLines.add(new String[]{line, fpBit});
                }
            }
        } catch (Throwable ignored) {}
        java.util.Collections.sort(barLines, (a, b) -> a[1].compareTo(b[1]));

        // ---- 5. 计算指纹（只看会反映"位置"的字段，避免 score 波动反复写）----
        StringBuilder fp = new StringBuilder();
        fp.append("kind=").append(snap.kind())
          .append("|brId=").append(breakRoomId).append("|mg=").append(miniGame)
          .append("|sidebar=").append(slotSidebar)
          .append("|list=").append(slotList)
          .append("|below=").append(slotBelow)
          .append("|objCount=").append(allObjs.size())
          .append("|tags=").append(new java.util.TreeSet<>(selfTags))
          .append("|barCount=").append(barLines.size());
        String packetSources = "CTT=" + new java.util.TreeSet<>(packetStage.scores().keySet())
                + " GameID=" + (packetStage.gameId() != null)
                + " Lives=" + (ClientScorePackets.playerScore(client, "Lives", selfName) != null)
                + " Coins=" + (ClientScorePackets.playerScore(client, "Coins", selfName) != null)
                + " HealthPercent=" + (ClientScorePackets.playerScore(client, "HealthPercent", selfName) != null);
        fp.append("|wire:").append(packetSources);
        for (Objective o : allObjs) fp.append("|o:").append(o.getName());
        for (String[] b : barLines) fp.append("|b:").append(b[1]);
        String fingerprint = fp.toString();
        if (fingerprint.equals(lastDumpFingerprint)) return;  // 没变化，跳过
        lastDumpFingerprint = fingerprint;

        // ---- 5. 真正写出 ----
        List<String> lines = new ArrayList<>(32);
        String stageKeyStr = (stageKey == null) ? "null"
                : String.format("g=%s/t=%s/f=%s/s=%s/n=%s",
                        stageKey.gameId(), stageKey.tier(), stageKey.floor(),
                        stageKey.stageType(), stageKey.stageNum());

        lines.add(String.format("[tick=%d] \u63a8\u65ad kind=%s breakRoomId=%d miniGameId=%d -> stageKey=%s",
                tickCounter, snap.kind(), breakRoomId, miniGame, stageKeyStr));
        lines.add(String.format("  CTT obj=%s  GameID obj=%s  CTT score: t=%d f=%d boss=%d mboss=%d "
                + "dungeon=%d shop=%d ally=%d misc=%d brId=%d cp=%d go=%d mg=%d gid=%d",
                cttObj != null ? "\u5b58\u5728" : "\u7f3a",
                gidObj != null ? "\u5b58\u5728" : "\u7f3a",
                tier, floor, boss, mboss, dungeon, shop, ally, misc,
                breakRoomId, checkpoint, gameOver, miniGame, gameId));
        lines.add("  packet scores present: " + packetSources);
        lines.add(String.format("  display slot: sidebar=%s | list=%s | belowname=%s",
                slotSidebar, slotList, slotBelow));
        lines.add(String.format("  \u73a9\u5bb6=%s commandTags=%s knownHolders\u603b\u6570=%d",
                selfName, selfTags, allHolders.size()));
        lines.add(String.format("  \u53ef\u89c1obj\u603b\u6570=%d\uff1a", allObjs.size()));
        for (Objective o : allObjs) {
            // 该 objective 的 top 8 holder + score；顺带捕获玩家自己的 score
            String selfScoreStr = "-";
            List<String> entries = new ArrayList<>();
            int total = 0;
            for (net.minecraft.world.scores.ScoreHolder h : allHolders) {
                ReadOnlyScoreInfo s = null;
                try { s = sb.getPlayerScoreInfo(h, o); } catch (Throwable ignored) {}
                if (s == null) continue;
                total++;
                String hn = h.getScoreboardName();
                if (hn != null && hn.equals(selfName)) {
                    selfScoreStr = String.valueOf(s.value());
                }
                if (entries.size() < 8) {
                    String hn2 = (hn == null) ? "?" : hn;
                    if (hn2.length() > 18) hn2 = hn2.substring(0, 18) + "..";
                    entries.add(hn2 + "=" + s.value());
                }
            }
            String disp = "";
            try {
                if (o.getDisplayName() != null) {
                    String d = o.getDisplayName().getString();
                    if (d != null && !d.isEmpty() && !d.equals(o.getName())) disp = " disp=\"" + d + "\"";
                }
            } catch (Throwable ignored) {}
            lines.add(String.format("    [%s]%s self=%s holders=%d  top: %s",
                    o.getName(), disp, selfScoreStr, total, String.join(" ", entries)));
        }
        lines.add(String.format("  bossbar\u603b\u6570=%d\uff08\u53ef\u80fd\u542b floor\u8fdb\u5ea6\u5fa1 / \u73a9\u5bb6\u4e2a\u4eba\u8840\u6761 / boss \u8840\u6761\uff09\uff1a",
                barLines.size()));
        for (String[] b : barLines) lines.add(b[0]);
        CspDumpWriter.INSTANCE.writeLines(lines.toArray(new String[0]));
    }

    /** 读 display slot 当前绑定的 objective 名字（{@code "<\u672a\u7ed1\u5b9a>"} = 该 slot 没显示）。 */
    private static String describeSlot(Scoreboard sb, DisplaySlot slot) {
        try {
            Objective o = sb.getDisplayObjective(slot);
            return o == null ? "<\u672a\u7ed1\u5b9a>" : o.getName();
        } catch (Throwable t) {
            return "<ex:" + t.getClass().getSimpleName() + ">";
        }
    }

    /**
     * 客户端版的 computePayload。与服务端差异：<b>不检查 CTT command tag</b>
     * （客户端 commandTags 永远空），其它判定优先级完全一致。
     */
    private static StagePayload computePayload(
            int tier, int floor,
            int boss, int mboss, int dungeon, int shop, int ally, int misc,
            int breakRoomId, int gameOver, int miniGame, int checkpoint) {
        boolean cp = checkpoint == 1;

        // v8.1.0 · 客户端 fallback 不识别 MT 上下文（详见 docs/MAGUM_TRIALS_STAGE_TRACKING.md §6.4），
        // 统一走 compat() 工厂，inMagumTrials 默认 false。MT 分关功能仅在双端装 mod 时由服务端权威路径生效。
        if (miniGame > 0) {
            return StagePayload.compat(K_MINIGAME, tier, floor, 0,
                    (byte) breakRoomId, (byte) miniGame, GO_NONE, cp);
        }
        if (gameOver >= 1) {
            byte phase;
            if (gameOver >= 100)      phase = GO_LOCKED;
            else if (gameOver == 99)  phase = GO_CONTINUE;
            else                       phase = GO_COUNTDOWN;
            return StagePayload.compat(K_GAME_OVER, tier, floor, gameOver,
                    (byte) breakRoomId, (byte) miniGame, phase, cp);
        }
        if (boss > 0)
            return StagePayload.compat(K_STAGE_BOSS, tier, floor, boss,
                    (byte) breakRoomId, (byte) miniGame, GO_NONE, cp);
        if (mboss > 0)
            return StagePayload.compat(K_STAGE_MBOSS, tier, floor, mboss,
                    (byte) breakRoomId, (byte) miniGame, GO_NONE, cp);
        if (dungeon > 0)
            return StagePayload.compat(K_STAGE_DUNGEON, tier, floor, dungeon,
                    (byte) breakRoomId, (byte) miniGame, GO_NONE, cp);
        if (shop > 0)
            return StagePayload.compat(K_STAGE_SHOP, tier, floor, shop,
                    (byte) breakRoomId, (byte) miniGame, GO_NONE, cp);
        if (ally > 0)
            return StagePayload.compat(K_STAGE_ALLY, tier, floor, ally,
                    (byte) breakRoomId, (byte) miniGame, GO_NONE, cp);
        if (misc > 0)
            return StagePayload.compat(K_STAGE_MISC, tier, floor, misc,
                    (byte) breakRoomId, (byte) miniGame, GO_NONE, cp);

        // holders 全 0：服务端会区分 LOBBY (无 CTT tag) vs BREAK_ROOM (有 CTT tag)。
        // 客户端拿不到 commandTags，无法分辨——统一返回 BREAK_ROOM 作为兜底。
        // 主大厅时玩家不打怪，二者都不进战斗关 stat，体感无差。
        return StagePayload.compat(K_BREAK_ROOM, tier, floor, 0,
                (byte) breakRoomId, (byte) miniGame, GO_NONE, cp);
    }

    /**
     * 由 snapshot 反算 StageKey，与
     * {@link com.ctt.healthdisplay.server.StageBoundaryDispatcher#computeState} 等价。
     * 仅 STAGE_* kind 才生成有意义 key；其它返回 {@code null}（= session 桶 / 不分关统计）。
     */
    private static StageKey computeStageKey(StageLocation.Snapshot snap, int gameId) {
        String stageType = switch (snap.kind()) {
            case STAGE_BOSS    -> "boss";
            case STAGE_MBOSS   -> "mboss";
            case STAGE_DUNGEON -> "dungeon";
            case STAGE_SHOP    -> "shop";
            case STAGE_ALLY    -> "ally";
            case STAGE_MISC    -> "misc";
            default -> null;
        };
        if (stageType == null) return null;
        String gameIdStr = gameId > 0 ? Integer.toString(gameId) : null;
        return new StageKey(
                gameIdStr,
                Integer.toString(snap.tier()),
                Integer.toString(snap.floor()),
                stageType,
                Integer.toString(snap.stageNum())
        );
    }

    private static int readStageScore(Map<String, Integer> received, Scoreboard sb, Objective ctt, String owner) {
        Integer packetValue = received.get(owner);
        if (packetValue != null) return packetValue;
        ReadOnlyScoreInfo score = readScoreInfo(sb, owner, ctt);
        return score != null ? score.value() : 0;
    }

    private static ReadOnlyScoreInfo readScoreInfo(Scoreboard sb, String fakePlayerName, Objective objective) {
        return objective == null ? null : sb.getPlayerScoreInfo(ScoreHolder.forNameOnly(fakePlayerName), objective);
    }
}
