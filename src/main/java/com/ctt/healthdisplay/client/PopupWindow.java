package com.ctt.healthdisplay.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个弹窗窗口：位置可拖动，内容由带样式的文字段（Seg）组成。
 * 带 word 的段就是可点击词条：颜色跟随原文字，只额外画一条同色下划线。
 * 点击它能再开一个新窗口（嵌套）。
 * owner 记录这个窗口是哪个界面带出来的：界面一关（含按 ESC），窗口自动关闭。
 *
 * ── 自动换行（这一版加的）──
 * 以前一行文字有多长，窗口就有多宽：描述一长（比如别的模组那种整段英文 lore），
 * 窗口就宽过屏幕，被钳到边上、右边整截看不见；贴在背包左侧时也放不下，
 * 只能被挪到背包右边去 —— 这就是「不是所有物品都出现在物品栏左侧」的原因。
 * 现在排版走 Layout：给定一个最大宽度，一行放不下就折到下一行，
 * 优先在空格处断（英文、带空格的中文），一个词本身就比整行还宽时（中文长句、超长英文单词）
 * 再按字切。可点击的词整词不拆，被拆开的片段仍然各自可点。
 */
public final class PopupWindow {
    public static final int PAD_X = 4;
    public static final int PAD_Y = 4;
    public static final int LINE_H = 10;

    /** 窄到不能再窄的宽度：再小一个字都放不下，宁可让它占满屏幕。 */
    public static final int MIN_W = 80;
    /** 单个弹窗的最宽宽度（跟原版 Tooltip 的 230 上限对齐，再宽就不像 MC 的界面了）。 */
    public static final int MAX_W = 230;

    public static final class Seg {
        public final String text;
        public final int color;
        public final String word; // null = 不可点击
        public int x, y, w;       // 相对窗口左上角的布局

        Seg(String text, int color, String word) {
            this.text = text;
            this.color = color;
            this.word = word;
        }
    }

    public double x, y;
    public int w, h;
    public int minH;       // 高度下限（进度窗要留出图标那一格）
    public Screen owner;   // 来源界面：null = 没有屏幕时打开；界面关闭则窗口一起关闭
    // ── 进度描述窗专用（普通详情窗不用管）──
    public ItemStack icon;          // 左上图标格里画的物品
    public int accent = 0xFFFFFF;   // 图标格边框色（按任务/挑战/目标区分）
    public int progressPct = -1;    // 完成百分比 0~100，-1 = 没有进度条
    public final List<Seg> segs = new ArrayList<>();
    private int tooltipHintStart;
    private List<Seg> pinnedTooltipHint;

    // ───────────────────────────── 构建入口 ─────────────────────────────

    /** 从物品提示构建描述窗。左侧模式同时预留两种提示的空间，固定时无需重新排版。 */
    public static PopupWindow fromTooltip(List<Component> lines, Font tr, String hint, String pinnedHint, int maxW) {
        Layout lo = new Layout(tr, maxW);
        for (Component line : lines) {
            lo.startLine();
            lo.appendStyled(line, Style.EMPTY, 0xFFFFFF);
            lo.endLine();
        }
        int hintStart = lo.pw.segs.size();
        int hintY = lo.y;
        lo.tooltipHint(hint, 0x808080);
        PopupWindow window = lo.finish();
        if (pinnedHint != null) {
            Layout pinnedLayout = new Layout(tr, maxW);
            pinnedLayout.y = hintY;
            pinnedLayout.tooltipHint(pinnedHint, 0xFFD75E);
            PopupWindow pinnedFooter = pinnedLayout.finish();
            window.tooltipHintStart = hintStart;
            window.pinnedTooltipHint = pinnedFooter.segs;
            window.w = Math.max(window.w, pinnedFooter.w);
            window.h = Math.max(window.h, pinnedFooter.h);
        }
        return window;
    }

    void showPinnedTooltipHint() {
        if (pinnedTooltipHint == null) return;
        segs.subList(tooltipHintStart, segs.size()).clear();
        segs.addAll(pinnedTooltipHint);
    }

    /**
     * 从语言文件词条正文构建详情窗。
     * 第一行是被点的那个词，颜色沿用它在原文里的颜色（不再固定金色）；标题本身不可再点。
     */
    public static PopupWindow fromEntry(String word, List<String> bodyLines, Font tr,
                                        int titleColor, int maxW) {
        Layout lo = new Layout(tr, maxW);
        lo.startLine();
        lo.raw(word, titleColor & 0xFFFFFF, false);
        lo.endLine();
        for (String bodyLine : bodyLines) {
            lo.startLine();
            lo.raw(bodyLine, 0xFFFFFF, true);
            lo.endLine();
        }
        return lo.finish();
    }

    /**
     * 从进度页面的描述构建我们的进度窗（自绘、可固定、左上带图标格）。
     * 图标格占住左上角：凡落在图标竖直范围内的行（标题、描述、提示都算）一律从图标右侧起排，
     * 越过图标下缘后恢复通栏 —— 这就是修「描述很短时提示行压在图标上、字叠字」的关键。
     * 正文超宽自动换行，注册词照旧加下划线可点。颜色由调用方给（羊皮纸牌面用深色字）。
     */
    public static PopupWindow fromAdvancement(Component title, Component desc, String progressText,
                                              String hint, Font tr, int maxW,
                                              int iconW, int iconH,
                                              int titleColor, int descColor, int metaColor) {
        Layout lo = new Layout(tr, maxW);
        lo.reserveIcon(iconW, iconH);
        if (title != null && !title.getString().isEmpty()) {
            lo.startLine();
            lo.appendStyled(title, Style.EMPTY, titleColor);
            lo.endLine();
        }
        if (desc != null && !desc.getString().isEmpty()) {
            lo.startLine();
            lo.appendStyled(desc, Style.EMPTY, descColor);
            lo.endLine();
        }
        if (progressText != null && !progressText.isEmpty()) {
            lo.startLine();
            lo.write(progressText, metaColor, null);
            lo.endLine();
        }
        if (hint != null && !hint.isEmpty()) {
            lo.gap(2);
            lo.startLine();
            lo.raw(hint, metaColor, false);
            lo.endLine();
        }
        PopupWindow pw = lo.finish();
        pw.minH = iconW + PAD_Y * 2; // 至少留出图标那一格的高度
        pw.h = Math.max(pw.h, pw.minH);
        return pw;
    }

    // ───────────────────────────── 排版 + 换行 ─────────────────────────────

    /**
     * 排版游标：把一段段带样式的文字铺到窗口里，顺便负责自动换行。
     * x / y 是当前落笔位置（相对窗口左上角），limit 是内容区可用宽度（不含左右内边距）。
     */
    private static final class Layout {
        private final PopupWindow pw = new PopupWindow();
        private final Font tr;
        private final int limit;
        private final int spaceW;

        private int x = PAD_X;
        private int y = PAD_Y;
        /** 已经占到的最底边（算窗口高度用）。 */
        private int bottom = PAD_Y;
        /** 词与词之间的空格：先记着，等下一个词放得下才真的占位，行尾空格直接丢掉。 */
        private boolean pendingSpace;
        /** 左上角图标格占掉的横向像素（0 = 没有图标）。 */
        private int iconW;
        /** 图标占位区的下边缘：y 还没越过这条线的行，行首都要让到图标右侧。 */
        private int iconUntilY;

        Layout(Font tr, int maxW) {
            this.tr = tr;
            this.limit = Math.max(16, maxW - PAD_X * 2);
            this.spaceW = tr.width(" ");
        }

        /** 预留左上角图标格：它的竖直范围内所有行从图标右侧起排，越过下缘后恢复通栏。 */
        void reserveIcon(int width, int height) {
            iconW = Math.max(0, width);
            iconUntilY = PAD_Y + Math.max(0, height);
            if (x <= PAD_X) {
                x = PAD_X + iconW; // 还在第一行：笔位先挪到图标右侧
            }
        }

        /** 当前这一行的行首（图标范围内要多让出图标宽）。 */
        private int currentIndent() {
            return y < iconUntilY ? iconW : 0;
        }

        PopupWindow finish() {
            // bottom 始终是「最后一行视觉行的下边缘」，place/endLine 都会维护它
            pw.h = bottom + PAD_Y;
            for (Seg s : pw.segs) {
                pw.w = Math.max(pw.w, s.x + s.w);
            }
            pw.w += PAD_X;
            return pw;
        }

        /** 逻辑行开头：上一条还有内容就先换行，保证每条原文各起一行。 */
        void startLine() {
            if (x > PAD_X + currentIndent()) {
                endLine();
            }
        }

        /** 逻辑行结尾：把当前视觉行结算掉。 */
        void endLine() {
            pendingSpace = false;
            bottom = Math.max(bottom, y + LINE_H);
            y += LINE_H;
            x = PAD_X + currentIndent();
        }

        void tooltipHint(String hint, int color) {
            if (hint == null || hint.isEmpty()) return;
            gap(2);
            startLine();
            raw(hint, color, false);
            endLine();
        }

        /** 行间额外留白（提示行与正文之间那点空隙）。只在行首调用。 */
        void gap(int px) {
            pendingSpace = false;
            bottom = Math.max(bottom, y + LINE_H);
            y += px;
            x = PAD_X + currentIndent(); // 行首位置跟着新的 y 重算（可能刚好越过图标下缘）
        }

        /** 放一个不可再拆的原子（一个词 / 一个可点击词条 / 超长词按字切出的一段）。 */
        void atom(String text, int color, String word) {
            int w = tr.width(text);
            if (w <= 0) {
                return;
            }
            int lead = pendingSpace ? spaceW : 0;
            int right = PAD_X + limit; // 内容区右缘
            if (x + lead + w > right && x > PAD_X + currentIndent()) {
                endLineSoft(); // 换行，行尾空格不留
            } else {
                x += lead;
                pendingSpace = false;
            }
            int remain = Math.max(8, right - x); // 这一行还剩多少：图标范围内比整行短
            if (w <= remain) {
                place(text, color, word, w);
                return;
            }
            // 一个词就比这一行还宽：按字切，逐段填满每一行
            int start = 0;
            int acc = 0;
            int cap = remain;
            for (int i = 0; i < text.length(); i++) {
                int cw = tr.width(text.substring(i, i + 1));
                if (acc + cw > cap && acc > 0) {
                    place(text.substring(start, i), color, word, acc);
                    endLineSoft();
                    start = i;
                    acc = 0;
                    cap = Math.max(8, right - x);
                }
                acc += cw;
            }
            if (start < text.length()) {
                place(text.substring(start), color, word, acc);
            }
        }

        /** 换行但不结算 bottom 之外的东西（行内折行用）。 */
        private void endLineSoft() {
            pendingSpace = false;
            bottom = Math.max(bottom, y + LINE_H);
            y += LINE_H;
            x = PAD_X + currentIndent();
        }

        private void place(String text, int color, String word, int w) {
            Seg s = new Seg(text, color, word);
            s.x = x;
            s.y = y;
            s.w = w;
            pw.segs.add(s);
            x += w;
            bottom = Math.max(bottom, y + LINE_H);
        }

        /** 普通文字：按空格切成词，逐个放（空格由 pendingSpace 管）。 */
        void write(String text, int color, String word) {
            if (text.isEmpty()) {
                return;
            }
            if (word != null) {
                atom(text, color, word); // 可点击词整词不拆
                return;
            }
            int i = 0;
            while (i < text.length()) {
                int sp = text.indexOf(' ', i);
                if (sp < 0) {
                    atom(text.substring(i), color, null);
                    return;
                }
                if (sp > i) {
                    atom(text.substring(i, sp), color, null);
                }
                pendingSpace = true;
                i = sp + 1;
            }
        }

        /** 一段原始文字（含 §颜色码）：先按颜色码切段，再交给 write/emit。 */
        void raw(String raw, int baseColor, boolean linkify) {
            int color = baseColor;
            int i = 0;
            StringBuilder buf = new StringBuilder();
            while (i < raw.length()) {
                char c = raw.charAt(i);
                if (c == '§' && i + 1 < raw.length()) {
                    ChatFormatting f = ChatFormatting.getByCode(raw.charAt(i + 1));
                    if (f != null) {
                        emit(buf.toString(), color, linkify);
                        buf.setLength(0);
                        var formatted = net.minecraft.network.chat.TextColor.fromLegacyFormat(f);
                        if (f == ChatFormatting.RESET) color = baseColor;
                        else if (formatted != null) color = formatted.getValue();
                        i += 2;
                        continue;
                    }
                }
                buf.append(c);
                i++;
            }
            emit(buf.toString(), color, linkify);
        }

        private void emit(String text, int color, boolean linkify) {
            if (text.isEmpty()) {
                return;
            }
            if (!linkify) {
                write(text, color, null);
                return;
            }
            int pos = 0;
            for (LoreRegistry.Match m : LoreRegistry.findMatches(text)) {
                write(text.substring(pos, m.start()), color, null);
                write(m.word(), color, m.word());
                pos = m.end();
            }
            write(text.substring(pos), color, null);
        }

        /** 递归遍历 Component 样式树：每个片段用它自己的生效颜色，不再整行拍成一个颜色。 */
        void appendStyled(Component t, Style inherited, int fallbackColor) {
            t.visit((style, text) -> {
                raw(text, colorOf(style, fallbackColor), style.getClickEvent() == null);
                return java.util.Optional.empty();
            }, inherited);
        }
    }

    private static int colorOf(Style st, int fallback) {
        if (st != null && st.getColor() != null) {
            return st.getColor().getValue();
        }
        return fallback;
    }

    public boolean contains(double mx, double my) {
        return mx >= x - 3 && mx <= x + w + 3 && my >= y - 3 && my <= y + h + 3;
    }

    /** 命中一个可点击词条段（用于点击判定）。 */
    public Seg segAt(double mx, double my) {
        for (Seg s : segs) {
            if (s.word != null
                    && mx >= x + s.x && mx < x + s.x + s.w
                    && my >= y + s.y && my < y + s.y + LINE_H) {
                return s;
            }
        }
        return null;
    }
}
