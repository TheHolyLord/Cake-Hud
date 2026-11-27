package com.ctt.healthdisplay.health;

import com.ctt.healthdisplay.client.ActionBarCapture;
import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.mixin.BossBarHudAccessor;
import com.ctt.healthdisplay.mixin.LerpingBossEventAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import com.ctt.healthdisplay.client.ClientScorePackets;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HealthData {

    public int healthPercent;
    public int lives;

    public int allHearts;
    public int maxHP;
    /** CrackedHearts 的正负值拆成 HUD 的两种颜色：正数碎心，负数粉心。 */
    public int crackedHearts;
    public int pinkHearts;
    public int mana;
    public int maxMana;
    /**
     * v8.6.1 · 体力（datapack objectives Stamina / MaxStamina）。
     * 数据来源三条腿（优先级从高到低）：① 服务端装了本 mod 时的 4 Hz payload；
     * ② v8.6.2 actionbar 捕获（手持耗体力武器时数据包写的 (Stamina x/y)，
     *    纯数据包服务器可用，见 ActionBarCapture）；
     * ③ SwanJelly 个人 bossbar 的 (Stamina x/y) 段文本。
     * 手持近战/匕首/龙之重剑时 HUD 用它替换法力条显示。
     */
    public int stamina;
    public int maxStamina;
    public boolean hasStaminaField;
    public int coins;
    private int bossBarCoins;
    // Joey (ClassPassive=5 / Vampire) 独有的鲜血值。数据来源：个人 bossbar 标题里多出的 `(Blood x/y)` 段。
    // 仅 Joey 的 bossbar 会写这段，所以 hasBlood 天然等价于"当前玩家是 Joey"。
    public int blood;
    public int maxBlood;
    public boolean hasBlood;

    // 果冻斯旺 (ClassPassive=14 / Swan Jelly) 独有的动量值。数据来源：个人 bossbar 标题里多出的 `(Velocity x/y)` 段。
    // 数据包里最大值是硬编码字面量 `/6`（不是 scoreboard），但我们依旧按 `-?\d+/-?\d+` 解析以保持健壮性。
    // bossbar 原生颜色 light_purple (#FF55FF)；HUD 顶行用同色 + ⚡ 图标呈现，占用原本 `✦mana/maxMana` 的位置。
    public int velocity;
    public int maxVelocity;
    public boolean hasVelocity;

    public float bossBarPercent;
    public boolean hasBossBarData;
    public boolean hasPersonalBossBar;
    public boolean hasTeamBossBar;
    public boolean hasManaField;
    // 癫狂 fallback：bossbar 文字被替换成装饰内容解析不出 HP 时，改用 /trigger ViewStats 的心数据重建。
    //   maxHP = StatsData.redHearts（红心上限）
    //   allHearts = red + soul + black + blue（所有心种的总和 = 当前总 HP）
    public boolean hasStatsFallback;
    private StatsData statsDataRef;

    public void setStatsData(StatsData s) { this.statsDataRef = s; }

    public final List<TeammateData> teammates = new ArrayList<>();

    private static volatile Map<String, TeammateData> teammateMap = Collections.emptyMap();
    private static volatile Set<UUID> hiddenBarUUIDs = Collections.emptySet();
    private static volatile Map<java.util.UUID, MobHealthData> mobHealthMap = new java.util.concurrent.ConcurrentHashMap<>();

    private static volatile List<MobBossBarEntry> activeMobBars = Collections.emptyList();

    public static Map<String, TeammateData> getTeammateMap() {
        return teammateMap;
    }

    public static Set<UUID> getHiddenBarUUIDs() {
        return hiddenBarUUIDs;
    }

    public static Map<java.util.UUID, MobHealthData> getMobHealthMap() {
        // v8.3.0 · M7 · 服务端权威 cache 命中时返回只读 snapshot；客户端渲染端 (TeammateWorldRenderer
        // 等) 得到的 map 与本地 mobHealthMap 结构完全一致 —— 字段 (name/suffixText/hp/maxHP/
        // targetted/nameColor) 在 ClientMobHealthCache.onPayload 里已按 MobHealthData 构造完成。
        // 未命中 (服务端没装 mod / 失鲜超过 5 s) 自动回落本地 mobHealthMap，由 CttHealthDisplay.
        // updateMobTracking 负责填充，行为与 v8.2 一致。
        if (com.ctt.healthdisplay.client.ClientMobHealthCache.isFresh()) {
            return com.ctt.healthdisplay.client.ClientMobHealthCache.viewSnapshot();
        }
        return mobHealthMap;
    }

    public static boolean hasMobBar() { return !activeMobBars.isEmpty(); }
    public static List<MobBossBarEntry> getActiveMobBars() { return activeMobBars; }

    private float displayPercent;
    private float displayAllHearts;
    private float displayMaxHP;
    private float displayMana;
    private float displayMaxMana;
    private boolean personalAvailable;
    private boolean available;

    private static final float LERP_SPEED = 0.18f;
    // stats 心数据 fallback 的有效期：超过该时长未成功 capture 过 ViewStats 就不再信任，避免旧数据误显示。
    private static final long STATS_FALLBACK_TTL_MS = 30_000;

    private static final Pattern HP_PATTERN = Pattern.compile("\\((?:HP|生命值|Health)\\s*(-?\\d+)/(-?\\d+)\\)");
    private static final Pattern LIVES_PATTERN = Pattern.compile("\\((?:Lives|生命数|生命效)\\s*(-?\\d+)\\)");
    // v8.6.1 · Mana 与 Stamina 拆成两条独立通道（旧版一条正则混抓，SwanJelly 的
    // (Stamina x/y) 会冒充法力值进 data.mana）。中英双语：
    //   Mana    → "法力值"（Misc_Bossbars_P10_Bossbar_5 zh 译名）
    //   Stamina → "体力"  （Misc_Bossbars_P10_Bossbar_8 zh 译名）
    private static final Pattern MANA_PATTERN = Pattern.compile("\\((?:Mana|法力值)\\s*(-?\\d+)/(\\d+)\\)");
    private static final Pattern STAMINA_PATTERN = Pattern.compile("\\((?:Stamina|体力)\\s*(-?\\d+)/(\\d+)\\)");
    // Joey 个人 bossbar 多出的 (Blood <current>/<max>) 段。负数兜底（允许 -1、0），与 HP/Mana 风格保持一致。
    // 中英双语：英文 `Blood`、简体中文 `鲜血`（客户端语言切换后数据包按 translate 键输出本地化字面量）。
    private static final Pattern BLOOD_PATTERN = Pattern.compile("\\((?:Blood|鲜血)\\s*(-?\\d+)/(-?\\d+)\\)");
    // 果冻斯旺（ClassPassive=14）独有的 (Velocity <current>/<max>) 段。
    // 中英双语：英文 `Velocity`、简体中文 `动量`（详见 ctt_lang/lang/zh_cn.json 第 20144 行）。
    private static final Pattern VELOCITY_PATTERN = Pattern.compile("\\((?:Velocity|动量)\\s*(-?\\d+)/(-?\\d+)\\)");
    private static final Pattern COINS_PATTERN = Pattern.compile("\\((?:Coins|硬币|金币)\\s*(-?\\d+)\\)");
    private static final Pattern TEAM_PLAYER_PATTERN = Pattern.compile("([A-Za-z0-9_]+)\\s*\\((-?\\d+)/(-?\\d+)\\)");
    private static final Pattern MOB_NAME_PATTERN = Pattern.compile("^(.+?)\\s*\\((?:HP|生命值|Health)");
    private static final Pattern MOB_SUFFIX_PATTERN = Pattern.compile("\\((?:HP|生命值|Health)\\s*-?\\d+/-?\\d+\\)\\s*(.+)$");

    public void update() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            available = false;
            personalAvailable = false;
            return;
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        String player = client.player.getScoreboardName();
        Integer healthScore = readScore(client, scoreboard, player, "HealthPercent");
        healthPercent = healthScore == null ? 0 : Math.clamp(healthScore, 0, 100);
        lives = 0;
        // v8.6.0 · 碎心数双通道：
        //   1) 服务端装了本 mod → mob 血量 payload 4 Hz 里带 CrackedHearts（最实时）；
        //   2) 纯客户端（服务器只跑数据包）→ 属性栏每 5 s 的 /trigger ViewStats 输出
        //      里解析碎心行（StatsData.tryParseCrackedLine，用户提议的通道）。
        // payload 失鲜（服务端下线 / 没装）时自动切到 2)，断线时两边都归 0。
        boolean freshMobPayload = com.ctt.healthdisplay.client.ClientMobHealthCache.isFresh();
        boolean freshServerStats = com.ctt.healthdisplay.client.ClientStatsPushCache.isFresh();
        // 服务端属性包和纯客户端属性捕获都遵循同一规则：只看 CrackedHearts。
        // 正数显示碎心，负数显示绝对值粉心；PinkHearts 不参与 HUD 判断。
        int crackedScore = freshServerStats && statsDataRef != null
                ? statsDataRef.getCrackedHeartsScore()
                : freshMobPayload
                    ? com.ctt.healthdisplay.client.ClientMobHealthCache.crackedHeartsScore()
                    : (statsDataRef != null ? statsDataRef.getCrackedHeartsScore() : 0);
        crackedHearts = Math.max(0, crackedScore);
        pinkHearts = Math.max(0, -crackedScore);

        parseBossBarData(client);

        // Prefer synced integers, including zero. Keep the text fallback when the score is absent.
        Integer livesScore = readScore(client, scoreboard, player, "Lives");
        if (livesScore != null) lives = Math.max(0, livesScore);
        Integer coinsScore = readScore(client, scoreboard, player, "Coins");
        coins = coinsScore != null ? coinsScore : bossBarCoins;

        // v8.6.1 · 服务端 payload 通道（fresh 时优先于 bossbar 文本）：玩家自身
        // Mana/MaxMana/Stamina/MaxStamina。SwanJelly 的 bossbar 没有 Mana 段、
        // Joey / 默认职业的 bossbar 没有 Stamina 段，纯靠文本解析拿不到；
        // 装了本 mod 的服务端 4 Hz 直接推数值。癫狂 / 诅咒把 bossbar 文本换成
        // 装饰内容时这条通道照常刷新，法力条不会闪没。
        // 注意：这段必须在"游戏未开始"守卫之前——大厅里 scoreboard 的 MaxMana
        // 照样 > 0，若后写会把守卫刚清掉的字段又点着，进大厅冒出空蓝条。
        if (com.ctt.healthdisplay.client.ClientMobHealthCache.isFresh()) {
            int pMaxMana = com.ctt.healthdisplay.client.ClientMobHealthCache.playerMaxMana();
            if (pMaxMana > 0) {
                maxMana = pMaxMana;
                mana = Math.max(0, Math.min(com.ctt.healthdisplay.client.ClientMobHealthCache.playerMana(), pMaxMana));
                hasManaField = true;
            }
            // SwanJelly 的体力必须始终以个人 BossBar 为准；payload 只服务于其他职业。
            int pMaxStamina = com.ctt.healthdisplay.client.ClientMobHealthCache.playerMaxStamina();
            if (!hasVelocity && pMaxStamina > 0) {
                maxStamina = pMaxStamina;
                stamina = Math.max(0, Math.min(com.ctt.healthdisplay.client.ClientMobHealthCache.playerStamina(), pMaxStamina));
                hasStaminaField = true;
            }
        }

        // v8.6.2 · actionbar 通道（纯数据包服务器可用，排在 payload 之后、
        // "游戏未开始"守卫之前；优先级 payload > actionbar > bossbar 文本）：
        //   · 体力：数据包在玩家手持耗体力武器时，把 (Stamina x/y) 写进 actionbar
        //     （sword*_*.mcfunction 的 *Hold 标签 + ActionbarValue 轮播）。
        //     Joey / 默认职业的 bossbar 没有体力段，旧版只能靠服务端 payload；
        //     这条通道让它们在裸数据包服务器上也拿到体力，近战切换照常生效。
        //   · 果冻（SwanJelly，顶行有动量段）：法力条数值改用 magic 物品
        //     SpellUses/SpellUsesMax（"还能放几次"，magic*_*.mcfunction 的 *Hold
        //     动作条），整条 actionbar 就只有 "x/y" 两个数（用户拍板：代替拿不到的
        //     法力值）。只在 hasVelocity 时替换，普通职业的真实法力不受影响。
        if (!hasVelocity && ActionBarCapture.hasFreshStamina()) {
            maxStamina = ActionBarCapture.maxStamina();
            stamina = Math.max(0, Math.min(ActionBarCapture.stamina(), maxStamina));
            hasStaminaField = true;
        }
        if (hasVelocity && ActionBarCapture.hasFreshSpellUses()) {
            maxMana = ActionBarCapture.spellUsesMax();
            mana = Math.max(0, Math.min(ActionBarCapture.spellUses(), maxMana));
            hasManaField = true;
        }
        // 但"游戏未开始 / 切回大厅"场景仍需要把上一局残留的 mana/coins/blood/velocity 数据清掉，
        // 避免进入大厅还显示上局打到一半的蓝条数字。
        // 触发点：`/trigger ViewStats` 收到"cannot trigger"消息 → StatsData.isGameNotStarted() 为 true。
        if (statsDataRef != null && statsDataRef.isGameNotStarted()) {
            hasManaField = false;
            hasStaminaField = false;
            hasBlood = false;
            hasVelocity = false;
            mana = 0;
            maxMana = 0;
            stamina = 0;
            maxStamina = 0;
            blood = 0;
            maxBlood = 0;
            velocity = 0;
            maxVelocity = 0;
            resetCoinData();
        }

        // 癫狂 fallback：bossbar 解析不出 HP（hasBossBarData=false）但 ViewStats 拿到过心数据时，
        // 用红心上限作 maxHP，各类心之和作当前 HP，让 HUD 仍可渲染。
        // 三道守卫避免在"游戏未开始 / 切回大厅 / 长时间没刷新"等场景下用陈旧残留值误显示 HP 条：
        //   1) 最近一次 `/trigger ViewStats` 不是"无法触发"状态；
        //   2) capture 有过成功完成（lastCaptureCompleteTimeMs > 0）；
        //   3) 自上次成功 capture 不超过 STATS_FALLBACK_TTL_MS（30s）。
        hasStatsFallback = false;
        if (!hasBossBarData
                && statsDataRef != null
                && statsDataRef.hasHeartData()
                && !statsDataRef.isGameNotStarted()
                && statsDataRef.getLastCaptureCompleteTimeMs() > 0
                && (System.currentTimeMillis() - statsDataRef.getLastCaptureCompleteTimeMs()) < STATS_FALLBACK_TTL_MS) {
            int red = statsDataRef.getRedHearts();
            if (red > 0) {
                int soul = statsDataRef.getSoulHearts();
                int black = statsDataRef.getBlackHearts();
                int blue = statsDataRef.getBlueHearts();
                maxHP = red;
                allHearts = red + soul + black + blue;
                hasStatsFallback = true;
                healthPercent = Math.round((float) allHearts * 100 / maxHP);
            }
        }

        personalAvailable = hasBossBarData || hasStatsFallback || (healthPercent > 0 && healthScore != null);
        available = personalAvailable || hasTeamBossBar;

        Map<String, TeammateData> map = new HashMap<>();
        for (TeammateData t : teammates) {
            map.put(t.name, t);
        }
        teammateMap = Collections.unmodifiableMap(map);

        displayPercent = lerp(displayPercent, healthPercent, LERP_SPEED);
        displayAllHearts = lerp(displayAllHearts, allHearts, LERP_SPEED);
        displayMaxHP = lerp(displayMaxHP, maxHP, LERP_SPEED);
        displayMana = lerp(displayMana, mana, LERP_SPEED);
        displayMaxMana = lerp(displayMaxMana, maxMana, LERP_SPEED);
    }

    private void parseBossBarData(Minecraft client) {
        hasBossBarData = false;
        hasPersonalBossBar = false;
        hasTeamBossBar = false;
        allHearts = 0;
        maxHP = 0;
        teammates.clear();
        // v5.3.1：右侧栏字段（mana/maxMana/hasManaField/blood/maxBlood/hasBlood/
        //   velocity/maxVelocity/hasVelocity/coins）在这里**不再清零**。
        // 原因：癫狂状态下个人 bossbar 文本在"HP 文本"与"装饰性文本"之间疯狂翻页，
        //   旧版每帧都把这些字段归零 → 当帧 personalBar=null 时不再刷新 → hasMana() 翻 false
        //   → 蓝量/动量/鲜血条交替消失 = 闪烁。
        // 新版：清零改到 parsePersonalBar 开头（只在 bossbar 真的被找到时触发），
        //   bossbar 不可见的帧自动保持上一次解析出的值（sticky），闪烁消失。
        // 大厅 / 切场景的强制清零由 update() 里的 StatsData.isGameNotStarted() 守卫负责。

        if (client.gui.hud == null) return;

        try {
            BossBarHudAccessor accessor = (BossBarHudAccessor) client.gui.hud.getBossOverlay();
            Map<UUID, BossEvent> bossBars = accessor.getBossBars();
            if (bossBars == null || bossBars.isEmpty()) return;

            String playerName = client.player.getName().getString();

            BossEvent personalBar = null;
            BossEvent teamBar = null;
            Set<UUID> toHide = new HashSet<>();

            List<MobBossBarEntry> mobBars = new ArrayList<>();

            ModConfig cfg = ModConfig.INSTANCE;

            for (Map.Entry<UUID, BossEvent> entry : bossBars.entrySet()) {
                BossEvent bar = entry.getValue();
                String barText = bar.getName().getString();

                if (barText.contains(playerName)) {
                    if (HP_PATTERN.matcher(barText).find()) {
                        personalBar = bar;
                        hasPersonalBossBar = true;
                        if (cfg.hidePersonalBar) toHide.add(entry.getKey());
                    } else if (barText.contains("(/)")) {
                        teamBar = bar;
                        hasTeamBossBar = true;
                        if (cfg.hideTeamBar) toHide.add(entry.getKey());
                    }
                } else if (HP_PATTERN.matcher(barText).find()) {
                    Matcher hpM = HP_PATTERN.matcher(barText);
                    if (hpM.find()) {
                        int mobHP = Integer.parseInt(hpM.group(1));
                        int mobMaxHP = Integer.parseInt(hpM.group(2));
                        Matcher nameM = MOB_NAME_PATTERN.matcher(barText);
                        String mobName = nameM.find() ? nameM.group(1).trim() : barText;
                        Component mobSuffix = extractSuffixText(bar.getName());
                        int[] mobHearts = deriveHeartsFromBar(bar, mobHP, mobMaxHP);
                        mobBars.add(new MobBossBarEntry(mobName, mobSuffix, mobHP, mobMaxHP,
                                mobHearts[0], mobHearts[1]));
                        if (cfg.hideMobBars) toHide.add(entry.getKey());
                    }
                } else if (teamBar == null && TEAM_PLAYER_PATTERN.matcher(barText).find()) {
                    teamBar = bar;
                    hasTeamBossBar = true;
                    if (cfg.hideTeamBar) toHide.add(entry.getKey());
                }
            }

            activeMobBars = Collections.unmodifiableList(mobBars);

            hiddenBarUUIDs = Collections.unmodifiableSet(toHide);

            if (personalBar != null) {
                parsePersonalBar(personalBar);
            }

            if (teamBar != null) {
                parseTeamBar(teamBar, playerName);
                // v5.3.3：两段稳定排序 —— 先按 lives 降序（旧行为），然后把"自己"强制提到首位。
                // 保证玩家打开 HUD 第一眼就看到自己的当前状态，而队友间的相对顺序仍按 lives 保留。
                teammates.sort((a, b) -> Integer.compare(b.lives, a.lives));
                teammates.sort((a, b) -> Boolean.compare(b.isSelf, a.isSelf));
            }
        } catch (Exception ignored) {
        }
    }

    private void parsePersonalBar(BossEvent bar) {
        String barText = bar.getName().getString();
        bossBarPercent = bar.getProgress();

        // v5.3.1：右侧栏字段在此集中清零，然后由下面的各 find 块重新赋值。
        // 这样保证 bossbar 真的出现且可解析时刷新，bossbar 消失时则保留上一次值（sticky）。
        // 清零前后行为：
        //   · 正常一帧：走完这里 → 各 matcher 从新 bossbar 读值覆盖（与旧版一致）；
        //   · 职业切换：旧 Joey 的 hasBlood=true 被清成 false，若新 bossbar 没 Blood 段就保持 false（不会卡）；
        //   · 癫狂闪烁：本方法根本不会被调用（bossbar 文本无 HP 段），所有字段保留上一帧值。
        hasManaField = false;
        hasStaminaField = false;
        hasBlood = false;
        hasVelocity = false;
        mana = 0;
        maxMana = 0;
        stamina = 0;
        maxStamina = 0;
        blood = 0;
        maxBlood = 0;
        velocity = 0;
        maxVelocity = 0;
        bossBarCoins = 0;

        Matcher hpMatcher = HP_PATTERN.matcher(barText);
        if (hpMatcher.find()) {
            allHearts = Integer.parseInt(hpMatcher.group(1));
            maxHP = Integer.parseInt(hpMatcher.group(2));
            hasBossBarData = true;
        }

        Matcher livesMatcher = LIVES_PATTERN.matcher(barText);
        if (livesMatcher.find()) {
            lives = Integer.parseInt(livesMatcher.group(1));
        }

        Matcher manaMatcher = MANA_PATTERN.matcher(barText);
        if (manaMatcher.find()) {
            hasManaField = true;
            mana = Integer.parseInt(manaMatcher.group(1));
            maxMana = Integer.parseInt(manaMatcher.group(2));
        }

        // v8.6.1 · SwanJelly (ClassPassive=14) 的 bossbar 写的是 (Stamina x/y) 段：
        // 单独解析进 stamina/maxStamina，不再冒充法力。
        Matcher staminaMatcher = STAMINA_PATTERN.matcher(barText);
        if (staminaMatcher.find()) {
            hasStaminaField = true;
            stamina = Integer.parseInt(staminaMatcher.group(1));
            maxStamina = Integer.parseInt(staminaMatcher.group(2));
        }

        // Joey 专属：鲜血值段存在时记录，用于把主 HUD 右侧"下方法力条"替换为深红鲜血条（上方 ✦ 法力文字仍保留）。
        Matcher bloodMatcher = BLOOD_PATTERN.matcher(barText);
        if (bloodMatcher.find()) {
            hasBlood = true;
            blood = Integer.parseInt(bloodMatcher.group(1));
            maxBlood = Integer.parseInt(bloodMatcher.group(2));
        }

        // 果冻斯旺专属：动量段存在时记录，用于把 HUD 顶行右侧的 `✦mana/maxMana` 替换为粉色 `⚡velocity/maxVelocity`。
        // v8.6.1：下方条改走 stamina/maxStamina 独立通道（手持近战切 Stamina 条、
        // 平时显示 payload 法力条），不再借用法力字段。
        Matcher velocityMatcher = VELOCITY_PATTERN.matcher(barText);
        if (velocityMatcher.find()) {
            hasVelocity = true;
            velocity = Integer.parseInt(velocityMatcher.group(1));
            maxVelocity = Integer.parseInt(velocityMatcher.group(2));
        }

        Matcher coinsMatcher = COINS_PATTERN.matcher(barText);
        if (coinsMatcher.find()) {
            bossBarCoins = Integer.parseInt(coinsMatcher.group(1));
        }

        if (hasBossBarData && maxHP > 0) {
            healthPercent = Math.round((float) allHearts * 100 / maxHP);
        }
    }

    private void parseTeamBar(BossEvent bar, String selfName) {
        String barText = bar.getName().getString();
        Matcher matcher = TEAM_PLAYER_PATTERN.matcher(barText);
        Minecraft client = Minecraft.getInstance();
        Scoreboard scoreboard = (client.level != null) ? client.level.getScoreboard() : null;
        while (matcher.find()) {
            String name = matcher.group(1);
            int hp = Integer.parseInt(matcher.group(2));
            int maxHp = Integer.parseInt(matcher.group(3));
            // v5.3.3：本客户端玩家本身也放进 teammates 列表，在 HUD 的"队友血量面板"里可见。
            // 旧版 `if (!name.equals(selfName))` 把自己滤掉是为了给自己的主血条让位，
            // 但主 HUD 左上角的 4 层大血条和队友面板的小条代表的数据不同（前者多层心叠，后者只有 HP 百分比），
            // 让自己也出现能一眼看到自己在队伍排名的 HP / Lives 情况，和真队友形成对比。
            Integer mateScore = readScore(client, scoreboard, name, "Lives");
            int mateLives = mateScore != null ? Math.max(0, mateScore) : 0;

            // v8.4.0 · 服务端 TeamHeartsBroadcaster 命中时优先用其四色心 + maxHp + lives
            // 数据，让队友头顶血条也能走 4 色心叠加（与玩家自己主血条一致）。
            // cache 失鲜 / 服务端没装 mod / 玩家不在 team hearts entries 时退回 vanilla 路径：
            //   - hp / maxHp 仍走 bossbar 正则（队伍 bossbar 是 vanilla 行为，无 mod 也有）
            //   - lives 仍走客户端本地 scoreboard
            //   - 四色心字段全 0，渲染端检测 hasLayeredHearts() == false → 老 OVERFLOW_COLORS 单色条
            int soul = 0, black = 0, blue = 0;
            com.ctt.healthdisplay.network.TeamHeartsPayload.Entry entry =
                    com.ctt.healthdisplay.client.ClientTeamHeartsCache.lookup(name);
            if (entry != null) {
                soul  = entry.soulHearts();
                black = entry.blackHearts();
                blue  = entry.blueHearts();
                // 服务端有更权威的 maxHp / lives —— 但 hp 仍以 bossbar 解析为准（动画即时性更高，
                // 服务端 5 Hz 推送有 200 ms 抖动；bossbar 是 vanilla 实时同步）。
                if (entry.maxHp() > 0) maxHp = entry.maxHp();
                if (entry.lives() > 0) mateLives = entry.lives();
            }
            boolean self = name.equals(selfName);
            Integer cracked = entry == null ? null : entry.crackedHearts();
            if (cracked == null) cracked = readScore(client, scoreboard, name, "CrackedHearts");
            teammates.add(new TeammateData(name, hp, maxHp, mateLives,
                    self, entry != null && entry.maxHp() > 0 ? entry.redHearts() : -1,
                    soul, black, blue, cracked != null ? Math.max(0, cracked) : self ? crackedHearts : 0));
        }
    }

    public float getDisplayPercent() { return displayPercent; }
    public float getDisplayAllHearts() { return displayAllHearts; }
    public float getDisplayMaxHP() { return displayMaxHP; }
    public float getDisplayMana() { return displayMana; }
    public float getDisplayMaxMana() { return displayMaxMana; }
    public boolean isAvailable() { return available; }
    public boolean isPersonalAvailable() { return personalAvailable; }
    public boolean hasRawValues() { return hasBossBarData || hasStatsFallback; }
    // v5.3.1：去掉 `hasBossBarData &&` 依赖。原先癫狂闪烁时 hasBossBarData 会在 true/false 间疯狂跳，
    // 蓝条跟着交替画/不画 → 这正是"蓝量条跟着闪"的根本原因。
    // 新版只看 hasManaField：只要上次成功解析过 Mana 段且未被 gameNotStarted 守卫清掉，就保持 true，
    // 蓝条持续显示，数值保留上一帧的正确值。
    public boolean hasMana() { return hasManaField; }

    /** v8.6.1 · 是否有可信的体力数据（bossbar Stamina 段或 payload 通道任一命中）。 */
    public boolean hasStamina() { return hasStaminaField && maxStamina > 0; }

    public int getStaminaPercent() {
        if (maxStamina <= 0) return 0;
        return Math.max(0, Math.min(100, Math.round((float) stamina * 100 / maxStamina)));
    }

    public int getManaPercent() {
        if (maxMana <= 0) return 0;
        return Math.round((float) mana * 100 / maxMana);
    }

    public int getEffectivePercent() {
        if (hasBossBarData) return Math.round(bossBarPercent * 100);
        if (hasStatsFallback && maxHP > 0) {
            return Math.max(0, Math.min(200, Math.round((float) allHearts * 100 / maxHP)));
        }
        return healthPercent;
    }

    public void resetCoinData() {
        coins = 0;
        bossBarCoins = 0;
    }

    private static Integer readScore(Minecraft client, Scoreboard scoreboard, String owner, String objectiveName) {
        Integer value = ClientScorePackets.playerScore(client, objectiveName, owner);
        if (value != null) return value;
        Objective objective = scoreboard == null ? null : scoreboard.getObjective(objectiveName);
        if (objective == null) return null;
        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(owner), objective);
        return score == null ? null : score.value();
    }

    private static float lerp(float current, float target, float speed) {
        if (Math.abs(current - target) < 0.5f) return target;
        return current + (target - current) * speed;
    }

    /**
     * v8.5.6 · 纯客户端推导怪物身上的魂心 / 黑心数（CakeTeamPack 源码逐行核实）。
     *
     * <h2>数据包通道</h2>
     * <ul>
     *   <li>标题左数 AllHearts = RedHearts + SoulHearts + BlackHearts + BlueHearts
     *       （health.mcfunction 25-28 行），且 RedHearts 被钳在 MaxHP 以下
     *       （45 行 {@code RedHearts < MaxHP}）→ <b>AllHearts − MaxHP &gt; 0 的差额
     *       必然全是非红心</b>。这是唯一与颜色无关、精确到 1 点的通道。</li>
     *   <li>锁定怪条（targetting / targetting_1001..1010）的颜色：
     *       HeartValue=1（魂心=0）→ 蓝条；HeartValue=2（魂心≥1）→ 黄条，
     *       且 value=SoulHearts、max=MaxSoulHearts，比值可反推魂心。</li>
     * </ul>
     *
     * <h2>为什么 v8.5.5 的"只看黄/蓝条"对 Zunzite Golem 无效（用户实测）</h2>
     * <p>数据包只在 {@code @e[tag=E]} 上维护 HeartValue（misc.mcfunction 1065-1066），
     * 而 HealthBar 锁定通道对 {@code @e[tag=Boss]} 同样生效；boss 模组怪（如
     * Ancient Armageddon 的 Zunzite Golem）走 {@code @e[tag=Boss]} 通道挂上血条时，
     * HeartValue 可能从未被赋值 → 条颜色保持创建时的默认 pink，黄/蓝推导整条链路
     * 直接返回 {0,0} → 头顶 💛 永远不出现。魂心本身照样计入 AllHearts
     * （{@code @e[tag=CTTAll]} 通道），所以改用"差额优先"规则：
     *
     * <ul>
     *   <li>黄条 → 魂心 = 差额；差额为 0（红心未打满）时退回条比值反推；</li>
     *   <li>蓝条 → 数据包保证魂心=0，差额按黑心计；紫条 → 黑心通道，差额按黑心计；</li>
     *   <li>其它任何颜色（pink/red/默认）→ 差额全部按魂心计——数据包给怪加
     *       非红心的主通道就是魂心（endless buff / Magum trials 等都是写 SoulHearts）。</li>
     * </ul>
     *
     * @return int[2]：[0]=魂心数，[1]=黑心数（不可辨的蓝心并入）
     */
    private static int[] deriveHeartsFromBar(BossEvent bar, int allHearts, int maxHP) {
        if (maxHP <= 0 || allHearts <= 0) return new int[]{0, 0};
        // 红心被数据包钳在 MaxHP 以下：超出红上限的量必然全是非红心（±1 容差防取整噪声）
        int excess = allHearts - maxHP > 1 ? allHearts - maxHP : 0;
        BossEvent.BossBarColor color = bar.getColor();
        if (color == BossEvent.BossBarColor.BLUE || color == BossEvent.BossBarColor.PURPLE) {
            // 蓝条 ⇔ HeartValue=1 ⇔ 魂心必为 0；紫条是黑心通道。差额按黑心计。
            return new int[]{0, excess};
        }
        int soul = excess;
        if (color == BossEvent.BossBarColor.YELLOW && soul == 0) {
            // 黄条 ⇔ 魂心≥1，但红心没打满时差额=0，只能靠条比值反推：
            // value=SoulHearts、max=MaxSoulHearts(=max(MaxHP,魂心峰值))，
            // 魂心 ≤1e4（bossbar 钳位上限）时反推精确，更大时只是下界估计。
            float p = -1f;
            if (bar instanceof LerpingBossEvent) {
                p = ((LerpingBossEventAccessor) bar).ctt$getTargetPercent();
            }
            if (p < 0f) p = bar.getProgress(); // 兜底：拿不到目标值就用插值
            p = Math.max(0f, Math.min(1f, p));
            soul = Math.min(allHearts, Math.round(p * maxHP));
        }
        return new int[]{soul, 0};
    }

    private static Component extractSuffixText(Component fullText) {        String full = fullText.getString();
        Matcher m = MOB_SUFFIX_PATTERN.matcher(full);
        if (!m.find()) return Component.empty();

        int suffixStart = m.start(1);

        MutableComponent result = Component.empty();
        AtomicInteger pos = new AtomicInteger(0);
        AtomicBoolean collecting = new AtomicBoolean(false);

        fullText.visit((style, text) -> {
            int segStart = pos.get();
            int segEnd = segStart + text.length();
            pos.set(segEnd);

            if (segEnd <= suffixStart) return java.util.Optional.empty();

            if (segStart < suffixStart) {
                String part = text.substring(suffixStart - segStart);
                if (!part.isEmpty()) {
                    result.append(Component.literal(part).withStyle(style));
                    collecting.set(true);
                }
            } else {
                result.append(Component.literal(text).withStyle(style));
                collecting.set(true);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);

        return result;
    }

    public static class TeammateData {
        public final String name;
        public final int hp;
        public final int maxHP;
        public final int lives;
        // v5.3.3：标记本条是否为"自己"。用于队友 HUD 把自己排首位 + 名字用金色 §6 和真队友区分。
        // 3D 头顶血条那边通过 `player == Minecraft.getInstance().player` 过滤自己，不受此字段影响。
        public final boolean isSelf;

        // 服务端同步的红心，-1 代表没有可用的分项数据。
        public final int redHeartsServer;
        public final int soulHearts;
        public final int blackHearts;
        public final int blueHearts;
        public final int crackedHearts;

        /** v8.3.x 兼容构造（无服务端四色心数据）。 */
        public TeammateData(String name, int hp, int maxHP, int lives, boolean isSelf) {
            this(name, hp, maxHP, lives, isSelf, -1, 0, 0, 0, 0);
        }

        /** v8.4.0 · 含服务端四色心数据的完整构造。 */
        public TeammateData(String name, int hp, int maxHP, int lives, boolean isSelf,
                            int redHeartsServer, int soulHearts, int blackHearts, int blueHearts, int crackedHearts) {
            this.name = name;
            this.hp = hp;
            this.maxHP = maxHP;
            this.lives = lives;
            this.isSelf = isSelf;
            this.redHeartsServer = redHeartsServer;
            this.soulHearts = soulHearts;
            this.blackHearts = blackHearts;
            this.blueHearts = blueHearts;
            this.crackedHearts = crackedHearts;
        }

        public int effectiveMaxHp() {
            return Math.max(0, maxHP - crackedHearts);
        }

        public int getPercent() {
            if (maxHP <= 0) return 100;
            return Math.round((float) hp * 100 / maxHP);
        }

        /**
         * v8.4.0 · 是否具备四色心叠加渲染所需数据。
         *
         * <p>判定条件 {@code (soul|black|blue) > 0}：服务端推过且至少一种非红心 ≥ 1
         * 才有意义切到 layered。如果所有非红心都是 0，layered 与单色条视觉等价，
         * 没必要切（少一层 fill 调用）。{@code redHeartsServer} 单独 &gt; 0 也不
         * 触发——队友 hp 来自 bossbar 比 redHeartsServer 更实时，红心数自身不必走
         * 服务端字段。
         */
        public boolean hasLayeredHearts() {
            return (soulHearts | blackHearts | blueHearts) > 0;
        }
    }

    public static class MobBossBarEntry {
        public final String name;
        public final Component suffixText;
        public final int hp;
        public final int maxHP;
        /** v8.5.3：从 bossbar 颜色+进度推导的魂心数（无魂心/非黄条为 0）。 */
        public final int soulHearts;
        /** v8.5.4：蓝条时 AllHearts 高出红心的余额，按黑心计入（数据包对怪无蓝心通道）。 */
        public final int blackHearts;

        public MobBossBarEntry(String name, Component suffixText, int hp, int maxHP) {
            this(name, suffixText, hp, maxHP, 0, 0);
        }

        public MobBossBarEntry(String name, Component suffixText, int hp, int maxHP, int soulHearts) {
            this(name, suffixText, hp, maxHP, soulHearts, 0);
        }

        public MobBossBarEntry(String name, Component suffixText, int hp, int maxHP,
                               int soulHearts, int blackHearts) {
            this.name = name;
            this.suffixText = suffixText;
            this.hp = hp;
            this.maxHP = maxHP;
            this.soulHearts = soulHearts;
            this.blackHearts = blackHearts;
        }
    }
}
