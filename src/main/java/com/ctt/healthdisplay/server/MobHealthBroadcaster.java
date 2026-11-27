package com.ctt.healthdisplay.server;

import com.ctt.healthdisplay.network.MobHealthPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * v8.3.0 · M7 · 服务端 → 客户端"怪物血量表"推送器。
 *
 * <h2>循环</h2>
 * <ul>
 *   <li>在 {@code END_SERVER_TICK} 上注册 {@link #tickPushIfDue}；每 {@value #PUSH_INTERVAL_TICKS}
 *       tick (4 Hz) 触发一次遍历，非 CTT 地图 (无 RedHearts / MaxHP objective) 直接跳过。</li>
 *   <li>per-player：以玩家位置为中心 {@value #SCAN_RADIUS} 格半径做 {@link ServerLevel#getOtherEntities}
 *       粗筛，读 {@code AllHearts} / {@code MaxHP} scoreboard（没有 AllHearts 时回退
 *       {@code RedHearts}），都为 0 的"无 CTT 数据 mob"
 *       (原版怪、漏打 scoreboard 的自定义实体等) 直接剔除。</li>
 *   <li>按距离平方升序排序，取前 {@value #MAX_ENTRIES_PER_PLAYER} 条；最近那一只标
 *       {@code targetted=true}，其余 false — 客户端 NEAREST 档和 ▶ 箭头就能锁到正确目标，
 *       不再受 vanilla bossbar 被 Boss champion 抢占所害。</li>
 *   <li>差量：与 {@link #LAST_SENT} 里上次推给该玩家的列表做值比较，完全相同时不重复发包；
 *       视野里没 CTT mob 时也只在"上次非空"时发一次空包，后续保持静默。</li>
 * </ul>
 *
 * <h2>v2 字段补全（i18n + 元素抗性护甲）</h2>
 * <ul>
 *   <li>{@code nameText}：直接用 {@link Entity#getDisplayName()} 的 {@link Component} 实例，
 *       包含 translate 键 + Style；与 v1 的 {@code .getString()} 摊平方案相比，
 *       客户端中文 + 服务端英文 = 中文名（关键修复）。</li>
 *   <li>{@code defence / armorType / armorValue}：读 datapack 的
 *       {@code Defence / ArmorDisplay / FireArmor|IceArmor|DarkArmor|LightArmor|
 *       TrueArmor|WaterArmor|ElectricArmor} scoreboard。armorType 0..7 与 datapack
 *       {@code ArmorDisplay} 各档一一对应，客户端按编号选图标 + 颜色重建 suffix Component。</li>
 * </ul>
 *
 * <h2>性能</h2>
 * <p>每 tick ~10 玩家 × 96³ 格 box 内 ~30 LivingEntity × 2 次 scoreboard lookup，
 * 内部都是 {@link java.util.HashMap} O(1) 查询；实测 &lt; 0.5 ms 的挂钟开销。
 * 差量+空包吞噬让稳态下 0 mob 场景几乎 0 网络流量。
 * v2 加 2-3 次额外 scoreboard 查询（Defence + ArmorDisplay [+ 1 ArmorN]），常数项
 * 仍在 µs 级，不影响 TPS。
 *
 * <h2>服务端未装 mod 的回落</h2>
 * <p>本类只在服务端 entrypoint {@link CttStatsServer} 里注册；客户端如果连到
 * 纯 vanilla 服务端（或纯 datapack、未装该 mod）就收不到任何 {@link MobHealthPayload}，
 * {@link com.ctt.healthdisplay.client.ClientMobHealthCache#isFresh()} 始终 false，
 * 渲染路径自动回落到客户端 bossbar 解析链路 —— 老行为完全保留。
 */
public final class MobHealthBroadcaster {

    private static final Logger LOGGER = LoggerFactory.getLogger("ctt-mob-health");

    /** 推送间隔 (tick)。4 Hz = 250 ms，对头顶 HP 条"够实时 + 不抖"是经验拍板值。 */
    private static final int PUSH_INTERVAL_TICKS = 5;

    /** 扫描半径 (格)。Psy 分身与本体可能相隔很远，统一按用户要求覆盖 200 格。 */
    private static final double SCAN_RADIUS = 200.0;

    /** 单次 payload 最大 entry 数。v8.5.9 提到 64：CTT 高塔一层能挤 30+ 只怪，
     *  旧 32 上限会把第 33 只之后的怪截掉——它们在客户端照样可见却没有数据，
     *  正是"新敌人进视野后血条刷新/借数据"的诱因之一。64 条 ≈ 3 KB/包，4 Hz 无压力。 */
    private static final int MAX_ENTRIES_PER_PLAYER = 64;

    /**
     * armorType 1..7 对应的 scoreboard 名（与 datapack {@code targetting_enemy.mcfunction}
     * 第 7-13 行的 {@code ArmorDisplay=1..7} 模板一一对应）。索引 0 是占位（armorType=0 表
     * 仅 Defence，无元素抗性，不查表）。
     */
    private static final String[] ARMOR_OBJECTIVES = {
            "",              // 0 = 仅 Defence
            "FireArmor",     // 1 = 🔥
            "IceArmor",      // 2 = ☃
            "DarkArmor",     // 3 = ☠
            "LightArmor",    // 4 = ☀
            "TrueArmor",     // 5 = 🛡 (白)
            "WaterArmor",    // 6 = ⚓
            "ElectricArmor", // 7 = ⚡
    };

    /** per-player 上次推送列表，用于差量判定。玩家下线时由 {@link #onPlayerDisconnect} 清理。 */
    private static final Map<UUID, List<MobHealthPayload.Entry>> LAST_SENT = new HashMap<>();
    private static final Map<UUID, Set<UUID>> LAST_NPCS = new HashMap<>();

    /** per-player 上次推送的玩家属性，CrackedHearts 保留正负原值；PinkHearts 字段仅兼容旧协议。 */
    private static final Map<UUID, int[]> LAST_PLAYER_STATS = new HashMap<>();

    /**
     * v8.6.0 · 保活轮次。差量抑制让"场上无变化"时服务端完全不发包，而客户端
     * {@code ClientMobHealthCache.FRESH_WINDOW_MS=5s} 一到就判失鲜、自动回落
     * bossbar 近似路径——同名怪挤在一起时该路径会把锁定 bar 的数据在几只怪之间
     * 来回搬，看起来就是"血条统一了"。这里每 {@value #KEEPALIVE_ROUNDS} 轮
     * （3 s < 5 s 窗口）无视差量强制重发一次，让服务端权威路径永不失鲜。
     */
    private static final int KEEPALIVE_ROUNDS = 12;
    /** per-player 上次"真实发出包"的轮次。 */
    private static final Map<UUID, Long> LAST_PUSH_ROUND = new HashMap<>();
    /** tickPushIfDue 每执行一轮 +1（5 tick / 轮，4 Hz）。 */
    private static long pushRound = 0L;

    private static int tickCounter = 0;

    private MobHealthBroadcaster() {}

    /** {@code END_SERVER_TICK} 钩子。到 {@value #PUSH_INTERVAL_TICKS} tick 就跑一次。 */
    public static void tickPushIfDue(MinecraftServer server) {
        if (++tickCounter < PUSH_INTERVAL_TICKS) return;
        tickCounter = 0;
        if (server == null) return;
        pushRound++;

        Scoreboard sb = server.getScoreboard();
        boolean hasAllHearts = ScoreboardReader.hasObjective(sb, "AllHearts");
        boolean hasRedHearts = ScoreboardReader.hasObjective(sb, "RedHearts");
        boolean hasMaxHp     = ScoreboardReader.hasObjective(sb, "MaxHP");
        // 非 CTT 地图：没有 AllHearts / RedHearts / MaxHP 任何一家 → 没数据可推，直接跳过。
        // 客户端 5 s 没收到包会自动回落老路径，纯 vanilla / 别的地图世界都不受影响。
        if (!hasAllHearts && !hasRedHearts && !hasMaxHp) {
            if (!LAST_SENT.isEmpty()) LAST_SENT.clear();
            if (!LAST_PLAYER_STATS.isEmpty()) LAST_PLAYER_STATS.clear();
            if (!LAST_PUSH_ROUND.isEmpty()) LAST_PUSH_ROUND.clear();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                processPlayer(player, sb);
            } catch (Throwable t) {
                LOGGER.warn("[CTT MobHealth] process {} failed: {}",
                        player.getName().getString(), t.toString());
            }
        }

        // 兜底清理：DISCONNECT 钩子漏触发时的保底（例如服务器 reload / 直接杀进程）
        if (!LAST_SENT.isEmpty()) {
            Set<UUID> online = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                online.add(p.getUUID());
            }
            LAST_SENT.keySet().removeIf(uuid -> !online.contains(uuid));
            LAST_PLAYER_STATS.keySet().removeIf(uuid -> !online.contains(uuid));
            LAST_PUSH_ROUND.keySet().removeIf(uuid -> !online.contains(uuid));
        }
    }

    private static void processPlayer(ServerPlayer player, Scoreboard sb) {
        ServerLevel world = player.level();
        if (world == null) return;

        // v8.5.9 · 玩家碎心数（datapack objective CrackedHearts，misc_159/161 等维护）。
        // 客户端 scoreboard 拿不到未上展示槽的 objective（ServerScoreboard.onScoreChanged
        // 只广播 trackedObjectives 内的分数），装了本 mod 的服务端走这条 4 Hz 推送；
        // v8.6.0 起纯客户端还有第二条腿：/trigger ViewStats 聊天输出里的碎心行
        // （StatsData.tryParseCrackedLine），服务端没装本 mod 时自动接管。
        // 粉心不读取 PinkHearts：数据包实际用 CrackedHearts 的负值表示粉心。
        // 正数 = 碎心，负数 = 粉心；两者共用同一个 HUD 槽位。
        int crackedHearts = ScoreboardReader.readOrZero(sb, "CrackedHearts", player);
        int pinkHearts = 0; // 旧协议字段保留，但粉心不再来自 PinkHearts
        // v8.6.1 · 玩家自身法力 / 体力（objective Mana/MaxMana/Stamina/MaxStamina 同样没上
        // 展示槽，纯客户端读不到）。HUD 的"SwanJelly 法力条 / 近战切 Stamina 条 / Joey
        // 条换法力"需要这些数值，随本包 4 Hz 下发；纯客户端环境收不到包时自动回落
        // bossbar 文本通道（Mana / Stamina 段）。
        int mana = Math.max(0, ScoreboardReader.readOrZero(sb, "Mana", player));
        int maxMana = Math.max(0, ScoreboardReader.readOrZero(sb, "MaxMana", player));
        int stamina = Math.max(0, ScoreboardReader.readOrZero(sb, "Stamina", player));
        int maxStamina = Math.max(0, ScoreboardReader.readOrZero(sb, "MaxStamina", player));
        // Knight 标签只在服务端判断。默认显示体力；只有主手属于 Magic 分类时才切到法力。
        byte knightResourceMode = player.entityTags().contains("Knight")
                ? (ServerItemCategoryProbe.holdingMagic(player)
                    ? MobHealthPayload.KNIGHT_MODE_MANA
                    : MobHealthPayload.KNIGHT_MODE_STAMINA)
                : MobHealthPayload.KNIGHT_MODE_NONE;
        int[] playerStats = {crackedHearts, pinkHearts, mana, maxMana, stamina, maxStamina,
                knightResourceMode};

        Vec3 pos = player.position();
        double boxSize = SCAN_RADIUS * 2;
        AABB box = AABB.ofSize(pos, boxSize, boxSize, boxSize);

        var npcChannel = com.ctt.healthdisplay.network.NpcHealthVisibilityPayload.ID;
        boolean syncNpcs = ServerPlayNetworking.canSend(player, npcChannel);
        List<Entity> npcs = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (Entity e : world.getEntities((Entity) null, box, ent ->
                ent instanceof LivingEntity
                        && !(ent instanceof Player)
                        && !(ent instanceof ArmorStand)
                        && ent.isAlive()
                        && !ent.isRemoved())) {
            if (syncNpcs && e.entityTags().contains("NPC")) npcs.add(e);
            int hp = ScoreboardReader.readOrZero(sb, "AllHearts", e);
            if (hp <= 0) hp = ScoreboardReader.readOrZero(sb, "RedHearts", e);
            int maxHp = ScoreboardReader.readOrZero(sb, "MaxHP", e);
            // 数据包没管控的实体（原版僵尸、自定义但未打 RedHearts 的 mob）完全跳过，
            // 避免头顶出现 0/0 的畸形条；客户端 vanilla 名牌继续负责显示。
            if (hp <= 0 && maxHp <= 0) continue;
            if (maxHp <= 0) maxHp = hp;  // 只写了 RedHearts 的自定义 mob：用当前值做 maxHp 兜底
            double dist = e.position().distanceToSqr(pos);
            candidates.add(new Candidate(e, hp, maxHp, dist));
        }

        if (syncNpcs) {
            npcs.sort(Comparator.comparingDouble(npc -> npc.position().distanceToSqr(pos)));
            Set<UUID> ids = new HashSet<>();
            int limit = Math.min(npcs.size(), com.ctt.healthdisplay.network.NpcHealthVisibilityPayload.MAX_ENTITIES);
            for (int i = 0; i < limit; i++) ids.add(npcs.get(i).getUUID());
            Set<UUID> previous = LAST_NPCS.put(player.getUUID(), ids);
            if (!ids.equals(previous)) {
                ServerPlayNetworking.send(player, new com.ctt.healthdisplay.network.NpcHealthVisibilityPayload(ids));
            }
        }

        if (candidates.isEmpty()) {
            maybeSendEmpty(player, playerStats);
            return;
        }

        candidates.sort(Comparator.comparingDouble(Candidate::dist));
        if (candidates.size() > MAX_ENTRIES_PER_PLAYER) {
            candidates = new ArrayList<>(candidates.subList(0, MAX_ENTRIES_PER_PLAYER));
        }

        UUID nearestUuid = candidates.stream()
                .filter(candidate -> !candidate.entity.entityTags().contains("NPC"))
                .findFirst().orElse(candidates.get(0)).entity.getUUID();

        List<MobHealthPayload.Entry> entries = new ArrayList<>(candidates.size());
        for (Candidate c : candidates) {
            Component nameText = c.entity.getDisplayName();
            if (nameText == null) nameText = c.entity.getName();
            if (nameText == null) nameText = Component.empty();

            // PsyClone 是视觉分身，数据包并没有给它单独维护一套 Boss 属性。
            // 这里直接把来源切到场地上带 Boss + Psy 标签的正式 Psy 实体，保证分身的
            // 血量、最大血量、护甲和三种特殊心始终跟随正式 Boss。
            // v8.6.1 · 修复"本体不在玩家屏幕上 / 分身离本体太远 → 分身血条变自己的"：
            //   1) 搜索半径从 48 提到 96（Psy 场地对角约 75 格，旧值切不到对侧分身）；
            //   2) 找到过一次就把 分身UUID→本体UUID 记入持久映射；之后即使本体
            //      实体没被搜索框扫到（超远 / 区块边缘），也按记住的 UUID 直接读
            //      scoreboard——CTT 的分数按 holder 名（UUID 字符串）维护，与实体
            //      是否被扫到无关。
            ScoreHolder statHolder = resolvePsyStatHolder(world, sb, c.entity);

            int sourceHp = ScoreboardReader.readOrZero(sb, "AllHearts", statHolder);
            if (sourceHp <= 0) sourceHp = ScoreboardReader.readOrZero(sb, "RedHearts", statHolder);
            int sourceMaxHp = ScoreboardReader.readOrZero(sb, "MaxHP", statHolder);
            int displayHp = sourceHp > 0 ? sourceHp : c.hp;
            int displayMaxHp = sourceMaxHp > 0 ? sourceMaxHp : (sourceHp > 0 ? sourceHp : c.maxHp);

            int soulHearts = ScoreboardReader.readOrZero(sb, "SoulHearts", statHolder);
            int blackHearts = ScoreboardReader.readOrZero(sb, "BlackHearts", statHolder);
            int blueHearts = ScoreboardReader.readOrZero(sb, "BlueHearts", statHolder);
            int defence = ScoreboardReader.readOrZero(sb, "Defence", statHolder);
            int armorDisplay = ScoreboardReader.readOrZero(sb, "ArmorDisplay", statHolder);
            byte armorType = (byte) (armorDisplay >= 1 && armorDisplay <= 7 ? armorDisplay : 0);
            int armorValue = 0;
            if (armorType >= 1 && armorType <= 7) {
                armorValue = ScoreboardReader.readOrZero(sb, ARMOR_OBJECTIVES[armorType], statHolder);
            }

            boolean tgt = c.entity.getUUID().equals(nearestUuid);
            entries.add(new MobHealthPayload.Entry(
                    c.entity.getUUID(),
                    nameText,
                    displayHp, displayMaxHp,
                    soulHearts, blackHearts, blueHearts,
                    tgt,
                    defence, armorType, armorValue
            ));
        }

        // 差量：上次 snapshot 和这次 entry-by-entry 完全相同、且玩家自身数值也没变 → 不重发。
        // HashMap 里存 Entry record 列表，record 自带结构相等，但 list 比较我们自己做
        // 一次避免 rebuild 一个"值相同但内存位不同"的 list 触发误推。
        // v8.6.0 · 差量命中也要过保活闸门：距上次真实发包 ≥ KEEPALIVE_ROUNDS 轮
        // （3 s）照样重发，否则客户端 5 s 失鲜窗口到点 → 回落 bossbar 近似路径 →
        // 同名怪挤在一起时血条被来回搬（"血条统一"的根因）。
        List<MobHealthPayload.Entry> prev = LAST_SENT.get(player.getUUID());
        int[] prevStats = LAST_PLAYER_STATS.get(player.getUUID());
        boolean keepaliveDue = pushRound - lastPushRound(player.getUUID()) >= KEEPALIVE_ROUNDS;
        if (!keepaliveDue && prevStats != null && statsEqual(prevStats, playerStats)
                && entriesEqual(prev, entries)) return;
        LAST_SENT.put(player.getUUID(), entries);
        LAST_PLAYER_STATS.put(player.getUUID(), playerStats);

        MobHealthPayload payload = new MobHealthPayload(MobHealthPayload.CURRENT_VERSION, entries,
                crackedHearts, pinkHearts, mana, maxMana, stamina, maxStamina, knightResourceMode);
        try {
            ServerPlayNetworking.send(player, payload);
            LAST_PUSH_ROUND.put(player.getUUID(), pushRound);
        } catch (Throwable t) {
            LOGGER.warn("[CTT MobHealth] send to {} failed: {}",
                    player.getName().getString(), t.toString());
        }
    }

    private static long lastPushRound(UUID uuid) {
        Long r = LAST_PUSH_ROUND.get(uuid);
        // 从未发过 → 0。首轮 prevCracked==null 本来就会走发送分支，不受保活闸门影响。
        return r == null ? 0L : r;
    }

    /**
     * 玩家视野里当前没有 CTT mob。上一次推的也是空 / 从未推过、且玩家自身数值没变时
     * 保持静默，避免进 lobby / 过场动画时每 250 ms 空包闪烁；碎心 / 法力 / 体力变了
     * 照样要发（空列表 + 新数值），保证大厅里的 "200/200 (150)" 与法力条也是实时的。
     */
    private static void maybeSendEmpty(ServerPlayer player, int[] stats) {
        List<MobHealthPayload.Entry> prev = LAST_SENT.get(player.getUUID());
        int[] prevStats = LAST_PLAYER_STATS.get(player.getUUID());
        boolean listWasEmpty = prev == null || prev.isEmpty();
        boolean statsUnchanged = prevStats == null
                ? statsEqual(new int[]{0, 0, 0, 0, 0, 0}, stats) : statsEqual(prevStats, stats);
        // v8.6.0 · 空视野同样要保活：一直静默会让客户端 5 s 失鲜、回落 bossbar 近似。
        boolean keepaliveDue = pushRound - lastPushRound(player.getUUID()) >= KEEPALIVE_ROUNDS;
        if (!keepaliveDue && listWasEmpty && statsUnchanged) return;
        LAST_SENT.put(player.getUUID(), Collections.emptyList());
        LAST_PLAYER_STATS.put(player.getUUID(), stats);
        try {
            ServerPlayNetworking.send(player, new MobHealthPayload(MobHealthPayload.CURRENT_VERSION,
                    Collections.emptyList(), stats[0], stats[1], stats[2], stats[3], stats[4], stats[5],
                    (byte) stats[6]));
            LAST_PUSH_ROUND.put(player.getUUID(), pushRound);
        } catch (Throwable ignored) {
            // 空包失败无副作用 — 下次 4 Hz 还会重试
        }
    }

    /** 玩家自身数值差量比较：{crackedHearts, pinkHearts, mana, maxMana, stamina, maxStamina, knightResourceMode}。 */
    private static boolean statsEqual(int[] a, int[] b) {
        return java.util.Arrays.equals(a, b);
    }

    private static boolean entriesEqual(List<MobHealthPayload.Entry> a,
                                        List<MobHealthPayload.Entry> b) {
        if (a == null) return b == null || b.isEmpty();
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            MobHealthPayload.Entry x = a.get(i), y = b.get(i);
            if (!x.uuid().equals(y.uuid())) return false;
            if (x.hp() != y.hp() || x.maxHp() != y.maxHp()) return false;
            if (x.soulHearts() != y.soulHearts()
                    || x.blackHearts() != y.blackHearts()
                    || x.blueHearts() != y.blueHearts()) return false;
            if (x.targetted() != y.targetted()) return false;
            if (x.defence() != y.defence()) return false;
            if (x.armorType() != y.armorType()) return false;
            if (x.armorValue() != y.armorValue()) return false;
            if (!textEquivalent(x.nameText(), y.nameText())) return false;
        }
        return true;
    }

    /**
     * Component 在 vanilla 没重写 {@code equals}，引用比对会把"同 tick 同实体两次 getDisplayName()"
     * 误判为变化（每次返回新 MutableText 实例）。这里按"渲染串 + 顶层颜色"做轻量等价比对：
     * <ul>
     *   <li>都为 null → 等价；任一 null → 不等；</li>
     *   <li>{@code getString()} 字面量不同 → 不等；</li>
     *   <li>顶层 Style 颜色不同（例如冠军变色）→ 不等。</li>
     * </ul>
     * 同 tick 同实体在 99% 场景下渲染串 + 顶层颜色都稳定，避免每 5 tick 多余推送。
     */
    private static boolean textEquivalent(Component a, Component b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getString(), b.getString())) return false;
        var ca = a.getStyle() != null ? a.getStyle().getColor() : null;
        var cb = b.getStyle() != null ? b.getStyle().getColor() : null;
        return Objects.equals(ca, cb);
    }

    /**
     * v8.6.1 · 分身 → 本体数据源解析（服务端）。
     *
     * <p>非分身直接返回分身自身 holder。分身按下列顺序解析正式 Psy Boss 的
     * scoreboard holder：
     * <ol>
     *   <li>在 {@value #PSY_SEARCH_RADIUS} 格半径内找最近的 Boss+Psy 实体
     *       （旧版 48 格覆盖不了 Psy 场地对角，分身被随机传送到边缘时失联）；</li>
     *   <li>找到 → 记住 分身UUID→本体UUID，返回本体实体；</li>
     *   <li>没找到但记忆里有 → 本体多半只是不在搜索框内；scoreboard 分数按
     *       holder 名（UUID 字符串）维护，直接返回 {@code forNameOnly(uuid)}
     *       的轻量 holder，读数照常命中本体的 AllHearts/MaxHP/护甲；</li>
     *   <li>都没有 → 返回分身自身（旧行为兜底）。</li>
     * </ol>
     */
    private static ScoreHolder resolvePsyStatHolder(ServerLevel world, Scoreboard sb, Entity entity) {
        if (!entity.entityTags().contains("PsyClone")) return entity;
        Entity boss = findPsySource(world, entity);
        if (boss != null) {
            if (PSY_SOURCE_MEMORY.size() > 256) PSY_SOURCE_MEMORY.clear();
            PSY_SOURCE_MEMORY.put(entity.getUUID(), boss.getUUID());
            return boss;
        }
        UUID remembered = PSY_SOURCE_MEMORY.get(entity.getUUID());
        if (remembered == null) return entity;
        Entity loaded = world.getEntity(remembered);
        return loaded != null ? loaded : ScoreHolder.forNameOnly(remembered.toString());
    }

    /** 分身 → 本体 UUID 记忆（进程内静态；Psy 每层只打一次，量级个位数）。 */
    private static final Map<UUID, UUID> PSY_SOURCE_MEMORY = new java.util.concurrent.ConcurrentHashMap<>();

    /** 分身找本体的搜索半径：客户端模式要求本体与分身最多相隔 200 格。 */
    private static final double PSY_SEARCH_RADIUS = 200.0;

    /**
     * 找到场地上与分身对应的正式 Psy Boss。正式实体有 Psy + Boss 标签，
     * 分身本身有 PsyClone 标签；按距离选最近的正式实体，适配同场地多阶段场景。
     */
    private static Entity findPsySource(ServerLevel world, Entity entity) {
        if (world == null || entity == null) return null;
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB search = entity.getBoundingBox().inflate(PSY_SEARCH_RADIUS);
        for (Entity candidate : world.getEntities((Entity) null, search, other ->
                other instanceof LivingEntity
                        && other.isAlive()
                        && other.entityTags().contains("Boss")
                        && other.entityTags().contains("Psy")
                        && !other.entityTags().contains("PsyClone"))) {
            double distance = candidate.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 客户端 DISCONNECT 事件的服务端侧钩子，防止 LAST_SENT 泄漏占用同 UUID 重连后残留。 */
    public static void onPlayerDisconnect(UUID uuid) {
        LAST_NPCS.remove(uuid);
        LAST_SENT.remove(uuid);
        LAST_PLAYER_STATS.remove(uuid);
        LAST_PUSH_ROUND.remove(uuid);
    }

    private record Candidate(Entity entity, int hp, int maxHp, double dist) {}
}
