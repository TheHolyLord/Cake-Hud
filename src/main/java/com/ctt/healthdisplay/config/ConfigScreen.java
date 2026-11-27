package com.ctt.healthdisplay.config;

import com.ctt.healthdisplay.hud.HealthBarRenderer;
import com.ctt.healthdisplay.hud.StatsRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen extends Screen implements PreviewDrag {

    private final Screen parent;
    private final boolean layoutOnly;
    private int enemyBarOpacity, enemyTextOpacity, playerBarOpacity, playerTextOpacity;
    private int enemyHeadDistance, playerHeadDistance, statsOpacity, shieldOpacity;
    private boolean compactHudNumbers, showEnemyHealthDuringEffects, suppressNpcHealth;

    private int mateX, mateY;
    private boolean horizontal;
    private boolean mateAlignRight, statsAlignRight;
    private boolean draggingMate = false;
    private double mateDragOX, mateDragOY;

    private int statsX, statsY;
    private boolean draggingStats = false;
    private double statsDragOX, statsDragOY;

    private int refreshInterval;
    private boolean showHeadHP;
    private int mobHPMode;
    private int enemyBarLength;
    private int teammateBarHalfWidth;
    private int statsColumns;
    private boolean showStatsOutsideInventory;
    private boolean showStatsInInventory;
    private boolean loreTooltipAtCursor;
    private boolean hidePersonalBar;
    private boolean hideTeamBar;
    private boolean hideMobBars;
    private boolean showArrowPreview;
    private boolean showShieldCooldown;
    private boolean shieldHorizontal;
    private int shieldSize;

    // v7.0.0 · 客户端探针 (ClientDamageProbe) 开关
    private boolean clientDamageHudHeader;
    private boolean clientDamageDebugChat;
    // v7.1.2 · 队友血条下方"每人伤害/击杀"双行嵌入 HUD（OFF/ONLY_STAGE/ONLY_SESSION/BOTH）
    private int embeddedHudMode;
    // v7.1.0 · 客户端击杀报告聊天（计数本身始终开启，不暴露开关）
    private boolean clientKillDebugChat;

    private static final int[] INTERVAL_OPTIONS = {1, 2, 3, 5, 10, 15, 30};

    private static final int HEAD_SIZE = HealthBarRenderer.HEAD_SIZE;
    private static final int MATE_BAR_WIDTH = HealthBarRenderer.MATE_BAR_WIDTH;
    private static final int V_ENTRY_HEIGHT = HealthBarRenderer.MATE_ENTRY_SPACING;
    private static final int H_ENTRY_WIDTH = HealthBarRenderer.H_ENTRY_WIDTH;

    private static final int STATS_LINE_HEIGHT = 10;
    private static final String[] SAMPLE_STATS = {
            "\u5c5e\u6027 / Stats",
            "113\u2764", "-1\ud83d\udee1", "3\u2605",
            "10\u2764%", "2\ud83d\udde1", "15\u26a1"
    };

    private static final String[] SAMPLE_NAMES = {"Steve", "Alex", "Notch"};
    private static final int[] SAMPLE_HP = {75, 120, 30};
    private static final int[] SAMPLE_MAX = {100, 200, 100};

    private static final int OPTION_BTN_W = 200;
    private static final int OPTION_BTN_H = 20;
    private static final int OPTION_SPACING = 24;

    private static final int OPTION_TOP = 36;
    private final List<AbstractWidget> optionButtons = new ArrayList<>();
    private int optionPanelX;
    private int optionScrollOffset;
    private int optionContentHeight;
    private int refreshHintY;
    private List<FormattedCharSequence> refreshHint = List.of();

    public ConfigScreen(Screen parent) {
        this(parent, false);
    }

    private ConfigScreen(Screen parent, boolean layoutOnly) {
        super(Component.translatable(layoutOnly ? "ctt-health-display.config.hud_layout" : "ctt-health-display.config.title"));
        this.parent = parent;
        this.layoutOnly = layoutOnly;
    }

    private boolean firstInit = true;
    private int positionWidth, positionHeight;

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.INSTANCE;
        if (!firstInit && positionWidth > 0 && positionHeight > 0) {
            mateX = Math.round(mateX * (float) width / positionWidth);
            mateY = Math.round(mateY * (float) height / positionHeight);
            statsX = Math.round(statsX * (float) width / positionWidth);
            statsY = Math.round(statsY * (float) height / positionHeight);
        }
        positionWidth = width;
        positionHeight = height;
        if (firstInit) {
            mateX = Math.round(cfg.teammateX * width);
            mateY = (int) (cfg.teammateY * height);
            horizontal = cfg.horizontalLayout;
            mateAlignRight = cfg.teammateAlignRight;
            statsAlignRight = cfg.statsAlignRight;
            statsX = Math.round(cfg.statsX * width);
            statsY = (int) (cfg.statsY * height);
            refreshInterval = cfg.autoRefreshIntervalSeconds;
            showHeadHP = cfg.showTeammateHeadHP;
            mobHPMode = cfg.mobHeadHPMode;
            enemyBarLength = cfg.enemyBarLength;
            teammateBarHalfWidth = cfg.teammateBarHalfWidth;
            statsColumns = cfg.statsColumns;
            showStatsOutsideInventory = cfg.showStatsOutsideInventory;
            showStatsInInventory = cfg.showStatsInInventory;
            loreTooltipAtCursor = cfg.loreTooltipAtCursor;
            hidePersonalBar = cfg.hidePersonalBar;
            hideTeamBar = cfg.hideTeamBar;
            hideMobBars = cfg.hideMobBars;
            showArrowPreview = cfg.showArrowPreview;
            showShieldCooldown = cfg.showShieldCooldown;
            enemyBarOpacity = cfg.enemyBarOpacity;
            enemyTextOpacity = cfg.enemyTextOpacity;
            playerBarOpacity = cfg.playerBarOpacity;
            playerTextOpacity = cfg.playerTextOpacity;
            enemyHeadDistance = cfg.enemyHeadDistance;
            playerHeadDistance = cfg.playerHeadDistance;
            statsOpacity = cfg.statsOpacity;
            shieldOpacity = cfg.shieldOpacity;
            shieldHorizontal = cfg.shieldHorizontal;
            shieldSize = cfg.shieldSize;
            compactHudNumbers = cfg.compactHudNumbers;
            showEnemyHealthDuringEffects = cfg.showEnemyHealthDuringEffects;
            suppressNpcHealth = cfg.suppressNpcHealth;
            // v7.0.0 · 客户端探针开关（ClientDamageProbe）
            clientDamageHudHeader = cfg.clientDamageHudHeader;
            clientDamageDebugChat = cfg.clientDamageDebugChat;
            // v7.1.2 · 嵌入式 HUD（每人伤害击杀双行）4 段开关
            embeddedHudMode = cfg.embeddedHudMode;
            // v7.1.0 · 客户端击杀报告聊天（计数本身始终运行，无需开关）
            clientKillDebugChat = cfg.clientKillDebugChat;
            // v6.6.4 · M5 · 服务端字段（broadcastXxxInChat / useRedHeartsTally /
            // filterXxx 等）从此版本起搬到二级子屏 {@link ServerConfigScreen}。
            // 主屏只保留客户端 HUD 偏好，避免把"客户端能改的"和"客户端改不到"混排。
            firstInit = false;
        }

        if (layoutOnly) {
            addAlignmentButtons();
            addPositionButtons();
            return;
        }
        optionPanelX = width / 2 + 10;
        optionButtons.clear();
        int x = optionPanelX;
        int y = OPTION_TOP;
        int btnW = Math.max(1, Math.min(OPTION_BTN_W, width / 2 - 20));

        optionButtons.add(Button.builder(option("arrow_preview", onOff(showArrowPreview)), btn -> {
            showArrowPreview = !showArrowPreview;
            btn.setMessage(option("arrow_preview", onOff(showArrowPreview)));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("arrow_layout"), btn -> {
            saveMainOnly();
            minecraft.gui.setScreen(new ArrowLayoutScreen(this));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(shieldCooldownBtnText(), btn -> {
            showShieldCooldown = !showShieldCooldown;
            btn.setMessage(shieldCooldownBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(shieldStyle(), btn -> {
            shieldHorizontal = !shieldHorizontal;
            btn.setMessage(shieldStyle());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        addSlider(x, y, btnW, "shield_size", ModConfig.MIN_SHIELD_SIZE, ModConfig.MAX_SHIELD_SIZE,
                shieldSize, v -> shieldSize = v);
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("shield_layout"), btn -> {
            saveMainOnly();
            minecraft.gui.setScreen(new ShieldLayoutScreen(this));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("hud_layout"), btn -> {
            saveMainOnly();
            minecraft.gui.setScreen(new ConfigScreen(this, true));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("stat_filter"), btn -> {
            saveMainOnly();
            minecraft.gui.setScreen(new StatFilterScreen(this));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("number_format", numberFormat()), btn -> {
            compactHudNumbers = !compactHudNumbers;
            btn.setMessage(option("number_format", numberFormat()));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("title_settings"), btn -> {
            saveMainOnly();
            minecraft.gui.setScreen(new TitleSettingsScreen(this));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("player_parts"), btn -> {
            saveMainOnly();
            minecraft.gui.setScreen(new PlayerHeadOptionsScreen(this));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("enemy_parts"), btn -> {
            saveMainOnly();
            minecraft.gui.setScreen(new EnemyHeadOptionsScreen(this));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("enemy_effects", onOff(showEnemyHealthDuringEffects)), btn -> {
            showEnemyHealthDuringEffects = !showEnemyHealthDuringEffects;
            btn.setMessage(option("enemy_effects", onOff(showEnemyHealthDuringEffects)));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        optionButtons.add(Button.builder(option("npc_health", onOff(suppressNpcHealth)), btn -> {
            suppressNpcHealth = !suppressNpcHealth;
            btn.setMessage(option("npc_health", onOff(suppressNpcHealth)));
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;
        addSlider(x, y, btnW, "enemy_bar_length", ModConfig.MIN_ENEMY_BAR_LENGTH, ModConfig.MAX_ENEMY_BAR_LENGTH,
                enemyBarLength, v -> enemyBarLength = v);
        addSlider(x, y += OPTION_SPACING, btnW, "enemy_bar_opacity", 10, 100, enemyBarOpacity, v -> enemyBarOpacity = v);
        addSlider(x, y += OPTION_SPACING, btnW, "enemy_text_opacity", 10, 100, enemyTextOpacity, v -> enemyTextOpacity = v);
        addSlider(x, y += OPTION_SPACING, btnW, "enemy_distance", 4, 200, enemyHeadDistance, v -> enemyHeadDistance = v);
        addSlider(x, y += OPTION_SPACING, btnW, "player_bar_opacity", 10, 100, playerBarOpacity, v -> playerBarOpacity = v);
        addSlider(x, y += OPTION_SPACING, btnW, "player_text_opacity", 10, 100, playerTextOpacity, v -> playerTextOpacity = v);
        addSlider(x, y += OPTION_SPACING, btnW, "player_distance", 4, 200, playerHeadDistance, v -> playerHeadDistance = v);
        addSlider(x, y += OPTION_SPACING, btnW, "stats_opacity", 20, 100, statsOpacity, v -> statsOpacity = v);
        addSlider(x, y += OPTION_SPACING, btnW, "shield_opacity", 10, 100, shieldOpacity, v -> shieldOpacity = v);
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(headHPBtnText(), btn -> {
            showHeadHP = !showHeadHP;
            btn.setMessage(headHPBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(mobHPBtnText(), btn -> {
            mobHPMode = (mobHPMode + 1) % 3;
            btn.setMessage(mobHPBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(layoutBtnText(), btn -> {
            horizontal = !horizontal;
            btn.setMessage(layoutBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(refreshBtnText(), btn -> {
            int idx = 0;
            for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
                if (INTERVAL_OPTIONS[i] == refreshInterval) { idx = i; break; }
            }
            refreshInterval = INTERVAL_OPTIONS[(idx + 1) % INTERVAL_OPTIONS.length];
            btn.setMessage(refreshBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        refreshHint = font.split(Component.translatable("ctt-health-display.config.hint.refresh_interval_warning"), btnW);
        refreshHintY = y + OPTION_BTN_H + 2;
        y += OPTION_SPACING + refreshHint.size() * font.lineHeight + 3;

        addSlider(x, y, btnW, "teammate_bar_length", ModConfig.MIN_TEAMMATE_BAR_LENGTH, ModConfig.MAX_TEAMMATE_BAR_LENGTH,
                teammateBarHalfWidth * 2, length -> teammateBarHalfWidth = length / 2, 2);
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(statsColumnsBtnText(), btn -> {
            statsColumns = statsColumns % ModConfig.MAX_STATS_COLUMNS + 1;
            btn.setMessage(statsColumnsBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(statsOutsideBtnText(), btn -> {
            showStatsOutsideInventory = !showStatsOutsideInventory;
            btn.setMessage(statsOutsideBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(statsInventoryBtnText(), btn -> {
            showStatsInInventory = !showStatsInInventory;
            btn.setMessage(statsInventoryBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(lorePositionBtnText(), btn -> {
            loreTooltipAtCursor = !loreTooltipAtCursor;
            btn.setMessage(lorePositionBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING + 6;

        optionButtons.add(Button.builder(hidePersonalBtnText(), btn -> {
            hidePersonalBar = !hidePersonalBar;
            btn.setMessage(hidePersonalBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(hideTeamBtnText(), btn -> {
            hideTeamBar = !hideTeamBar;
            btn.setMessage(hideTeamBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(hideMobBarsBtnText(), btn -> {
            hideMobBars = !hideMobBars;
            btn.setMessage(hideMobBarsBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING + 6;

        // v7.0.0 · 客户端伤害探针 (ClientDamageProbe) ——
        //   - HUD 顶部聚合行：⚔ 全局 · ⚔ 当前关 ⚡ 5sDPS/s
        //   - 聊天栏粒子流水：每个 DamageShower 粒子在本地聊天打 [CDP] 日志（调试用，会刷屏）
        // 默认 hud=on / debugChat=off。
        optionButtons.add(Button.builder(clientDamageHudHeaderBtnText(), btn -> {
            clientDamageHudHeader = !clientDamageHudHeader;
            btn.setMessage(clientDamageHudHeaderBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        // v7.1.2 · 每人伤害击杀双行（嵌入式 HUD）4 段循环按钮：
        //   关闭 → 仅本关 → 仅本局 → 全部显示 → 关闭 ...
        // 默认 BOTH（全部显示）；设计语义见 ModConfig.embeddedHudMode 注释。
        optionButtons.add(Button.builder(embeddedHudModeBtnText(), btn -> {
            embeddedHudMode = (embeddedHudMode + 1) % 4;
            btn.setMessage(embeddedHudModeBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        optionButtons.add(Button.builder(clientDamageDebugChatBtnText(), btn -> {
            clientDamageDebugChat = !clientDamageDebugChat;
            btn.setMessage(clientDamageDebugChatBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING;

        // v7.1.0 · 客户端击杀计数本身始终开启（无开关），仅暴露"是否打聊天报告"
        optionButtons.add(Button.builder(clientKillDebugChatBtnText(), btn -> {
            clientKillDebugChat = !clientKillDebugChat;
            btn.setMessage(clientKillDebugChatBtnText());
        }).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_SPACING + 6;

        // v6.6.4 · M5 · 服务器配置二级子屏入口（broadcast / useRedHearts / filter 等都进了那边）。
        optionButtons.add(Button.builder(
                Component.translatable("ctt-health-display.config.btn.open_server_config"),
                btn -> {
                    // 同步保存当前主屏未提交的修改，避免打开子屏时丢失
                    saveMainOnly();
                    minecraft.gui.setScreen(new ServerConfigScreen(this));
                }
        ).bounds(x, y, btnW, OPTION_BTN_H).build());
        y += OPTION_BTN_H;

        optionContentHeight = y - OPTION_TOP;
        optionScrollOffset = clamp(optionScrollOffset, 0, maxOptionScroll());
        // 只有完整落在列表内的按钮才注册，避免遮住底部按钮或抢走点击和焦点。
        for (AbstractWidget button : optionButtons) {
            button.setY(button.getY() - optionScrollOffset);
            if (button.getY() >= OPTION_TOP && button.getBottom() <= OPTION_TOP + optionVisibleHeight()) {
                addRenderableWidget(button);
            }
        }

        addPositionButtons();
    }

    private static Component option(String key, Object... args) {
        return Component.translatable("ctt-health-display.config.option." + key, args);
    }

    private Component shieldStyle() {
        return option("shield_style", Component.translatable("ctt-health-display.config.value.shield_style."
                + (shieldHorizontal ? "horizontal" : "vertical")));
    }

    private Component numberFormat() {
        return Component.translatable("ctt-health-display.config.value.number_format." + (compactHudNumbers ? "compact" : "full"));
    }

    private void addSlider(int x, int y, int w, String key, int min, int max, int initial, IntConsumer setter) {
        addSlider(x, y, w, key, min, max, initial, setter, 1);
    }

    private void addSlider(int x, int y, int w, String key, int min, int max, int initial, IntConsumer setter, int step) {
        int steps = (max - min) / step;
        optionButtons.add(new AbstractSliderButton(x, y, w, OPTION_BTN_H, option(key, initial),
                (Math.clamp(initial, min, max) - min) / (double) (max - min)) {
            private int selected() {
                return min + step * (int) Math.round(value * steps);
            }
            @Override protected void updateMessage() { setMessage(option(key, selected())); }
            @Override protected void applyValue() { setter.accept(selected()); }
        });
    }

    private void addAlignmentButtons() {
        int w = Math.max(1, Math.min(146, (width - 16) / 2));
        int y = height - 52;
        int cx = width / 2;
        addRenderableWidget(Button.builder(alignmentLabel("teammate_alignment", mateAlignRight), button -> {
            int left = matePreviewX();
            mateAlignRight = !mateAlignRight;
            mateX = mateAlignRight ? left + getMatePanelW() : left;
            button.setMessage(alignmentLabel("teammate_alignment", mateAlignRight));
        }).bounds(cx - w - 2, y, w, 20).build());
        addRenderableWidget(Button.builder(alignmentLabel("stats_alignment", statsAlignRight), button -> {
            int left = statsPreviewX();
            statsAlignRight = !statsAlignRight;
            statsX = statsAlignRight ? left + getStatsPanelW() : left;
            button.setMessage(alignmentLabel("stats_alignment", statsAlignRight));
        }).bounds(cx + 2, y, w, 20).build());
    }

    private static Component alignmentLabel(String key, boolean alignRight) {
        return option(key, Component.translatable("ctt-health-display.config.value.alignment."
                + (alignRight ? "right" : "left")));
    }

    private int previewBottom() {
        return height - (layoutOnly ? 56 : 36);
    }

    private void addPositionButtons() {
        int bottomY = height - 28;
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("ctt-health-display.config.btn.reset_position"), btn -> {
            mateX = (int) ((mateAlignRight ? 1 - 0.00625f : 0.00625f) * width);
            mateY = (int) (0.26912183f * height);
            statsX = (int) ((statsAlignRight ? 1 - 0.0046875f : 0.0046875f) * width);
            statsY = (int) (0.014164306f * height);
        }).bounds(cx - 102, bottomY, 100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("ctt-health-display.config.btn.done"), btn -> saveAndClose())
                .bounds(cx + 2, bottomY, 100, 20).build());
    }

    private Component shieldCooldownBtnText() {
        return Component.translatable("ctt-health-display.config.option.shield_cooldown",
                Component.translatable(showShieldCooldown ? "ctt-health-display.config.value.on"
                        : "ctt-health-display.config.value.off"));
    }

    private Component layoutBtnText() {
        return Component.translatable(
                "ctt-health-display.config.option.teammate_layout",
                Component.translatable(horizontal
                        ? "ctt-health-display.config.value.layout.horizontal"
                        : "ctt-health-display.config.value.layout.vertical").getString()
        );
    }

    private Component refreshBtnText() {
        return Component.translatable("ctt-health-display.config.option.refresh_interval_seconds", refreshInterval);
    }

    private Component headHPBtnText() {
        return Component.translatable(
                "ctt-health-display.config.option.teammate_head_hp",
                Component.translatable(showHeadHP
                        ? "ctt-health-display.config.value.on"
                        : "ctt-health-display.config.value.off").getString()
        );
    }

    private Component mobHPBtnText() {
        Component label = switch (mobHPMode) {
            case ModConfig.MOB_HP_MODE_ALL -> Component.translatable("ctt-health-display.config.value.mob_hp_mode.all");
            case ModConfig.MOB_HP_MODE_NEAREST -> Component.translatable("ctt-health-display.config.value.mob_hp_mode.nearest");
            default -> Component.translatable("ctt-health-display.config.value.mob_hp_mode.off");
        };
        return Component.translatable("ctt-health-display.config.option.mob_head_hp", label);
    }

    private Component statsColumnsBtnText() {
        return Component.translatable("ctt-health-display.config.option.stats_columns", statsColumns);
    }

    private Component statsOutsideBtnText() {
        return Component.translatable("ctt-health-display.config.option.stats_outside_inventory",
                onOff(showStatsOutsideInventory));
    }

    private Component statsInventoryBtnText() {
        return Component.translatable("ctt-health-display.config.option.stats_in_inventory",
                onOff(showStatsInInventory));
    }

    private Component lorePositionBtnText() {
        return Component.translatable("ctt-health-display.config.option.lore_tooltip_position",
                Component.translatable(loreTooltipAtCursor ? "ctt-health-display.config.value.lore_position.cursor"
                        : "ctt-health-display.config.value.lore_position.left"));
    }

    private static Component onOff(boolean enabled) {
        return Component.translatable(enabled ? "ctt-health-display.config.value.on"
                : "ctt-health-display.config.value.off");
    }

    private Component hidePersonalBtnText() {
        return Component.translatable(
                "ctt-health-display.config.option.hide_personal_bossbar",
                Component.translatable(hidePersonalBar
                        ? "ctt-health-display.config.value.yes"
                        : "ctt-health-display.config.value.no").getString()
        );
    }

    private Component hideTeamBtnText() {
        return Component.translatable(
                "ctt-health-display.config.option.hide_team_bossbar",
                Component.translatable(hideTeamBar
                        ? "ctt-health-display.config.value.yes"
                        : "ctt-health-display.config.value.no").getString()
        );
    }

    private Component hideMobBarsBtnText() {
        return Component.translatable(
                "ctt-health-display.config.option.hide_mob_bossbars",
                Component.translatable(hideMobBars
                        ? "ctt-health-display.config.value.yes"
                        : "ctt-health-display.config.value.no").getString()
        );
    }

    private Component clientDamageHudHeaderBtnText() {
        return Component.translatable(
                "ctt-health-display.config.option.client_damage_hud_header",
                Component.translatable(clientDamageHudHeader
                        ? "ctt-health-display.config.value.on"
                        : "ctt-health-display.config.value.off").getString()
        );
    }

    private Component embeddedHudModeBtnText() {
        String stateKey = switch (embeddedHudMode) {
            case ModConfig.EMBED_OFF          -> "ctt-health-display.config.value.embedded_hud_mode.off";
            case ModConfig.EMBED_ONLY_STAGE   -> "ctt-health-display.config.value.embedded_hud_mode.only_stage";
            case ModConfig.EMBED_ONLY_SESSION -> "ctt-health-display.config.value.embedded_hud_mode.only_session";
            default                            -> "ctt-health-display.config.value.embedded_hud_mode.both";
        };
        return Component.translatable("ctt-health-display.config.option.embedded_hud_mode",
                Component.translatable(stateKey));
    }

    private Component clientDamageDebugChatBtnText() {
        return Component.translatable(
                "ctt-health-display.config.option.client_damage_debug_chat",
                Component.translatable(clientDamageDebugChat
                        ? "ctt-health-display.config.value.on"
                        : "ctt-health-display.config.value.off").getString()
        );
    }

    private Component clientKillDebugChatBtnText() {
        return Component.translatable(
                "ctt-health-display.config.option.client_kill_debug_chat",
                Component.translatable(clientKillDebugChat
                        ? "ctt-health-display.config.value.on"
                        : "ctt-health-display.config.value.off").getString()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        context.centeredText(font, this.title, width / 2, 8, 0xFFFFFF);

        if (layoutOnly) {
            if (previewBottom() > 0) {
                context.enableScissor(0, 0, width, previewBottom());
                renderMatePreview(context);
                renderStatsPreview(context);
                context.disableScissor();
            }
            context.text(font, Component.translatable("ctt-health-display.config.hint_drag"), 8, 22, 0xFFCCCCCC, true);
            return;
        }
        int dividerX = width / 2;
        context.fill(dividerX - 1, 24, dividerX, height - 36, 0x40FFFFFF);

        context.text(font, Component.translatable("ctt-health-display.config.hint_drag").getString(), 8, 26, 0xFF888888, true);

        context.text(font, Component.translatable("ctt-health-display.config.header_options").getString(), optionPanelX, 26, 0xFF888888, true);

        int visibleH = optionVisibleHeight();
        if (visibleH > 0 && optionPanelX < width) {
            context.enableScissor(optionPanelX, OPTION_TOP, width, OPTION_TOP + visibleH);
            int hintY = refreshHintY - optionScrollOffset;
            for (FormattedCharSequence line : refreshHint) {
                context.text(font, line, optionPanelX, hintY, 0xFFFF5555, true);
                hintY += font.lineHeight;
            }
            context.disableScissor();
        }

        // 预览可以跨过分隔线，但不能覆盖列表和底部按钮。
        if (width / 2 > 0 && height > 36) {
            context.enableScissor(0, 0, width / 2, height - 36);
            renderMatePreview(context);
            renderStatsPreview(context);
            context.disableScissor();
        }

        int maxScroll = maxOptionScroll();
        if (maxScroll > 0 && visibleH > 0) {
            int scrollTrackX = width - 4;
            int scrollTrackTop = OPTION_TOP;
            int scrollTrackH = visibleH;
            context.fill(scrollTrackX, scrollTrackTop, scrollTrackX + 3, scrollTrackTop + scrollTrackH, 0x30FFFFFF);
            int thumbH = clamp(scrollTrackH * visibleH / optionContentHeight, 1, scrollTrackH);
            int thumbY = scrollTrackTop + (int) ((float) optionScrollOffset / maxScroll * (scrollTrackH - thumbH));
            context.fill(scrollTrackX, thumbY, scrollTrackX + 3, thumbY + thumbH, 0x80FFFFFF);
        }
    }

    private void renderMatePreview(GuiGraphicsExtractor context) {
        int pw = getMatePanelW();
        int ph = getMatePanelH();
        int left = matePreviewX();
        drawPanelFrame(
                context, left, mateY, pw, ph, draggingMate, mateAlignRight,
                Component.translatable("ctt-health-display.config.preview.teammate_health").getString()
        );

        int x = mateAlignRight ? left + pw - MATE_BAR_WIDTH : left;
        int y = mateY + 10;
        for (int i = 0; i < SAMPLE_NAMES.length; i++) {
            int headX = HealthBarRenderer.teammateHeadX(x, mateAlignRight);
            context.fill(headX, y, headX + HEAD_SIZE, y + HEAD_SIZE, 0xFF808080);
            HealthBarRenderer.drawTeammateLabels(context, font, x, y, SAMPLE_NAMES[i], 3 - i, i == 0, mateAlignRight);
            int barY = y + HEAD_SIZE + 1;
            HealthBarRenderer.drawTeammateBar(context, x, barY, SAMPLE_HP[i], SAMPLE_MAX[i], mateAlignRight);
            HealthBarRenderer.drawTeammateHealthLabel(context, font, x, barY, SAMPLE_HP[i], SAMPLE_MAX[i], SAMPLE_MAX[i]);
            if (horizontal) x += mateAlignRight ? -H_ENTRY_WIDTH : H_ENTRY_WIDTH;
            else y += V_ENTRY_HEIGHT;
        }
    }

    private int matePreviewX() {
        return previewLeft(mateX, getMatePanelW(), mateAlignRight);
    }

    private int statsPreviewX() {
        return previewLeft(statsX, getStatsPanelW(), statsAlignRight);
    }

    private int previewLeft(int anchor, int panelWidth, boolean alignRight) {
        if (!alignRight) return anchor;
        return Math.min(width - 1, Math.max(anchor, panelWidth)) - panelWidth;
    }

    private int getMatePanelW() {
        return MATE_BAR_WIDTH + (horizontal ? H_ENTRY_WIDTH * (SAMPLE_NAMES.length - 1) : 0);
    }

    private int getMatePanelH() {
        return 10 + V_ENTRY_HEIGHT * (horizontal ? 1 : SAMPLE_NAMES.length);
    }

    private void renderStatsPreview(GuiGraphicsExtractor context) {
        int pw = getStatsPanelW();
        int ph = getStatsPanelH();
        int left = statsPreviewX();
        drawPanelFrame(
                context, left, statsY, pw, ph, draggingStats, statsAlignRight,
                Component.translatable("ctt-health-display.config.preview.stats_panel").getString()
        );

        int edge = statsAlignRight ? left + pw : left;
        int sy = statsY + 10;
        int rows = getStatsRows();
        int step = getStatsColWidth() + StatsRenderer.COLUMN_GAP;
        for (int i = 0; i < SAMPLE_STATS.length; i++) {
            String line = SAMPLE_STATS[i];
            int x = StatsRenderer.columnX(edge, i / rows, step, font.width(line), statsAlignRight);
            int y = sy + (i % rows) * STATS_LINE_HEIGHT;
            int color = i == 0 ? 0xFFFFAA00 : 0xFFFFFFFF;
            context.text(font, line, x, y, color, true);
        }
    }

    private int getStatsColWidth() {
        int maxW = 0;
        for (String s : SAMPLE_STATS) maxW = Math.max(maxW, font.width(s));
        return maxW;
    }

    private int getStatsPanelW() {
        int columns = Math.ceilDiv(SAMPLE_STATS.length, getStatsRows());
        return columns * (getStatsColWidth() + StatsRenderer.COLUMN_GAP) - StatsRenderer.COLUMN_GAP;
    }

    private int getStatsRows() {
        return Math.ceilDiv(SAMPLE_STATS.length, statsColumns);
    }

    private int getStatsPanelH() {
        return getStatsRows() * STATS_LINE_HEIGHT + 12;
    }

    private void drawPanelFrame(GuiGraphicsExtractor context, int x, int y, int w, int h,
                                boolean active, boolean alignRight, String label) {
        int bg = active ? 0x40FFFFFF : 0x20FFFFFF;
        context.fill(x - 3, y - 3, x + w + 3, y + h + 3, bg);
        int border = active ? 0xC055FF55 : 0x80FFFFFF;
        context.fill(x - 3, y - 3, x + w + 3, y - 2, border);
        context.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, border);
        context.fill(x - 3, y - 3, x - 2, y + h + 3, border);
        context.fill(x + w + 2, y - 3, x + w + 3, y + h + 3, border);
        int labelX = alignRight ? x + w - font.width(label) : x;
        context.text(font, label, labelX, y, 0xFF55FF55, true);
    }

    public boolean beginPreviewDrag(double mouseX, double mouseY) {
        if ((!layoutOnly && mouseX >= width / 2) || mouseY >= previewBottom()) return false;
        if (hitTest(mouseX, mouseY, statsPreviewX(), statsY, getStatsPanelW(), getStatsPanelH())) {
            draggingStats = true;
            statsDragOX = mouseX - statsPreviewX();
            statsDragOY = mouseY - statsY;
            return true;
        }
        if (hitTest(mouseX, mouseY, matePreviewX(), mateY, getMatePanelW(), getMatePanelH())) {
            draggingMate = true;
            mateDragOX = mouseX - matePreviewX();
            mateDragOY = mouseY - mateY;
            return true;
        }
        return false;
    }

    public void movePreviewDrag(double mouseX, double mouseY) {
        if (draggingStats) {
            statsX = draggedAnchor(mouseX - statsDragOX, getStatsPanelW(), statsAlignRight);
            statsY = clamp((int) (mouseY - statsDragOY), 0, previewBottom() - getStatsPanelH());
        } else if (draggingMate) {
            mateX = draggedAnchor(mouseX - mateDragOX, getMatePanelW(), mateAlignRight);
            mateY = clamp((int) (mouseY - mateDragOY), 0, previewBottom() - getMatePanelH());
        }
    }

    private int draggedAnchor(double left, int panelWidth, boolean alignRight) {
        int rightEdge = layoutOnly ? width - 1 : width / 2 - 10;
        if (alignRight) {
            return clamp((int) left + panelWidth, Math.min(panelWidth, rightEdge), rightEdge);
        }
        return clamp((int) left, 0, rightEdge - (layoutOnly ? panelWidth : 0));
    }

    public void endPreviewDrag() {
        draggingMate = false;
        draggingStats = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!layoutOnly && mouseX >= width / 2 && mouseY >= OPTION_TOP && mouseY < height - 36 && verticalAmount != 0) {
            int step = (int) Math.copySign(Math.max(1, Math.round(Math.abs(verticalAmount) * 10)), verticalAmount);
            int nextOffset = clamp(optionScrollOffset - step, 0, maxOptionScroll());
            if (nextOffset != optionScrollOffset) {
                optionScrollOffset = nextOffset;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int optionVisibleHeight() {
        return Math.max(0, height - 36 - OPTION_TOP);
    }

    private int maxOptionScroll() {
        return Math.max(0, optionContentHeight - optionVisibleHeight());
    }

    private boolean hitTest(double mx, double my, int px, int py, int pw, int ph) {
        return mx >= px - 3 && mx <= px + pw + 3 && my >= py - 3 && my <= py + ph + 3;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * 把当前主屏的所有客户端字段写回 {@link ModConfig#INSTANCE} 并落盘 JSON。
     * <p>由 {@link #saveAndClose} 与"打开服务器配置子屏"前的同步点共用，
     * 保证用户切到子屏 / 子屏返回时不会丢主屏未提交的修改。
     */
    private void saveMainOnly() {
        ModConfig.INSTANCE.teammateX = (float) mateX / width;
        ModConfig.INSTANCE.teammateY = (float) mateY / height;
        ModConfig.INSTANCE.horizontalLayout = horizontal;
        ModConfig.INSTANCE.teammateAlignRight = mateAlignRight;
        ModConfig.INSTANCE.statsX = (float) statsX / width;
        ModConfig.INSTANCE.statsY = (float) statsY / height;
        ModConfig.INSTANCE.statsAlignRight = statsAlignRight;
        ModConfig.INSTANCE.autoRefreshIntervalSeconds = refreshInterval;
        ModConfig.INSTANCE.showTeammateHeadHP = showHeadHP;
        ModConfig.INSTANCE.mobHeadHPMode = mobHPMode;
        // 同步旧字段，防止有外部脚本/逻辑继续读 showMobHeadHP；新代码内部一律读 mobHeadHPMode。
        ModConfig.INSTANCE.showMobHeadHP = (mobHPMode != ModConfig.MOB_HP_MODE_OFF);
        ModConfig.INSTANCE.enemyBarLength = enemyBarLength;
        ModConfig.INSTANCE.teammateBarHalfWidth = teammateBarHalfWidth;
        ModConfig.INSTANCE.statsColumns = statsColumns;
        ModConfig.INSTANCE.showStatsOutsideInventory = showStatsOutsideInventory;
        ModConfig.INSTANCE.showStatsInInventory = showStatsInInventory;
        ModConfig.INSTANCE.loreTooltipAtCursor = loreTooltipAtCursor;
        ModConfig.INSTANCE.hidePersonalBar = hidePersonalBar;
        ModConfig.INSTANCE.hideTeamBar = hideTeamBar;
        ModConfig.INSTANCE.hideMobBars = hideMobBars;
        ModConfig.INSTANCE.showArrowPreview = showArrowPreview;
        ModConfig.INSTANCE.showShieldCooldown = showShieldCooldown;
        ModConfig.INSTANCE.enemyBarOpacity = enemyBarOpacity;
        ModConfig.INSTANCE.enemyTextOpacity = enemyTextOpacity;
        ModConfig.INSTANCE.playerBarOpacity = playerBarOpacity;
        ModConfig.INSTANCE.playerTextOpacity = playerTextOpacity;
        ModConfig.INSTANCE.enemyHeadDistance = enemyHeadDistance;
        ModConfig.INSTANCE.playerHeadDistance = playerHeadDistance;
        ModConfig.INSTANCE.statsOpacity = statsOpacity;
        ModConfig.INSTANCE.shieldOpacity = shieldOpacity;
        ModConfig.INSTANCE.shieldHorizontal = shieldHorizontal;
        ModConfig.INSTANCE.shieldSize = shieldSize;
        ModConfig.INSTANCE.compactHudNumbers = compactHudNumbers;
        ModConfig.INSTANCE.showEnemyHealthDuringEffects = showEnemyHealthDuringEffects;
        ModConfig.INSTANCE.suppressNpcHealth = suppressNpcHealth;
        // v7.0.0 · 客户端探针开关
        ModConfig.INSTANCE.clientDamageHudHeader = clientDamageHudHeader;
        ModConfig.INSTANCE.clientDamageDebugChat = clientDamageDebugChat;
        // v7.1.2 · 嵌入式 HUD（每人伤害击杀双行）4 段
        ModConfig.INSTANCE.embeddedHudMode = embeddedHudMode;
        // v7.1.0 · 客户端击杀报告聊天（计数本身始终开启，无字段）
        ModConfig.INSTANCE.clientKillDebugChat = clientKillDebugChat;
        ModConfig.INSTANCE.save();
    }

    private void saveAndClose() {
        if (layoutOnly && parent instanceof ConfigScreen config) {
            config.mateX = mateX;
            config.mateY = mateY;
            config.statsX = statsX;
            config.statsY = statsY;
            config.mateAlignRight = mateAlignRight;
            config.statsAlignRight = statsAlignRight;
            config.positionWidth = width;
            config.positionHeight = height;
        }
        saveMainOnly();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

}
