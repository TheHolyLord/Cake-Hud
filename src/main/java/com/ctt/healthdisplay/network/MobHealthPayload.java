package com.ctt.healthdisplay.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * v8.3.0 · M7 · 服务端 → 客户端 "怪物血量表" 推送包。
 *
 * <h2>问题背景</h2>
 * <p>Cake Team Towers 中 vanilla bossbar 同一时间只会锁定 <b>一只</b> 怪（Boss 出现
 * 后永远锁 Boss），客户端只能看见这一条 bossbar 里的 {@code (HP cur/max)}。
 * 结果：多个同名精英 / 普通怪环绕时，头顶 HP 条只有 Boss 那只是正确的，
 * 其余都靠"bossbar 锁谁就把谁的 HP 复制给最近同名怪"的近似，Boss champion
 * 场景下（反馈 2026-05-01）会大面积错位闪烁。
 *
 * <h2>解决思路</h2>
 * <p>服务端本来就有 CTT 数据包维护的 {@code RedHearts} / {@code MaxHP} scoreboard，
 * 直接 per-player 扫视野 48 格内所有活 LivingEntity、按距离排序取前 32 条，
 * 每 5 tick (4 Hz) 打成一包推给该玩家。客户端缓存 5 s 内视作 fresh，
 * 头顶血条渲染直接读 cache；5 s 内没收到 (服务端没装 mod) → 自动回落旧 bossbar
 * 解析链路，老行为完全保留。
 *
 * <h2>v2 协议升级（i18n + 元素抗性护甲）</h2>
 * <p>v1 把 {@code entity.getDisplayName().getString()} 摊平成服务端 locale 的纯字符串
 * 下发，客户端拿不回 translate 键 → 客户端中文 + 服务端英文 = 头顶条恒英文名。
 * 同时 v1 硬写空 suffix，丢失了 datapack {@code targetting_enemy.mcfunction} 的
 * {@code 🛡Defence + 🔥/☃/☠/☀/🛡/⚓/⚡ 元素抗性} 后缀（8 档 ArmorDisplay 模板）。
 * v2 起：
 * <ul>
 *   <li>{@code nameText} 用 {@link ComponentSerialization#REGISTRY_PACKET_CODEC} 结构化下发，translate 键 +
 *       Style 都保留，客户端按本地 lang 自动渲染中文/英文；</li>
 *   <li>新增 {@code defence}（int）/ {@code armorType}（byte 0..7）/ {@code armorValue}（int）
 *       三段原子字段，客户端按 armorType 用硬编码图标 + 颜色重建 suffix Component，
 *       i18n / 颜色全部交客户端，协议保持轻量。</li>
 * </ul>
 *
 * <h2>Entry 字段（v2）</h2>
 * <ul>
 *   <li>{@code uuid}：与客户端实体侧匹配的 entity UUID</li>
 *   <li>{@code nameText}：结构化 {@link Component}，含 translate 键 + Style</li>
 *   <li>{@code hp / maxHp}：来自 scoreboard，maxHp=0 时不会出现（服务端已丢弃）</li>
 *   <li>{@code targetted}：服务端选定的"最近那只"，用来驱动 NEAREST 档与 ▶ 箭头</li>
 *   <li>{@code defence}：物理护甲值（{@code Defence} scoreboard）</li>
 *   <li>{@code armorType}：0 = 仅 Defence；1..7 分别对应
 *       FireArmor / IceArmor / DarkArmor / LightArmor / TrueArmor / WaterArmor / ElectricArmor
 *       （= datapack {@code ArmorDisplay} 0..7）</li>
 *   <li>{@code armorValue}：被选中的元素抗性 scoreboard 值；armorType=0 时恒 0</li>
 * </ul>
 *
 * <h2>v4 · 玩家碎心数（CrackedHearts）</h2>
 * <p>客户端 scoreboard 收不到未上展示槽的 objective（vanilla {@code ServerScoreboard
 * #onScoreChanged} 只广播 trackedObjectives 内的分数，已对照 26.3 字节码核实），
 * 所以玩家碎心数只能由服务端读 scoreboard 随本包 4 Hz 推送。v4 在版本号之后、
 * 条目数之前多写一个 varint {@code crackedHearts}；v3 及以下的旧包该字段按 0 处理。
 *
 * <h2>v6 · 独立碎心 / 粉心</h2>
 * <p>v6 保留了旧的 {@code PinkHearts} 协议字段以兼容旧客户端；当前版本不读取该记分板，
 * 粉心由 {@code CrackedHearts} 的负值表示。</p>
 *
 * <h2>v5 · 玩家自身法力 / 体力</h2>
 * <p>同理，{@code Mana / MaxMana / Stamina / MaxStamina} 四个 objective 都没上展示槽，
 * 纯客户端读不到。而 HUD 新需求要求 SwanJelly 显示"法力条"、Joey 的条换成法力、
 * 手持近战物品时法力条与 Stamina 条互换——这些数值必须随包下发。v5 在
 * {@code crackedHearts} 之后、条目数之前追加 4 个 varint；v4 及以下旧包按 0 处理
 * （客户端自动回落 bossbar 文本解析通道）。
 *
 * <h2>v7 · 服务端骑士资源模式</h2>
 * <p>服务端检查玩家是否带有 {@code Knight} 标签，并检查主手物品是否为
 * {@code Magic} 分类。模式 1 表示骑士默认显示体力，模式 2 表示骑士手持魔法物品显示法力，
 * 模式 0 表示服务端未提供骑士模式，客户端继续使用原有本地兼容逻辑。</p>
 *
 * <h2>版本协商</h2>
 * <p>{@link #read} 对"未来版本"防御：{@code ver > CURRENT_VERSION} 时直接 drain 返回
 * 空包，让 client receiver 本 tick 跳过，UI 沿用上一帧或回落本地。{@code n} 超过
 * 256 的非法值同样 drain 清包，避免恶意或损坏数据触发 {@code NegativeArraySizeException}。
 * <p>{@code ver == 1} 老格式（旧服务端 ↔ 新客户端）走兼容分支：读
 * {@code String name + suffix + nameColor}，name 包成 {@link Component#literal} + 颜色 Style，
 * suffix 字段读完丢弃（v1 恒空），新字段 defence/armorType/armorValue 全填 0。
 *
 * @see com.ctt.healthdisplay.server.MobHealthBroadcaster
 * @see com.ctt.healthdisplay.client.ClientMobHealthCache
 */
public record MobHealthPayload(
        byte version,
        List<Entry> entries,
        int crackedHearts,
        int pinkHearts,
        int mana,
        int maxMana,
        int stamina,
        int maxStamina,
        byte knightResourceMode
) implements CustomPacketPayload {

    /** 服务端骑士资源模式：0=未提供/非骑士，1=体力，2=法力。 */
    public static final byte KNIGHT_MODE_NONE = 0;
    public static final byte KNIGHT_MODE_STAMINA = 1;
    public static final byte KNIGHT_MODE_MANA = 2;

    public static final byte CURRENT_VERSION = 7;

    /** v4 前的兼容构造：玩家自身字段缺省 0（旧测试代码 / 空包路径仍可用）。 */
    public MobHealthPayload(byte version, List<Entry> entries) {
        this(version, entries, 0, 0, 0, 0, 0, 0, KNIGHT_MODE_NONE);
    }

    /** v5 前的兼容构造：只带碎心数。 */
    public MobHealthPayload(byte version, List<Entry> entries, int crackedHearts) {
        this(version, entries, crackedHearts, 0, 0, 0, 0, 0, KNIGHT_MODE_NONE);
    }

    /** v7 前的兼容构造：带玩家属性，但不带服务端骑士模式。 */
    public MobHealthPayload(byte version, List<Entry> entries, int crackedHearts, int pinkHearts,
                            int mana, int maxMana, int stamina, int maxStamina) {
        this(version, entries, crackedHearts, pinkHearts, mana, maxMana, stamina, maxStamina,
                KNIGHT_MODE_NONE);
    }

    public static final CustomPacketPayload.Type<MobHealthPayload> ID = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("ctt-health-display", "mob_health")
    );

    /**
     * 单个怪物血条条目。不带客户端本地的 {@code lastUpdateTick}（那个字段仅本地
     * stale-cleanup 用，服务端不需要 snapshot）。
     */
    public record Entry(
            UUID uuid,
            Component nameText,
            int hp,
            int maxHp,
            int soulHearts,
            int blackHearts,
            int blueHearts,
            boolean targetted,
            int defence,
            byte armorType,
            int armorValue
    ) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, MobHealthPayload> CODEC = StreamCodec.of(
            MobHealthPayload::write,
            MobHealthPayload::read
    );

    private static void write(RegistryFriendlyByteBuf buf, MobHealthPayload p) {
        buf.writeByte(p.version);
        // v4：紧跟版本号写玩家 CrackedHearts（碎心数），供客户端在血条上限旁显示
        // 碎心后的有效上限 "200/150 (200)"。旧版本包不写本字段。
        if (Byte.toUnsignedInt(p.version) >= 4) {
            buf.writeVarInt(p.crackedHearts);
        }
        // v6：旧协议保留 PinkHearts 字段，但当前服务端固定写 0；客户端不再使用它。
        if (Byte.toUnsignedInt(p.version) >= 6) {
            buf.writeVarInt(p.pinkHearts);
        }
        // v5：玩家自身法力 / 体力四段（objective 未上展示槽，纯客户端读不到，只能随包推）。
        if (Byte.toUnsignedInt(p.version) >= 5) {
            buf.writeVarInt(p.mana);
            buf.writeVarInt(p.maxMana);
            buf.writeVarInt(p.stamina);
            buf.writeVarInt(p.maxStamina);
        }
        if (Byte.toUnsignedInt(p.version) >= 7) {
            buf.writeByte(p.knightResourceMode);
        }
        buf.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            buf.writeUUID(e.uuid);
            ComponentSerialization.STREAM_CODEC.encode(buf, e.nameText == null ? Component.empty() : e.nameText);
            buf.writeVarInt(e.hp);
            buf.writeVarInt(e.maxHp);
            buf.writeVarInt(e.soulHearts);
            buf.writeVarInt(e.blackHearts);
            buf.writeVarInt(e.blueHearts);
            buf.writeBoolean(e.targetted);
            buf.writeVarInt(e.defence);
            buf.writeByte(e.armorType);
            buf.writeVarInt(e.armorValue);
        }
    }

    private static MobHealthPayload read(RegistryFriendlyByteBuf buf) {
        byte ver = buf.readByte();
        int verU = Byte.toUnsignedInt(ver);
        if (verU > Byte.toUnsignedInt(CURRENT_VERSION)) {
            // 未来版本：drain 丢弃，UI 沿用上一帧
            buf.skipBytes(buf.readableBytes());
            return new MobHealthPayload(ver, Collections.emptyList(), 0, 0, 0, 0, 0, 0,
                    KNIGHT_MODE_NONE);
        }
        // v4：碎心数在版本号之后、条目数之前；旧包读不到就保持 0。
        int crackedHearts = verU >= 4 ? buf.readVarInt() : 0;
        int pinkHearts = verU >= 6 ? buf.readVarInt() : 0;
        // v5：玩家自身法力 / 体力四段，紧跟心数据、条目数之前。
        int mana = 0, maxMana = 0, stamina = 0, maxStamina = 0;
        if (verU >= 5) {
            mana = buf.readVarInt();
            maxMana = buf.readVarInt();
            stamina = buf.readVarInt();
            maxStamina = buf.readVarInt();
        }
        byte knightResourceMode = verU >= 7 ? buf.readByte() : KNIGHT_MODE_NONE;
        int n = buf.readVarInt();
        // 非法 / 失控值保险：服务端硬上限 64，给 256 余量后直接丢
        if (n < 0 || n > 256) {
            buf.skipBytes(buf.readableBytes());
            return new MobHealthPayload(ver, Collections.emptyList(), crackedHearts, pinkHearts,
                    mana, maxMana, stamina, maxStamina, knightResourceMode);
        }
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (verU >= 2) {
                UUID uuid = buf.readUUID();
                Component nameText = ComponentSerialization.STREAM_CODEC.decode(buf);
                int hp = buf.readVarInt();
                int maxHp = buf.readVarInt();
                int soulHearts = verU >= 3 ? buf.readVarInt() : 0;
                int blackHearts = verU >= 3 ? buf.readVarInt() : 0;
                int blueHearts = verU >= 3 ? buf.readVarInt() : 0;
                boolean targetted = buf.readBoolean();
                int defence = buf.readVarInt();
                byte armorType = buf.readByte();
                int armorValue = buf.readVarInt();
                list.add(new Entry(uuid, nameText, hp, maxHp,
                        soulHearts, blackHearts, blueHearts, targetted,
                        defence, armorType, armorValue));
            } else {
                // ver == 1 兼容：旧 schema 读完转换。
                // v1 schema: uuid, shortString name, shortString suffix, varInt nameColor,
                //            varInt hp, varInt maxHp, boolean targetted
                UUID uuid = buf.readUUID();
                String name = readShortString(buf);
                /* String suffix = */ readShortString(buf); // v1 恒空，丢弃
                int nameColor = buf.readVarInt();
                int hp = buf.readVarInt();
                int maxHp = buf.readVarInt();
                boolean targetted = buf.readBoolean();
                Component nameText = literalWithColor(name, nameColor);
                list.add(new Entry(uuid, nameText, hp, maxHp, 0, 0, 0, targetted, 0, (byte) 0, 0));
            }
        }
        // 末尾残字节保险阀（同 StatsSnapshotPayload 注释）：未来仅追加的末尾标量字段
        // 忘记 bump version 时，drain 一下不至于 codec 报 "buffer not fully consumed" 踢线
        if (buf.isReadable()) {
            buf.skipBytes(buf.readableBytes());
        }
        return new MobHealthPayload(ver, Collections.unmodifiableList(list), crackedHearts, pinkHearts,
                mana, maxMana, stamina, maxStamina, knightResourceMode);
    }

    /** v1 兼容：把"裸字符串名 + ARGB 颜色"包成最简 Component，颜色 0 时不强制覆盖默认样式。 */
    private static Component literalWithColor(String name, int argb) {
        MutableComponent t = Component.literal(name == null ? "" : name);
        if (argb != 0) {
            // v1 server 端写入 nameColor 时会 OR 0xFF000000（见旧 broadcaster 代码），
            // 客户端 Style 颜色只看低 24 位 RGB，保留 alpha 也不影响 TextColor.fromRgb。
            t.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(argb & 0x00FFFFFF)));
        }
        return t;
    }

    private static String readShortString(FriendlyByteBuf buf) {
        int len = buf.readVarInt();
        if (len <= 0) return "";
        byte[] b = new byte[len];
        buf.readBytes(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
