package com.ctt.healthdisplay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public class ServerConfig {
   public static final int CURRENT_CONFIG_VERSION = 6;
   public static ServerConfig INSTANCE = new ServerConfig();
   public int configVersion = 0;
   public boolean broadcastDamageInChat = false;
   public boolean broadcastKillsInChat = false;
   public boolean broadcastTakenInChat = false;
   public boolean broadcastStageReportInChat = false;
   public int broadcastTakenThreshold = 0;
   public int broadcastDamageThreshold = 0;
   public boolean useRedHeartsTally = true;
   public boolean serverPushStatsEnabled = true;
   public int serverPushStatsIntervalTicks = 20;
   public boolean serverPushTeamHeartsEnabled = true;
   public int serverPushTeamHeartsIntervalTicks = 4;
   public boolean filterEnabled = true;
   public int lowDamageFloor = 3;
   public int defenceExclusionThreshold = 50;
   public String[] lowNoiseWeaponWhitelist = new String[]{"nutStickLaser", "ak47"};
   public double lowNoiseWhitelistRadius = 16.0;
   public boolean filterPhysicalCeil = true;
   public int physicalCeilMultiplier = 3;
   public boolean filterStateBoundary = false;
   public int sessionBoundaryGuardTicks = 5;
   public String[] suspectVictimTags = new String[]{"Coffin", "Prop", "NPC", "TestDummy", "Debug"};
   public boolean filterDuplicateReplay = false;
   public boolean filterInitHpJumps = true;
   public int[] initHpJumpValues = new int[]{1000, 9000, 10000, 100000};
   public String[] blockedStages = new String[]{"dungeon:47"};
   public boolean collectMagumTrials = true;
   public boolean filterSuspectVictims = true;
   public String[] suspectVictims = new String[]{"幽匿骷髅", "幽匿僵尸"};
   public int suspectVictimDamageThreshold = 800;
   public boolean useDamageBuffer = true;
   public int bufferDelayTicks = 2;
   public int bufferMaxSize = 4096;
   public boolean p95OutlierEnabled = true;
   public int p95WindowSize = 100;
   public int p95MinSamples = 20;
   public int p95OutlierMultiplier = 3;
   public double dpsActiveMultiplier = 1.5;
   public int outlierAbsoluteFloor = 800;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path SERVER_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ctt-health-display-server.json");
   private static final Path LEGACY_CLIENT_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ctt-health-display.json");

   public void save() {
      try (Writer writer = Files.newBufferedWriter(SERVER_CONFIG_PATH)) {
         GSON.toJson(this, writer);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void load() {
      if (Files.exists(SERVER_CONFIG_PATH)) {
         try (Reader reader = Files.newBufferedReader(SERVER_CONFIG_PATH)) {
            ServerConfig loaded = (ServerConfig)GSON.fromJson(reader, ServerConfig.class);
            if (loaded != null) {
               boolean migrated = loaded.migrate();
               loaded.normalize();
               INSTANCE = loaded;
               if (migrated) {
                  INSTANCE.save();
               }
            }
         } catch (Exception e) {
            INSTANCE = new ServerConfig();
         }
      } else {
         ServerConfig fresh = new ServerConfig();
         tryMigrateFromLegacy(fresh);
         fresh.configVersion = 6;
         fresh.normalize();
         INSTANCE = fresh;
         INSTANCE.save();
      }
   }

   private boolean migrate() {
      boolean changed = false;
      if (this.configVersion < 1) {
         this.filterStateBoundary = false;
         changed = true;
      }

      if (this.configVersion < 2) {
         this.filterDuplicateReplay = false;
         changed = true;
      }

      if (this.configVersion < 3 && this.lowDamageFloor == 5) {
         this.lowDamageFloor = 3;
         changed = true;
      }

      if (this.configVersion < 4 && this.broadcastTakenThreshold == 1) {
         this.broadcastTakenThreshold = 0;
         changed = true;
      }

      if (this.configVersion < 5) {
         changed = true;
      }

      if (this.configVersion < 6) {
         this.serverPushStatsEnabled = true;
         if (this.serverPushStatsIntervalTicks <= 0) {
            this.serverPushStatsIntervalTicks = 20;
         }

         this.serverPushTeamHeartsEnabled = true;
         if (this.serverPushTeamHeartsIntervalTicks <= 0) {
            this.serverPushTeamHeartsIntervalTicks = 4;
         }

         changed = true;
      }

      if (this.configVersion < 6) {
         this.configVersion = 6;
         changed = true;
      }

      return changed;
   }

   private static void tryMigrateFromLegacy(ServerConfig dst) {
      if (Files.exists(LEGACY_CLIENT_CONFIG_PATH)) {
         try (Reader reader = Files.newBufferedReader(LEGACY_CLIENT_CONFIG_PATH)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root != null && root.isJsonObject()) {
               JsonObject obj = root.getAsJsonObject();
               if (obj.has("broadcastDamageInChat")) {
                  dst.broadcastDamageInChat = obj.get("broadcastDamageInChat").getAsBoolean();
               }

               if (obj.has("broadcastKillsInChat")) {
                  dst.broadcastKillsInChat = obj.get("broadcastKillsInChat").getAsBoolean();
               }

               if (obj.has("broadcastTakenInChat")) {
                  dst.broadcastTakenInChat = obj.get("broadcastTakenInChat").getAsBoolean();
               }

               if (obj.has("broadcastTakenThreshold")) {
                  dst.broadcastTakenThreshold = obj.get("broadcastTakenThreshold").getAsInt();
               }

               if (obj.has("broadcastDamageThreshold")) {
                  dst.broadcastDamageThreshold = obj.get("broadcastDamageThreshold").getAsInt();
               }

               if (obj.has("useRedHeartsTally")) {
                  dst.useRedHeartsTally = obj.get("useRedHeartsTally").getAsBoolean();
               }

               if (obj.has("filterEnabled")) {
                  dst.filterEnabled = obj.get("filterEnabled").getAsBoolean();
               }

               if (obj.has("lowDamageFloor")) {
                  dst.lowDamageFloor = obj.get("lowDamageFloor").getAsInt();
               }

               if (obj.has("defenceExclusionThreshold")) {
                  dst.defenceExclusionThreshold = obj.get("defenceExclusionThreshold").getAsInt();
               }

               if (obj.has("filterPhysicalCeil")) {
                  dst.filterPhysicalCeil = obj.get("filterPhysicalCeil").getAsBoolean();
               }

               if (obj.has("physicalCeilMultiplier")) {
                  dst.physicalCeilMultiplier = obj.get("physicalCeilMultiplier").getAsInt();
               }

               if (obj.has("filterStateBoundary")) {
                  dst.filterStateBoundary = obj.get("filterStateBoundary").getAsBoolean();
               }

               if (obj.has("sessionBoundaryGuardTicks")) {
                  dst.sessionBoundaryGuardTicks = obj.get("sessionBoundaryGuardTicks").getAsInt();
               }

               if (obj.has("suspectVictimTags") && obj.get("suspectVictimTags").isJsonArray()) {
                  List<String> tmp = new ArrayList<>();

                  for (JsonElement e : obj.get("suspectVictimTags").getAsJsonArray()) {
                     try {
                        tmp.add(e.getAsString());
                     } catch (Exception var11) {
                     }
                  }

                  if (!tmp.isEmpty()) {
                     dst.suspectVictimTags = tmp.toArray(new String[0]);
                  }
               }

               if (obj.has("filterDuplicateReplay")) {
                  dst.filterDuplicateReplay = obj.get("filterDuplicateReplay").getAsBoolean();
               }

               if (obj.has("filterInitHpJumps")) {
                  dst.filterInitHpJumps = obj.get("filterInitHpJumps").getAsBoolean();
               }

               if (obj.has("initHpJumpValues") && obj.get("initHpJumpValues").isJsonArray()) {
                  List<Integer> tmp = new ArrayList<>();

                  for (JsonElement e : obj.get("initHpJumpValues").getAsJsonArray()) {
                     try {
                        tmp.add(e.getAsInt());
                     } catch (Exception var10) {
                     }
                  }

                  if (!tmp.isEmpty()) {
                     dst.initHpJumpValues = tmp.stream().mapToInt(Integer::intValue).toArray();
                  }
               }

               if (obj.has("filterSuspectVictims")) {
                  dst.filterSuspectVictims = obj.get("filterSuspectVictims").getAsBoolean();
               }

               if (obj.has("suspectVictims") && obj.get("suspectVictims").isJsonArray()) {
                  List<String> tmp = new ArrayList<>();

                  for (JsonElement e : obj.get("suspectVictims").getAsJsonArray()) {
                     try {
                        tmp.add(e.getAsString());
                     } catch (Exception var9) {
                     }
                  }

                  if (!tmp.isEmpty()) {
                     dst.suspectVictims = tmp.toArray(new String[0]);
                  }
               }

               if (obj.has("suspectVictimDamageThreshold")) {
                  dst.suspectVictimDamageThreshold = obj.get("suspectVictimDamageThreshold").getAsInt();
               }

               if (obj.has("useDamageBuffer")) {
                  dst.useDamageBuffer = obj.get("useDamageBuffer").getAsBoolean();
               }

               if (obj.has("bufferDelayTicks")) {
                  dst.bufferDelayTicks = obj.get("bufferDelayTicks").getAsInt();
               }

               if (obj.has("bufferMaxSize")) {
                  dst.bufferMaxSize = obj.get("bufferMaxSize").getAsInt();
               }

               if (obj.has("p95OutlierEnabled")) {
                  dst.p95OutlierEnabled = obj.get("p95OutlierEnabled").getAsBoolean();
               }

               if (obj.has("p95WindowSize")) {
                  dst.p95WindowSize = obj.get("p95WindowSize").getAsInt();
               }

               if (obj.has("p95MinSamples")) {
                  dst.p95MinSamples = obj.get("p95MinSamples").getAsInt();
               }

               if (obj.has("p95OutlierMultiplier")) {
                  dst.p95OutlierMultiplier = obj.get("p95OutlierMultiplier").getAsInt();
               }

               if (obj.has("dpsActiveMultiplier")) {
                  dst.dpsActiveMultiplier = obj.get("dpsActiveMultiplier").getAsDouble();
               }

               if (obj.has("outlierAbsoluteFloor")) {
                  dst.outlierAbsoluteFloor = obj.get("outlierAbsoluteFloor").getAsInt();
               }
            }
         } catch (Exception var13) {
         }
      }
   }

   private void normalize() {
      if (this.initHpJumpValues == null) {
         this.initHpJumpValues = new int[0];
      }

      if (this.suspectVictims == null) {
         this.suspectVictims = new String[0];
      }

      if (this.suspectVictimTags == null) {
         this.suspectVictimTags = new String[0];
      }

      if (this.blockedStages == null) {
         this.blockedStages = new String[0];
      }

      if (this.lowNoiseWeaponWhitelist == null) {
         this.lowNoiseWeaponWhitelist = new String[0];
      }

      if (this.broadcastTakenThreshold < 0) {
         this.broadcastTakenThreshold = 0;
      }

      if (this.broadcastDamageThreshold < 0) {
         this.broadcastDamageThreshold = 0;
      }

      if (this.suspectVictimDamageThreshold < 0) {
         this.suspectVictimDamageThreshold = 0;
      }

      if (this.lowDamageFloor < 0) {
         this.lowDamageFloor = 0;
      }

      if (this.defenceExclusionThreshold < 0) {
         this.defenceExclusionThreshold = 0;
      }

      if (this.physicalCeilMultiplier < 0) {
         this.physicalCeilMultiplier = 0;
      }

      if (this.sessionBoundaryGuardTicks < 0) {
         this.sessionBoundaryGuardTicks = 0;
      }

      if (this.lowNoiseWhitelistRadius < 0.0) {
         this.lowNoiseWhitelistRadius = 0.0;
      }

      if (this.bufferDelayTicks < 0) {
         this.bufferDelayTicks = 0;
      }

      if (this.bufferMaxSize < 16) {
         this.bufferMaxSize = 4096;
      }

      if (this.p95WindowSize < 1) {
         this.p95WindowSize = 100;
      }

      if (this.p95MinSamples < 1) {
         this.p95MinSamples = 20;
      }

      if (this.p95MinSamples > this.p95WindowSize) {
         this.p95MinSamples = this.p95WindowSize;
      }

      if (this.p95OutlierMultiplier < 1) {
         this.p95OutlierMultiplier = 3;
      }

      if (this.dpsActiveMultiplier < 0.0) {
         this.dpsActiveMultiplier = 0.0;
      }

      if (this.outlierAbsoluteFloor < 0) {
         this.outlierAbsoluteFloor = 0;
      }

      if (this.serverPushStatsIntervalTicks <= 0) {
         this.serverPushStatsIntervalTicks = 20;
      }

      if (this.serverPushTeamHeartsIntervalTicks <= 0) {
         this.serverPushTeamHeartsIntervalTicks = 4;
      }
   }
}
