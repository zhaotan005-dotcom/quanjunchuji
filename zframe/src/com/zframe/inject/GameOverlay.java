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
    static final String VER = "v2.10";
    static boolean collapsed = true;   // v2.06 用户：一进去先看到角色，菜单默认收起
    static int tab = 0, weapCat = 0;

    // 配色（v1.49 简约：近黑半透明底 + 白/灰文字 + 一个低饱和蓝，只用细线分隔，不用渐变和彩色标签）
    // v2.05 用户：不要黑底，参考苹果风格 → 浅灰毛玻璃底 + 白色卡片 + 系统蓝，开关绿色
    static final int PANEL = 0xF5F2F2F7, CARD = 0xFFFFFFFF, CARD2 = 0xFFFFFFFF, LINE = 0x14000000,
            TXT = 0xFF1C1C1E, SUBT = 0xFF8E8E93, ACC = 0xFF007AFF, ON_BG = 0xFF007AFF, ON_TXT = 0xFFFFFFFF,
            ORANGE = 0xFFFF9500, CHIP = 0x0F000000, GREEN = 0xFF34C759;

    static GradientDrawable box(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(r);
        return g;
    }
    static GradientDrawable boxLine(int color, int line, float r, int w) {
        GradientDrawable g = box(color, r); g.setStroke(w, line); return g;
    }
    /** 选中态：白底（深色字） */
    static GradientDrawable grad(float r) { return box(ON_BG, r); }

    static Runnable refresher;
    /** v1.50：界面重建一律排到下一帧。以前在主线程上会立刻重建，松开滑条的那一刻把滑条自己删掉 → 闪退 */
    static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    static void refreshAny() { final Runnable r = refresher; if (r != null) { MAIN.removeCallbacks(r); MAIN.post(r); } }

    static void attach(final Activity a) {
        try {
            Con.setActivity(a);
            Con.loadGfx();
            final ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            if (decor.findViewWithTag("zframe_panel") != null) return;
            final Ui u = new Ui(a);
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
            // v1.49：窄一点（和最早的版本差不多宽），高度不超过屏幕的 82%
            final int pw = Math.min((int) (sw * 0.46f), u.dp(400)), ph = Math.min((int) (sh * 0.82f), u.dp(340));   // v1.99 左边竖排分页，所以宽一点

            final LinearLayout panel = new LinearLayout(a);
            panel.setTag("zframe_panel");
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(boxLine(PANEL, 0x33000000, u.dp(8), Math.max(1, u.dp(1) / 2)));   // v2.08 ImGui 浅色风：小圆角 + 细边框
            panel.setElevation(u.dp(8));
            panel.setClipToOutline(true);

            // ---- 标题栏：名字 + 版本，右边 – × ----
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(u.dp(6), u.dp(4), u.dp(6), u.dp(4));
            head.setBackground(box(0xFFDCDCE2, 0));   // v2.08 ImGui 风：单独一条灰色标题栏
            // v2.07 用户：左边一个三角方块（点开展开菜单、再点折叠），标题居中，版本号不放标题栏
            final TextView fold = new TextView(a);
            fold.setTextColor(ACC); fold.setTextSize(13); fold.setGravity(Gravity.CENTER);
            fold.setBackground(box(0x1F007AFF, u.dp(4)));
            head.addView(fold, new LinearLayout.LayoutParams(u.dp(26), u.dp(26)));
            TextView title = new TextView(a);
            title.setText("全军出击联机版"); title.setTextColor(TXT); title.setTextSize(15); title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setLetterSpacing(0.04f); title.setGravity(Gravity.CENTER); title.setSingleLine(true);
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            final TextView close = iconBtn(a, u, "×");
            close.setOnClickListener(v -> showPanel(panel, false, u));
            head.addView(close, new LinearLayout.LayoutParams(u.dp(26), u.dp(26)));
            panel.addView(head, new LinearLayout.LayoutParams(-1, -2));

            // ---- 当前状态：一行灰字 ----
            final TextView status = new TextView(a);
            status.setTextSize(10.5f); status.setTextColor(SUBT); status.setSingleLine(true);
            status.setEllipsize(android.text.TextUtils.TruncateAt.END);
            status.setPadding(u.dp(14), u.dp(6), u.dp(14), u.dp(6));
            panel.addView(status, new LinearLayout.LayoutParams(-1, -2));

            // ---- 主体（可收起）：v1.99 左边一列分页（用户：菜单太复古、排版没逻辑），右边内容 ----
            final LinearLayout body = new LinearLayout(a);
            body.setOrientation(LinearLayout.HORIZONTAL);
            final LinearLayout tabsRow = new LinearLayout(a);
            tabsRow.setOrientation(LinearLayout.VERTICAL);
            tabsRow.setPadding(u.dp(8), u.dp(4), u.dp(4), u.dp(10));
            final String[] tabs = {"常用", "武器", "装备", "刷车", "人机", "联机", "画面"};
            final int[] map = {0, 3, 4, 1, 2, 6, 5};   // 显示顺序 → 原来的页号（fill 按页号填）
            final TextView[] tv = new TextView[tabs.length];
            final ScrollView sv = new ScrollView(a);
            sv.setVerticalScrollBarEnabled(false);
            final LinearLayout content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(u.dp(10), u.dp(2), u.dp(14), u.dp(14));
            sv.addView(content);
            for (int k = 0; k < tabs.length; k++) {
                final int idx = map[k];
                TextView t = new TextView(a);
                t.setText(tabs[k]); t.setTextSize(12.5f); t.setGravity(Gravity.CENTER);
                t.setTag(idx);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(u.dp(58), u.dp(36)); tl.bottomMargin = u.dp(4);
                tabsRow.addView(t, tl);
                tv[k] = t;
                t.setOnClickListener(v -> { tab = idx; paintTabs(tv, u); fill(a, u, content); sv.scrollTo(0, 0); });
            }
            paintTabs(tv, u);
            body.addView(tabsRow, new LinearLayout.LayoutParams(-2, -1));
            final View hr = new View(a); hr.setBackgroundColor(LINE);
            body.addView(hr, new LinearLayout.LayoutParams(1, -1));
            body.addView(sv, new LinearLayout.LayoutParams(0, ph - u.dp(56), 1));
            panel.addView(body, new LinearLayout.LayoutParams(-1, -2));

            final View tabsLine = hr;
            // v2.05：对局里的分页按身份不同 —— 单机：常用/武器/装备/刷车/人机/画面；房主：人机换成联机；加入的客户端：只有联机/画面
            final Runnable mode = () -> {
                int v = Con.inMatch ? View.VISIBLE : View.GONE; tabsRow.setVisibility(v); tabsLine.setVisibility(v);
                int r = Lan.role;
                for (TextView t : tv) {
                    int id = (Integer) t.getTag();
                    boolean show = r == 0 ? id != 6 : r == 1 ? id != 2 : (id == 6 || id == 5);
                    t.setVisibility(show ? View.VISIBLE : View.GONE);
                }
                if (Con.inMatch) {
                    boolean ok = r == 0 ? tab != 6 : r == 1 ? tab != 2 : (tab == 6 || tab == 5);
                    if (!ok) { tab = r == 2 ? 6 : 0; paintTabs(tv, u); }
                }
            };
            mode.run();
            refresher = () -> { mode.run(); paintStatus(status); fill(a, u, content); };
            paintStatus(status);
            fill(a, u, content);
            final Runnable[] tick = new Runnable[1];
            tick[0] = () -> { if (panel.getParent() == null) return; if (panel.getVisibility() == View.VISIBLE) paintStatus(status); panel.postDelayed(tick[0], 1000); };
            panel.postDelayed(tick[0], 1000);

            // v2.05 用户：不要「艾」悬浮球 → 「–」收起成只剩标题栏的小条，点标题栏或「+」展开
            final Runnable apply = () -> {
                body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
                status.setVisibility(collapsed ? View.GONE : View.VISIBLE);
                fold.setText(collapsed ? "▶" : "▼");
                ViewGroup.LayoutParams lp0 = panel.getLayoutParams();
                if (lp0 != null) { lp0.width = collapsed ? u.dp(210) : pw; panel.setLayoutParams(lp0); }
            };
            fold.setOnClickListener(v -> { collapsed = !collapsed; apply.run(); if (!collapsed) fill(a, u, content); });

            hookKeys(a, panel, u);
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(pw, -2);
            flp.gravity = Gravity.TOP | Gravity.START;
            decor.addView(panel, flp);
            if (px < 0) { px = u.dp(40); py = u.dp(24); }
            panel.setX(px); panel.setY(py);
            if (hidden) panel.setVisibility(View.GONE);
            apply.run();

            head.setOnTouchListener(new View.OnTouchListener() {
                float dx, dy, sx, sy; boolean moved;
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: dx = e.getRawX(); dy = e.getRawY(); sx = panel.getX(); sy = panel.getY(); moved = false; return true;
                        case MotionEvent.ACTION_MOVE:
                            float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                            if (Math.abs(mx) + Math.abs(my) > u.dp(6)) moved = true;
                            if (moved) { panel.setX(sx + mx); panel.setY(sy + my); px = panel.getX(); py = panel.getY(); }
                            return true;
                        case MotionEvent.ACTION_UP:
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
            tv[j].setBackground(on ? box(ON_BG, u.dp(9)) : null);   // v1.99 选中：白色圆角块
        }
    }

    /** 当前状态：一行文字，例如「● 不上飞机 · 不刷圈 · 男 · 加速 2 · 无后座 · 聚点 · 子弹 999」 */
    static void paintStatus(TextView st) {
        StringBuilder b = new StringBuilder();
        if (!Mods.noPlane && !Mods.noPoison) b.append("正常模式");
        else { if (Mods.noPlane) b.append("不上飞机"); if (Mods.noPoison) b.append(b.length() > 0 ? " · " : "").append("不刷圈"); }
        if (!Con.inMatch) { st.setText(dot(b.toString() + " · " + VER)); return; }   // v1.73：不显示男女和大厅
        if (Mods.speed != 1f) b.append(" · 加速 ").append(fmt(Mods.speed));
        if (Mods.jump != 1f) b.append(" · 高跳 ").append(fmt(Mods.jump));
        if (Mods.noRecoil) b.append(" · 无后座");
        if (Mods.noShake) b.append(" · 聚点");
        if (Mods.infAmmo) b.append(" · 子弹 999");
        if (Con.godOn) b.append(" · 无敌");
        if ((Mods.speed != 1f || Mods.jump != 1f || Mods.noRecoil || Mods.noShake || Mods.infAmmo) && !Mods.moveState.isEmpty() && !"已生效".equals(Mods.moveState))
            b.append("（").append(Mods.moveState).append("）");
        st.setText(dot(b.toString() + " · " + VER));
    }
    static CharSequence dot(String s) {
        android.text.SpannableString ss = new android.text.SpannableString("●  " + s);
        ss.setSpan(new android.text.style.ForegroundColorSpan(0xFF4ADE80), 0, 1, 0);
        return ss;
    }

    // v1.40：五指同时点屏幕 → 打开菜单；标题栏右边 ✕ → 关掉（带出场/退场动画）
    static volatile boolean hidden;
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
                        View pv = ((ViewGroup) w.getDecorView()).findViewWithTag("zframe_panel");
                        if (pv != null) a.runOnUiThread(() -> showPanel(pv, true, u));
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
            pv.setAlpha(0f); pv.setScaleX(0.85f); pv.setScaleY(0.85f); pv.setTranslationY(-u.dp(30));
            pv.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(260)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        } else {
            if (pv.getVisibility() != View.VISIBLE) return;
            pv.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).translationY(-u.dp(30)).setDuration(200)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> { if (hidden) pv.setVisibility(View.GONE); }).start();
        }
    }

    static void detach(Activity a) {
        try {
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
        if (label != null) Toast.makeText(a, ok ? label : "执行失败", Toast.LENGTH_SHORT).show();
    }

    static void toastAny(String msg) { Activity a = Con.act; if (a != null) a.runOnUiThread(() -> toast(a, msg)); }
    static void toast(Activity a, String msg) { Toast.makeText(a, msg, Toast.LENGTH_SHORT).show(); }

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
    static volatile boolean onGround;
    static void give(Activity a, String id, int n) {
        if (onGround) {
            final long v; try { v = Long.parseLong(id); } catch (Throwable e) { return; }
            final int piles = n <= 1 ? 1 : Math.min(3, n);
            new Thread(() -> {
                try { String e = Veh.ground(v, piles); if (e != null) toastAny("没刷到地上：" + e); }
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
    static void give(Activity a, String id, int n, String label) {
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
        if (onGround) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            String t = dbgText(); LogCap.note("刷物品诊断 " + lb + " " + id + ": " + t);
            boolean ok = dbg != null && dbg.getInt(0) == 0x5a02 && dbg.getInt(28) != 0;
            if (ok) okId.put(lb, id);
            else giveTry(a, ids, i + 1, lb);
        }, 1200);
    }
    static java.util.List<String> armorIds() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String k = okId.get("3级甲"); if (k != null) l.add(k);
        if (PakScan.armor3 != null && !l.contains(PakScan.armor3)) l.add(PakScan.armor3);
        for (String x : new String[]{"30305000003", "30305000005", "30305000006"}) if (!l.contains(x)) l.add(x);
        return l;
    }

    static void after(Activity a, final String lb) {
        if (onGround) return;
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
        t.setPadding(u.dp(6), u.dp(14), 0, u.dp(6));
    }
    /** 卡片容器 */
    static LinearLayout card(Activity a, Ui u, ViewGroup p) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(CARD, u.dp(12)));
        c.setPadding(u.dp(12), u.dp(4), u.dp(12), u.dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = u.dp(4);
        p.addView(c, lp);
        return c;
    }
    /** 普通按钮（卡片样式，按下有缩放反馈） */
    static TextView btn(Activity a, Ui u, LinearLayout row, String text, final Runnable r) {
        TextView b = new TextView(a);
        b.setText(text); b.setTextColor(ACC); b.setTextSize(12.5f);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(false); b.setMaxLines(2);
        b.setPadding(u.dp(4), u.dp(10), u.dp(4), u.dp(10));
        b.setBackground(box(CARD2, u.dp(10)));
        press(b);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(u.dp(3), u.dp(3), u.dp(3), u.dp(3));
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
        b.setPadding(u.dp(4), u.dp(8), u.dp(4), u.dp(8));
        b.setBackground(on ? grad(u.dp(9)) : box(CARD2, u.dp(9)));
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
            p.setColor(0xFFE5E5EA); c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
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
        r.setPadding(0, u.dp(8), 0, u.dp(8));
        LinearLayout t = new LinearLayout(a); t.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(a); n.setText(name); n.setTextColor(TXT); n.setTextSize(13);
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
            p.setColor(0xFFE5E5EA); c.drawRoundRect(new RectF(x0, cy - th / 2, x1, cy + th / 2), th, th, p);
            p.setColor(ACC); if (cx > x0) c.drawRoundRect(new RectF(x0, cy - th / 2, cx, cy + th / 2), th, th, p);
            float r = (drag ? 8 : 6.5f) * dens;
            if (drag) { p.setColor(0x22007AFF); c.drawCircle(cx, cy, r + 6 * dens, p); }
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
    static boolean takesAttachments(String id) { return id.startsWith("20101") || id.startsWith("20102") || id.startsWith("20103") || id.startsWith("20104") || id.startsWith("20105"); }
    /** 这一局已经刷过的配件（换局清空）：包里已经有的配件不再重复刷 */
    static final java.util.Set<String> givenAtt = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    static void newMatch() { givenAtt.clear(); givenGear.clear(); }
    /** v2.02 用户：开局刷了 3 级套，跳伞后再刷一次直接闪退（栈溢出）。头/甲/包身上已有时再塞一件 = 游戏要换装、把旧的扔地上，这条路会崩。
     *  同一局每个部位只刷一次（刷在地上不受限）。 */
    static final java.util.Set<String> givenGear = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    static boolean gearOnce(Activity a, String slot) {
        if (onGround) return true;
        if (givenGear.add(slot)) return true;
        toast(a, "这局已经刷过" + slot + "，身上已有再刷会闪退");
        return false;
    }
    /** 刷一把枪：枪 + 对应子弹 + 这一局还没刷过的全套配件 */
    static void giveGun(Activity a, String name, String id, String ammo) {
        give(a, id, 1);
        if (!"0".equals(ammo)) give(a, "2060000000" + ammo, "7".equals(ammo) ? 30 : 60);
        if (takesAttachments(id)) for (String at : ATT) if (givenAtt.add(at)) give(a, at, 1);
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
            if (n % 3 == 0) r = row(a, root);
            btn(a, u, r, it[0], () -> {
                if ("flare".equals(it[1])) flare(a);
                else if (it[1].startsWith("204")) give(a, it[1], 3, it[0]);
                else if (it[1].startsWith("206")) give(a, it[1], it[1].endsWith("9") ? 5 : it[1].endsWith("7") ? 30 : 150, it[0]);
                else giveGun(a, it[0], it[1], it[2]);
            });
            n++;
        }
        while (n % 3 != 0) { r.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1)); n++; }
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
            default: mainPage(a, u, root);
        }
    }
    static void giveHint(Activity a, LinearLayout root) {
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
        LinearLayout seg = row(a, root);
        seg.setBackground(box(0x1F767680, u.dp(10))); seg.setPadding(u.dp(2), u.dp(2), u.dp(2), u.dp(2));
        segBtn(a, u, seg, "联机版", lobbyTab == 0, () -> { lobbyTab = 0; refreshAny(); });
        segBtn(a, u, seg, "单机版", lobbyTab == 1, () -> { lobbyTab = 1; refreshAny(); });
        if (lobbyTab == 0) {
            head(a, u, root, "开房（我当服务器）");
            LinearLayout hc = card(a, u, root);
            kv(a, u, hc, "本机 IP", Lan.ip());
            LinearLayout r = row(a, hc); r.setPadding(0, u.dp(4), 0, u.dp(6));
            fillBtn(a, u, r, "老海岛", () -> toast(a, Lan.host("/Game/Maps/ErangelMobile_Runtime/BR-Erangel_Mobile_Main")));
            fillBtn(a, u, r, "新海岛", () -> toast(a, Lan.host("/Game/Maps/ErangeMobileRenew_Runtime/BR-ErangelRenew_Mobile_Main")));
            fillBtn(a, u, r, "雨林", () -> toast(a, Lan.host("/Game/Maps/SavageMobile_4x4_Runtime/BR-Savage_Mobile_Main")));
            note(a, u, root, "点地图直接进图当房主。外网玩家用内网穿透连你的 UDP 7777。");
            head(a, u, root, "加入房间");
            LinearLayout jc = card(a, u, root);
            LinearLayout ir = row(a, jc); ir.setPadding(0, u.dp(6), 0, u.dp(6));
            final EditText et = new EditText(a);
            et.setHint("房主 IP，如 192.168.1.5 或 穿透地址:端口"); et.setTextColor(TXT); et.setHintTextColor(SUBT); et.setTextSize(13);
            et.setInputType(android.text.InputType.TYPE_CLASS_TEXT); et.setSingleLine(true);
            if (Lan.lastIp != null) et.setText(Lan.lastIp);
            et.setBackground(box(0xFFF2F2F7, u.dp(9))); et.setPadding(u.dp(10), u.dp(8), u.dp(10), u.dp(8));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, -2, 2.4f); elp.setMargins(0, u.dp(3), u.dp(6), u.dp(3));
            ir.addView(et, elp);
            fillBtn(a, u, ir, "加入", () -> toast(a, Lan.join(et.getText().toString())));
            if (!"还没试".equals(Lan.state)) note(a, u, root, Lan.state);
        } else {
            head(a, u, root, "游戏模式");
            LinearLayout fl = row(a, root);
            pick(a, u, fl, "正常", !Mods.noPlane && !Mods.noPoison, () -> result(a, Mods.setNormal()));
            pick(a, u, fl, "不上飞机", Mods.noPlane, () -> result(a, Mods.setPlane(!Mods.noPlane)));
            pick(a, u, fl, "不刷圈", Mods.noPoison, () -> result(a, Mods.setPoison(!Mods.noPoison)));
            note(a, u, root, "选好后点游戏里的「匹配赛」开始。单机自动刷人机，难度在对局里的「人机」页改。");
            LinearLayout dg = row(a, root);
            btn(a, u, dg, "清空仓库", () -> new Thread(() -> toastAny(Server.resetProps())).start());
        }
        logRow(a, u, root);
    }
    /** 最下面：日志 */
    static void logRow(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "日志");
        LinearLayout dg = row(a, root);
        btn(a, u, dg, "导出诊断", () -> new Thread(() -> toastAny(AvatarLog.export(a))).start());
        note(a, u, root, Server.state);
    }
    /** 对局里的「联机」页：房主 / 客户端都有 */
    static void lanPage(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "联机");
        LinearLayout c = card(a, u, root);
        kv(a, u, c, "身份", Lan.roleText());
        kv(a, u, c, "本机 IP", Lan.ip());
        if (Lan.role == 2 && Lan.lastIp != null) kv(a, u, c, "房主", Lan.lastIp);
        note(a, u, root, Lan.role == 2 ? "你是加入的玩家：刷东西、无敌这些要房主那边开，客户端改了也不生效。" : "你是房主：别人连你的 IP（UDP 7777）。房主这边的刷物资、刷车照常能用。");
        note(a, u, root, Lan.state);
        logRow(a, u, root);
    }
    /** iOS 分段控件里的一个按钮 */
    static void segBtn(Activity a, Ui u, LinearLayout seg, String text, boolean on, Runnable r) {
        TextView b = new TextView(a);
        b.setText(text); b.setTextSize(12.5f); b.setGravity(Gravity.CENTER);
        b.setTextColor(TXT); b.setTypeface(Typeface.create(on ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        b.setPadding(0, u.dp(7), 0, u.dp(7));
        if (on) { b.setBackground(box(0xFFFFFFFF, u.dp(8))); b.setElevation(u.dp(1)); }
        b.setOnClickListener(v -> r.run());
        seg.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    /** 实心蓝按钮 */
    static TextView fillBtn(Activity a, Ui u, LinearLayout row, String text, Runnable r) {
        TextView b = btn(a, u, row, text, r); b.setBackground(box(ACC, u.dp(10))); b.setTextColor(0xFFFFFFFF); return b;
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
        head(a, u, root, "保命");
        LinearLayout oc = card(a, u, root);
        switchRow(a, u, oc, "无敌", "不会掉血", Con.godOn, v -> { cmd(a, "EnableCheats"); cmd(a, "God"); Con.godToggled(); return "ok"; });
        LinearLayout tr = row(a, root);
        btn(a, u, tr, "传送到地图中间", () -> toast(a, Con.tpCenter()));
        head(a, u, root, "刷物资放在");
        LinearLayout gr = row(a, root);
        pick(a, u, gr, "身上（背包）", !onGround, () -> { onGround = false; refreshAny(); });
        pick(a, u, gr, "地上（脚下）", onGround, () -> { onGround = true; refreshAny(); });
        head(a, u, root, "枪");
        LinearLayout gc = card(a, u, root);
        switchRow(a, u, gc, "无后座", "开枪时枪口不往上跳", Mods.noRecoil, v -> { Mods.setGun(v, Mods.noShake, Mods.infAmmo); return "ok"; });
        switchRow(a, u, gc, "聚点", "子弹不散开，打得更准", Mods.noShake, v -> { Mods.setGun(Mods.noRecoil, v, Mods.infAmmo); return "ok"; });
        switchRow(a, u, gc, "无限子弹", "子弹一直是 999 发", Mods.infAmmo, v -> { Mods.setGun(Mods.noRecoil, Mods.noShake, v); return "ok"; });
        head(a, u, root, "跑跳");
        slider(a, u, root, "加速", "跑得更快，拖到最左边是关", 0f, 10f, 0.5f, Mods.speed == 1f ? 0f : Mods.speed, true, v -> Mods.setMove(v, Mods.jump));
        slider(a, u, root, "高跳", "跳得更高，拖到最左边是关", 0f, 10f, 0.5f, Mods.jump == 1f ? 0f : Mods.jump, true, v -> Mods.setMove(Mods.speed, v));
        slider(a, u, root, "加速时的动作", "越小动作越慢，1 是正常", 0.3f, 1f, 0.05f, Mods.slowMo, false, v -> Mods.slowMo = v);
    }

    /** v1.93 刷车页：车按钮直接显示（编号写死，不用读车表），点了刷在面前 */
    static void vehPage(final Activity a, final Ui u, final LinearLayout root) {
        head(a, u, root, "载具");
        java.util.ArrayList<Veh.Group> vg = Veh.groups;
        LinearLayout vr = null;
        for (int i = 0; i < vg.size(); i++) {
            if (i % 3 == 0) vr = row(a, root);
            final int gi = i;
            btn(a, u, vr, vg.get(i).label, () -> Veh.spawnGroup(gi));
        }
        if (Veh.state.contains("没反应") || Veh.state.contains("出错") || Veh.state.contains("没有")) note(a, u, root, Veh.state);
    }
    /** v1.93 人机页 */
    static void botPage(final Activity a, final Ui u, final LinearLayout root) {
        botRows(a, u, root);
        LinearLayout br = row(a, root);
        btn(a, u, br, "再刷一个（面前 8 米）", Veh::spawnBot);
        note(a, u, root, Bots.state);
    }

    static void weaponPage(final Activity a, final Ui u, final LinearLayout root) {
        HorizontalScrollView hs = new HorizontalScrollView(a); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout cr = new LinearLayout(a); cr.setOrientation(LinearLayout.HORIZONTAL);
        for (int k = 0; k < CATS.length; k++) {
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
        head(a, u, root, "点一下放进背包");
        gunGrid(a, u, root, weapCat);
        giveHint(a, root);
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
        head(a, u, root, "其他");
        LinearLayout g3 = row(a, root);
        btn(a, u, g3, "全套药品", () -> { for (String m : MED) give(a, m, 5); after(a, "全套药品"); });
        btn(a, u, g3, "手雷×3", () -> give(a, "20401000001", 3, "手雷"));
        btn(a, u, g3, "全套配件", () -> { int n = 0; for (String at : ATT) if (givenAtt.add(at)) { give(a, at, 1); n++; } if (n == 0) toast(a, "这局配件已经刷过了"); else after(a, "全套配件"); });
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
        btn(a, u, ir, "穿上", () -> {
            String id = et.getText().toString().trim();
            if (!id.matches("\\d{11}")) { toast(a, "编号是 11 位数字"); return; }
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
        label(a, root, VER, 10, false, SUBT).setPadding(u.dp(3), u.dp(14), 0, 0);
    }
}
