#!/usr/bin/env python3
"""把 Yarn 风格类名批量转成 26.3 Mojang 官方名。
规则: 简单名替换只在文件确实 import 了对应 Yarn 类时执行, 避免误伤普通单词。"""
import re, pathlib, sys

# Yarn 全类名 -> (Mojang 全类名, 简单名替换或 None)
IMPORT_MAP = {
    "net.minecraft.client.MinecraftClient": ("net.minecraft.client.Minecraft", "MinecraftClient: Minecraft"),
    "net.minecraft.client.font.TextRenderer": ("net.minecraft.client.gui.Font", "TextRenderer: Font"),
    "net.minecraft.client.gui.DrawContext": ("net.minecraft.client.gui.GuiGraphicsExtractor", "DrawContext: GuiGraphicsExtractor"),
    "net.minecraft.client.gui.hud.BossBarHud": ("net.minecraft.client.gui.components.BossHealthOverlay", "BossBarHud: BossHealthOverlay"),
    "net.minecraft.client.gui.hud.ClientBossBar": ("net.minecraft.world.BossEvent", "ClientBossBar: BossEvent"),
    "net.minecraft.client.gui.hud.InGameHud": ("net.minecraft.client.gui.Hud", "InGameHud: Hud"),
    "net.minecraft.client.gui.screen.ConfirmScreen": ("net.minecraft.client.gui.screens.ConfirmScreen", None),
    "net.minecraft.client.gui.screen.Screen": ("net.minecraft.client.gui.screens.Screen", None),
    "net.minecraft.client.gui.screen.ingame.InventoryScreen": ("net.minecraft.client.gui.screens.inventory.InventoryScreen", None),
    "net.minecraft.client.gui.widget.ButtonWidget": ("net.minecraft.client.gui.components.Button", "ButtonWidget: Button"),
    "net.minecraft.client.network.ClientPlayerEntity": ("net.minecraft.client.player.LocalPlayer", "ClientPlayerEntity: LocalPlayer"),
    "net.minecraft.client.network.PlayerListEntry": ("net.minecraft.client.multiplayer.PlayerInfo", "PlayerListEntry: PlayerInfo"),
    "net.minecraft.client.option.KeyBinding": ("net.minecraft.client.KeyMapping", "KeyBinding: KeyMapping"),
    "net.minecraft.client.render.RenderLayer": ("net.minecraft.client.renderer.RenderType", "RenderLayer: RenderType"),
    "net.minecraft.client.render.RenderLayers": ("net.minecraft.client.renderer.RenderType", "RenderLayers: RenderType"),
    "net.minecraft.client.render.RenderTickCounter": ("net.minecraft.client.DeltaTracker", "RenderTickCounter: DeltaTracker"),
    "net.minecraft.client.render.VertexConsumer": ("com.mojang.blaze3d.vertex.VertexConsumer", None),
    "net.minecraft.client.render.VertexConsumerProvider": ("net.minecraft.client.renderer.MultiBufferSource", "VertexConsumerProvider: MultiBufferSource"),
    "net.minecraft.client.render.WorldRenderer": ("net.minecraft.client.renderer.LevelRenderer", "WorldRenderer: LevelRenderer"),
    "net.minecraft.client.render.entity.EntityRenderDispatcher": ("net.minecraft.client.renderer.entity.EntityRenderDispatcher", None),
    "net.minecraft.client.render.entity.EntityRenderer": ("net.minecraft.client.renderer.entity.EntityRenderer", None),
    "net.minecraft.client.render.entity.PlayerEntityRenderer": ("net.minecraft.client.renderer.entity.PlayerRenderer", "PlayerEntityRenderer: PlayerRenderer"),
    "net.minecraft.client.render.entity.state.EntityRenderState": ("net.minecraft.client.renderer.entity.state.EntityRenderState", None),
    "net.minecraft.client.render.entity.state.PlayerEntityRenderState": ("net.minecraft.client.renderer.state.level.PlayerRenderState", "PlayerEntityRenderState: PlayerRenderState"),
    "net.minecraft.client.util.math.MatrixStack": ("com.mojang.blaze3d.vertex.PoseStack", "MatrixStack: PoseStack"),
    "net.minecraft.client.world.ClientWorld": ("net.minecraft.client.multiplayer.ClientLevel", "ClientWorld: ClientLevel"),
    "net.minecraft.component.DataComponentTypes": ("net.minecraft.core.component.DataComponents", "DataComponentTypes: DataComponents"),
    "net.minecraft.component.type.NbtComponent": ("net.minecraft.world.item.component.CustomData", "NbtComponent: CustomData"),
    "net.minecraft.entity.Entity": ("net.minecraft.world.entity.Entity", None),
    "net.minecraft.entity.LivingEntity": ("net.minecraft.world.entity.LivingEntity", None),
    "net.minecraft.entity.decoration.ArmorStandEntity": ("net.minecraft.world.entity.decoration.ArmorStand", "ArmorStandEntity: ArmorStand"),
    "net.minecraft.entity.decoration.DisplayEntity": ("net.minecraft.world.entity.decoration.Display", "DisplayEntity: Display"),
    "net.minecraft.entity.player.PlayerEntity": ("net.minecraft.world.entity.player.Player", "PlayerEntity: Player"),
    "net.minecraft.entity.player.PlayerInventory": ("net.minecraft.world.entity.player.Inventory", "PlayerInventory: Inventory"),
    "net.minecraft.item.Item": ("net.minecraft.world.item.Item", None),
    "net.minecraft.item.ItemStack": ("net.minecraft.world.item.ItemStack", None),
    "net.minecraft.item.Items": ("net.minecraft.world.item.Items", None),
    "net.minecraft.nbt.NbtCompound": ("net.minecraft.nbt.CompoundTag", "NbtCompound: CompoundTag"),
    "net.minecraft.nbt.NbtElement": ("net.minecraft.nbt.Tag", "NbtElement: Tag"),
    "net.minecraft.nbt.NbtIo": ("net.minecraft.nbt.NbtIo", None),
    "net.minecraft.nbt.NbtList": ("net.minecraft.nbt.ListTag", "NbtList: ListTag"),
    "net.minecraft.network.PacketByteBuf": ("net.minecraft.network.FriendlyByteBuf", "PacketByteBuf: FriendlyByteBuf"),
    "net.minecraft.network.RegistryByteBuf": ("net.minecraft.network.RegistryFriendlyByteBuf", "RegistryByteBuf: RegistryFriendlyByteBuf"),
    "net.minecraft.network.codec.PacketCodec": ("net.minecraft.network.codec.StreamCodec", "PacketCodec: StreamCodec"),
    "net.minecraft.network.packet.CustomPayload": ("net.minecraft.network.protocol.common.custom.CustomPacketPayload", "CustomPayload: CustomPacketPayload"),
    "net.minecraft.registry.Registries": ("net.minecraft.core.registries.BuiltInRegistries", None),
    "net.minecraft.registry.RegistryKey": ("net.minecraft.resources.ResourceKey", "RegistryKey: ResourceKey"),
    "net.minecraft.scoreboard.ReadableScoreboardScore": ("net.minecraft.world.scores.PlayerScoreEntry", "ReadableScoreboardScore: PlayerScoreEntry"),
    "net.minecraft.scoreboard.ScoreHolder": ("net.minecraft.world.scores.ScoreAccess", "ScoreHolder: ScoreAccess"),
    "net.minecraft.scoreboard.Scoreboard": ("net.minecraft.world.scores.Scoreboard", None),
    "net.minecraft.scoreboard.ScoreboardCriterion": ("net.minecraft.world.scores.Criteria", "ScoreboardCriterion: Criteria"),
    "net.minecraft.scoreboard.ScoreboardDisplaySlot": ("net.minecraft.world.scores.DisplaySlot", "ScoreboardDisplaySlot: DisplaySlot"),
    "net.minecraft.scoreboard.ScoreboardObjective": ("net.minecraft.world.scores.Objective", "ScoreboardObjective: Objective"),
    "net.minecraft.scoreboard.ScoreboardScore": ("net.minecraft.world.scores.Score", "ScoreboardScore: Score"),
    "net.minecraft.scoreboard.ServerScoreboard": ("net.minecraft.server.ServerScoreboard", None),
    "net.minecraft.server.command.CommandManager": ("net.minecraft.commands.Commands", "CommandManager: Commands"),
    "net.minecraft.server.command.ServerCommandSource": ("net.minecraft.commands.CommandSourceStack", "ServerCommandSource: CommandSourceStack"),
    "net.minecraft.server.network.ServerPlayerEntity": ("net.minecraft.server.level.ServerPlayer", "ServerPlayerEntity: ServerPlayer"),
    "net.minecraft.server.world.ServerWorld": ("net.minecraft.server.level.ServerLevel", "ServerWorld: ServerLevel"),
    "net.minecraft.stat.Stat": ("net.minecraft.stats.Stat", None),
    "net.minecraft.stat.Stats": ("net.minecraft.stats.Statistics", "Stats: Statistics"),
    "net.minecraft.text.Text": ("net.minecraft.network.chat.Component", "Text: Component"),
    "net.minecraft.text.MutableText": ("net.minecraft.network.chat.MutableComponent", "MutableText: MutableComponent"),
    "net.minecraft.text.OrderedText": ("net.minecraft.util.FormattedCharSequence", "OrderedText: FormattedCharSequence"),
    "net.minecraft.text.Style": ("net.minecraft.network.chat.Style", None),
    "net.minecraft.text.TextColor": ("net.minecraft.network.chat.TextColor", None),
    "net.minecraft.text.TextContent": ("net.minecraft.network.chat.contents.Content", "TextContent: Content"),
    "net.minecraft.text.TranslatableTextContent": ("net.minecraft.network.chat.contents.TranslatableContents", "TranslatableTextContent: TranslatableContents"),
    "net.minecraft.text.TextCodecs": ("net.minecraft.network.chat.ComponentSerialization", "TextCodecs: ComponentSerialization"),
    "net.minecraft.util.Formatting": ("net.minecraft.ChatFormatting", "Formatting: ChatFormatting"),
    "net.minecraft.util.Identifier": ("net.minecraft.resources.Identifier", None),
    "net.minecraft.util.Util": ("net.minecraft.Util", None),
    "net.minecraft.util.WorldSavePath": ("net.minecraft.world.level.storage.LevelResource", "WorldSavePath: LevelResource"),
    "net.minecraft.util.math.Box": ("net.minecraft.world.phys.AABB", "Box: AABB"),
    "net.minecraft.util.math.MathHelper": ("net.minecraft.util.Mth", "MathHelper: Mth"),
    "net.minecraft.util.math.RotationAxis": ("net.minecraft.world.phys.RotationAxis", None),
    "net.minecraft.util.math.Vec3d": ("net.minecraft.world.phys.Vec3", "Vec3d: Vec3"),
    "net.minecraft.world.World": ("net.minecraft.world.level.Level", "World: Level"),
    "net.minecraft.command.CommandRegistryAccess": ("net.minecraft.commands.CommandDispatcher", "CommandRegistryAccess: CommandDispatcher"),
    "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper": ("net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper", "KeyBindingHelper: KeyMappingHelper"),
}

FQN_USAGE = [  # 代码里直接写 FQN 的情况
    ("net.minecraft.client.MinecraftClient", "net.minecraft.client.Minecraft"),
    ("net.minecraft.client.util.math.MatrixStack", "com.mojang.blaze3d.vertex.PoseStack"),
    ("net.minecraft.client.render.VertexConsumer", "com.mojang.blaze3d.vertex.VertexConsumer"),
]

def main(root):
    files = list(pathlib.Path(root).rglob("*.java"))
    changed = 0
    for f in files:
        src = f.read_text(encoding="utf-8")
        orig = src
        imports = set(re.findall(r'^import (?:static )?([\w.$]+);', src, re.M))
        for imp, (new_imp, rename) in IMPORT_MAP.items():
            if imp in imports and new_imp != imp:
                src = re.sub(rf'^import (static )?{re.escape(imp)};', rf'import \1{new_imp};', src, flags=re.M)
                src = re.sub(rf'^import {re.escape(imp)}\.[\w$]+;', f'import {new_imp}.PLACEHOLDER;', src, flags=re.M)  # 内部类占位, 人工处理
                if rename:
                    old, new = rename.split(": ")
                    src = re.sub(rf'(?<![\w.$]){re.escape(old)}(?![\w])', new, src)
        for old_fqn, new_fqn in FQN_USAGE:
            src = src.replace(old_fqn, new_fqn)
        if src != orig:
            f.write_text(src, encoding="utf-8")
            changed += 1
            print("updated:", f)
    print(f"{changed}/{len(files)} files changed")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")
