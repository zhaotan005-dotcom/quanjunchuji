package com.zframe.inject;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

import com.zframe.ui.Ui;

/**
 * v1.47 艾挽：游戏内菜单（全新暗色卡片界面）。
 *  - 标题栏可拖动，「—」收起成一条，「✕」关掉；五指同时点屏幕重新打开。
 *  - 顶部一排状态标签，随时看清当前开了什么（游戏模式 / 角色 / 加速 / 高跳 / 无后座 / 聚点 / 子弹 999）。
 *  - 4 个分页：常用、武器、装备、画面（v1.79 删了衣服页）。武器页上面一排分类标签。
 *  - 开关是滑动开关，倍数用新的圆角渐变滑条 + 快捷倍数按钮。
 */
public class GameOverlay {
    static float px = -1, py = -1;
    static final String VER = "v3.41";
    static boolean collapsed = true;   // v2.06 用户：一进去先看到角色，菜单默认收起
    static int tab = 0, weapCat = 0;

    // 配色（v1.49 简约：近黑半透明底 + 白/灰文字 + 一个低饱和蓝，只用细线分隔，不用渐变和彩色标签）
    // v2.05 用户：不要黑底，参考苹果风格 → 浅灰毛玻璃底 + 白色卡片 + 系统蓝，开关绿色
    // v2.64 用户：v2.63 太丑、GPT 那个也不要 → 简约：近黑半透明底、白字、没有边框和渐变，选中=白底黑字，开关绿色
    // v2.65 用户：喜欢白底黑字，要通透的模块，左边分页和右边按钮不能一个样 →
    //   白色半透明底；左边分页是一条浅灰「轨道」、选中=黑底白字胶囊；右边内容放在半透明浅灰卡片里，按钮是白色描边块
    static final int PANEL = 0xEBFAFAFB, CARD = 0x0A000000, CARD2 = 0xFFFFFFFF, LINE = 0x14000000,
            TXT = 0xFF141414, SUBT = 0x8C000000, ACC = 0xFF141414, ON_BG = 0xFF141414, ON_TXT = 0xFFFFFFFF,
            ORANGE = 0xFFFF9500, CHIP = 0x0D000000, GREEN = 0xFF34C759, TRACK = 0x24000000, PURPLE = 0xFF141414, STROKE = 0x1F000000, RAIL = 0x0F000000;

    static GradientDrawable box(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(r);
        return g;
    }
    static GradientDrawable boxLine(int color, int line, float r, int w) {
        GradientDrawable g = box(color, r); g.setStroke(w, line); return g;
    }
    /** 选中态：白底（深色字） */
    static GradientDrawable grad(float r) {
        return box(ON_BG, r);
    }

    static Runnable refresher;
    /** v1.50：界面重建一律排到下一帧。以前在主线程上会立刻重建，松开滑条的那一刻把滑条自己删掉 → 闪退 */
    static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    static void refreshAny() { final Runnable r = refresher; if (r != null) { MAIN.removeCallbacks(r); MAIN.post(r); } }

    static void attach(final Activity a) {
        try {
            Con.setActivity(a);
            Con.loadGfx();
            // v2.58 用户：别弹在腾讯 logo 页（闪屏 Activity）上，等游戏自己的加载页
            String an = a.getClass().getName(); LogCap.note("页面：" + an);
            boolean splash = an.toLowerCase().contains("splash") || an.toLowerCase().contains("logo");
            // v2.59：选择框改到游戏自己的加载页（StartupMap）才弹，见 Con
            // v2.63 用户：更新放到腾讯页 → 一打开就后台查更新（只联网，不碰游戏内存），下好了直接弹「更新」框
            try { NetCore.check(a); } catch (Throwable ignored) {}
            final ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            if (decor.findViewWithTag("zframe_panel") != null) return;
            { View old = panelRef; if (old != null && old.isAttachedToWindow() && panelAct == a) return; }
            final Ui u = new Ui(a);
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
            // v1.49：窄一点（和最早的版本差不多宽），高度不超过屏幕的 82%
            final int pw = Math.min((int) (sw * 0.56f), u.dp(470)), ph = Math.min((int) (sh * 0.90f), u.dp(380));   // v2.61 用户：尽量不用滑 → 宽一点高一点，按钮一行 4 个   // v1.99 左边竖排分页，所以宽一点

            final LinearLayout panel = new LinearLayout(a);
            panel.setTag("zframe_panel");
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(boxLine(PANEL, 0x14000000, u.dp(14), Math.max(1, u.dp(1))));   // v2.08 ImGui 浅色风：小圆角 + 细边框
            panel.setElevation(u.dp(8));
            panel.setClipToOutline(true);

            // ---- 标题栏：名字 + 版本，右边 – × ----
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(u.dp(12), u.dp(2), u.dp(4), u.dp(2));   // v2.08 ImGui 风：单独一条灰色标题栏
            // v2.07 用户：左边一个三角方块（点开展开菜单、再点折叠），标题居中，版本号不放标题栏
            final TextView fold = new TextView(a);
            fold.setTextColor(ON_TXT); fold.setTextSize(13); fold.setGravity(Gravity.CENTER);
            fold.setBackground(grad(u.dp(8)));
            fold.setVisibility(View.GONE);   // v2.64 简约：不要三角方块，点标题收起/展开
            head.addView(fold, new LinearLayout.LayoutParams(u.dp(26), u.dp(26)));
            TextView title = new TextView(a);
            titleRef = title; title.setText(titleText()); title.setTextColor(TXT); title.setTextSize(13.5f); title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setGravity(Gravity.CENTER_VERTICAL); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            final TextView close = iconBtn(a, u, "×");
            close.setOnClickListener(v -> showPanel(panel, false, u));
            head.addView(close, new LinearLayout.LayoutParams(u.dp(34), u.dp(34)));
            panel.addView(head, new LinearLayout.LayoutParams(-1, -2));

            // ---- 当前状态：一行灰字 ----
            final TextView status = new TextView(a);
            status.setTextSize(10.5f); status.setTextColor(SUBT); status.setSingleLine(true);
            status.setEllipsize(android.text.TextUtils.TruncateAt.END);
            status.setPadding(u.dp(14), u.dp(6), u.dp(14), u.dp(6));
            panel.addView(status, new LinearLayout.LayoutParams(-1, -2));
            status.setVisibility(View.GONE);   // v2.64 简约：不要状态行

            // ---- 主体（可收起）：v1.99 左边一列分页（用户：菜单太复古、排版没逻辑），右边内容 ----
            final LinearLayout body = new LinearLayout(a);
            body.setOrientation(LinearLayout.HORIZONTAL);
            final LinearLayout tabsRow = new LinearLayout(a);
            tabsRow.setOrientation(LinearLayout.VERTICAL);
            tabsRow.setPadding(u.dp(4), u.dp(4), u.dp(4), u.dp(4));
            tabsRow.setBackground(box(RAIL, u.dp(12)));
            // v2.41 用户：服务端把刷人机删了
            // v2.61 用户：刷武器、特殊武器、子弹单独分页；道具单独一页
            // v3.05 用户：客户端只保留刷车（其他刷东西的页去掉，空投在出生岛）；分组重排更好看：人物 | 刷物资 | 载具/外观 | 联机/设置，组之间加分隔
            // v3.10 用户：客户端刷东西、画质、加速高跳都删掉（留给服务端），客户端只剩联机；服务端只留地图上没有的东西能刷（道具页只列战术道具 402）
            // v3.13：客户端包现在自建服务端 —— 没加入别人时按服务端的页；加入别人后只剩联机
            final boolean cliT = Ed.ED == 2 || Lan.role == 2, srvT = Ed.ED == 1 || Ed.BUILD == 2;
            final String[] tabs = cliT ? new String[]{"联机"}
                : srvT ? new String[]{"常用", "功能", "道具", "刷车", "衣服", "联机", "画面"}
                : new String[]{"常用", "功能", "武器", "特殊", "子弹", "装备", "道具", "刷车", "衣服", "人机", "联机", "画面"};
            final int[] map = cliT ? new int[]{6}
                : srvT ? new int[]{0, 8, 11, 1, 7, 6, 5} : new int[]{0, 8, 3, 9, 10, 4, 11, 1, 7, 2, 6, 5};
            { boolean ok = false; for (int x : map) if (x == tab) ok = true; if (!ok) tab = map[0]; }
            final java.util.Set<String> groupStart = new java.util.HashSet<>(java.util.Arrays.asList("道具", "刷车", "联机"));   // v2.19：常用下面加「功能」页（加速/高跳/动作放慢）   // 显示顺序 → 原来的页号（fill 按页号填）
            final TextView[] tv = new TextView[tabs.length];
            final ScrollView sv = new ScrollView(a);
            sv.setVerticalScrollBarEnabled(false);
            final LinearLayout content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(u.dp(8), u.dp(2), u.dp(10), u.dp(8));
            sv.addView(content);
            for (int k = 0; k < tabs.length; k++) {
                final int idx = map[k];
                TextView t = new TextView(a);
                t.setText(tabs[k]); t.setTextSize(12.5f); t.setGravity(Gravity.CENTER);
                t.setTag(idx);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(u.dp(48), u.dp(26)); tl.bottomMargin = u.dp(1);
                if (k > 0 && groupStart.contains(tabs[k])) {
                    android.view.View sep = new android.view.View(a); sep.setBackgroundColor(0x33FFFFFF);
                    LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(u.dp(30), u.dp(1)); sl.gravity = Gravity.CENTER_HORIZONTAL; sl.topMargin = u.dp(4); sl.bottomMargin = u.dp(5);
                    tabsRow.addView(sep, sl);
                }
                tabsRow.addView(t, tl);
                tv[k] = t;
                t.setOnClickListener(v -> { tab = idx; paintTabs(tv, u); fill(a, u, content); sv.scrollTo(0, 0); });
            }
            paintTabs(tv, u);
            // v2.27b：房主 9 个分页，竖着放不下（人机/联机/画面被挤出去，看不到日志）→ 左列能上下滑
            final ScrollView tsv = new ScrollView(a);
            tsv.setVerticalScrollBarEnabled(false);
            tsv.addView(tabsRow);
            { LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, ph - u.dp(56)); tlp.setMargins(u.dp(8), 0, u.dp(2), u.dp(8)); body.addView(tsv, tlp); }
            final View hr = new View(a); hr.setBackgroundColor(0);
            body.addView(hr, new LinearLayout.LayoutParams(1, -1));
            body.addView(sv, new LinearLayout.LayoutParams(0, ph - u.dp(56), 1));
            final int svH = ph - u.dp(56);
            panel.addView(body, new LinearLayout.LayoutParams(-1, -2));

            final View tabsLine = hr;
            // v2.05：对局里的分页按身份不同 —— 单机：常用/武器/装备/刷车/人机/画面；房主：人机换成联机；加入的客户端：只有联机/画面
            final Runnable mode = () -> {
                int v = Con.inMatch ? View.VISIBLE : View.GONE; tsv.setVisibility(v); tabsLine.setVisibility(v);
                // v2.19 用户：大厅菜单下面一大片空白 → 大厅里内容多高就多高
                LinearLayout.LayoutParams sl = (LinearLayout.LayoutParams) sv.getLayoutParams();
                int want = Con.inMatch ? svH : -2;
                if (sl.height != want) { sl.height = want; sv.setLayoutParams(sl); }
                int r = Lan.role;
                for (TextView t : tv) {
                    int id = (Integer) t.getTag();
                    boolean show = r == 0 ? id != 6 && id != 7 : r == 1 ? true : (id == 6 || id == 5 || id == 0 || id == 8 || id == 3 || id == 9 || id == 10 || id == 4 || id == 11 || id == 1 || id == 7);   // v2.92 客户端加回 刷车、衣服（服务端代刷）   // v2.91 客户端刷东西页加回来（服务端代刷）
                    t.setVisibility(show ? View.VISIBLE : View.GONE);
                }
                if (Con.inMatch) {
                    boolean ok = r == 0 ? tab != 6 && tab != 7 : r == 1 ? true : (tab == 6 || tab == 5 || tab == 0 || tab == 8 || tab == 3 || tab == 9 || tab == 10 || tab == 4 || tab == 11);
                    if (!ok) { tab = r == 2 ? 6 : 0; paintTabs(tv, u); }
                }
            };
            mode.run();
            refresher = () -> { mode.run(); paintStatus(status); fill(a, u, content); TextView tt = titleRef; if (tt != null) tt.setText(titleText()); };
            paintStatus(status);
            fill(a, u, content);
            final Runnable[] tick = new Runnable[1];
            tick[0] = () -> { if (panel.getParent() == null) return; if (panel.getVisibility() == View.VISIBLE) paintStatus(status); panel.postDelayed(tick[0], 1000); };
            panel.postDelayed(tick[0], 1000);

            // v2.05 用户：不要「艾」悬浮球 → 「–」收起成只剩标题栏的小条，点标题栏或「+」展开
            final Runnable apply = () -> {
                body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
                close.setVisibility(View.GONE);   // v3.38 用户：取消关闭按钮（大厅进对局自动关，对局里上拖隐藏）
                fold.setText(collapsed ? "▶" : "▼");
                ViewGroup.LayoutParams lp0 = panel.getLayoutParams();
                int nw = collapsed ? u.dp(150) : pw;   // v2.64 收起=只剩一小条标题
                if (lp0 != null) {
                    int ow = lp0.width > 0 ? lp0.width : nw; lp0.width = nw;
                    if (lp0 instanceof WindowManager.LayoutParams) { try { pwm.updateViewLayout(panel, lp0); } catch (Throwable ignored) {} }
                    else panel.setLayoutParams(lp0);
                    if (ow != nw) { setXY(panel, getX(panel) + (ow - nw) / 2f, getY(panel)); px = getX(panel); }
                }
                panel.post(() -> clamp(panel, decor, u));
            };
            fold.setOnClickListener(v -> { collapsed = !collapsed; apply.run(); if (!collapsed) fill(a, u, content); });

            hookKeys(a, panel, u);
            if (px < 0) { px = (sw - (collapsed ? u.dp(150) : pw)) / 2f; py = u.dp(4); }   // v2.19 用户：默认放中上（以前偏左，容易点到电竞页）
            // v2.36 用户：菜单上滑动游戏还是跟着动（v2.29 的拦截没用）。游戏是 NativeActivity，触摸先进引擎的输入队列、再给界面，
            // 同一个窗口里拦不住。所以菜单放进自己的小窗口（只有菜单那么大），按在菜单上的触摸根本不会到游戏窗口。
            plp = null; pwm = null;
            try {
                WindowManager wm = a.getWindowManager();
                WindowManager.LayoutParams wl = new WindowManager.LayoutParams(pw, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                wl.gravity = Gravity.TOP | Gravity.START;
                wl.x = (int) px; wl.y = (int) py;
                if (android.os.Build.VERSION.SDK_INT >= 28) wl.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                wl.setTitle("zframe_menu");
                panel.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                pwm = wm; plp = wl;
                wm.addView(panel, wl);
                LogCap.note("菜单：独立窗口");
            } catch (Throwable t) {
                plp = null; pwm = null;
                if (panel.getParent() != null) { try { ((ViewGroup) panel.getParent()).removeView(panel); } catch (Throwable ignored) {} }
                LogCap.note("菜单：独立窗口失败，放回游戏窗口 " + t);
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(pw, -2);
                flp.gravity = Gravity.TOP | Gravity.START;
                decor.addView(panel, flp);
                panel.setX(px); panel.setY(py);
            }
            panelAct = a;
            // v3.22 用户：删掉自制的「道具」键（战术模式用游戏原生道具栏）
            // v2.34：屏幕大小变了（云手机转屏/分辨率）或菜单变大变小，都重新夹回屏幕里
            panel.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> clamp(panel, decor, u));
            decor.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> { if (r - l != or - ol || bt - t != ob - ot) panel.post(() -> clamp(panel, decor, u)); });
            if (hidden || !lobbySeen) panel.setVisibility(View.GONE);   // v2.19 用户：腾讯加载页不显示，到大厅才出来
            panelRef = panel;
            apply.run();
            // v3.38 用户：大厅里菜单固定在中上（收起），不能拖
            lobbyPos = () -> { collapsed = true; apply.run(); px = (sw - u.dp(150)) / 2f; py = u.dp(4); setXY(panel, px, py); };

            head.setOnTouchListener(new View.OnTouchListener() {
                float dx, dy, sx, sy; boolean moved;
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: dx = e.getRawX(); dy = e.getRawY(); sx = getX(panel); sy = getY(panel); moved = false; return true;
                        case MotionEvent.ACTION_MOVE:
                            if (!Con.inMatch) return true;   // v3.38 用户：大厅里不能拖
                            float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                            if (Math.abs(mx) + Math.abs(my) > u.dp(6)) moved = true;
                            if (moved) { setXY(panel, sx + mx, sy + my); clamp(panel, decor, u); }
                            return true;
                        case MotionEvent.ACTION_CANCEL: clamp(panel, decor, u); return true;
                        case MotionEvent.ACTION_UP:
                            // v3.38 用户：对局里往上拖（顶到屏幕上边还继续拖）= 隐藏，五指再打开
                            if (Con.inMatch && moved && sy + (e.getRawY() - dy) < -u.dp(24)) { clamp(panel, decor, u); showPanel(panel, false, u); return true; }
                            clamp(panel, decor, u);
                            if (!moved) { collapsed = !collapsed; apply.run(); if (!collapsed) fill(a, u, content); }
                            return true;
                    }
                    return false;
                }
            });
        } catch (Throwable t) {
            android.util.Log.e("ZFrame", "overlay", t);
        }
    }

    /** v2.34 用户：服务端菜单被拖到屏幕外（标题栏出去了）就拉不回来、点不到。位置一直夹在屏幕里：整个标题栏必须在屏幕内 */
    static void clamp(View panel, View decor, Ui u) {
        int W = decor.getWidth(), H = decor.getHeight();
        if (W <= 0 || H <= 0) return;
        int w = panel.getWidth() > 0 ? panel.getWidth() : u.dp(210);
        float x = getX(panel), y = getY(panel);
        float nx = w <= W ? Math.max(0, Math.min(x, W - w)) : Math.max(W - w, Math.min(x, 0));
        float ny = Math.max(0, Math.min(y, H - u.dp(40)));
        if (nx != x || ny != y) setXY(panel, nx, ny);
        px = nx; py = ny;
    }
    /** v2.36：菜单在自己的窗口里时，位置是窗口的 x/y；放回游戏窗口时是 View 的 x/y */
    static volatile WindowManager pwm; static volatile WindowManager.LayoutParams plp; static volatile Activity panelAct;
    static float getX(View v) { WindowManager.LayoutParams l = plp; return l != null && v.getLayoutParams() == l ? l.x : v.getX(); }
    static float getY(View v) { WindowManager.LayoutParams l = plp; return l != null && v.getLayoutParams() == l ? l.y : v.getY(); }
    static void setXY(View v, float x, float y) {
        WindowManager.LayoutParams l = plp;
        if (l != null && v.getLayoutParams() == l) {
            if (l.x == (int) x && l.y == (int) y) return;
            l.x = (int) x; l.y = (int) y;
            try { if (v.isAttachedToWindow()) pwm.updateViewLayout(v, l); } catch (Throwable ignored) {}
        } else { v.setX(x); v.setY(y); }
    }
    /** v2.36：独立窗口不抢焦点（不然游戏会以为切到后台）；只有点「按编号穿」的输入框时临时可聚焦，弹键盘 */
    static void focusable(View v, boolean on) {
        WindowManager.LayoutParams l = plp; View p = panelRef;
        if (l == null || p == null || p.getLayoutParams() != l) return;
        int f = on ? l.flags & ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE : l.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (f == l.flags) return;
        l.flags = f;
        try { pwm.updateViewLayout(p, l); } catch (Throwable ignored) {}
    }
    static String icon(String t) {
        switch (t) { case "常用": return "⌂"; case "功能": return "✧"; case "武器": return "✦"; case "特殊": return "◆"; case "子弹": return "•";
            case "装备": return "▣"; case "道具": return "◈"; case "衣服": return "◇"; case "刷车": return "▸"; case "人机": return "◎"; case "联机": return "⇄"; case "画面": return "⚙"; }
        return "·";
    }
    static TextView iconBtn(Activity a, Ui u, String s) {
        TextView b = new TextView(a);
        b.setText(s); b.setTextColor(SUBT); b.setTextSize(17);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    static void paintTabs(TextView[] tv, Ui u) {
        for (int j = 0; j < tv.length; j++) {
            boolean on = tv[j].getTag() instanceof Integer && (Integer) tv[j].getTag() == tab;
            tv[j].setTextColor(on ? ON_TXT : SUBT);
            tv[j].setTypeface(on ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
            tv[j].setBackground(on ? box(ON_BG, u.dp(9)) : null);   // v2.65 选中：黑底白字胶囊
            tv[j].setTextColor(on ? ON_TXT : SUBT);   // v1.99 选中：白色圆角块
        }
    }

    /** 当前状态：一行文字，例如「● 不上飞机 · 不刷圈 · 男 · 加速 2 · 无后座 · 聚点 · 子弹 999」 */
    static void paintStatus(TextView st) {
        StringBuilder b = new StringBuilder();
        if (!Mods.noPlane && !Mods.noPoison) b.append("正常对局");
        else { if (Mods.noPlane || Mods.noPoison) b.append("不刷毒对局"); }
        if (!Con.inMatch) { st.setText(dot((Ed.ED == 2 ? "" : b.toString() + " · ") + VER + " " + Ed.name())); return; }   // v2.26 客户端大厅不显示对局模式   // v1.73：不显示男女和大厅
        if (Mods.speed != 1f) b.append(" · 加速 ").append(fmt(Mods.speed));
        if (Mods.jump != 1f) b.append(" · 高跳 ").append(fmt(Mods.jump));
        if (Mods.noRecoil) b.append(" · 无后座");
        if (Mods.noShake) b.append(" · 聚点");
        if (Mods.infAmmo) b.append(" · 子弹 999");
        if (Con.godOn) b.append(" · 无敌");
        if ((Mods.speed != 1f || Mods.jump != 1f || Mods.noRecoil || Mods.noShake || Mods.infAmmo) && !Mods.moveState.isEmpty() && !"已生效".equals(Mods.moveState))
            b.append("（").append(Mods.moveState).append("）");
        st.setText(dot(b.toString() + " · " + VER + " " + Ed.name()));
    }
    static CharSequence dot(String s) {
        android.text.SpannableString ss = new android.text.SpannableString("●  " + s);
        ss.setSpan(new android.text.style.ForegroundColorSpan(0xFF4ADE80), 0, 1, 0);
        return ss;
    }

    // v1.40：五指同时点屏幕 → 打开菜单；标题栏右边 ✕ → 关掉（带出场/退场动画）
    static volatile boolean hidden;
    static volatile boolean lobbySeen; static volatile View panelRef;
    /** v2.19：第一次进大厅（或进图）后才把菜单显示出来 */
    static void lobbyReady() {
        if (lobbySeen) { mapShown(); return; } lobbySeen = true;
        try { NetCore.lobby(Con.act); } catch (Throwable ignored) {}   // v2.52 在线内核
        ModeProbe.survey();   // v3.00 服务端：名字表修好后自动全量找一次三军对垒/决胜五军模式蓝图，传 mode-*
        // v2.56：卸了补丁更新 App 后仓库空 → 本次启动进大厅 40 秒后自动传一份诊断（只传一次）
        new Thread(() -> { try { Thread.sleep(40000); Activity x = Con.act; if (x != null) AvatarLog.export(x, "lobby-"); } catch (Throwable ignored) {} }, "zf-diag").start();
        mapShown();
    }
    /** v2.35 用户：进大厅、进对局菜单出来时，也要和五指打开一样的过渡动画。切图（Browse）时先收起，图加载好再弹出来 */
    static volatile int mapGen;
    static volatile Runnable lobbyPos;
    /** v3.38 用户：大厅菜单固定中上（收起）；进对局自动关、对局里不自动开，五指按屏幕才开 */
    static void placeFor(View p) {
        if (Con.inMatch) { hidden = true; p.animate().cancel(); p.setVisibility(View.GONE); return; }
        hidden = false; Runnable r = lobbyPos; if (r != null) r.run(); enterAnim(p);
    }
    static void mapShown() {
        final int g = ++mapGen;
        MAIN.post(() -> { View p = panelRef; if (p != null && g == mapGen) placeFor(p); });
    }
    static void mapLeaving() {
        final int g = ++mapGen;
        MAIN.post(() -> {
            final View p = panelRef; if (p == null || hidden || p.getVisibility() != View.VISIBLE) return;
            p.animate().cancel();
            p.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(180)
                    .withEndAction(() -> { if (g == mapGen) p.setVisibility(View.GONE); }).start();
            // 加载失败没收到进图：40 秒后照样弹回来
            MAIN.postDelayed(() -> { if (g == mapGen) placeFor(p); }, 40000);
        });
    }
    static void enterAnim(View pv) {
        pv.animate().cancel();
        float d = pv.getResources().getDisplayMetrics().density;
        pv.setVisibility(View.VISIBLE);
        pv.setAlpha(0f); pv.setScaleX(0.85f); pv.setScaleY(0.85f); pv.setTranslationY(plp != null && pv.getLayoutParams() == plp ? 0 : -30 * d);
        pv.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(260)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
    }
    static volatile boolean onPanel;
    static void hookKeys(final Activity a, final View panel, final Ui u) {
        try {
            final android.view.Window w = a.getWindow();
            final android.view.Window.Callback orig = w.getCallback();
            if (orig == null || java.lang.reflect.Proxy.isProxyClass(orig.getClass())) return;
            Object px = java.lang.reflect.Proxy.newProxyInstance(android.view.Window.Callback.class.getClassLoader(),
                    new Class<?>[]{android.view.Window.Callback.class}, (p, m, args) -> {
                String n = m.getName();
                if ("dispatchTouchEvent".equals(n) && args != null && args[0] instanceof MotionEvent) {
                    MotionEvent e = (MotionEvent) args[0];
                    if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() >= 5 && hidden) {
                        View pv = panelRef;
                        if (pv != null) a.runOnUiThread(() -> showPanel(pv, true, u));
                    }
                    // v2.28b 用户：在菜单上滑动，游戏也跟着转视角/走路。游戏在 Activity.dispatchTouchEvent 里就把触摸交给引擎了，
                    // 所以按下点在菜单上的这一整串触摸，直接交给界面（w.superDispatchTouchEvent），不经过游戏。
                    int am = e.getActionMasked();
                    if (am == MotionEvent.ACTION_DOWN) {
                        View pv = ((ViewGroup) w.getDecorView()).findViewWithTag("zframe_panel");   // 只有菜单放回游戏窗口时才找得到
                        onPanel = false;
                        if (pv != null && pv.getVisibility() == View.VISIBLE && pv.getAlpha() > 0.1f) {
                            android.graphics.Rect r = new android.graphics.Rect();
                            if (pv.getGlobalVisibleRect(r)) onPanel = r.contains((int) e.getRawX(), (int) e.getRawY());
                        }
                    }
                    if (onPanel) {
                        boolean h = w.superDispatchTouchEvent(e);
                        if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) onPanel = false;
                        return h;
                    }
                }
                if ("equals".equals(n)) return p == args[0];
                if ("hashCode".equals(n)) return System.identityHashCode(p);
                if ("toString".equals(n)) return "AW-touch";
                try { return m.invoke(orig, args); } catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
            });
            w.setCallback((android.view.Window.Callback) px);
        } catch (Throwable t) { LogCap.note("五指开关失败 " + t); }
    }
    static void showPanel(final View pv, boolean show, Ui u) {
        hidden = !show;
        pv.animate().cancel();
        if (show) {
            if (pv.getVisibility() == View.VISIBLE && pv.getAlpha() == 1f) return;
            pv.setVisibility(View.VISIBLE);
            pv.setAlpha(0f); pv.setScaleX(0.85f); pv.setScaleY(0.85f); pv.setTranslationY(plp != null && pv.getLayoutParams() == plp ? 0 : -u.dp(30));
            pv.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(260)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        } else {
            if (pv.getVisibility() != View.VISIBLE) return;
            pv.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).translationY(plp != null && pv.getLayoutParams() == plp ? 0 : -u.dp(30)).setDuration(200)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> { if (hidden) pv.setVisibility(View.GONE); }).start();
        }
    }

    static void detach(Activity a) {
        try {
            if (PropBtn.act == a) PropBtn.detach();
            if (chooser != null && chooserAct == a) { try { a.getWindowManager().removeViewImmediate(chooser); } catch (Throwable ignored) {} chooser = null; chooserAct = null; }
            View pr = panelRef;
            if (pr != null && plp != null && pr.getLayoutParams() == plp && panelAct == a) {
                try { pwm.removeViewImmediate(pr); } catch (Throwable ignored) {}
                panelRef = null; panelAct = null;
            }
            ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            View b = decor.findViewWithTag("zframe_panel");
            if (b != null) decor.removeView(b);
            View ball = decor.findViewWithTag("zframe_ball");
            if (ball != null) decor.removeView(ball);
        } catch (Throwable ignored) {}
    }

    static void cmd(Activity a, String c) { cmd(a, c, null); }

    static void cmd(Activity a, String c, String label) {
        boolean ok = Con.exec(c);
        if (label != null && !ok) Toast.makeText(a, "执行失败", Toast.LENGTH_SHORT).show();
    }

    static void toastAny(String msg) { Activity a = Con.act; if (a != null) a.runOnUiThread(() -> toast(a, msg)); }
    /** v3.22 用户：黑框提示直接删掉（不是精简），只有出错这类必须让人知道的才弹 */
    static boolean keepToast(String msg) {
        if (msg == null || msg.isEmpty()) return false;
        for (String k : new String[]{"失败", "出错", "错误", "闪退", "没反应", "找不到", "没找到", "已自动上传"}) if (msg.contains(k)) return true;
        return false;
    }
    static void toast(Activity a, String msg) { if (!keepToast(msg)) { LogCap.note("提示(不显示)：" + msg); return; } Toast.makeText(a, msg, Toast.LENGTH_SHORT).show(); }

    /** v1.26：GiveItem 编号x数量（刷物品补丁里的代码会把 x 后面的数字当数量，最多 999） */
    // v1.27 诊断：传一块自己的内存地址给补丁代码，它把走到哪一步写进来（只写这块缓冲区，不碰游戏内存）
    static java.nio.ByteBuffer dbg; static long dbgAddr;
    static long bufAddr(java.nio.ByteBuffer b) {
        try { java.lang.reflect.Field f = java.nio.Buffer.class.getDeclaredField("address"); f.setAccessible(true); return f.getLong(b); }
        catch (Throwable e) { return 0; }
    }
    // v1.37：问补丁代码要人物地址（编号 0 只填地址不刷东西），给加速/高跳用
    static java.nio.ByteBuffer pbuf; static long pbufAddr;
    static void askPawn() {
        if (giveSt != 2 || !Con.inMatch) return;
        if (pbuf == null) { pbuf = java.nio.ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.LITTLE_ENDIAN); pbufAddr = bufAddr(pbuf); }
        if (pbufAddr <= 0 || pbufAddr >= 0xFFFFFFFFL) return;
        final Activity a = Con.act; if (a == null) return;
        a.runOnUiThread(() -> Con.exec("AddAvaItem P20600000001x1x" + pbufAddr)); // v1.47：编号 0 补丁代码不填人物地址，改成刷 1 发 5.56
    }
    static long pawnAddr() {
        if (pbuf != null) { int m = pbuf.getInt(0); if (m == 0x5a01 || m == 0x5a02) return pbuf.getInt(4) & 0xffffffffL; }
        // 刷过任何东西后，诊断缓冲区里也有人物地址
        if (dbg != null) { int m = dbg.getInt(0); if (m == 0x5a01 || m == 0x5a02) return dbg.getInt(4) & 0xffffffffL; }
        return 0;
    }
    static void forgetPawn() { if (pbuf != null) pbuf.putInt(0, 0); if (dbg != null) dbg.putInt(0, 0); }
    /** v1.91 刷在身上（背包）还是刷在地上（面前地面，用游戏自己的 SpawnOnePickUp），不记忆，每次启动回到身上 */
    static volatile boolean onGround = Ed.ED == 1;   // v2.13：房主版固定刷在脚下
    static void give(Activity a, String id, int n) {
        // v2.89：联机客户端本地刷的东西服务器不认（日志 Force set local front ammo count…ServerAmmo=0）→ 一直换弹。加入别人房间时不在本地刷
        if (Ed.ED == 2 && Lan.role == 2) { Give.ask(id, n); return; }   // v2.90：让服务端代刷在自己脚下
        if (onGround) {
            final long v; try { v = Long.parseLong(id); } catch (Throwable e) { return; }
            final int piles = n <= 1 ? 1 : Math.min(3, n);
            new Thread(() -> {
                try {
                    // v3.10 用户：服务端刷的东西只放在加入玩家脚下，不放服务端脚下（没有加入玩家才放自己脚下）
                    if (Ed.ED == 1 && Lan.role == 1) {
                        float[] pl = Box.joinedPos(Mods.base(), Veh.Names.get());
                        if (pl != null) { String e = null; for (int i = 0; i < piles && e == null; i++) e = Veh.dropAt(v, pl[0], pl[1], pl[2] - 80f); toastAny(e == null ? "已刷在加入玩家脚下" : "没刷到：" + e); return; }
                    }
                    String e = Veh.ground(v, piles); if (e != null) toastAny("没刷到地上：" + e); }
                catch (Throwable e) { toastAny("没刷到地上：" + e); }
            }, "zf-ground").start();
            return;
        }
        if (dbg == null) { dbg = java.nio.ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.LITTLE_ENDIAN); dbgAddr = bufAddr(dbg); }
        for (int i = 0; i < 64; i += 4) dbg.putInt(i, 0);
        // v1.28：GiveItem 走不到人物身上；改用控制器上的 AddAvaItem（同一个入口），P 开头表示由补丁代码去找控制器的人物
        String c = "AddAvaItem P" + id + "x" + Math.max(1, n);
        if (dbgAddr > 0 && dbgAddr < 0xFFFFFFFFL) c += "x" + dbgAddr;
        Con.exec(c);
    }
    static String dbgReason() {
        if (dbg == null || dbg.getInt(0) == 0) return "没反应";
        int k = dbg.getInt(24);
        return k == -5 ? "游戏不让放进背包，可能和角色性别不符" : k == -6 ? "游戏里没有这个编号" : "代码 " + k;
    }
    static String dbgText() {
        if (dbg == null) return "没有诊断";
        int m = dbg.getInt(0);
        if (m == 0) return dbgAddr == 0 ? "诊断缓冲区拿不到地址" : "补丁代码没被调用";
        return String.format("进入=%s 人物=%08x 背包=%08x 类型=%d 子编号=%d 数量=%d 可放=%d 结果=%d",
                m == 0x5a02 ? "走完" : "中途", dbg.getInt(4), dbg.getInt(8), dbg.getInt(12), dbg.getInt(16), dbg.getInt(20), dbg.getInt(24), dbg.getInt(28));
    }
    // 配件：枪口/握把/瞄具/弹匣/枪托（编号来自游戏里的自动拾取表和 GM 列表）
    static final String[] ATT = {"20501000001", "20501000003", "20502000001", "20502000002", "20503000001", "20503000003",
            "20503000004", "20503000005", "20503000008", "20503000009", "20504000001", "20505000001"};
    /** v2.92 用户：全套配件只有步枪的、扩容没有快速扩容 → 用游戏物品表里扫到的全部 205 类（枪口/握把/瞄具/弹匣/枪托，所有枪种），没扫过就先扫 */
    static void allAtt(Activity a) {
        java.util.TreeMap<Long, java.util.ArrayList<Long>> m = items;
        if (m == null) { loadItems(false); toast(a, "正在读游戏里的全部配件，读完再点一次"); return; }
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, java.util.ArrayList<Long>> e : m.entrySet()) if (e.getKey() / 100 == 205) for (long v : e.getValue()) if (!itemBad.contains(v)) l.add(String.valueOf(v));
        if (l.isEmpty()) for (String at : ATT) l.add(at);
        int n = 0;
        for (String at : l) if (givenAtt.add(at)) { give(a, at, 1); n++; }
        if (n == 0) toast(a, "这局配件已经刷过了（共 " + l.size() + " 件）"); else toast(a, "全套配件 " + n + " 件，放在脚下");
        LogCap.note("全套配件：" + l);
    }
    /** v2.92 用户：刷油桶。油桶是道具类（402），编号游戏里没写名字 → 从扫到的道具里找认出名字是油桶/Barrel 的；没认出来就提示 */
    static void barrel(Activity a) {
        java.util.TreeMap<Long, java.util.ArrayList<Long>> m = items;
        if (m == null) { loadItems(false); toast(a, "正在读游戏道具表，读完再点一次"); return; }
        for (java.util.Map.Entry<Long, String> e : itemName.entrySet()) {
            String nm = e.getValue();
            if (nm.contains("油桶") || nm.contains("Barrel") || nm.contains("Gas") || nm.contains("Oil") || nm.contains("Fuel")) { give(a, String.valueOf(e.getKey()), 1, "油桶"); return; }
        }
        toast(a, "还没认出哪个编号是油桶：去「道具」页 全部道具→道具，刷过的会显示名字，认出后这个键就能用");
    }
    /** v2.91：加入别人房间的客户端 → 刷东西都让服务端代刷在脚下 */
    static boolean remote() { return Ed.ED == 2 && Lan.role == 2; }
    static void give(Activity a, String id, int n, String label) {
        if (remote()) { Give.ask(id, n); return; }
        if (giveSt == 1 || giveSt == 3) { toast(a, "先在补丁更新里点「更新补丁」"); return; }
        give(a, id, n);
        after(a, label);
    }
    /** v1.31：按顺序试一串编号，刷不出来（结果=0）自动换下一个，刷出来就记住 */
    static final java.util.Map<String, String> okId = new java.util.HashMap<>();
    static void giveTry(final Activity a, final java.util.List<String> ids, final int i, final String lb) {
        if (giveSt == 1 || giveSt == 3) { toast(a, "先在补丁更新里点「更新补丁」"); return; }
        if (i >= ids.size()) { toast(a, ids.size() == 1 ? lb + " 没刷出来（" + dbgReason() + "）" : lb + " 试了 " + ids.size() + " 个编号都没刷出来，把日志发我"); return; }
        final String id = ids.get(i);
        give(a, id, 1);
        if (onGround || remote()) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            String t = dbgText(); LogCap.note("刷物品诊断 " + lb + " " + id + ": " + t);
            boolean ok = dbg != null && dbg.getInt(0) == 0x5a02 && dbg.getInt(28) != 0;
            if (ok) okId.put(lb, id);
            else giveTry(a, ids, i + 1, lb);
        }, 1200);
    }
    /** v2.13 吉利服：EAvaItemSlot 顺序 Trousers/Shirt/Shoes/Helmet/Armor/Gloves/Bagpack/Facing/Ghillie… 对上 30301 裤 … 30304 头、30305 甲、
     *  30306 手套、30307 包 → Ghillie = 30309（仓库扫到 12 件）。具体编号没表，按顺序试。刷身上能判断成没成；刷地上判断不了，每点一次换下一个编号。 */
    static final String[] GHILLIE = {"30309000001", "30309000002", "30309000003"};   // v2.38：服务端日志 20:45 证实 04/05/06/00 报 Can't Find Item，只有 01-03 是真的
    static int ghAt = 0;
    static void ghillie(Activity a) {
        String k = okId.get("吉利服");
        if (!onGround) { if (gearOnce(a, "吉利服")) giveTry(a, k != null ? java.util.Collections.singletonList(k) : java.util.Arrays.asList(GHILLIE), 0, "吉利服"); return; }
        if (k != null) { give(a, k, 1); return; }
        String id = GHILLIE[ghAt % GHILLIE.length]; ghAt++;
        give(a, id, 1);
        toast(a, "吉利服：试编号 " + id + "（第 " + ghAt + " 个），脚下没出现就再点一次");
        LogCap.note("吉利服 地上 试 " + id);
    }
    static java.util.List<String> armorIds() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String k = okId.get("3级甲"); if (k != null) l.add(k);
        if (PakScan.armor3 != null && !l.contains(PakScan.armor3)) l.add(PakScan.armor3);
        for (String x : new String[]{"30305000003", "30305000005", "30305000006"}) if (!l.contains(x)) l.add(x);
        return l;
    }

    static void after(Activity a, final String lb) {
        if (onGround || remote()) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            String t = dbgText(); LogCap.note("刷物品诊断 " + lb + ": " + t);
            boolean ok = dbg != null && dbg.getInt(0) == 0x5a02 && dbg.getInt(28) != 0;
            if (!ok) toast(a, lb + " 没刷出来");
        }, 1200);
    }

    static volatile int giveSt = -1; // -1 还没查，0 查不到，1 没打，2 已打
    static String giveText(final Activity a) {
        if (giveSt == -1) {
            giveSt = -2;
            new Thread(() -> { giveSt = readGive(a); LogCap.note("刷物品补丁状态 " + giveSt); }).start();
        }
        return giveSt == 2 ? "补丁已打" : giveSt == 1 || giveSt == 3 ? "先在补丁更新里点「更新补丁」" : giveSt == 0 ? "" : "检查补丁中…";
    }
    static int readGive(Activity a) {
        int w = readWord(a, 0xe40cccL);
        if (w == 0xebf48b57) return 1;
        if (w != 0xeb23573b) return 0;
        return readWord(a, 0x17169dcL) == 0xe3560002 ? 2 : 3;
    }
    static int readWord(Activity a, final long off) {
        byte[] cur = new byte[4];
        try {
            java.io.File f = new java.io.File(a.getApplicationInfo().nativeLibraryDir, "libUE4.so");
            if (f.length() > off + 4) {
                try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r")) { r.seek(off); r.readFully(cur); }
            } else {
                try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(a.getApplicationInfo().sourceDir)) {
                    java.util.zip.ZipEntry e = z.getEntry("lib/armeabi-v7a/libUE4.so");
                    if (e == null) return 0;
                    try (java.io.InputStream is = z.getInputStream(e)) {
                        long left = off; byte[] b = new byte[1 << 16];
                        while (left > 0) { int r = is.read(b, 0, (int) Math.min(b.length, left)); if (r < 0) return 0; left -= r; }
                        int o = 0; while (o < 4) { int r = is.read(cur, o, 4 - o); if (r < 0) return 0; o += r; }
                    }
                }
            }
        } catch (Throwable e) { return 0; }
        return (cur[0] & 0xff) | (cur[1] & 0xff) << 8 | (cur[2] & 0xff) << 16 | (cur[3] & 0xff) << 24;
    }

    // v1.30：3级头/3级甲/风衣编号优先用包扫描的结果（文件名里带 Helmet03/Armor03/Trench 等），扫不到时用按规律推的编号：
    // 已知 30304000003 = 2级头，30304000004 能刷；30305000002 = 1级甲，30305000004 刷不出（可放=-6）→ 甲按顺序试
    // 实测：30304000004 是 2 级头（耐久 150）；30305000003 是 3 级甲（耐久 250）
    static String helmet3() { String k = okId.get("3级头"); return k != null ? k : "30304000005"; }
    static java.util.List<String> helmetIds() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String k = okId.get("3级头"); if (k != null) l.add(k);
        for (String x : new String[]{"30304000005", "30304000006", "30304000007", "30304000008", "30304000009"}) if (!l.contains(x)) l.add(x);
        return l;
    }
    static String armor3() { String k = okId.get("3级甲"); return k != null ? k : PakScan.armor3 != null ? PakScan.armor3 : "30305000003"; }
    static int coatPos = -1; static String coatNow = "";
    // v1.40：外套 30315、连帽 30316、连体 30323，再加男款专属分类 30115/30116/30123（女款发型在 30213、男款在 30113，同一规律）
    static java.util.List<String> allCoats() {
        java.util.List<String> l = new java.util.ArrayList<>();
        int[][] r = {{30315, 80}, {30316, 20}, {30323, 10}, {30115, 40}, {30116, 20}, {30123, 10}, {30215, 20}};
        for (int[] x : r) for (int k = 1; k <= x[1]; k++) l.add(String.format(java.util.Locale.US, "%d0%05d", x[0], k));
        return l;
    }
    static void giveCoat(final Activity a, int dir) { coatStep(a, dir, 0); }
    static void coatStep(final Activity a, final int dir, final int tries) {
        if (giveSt == 1 || giveSt == 3) { toast(a, "先在补丁更新里点「更新补丁」"); return; }
        java.util.List<String> l = allCoats();
        coatPos = ((coatPos + dir) % l.size() + l.size()) % l.size();
        final String id = l.get(coatPos);
        give(a, id, 1);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            String d = dbgText();
            LogCap.note("刷物品诊断 衣服 " + id + ": " + d);
            boolean ok = dbg != null && dbg.getInt(0) == 0x5a02 && dbg.getInt(28) != 0;
            if (ok) { coatNow = id; if (coatLbl != null) coatLbl.setText("刚穿上：" + id); return; }
            if (tries < 30) coatStep(a, dir, tries + 1); else toast(a, "连续 30 个编号都没有衣服");
        }, 600);
    }
    static TextView coatLbl;

    static final String[] MED = {"40100000001", "40100000002", "40100000003", "40101000001", "40101000002", "40101000003"};
    // v1.38：按分类选枪（编号 = 游戏枚举顺序，已实测 S12K=20103000003、平底锅=20301000002、92式=20201000005）；刷枪顺带对应子弹
    // 子弹编号 2060000000N，N = EItemAmmo：1=5.56 2=7.62 3=9mm 4=.45 5=12号 6=.300马格南 7=弩箭 8=5.8
    static final String[][] CATS = {
        {"步枪", "M416:20101000001:1", "AKM:20101000002:2", "M16A4:20101000003:1", "SCAR-L:20101000004:1", "GROZA:20101000005:2"},
        {"机枪", "M249:20102000001:1", "大盘鸡 DP-28:20102000002:2"},   // v1.77：DP-28 编号是猜的（武器表里只看到 M249），没有会提示没刷出来
        {"冲锋枪", "UMP9:20105000001:3", "UZI:20105000002:3", "Vector:20105000003:3", "汤姆逊:20105000004:4"},
        {"喷子", "S1897:20103000001:5", "S686:20103000002:5", "S12K:20103000003:5"},
        {"狙击枪", "98K:20104000001:2", "AWM:20104000002:6", "M24:20104000004:2"},
        {"连狙", "SKS:20104000003:2", "MK14:20104000005:2", "Mini14:20104000006:1", "VSS:20104000007:3", "QBU88:20104000008:1"},
        {"手枪", "P92:20201000001:3", "P1911:20201000002:4", "R1895:20201000003:2", "P18C:20201000004:3", "92式:20201000005:8", "R45:20201000006:4", "短管喷子:20201000007:5"},
        // v1.47：近战并进特殊武器
        {"特殊武器", "十字弩:20106000001:7", "平底锅:20301000002:0", "砍刀:20301000003:0", "镰刀:20301000004:0", "撬棍:20301000005:0"},
        {"投掷物", "手雷:20401000001:0", "震爆弹:20401000002:0", "烟雾弹:20401000003:0", "燃烧瓶:20401000004:0", "苹果:20401000005:0"},
        {"子弹", "5.56:20600000001:0", "7.62:20600000002:0", "9mm:20600000003:0", ".45:20600000004:0", "12号:20600000005:0", ".300马格南:20600000006:0", "弩箭:20600000007:0", "5.8:20600000008:0", "信号弹:20600000009:0"},
    };
    // v1.39：信号枪编号不在武器枚举里 → 按顺序试候选，刷出来后从游戏日志读武器名（BP_NZWeap_xxx / WeaponBase/Wxxx_名字）
    static final String[] FLARE = {"20201000008", "20201000009", "20201000010", "20201000011", "20202000001", "20203000001",
            "20106000002", "20106000003", "20107000001", "20108000001", "20204000001", "20205000001", "20206000001", "20701000001"};
    static int flareAt = 0;
    static void flare(final Activity a) {
        String k = okId.get("信号枪");
        if (k != null) { give(a, k, 1); give(a, "20600000009", 5); after(a, "信号枪"); return; }
        flareStep(a, 0);
    }
    static void flareStep(final Activity a, final int tries) {
        if (giveSt == 1 || giveSt == 3) { toast(a, "先在补丁更新里点「更新补丁」"); return; }
        if (flareAt >= FLARE.length) { flareAt = 0; toast(a, "候选编号都试完了，把日志发我"); return; }
        final String id = FLARE[flareAt++];
        final long t0 = System.currentTimeMillis();
        give(a, id, 1);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            boolean ok = dbg != null && dbg.getInt(0) == 0x5a02 && dbg.getInt(28) != 0;
            String w = Con.lastWeapAt >= t0 ? Con.lastWeap : "";
            LogCap.note("找信号枪 " + id + " 刷出=" + ok + " 武器=" + w + " " + dbgText());
            if (!ok) { if (tries < 20) flareStep(a, tries + 1); return; }
            // v1.81：游戏日志里常常读不到武器名（用户实测刷出来了却提示不是）→ 读不到名字也算成功
            if (w.isEmpty() || w.toLowerCase(java.util.Locale.ROOT).contains("flare")) {
                okId.put("信号枪", id); give(a, "20600000009", 5); toast(a, "信号枪已刷出（编号 " + id + "）");
            } else toast(a, "刷出的是 " + (w.isEmpty() ? "别的东西" : w) + "，再点一次信号枪");
        }, 1500);
    }
    static String fmt(float f) { return f == (int) f ? String.valueOf((int) f) : String.format(java.util.Locale.US, "%.1f", f); }
    // v1.36 平板视角：控制器自带的 FOV 命令（0 = 恢复默认）
    static int fov = 0;
    static void setFov(Activity a, int f) {
        fov = f;
        boolean ok = Con.exec("FOV " + f);
        LogCap.note("平板视角 FOV " + f + " 发送=" + ok);
        if (!ok) toast(a, "视野没改成");
    }

    // ================= 控件 =================
    static TextView label(Activity a, ViewGroup p, String s, float size, boolean bold, int color) {
        TextView t = new TextView(a);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        p.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }
    static LinearLayout row(Activity a, ViewGroup p) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        p.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }
    /** 小标题：左边一条短竖线 + 白字 */
    static void head(Activity a, Ui u, ViewGroup p, String s) {
        TextView t = label(a, p, s, 11f, false, SUBT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.03f);
        t.setPadding(u.dp(2), u.dp(8), 0, u.dp(3)); t.setTextSize(10.5f);
    }
    /** 卡片容器 */
    static LinearLayout card(Activity a, Ui u, ViewGroup p) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(CARD, u.dp(12)));
        c.setPadding(u.dp(10), u.dp(4), u.dp(10), u.dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = u.dp(4);
        p.addView(c, lp);
        return c;
    }
    /** 普通按钮（卡片样式，按下有缩放反馈） */
    static TextView btn(Activity a, Ui u, LinearLayout row, String text, final Runnable r) {
        TextView b = new TextView(a);
        b.setText(text); b.setTextColor(TXT); b.setTextSize(12f);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(false); b.setMaxLines(2);
        b.setPadding(u.dp(3), u.dp(7), u.dp(3), u.dp(7));
        b.setBackground(boxLine(CARD2, STROKE, u.dp(8), Math.max(1, u.dp(1))));
        press(b);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(u.dp(2), u.dp(2), u.dp(2), u.dp(2));
        row.addView(b, lp);
        return b;
    }
    static TextView primary(Activity a, Ui u, LinearLayout row, String text, Runnable r) {
        TextView b = btn(a, u, row, text, r); b.setBackground(grad(u.dp(8))); b.setTextColor(ON_TXT); return b;
    }
    static void press(final View v) {
        v.setOnTouchListener((x, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
            else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            return false;
        });
    }
    /** 选择标签（选中=渐变+✓），用来做「游戏模式」「角色」「视野」这类多选一/多选 */
    static TextView pick(Activity a, Ui u, LinearLayout row, String text, boolean on, Runnable r) {
        TextView b = new TextView(a);
        b.setText(text); b.setTextColor(on ? ON_TXT : SUBT); b.setTextSize(12);
        if (on) b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setPadding(u.dp(3), u.dp(6), u.dp(3), u.dp(6));
        b.setBackground(on ? grad(u.dp(8)) : boxLine(CARD2, STROKE, u.dp(8), Math.max(1, u.dp(1))));
        if (!on) b.setTextColor(TXT);
        press(b);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(u.dp(3), u.dp(3), u.dp(3), u.dp(3));
        row.addView(b, lp);
        return b;
    }

    /** 滑动开关 */
    static class Sw extends View {
        boolean on; float pos; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Sw(Context c, boolean on) { super(c); this.on = on; pos = on ? 1 : 0; }
        void set(boolean v) {
            on = v;
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(pos, v ? 1 : 0).setDuration(160);
            va.addUpdateListener(an -> { pos = (Float) an.getAnimatedValue(); invalidate(); });
            va.start();
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            p.setColor(TRACK); c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
            if (pos > 0) { p.setColor(GREEN); p.setAlpha((int) (255 * pos)); c.drawRoundRect(new RectF(0, 0, w, h), r, r, p); p.setAlpha(255); }
            float cx = r + (w - 2 * r) * pos;
            p.setColor(0x22000000); c.drawCircle(cx, r + 1f, r - 2f, p);
            p.setColor(0xFFFFFFFF); c.drawCircle(cx, r, r - 2.5f, p);
        }
    }
    interface BCb { String on(boolean v); }
    /** 一行：名字 + 说明 + 开关（整行可点） */
    static void switchRow(final Activity a, final Ui u, ViewGroup p, String name, String sub, boolean on, final BCb cb) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, u.dp(6), 0, u.dp(6));
        LinearLayout t = new LinearLayout(a); t.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(a); n.setText(name); n.setTextColor(TXT); n.setTextSize(12.5f);
        t.addView(n);
        final TextView s = new TextView(a); s.setText(sub); s.setTextColor(SUBT); s.setTextSize(10);
        if (sub != null && !sub.isEmpty()) t.addView(s);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        final Sw sw = new Sw(a, on);
        r.addView(sw, new LinearLayout.LayoutParams(u.dp(34), u.dp(19)));
        r.setOnClickListener(v -> {
            boolean want = !sw.on;
            String res = cb.on(want);
            if (res == null || "ok".equals(res)) { sw.set(want); refreshAny(); }
            else toast(a, "没改成：" + res);
        });
        p.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    interface FCb { void on(float f); }
    /** 细滑条：左边 0、右边最大值，拖动实时生效；倍数滑条小于 1 显示「关」 */
    static class Slider extends View {
        final float min, max, step; float val; final FCb cb; final boolean times;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG), ep = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean drag; final float dens;
        Slider(Context c, float min, float max, float step, float v, boolean times, FCb cb) {
            super(c); this.min = min; this.max = max; this.step = step; this.val = v; this.times = times; this.cb = cb;
            dens = c.getResources().getDisplayMetrics().density;
            tp.setColor(TXT); tp.setTextSize(11.5f * dens); tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); tp.setTextAlign(Paint.Align.RIGHT);
            ep.setColor(SUBT); ep.setTextSize(9 * dens);
        }
        float labelW() { return 40 * dens; }
        float x0() { return 8 * dens; }
        float x1() { return getWidth() - labelW() - 8 * dens; }
        String text() { return times && val < 1f ? "关" : (times ? "×" : "") + fmt(val); }
        @Override protected void onDraw(Canvas c) {
            float cy = getHeight() * 0.42f, th = 2.5f * dens, x0 = x0(), x1 = x1();
            float cx = x0 + (x1 - x0) * (val - min) / (max - min);
            p.setColor(TRACK); c.drawRoundRect(new RectF(x0, cy - th / 2, x1, cy + th / 2), th, th, p);
            p.setColor(ACC); if (cx > x0) c.drawRoundRect(new RectF(x0, cy - th / 2, cx, cy + th / 2), th, th, p);
            float r = (drag ? 8 : 6.5f) * dens;
            if (drag) { p.setColor(0x14000000); c.drawCircle(cx, cy, r + 6 * dens, p); }
            p.setColor(0x33000000); c.drawCircle(cx, cy + dens, r + 0.5f * dens, p); p.setColor(0xFFFFFFFF); c.drawCircle(cx, cy, r, p);
            float ey = getHeight() - 2 * dens;                                   // 两端刻度：0 和 最大
            ep.setTextAlign(Paint.Align.LEFT); c.drawText(fmt(min), x0 - 2 * dens, ey, ep);
            ep.setTextAlign(Paint.Align.RIGHT); c.drawText(fmt(max), x1 + 2 * dens, ey, ep);
            c.drawText(text(), getWidth(), cy + 4.5f * dens, tp);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: drag = true; getParent().requestDisallowInterceptTouchEvent(true); // 接着按 MOVE 处理
                case MotionEvent.ACTION_MOVE: {
                    float fr = Math.max(0, Math.min(1, (e.getX() - x0()) / (x1() - x0())));
                    float v = Math.round((min + Math.round(fr * (max - min) / step) * step) * 100) / 100f;
                    if (v != val) { val = v; if (cb != null) cb.on(v); }
                    invalidate(); return true;
                }
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    drag = false; getParent().requestDisallowInterceptTouchEvent(false); invalidate(); return true;
            }
            return true;
        }
    }
    /** 一行：名字 + 说明，下面是滑条 */
    static void slider(final Activity a, final Ui u, ViewGroup root, String name, String sub, float min, float max, float step, float v, boolean times, final FCb cb) {
        LinearLayout c = card(a, u, root);
        LinearLayout tr = row(a, c);
        tr.setPadding(0, u.dp(6), 0, 0);
        TextView n = new TextView(a); n.setText(name); n.setTextColor(TXT); n.setTextSize(13);
        tr.addView(n, new LinearLayout.LayoutParams(-2, -2));
        TextView s = new TextView(a); s.setText("  " + sub); s.setTextColor(SUBT); s.setTextSize(10);
        tr.addView(s, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(new Slider(a, min, max, step, v, times, cb), new LinearLayout.LayoutParams(-1, u.dp(40)));
    }

    // ================= 刷枪 =================
    /** 枪类（会带配件）：步枪/机枪 201 01-02、霰弹 03、狙击/连狙 04、冲锋 05 */
    static boolean takesAttachments(String id) { return !attFor(id).isEmpty(); }
    /** v3.01 用户：刷枪给的配件里没有快扩，而且别的枪给的也是步枪配件。
     *  根因：以前所有枪都放同一套固定 12 件 ATT（步枪消音/补偿、20504000001 = 步枪「扩容」不是快扩）。
     *  编号规律（推断，未实测）：205 类 5 组的个数和 libUE4 里 ENZWeapAdapterItemType 枚举按声明顺序的分组个数完全一致
     *  （枪口 12、握把 6、瞄具 9、弹匣 12、枪托 5），所以 205XX000NNN 的 NNN = 该组内枚举顺序：
     *  20501：1 步枪消音 2 步枪消焰 3 步枪补偿 4 冲锋消音 5 冲锋消焰 6 冲锋补偿 7 狙击消音 8 狙击消焰 9 狙击补偿 10 手枪消音 11 收束器 12 鸭嘴
     *  20502：1 垂直 2 直角 3 拇指 4 半截 5 轻型握把 6 箭袋
     *  20503：1 红点 2 全息 3 二倍 4 三倍 5 四倍 6 六倍 7 八倍 8 VSS 四倍 9 Dot
     *  20504：步枪 1 扩容 2 快速 3 快扩；冲锋 4/5/6；狙击 7/8/9；手枪 10/11/12
     *  20505：1 战术枪托 2 UZI 枪托 3 托腮板 4 子弹袋 5 98K 子弹袋
     *  每把枪按它能装的位置给一套，弹匣只给快扩。 */
    static java.util.List<String> attFor(String id) {
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        String[] AR_MZ = {"20501000001", "20501000002", "20501000003"}, SMG_MZ = {"20501000004", "20501000005", "20501000006"}, SR_MZ = {"20501000007", "20501000008", "20501000009"};
        String[] GRIP = {"20502000001", "20502000002", "20502000003", "20502000004", "20502000005"};
        String[] SC_CLOSE = {"20503000001", "20503000002"}, SC_MID = {"20503000001", "20503000002", "20503000003", "20503000004", "20503000005"},
                 SC_AR = {"20503000001", "20503000002", "20503000003", "20503000004", "20503000005", "20503000006"},
                 SC_SR = {"20503000001", "20503000002", "20503000003", "20503000004", "20503000005", "20503000006", "20503000007"};
        String AR_MAG = "20504000003", SMG_MAG = "20504000006", SR_MAG = "20504000009", TAC = "20505000001", CHEEK = "20505000003";
        switch (id) {
            case "20101000001": case "20101000004": add(l, AR_MZ, GRIP, SC_AR); l.add(AR_MAG); if (id.endsWith("1")) l.add(TAC); break;   // M416（枪托）、SCAR-L
            case "20101000003": add(l, AR_MZ, SC_AR); l.add(AR_MAG); l.add(TAC); break;   // M16A4 无握把
            case "20101000002": add(l, AR_MZ, SC_AR); l.add(AR_MAG); break;               // AKM
            case "20101000005": l.add("20501000001"); add(l, SC_AR); l.add(AR_MAG); break; // GROZA 只能消音
            case "20102000001": case "20102000002": add(l, SC_AR); break;                // 机枪只给瞄具
            case "20105000001": add(l, SMG_MZ, GRIP, SC_MID); l.add(SMG_MAG); break;      // UMP9
            case "20105000002": add(l, SMG_MZ); l.add(SMG_MAG); l.add("20505000002"); break;   // UZI：UZI 枪托，没瞄具
            case "20105000003": add(l, SMG_MZ, SC_MID); l.add(SMG_MAG); l.add(TAC); break;     // Vector
            case "20105000004": l.add("20501000004"); l.add("20502000001"); add(l, SC_CLOSE); l.add(SMG_MAG); break;   // 汤姆逊
            case "20103000001": case "20103000002": l.add("20501000011"); l.add("20505000004"); break;   // S1897、S686：收束器 + 子弹袋
            case "20103000003": l.add("20501000011"); add(l, SC_CLOSE); l.add(AR_MAG); break;   // S12K
            case "20104000001": add(l, SR_MZ, SC_SR); l.add(CHEEK); l.add("20505000005"); break;   // 98K
            case "20104000002": case "20104000004": add(l, SR_MZ, SC_SR); l.add(SR_MAG); l.add(CHEEK); break;   // AWM、M24
            case "20104000003": add(l, SR_MZ, GRIP, SC_SR); l.add(SR_MAG); l.add(CHEEK); break;   // SKS
            case "20104000005": case "20104000008": add(l, SR_MZ, SC_SR); l.add(SR_MAG); l.add(CHEEK); break;   // MK14、QBU88
            case "20104000006": add(l, SR_MZ, SC_SR); l.add(SR_MAG); break;   // Mini14
            case "20104000007": l.add(SR_MAG); break;   // VSS：自带消音和瞄具
            case "20106000001": l.add("20502000006"); add(l, SC_CLOSE); break;   // 十字弩：箭袋
        }
        return l;
    }
    static void add(java.util.List<String> l, String[]... gs) { for (String[] g : gs) for (String x : g) l.add(x); }
    /** 这一局已经刷过的配件（换局清空）：包里已经有的配件不再重复刷 */
    static final java.util.Set<String> givenAtt = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    static void newMatch() { givenAtt.clear(); givenGear.clear(); HostMatch.newMatch(); }
    /** v2.02 用户：开局刷了 3 级套，跳伞后再刷一次直接闪退（栈溢出）。头/甲/包身上已有时再塞一件 = 游戏要换装、把旧的扔地上，这条路会崩。
     *  同一局每个部位只刷一次（刷在地上不受限）。 */
    static final java.util.Set<String> givenGear = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    static boolean gearOnce(Activity a, String slot) {
        if (onGround || remote()) return true;
        if (givenGear.add(slot)) return true;
        toast(a, "这局已经刷过" + slot + "，身上已有再刷会闪退");
        return false;
    }
    /** 刷一把枪：枪 + 对应子弹 + 这一局还没刷过的全套配件 */
    static void giveGun(Activity a, String name, String id, String ammo) {
        give(a, id, 1);
        if (!"0".equals(ammo)) give(a, "2060000000" + ammo, "7".equals(ammo) ? 30 : 60);
        if (takesAttachments(id)) {
            // v2.70 用户：刷的枪没有配件。以前配件直接塞进背包（游戏不会自动装到枪上，日志里没有 Attaching adapter）。
            // 现在放到脚下：走游戏自己的拾取（自动拾取/点拾取），捡起来时游戏会自动装到能装的枪上。每把枪都给一套。
            final java.util.List<String> set = attFor(id);
            LogCap.note("刷枪配件 " + name + "：" + set);
            if (onGround || remote()) { for (String at : set) give(a, at, 1); }
            else new Thread(() -> {
                try {
                    Thread.sleep(700);
                    int bad = 0;
                    for (String at : set) { String e = Veh.ground(Long.parseLong(at), 1); if (e != null) bad++; }
                    if (bad > 0) toastAny("配件有 " + bad + " 件没放到脚下");
                    else toastAny("配件放在脚下了，捡起来会自动装到枪上");
                } catch (Throwable e) { toastAny("配件没放到脚下：" + e); }
            }, "zf-att").start();
        }
        after(a, name);
    }
    static void gunGrid(final Activity a, final Ui u, final LinearLayout root, final int c) {
        java.util.List<String[]> items = new java.util.ArrayList<>();
        boolean special = "特殊武器".equals(CATS[c][0]);
        if (special) items.add(new String[]{"信号枪", "flare", "0"});
        for (int j = 1; j < CATS[c].length; j++) items.add(CATS[c][j].split(":"));
        if (special) items.add(new String[]{"信号弹×5", "20600000009", "0"});
        LinearLayout r = null; int n = 0;
        for (final String[] it : items) {
            if (n % 4 == 0) r = row(a, root);
            btn(a, u, r, it[0], () -> {
                if ("flare".equals(it[1])) flare(a);
                else if (it[1].startsWith("204")) give(a, it[1], 3, it[0]);
                else if (it[1].startsWith("206")) give(a, it[1], it[1].endsWith("9") ? 5 : it[1].endsWith("7") ? 30 : 150, it[0]);
                else giveGun(a, it[0], it[1], it[2]);
            });
            n++;
        }
        while (n % 4 != 0) { r.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1)); n++; }
    }

    // ================= 页面 =================
    static void fill(final Activity a, final Ui u, final LinearLayout root) {
        root.removeAllViews();
        if (!Con.inMatch) { lobbyPage(a, u, root); return; }
        switch (tab) {
            case 1: vehPage(a, u, root); return;
            case 2: botPage(a, u, root); return;
            case 3: weaponPage(a, u, root); return;
            case 4: gearPage(a, u, root); return;
            case 5: settingsPage(a, u, root); return;
            case 6: lanPage(a, u, root); return;
            case 7: clothesPage(a, u, root); return;
            case 8: funcPage(a, u, root); return;
            case 9: specialPage(a, u, root); return;
            case 10: ammoPage(a, u, root); return;
            case 11: propPage(a, u, root); return;
            default:
                mainPage(a, u, root);
        }
    }
    static void giveHint(Activity a, LinearLayout root) {
        if (remote()) { label(a, root, "点了让服务端刷在你脚下，几秒后出现，自己捡", 10.5f, false, ORANGE); return; }
        String gt = giveText(a); if (giveSt != 2 && !gt.isEmpty()) label(a, root, gt, 10.5f, false, ORANGE);
    }
    static void result(Activity a, String r) { if (!"ok".equals(r)) toast(a, "没改成：" + r); refreshAny(); }
    static void note(Activity a, Ui u, ViewGroup p, String s) {
        label(a, p, s, 10.5f, false, SUBT).setPadding(u.dp(3), u.dp(2), 0, u.dp(2));
    }

    /** v2.05 大厅：上面「联机版 / 单机版」切换。联机版 = 开房 / 加入（不刷人机）；单机版 = 游戏模式，点游戏的匹配赛开始（默认刷人机）。
     *  服务端自动开（不显示开关）；日志放最下面。 */
    static int lobbyTab = 0;
    static void lobbyPage(final Activity a, final Ui u, final LinearLayout root) {
        // v2.54 用户：「我都不知道更新了没」→ 大厅菜单最上面一行显示更新状态，有新版本就给「重启更新」
        if (NetCore.readyVer > ZInit.CORE_VER) {
            LinearLayout ur = row(a, root);
            primary(a, u, ur, "有新版本，点这里重启更新", () -> NetCore.restart(a));
        } else if (Ed.ED == 1 ? (NetCore.status.startsWith("检查更新失败") || NetCore.status.startsWith("发现新版本")) : !NetCore.status.isEmpty()) note(a, u, root, Ed.ED == 1 ? NetCore.status : "更新：" + NetCore.status);
        // v2.13：分版本，不再有联机/单机切换
        lobbyTab = Ed.single() ? 1 : 0;
        UpdUi.probe();   // v3.14：找游戏自己的更新页蓝图（日志 upd-*）
        if (lobbyTab == 0) {
          if (Ed.ED == 1) {
            LinearLayout hc = card(a, u, root);
            kv(a, u, hc, "本机 IP", Lan.ip());
            LinearLayout r = row(a, hc); r.setPadding(0, u.dp(4), 0, u.dp(6));
            fillBtn(a, u, r, "老海岛", () -> { Training.off(); Duel.off(); toast(a, Lan.host("/Game/Maps/ErangelMobile_Runtime/BR-Erangel_Mobile_Main")); });
            // v3.03 用户：新海岛默认就是战术模式（v3.02 实测战术模式能用：原生道具栏、道具都能用）
            fillBtn(a, u, r, "新海岛", () -> { Training.off(); Duel.off(); toast(a, ModeProbe.host("Props", "战术模式", "/Game/Maps/ErangeMobileRenew_Runtime/BR-ErangelRenew_Mobile_Main")); });
            fillBtn(a, u, r, "雨林", () -> { Training.off(); Duel.off(); toast(a, Lan.host("/Game/Maps/SavageMobile_4x4_Runtime/BR-Savage_Mobile_Main")); });
            // v3.16 用户：两人单挑（20 个头赢、选枪、5 秒复活、3 秒无敌），雨林 / 海岛各一个，打出生岛
            LinearLayout dr = row(a, hc); dr.setPadding(0, 0, 0, u.dp(6));
            fillBtn(a, u, dr, "单挑·海岛", () -> toast(a, Duel.open(false)));
            fillBtn(a, u, dr, "单挑·雨林", () -> toast(a, Duel.open(true)));
            // v3.21 用户：不要让用户记录任何地点 → 去掉「记录单挑点 / 清除单挑点」，场地程序自己选（DuelArena.java）
            note(a, u, hc, Duel.state + (Duel.respawnState.isEmpty() ? "" : "；" + Duel.respawnState));
            // v3.33 用户：训练营回来了（真地图找到了），服务端也能开房进训练岛
            LinearLayout tr2 = row(a, hc); tr2.setPadding(0, 0, 0, u.dp(6));
            fillBtn(a, u, tr2, "训练营", () -> { Training.off(); Duel.off(); toast(a, Lan.host(Con.MAP_TRAINING + "?game=" + Con.GM_TRAINING)); });
            fillBtn(a, u, tr2, "训练场", () -> { Training.off(); Duel.off(); toast(a, Lan.host(Con.MAP_TRAINING)); });
            // v3.15 用户：删掉训练营和决胜五军（Training/ModeProbe.BFA 代码留着，不再有入口）
            // v3.03 用户：再找找别的地图 → 服务端进大厅自动扫名字表里的 /Game/Maps/…_Main（ModeProbe.maps），除了上面三张，扫到的都列出来（战术模式开）
            java.util.List<String> extra = ModeProbe.maps;
            LinearLayout xr = null; int xn = 0;
            for (final String mp : extra) {
                if (mp.contains("BR-Erangel_Mobile_Main") || mp.contains("BR-ErangelRenew_Mobile_Main") || mp.contains("BR-Savage_Mobile_Main")) continue;
                if (xn % 3 == 0) xr = row(a, hc);
                String lb = mp.substring(mp.lastIndexOf('/') + 1).replace("BR-", "").replace("_Mobile_Main", "").replace("_Main", "");
                fillBtn(a, u, xr, lb + "（试）", () -> toast(a, ModeProbe.host("Props", "战术模式", mp)));
                xn++;
            }
            if (xr != null) while (xn % 3 != 0) { xr.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1)); xn++; }
            switchRow(a, u, hc, "去掉房主人物", "开局把房主自己的人物隐藏、关碰撞、冻住（看不到、打不到、挡不住人，也不和任何人一队），房主只当服务器", HostMatch.hideHost, v -> { HostMatch.hideHost = v; return "ok"; });
            // v3.21 用户：删掉「出生岛空投」（更新后出生岛根本没有空投，没用）。Box 开局放堆/排车不再跑（Box.on=false，HostMatch 不再调 Box.tick）
            note(a, u, hc, HostMatch.state);
            note(a, u, hc, ModeProbe.state);
            LinearLayout mr = row(a, root);
            // v2.22：房主也能选模式。房主自己没网络连接，游戏不让他上飞机；「不刷毒对局」= 大家都不上飞机，一起在出生岛开打
            pick(a, u, mr, "正常对局", !Mods.zoneOff(), () -> { toast(a, Mods.setNormal()); refreshAny(); });
            pick(a, u, mr, "不刷毒对局", Mods.zoneOff(), () -> { toast(a, Mods.setNoZone()); refreshAny(); });
            // v3.21 用户：「地图物资/门」暂时没有用 → 删掉开关（Loot 默认关、不再有入口）
          } else {
            head(a, u, root, "加入房间");
            joinCard(a, u, root);
            note(a, u, root, "服务端先进图，再点加入。");
          }
            if (!"还没试".equals(Lan.state)) note(a, u, root, Lan.state);
        } else {
            // v3.21 用户：最上面一排「单机 | 联机」分段选择（不弹窗，记住选择）。单机 = 游戏模式 + 自动刷人机，点「匹配赛」本机开局；联机 = 加入艾挽服务器 + 自己填地址
            LinearLayout seg = row(a, root); seg.setPadding(0, u.dp(2), 0, u.dp(6));
            final boolean net = netTab();
            pick(a, u, seg, "单机", !net, () -> setNetTab(false));
            pick(a, u, seg, "联机", net, () -> setNetTab(true));
            if (!net) {
                head(a, u, root, "游戏模式");
                LinearLayout fl = row(a, root);
                // v2.17 用户：只要两个模式；「不刷毒对局」= 不上飞机（直接在出生岛开打）+ 不刷圈。去掉清空仓库、日志
                pick(a, u, fl, "正常对局", !Mods.zoneOff(), () -> result(a, Mods.setNormal()));
                pick(a, u, fl, "不刷毒对局", Mods.zoneOff(), () -> result(a, Mods.setNoZone()));
                note(a, u, root, "选好后点游戏里的「匹配赛」开始。");
                // v3.33 用户：训练营资源找到了（训练岛 BR-TrainingIsland_Mobile_Main）→ 单机直接进
                head(a, u, root, "诊断");   // v3.38 用户：训练营/训练场用游戏自己的入口，菜单里去掉
                LinearLayout dgx = row(a, root);   // v3.36 用户：单机也要能导出诊断（训练场闪退要看）
                btn(a, u, dgx, "导出诊断", () -> new Thread(() -> toastAny(AvatarLog.export(a))).start());
                // v2.56 用户：自动刷人机开关放大厅
                switchRow(a, u, root, "自动刷人机", "关掉后整局不自动刷人机（手动刷不受影响），下次也记住", Bots.want, v -> { Bots.setWant(a, v); Bots.state = v ? "人机：自动刷新已开" : "人机：自动刷新已关"; return "ok"; });
            } else {
                head(a, u, root, "加入房间");
                joinCard(a, u, root);
                note(a, u, root, "服务端先进图，再点加入。");
                if (!"还没试".equals(Lan.state)) note(a, u, root, Lan.state);
            }
        }
        if (Ed.ED != 1) note(a, u, root, Server.state);
    }
    /** 加入房间卡片：「加入艾挽服务器」+ 自己填地址（v3.10；v3.21 单机版「联机」页也用） */
    static void joinCard(final Activity a, final Ui u, final LinearLayout root) {
        LinearLayout jc = card(a, u, root);
        // v3.10 用户：默认一个地址 frp-put.com:47937，按钮显示「加入艾挽服务器」；右边留一个能自己改的地址
        if (ipBuf == null) ipBuf = Lan.lastIp != null && !AIWAN.equals(Lan.lastIp) ? Lan.lastIp : "";
        LinearLayout jr = row(a, jc); jr.setPadding(0, u.dp(8), 0, u.dp(8)); jr.setGravity(Gravity.CENTER_VERTICAL);
        fillBtn(a, u, jr, "加入艾挽服务器", () -> toast(a, Lan.join(AIWAN)));
        LinearLayout rc = new LinearLayout(a); rc.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rcl = new LinearLayout.LayoutParams(0, -2, 1); rcl.leftMargin = u.dp(8);
        jr.addView(rc, rcl);
        final EditText ipEt = new EditText(a);
        ipEt.setText(ipBuf); ipEt.setHint("自己填地址"); ipEt.setTextColor(TXT); ipEt.setHintTextColor(SUBT); ipEt.setTextSize(13);
        ipEt.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); ipEt.setSingleLine(true);
        ipEt.setGravity(Gravity.CENTER);
        ipEt.setBackground(boxLine(CARD2, LINE, u.dp(8), 1)); ipEt.setPadding(u.dp(8), u.dp(6), u.dp(8), u.dp(6));
        rc.addView(ipEt, new LinearLayout.LayoutParams(-1, -2));
        ipEt.setOnTouchListener((v, e) -> { if (e.getActionMasked() == MotionEvent.ACTION_DOWN) focusable(v, true); return false; });
        ipEt.setOnFocusChangeListener((v, f) -> { if (!f) focusable(v, false); });
        ipEt.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence x, int st, int c, int af) {}
            public void onTextChanged(CharSequence x, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) { ipBuf = e.toString().trim().replaceAll("^[a-zA-Z]+://", "").replaceAll("[\\s/]+$", ""); }
        });
        ipEt.setOnEditorActionListener((v, act, ev) -> { focusable(ipEt, false); ipEt.clearFocus(); return false; });
        LinearLayout er = row(a, rc); er.setPadding(0, u.dp(4), 0, 0);
        btn(a, u, er, "加入这个地址", () -> { focusable(ipEt, false); ipEt.clearFocus(); if (ipBuf.isEmpty()) { toast(a, "先填地址"); return; } toast(a, Lan.join(ipBuf)); });
    }
    /** 最下面：日志 */
    static void logRow(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "日志");
        LinearLayout dg = row(a, root);
        btn(a, u, dg, "导出诊断", () -> new Thread(() -> toastAny(AvatarLog.export(a))).start());
        btn(a, u, dg, "查剩余人数", () -> new Thread(() -> toastAny(Alive.probe())).start());   // v2.23
        note(a, u, root, Server.state);
    }
    /** 对局里的「联机」页：房主 / 客户端都有 */
    static void lanPage(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "联机");
        LinearLayout c = card(a, u, root);
        kv(a, u, c, "身份", Lan.roleText());
        kv(a, u, c, "本机 IP", Lan.ip());
        if (Lan.role == 2 && Lan.lastIp != null) kv(a, u, c, "服务端", Lan.lastIp);
        note(a, u, root, Lan.role == 2 ? "你是加入的玩家：物资箱和车开局由服务端放在你身边；要别的东西让服务端刷，会刷在你脚下。" : "你是服务端：别人连你的 IP（UDP 7777）。服务端这边的刷物资、刷车照常能用。");
        note(a, u, root, Lan.state);
        if (Lan.role == 1) { note(a, u, root, Crew.state); }
        // v2.26：联机版从游戏设置里退不出去 / 退了加入不进去 → 这里直接回大厅
        LinearLayout xr = row(a, root);
        fillBtn(a, u, xr, Lan.role == 1 ? "关房间，回大厅" : "退出房间，回大厅", () -> toast(a, Lan.leave()));
        logRow(a, u, root);
    }
    /** iOS 分段控件里的一个按钮 */
    static void segBtn(Activity a, Ui u, LinearLayout seg, String text, boolean on, Runnable r) {
        TextView b = new TextView(a);
        b.setText(text); b.setTextSize(12.5f); b.setGravity(Gravity.CENTER);
        b.setTextColor(TXT); b.setTypeface(Typeface.create(on ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        b.setPadding(0, u.dp(7), 0, u.dp(7));
        if (on) { b.setBackground(grad(u.dp(8))); b.setTextColor(ON_TXT); }
        b.setOnClickListener(v -> r.run());
        seg.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    /** 实心蓝按钮 */
    static String ipBuf;
    static final String AIWAN = "frp-put.com:47937";
    static boolean ipOk(String ip) {
        if (!ip.matches("\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?")) return false;
        for (String o : ip.split(":")[0].split("\\.")) if (Integer.parseInt(o) > 255) return false;
        return true;
    }
    static TextView fillBtn(Activity a, Ui u, LinearLayout row, String text, Runnable r) {
        TextView b = btn(a, u, row, text, r); b.setBackground(grad(u.dp(8))); b.setTextColor(ON_TXT); return b;
    }
    /** 卡片里的一行：左名字，右灰字 */
    static void kv(Activity a, Ui u, LinearLayout card, String k, String v) {
        LinearLayout r = new LinearLayout(a); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, u.dp(9), 0, u.dp(9));
        TextView kt = new TextView(a); kt.setText(k); kt.setTextColor(TXT); kt.setTextSize(13);
        TextView vt = new TextView(a); vt.setText(v); vt.setTextColor(SUBT); vt.setTextSize(13); vt.setGravity(Gravity.END);
        r.addView(kt, new LinearLayout.LayoutParams(-2, -2));
        r.addView(vt, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }
    /** v1.85 人机：100 人局开关 + 难度（不记忆，每次启动回到 开 / 普通） */
    static void botRows(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "人机难度");
        LinearLayout d = row(a, root);
        pick(a, u, d, "简单", Bots.diff == 1, () -> { Bots.diff = 1; refreshAny(); });
        pick(a, u, d, "普通", Bots.diff == 2, () -> { Bots.diff = 2; refreshAny(); });
        pick(a, u, d, "困难", Bots.diff == 3, () -> { Bots.diff = 3; refreshAny(); });
    }
    static String coatText() { return Mods.coatOn ? "绝地风衣：已设为初始衣服，大厅里和出生时都穿着" : "绝地风衣：大厅加载好后自动穿上，大厅会刷新一次"; }
    static void gfxRow(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "画质");
        LinearLayout r = row(a, root);
        pick(a, u, r, "流畅（帧率最高）", !Con.hiGfx, () -> { Con.setGfx(false); refreshAny(); });
        pick(a, u, r, "最高画质", Con.hiGfx, () -> { Con.setGfx(true); refreshAny(); });
    }

    /** 常用：最上面无敌和传送，然后枪，最下面加速/高跳（刷车、刷人机在「刷车人机」页） */
    static void mainPage(final Activity a, final Ui u, final LinearLayout root) {
        if (Lan.role == 2 || Ed.ED == 2) { clientMain(a, u, root); return; }
        head(a, u, root, "保命");
        LinearLayout oc = card(a, u, root);
        switchRow(a, u, oc, "无敌", "不会掉血", Con.godOn, v -> { cmd(a, "EnableCheats"); cmd(a, "God"); Con.godToggled(); return "ok"; });
        if (Ed.ED == 1 || Lan.role == 1) {
            head(a, u, root, "单挑");
            LinearLayout dq = row(a, root);
            btn(a, u, dq, "再开一把", () -> toast(a, Duel.rematch()));   // v3.20：同一张图马上重开（比分清零、清墙清枪、回到点上）
            note(a, u, root, Duel.state + (Duel.respawnState.isEmpty() ? "" : "；" + Duel.respawnState));
        }
        if (Ed.ED == 1 || Lan.role == 1) switchRow(a, u, oc, "全员无敌", "房间里所有人都不掉血（服务端这边算伤害）", Con.allGod, v -> Con.setAllGod(v));
        // v3.25 用户：「传送到地图中间」「恢复键位」删掉（有点地图标点传送了；恢复键位没用了）
        if (Ed.ED == 1) { onGround = true; note(a, u, root, "服务端：刷的东西都放在脚下，自己捡。"); }
        else {
        head(a, u, root, "刷物资放在");
        LinearLayout gr = row(a, root);
        pick(a, u, gr, "身上（背包）", !onGround, () -> { onGround = false; refreshAny(); });
        pick(a, u, gr, "地上（脚下）", onGround, () -> { onGround = true; refreshAny(); });
        }
        head(a, u, root, "枪");
        LinearLayout gc = card(a, u, root);
        switchRow(a, u, gc, "无后座", "开枪时枪口不往上跳", Mods.noRecoil, v -> { Mods.setGun(v, Mods.noShake, Mods.infAmmo); return "ok"; });
        switchRow(a, u, gc, "聚点", "子弹不散开，打得更准", Mods.noShake, v -> { Mods.setGun(Mods.noRecoil, v, Mods.infAmmo); return "ok"; });
        switchRow(a, u, gc, "无限子弹", "子弹一直是 999 发", Mods.infAmmo, v -> { Mods.setGun(Mods.noRecoil, Mods.noShake, v); return "ok"; });
    }
    /** v2.19「功能」页：加速、高跳；「动作放慢」默认折叠，点标题展开 */
    static boolean slowOpen;
    static void funcPage(final Activity a, final Ui u, final LinearLayout root) {
        boolean cl = Lan.role == 2 || Ed.ED == 2;
        head(a, u, root, "跑跳");
        if (Ed.ED == 2 && Lan.role == 2) {
            // v2.92 用户：客户端也要加速 → 拉条发给服务端，服务端只给你一个人改，再同步回来
            slider(a, u, root, "加速", "发给服务端，只改你自己；几秒后生效。现在：" + Mods.hostSyncText(), 0f, 67f, 1f, Give.mySpeed == 1f ? 0f : Give.mySpeed, true, v -> Give.askMove(v, Give.myJump));
            slider(a, u, root, "高跳", "发给服务端，只改你自己", 0f, 67f, 1f, Give.myJump == 1f ? 0f : Give.myJump, true, v -> Give.askMove(Give.mySpeed, v));
        } else if (Ed.ED == 2) {
            // v2.32 客户端：不再自己拉，跟房主的数
            LinearLayout cc = card(a, u, root);
            note(a, u, cc, "加速、高跳跟服务端走，服务端拉多少你就是多少。现在：" + Mods.hostSyncText());
        } else {
        slider(a, u, root, "加速", cl ? "要和服务端拉到同一个数才不被拉回去" : Ed.ED == 1 ? "服务端拉的数会自动同步给加入的玩家" : "跑得更快，拖到最左边是关（最大 67 倍；游泳、翻墙时自动恢复正常）", 0f, 67f, 1f, Mods.speed == 1f ? 0f : Mods.speed, true, v -> Mods.setMove(v, Mods.jump));
        slider(a, u, root, "高跳", cl ? "要和服务端拉到同一个数" : Ed.ED == 1 ? "服务端拉的数会自动同步给加入的玩家" : "跳得更高，拖到最左边是关（最大 67 倍）", 0f, 67f, 1f, Mods.jump == 1f ? 0f : Mods.jump, true, v -> Mods.setMove(Mods.speed, v));
        }
        if (Ed.ED != 2 && Lan.role != 2) {   // v2.73 用户：参考弹射板做「飞天」，高度可调
            // v3.03 用户：飞天去掉（客户端本来就没有）。传送落地还用 Fly.softLand
            head(a, u, root, "传送");
            switchRow(a, u, root, "点地图标点自动传送", Ed.ED == 1 || Lan.role == 1 ? "标点后你先传过去慢慢落地，落地后加入的人全部传到你身边" : "在大地图上标点后，自动传到那个点上空 200 米，再慢慢落下来。现在：" + MapMark.state, MapMark.autoTp, v -> { MapMark.save(v); MapMark.tpNow = v; MapMark.start(); return "ok"; });
            if (Ed.ED == 0 && Lan.role == 0) {
                head(a, u, root, "单机");
                switchRow(a, u, root, "单机按联机方式进图", "让游戏当成联机（你的手机当房主）。已知问题：下车后人物会消失，默认关；下一局生效", Lan.fakeOnline, v -> { Lan.fakeOnline = v; return "ok"; });
            }
        }
        LinearLayout fc = card(a, u, root);
        LinearLayout fr = row(a, fc); fr.setPadding(0, u.dp(9), 0, u.dp(9)); fr.setGravity(Gravity.CENTER_VERTICAL);
        TextView fn = new TextView(a); fn.setText("动作放慢"); fn.setTextColor(TXT); fn.setTextSize(13);
        fr.addView(fn, new LinearLayout.LayoutParams(-2, -2));
        TextView fs = new TextView(a); fs.setText("  加速时人物动作 " + fmt(Mods.slowMo)); fs.setTextColor(SUBT); fs.setTextSize(10);
        fr.addView(fs, new LinearLayout.LayoutParams(0, -2, 1));
        TextView fa = new TextView(a); fa.setText(slowOpen ? "收起 ▲" : "展开 ▼"); fa.setTextColor(ACC); fa.setTextSize(12);
        fr.addView(fa, new LinearLayout.LayoutParams(-2, -2));
        fr.setOnClickListener(v -> { slowOpen = !slowOpen; refreshAny(); });
        if (slowOpen) slider(a, u, root, "加速时的动作", "越小动作越慢，1 是正常", 0.3f, 1f, 0.05f, Mods.slowMo, false, v -> Mods.slowMo = v);
    }

    /** v1.93 刷车页：车按钮直接显示（编号写死，不用读车表），点了刷在面前 */
    static void vehPage(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "载具");
        java.util.ArrayList<Veh.Group> vg = Veh.groups;
        LinearLayout vr = null;
        for (int i = 0; i < vg.size(); i++) {
            if (i % 3 == 0) vr = row(a, root);
            final int gi = i;
            btn(a, u, vr, vg.get(i).label, () -> { if (remote()) Give.askCar(gi, vg.get(gi).label); else Veh.spawnGroup(gi); });
        }
        if (Veh.state.contains("没反应") || Veh.state.contains("出错") || Veh.state.contains("没有")) note(a, u, root, Veh.state);
    }
    /** v1.93 人机页 */
    static void botPage(final Activity a, final Ui u, final LinearLayout root) {
        // v2.19 用户：去掉难度；刷人机分两种 —— 固定人机（站着不动当靶子）、自由人机（会跑会打）
        switchRow(a, u, root, "自动刷人机", "关掉后整局不会自动刷人机（手动刷不受影响），下次也记住", Bots.want, v -> { Bots.setWant(a, v); if (!v) Bots.state = "人机：自动刷新已关"; return "ok"; });
        head(a, u, root, "刷人机（面前 8 米）");
        LinearLayout br = row(a, root);
        btn(a, u, br, "固定人机", () -> Veh.spawnBot(true));
        btn(a, u, br, "自由人机", () -> Veh.spawnBot(false));
        switchRow(a, u, root, "固定人机带枪", "关掉就刷空手的固定人机", Bots.gunFixed, v -> { Bots.gunFixed = v; return "ok"; });
        switchRow(a, u, root, "自由人机带枪", "关掉就刷空手的自由人机", Bots.gunFree, v -> { Bots.gunFree = v; return "ok"; });
        note(a, u, root, "带枪的人机会随机配枪、头甲；都会随机衣服和名字。");
        note(a, u, root, Bots.state);
    }

    static void weaponPage(final Activity a, final Ui u, final LinearLayout root) {
        HorizontalScrollView hs = new HorizontalScrollView(a); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout cr = new LinearLayout(a); cr.setOrientation(LinearLayout.HORIZONTAL);
        if (weapCat >= GUN_CATS) weapCat = 0;
        for (int k = 0; k < GUN_CATS; k++) {
            final int idx = k;
            TextView t = new TextView(a);
            t.setText(CATS[k][0]); t.setTextSize(11.5f);
            t.setTextColor(k == weapCat ? ON_TXT : SUBT);
            t.setBackground(k == weapCat ? grad(u.dp(14)) : boxLine(0, LINE, u.dp(14), 1));
            t.setPadding(u.dp(11), u.dp(5), u.dp(11), u.dp(5));
            t.setOnClickListener(v -> { weapCat = idx; fill(a, u, root); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = u.dp(6);
            cr.addView(t, lp);
        }
        hs.addView(cr);
        root.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        head(a, u, root, "点一下放进背包（带子弹，配件放在脚下）");
        gunGrid(a, u, root, weapCat);
        giveHint(a, root);
    }
    static final int GUN_CATS = 7;   // CATS 前 7 类是枪
    static int catIdx(String n) { for (int k = 0; k < CATS.length; k++) if (n.equals(CATS[k][0])) return k; return -1; }
    /** v2.61 特殊武器 + 投掷物一页 */
    static void specialPage(final Activity a, final Ui u, final LinearLayout root) {
        giveHint(a, root);
        head(a, u, root, "特殊武器 / 近战");
        gunGrid(a, u, root, catIdx("特殊武器"));
        head(a, u, root, "投掷物（每次 3 个）");
        gunGrid(a, u, root, catIdx("投掷物"));
    }
    /** v2.61 子弹一页 */
    static void ammoPage(final Activity a, final Ui u, final LinearLayout root) {
        giveHint(a, root);
        head(a, u, root, "子弹");
        gunGrid(a, u, root, catIdx("子弹"));
        if (Ed.ED != 2 && Lan.role != 2) {
            LinearLayout gc = card(a, u, root);
            switchRow(a, u, gc, "无限子弹", "子弹一直是 999 发", Mods.infAmmo, v -> { Mods.setGun(Mods.noRecoil, Mods.noShake, v); return "ok"; });
        }
    }
    /** v2.61 道具页：全部道具（道具键在对局里右边） */
    static void propPage(final Activity a, final Ui u, final LinearLayout root) {
        if (Ed.ED == 2 && !remote()) { note(a, u, root, "加入别人的房间后才能刷（服务端代刷在你脚下）。"); return; }
        giveHint(a, root);
        allItems(a, u, root);
    }

    static void gearPage(final Activity a, final Ui u, final LinearLayout root) {
        giveHint(a, root);
        head(a, u, root, "一键");
        LinearLayout g0 = row(a, root);
        primary(a, u, g0, "一键三件套（3级头 + 3级甲 + 3级包）", () -> {
            boolean h = gearOnce(a, "头"), r = gearOnce(a, "甲"), b = gearOnce(a, "包");
            if (h) give(a, helmet3(), 1); if (r) give(a, armor3(), 1); if (b) give(a, "30307000006", 1);
            if (h || r || b) after(a, "三件套");
        });
        head(a, u, root, "单件");
        LinearLayout g4 = row(a, root);
        btn(a, u, g4, "3级头", () -> { if (gearOnce(a, "头")) giveTry(a, helmetIds(), 0, "3级头"); });
        btn(a, u, g4, "3级甲", () -> { if (gearOnce(a, "甲")) giveTry(a, armorIds(), 0, "3级甲"); });
        btn(a, u, g4, "3级包", () -> { if (gearOnce(a, "包")) give(a, "30307000006", 1, "背包"); });
        LinearLayout g5 = row(a, root);
        btn(a, u, g5, "吉利服", () -> ghillie(a));
        head(a, u, root, "其他");
        LinearLayout g3 = row(a, root);
        btn(a, u, g3, "全套药品", () -> { for (String m : MED) give(a, m, 5); after(a, "全套药品"); });
        btn(a, u, g3, "手雷×3", () -> give(a, "20401000001", 3, "手雷"));
        btn(a, u, g3, "全套配件", () -> allAtt(a));
        // v3.03：油桶键去掉（编号认不出名字，按不出来）；战术模式开局服务端每样战术道具发 999（HostMatch），油桶类道具在里面
        if (Ed.ED != 2) note(a, u, root, "战术道具在「道具」页；拿出/放下用对局里的「道具」键。");
    }

    // ---------------- v2.51 用道具：草丛/盾/雷这类功能道具，经典模式界面没有道具栏选不中，这里替游戏「拿出」「放下」 ----------------
    static void useProps(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "用道具（草丛/盾/雷）");
        LinearLayout r0 = row(a, root);
        btn(a, u, r0, "查背包里的道具", () -> new Thread(() -> { String r = Prop.scanUse(); if (!"ok".equals(r)) a.runOnUiThread(() -> toast(a, r)); refreshAny(); }, "zf-use").start());
        btn(a, u, r0, "放下 / 使用", () -> new Thread(() -> {
            final String r = Prop.place();   // v2.98 游戏原生放下（读条、扣数量、生成都由游戏做）
            a.runOnUiThread(() -> toast(a, "ok".equals(r) ? "放下中（游戏读条）" : r));
            try { Thread.sleep(1500); } catch (Throwable t) {}
            Prop.scanUse(); refreshAny();
        }, "zf-use").start());
        java.util.ArrayList<Prop.It> l = Prop.useList;
        LinearLayout ir = null; int j = 0;
        for (final Prop.It it : l) {
            if (j++ % 2 == 0) ir = row(a, root);
            btn(a, u, ir, "拿出 " + Prop.nice(it.cls) + (it.sel ? "（已拿出）" : ""), () -> new Thread(() -> {
                final String r = Prop.select(it);
                a.runOnUiThread(() -> toast(a, "ok".equals(r) ? "已拿出，对好位置再点「放下 / 使用」" : r));
                try { Thread.sleep(500); } catch (Throwable t) {}
                Prop.scanUse(); refreshAny();
            }, "zf-use").start());
        }
        note(a, u, root, (Prop.useState.isEmpty() ? "先点「查背包里的道具」。" : Prop.useState)
            + (Prop.lastUseLog.isEmpty() ? "" : "\n最近游戏日志：" + (Prop.lastUseLog.length() > 120 ? Prop.lastUseLog.substring(Prop.lastUseLog.length() - 120) : Prop.lastUseLog)));
    }

    // ---------------- v2.49 全部道具：游戏物品表里所有能刷的（不含衣服），按分类，点一下刷一个 ----------------
    static final String[][] ITEM_CAT = {
        {"20101", "步枪"}, {"20102", "机枪"}, {"20103", "喷子"}, {"20104", "狙击枪"}, {"20105", "冲锋枪"}, {"20106", "特殊武器"},
        {"202", "手枪"}, {"203", "近战"}, {"204", "投掷物"}, {"205", "配件"}, {"206", "子弹"},
        {"30304", "头盔"}, {"30305", "防弹衣"}, {"30307", "背包"}, {"401", "药品/饮料"}, {"40201", "战术道具"}, {"402", "道具"}};
    /** v3.25 用户：道具名字全改中文。小分类（5 位）→ 中文 */
    static final String[][] SUB_CAT = {
        {"20201", "手枪"}, {"20301", "近战武器"}, {"20401", "投掷物"}, {"20501", "枪口"}, {"20502", "握把"}, {"20503", "瞄准镜"},
        {"20504", "弹匣"}, {"20505", "枪托"}, {"20600", "子弹"}, {"20601", "子弹"}, {"40100", "回血药品"}, {"40101", "能量药品"}, {"40200", "道具"}};
    static String itemCat(long cat) {
        String c = String.valueOf(cat);
        for (String[] x : SUB_CAT) if (c.equals(x[0])) return x[1];
        for (String[] x : ITEM_CAT) if (c.equals(x[0])) return x[1];
        for (String[] x : ITEM_CAT) if (x[0].length() == 3 && c.startsWith(x[0])) return x[1] + " " + c.substring(3);
        return "其他 " + c;
    }
    /** v3.25：英文类名片段 → 中文（找不到就「分类 序号」，不再显示英文） */
    static final String[][] CN = {
        {"suppress", "消音器"}, {"silenc", "消音器"}, {"ompensat", "补偿器"}, {"flashhid", "消焰器"}, {"flash_h", "消焰器"}, {"choke", "收束器"}, {"hoke", "收束器"}, {"duckbill", "鸭嘴枪口"},
        {"extended", "扩容弹匣"}, {"quickdraw", "快速弹匣"}, {"quick", "快速弹匣"}, {"extquick", "快速扩容弹匣"},
        {"tactical", "战术枪托"}, {"stockuzi", "UZI 枪托"}, {"cheek", "托腮板"}, {"bulletlo", "子弹袋"}, {"bulletloop", "子弹袋"},
        {"angled", "直角握把"}, {"vertical", "垂直握把"}, {"halfgrip", "半截式握把"}, {"thumb", "拇指握把"}, {"lightgrip", "轻型握把"}, {"laser", "激光瞄准器"},
        {"reddot", "红点瞄准镜"}, {"holo", "全息瞄准镜"}, {"2x", "2 倍镜"}, {"3x", "3 倍镜"}, {"4x", "4 倍镜"}, {"6x", "6 倍镜"}, {"8x", "8 倍镜"}, {"15x", "15 倍镜"},
        {"crossbow", "十字弩"}, {"bolt", "弩箭"}, {"arrow", "弩箭"},
        {"grenade", "手雷"}, {"frag", "手雷"}, {"smoke", "烟雾弹"}, {"molotov", "燃烧瓶"}, {"burn", "燃烧瓶"}, {"stun", "震爆弹"}, {"flashbang", "震爆弹"},
        {"bandage", "绷带"}, {"firstaid", "急救包"}, {"first", "急救包"}, {"medkit", "医疗箱"}, {"medic", "医疗箱"}, {"drink", "能量饮料"}, {"energy", "能量饮料"},
        {"painkill", "止痛药"}, {"pill", "止痛药"}, {"adrenal", "肾上腺素"}, {"syringe", "肾上腺素"},
        {"pan", "平底锅"}, {"machete", "砍刀"}, {"crowbar", "撬棍"}, {"sickle", "镰刀"},
        {"helmet", "头盔"}, {"armor", "防弹衣"}, {"vest", "防弹衣"}, {"bag", "背包"}, {"backpack", "背包"},
        {"ghillie", "吉利服"}, {"doll", "人偶"}, {"dummy", "人偶"}, {"chara", "人偶"}, {"shrub", "草丛"}, {"camou", "伪装岩"}, {"rock", "伪装岩"}, {"shield", "护盾"}, {"shiled", "护盾"},
        {"jetpack", "喷气背包"}, {"flare", "信号枪"}, {"lift", "升降台"}, {"trampol", "弹射板"}, {"detect", "探测器"}, {"drone", "无人机"}, {"radar", "雷达"}, {"mine", "地雷"}, {"trap", "陷阱"},
        {"gas", "汽油桶"}, {"fuel", "汽油桶"}, {"556", "5.56 子弹"}, {"762", "7.62 子弹"}, {"9mm", "9mm 子弹"}, {"45acp", ".45 子弹"}, {"acp", ".45 子弹"}, {"12g", "12 号霰弹"}, {"gauge", "12 号霰弹"}, {"300", ".300 马格南"}, {"magnum", ".300 马格南"}};
    /** 枪名（M416 这种）在配件分类里 → 「M416 专用」+分类；枪本身保留型号（M416、AKM 本来就这么叫） */
    static String cnName(long id, String raw, String cat, int idx) {
        String c3 = String.valueOf(id / 1000000);
        boolean att = c3.startsWith("205") || c3.startsWith("206");
        if (raw != null && !raw.isEmpty()) {
            String r = raw.toLowerCase(java.util.Locale.US).replace(" ", "");
            for (String[] x : CN) if (r.contains(x[0])) {
                String z = x[1];
                if (att && (x[0].equals("crossbow"))) return "十字弩" + cat;
                if (c3.startsWith("20504") && !z.contains("弹匣") && !z.contains("弩")) return z + "弹匣";
                return z;
            }
            boolean gun = c3.startsWith("201") || c3.startsWith("202");
            if (att && raw.matches("[A-Za-z0-9\\-]{2,10}")) return raw.toUpperCase(java.util.Locale.US) + " 专用" + cat;
            if (gun && raw.matches("[A-Za-z0-9\\-\\.]{2,10}")) return raw;   // 枪的型号（M416、AKM）
        }
        return cat + " " + idx;
    }
    static boolean useful(long cat) {
        int c3 = (int) (cat / 100);
        return (c3 >= 201 && c3 <= 206) || cat == 30304 || cat == 30305 || cat == 30307 || c3 == 401 || c3 == 402;
    }
    static volatile java.util.TreeMap<Long, java.util.ArrayList<Long>> items;
    static volatile String itemState = "";
    static long itemCatSel = 0; static int itemPage = 0;
    static final java.util.Set<Long> itemBad = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    static final java.util.Map<Long, String> itemName = new java.util.concurrent.ConcurrentHashMap<>();
    static java.io.File badFile() { Activity a = Con.act; return a == null ? null : new java.io.File(a.getFilesDir(), "zf_items_bad.txt"); }
    static void saveBad() { try { java.io.File f = badFile(); if (f == null) return; StringBuilder b = new StringBuilder(); synchronized (itemBad) { for (long v : itemBad) b.append(v).append(' '); } java.nio.file.Files.write(f.toPath(), b.toString().trim().getBytes()); } catch (Throwable ignored) {} }
    static java.io.File nameFile() { Activity a = Con.act; return a == null ? null : new java.io.File(a.getFilesDir(), "zf_item_names.txt"); }
    static void saveNames() { try { java.io.File f = nameFile(); if (f == null) return; StringBuilder b = new StringBuilder(); for (java.util.Map.Entry<Long, String> e : itemName.entrySet()) b.append(e.getKey()).append('=').append(e.getValue().replace('\n', ' ')).append('\n'); java.nio.file.Files.write(f.toPath(), b.toString().getBytes("UTF-8")); } catch (Throwable ignored) {} }
    static void loadNames() { try { java.io.File f = nameFile(); if (f == null || !f.exists()) return; for (String l : new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").split("\n")) { int k = l.indexOf('='); if (k > 0) itemName.put(Long.parseLong(l.substring(0, k).trim()), l.substring(k + 1)); } } catch (Throwable ignored) {} }
    /** v3.00：v2.99 及以前「没反应 / 缺任何资源」都会永久删，配件（快扩）等被误删。第一次读时把不是功能道具（402）的删除记录清掉一次，原样记进诊断。 */
    static volatile String badState = "坏编号：没读";
    static void loadBad() {
        try {
            java.io.File f = badFile(); if (f == null || !f.exists()) { badState = "坏编号：没有文件"; return; }
            java.util.ArrayList<Long> all = new java.util.ArrayList<>();
            for (String x : new String(java.nio.file.Files.readAllBytes(f.toPath())).trim().split("[,\\s]+")) if (!x.isEmpty()) all.add(Long.parseLong(x));
            java.io.File mk = new java.io.File(f.getParentFile(), "zf_items_bad.v300");
            java.util.ArrayList<Long> cleared = new java.util.ArrayList<>();
            if (!mk.exists()) { for (long v : all) if (v / 100000000L != 402) cleared.add(v); all.removeAll(cleared); }
            itemBad.addAll(all);
            if (!mk.exists()) { saveBad(); java.nio.file.Files.write(mk.toPath(), cleared.toString().getBytes()); }
            java.io.File mk2 = new java.io.File(f.getParentFile(), "zf_items_bad.v325");   // v3.25：以前因缺模型删掉的战术道具（人偶等）放回来一次
            if (!mk2.exists()) { java.util.ArrayList<Long> c2 = new java.util.ArrayList<>(); for (long v : all) if (v / 100000000L == 402) c2.add(v); all.removeAll(c2); itemBad.removeAll(c2); saveBad(); java.nio.file.Files.write(mk2.toPath(), c2.toString().getBytes()); }
            StringBuilder b205 = new StringBuilder(); for (long v : all) if (v / 100000000L == 205) b205.append(v).append(' ');
            String old = mk.exists() ? new String(java.nio.file.Files.readAllBytes(mk.toPath())) : "";
            badState = "坏编号：" + all.size() + " 个，其中配件 " + (b205.length() == 0 ? "0" : b205.toString().trim()) + "；v3.00 清掉过的误删：" + (cleared.isEmpty() ? old : cleared.toString());
        } catch (Throwable e) { badState = "坏编号：读出错 " + e; }
    }
    /** 诊断：扫到的全部配件编号（205 类） */
    static String attText() {
        java.util.TreeMap<Long, java.util.ArrayList<Long>> m = items;
        StringBuilder b = new StringBuilder("配件编号（205）：");
        if (m == null) { b.append("还没读道具表（装备页「全部道具」读一次）"); }
        else { int n = 0; for (java.util.Map.Entry<Long, java.util.ArrayList<Long>> e : m.entrySet()) if (e.getKey() / 100 == 205) for (long v : e.getValue()) { b.append(v).append(itemName.containsKey(v) ? "=" + itemName.get(v) : "").append(' '); n++; } b.append("（共 ").append(n).append("）"); }
        return b.append("\n  ").append(badState).toString();
    }
    static java.io.File itemFile() { Activity a = Con.act; return a == null ? null : new java.io.File(a.getFilesDir(), "zf_items.txt"); }
    static void loadItems(boolean rescan) {
        if ("正在读…".equals(itemState)) return;
        itemState = "正在读…"; refreshAny();
        new Thread(() -> {
            try {
                java.util.TreeSet<Long> ids = new java.util.TreeSet<>();
                java.io.File f = itemFile();
                if (!rescan && f != null && f.exists())
                    for (String x : new String(java.nio.file.Files.readAllBytes(f.toPath())).trim().split("[,\\s]+")) if (!x.isEmpty()) ids.add(Long.parseLong(x));
                if (ids.isEmpty()) {
                    long t0 = System.currentTimeMillis();
                    ids.addAll(Server.scanItems());
                    StringBuilder b = new StringBuilder(); for (long v : ids) b.append(v).append(' ');
                    if (f != null && !ids.isEmpty()) java.nio.file.Files.write(f.toPath(), b.toString().trim().getBytes());
                    LogCap.note("全部道具：扫到 " + ids.size() + " 个，" + (System.currentTimeMillis() - t0) + " ms");
                }
                loadBad(); loadNames(); ids.removeAll(itemBad);
                // v2.61 用户：整理掉没用的 → 只留武器/配件/子弹/头甲包/药品/道具，「其他」类（403 死亡盒子、货币、大厅物品等）都不显示
                ids.removeIf(v -> !useful(v / 1000000));
                java.util.TreeMap<Long, java.util.ArrayList<Long>> m = new java.util.TreeMap<>();
                for (long v : ids) m.computeIfAbsent(v / 1000000, k -> new java.util.ArrayList<>()).add(v);
                items = m; if (itemCatSel == 0 || !m.containsKey(itemCatSel)) itemCatSel = m.isEmpty() ? 0 : m.firstKey(); itemPage = 0;
                itemState = ids.isEmpty() ? "没读到道具编号（进对局后再读）" : "共 " + ids.size() + " 个";
            } catch (Throwable e) { itemState = "读道具出错 " + e; }
            refreshAny();
        }, "zf-items").start();
    }
    static void allItems(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "全部道具");
        java.util.TreeMap<Long, java.util.ArrayList<Long>> m = items;
        if (m == null && itemState.isEmpty()) { java.io.File f0 = itemFile(); if (f0 != null && f0.exists()) loadItems(false); }   // v2.54 记住的直接用，不用再点
        if (m == null) {
            LinearLayout r0 = row(a, root);
            primary(a, u, r0, "读出游戏里所有能刷的道具", () -> loadItems(false));
            if (!itemState.isEmpty()) note(a, u, root, itemState);
            return;
        }
        LinearLayout cr = null; int i = 0;
        if (Ed.ED == 1 && itemCatSel / 100 != 402) for (long c0 : m.keySet()) if (c0 / 100 == 402) { itemCatSel = c0; break; }
        for (final long c : m.keySet()) {
            if (Ed.ED == 1 && c / 100 != 402) continue;   // v3.10 服务端只留地图上没有的（战术道具）
            int cn = 0; for (long v : m.get(c)) if (!itemBad.contains(v)) cn++;
            if (cn == 0) continue;
            if (i++ % 4 == 0) cr = row(a, root);
            pick(a, u, cr, itemCat(c) + "（" + cn + "）", c == itemCatSel, () -> { itemCatSel = c; itemPage = 0; refreshAny(); });
        }
        java.util.ArrayList<Long> l0 = m.get(itemCatSel);
        if (l0 == null) return;
        // v2.53 用户：刷不出来的不要留着 → 刷失败的编号直接不显示（存 zf_items_bad.txt，「重新读」也不回来）
        java.util.ArrayList<Long> l = new java.util.ArrayList<>(); for (long v : l0) if (!itemBad.contains(v)) l.add(v);
        final int per = 16, pages = (l.size() + per - 1) / per;
        if (itemPage >= pages) itemPage = 0;
        LinearLayout ir = null;
        for (int k = itemPage * per, j = 0; k < Math.min(l.size(), (itemPage + 1) * per); k++, j++) {
            final long id = l.get(k);
            if (j % 4 == 0) ir = row(a, root);
            String lb = cnName(id, itemName.get(id), itemCat(itemCatSel), k + 1) + "\n" + id;
            btn(a, u, ir, lb, () -> giveItem(a, id));
        }
        LinearLayout pr = row(a, root);
        if (pages > 1) {
            btn(a, u, pr, "上一页", () -> { itemPage = (itemPage + pages - 1) % pages; refreshAny(); });
            btn(a, u, pr, (itemPage + 1) + " / " + pages, () -> {});
            btn(a, u, pr, "下一页", () -> { itemPage = (itemPage + 1) % pages; refreshAny(); });
        }
        btn(a, u, pr, "重新读", () -> { items = null; loadItems(true); });
        note(a, u, root, itemState + "。刷不出来的会自动删掉。");
        // v2.50：模型/特效文件不在手机上的道具，游戏日志里有加载失败的路径，这里直接列出来（单机没有导出）
        String ps = Prop.on ? (Prop.fallbackUsed() ? "地上看不见的道具：已用替身模型显示" : "地上看不见的道具：替身已装，等第一个地上模型加载") : "地上替身：" + (Prop.state.isEmpty() ? "进对局后自动装" : Prop.state);
        int mc = Prop.missCount();
        note(a, u, root, ps + (mc == 0 ? "。还没发现缺的资源。" : "。游戏缺的资源（最近 " + Math.min(6, mc) + " 条，共 " + mc + " 条）：" + Prop.recent(6)));
    }
    static void giveItem(final Activity a, final long id) {
        if (remote()) { int c3 = (int) (id / 100000000L); Give.ask(String.valueOf(id), c3 == 206 ? 30 : c3 == 204 || c3 == 401 ? 3 : 1); return; }
        if (giveSt == 1 || giveSt == 3) { toast(a, "先在补丁更新里点「更新补丁」"); return; }
        int cat3 = (int) (id / 100000000L);
        int n = cat3 == 206 ? 30 : cat3 == 204 || cat3 == 401 ? 3 : 1;
        final long t0 = System.currentTimeMillis();
        give(a, String.valueOf(id), n);
        if (onGround) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            boolean done = dbg != null && dbg.getInt(0) == 0x5a02, ok = done && dbg.getInt(28) != 0;
            LogCap.note("全部道具 " + id + ": " + dbgText());
            // v3.00：快扩刷不出的根因 —— 以前「补丁代码没被调用 / 走到一半」也当刷不出来永久删。现在只有补丁走完、游戏明确返回失败才删
            if (!done) { toast(a, "这次没反应（" + dbgText() + "），没删，再点一次"); return; }
            if (!ok) { itemBad.add(id); saveBad(); LogCap.note("删掉 " + id + "：" + dbgReason()); toast(a, "这个刷不出来（" + dbgReason() + "），已从列表删掉"); refreshAny(); return; }
            String w = Con.lastWeapAt >= t0 ? Con.lastWeap : "";
            if (!w.isEmpty()) { itemName.put(id, w.length() > 14 ? w.substring(0, 14) : w); saveNames(); refreshAny(); }
            else if (!itemName.containsKey(id)) new Thread(() -> { String nb = Prop.nameInBag(id); if (nb != null) { itemName.put(id, nb); saveNames(); refreshAny(); } }, "zf-name").start();   // v2.54
            // v2.56 用户：用不了的还在 → 刷出来但缺模型/资源的也删掉（地上只能显示替身箱子的那种）
            // v3.00：缺资源只对功能道具（402）算数；配件等缺个图标/特效也能用，以前被误删（快扩就是这样没的）
            // v3.25 用户：少了人偶 → 战术模式用游戏自己的道具栏，缺模型的道具也能用，不再因为「缺资源/没模型」删战术道具
        }, 1200);
    }

    /** v2.15 客户端版「常用」：只放本机生效的 —— 枪（无后座/聚点/无限子弹）和跑跳。刷东西、无敌、传送都是服务器说了算，客户端不放。 */
    static void clientMain(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "枪");
        LinearLayout gc = card(a, u, root);
        switchRow(a, u, gc, "无后座", "开枪时枪口不往上跳", Mods.noRecoil, v -> { Mods.setGun(v, Mods.noShake, Mods.infAmmo); return "ok"; });
        switchRow(a, u, gc, "防抖 / 聚点", "镜头不晃、子弹不散开", Mods.noShake, v -> { Mods.setGun(Mods.noRecoil, v, Mods.infAmmo); return "ok"; });
        note(a, u, root, "无限子弹要服务端开：服务端开了，房间里所有人都有。客户端自己改会一直换弹。");
    }

    /** v2.15「衣服」页（房主/单机）：最上面绝地风衣单独一个按钮；下面是从游戏内存里读出来的全部服装编号（Server.scanSkins，只读），
     *  按分类分组，名字用「分类 + 序号」（物品的中文名没有表可查）。读一次存 filesDir/zf_clothes.txt，下次直接用。 */
    static final String[][] CLOTH_CAT = {
        {"01", "裤子"}, {"02", "上衣"}, {"03", "鞋子"}, {"06", "手套"}, {"07", "背包外观"}, {"08", "面部/眼镜"}, {"09", "吉利服"},
        {"10", "短裤"}, {"11", "短袖"}, {"12", "帽子"}, {"13", "头饰"}, {"14", "外套"}, {"15", "外套"}, {"16", "长外套"}, {"17", "套装"},
        {"18", "风衣"}, {"19", "连衣/套装"}, {"20", "其他"}, {"21", "面罩"}, {"23", "饰品"}, {"24", "饰品"}, {"26", "其他"}, {"28", "其他"}};
    static String catName(long cat) {
        int big = (int) (cat / 100) % 1000, yy = (int) (cat % 100);
        String y = String.format(java.util.Locale.US, "%02d", yy), n = "分类 " + y;
        for (String[] c : CLOTH_CAT) if (c[0].equals(y)) { n = c[1]; break; }
        return n + (big == 301 ? "（男）" : big == 302 ? "（女）" : "");
    }
    static volatile java.util.TreeMap<Long, java.util.ArrayList<Long>> clothes;
    static volatile String clothState = "";
    static long clothCat = 0; static int clothPage = 0;
    static java.io.File clothFile() { Activity a = Con.act; return a == null ? null : new java.io.File(a.getFilesDir(), "zf_clothes.txt"); }
    static void loadClothes(boolean rescan) {
        if ("正在读…".equals(clothState)) return;
        clothState = "正在读…"; refreshAny();
        new Thread(() -> {
            try {
                java.util.TreeSet<Long> ids = new java.util.TreeSet<>();
                java.io.File f = clothFile();
                if (!rescan && f != null && f.exists())
                    for (String x : new String(java.nio.file.Files.readAllBytes(f.toPath())).trim().split("[,\\s]+")) if (!x.isEmpty()) ids.add(Long.parseLong(x));
                if (ids.isEmpty()) {
                    long t0 = System.currentTimeMillis();
                    ids.addAll(Server.scanSkins().keySet());
                    ids.remove(Long.parseLong(Mods.COAT));
                    StringBuilder b = new StringBuilder(); for (long v : ids) b.append(v).append(' ');
                    if (f != null && !ids.isEmpty()) java.nio.file.Files.write(f.toPath(), b.toString().trim().getBytes());
                    LogCap.note("衣服列表：扫到 " + ids.size() + " 件，" + (System.currentTimeMillis() - t0) + " ms");
                }
                loadBad(); loadNames(); ids.removeAll(itemBad);
                // v2.61 用户：整理掉没用的 → 只留武器/配件/子弹/头甲包/药品/道具，「其他」类（403 死亡盒子、货币、大厅物品等）都不显示
                ids.removeIf(v -> !useful(v / 1000000));
                java.util.TreeMap<Long, java.util.ArrayList<Long>> m = new java.util.TreeMap<>();
                for (long v : ids) m.computeIfAbsent(v / 1000000, k -> new java.util.ArrayList<>()).add(v);
                clothes = m; if (clothCat == 0 || !m.containsKey(clothCat)) clothCat = m.isEmpty() ? 0 : m.firstKey(); clothPage = 0;
                clothState = ids.isEmpty() ? "没读到衣服编号" : "共 " + ids.size() + " 件";
            } catch (Throwable e) { clothState = "读衣服出错 " + e; }
            refreshAny();
        }, "zf-cloth").start();
    }
    /** v2.37 用户：衣服页不用刷那么多皮肤，只要风衣和吉利服，排整齐。吉利服 7 个候选编号各一个按钮（刷在脚下看不出哪个成功，所以把编号写在按钮上）；保留按编号刷 */
    static void clothesPage(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "绝地风衣");
        LinearLayout fc = card(a, u, root);
        LinearLayout fr = row(a, fc);
        primary(a, u, fr, "刷绝地风衣", () -> give(a, Mods.COAT, 1, "绝地风衣"));
        label(a, fc, "编号 " + Mods.COAT, 11, false, SUBT).setPadding(u.dp(3), u.dp(2), 0, u.dp(4));
        head(a, u, root, "吉利服");
        LinearLayout gc = card(a, u, root);
        LinearLayout gr = row(a, gc);
        for (int i = 0; i < GHILLIE.length; i++) {
            final String id = GHILLIE[i]; final String lb = "吉利服 " + (i + 1);
            if (i == 0) primary(a, u, gr, lb, () -> { give(a, id, 1); toast(a, lb); });
            else btn(a, u, gr, lb, () -> { give(a, id, 1); toast(a, lb); });
        }
        if (Ed.ED == 1) note(a, u, root, "服务端：衣服刷在脚下，捡起来就穿上。");
        head(a, u, root, "按编号刷");
        LinearLayout ir = row(a, root);
        final EditText et = new EditText(a);
        et.setHint("11 位编号"); et.setTextColor(TXT); et.setHintTextColor(SUBT); et.setTextSize(13);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); et.setSingleLine(true);
        et.setBackground(boxLine(CARD2, LINE, u.dp(8), 1)); et.setPadding(u.dp(10), u.dp(7), u.dp(10), u.dp(7));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, -2, 2); elp.setMargins(u.dp(3), u.dp(3), u.dp(3), u.dp(3));
        ir.addView(et, elp);
        et.setOnTouchListener((v, e) -> { if (e.getActionMasked() == MotionEvent.ACTION_DOWN) focusable(v, true); return false; });
        et.setOnFocusChangeListener((v, f) -> { if (!f) focusable(v, false); });
        btn(a, u, ir, "刷出", () -> {
            String id = et.getText().toString().trim();
            if (!id.matches("\\d{11}")) { toast(a, "编号是 11 位数字"); return; }
            focusable(et, false); et.clearFocus();
            give(a, id, 1); toast(a, "已刷 编号 " + id);
        });
    }

    static void coatPage(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "绝地风衣");
        LinearLayout c = card(a, u, root);
        label(a, c, coatText(), 12, false, TXT).setPadding(0, u.dp(8), 0, u.dp(2));
        LinearLayout fr = row(a, c);
        primary(a, u, fr, "现在穿上", () -> give(a, Mods.COAT, 1, "绝地风衣"));
        head(a, u, root, "换别的衣服");
        LinearLayout cr = row(a, root);
        btn(a, u, cr, "上一件", () -> giveCoat(a, -1));
        btn(a, u, cr, "下一件", () -> giveCoat(a, 1));
        coatLbl = label(a, root, coatNow.isEmpty() ? "" : "刚穿上：" + coatNow, 11, false, SUBT);
        head(a, u, root, "按编号穿");
        LinearLayout ir = row(a, root);
        final EditText et = new EditText(a);
        et.setHint("11 位编号"); et.setTextColor(TXT); et.setHintTextColor(SUBT); et.setTextSize(13);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); et.setSingleLine(true);
        et.setBackground(boxLine(CARD2, LINE, u.dp(8), 1)); et.setPadding(u.dp(10), u.dp(7), u.dp(10), u.dp(7));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, -2, 2); elp.setMargins(u.dp(3), u.dp(3), u.dp(3), u.dp(3));
        ir.addView(et, elp);
        et.setOnTouchListener((v, e) -> { if (e.getActionMasked() == MotionEvent.ACTION_DOWN) focusable(v, true); return false; });
        et.setOnFocusChangeListener((v, f) -> { if (!f) focusable(v, false); });
        btn(a, u, ir, "穿上", () -> {
            String id = et.getText().toString().trim();
            if (!id.matches("\\d{11}")) { toast(a, "编号是 11 位数字"); return; }
            focusable(et, false); et.clearFocus();
            giveTry(a, java.util.Collections.singletonList(id), 0, "编号 " + id);
            coatNow = id; if (coatLbl != null) coatLbl.setText("刚穿上：" + id);
        });
    }

    static void settingsPage(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "画质");
        note(a, u, root, "在游戏设置里选画质，选了立即生效。" + Gfx.state);
        head(a, u, root, "视野");
        LinearLayout fv = row(a, root);
        for (final int f : new int[]{0, 90, 100, 110}) pick(a, u, fv, f == 0 ? "默认" : String.valueOf(f), fov == f, () -> { setFov(a, f); fill(a, u, root); });
        label(a, root, VER + " " + Ed.name(), 10, false, SUBT).setPadding(u.dp(3), u.dp(14), 0, 0);
    }

    // ---------------- v2.44 客户端包：加载页选「单机 / 联机」 ----------------
    static volatile TextView titleRef;
    static volatile View chooser; static volatile Activity chooserAct;
    /** v3.21 用户：单机版加入服务器后标题还是「单机版」。标题跟实际状态走：加入了别人的房间（Lan.role==2）= 联机版；
     *  在本机对局里 = 单机版；在大厅按菜单顶上的「单机 | 联机」选择。 */
    static String titleText() {
        if (Ed.single()) {
            if (Lan.role == 2) return "全军出击联机版";
            if (Con.inMatch) return "全军出击单机版";
            return netTab() ? "全军出击联机版" : "全军出击单机版";
        }
        return Ed.ED == 1 ? "全军出击联机版 服务端" : "全军出击联机版 客户端";
    }
    /** v3.21 用户：单机/联机选择放在大厅菜单里（分段按钮），不要弹窗；记住（prefs lobby_net） */
    static volatile Boolean netTabV;
    static boolean netTab() {
        Boolean v = netTabV;
        if (v == null) { boolean x = false; try { Activity a = Con.act; if (a != null) x = a.getSharedPreferences("zframe", 0).getBoolean("lobby_net", false); } catch (Throwable ignored) {} netTabV = v = x; }
        return v;
    }
    static void setNetTab(boolean v) {
        netTabV = v;
        try { Activity a = Con.act; if (a != null) a.getSharedPreferences("zframe", 0).edit().putBoolean("lobby_net", v).apply(); } catch (Throwable ignored) {}
        LogCap.note("大厅菜单：选了「" + (v ? "联机" : "单机") + "」");
        refreshAny();
    }
    static void showChooser(final Activity a) {
        if (chooser != null) return;
        try {
            // v2.56 用户：照游戏自己的「提示」框做；自动刷人机开关挪到大厅；下载新版本时显示进度条
            View[] f = GameDlg.frame(a, "选择模式", null);
            LinearLayout body = (LinearLayout) f[1];
            final int last = Ed.ED;
            // v3.10 用户：把「单机 / 联机」选择删了，客户端包只有联机
            GameDlg.text(a, body, "全军出击联机版", 16, GameDlg.WHITE).setPadding(0, GameDlg.dp(a, 4), 0, 0);
            final TextView sub = GameDlg.text(a, body, "检查完更新自动进入", 11.5f, GameDlg.SUB);
            sub.setPadding(0, GameDlg.dp(a, 4), 0, 0);
            picked = true;   // 没有可选的了：查完更新就进
            final TextView up = GameDlg.text(a, body, "当前 " + VER + " · 正在检查更新…", 11.5f, GameDlg.SUB);
            up.setPadding(0, GameDlg.dp(a, 14), 0, 0);
            final View[] pb = GameDlg.progress(a, body);
            pb[0].setVisibility(View.GONE);
            final LinearLayout rr = GameDlg.row(a, body, 12);
            GameDlg.add(a, rr, GameDlg.button(a, "重启更新", true, () -> NetCore.restart(a)));
            rr.setVisibility(View.GONE);
            NetCore.listener = (msg, ready) -> a.runOnUiThread(() -> {
                up.setText("当前 " + VER + " · " + msg);
                int p = NetCore.pct;
                pb[0].setVisibility(ready || p >= 0 ? View.VISIBLE : View.GONE);
                GameDlg.setPct(pb, ready ? 100 : Math.max(p, 0));
                rr.setVisibility(ready ? View.VISIBLE : View.GONE);
                if (ready && !autoRestart) {   // v2.68 用户：别没查完就进大厅 → 下好了直接重启用新版
                    autoRestart = true;
                    sub.setText("新版本已下好，2 秒后自动重启更新…");
                    a.getWindow().getDecorView().postDelayed(() -> NetCore.restart(a), 2000);
                }
            });
            NetCore.check(a);
            { NetCore.L l = NetCore.listener; String st = NetCore.status; if (l != null && !st.isEmpty()) l.on(st, NetCore.readyVer > ZInit.CORE_VER); }   // v2.68：腾讯页时已经查完/下好的，框一出来就显示（下好了就自动重启）
            f[0].setBackgroundColor(0xF0000000);   // v2.68：盖住后面，查更新时停在这一页
            a.getWindowManager().addView(f[0], GameDlg.wl("zframe_mode"));
            chooser = f[0]; chooserAct = a; chooserShown = System.currentTimeMillis();
            LogCap.note("启动选择：已显示");
        } catch (Throwable t) { LogCap.note("启动选择：显示失败 " + t + "，按上次的"); Ed.choose(Ed.saved(), "显示失败"); }
    }
    /** v2.59：加载页选模式。和现在一样 → 关框；不一样 → 存下来重启游戏（钩子要在大厅代码跑之前装，只能重启） */
    static volatile long chooserTouched;
    static void pickMode(final Activity a, int m, TextView sub) {
        chooserTouched = System.currentTimeMillis();
        if (m == Ed.ED) {
            if (!NetCore.done && NetCore.readyVer <= ZInit.CORE_VER) { sub.setText("已选" + (m == 0 ? "单机" : "联机") + "，查完更新自动进入"); picked = true; return; }   // v2.68
            closeChooser(); return;
        }
        Ed.save(m);
        LogCap.note("启动选择：换成 " + (m == 0 ? "单机" : "联机") + "，重启");
        sub.setText("换成" + (m == 0 ? "单机" : "联机") + "，正在重启…");
        NetCore.restart(a);
    }
    static void closeChooser() {
        final Activity a = chooserAct; final View c = chooser;
        if (a == null || c == null) return;
        a.runOnUiThread(() -> { try { a.getWindowManager().removeViewImmediate(c); } catch (Throwable ignored) {} chooser = null; chooserAct = null; });
    }
    /** Con 看到游戏进加载页（Browse StartupMap）时调：弹选择框，20 秒没点就按现在的模式关掉 */
    static volatile boolean asked;
    static void loadingPage() {
        if (!Ed.canChoose() || asked) return;
        final Activity a = Con.act; if (a == null) return;
        asked = true;
        a.runOnUiThread(() -> {
            showChooser(a);
            final View c = chooser;
            if (c != null) holdTick(c);
        });
    }
    /** v2.68 用户：查更新的时候停在这一页，不要没查完就进大厅。以前 20 秒到点就关；
     *  现在要「查更新有结果」且「显示满 20 秒、20 秒内没点过」才关；查到新版本就自动重启（见 listener），不关框。 */
    static volatile boolean autoRestart, picked; static volatile long chooserShown;
    static void holdTick(final View c) {
        c.postDelayed(() -> {
            if (chooser != c) return;
            long now = System.currentTimeMillis();
            boolean ready = NetCore.readyVer > ZInit.CORE_VER;
            if (!ready && NetCore.done && (picked || (now - chooserShown > 20000 && now - chooserTouched > 20000))) { closeChooser(); return; }
            holdTick(c);
        }, 1000);
    }
    /** v2.68 服务端（用户：更新一直显示获取更新、没在腾讯页面弹）：游戏自己的加载页（StartupMap）上盖一层，
     *  查 GitHub 有没有新内核；最新/失败 → 1.5 秒后关；下好了 → 2 秒后自动重启用新版。最多停 40 秒。 */
    static volatile View upd;
    static final java.util.concurrent.atomic.AtomicBoolean updShown = new java.util.concurrent.atomic.AtomicBoolean();
    /** v3.22 用户：开机加载游戏那样的更新进度条，公告改成我们的（看得到每版更新了什么）。
     *  游戏自己的更新页（UNZUpdateUIView）在这个包里不会建出来（腾讯版本服务停了，v2.70 探测只有默认对象），
     *  所以在游戏加载页（StartupMap）上盖一层同样布局：上面「公告」= 仓库 core/notice.txt（拿不到就用包里自带的 NOTICE），
     *  下面进度条 = 我们从仓库下内核的进度。最新/失败 → 显示满 6 秒后关；下好了 → 2 秒后自动重启用新版。点一下就关，最多 40 秒。 */
    static final String NOTICE = "v3.41 更新内容\n" + "· 训练岛：传到出生点后挨个加载地形块（之前山是黑的、没建筑），留地形最多的那块；结果在进岛自动诊断里\n" + "\n" + "v3.40 更新内容\n" + "· 训练岛：游戏设置里的「退出」接上了，点了直接回大厅；进岛 50 秒自动传一份诊断\n" + "\n" + "v3.38 更新内容\n" + "· 菜单：大厅里固定在中上（收起），不能拖；去掉关闭按钮\n" + "· 菜单：进对局自动关，对局里不自动开，五指按屏幕才开；点标题收起，往上拖到顶隐藏\n" + "· 菜单「功能」里去掉训练营/训练场（用游戏自己的入口）\n" + "· 开机更新页：找游戏页面的过程显示在底部，导出诊断里也有\n" + "\n" + "v3.37 更新内容\n" + "· 开机更新：去掉「上次没进大厅这次就不开游戏更新页」的保险，每次都走游戏自己的更新页\n" + "· 训练营/训练场：进岛后主动让游戏加载训练岛的地块（之前只有海），再传到出生点\n" + "· 训练场：修加载时闪退（没有队伍时游戏读空指针）\n" + "\n" + "v3.36 更新内容\n" + "· 单机大厅菜单「训练」下面加回「导出诊断」（手动点才导出，自动上传，不存手机）\n" + "\n" + "v3.35 更新内容\n" + "· 开机更新：我们的进度和公告一直显示，直到游戏自己的进度条真接上才收起（之前游戏页只有背景、什么都看不到）\n" + "· 训练营/训练场：进岛后自动找出生点并把你传过去（之前掉在海里）；找不到会弹提示\n" + "\n" + "v3.34 更新内容\n" + "· 开机更新页：之前只有背景没有进度条（进度条那块在游戏里默认是隐藏的），现在自己把它显示出来；没接上会弹提示\n" + "\n" + "v3.33 更新内容\n" + "· 训练营回来了：找到了真地图（训练岛），大厅「单机」下新增「训练营」「训练场」两个按钮；游戏自己选图里的训练场也能进了；训练岛里不自动刷人机、不改模式\n" + "\n" + "v3.32 更新内容\n" + "· 单机「剩余」多出来的那个占位人物：进图后找到它本人并删掉（不是硬改数字），剩余只算你和人机\n" + "\n"
            + "v3.31 更新内容\n" + "· 单机默认不再伪装联机（之前会让下车后人物消失），回到真单机；菜单里「单机按联机方式进图」默认关\n" + "\n"
            + "v3.30 更新内容\n" + "· 修游戏自带更新页没开：上次更新下好自动重启（没进大厅）被当成「卡住」，下一次就把更新页关了；现在不会了\n" + "· 标点传送：开关会记住、默认开（之前重启就变回关）；控制器里没标点时从队伍信息读；菜单里显示标点和传送状态，传送失败会提示\n" + "\n"
            + "v3.29 更新内容\n" + "· 开机更新不再有时间上限：网络失败自动重试，直到查到结果或下完重启才进大厅\n" + "\n"
            + "v3.28 更新内容\n" + "· 开机必须走完更新进度条才进大厅：查到新版本就在更新页里下完、自动重启；已是最新才放行\n" + "\n"
            + "v3.27 更新内容\n" + "· 腾讯 logo 页不再弹更新框，更新只在游戏的更新页里走（下好了自动重启）；进大厅后才下好的，大厅里再提示\n" + "\n"
            + "v3.26 更新内容\n" + "· 修游戏自带更新页一直没开的原因：启动时拿不到存档目录，开关根本没装上（v3.24、v3.25 都是这样）。没开成时，加载页最下面会写原因\n" + "· 无敌改成直接关「能受伤」标记 + 补血，伪装联机下也生效（之前单机开无敌照样摔死）\n" + "· 标点传送：打开开关时已经有标点，马上传过去，不用等下局\n" + "\n"
            + "v3.25 更新内容\n" + "· 开机直接用游戏自己的更新页：进度条走的是我们仓库的下载进度，公告也换成我们的（建不出来才用我们自己的那层）\n" + "· 「传送到地图中间」「恢复键位」删掉\n" + "· 高跳最大 67 倍\n" + "· 道具名字改成中文（枪口/握把/瞄准镜/弹匣/枪托，消音器、扩容弹匣这些）\n" + "· 人偶等战术道具放回列表（以前因为缺模型被删了）\n" + "· 服务端点地图标点传送：你落地后，加入的人全部传到你身边\n" + "· 单机按联机方式进图（伪装联机），手雷抛物线、探测器视角、标点按联机那样走；功能页可关\n" + "\n"
            + "v3.24 更新内容\n"
            + "· 试着打开游戏自己的更新页（启动流程不再关掉更新步骤）；万一卡在更新页，下次启动会自动不开\n\n"
            + "v3.23 更新内容\n"
            + "· 开机显示更新进度条和公告（公告就是每版更新了什么）\n\n"
            + "v3.22 更新内容\n"
            + "· 黑框提示删掉了，只有出错才弹\n"
            + "· 单机不再记录、上传日志，也不往手机里放文件\n"
            + "· 删掉自制「道具」键（战术模式用游戏自己的道具栏）\n"
            + "· 单机新海岛改成战术模式（和联机一样）\n"
            + "· 水里加速修复（游泳也加速）\n"
            + "· 单机地图标点修复";
    static volatile String remoteNotice;
    static void fetchNotice() {
        new Thread(() -> {
            for (String base : NetCore.BASES) {
                try {
                    String t = new String(NetCore.get(base + "notice.txt", 64 << 10), "UTF-8").trim();
                    if (t.length() > 4 && !t.startsWith("<")) { remoteNotice = t; GameOverlay.MAIN.post(() -> { Runnable r = noticeSet; if (r != null) r.run(); }); return; }
                } catch (Throwable ignored) {}
            }
        }, "zf-notice").start();
    }
    static volatile Runnable noticeSet, gameMode;
    /** v3.24：游戏自己的更新页出来了 → 我们这层去掉黑底，只留公告和进度（不挡游戏的页） */
    static void gamePage() { Runnable r = gameMode; if (r != null) MAIN.post(r); }
    /** v3.25：开了游戏自己的更新页（UpdFlow）→ 用游戏的页（UpdPage），建不出来再用我们这层 */
    static final java.util.concurrent.atomic.AtomicBoolean startupOnce = new java.util.concurrent.atomic.AtomicBoolean();
    static void startupUpdate() {
        if (!startupOnce.compareAndSet(false, true)) return;
        if (UpdFlow.on) { fetchNotice(); UpdPage.start(); } srvUpdatePage();   // v3.35 用户录屏：游戏页只有背景、什么都不显示 → 我们这层先一直显示进度，游戏的进度条真接上了才关
    }
    static void srvFallback() { MAIN.post(GameOverlay::srvUpdatePage); }
    static void srvUpdatePage() {
        final Activity a = Con.act; if (a == null) return;
        if (!updShown.compareAndSet(false, true)) return;   // v2.69：加载页日志会连着来几行，以前会叠两层，关掉一层另一层一直挂着
        a.runOnUiThread(() -> {
            try {
                Ui u = new Ui(a);
                FrameLayout bg = new FrameLayout(a); bg.setBackgroundColor(0xE6000000);
                LinearLayout col = new LinearLayout(a); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.setPadding(u.dp(40), u.dp(24), u.dp(40), u.dp(18));
                // 公告
                LinearLayout nb = new LinearLayout(a); nb.setOrientation(LinearLayout.VERTICAL);
                nb.setPadding(u.dp(18), u.dp(12), u.dp(18), u.dp(12)); nb.setBackground(box(0xF2FFFFFF, u.dp(12)));
                TextView nt = new TextView(a); nt.setText("公告"); nt.setTextColor(TXT); nt.setTextSize(16); nt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); nt.setGravity(Gravity.CENTER);
                nb.addView(nt, new LinearLayout.LayoutParams(-1, -2));
                ScrollView sv = new ScrollView(a);
                final TextView body = new TextView(a); body.setText(NOTICE); body.setTextColor(TXT); body.setTextSize(13); body.setLineSpacing(u.dp(3), 1f);
                body.setPadding(0, u.dp(8), 0, 0);
                sv.addView(body);
                nb.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
                col.addView(nb, new LinearLayout.LayoutParams(u.dp(520), 0, 1));
                // 进度条
                final TextView st = new TextView(a); st.setText("正在检查更新…"); st.setTextColor(0xFFFFFFFF); st.setTextSize(13); st.setGravity(Gravity.CENTER);
                st.setPadding(0, u.dp(14), 0, u.dp(6));
                col.addView(st, new LinearLayout.LayoutParams(-1, -2));
                final ProgressBar pb = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
                pb.setMax(100); pb.setIndeterminate(true);
                try { pb.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFF5C518)); pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFFF5C518)); } catch (Throwable ignored) {}
                col.addView(pb, new LinearLayout.LayoutParams(u.dp(520), u.dp(10)));
                final TextView ver = new TextView(a); ver.setText("全军出击 " + Ed.name() + " " + VER + "（内核 " + ZInit.CORE_VER + "）· 点任意处关闭\n" + (UpdPage.started ? UpdPage.state : UpdFlow.state));   // v3.26：游戏更新页为什么没出来，直接显示（单机不导日志） ver.setTextColor(0x99FFFFFF); ver.setTextSize(11); ver.setGravity(Gravity.CENTER);
                ver.setPadding(0, u.dp(6), 0, 0);
                col.addView(ver, new LinearLayout.LayoutParams(-1, -2));
                bg.addView(col, new FrameLayout.LayoutParams(-1, -1));
                WindowManager.LayoutParams wl = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                if (android.os.Build.VERSION.SDK_INT >= 28) wl.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                wl.setTitle("zframe_upd");
                a.getWindowManager().addView(bg, wl);
                upd = bg;
                bg.setOnClickListener(v -> closeUpd(a, bg));   // v2.69：点一下就关
                gameMode = () -> { if (bg.isAttachedToWindow()) bg.setBackgroundColor(0x00000000); };
                if (UpdFlow.pageSeen) gameMode.run();
                noticeSet = () -> { String r = remoteNotice; if (r != null && bg.isAttachedToWindow()) body.setText(r); };
                if (remoteNotice != null) noticeSet.run(); else fetchNotice();
                final long t0 = System.currentTimeMillis();
                final boolean[] restarting = { false };
                final Runnable showPct = () -> { int p = NetCore.pct; if (p >= 0) { pb.setIndeterminate(false); pb.setProgress(p); } else if (NetCore.done) { pb.setIndeterminate(false); pb.setProgress(100); } };
                NetCore.listener = (msg, ready) -> a.runOnUiThread(() -> {
                    st.setText(msg); showPct.run();
                    if (ready && !restarting[0]) { restarting[0] = true; pb.setIndeterminate(false); pb.setProgress(100); st.setText("更新已下好，2 秒后自动重启…"); bg.postDelayed(() -> NetCore.restart(a), 2000); }
                });
                NetCore.check(a);
                { String s0 = NetCore.status; if (!s0.isEmpty()) st.setText(s0); showPct.run(); if (NetCore.readyVer > ZInit.CORE_VER && !restarting[0]) { restarting[0] = true; st.setText("更新已下好，2 秒后自动重启…"); bg.postDelayed(() -> NetCore.restart(a), 2000); } }
                final Runnable[] tick = new Runnable[1];
                tick[0] = () -> {
                    if (!bg.isAttachedToWindow() || restarting[0]) return;
                    showPct.run();
                    long el = System.currentTimeMillis() - t0;
                    ver.setText("全军出击 " + Ed.name() + " " + VER + "（内核 " + ZInit.CORE_VER + "）· 点任意处关闭\n" + (UpdPage.started ? UpdPage.state : UpdFlow.state));
                    if (UpdPage.panelOk) { closeUpd(a, bg); return; }
                    if (NetCore.done && el > 6000 && (!UpdFlow.on || UpdPage.finished)) { closeUpd(a, bg); return; }   // v3.35：去掉 40 秒自动关，跟开机规则一致（走完才放行）
                    bg.postDelayed(tick[0], 300);
                };
                bg.postDelayed(tick[0], 300);
                LogCap.note("加载页更新+公告：已显示");
            } catch (Throwable e) { LogCap.note("加载页更新：显示失败 " + e); }
        });
    }
    static void closeUpdAny() { Activity a = Con.act; View v = upd; if (a != null && v != null) a.runOnUiThread(() -> closeUpd(a, v)); }
    static void closeUpd(Activity a, View v) {
        if (upd == v) { upd = null; NetCore.listener = null; }
        if (v != null) try { a.getWindowManager().removeViewImmediate(v); } catch (Throwable ignored) {}
    }
    /** 选完（手动或超时）：关掉选择框，菜单标题跟着变 */
    static void chosen() {
        final Activity a = chooserAct; final View c = chooser;
        final TextView tr = titleRef;
        Activity any = a != null ? a : Con.act;
        if (any == null) return;
        any.runOnUiThread(() -> {
            if (c != null && a != null) { try { a.getWindowManager().removeViewImmediate(c); } catch (Throwable ignored) {} }
            chooser = null; chooserAct = null;
            if (tr != null) tr.setText(titleText());
            refreshAny();
        });
    }
}
