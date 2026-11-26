package com.ctt.healthdisplay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class ModConfig {
   public static ModConfig INSTANCE = new ModConfig();
   public static final int MOB_HP_MODE_ALL = 0;
   public static final int MOB_HP_MODE_NEAREST = 1;
   public static final int MOB_HP_MODE_OFF = 2;
   public float teammateX = 0.01F;
   public float teammateY = 0.33F;
   public boolean horizontalLayout = false;
   public float statsX = 0.005F;
   public float statsY = 0.005F;
   public boolean autoRefreshStats = true;
   public int autoRefreshIntervalSeconds = 30;
   public boolean showTeammateHeadHP = true;
   public boolean showMobHeadHP = true;
   public int mobHeadHPMode = 0;
   public int mobBarHalfWidth = 60;
   public int teammateBarHalfWidth = 40;
   public static final int MAX_STATS_COLUMNS = 3;
   public int statsColumns = 2;
   public boolean showStatsOutsideInventory = true;
   public boolean showStatsInInventory = false;
   public boolean loreTooltipAtCursor = false;
   public boolean hidePersonalBar = true;
   public boolean hideTeamBar = true;
   public boolean hideMobBars = false;
   public boolean showShieldCooldown = true;
   public boolean showTeammateLayeredHearts = true;
   public boolean damagePanelHudVisible = false;
   public float damagePanelX = 0.005F;
   public float damagePanelY = 0.2F;
   public boolean damagePanelDetailed = true;
   public int damagePanelStageScope = 0;
   public static final int STAGE_SCOPE_SESSION = 0;
   public static final int STAGE_SCOPE_CURRENT = 1;
   public int embeddedHudMode = 1;
   public static final int EMBED_OFF = 0;
   public static final int EMBED_ONLY_STAGE = 1;
   public static final int EMBED_ONLY_SESSION = 2;
   public static final int EMBED_BOTH = 3;
   public boolean clientDamageDebugChat = false;
   public boolean clientDamageHudHeader = true;
   public boolean clientKillDebugChat = false;
   public boolean hideHudOnBerserk = true;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ctt-health-display.json");

   public void save() {
      try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
         GSON.toJson(this, writer);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void load() {
      if (Files.exists(CONFIG_PATH)) {
         try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject settings = (JsonObject)GSON.fromJson(reader, JsonObject.class);
            if (settings != null) {
               ModConfig loaded = (ModConfig)GSON.fromJson(settings, ModConfig.class);
               loaded.migrate(settings);
               INSTANCE = loaded;
               return;
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      INSTANCE = new ModConfig();
   }

   public static boolean reload() {
      boolean existed = Files.exists(CONFIG_PATH);
      load();
      return existed;
   }

   private void migrate(JsonObject settings) {
      this.statsColumns = Math.clamp(this.statsColumns, 1, 3);
      JsonElement oldStatsVisibility = settings.get("statsVisibility");
      if (oldStatsVisibility != null && oldStatsVisibility.isJsonPrimitive() && oldStatsVisibility.getAsJsonPrimitive().isNumber()) {
         int oldMode = oldStatsVisibility.getAsInt();
         if (!settings.has("showStatsOutsideInventory")) {
            this.showStatsOutsideInventory = oldMode == 0;
         }

         if (!settings.has("showStatsInInventory")) {
            this.showStatsInInventory = oldMode == 1;
         }
      }

      if (!this.showMobHeadHP && this.mobHeadHPMode == 0) {
         this.mobHeadHPMode = 2;
      }

      if (this.mobHeadHPMode < 0 || this.mobHeadHPMode > 2) {
         this.mobHeadHPMode = 0;
      }

      if (this.embeddedHudMode < 0 || this.embeddedHudMode > 3) {
         this.embeddedHudMode = 1;
      }
   }

   public boolean isMobHeadHPEnabled() {
      return this.mobHeadHPMode != 2;
   }

   public boolean isMobHeadHPNearestOnly() {
      return this.mobHeadHPMode == 1;
   }

    // ── 本 fork 移植：不透明度 / 文字阴影 / 盾牌贴图 ──
    public double mobBarOpacity = 0.65;
    public double mobTextOpacity = 1.00;
    public double teamBarOpacity = 0.65;
    public double teamTextOpacity = 1.00;
    public boolean textShadow = false;
    public boolean shieldTextureStyle = false;
    public double shieldScale = 0.7;
    public double shieldOpacity = 1.0;

    // ── 本 fork 移植：盾牌位置 / 头顶血条风格 / 属性面板过滤 ──
    /** 盾牌指示器位置（GUI 像素）；-1 = 上游默认（准星下方居中）。 */
    public int shieldX = -1;
    public int shieldY = -1;
    /** 是否允许在设置界面拖动盾牌位置。 */
    public boolean shieldDraggable = true;
    /** 头顶血条风格：0 = 上游（矩形条），1 = fork（心型行文本）。 */
    public int hudStyle = 0;
    /** 属性面板里隐藏的行（按行标签精确匹配）。 */
    public java.util.List<String> hiddenStats = new java.util.ArrayList<>();

    /** 属性面板：该行是否被用户隐藏（按 "标签: 值" 的标签精确匹配）。 */
    public boolean isStatsLineHidden(String plain) {
        if (hiddenStats == null || hiddenStats.isEmpty() || plain == null) return false;
        String t = plain.trim();
        for (String h : hiddenStats) {
            if (h == null || h.isEmpty()) continue;
            String key = h.trim();
            if (t.equals(key) || t.startsWith(key + ":") || t.startsWith(key + "：")) return true;
        }
        return false;
    }
}
