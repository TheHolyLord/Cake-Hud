package com.ctt.healthdisplay.config;

import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ServerConfigScreen extends Screen {
   private final Screen parent;
   private boolean broadcastDamage;
   private boolean broadcastKills;
   private boolean broadcastTaken;
   private boolean broadcastStageReport;
   private int broadcastTakenThreshold;
   private boolean useRedHearts;
   private boolean filterInitHpJumps;
   private boolean filterSuspectVictims;
   private int suspectThreshold;
   private static final int[] TAKEN_THRESHOLDS = new int[]{1, 5, 10, 20, 50, 100};
   private static final int[] SUSPECT_THRESHOLDS = new int[]{100, 200, 400, 800, 1500, 3000};
   private static final int OPTION_BTN_W = 280;
   private static final int OPTION_BTN_H = 20;
   private static final int OPTION_SPACING = 24;
   private int optionScrollOffset = 0;
   private int optionContentHeight = 0;
   private boolean firstInit = true;

   public ServerConfigScreen(Screen parent) {
      super(Component.translatable("ctt-health-display.config.server.title"));
      this.parent = parent;
   }

   protected void init() {
      if (this.firstInit) {
         ServerConfig cfg = ServerConfig.INSTANCE;
         this.broadcastDamage = cfg.broadcastDamageInChat;
         this.broadcastKills = cfg.broadcastKillsInChat;
         this.broadcastTaken = cfg.broadcastTakenInChat;
         this.broadcastStageReport = cfg.broadcastStageReportInChat;
         this.broadcastTakenThreshold = cfg.broadcastTakenThreshold;
         this.useRedHearts = cfg.useRedHeartsTally;
         this.filterInitHpJumps = cfg.filterInitHpJumps;
         this.filterSuspectVictims = cfg.filterSuspectVictims;
         this.suspectThreshold = cfg.suspectVictimDamageThreshold;
         this.firstInit = false;
      }

      int btnW = Math.min(280, this.width - 40);
      int x = (this.width - btnW) / 2;
      int y = 50 - this.optionScrollOffset;
      this.addRenderableWidget(Button.builder(this.broadcastDamageBtnText(), btn -> {
         this.broadcastDamage = !this.broadcastDamage;
         btn.setMessage(this.broadcastDamageBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.addRenderableWidget(Button.builder(this.broadcastKillsBtnText(), btn -> {
         this.broadcastKills = !this.broadcastKills;
         btn.setMessage(this.broadcastKillsBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.addRenderableWidget(Button.builder(this.broadcastTakenBtnText(), btn -> {
         this.broadcastTaken = !this.broadcastTaken;
         btn.setMessage(this.broadcastTakenBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.addRenderableWidget(Button.builder(this.broadcastStageReportBtnText(), btn -> {
         this.broadcastStageReport = !this.broadcastStageReport;
         btn.setMessage(this.broadcastStageReportBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.addRenderableWidget(Button.builder(this.broadcastTakenThresholdBtnText(), btn -> {
         this.broadcastTakenThreshold = cycleNext(TAKEN_THRESHOLDS, this.broadcastTakenThreshold);
         btn.setMessage(this.broadcastTakenThresholdBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 30;
      this.addRenderableWidget(Button.builder(this.useRedHeartsBtnText(), btn -> {
         this.useRedHearts = !this.useRedHearts;
         btn.setMessage(this.useRedHeartsBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 30;
      this.addRenderableWidget(Button.builder(this.filterInitHpJumpsBtnText(), btn -> {
         this.filterInitHpJumps = !this.filterInitHpJumps;
         btn.setMessage(this.filterInitHpJumpsBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.addRenderableWidget(Button.builder(this.filterSuspectVictimsBtnText(), btn -> {
         this.filterSuspectVictims = !this.filterSuspectVictims;
         btn.setMessage(this.filterSuspectVictimsBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 24;
      this.addRenderableWidget(Button.builder(this.suspectThresholdBtnText(), btn -> {
         this.suspectThreshold = cycleNext(SUSPECT_THRESHOLDS, this.suspectThreshold);
         btn.setMessage(this.suspectThresholdBtnText());
      }).bounds(x, y, btnW, 20).build());
      y += 20;
      this.optionContentHeight = y - (50 - this.optionScrollOffset);
      int bottomY = this.height - 28;
      int cx = this.width / 2;
      this.addRenderableWidget(
         Button.builder(Component.translatable("ctt-health-display.config.server.btn.cancel"), btn -> this.minecraft.gui.setScreen(this.parent))
            .bounds(cx - 102, bottomY, 100, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("ctt-health-display.config.btn.done"), btn -> this.saveAndClose()).bounds(cx + 2, bottomY, 100, 20).build()
      );
   }

   private static int cycleNext(int[] arr, int cur) {
      int idx = 0;

      for (int i = 0; i < arr.length; i++) {
         if (arr[i] == cur) {
            idx = i;
            break;
         }
      }

      return arr[(idx + 1) % arr.length];
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      super.extractRenderState(context, mouseX, mouseY, delta);
      context.centeredText(this.font, this.title, this.width / 2, 8, 16777215);
      context.centeredText(this.font, Component.translatable("ctt-health-display.config.server.subtitle").getString(), this.width / 2, 24, -5592406);
      int infoY = this.height - 60;
      ServerConfig cfg = ServerConfig.INSTANCE;
      String arrLine1 = Component.translatable("ctt-health-display.config.server.info.init_hp_values", new Object[]{Arrays.toString(cfg.initHpJumpValues)})
         .getString();
      String arrLine2 = Component.translatable("ctt-health-display.config.server.info.suspect_victims", new Object[]{String.join(", ", cfg.suspectVictims)})
         .getString();
      context.centeredText(this.font, Component.literal(arrLine1), this.width / 2, infoY, -7829368);
      context.centeredText(this.font, Component.literal(arrLine2), this.width / 2, infoY + 10, -7829368);
   }

   private void saveAndClose() {
      ServerConfig cfg = ServerConfig.INSTANCE;
      cfg.broadcastDamageInChat = this.broadcastDamage;
      cfg.broadcastKillsInChat = this.broadcastKills;
      cfg.broadcastTakenInChat = this.broadcastTaken;
      cfg.broadcastStageReportInChat = this.broadcastStageReport;
      cfg.broadcastTakenThreshold = this.broadcastTakenThreshold;
      cfg.useRedHeartsTally = this.useRedHearts;
      cfg.filterInitHpJumps = this.filterInitHpJumps;
      cfg.filterSuspectVictims = this.filterSuspectVictims;
      cfg.suspectVictimDamageThreshold = this.suspectThreshold;
      cfg.save();
      this.minecraft.gui.setScreen(this.parent);
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int visibleH = this.height - 36 - 70;
      int maxScroll = Math.max(0, this.optionContentHeight - visibleH);
      if (maxScroll > 0) {
         this.optionScrollOffset = clamp(this.optionScrollOffset - (int)(verticalAmount * 10.0), 0, maxScroll);
         this.rebuildWidgets();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   private static int clamp(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }

   private Component broadcastDamageBtnText() {
      return Component.translatable("ctt-health-display.config.option.broadcast_damage", new Object[]{this.onOff(this.broadcastDamage)});
   }

   private Component broadcastKillsBtnText() {
      return Component.translatable("ctt-health-display.config.option.broadcast_kills", new Object[]{this.onOff(this.broadcastKills)});
   }

   private Component broadcastTakenBtnText() {
      return Component.translatable("ctt-health-display.config.option.broadcast_taken", new Object[]{this.onOff(this.broadcastTaken)});
   }

   private Component broadcastStageReportBtnText() {
      return Component.translatable("ctt-health-display.config.option.broadcast_stage_report", new Object[]{this.onOff(this.broadcastStageReport)});
   }

   private Component broadcastTakenThresholdBtnText() {
      return Component.translatable("ctt-health-display.config.server.option.broadcast_taken_threshold", new Object[]{this.broadcastTakenThreshold});
   }

   private Component useRedHeartsBtnText() {
      return Component.translatable("ctt-health-display.config.server.option.use_red_hearts_tally", new Object[]{this.onOff(this.useRedHearts)});
   }

   private Component filterInitHpJumpsBtnText() {
      return Component.translatable("ctt-health-display.config.server.option.filter_init_hp_jumps", new Object[]{this.onOff(this.filterInitHpJumps)});
   }

   private Component filterSuspectVictimsBtnText() {
      return Component.translatable("ctt-health-display.config.server.option.filter_suspect_victims", new Object[]{this.onOff(this.filterSuspectVictims)});
   }

   private Component suspectThresholdBtnText() {
      return Component.translatable("ctt-health-display.config.server.option.suspect_victim_threshold", new Object[]{this.suspectThreshold});
   }

   private Component onOff(boolean v) {
      return Component.translatable(v ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off");
   }
}
