package com.ctt.healthdisplay.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class ConfigScreen extends Screen {
   private final Screen parent;
   private int mateX;
   private int mateY;
   private boolean horizontal;
   private boolean draggingMate = false;
   private double mateDragOX;
   private double mateDragOY;
   private int statsX;
   private int statsY;
   private boolean draggingStats = false;
   private int shieldX = -1;
   private int shieldY = -1;
   private boolean draggingShield = false;
   private double shieldDragOX;
   private double shieldDragOY;
   private double statsDragOX;
   private double statsDragOY;
   private int refreshInterval;
   private boolean showHeadHP;
   private int mobHPMode;
   private int mobBarHalfWidth;
   private int teammateBarHalfWidth;
   private int statsColumns;
   private boolean showStatsOutsideInventory;
   private boolean showStatsInInventory;
   private boolean loreTooltipAtCursor;
   private boolean hidePersonalBar;
   private boolean hideTeamBar;
   private boolean hideMobBars;
   private boolean showShieldCooldown;
   private boolean clientDamageHudHeader;
   private boolean clientDamageDebugChat;
   private int embeddedHudMode;
   private boolean clientKillDebugChat;
   private static final int[] INTERVAL_OPTIONS = new int[]{1, 2, 3, 5, 10, 15, 30};
   private static final int[] BAR_WIDTH_OPTIONS = new int[]{30, 40, 52, 60, 70, 80};
   private static final int[] MATE_BAR_WIDTH_OPTIONS = new int[]{15, 20, 25, 30, 40, 50};
   private static final int HEAD_SIZE = 8;
   private static final int MATE_BAR_WIDTH = 60;
   private static final int MATE_BAR_HEIGHT = 3;
   private static final int V_ENTRY_HEIGHT = 16;
   private static final int H_ENTRY_WIDTH = 80;
   private static final int STATS_LINE_HEIGHT = 10;
   private static final String[] SAMPLE_STATS = new String[]{"属性 / Stats", "113❤", "-1\ud83d\udee1", "3★", "10❤%", "2\ud83d\udde1", "15⚡"};
   private static final String[] SAMPLE_NAMES = new String[]{"Steve", "Alex", "Notch"};
   private static final int[] SAMPLE_HP = new int[]{75, 120, 30};
   private static final int[] SAMPLE_MAX = new int[]{100, 200, 100};
   private static final int OPTION_BTN_W = 200;
   private static final int OPTION_BTN_H = 20;
   private static final int OPTION_SPACING = 24;
   private static final int OPTION_TOP = 36;
   private final List<Button> optionButtons = new ArrayList<>();
   private int optionPanelX;
   private int optionScrollOffset;
   private int optionContentHeight;
   private int refreshHintY;
   private List<FormattedCharSequence> refreshHint = List.of();
   private boolean firstInit = true;

   public ConfigScreen(Screen parent) {
      super(Component.translatable("ctt-health-display.config.title"));
      this.parent = parent;
   }

   protected void init() {
      ModConfig cfg = ModConfig.INSTANCE;
      if (this.firstInit) {
         this.mateX = (int)(cfg.teammateX * this.width);
         this.mateY = (int)(cfg.teammateY * this.height);
         this.horizontal = cfg.horizontalLayout;
         this.statsX = (int)(cfg.statsX * this.width);
         this.statsY = (int)(cfg.statsY * this.height);
         this.refreshInterval = cfg.autoRefreshIntervalSeconds;
         this.showHeadHP = cfg.showTeammateHeadHP;
         this.mobHPMode = cfg.mobHeadHPMode;
         this.mobBarHalfWidth = cfg.mobBarHalfWidth;
         this.teammateBarHalfWidth = cfg.teammateBarHalfWidth;
         this.statsColumns = cfg.statsColumns;
         this.showStatsOutsideInventory = cfg.showStatsOutsideInventory;
         this.showStatsInInventory = cfg.showStatsInInventory;
         this.loreTooltipAtCursor = cfg.loreTooltipAtCursor;
         this.hidePersonalBar = cfg.hidePersonalBar;
         this.hideTeamBar = cfg.hideTeamBar;
         this.hideMobBars = cfg.hideMobBars;
         this.showShieldCooldown = cfg.showShieldCooldown;
         this.clientDamageHudHeader = cfg.clientDamageHudHeader;
         this.clientDamageDebugChat = cfg.clientDamageDebugChat;
         this.embeddedHudMode = cfg.embeddedHudMode;
         this.clientKillDebugChat = cfg.clientKillDebugChat;
         this.shieldX = cfg.shieldX;
         this.shieldY = cfg.shieldY;
         this.firstInit = false;
      }

      this.optionPanelX = this.width / 2 + 10;
      this.optionButtons.clear();
      int x = this.optionPanelX;
      int y = 36;
      int btnW = Math.max(1, Math.min(200, this.width / 2 - 20));
      this.optionButtons.add(Button.builder(this.shieldCooldownBtnText(), btn -> {
         this.showShieldCooldown = !this.showShieldCooldown;
         btn.setMessage(this.shieldCooldownBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.headHPBtnText(), btn -> {
         this.showHeadHP = !this.showHeadHP;
         btn.setMessage(this.headHPBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.mobHPBtnText(), btn -> {
         this.mobHPMode = (this.mobHPMode + 1) % 3;
         btn.setMessage(this.mobHPBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.layoutBtnText(), btn -> {
         this.horizontal = !this.horizontal;
         btn.setMessage(this.layoutBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.refreshBtnText(), btn -> {
         int idx = 0;

         for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i] == this.refreshInterval) {
               idx = i;
               break;
            }
         }

         this.refreshInterval = INTERVAL_OPTIONS[(idx + 1) % INTERVAL_OPTIONS.length];
         btn.setMessage(this.refreshBtnText());
      }).bounds(x, y, btnW, 20).build());
      this.refreshHint = this.font.split(Component.translatable("ctt-health-display.config.hint.refresh_interval_warning"), btnW);
      this.refreshHintY = y + 20 + 2;
      y += 24 + this.refreshHint.size() * 9 + 3;
      this.optionButtons.add(Button.builder(this.mateBarWidthBtnText(), btn -> {
         int idx = 0;

         for (int i = 0; i < MATE_BAR_WIDTH_OPTIONS.length; i++) {
            if (MATE_BAR_WIDTH_OPTIONS[i] == this.teammateBarHalfWidth) {
               idx = i;
               break;
            }
         }

         this.teammateBarHalfWidth = MATE_BAR_WIDTH_OPTIONS[(idx + 1) % MATE_BAR_WIDTH_OPTIONS.length];
         btn.setMessage(this.mateBarWidthBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.statsColumnsBtnText(), btn -> {
         this.statsColumns = this.statsColumns % 3 + 1;
         btn.setMessage(this.statsColumnsBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.statsOutsideBtnText(), btn -> {
         this.showStatsOutsideInventory = !this.showStatsOutsideInventory;
         btn.setMessage(this.statsOutsideBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.statsInventoryBtnText(), btn -> {
         this.showStatsInInventory = !this.showStatsInInventory;
         btn.setMessage(this.statsInventoryBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.lorePositionBtnText(), btn -> {
         this.loreTooltipAtCursor = !this.loreTooltipAtCursor;
         btn.setMessage(this.lorePositionBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 30;
      this.optionButtons.add(Button.builder(this.hidePersonalBtnText(), btn -> {
         this.hidePersonalBar = !this.hidePersonalBar;
         btn.setMessage(this.hidePersonalBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.hideTeamBtnText(), btn -> {
         this.hideTeamBar = !this.hideTeamBar;
         btn.setMessage(this.hideTeamBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.hideMobBarsBtnText(), btn -> {
         this.hideMobBars = !this.hideMobBars;
         btn.setMessage(this.hideMobBarsBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 30;
      this.optionButtons.add(Button.builder(this.clientDamageHudHeaderBtnText(), btn -> {
         this.clientDamageHudHeader = !this.clientDamageHudHeader;
         btn.setMessage(this.clientDamageHudHeaderBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.embeddedHudModeBtnText(), btn -> {
         this.embeddedHudMode = (this.embeddedHudMode + 1) % 4;
         btn.setMessage(this.embeddedHudModeBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.clientDamageDebugChatBtnText(), btn -> {
         this.clientDamageDebugChat = !this.clientDamageDebugChat;
         btn.setMessage(this.clientDamageDebugChatBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.clientKillDebugChatBtnText(), btn -> {
         this.clientKillDebugChat = !this.clientKillDebugChat;
         btn.setMessage(this.clientKillDebugChatBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 30;
      this.optionButtons.add(Button.builder(Component.translatable("ctt-health-display.config.btn.open_server_config"), btn -> {
         this.saveMainOnly();
         this.minecraft.gui.setScreen(new ServerConfigScreen(this));
      }).bounds(x, y, btnW, 20).build());
      y += 20;

      // ── 本 fork 显示设置 ──
      y += 10;
      this.optionButtons.add(Button.builder(forkOption("ctt-health-display.config.option.fork_mob_bar_opacity",
            forkPercent(ModConfig.INSTANCE.mobBarOpacity)), btn -> {
         ModConfig.INSTANCE.mobBarOpacity = forkNextStep(FORK_OPACITY_STEPS, ModConfig.INSTANCE.mobBarOpacity);
         ModConfig.INSTANCE.save();
         btn.setMessage(forkOption("ctt-health-display.config.option.fork_mob_bar_opacity",
               forkPercent(ModConfig.INSTANCE.mobBarOpacity)));
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(forkOption("ctt-health-display.config.option.fork_mob_text_opacity",
            forkPercent(ModConfig.INSTANCE.mobTextOpacity)), btn -> {
         ModConfig.INSTANCE.mobTextOpacity = forkNextStep(FORK_OPACITY_STEPS, ModConfig.INSTANCE.mobTextOpacity);
         ModConfig.INSTANCE.save();
         btn.setMessage(forkOption("ctt-health-display.config.option.fork_mob_text_opacity",
               forkPercent(ModConfig.INSTANCE.mobTextOpacity)));
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(forkOption("ctt-health-display.config.option.fork_team_bar_opacity",
            forkPercent(ModConfig.INSTANCE.teamBarOpacity)), btn -> {
         ModConfig.INSTANCE.teamBarOpacity = forkNextStep(FORK_OPACITY_STEPS, ModConfig.INSTANCE.teamBarOpacity);
         ModConfig.INSTANCE.save();
         btn.setMessage(forkOption("ctt-health-display.config.option.fork_team_bar_opacity",
               forkPercent(ModConfig.INSTANCE.teamBarOpacity)));
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(forkOption("ctt-health-display.config.option.fork_team_text_opacity",
            forkPercent(ModConfig.INSTANCE.teamTextOpacity)), btn -> {
         ModConfig.INSTANCE.teamTextOpacity = forkNextStep(FORK_OPACITY_STEPS, ModConfig.INSTANCE.teamTextOpacity);
         ModConfig.INSTANCE.save();
         btn.setMessage(forkOption("ctt-health-display.config.option.fork_team_text_opacity",
               forkPercent(ModConfig.INSTANCE.teamTextOpacity)));
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.forkShadowText(), btn -> {
         ModConfig.INSTANCE.textShadow = !ModConfig.INSTANCE.textShadow;
         ModConfig.INSTANCE.save();
         btn.setMessage(this.forkShadowText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(this.forkShieldStyleText(), btn -> {
         ModConfig.INSTANCE.shieldTextureStyle = !ModConfig.INSTANCE.shieldTextureStyle;
         ModConfig.INSTANCE.save();
         btn.setMessage(this.forkShieldStyleText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(forkOption("ctt-health-display.config.option.fork_shield_scale",
            forkScaleText(ModConfig.INSTANCE.shieldScale)), btn -> {
         ModConfig.INSTANCE.shieldScale = forkNextStep(FORK_SCALE_STEPS, ModConfig.INSTANCE.shieldScale);
         ModConfig.INSTANCE.save();
         btn.setMessage(forkOption("ctt-health-display.config.option.fork_shield_scale",
               forkScaleText(ModConfig.INSTANCE.shieldScale)));
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.optionButtons.add(Button.builder(forkOption("ctt-health-display.config.option.fork_shield_opacity",
            forkPercent(ModConfig.INSTANCE.shieldOpacity)), btn -> {
         ModConfig.INSTANCE.shieldOpacity = forkNextStep(FORK_OPACITY_STEPS, ModConfig.INSTANCE.shieldOpacity);
         ModConfig.INSTANCE.save();
         btn.setMessage(forkOption("ctt-health-display.config.option.fork_shield_opacity",
               forkPercent(ModConfig.INSTANCE.shieldOpacity)));
      }).bounds(x, y, btnW, 20).build());
      y += 20;

      this.optionContentHeight = y - 36;
      this.optionScrollOffset = clamp(this.optionScrollOffset, 0, this.maxOptionScroll());

      for (Button button : this.optionButtons) {
         button.setY(button.getY() - this.optionScrollOffset);
         if (button.getY() >= 36 && button.getBottom() <= 36 + this.optionVisibleHeight()) {
            this.addRenderableWidget(button);
         }
      }

      int bottomY = this.height - 28;
      int cx = this.width / 2;
      this.addRenderableWidget(Button.builder(Component.translatable("ctt-health-display.config.btn.reset_position"), btn -> {
         this.mateX = (int)(0.00625F * this.width);
         this.mateY = (int)(0.26912183F * this.height);
         this.statsX = (int)(0.0046875F * this.width);
         this.statsY = (int)(0.014164306F * this.height);
      }).bounds(cx - 102, bottomY, 100, 20).build());
      this.addRenderableWidget(
         Button.builder(Component.translatable("ctt-health-display.config.btn.done"), btn -> this.saveAndClose()).bounds(cx + 2, bottomY, 100, 20).build()
      );
   }

   // ── 本 fork 移植：不透明度 / 文字阴影 / 盾牌贴图控件 ──
   private static final double[] FORK_OPACITY_STEPS = new double[]{0.25, 0.4, 0.5, 0.65, 0.75, 0.9, 1.0};
   private static final double[] FORK_SCALE_STEPS = new double[]{0.5, 0.7, 0.85, 1.0, 1.25, 1.5};

   private static double forkNextStep(double[] steps, double current) {
      int best = 0;

      for (int i = 0; i < steps.length; i++) {
         if (Math.abs(steps[i] - current) < Math.abs(steps[best] - current)) {
            best = i;
         }
      }

      return steps[(best + 1) % steps.length];
   }

   private static Component forkPercent(double value) {
      return Component.literal(Math.round(value * 100.0) + "%");
   }

   private static Component forkScaleText(double value) {
      return Component.literal(String.format(java.util.Locale.ROOT, "%.2f×", value));
   }

   private static Component forkOption(String key, Component value) {
      return Component.translatable(key, new Object[]{value});
   }

   private Component forkShadowText() {
      return forkOption("ctt-health-display.config.option.fork_text_shadow",
         Component.translatable(ModConfig.INSTANCE.textShadow
            ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off"));
   }

   private Component forkShieldStyleText() {
      return forkOption("ctt-health-display.config.option.fork_shield_style",
         Component.translatable(ModConfig.INSTANCE.shieldTextureStyle
            ? "ctt-health-display.config.value.fork_style_texture"
            : "ctt-health-display.config.value.fork_style_bar"));
   }

   private Component shieldCooldownBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.shield_cooldown",
         new Object[]{Component.translatable(this.showShieldCooldown ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off")}
      );
   }

   private Component layoutBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.teammate_layout",
         new Object[]{
            Component.translatable(this.horizontal ? "ctt-health-display.config.value.layout.horizontal" : "ctt-health-display.config.value.layout.vertical")
               .getString()
         }
      );
   }

   private Component refreshBtnText() {
      return Component.translatable("ctt-health-display.config.option.refresh_interval_seconds", new Object[]{this.refreshInterval});
   }

   private Component headHPBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.teammate_head_hp",
         new Object[]{Component.translatable(this.showHeadHP ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off").getString()}
      );
   }

   private Component mobHPBtnText() {
      Component label = switch (this.mobHPMode) {
         case 0 -> Component.translatable("ctt-health-display.config.value.mob_hp_mode.all");
         case 1 -> Component.translatable("ctt-health-display.config.value.mob_hp_mode.nearest");
         default -> Component.translatable("ctt-health-display.config.value.mob_hp_mode.off");
      };
      return Component.translatable("ctt-health-display.config.option.mob_head_hp", new Object[]{label});
   }

   private Component mobBarWidthBtnText() {
      return Component.translatable("ctt-health-display.config.option.mob_bar_width", new Object[]{this.mobBarHalfWidth * 2});
   }

   private Component mateBarWidthBtnText() {
      return Component.translatable("ctt-health-display.config.option.teammate_bar_width", new Object[]{this.teammateBarHalfWidth * 2});
   }

   private Component statsColumnsBtnText() {
      return Component.translatable("ctt-health-display.config.option.stats_columns", new Object[]{this.statsColumns});
   }

   private Component statsOutsideBtnText() {
      return Component.translatable("ctt-health-display.config.option.stats_outside_inventory", new Object[]{onOff(this.showStatsOutsideInventory)});
   }

   private Component statsInventoryBtnText() {
      return Component.translatable("ctt-health-display.config.option.stats_in_inventory", new Object[]{onOff(this.showStatsInInventory)});
   }

   private Component lorePositionBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.lore_tooltip_position",
         new Object[]{
            Component.translatable(
               this.loreTooltipAtCursor ? "ctt-health-display.config.value.lore_position.cursor" : "ctt-health-display.config.value.lore_position.left"
            )
         }
      );
   }

   private static Component onOff(boolean enabled) {
      return Component.translatable(enabled ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off");
   }

   private Component hidePersonalBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.hide_personal_bossbar",
         new Object[]{Component.translatable(this.hidePersonalBar ? "ctt-health-display.config.value.yes" : "ctt-health-display.config.value.no").getString()}
      );
   }

   private Component hideTeamBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.hide_team_bossbar",
         new Object[]{Component.translatable(this.hideTeamBar ? "ctt-health-display.config.value.yes" : "ctt-health-display.config.value.no").getString()}
      );
   }

   private Component hideMobBarsBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.hide_mob_bossbars",
         new Object[]{Component.translatable(this.hideMobBars ? "ctt-health-display.config.value.yes" : "ctt-health-display.config.value.no").getString()}
      );
   }

   private Component clientDamageHudHeaderBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.client_damage_hud_header",
         new Object[]{
            Component.translatable(this.clientDamageHudHeader ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off").getString()
         }
      );
   }

   private Component embeddedHudModeBtnText() {
      String stateKey = switch (this.embeddedHudMode) {
         case 0 -> "ctt-health-display.config.value.embedded_hud_mode.off";
         case 1 -> "ctt-health-display.config.value.embedded_hud_mode.only_stage";
         case 2 -> "ctt-health-display.config.value.embedded_hud_mode.only_session";
         default -> "ctt-health-display.config.value.embedded_hud_mode.both";
      };
      return Component.translatable("ctt-health-display.config.option.embedded_hud_mode", new Object[]{Component.translatable(stateKey)});
   }

   private Component clientDamageDebugChatBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.client_damage_debug_chat",
         new Object[]{
            Component.translatable(this.clientDamageDebugChat ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off").getString()
         }
      );
   }

   private Component clientKillDebugChatBtnText() {
      return Component.translatable(
         "ctt-health-display.config.option.client_kill_debug_chat",
         new Object[]{
            Component.translatable(this.clientKillDebugChat ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off").getString()
         }
      );
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      super.extractRenderState(context, mouseX, mouseY, delta);
      this.renderShieldMarker(context);
      context.centeredText(this.font, this.title, this.width / 2, 8, 16777215);
      int dividerX = this.width / 2;
      context.fill(dividerX - 1, 24, dividerX, this.height - 36, 1090519039);
      context.text(this.font, Component.translatable("ctt-health-display.config.hint_drag").getString(), 8, 26, -7829368, true);
      context.text(this.font, Component.translatable("ctt-health-display.config.header_options").getString(), this.optionPanelX, 26, -7829368, true);
      int visibleH = this.optionVisibleHeight();
      if (visibleH > 0 && this.optionPanelX < this.width) {
         context.enableScissor(this.optionPanelX, 36, this.width, 36 + visibleH);
         int hintY = this.refreshHintY - this.optionScrollOffset;

         for (FormattedCharSequence line : this.refreshHint) {
            context.text(this.font, line, this.optionPanelX, hintY, -43691, true);
            hintY += 9;
         }

         context.disableScissor();
      }

      if (this.width / 2 > 0 && this.height > 36) {
         context.enableScissor(0, 0, this.width / 2, this.height - 36);
         this.renderMatePreview(context);
         this.renderStatsPreview(context);
         context.disableScissor();
      }

      int maxScroll = this.maxOptionScroll();
      if (maxScroll > 0 && visibleH > 0) {
         int scrollTrackX = this.width - 4;
         int scrollTrackTop = 36;
         int scrollTrackH = visibleH;
         context.fill(scrollTrackX, scrollTrackTop, scrollTrackX + 3, scrollTrackTop + scrollTrackH, 822083583);
         int thumbH = clamp(scrollTrackH * visibleH / this.optionContentHeight, 1, scrollTrackH);
         int thumbY = scrollTrackTop + (int)((float)this.optionScrollOffset / maxScroll * (scrollTrackH - thumbH));
         context.fill(scrollTrackX, thumbY, scrollTrackX + 3, thumbY + thumbH, -2130706433);
      }
   }

   private void renderMatePreview(GuiGraphicsExtractor context) {
      int pw = this.getMatePanelW();
      int ph = this.getMatePanelH();
      this.drawPanelFrame(
         context, this.mateX, this.mateY, pw, ph, this.draggingMate, Component.translatable("ctt-health-display.config.preview.teammate_health").getString()
      );
      int x = this.mateX;
      int y = this.mateY + 10;

      for (int i = 0; i < SAMPLE_NAMES.length; i++) {
         int pct = Math.round(SAMPLE_HP[i] * 100.0F / SAMPLE_MAX[i]);
         context.fill(x, y, x + 8, y + 8, -8355712);
         int tx = x + 8 + 2;
         context.text(this.font, SAMPLE_NAMES[i], tx, y, -1, true);
         int nw = this.font.width(SAMPLE_NAMES[i]);
         context.text(this.font, SAMPLE_HP[i] + "/" + SAMPLE_MAX[i], tx + nw + 4, y, hpTextColor(pct), true);
         int barY = y + 8 + 1;
         context.fill(x - 1, barY - 1, x + 60 + 1, barY + 3 + 1, -1610612736);
         context.fill(x, barY, x + 60, barY + 3, 1343756312);
         int fill = Math.max(0, Math.min(60, Math.round(pct / 100.0F * 60.0F)));
         if (fill > 0) {
            context.fill(x, barY, x + fill, barY + 3, hpBarColor(pct));
         }

         if (this.horizontal) {
            x += 80;
         } else {
            y += 16;
         }
      }
   }

   private int getMatePanelW() {
      return this.horizontal ? 80 * SAMPLE_NAMES.length : 62;
   }

   private int getMatePanelH() {
      int content = this.horizontal ? 14 : 16 * SAMPLE_NAMES.length;
      return content + 10;
   }

   /** 盾牌标记的当前屏幕坐标（未设置时 = 准星下方默认位置）。 */
   private int shieldMarkerX() {
      return this.shieldX >= 0 ? this.shieldX : this.width / 2;
   }

   private int shieldMarkerY() {
      return this.shieldY >= 0 ? this.shieldY : this.height / 2 + 32;
   }

   /** 设置界面里的盾牌位置标记 + 提示。 */
   private void renderShieldMarker(GuiGraphicsExtractor context) {
      int x = this.shieldMarkerX();
      int y = this.shieldMarkerY();
      context.fill(x - 10, y - 10, x + 10, y + 10, 0x60FFAA00);
      context.fill(x - 10, y - 10, x + 10, y - 9, 0xFFFFAA00);
      context.fill(x - 10, y + 9, x + 10, y + 10, 0xFFFFAA00);
      context.fill(x - 10, y - 10, x - 9, y + 10, 0xFFFFAA00);
      context.fill(x + 9, y - 10, x + 10, y + 10, 0xFFFFAA00);
      context.centeredText(this.font, Component.translatable("ctt-health-display.config.hint_drag_shield"),
            x, y - 4, 0xFFCC8800);
   }

   private void renderStatsPreview(GuiGraphicsExtractor context) {
      int pw = this.getStatsPanelW();
      int ph = this.getStatsPanelH();
      this.drawPanelFrame(
         context, this.statsX, this.statsY, pw, ph, this.draggingStats, Component.translatable("ctt-health-display.config.preview.stats_panel").getString()
      );
      int sx = this.statsX + 3;
      int sy = this.statsY + 10;
      int rows = this.getStatsRows();
      int colW = this.getStatsColWidth() + 6;

      for (int i = 0; i < SAMPLE_STATS.length; i++) {
         int col = i / rows;
         int row = i % rows;
         int color = i == 0 ? -22016 : -1;
         context.text(this.font, SAMPLE_STATS[i], sx + col * colW, sy + row * 10, color, true);
      }
   }

   private int getStatsColWidth() {
      int maxW = 0;

      for (String s : SAMPLE_STATS) {
         maxW = Math.max(maxW, this.font.width(s));
      }

      return maxW;
   }

   private int getStatsPanelW() {
      return (this.getStatsColWidth() + 6) * this.statsColumns;
   }

   private int getStatsRows() {
      return Math.ceilDiv(SAMPLE_STATS.length, this.statsColumns);
   }

   private int getStatsPanelH() {
      return this.getStatsRows() * 10 + 12;
   }

   private void drawPanelFrame(GuiGraphicsExtractor context, int x, int y, int w, int h, boolean active, String label) {
      int bg = active ? 1090519039 : 553648127;
      context.fill(x - 3, y - 3, x + w + 3, y + h + 3, bg);
      int border = active ? -1068105899 : -2130706433;
      context.fill(x - 3, y - 3, x + w + 3, y - 2, border);
      context.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, border);
      context.fill(x - 3, y - 3, x - 2, y + h + 3, border);
      context.fill(x + w + 2, y - 3, x + w + 3, y + h + 3, border);
      context.text(this.font, label, x, y, -11141291, true);
   }

   public boolean beginPreviewDrag(double mouseX, double mouseY) {
      if (!(mouseX >= this.width / 2) && !(mouseY >= this.height - 36)) {
         int sx = this.shieldMarkerX();
         int sy = this.shieldMarkerY();
         if (ModConfig.INSTANCE.shieldDraggable
               && this.hitTest(mouseX, mouseY, sx - 10, sy - 10, 20, 20)) {
            this.draggingShield = true;
            this.shieldDragOX = mouseX - sx;
            this.shieldDragOY = mouseY - sy;
            return true;
         }
         if (this.hitTest(mouseX, mouseY, this.statsX, this.statsY, this.getStatsPanelW(), this.getStatsPanelH())) {
            this.draggingStats = true;
            this.statsDragOX = mouseX - this.statsX;
            this.statsDragOY = mouseY - this.statsY;
            return true;
         } else if (this.hitTest(mouseX, mouseY, this.mateX, this.mateY, this.getMatePanelW(), this.getMatePanelH())) {
            this.draggingMate = true;
            this.mateDragOX = mouseX - this.mateX;
            this.mateDragOY = mouseY - this.mateY;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public void movePreviewDrag(double mouseX, double mouseY) {
      int maxDragX = this.width / 2 - 10;
      if (this.draggingShield) {
         this.shieldX = clamp((int)(mouseX - this.shieldDragOX), 0, this.width - 20);
         this.shieldY = clamp((int)(mouseY - this.shieldDragOY), 0, this.height - 20);
      } else if (this.draggingStats) {
         this.statsX = clamp((int)(mouseX - this.statsDragOX), 0, maxDragX);
         this.statsY = clamp((int)(mouseY - this.statsDragOY), 0, this.height - this.getStatsPanelH());
      } else if (this.draggingMate) {
         this.mateX = clamp((int)(mouseX - this.mateDragOX), 0, maxDragX);
         this.mateY = clamp((int)(mouseY - this.mateDragOY), 0, this.height - this.getMatePanelH());
      }
   }

   public void endPreviewDrag() {
      this.draggingMate = false;
      this.draggingStats = false;
      this.draggingShield = false;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (mouseX >= this.width / 2 && mouseY >= 36.0 && mouseY < this.height - 36 && verticalAmount != 0.0) {
         int step = (int)Math.copySign(Math.max(1L, Math.round(Math.abs(verticalAmount) * 10.0)), verticalAmount);
         int nextOffset = clamp(this.optionScrollOffset - step, 0, this.maxOptionScroll());
         if (nextOffset != this.optionScrollOffset) {
            this.optionScrollOffset = nextOffset;
            this.rebuildWidgets();
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   private int optionVisibleHeight() {
      return Math.max(0, this.height - 36 - 36);
   }

   private int maxOptionScroll() {
      return Math.max(0, this.optionContentHeight - this.optionVisibleHeight());
   }

   private boolean hitTest(double mx, double my, int px, int py, int pw, int ph) {
      return mx >= px - 3 && mx <= px + pw + 3 && my >= py - 3 && my <= py + ph + 3;
   }

   private static int clamp(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }

   private void saveMainOnly() {
      ModConfig.INSTANCE.teammateX = (float)this.mateX / this.width;
      ModConfig.INSTANCE.teammateY = (float)this.mateY / this.height;
      ModConfig.INSTANCE.horizontalLayout = this.horizontal;
      ModConfig.INSTANCE.statsX = (float)this.statsX / this.width;
      ModConfig.INSTANCE.statsY = (float)this.statsY / this.height;
      ModConfig.INSTANCE.autoRefreshIntervalSeconds = this.refreshInterval;
      ModConfig.INSTANCE.showTeammateHeadHP = this.showHeadHP;
      ModConfig.INSTANCE.mobHeadHPMode = this.mobHPMode;
      ModConfig.INSTANCE.showMobHeadHP = this.mobHPMode != 2;
      ModConfig.INSTANCE.mobBarHalfWidth = this.mobBarHalfWidth;
      ModConfig.INSTANCE.teammateBarHalfWidth = this.teammateBarHalfWidth;
      ModConfig.INSTANCE.statsColumns = this.statsColumns;
      ModConfig.INSTANCE.showStatsOutsideInventory = this.showStatsOutsideInventory;
      ModConfig.INSTANCE.showStatsInInventory = this.showStatsInInventory;
      ModConfig.INSTANCE.loreTooltipAtCursor = this.loreTooltipAtCursor;
      ModConfig.INSTANCE.hidePersonalBar = this.hidePersonalBar;
      ModConfig.INSTANCE.hideTeamBar = this.hideTeamBar;
      ModConfig.INSTANCE.hideMobBars = this.hideMobBars;
      ModConfig.INSTANCE.showShieldCooldown = this.showShieldCooldown;
      ModConfig.INSTANCE.shieldX = this.shieldX;
      ModConfig.INSTANCE.shieldY = this.shieldY;
      ModConfig.INSTANCE.clientDamageHudHeader = this.clientDamageHudHeader;
      ModConfig.INSTANCE.clientDamageDebugChat = this.clientDamageDebugChat;
      ModConfig.INSTANCE.embeddedHudMode = this.embeddedHudMode;
      ModConfig.INSTANCE.clientKillDebugChat = this.clientKillDebugChat;
      ModConfig.INSTANCE.save();
   }

   private void saveAndClose() {
      this.saveMainOnly();
      this.minecraft.gui.setScreen(this.parent);
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   private static int hpTextColor(int pct) {
      if (pct <= 25) {
         return -39322;
      } else {
         return pct <= 50 ? -8892 : -1;
      }
   }

   private static int hpBarColor(int pct) {
      if (pct <= 25) {
         return -5630699;
      } else if (pct <= 50) {
         return -3381728;
      } else {
         return pct <= 75 ? -2609104 : -1556416;
      }
   }
}
