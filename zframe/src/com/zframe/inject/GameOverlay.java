package com.zframe.inject;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import com.zframe.ui.Ui;

/**
 * v1.23 艾挽：游戏里的常驻小窗（仿原作者窗口的布局）——标题栏可拖动、点 ▼/▶ 折叠，
 * 左边一列分页（地图/功能/设置），右边是内容。挂在游戏 Activity 的窗口上，不需要悬浮窗权限，也不需要 root。
 */
public class GameOverlay {
    static float px = -1, py = -1;
    static boolean collapsed = false;
    static int tab = 0;

    static final int BG = 0xE6EEF2FA, HEAD = 0xF0DCE4F4, TXT = 0xFF1E2433, SUBT = 0xFF4A5470,
            TAB = 0xFFA9BDE8, TAB_ON = 0xFF6F8FD6, BTN = 0xFFB7CAF1, ACCENT = 0xFF1FA67A;

    static GradientDrawable box(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(r);
        return g;
    }

    static void attach(final Activity a) {
        Mods.restore(a);
        try {
            Con.setActivity(a);
            final ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            if (decor.findViewWithTag("zframe_panel") != null) return;
            final Ui u = new Ui(a);

            final LinearLayout panel = new LinearLayout(a);
            panel.setTag("zframe_panel");
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(box(BG, u.dp(6)));
            panel.setElevation(u.dp(6));

            // 标题栏
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setBackground(box(HEAD, u.dp(6)));
            head.setPadding(u.dp(10), u.dp(6), u.dp(10), u.dp(6));
            final TextView arrow = new TextView(a);
            arrow.setTextColor(TXT); arrow.setTextSize(15); arrow.setTypeface(Typeface.DEFAULT_BOLD);
            head.addView(arrow, new LinearLayout.LayoutParams(u.dp(28), -2));
            TextView title = new TextView(a);
            title.setText("艾挽 全军出击单机版");
            title.setTextColor(TXT); title.setTextSize(15); title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            final TextView close = new TextView(a);
            close.setText("✕"); close.setTextColor(TXT); close.setTextSize(16); close.setTypeface(Typeface.DEFAULT_BOLD);
            close.setGravity(Gravity.CENTER);
            close.setOnClickListener(v -> showPanel(panel, false, u));
            head.addView(close, new LinearLayout.LayoutParams(u.dp(32), -2));
            panel.addView(head, new LinearLayout.LayoutParams(-1, -2));

            // 主体：左边分页，右边内容
            final LinearLayout body = new LinearLayout(a);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setPadding(u.dp(6), u.dp(6), u.dp(6), u.dp(6));
            LinearLayout side = new LinearLayout(a);
            side.setOrientation(LinearLayout.VERTICAL);
            TextView logo = new TextView(a);
            logo.setText("AW"); logo.setTextColor(ACCENT); logo.setTextSize(20); logo.setTypeface(Typeface.DEFAULT_BOLD);
            logo.setGravity(Gravity.CENTER);
            logo.setPadding(0, u.dp(6), 0, u.dp(10));
            side.addView(logo, new LinearLayout.LayoutParams(-1, -2));
            final ScrollView sv = new ScrollView(a);
            final LinearLayout content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(u.dp(8), 0, u.dp(2), 0);
            sv.addView(content);
            final String[] tabs = new String[CATS.length + 3];
            tabs[0] = "功能"; for (int k = 0; k < CATS.length; k++) tabs[k + 1] = CATS[k][0];
            tabs[CATS.length + 1] = "衣服"; tabs[CATS.length + 2] = "设置";
            final TextView[] tv = new TextView[tabs.length];
            for (int k = 0; k < tabs.length; k++) {
                final int idx = k;
                TextView t = new TextView(a);
                t.setText(tabs[k]); t.setTextColor(TXT); t.setTextSize(14); t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setGravity(Gravity.CENTER);
                t.setPadding(0, u.dp(8), 0, u.dp(8));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, u.dp(4));
                side.addView(t, lp);
                tv[k] = t;
                t.setOnClickListener(v -> { tab = idx; paintTabs(tv, u); fill(a, u, content); });
            }
            paintTabs(tv, u);
            ScrollView sideSv = new ScrollView(a);
            sideSv.setVerticalScrollBarEnabled(false);
            sideSv.addView(side);
            body.addView(sideSv, new LinearLayout.LayoutParams(u.dp(78), -1));
            body.addView(sv, new LinearLayout.LayoutParams(0, -1, 1));
            panel.addView(body, new LinearLayout.LayoutParams(-1, u.dp(230)));
            fill(a, u, content);

            Runnable apply = () -> { body.setVisibility(collapsed ? View.GONE : View.VISIBLE); arrow.setText(collapsed ? "▶" : "▼"); };
            apply.run();

            hookKeys(a, panel, u);
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(u.dp(340), -2);
            flp.gravity = Gravity.TOP | Gravity.START;
            decor.addView(panel, flp);
            if (px < 0) { px = u.dp(40); py = u.dp(40); }
            panel.setX(px); panel.setY(py);
            if (hidden) panel.setVisibility(View.GONE);

            // 拖标题栏移动；点一下折叠/展开
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

    static void paintTabs(TextView[] tv, Ui u) {
        for (int j = 0; j < tv.length; j++) tv[j].setBackground(box(j == tab ? TAB_ON : TAB, u.dp(4)));
    }

    static void detach(Activity a) {
        try {
            ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            View b = decor.findViewWithTag("zframe_panel");
            if (b != null) decor.removeView(b);
        } catch (Throwable ignored) {}
    }

    static TextView label(Activity a, ViewGroup p, String s, float size, boolean bold, int color) {
        TextView t = new TextView(a);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, 0);
        p.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    static LinearLayout row(Activity a, ViewGroup p) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        p.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    static TextView btn(Activity a, Ui u, LinearLayout row, String label, final Runnable r) {
        TextView b = new TextView(a);
        b.setText(label); b.setTextColor(TXT); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(u.dp(4), u.dp(10), u.dp(4), u.dp(10));
        b.setBackground(box(BTN, u.dp(4)));
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(0, u.dp(3), u.dp(6), u.dp(3));
        row.addView(b, lp);
        return b;
    }

    static void head(Activity a, Ui u, ViewGroup p, String s) {
        TextView t = label(a, p, "● " + s, 14, true, TXT);
        t.setPadding(0, u.dp(6), 0, u.dp(2));
    }

    static void cmd(Activity a, String c) { cmd(a, c, null); }

    static void cmd(Activity a, String c, String label) {
        boolean ok = Con.exec(c);
        if (label != null) Toast.makeText(a, ok ? label : "执行失败", Toast.LENGTH_SHORT).show();
    }

    static void toastAny(String msg) { Activity a = Con.act; if (a != null) a.runOnUiThread(() -> toast(a, msg)); }
    static void toast(Activity a, String msg) { Toast.makeText(a, msg, Toast.LENGTH_SHORT).show(); }

    static String mcziText(Activity a) {
        try {
            java.io.File d = new java.io.File(a.getApplicationInfo().nativeLibraryDir);
            long m = new java.io.File(d, "libMczi.so").length(), z = new java.io.File(d, "libzlib.so").length();
            if (m == 0) return "游戏里没有这个库";
            if (m == z) return "已关（装进游戏的是空壳）";
            return "还开着（" + (m >> 10) + " KB）。要关：ZFrame → 修补游戏 → 更新内核 + 关掉原作者窗口，最后一步要点“安装/更新”";
        } catch (Throwable e) { return "查不到：" + e; }
    }

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
        if (giveSt != 2) return;
        if (pbuf == null) { pbuf = java.nio.ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.LITTLE_ENDIAN); pbufAddr = bufAddr(pbuf); }
        if (pbufAddr <= 0 || pbufAddr >= 0xFFFFFFFFL) return;
        final Activity a = Con.act; if (a == null) return;
        a.runOnUiThread(() -> Con.exec("AddAvaItem P0x1x" + pbufAddr));
    }
    static long pawnAddr() {
        if (pbuf != null) { int m = pbuf.getInt(0); if (m == 0x5a01 || m == 0x5a02) return pbuf.getInt(4) & 0xffffffffL; }
        // 刷过任何东西后，诊断缓冲区里也有人物地址
        if (dbg != null) { int m = dbg.getInt(0); if (m == 0x5a01 || m == 0x5a02) return dbg.getInt(4) & 0xffffffffL; }
        return 0;
    }
    static void forgetPawn() { if (pbuf != null) pbuf.putInt(0, 0); if (dbg != null) dbg.putInt(0, 0); }
    static void give(Activity a, String id, int n) {
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
    static int coatIdx = 0;
    // v1.34：只轮换实测能放进背包的衣服（外套 30315 + 连帽 30316000007 + 连体 30323000002），从 30315000001（长款外套）开始
    static final int[] COAT_OK = {1,2,3,4,5,6,8,9,10,11,12,14,15,16,17,18,20,21,22,26,33,36,37,39,41,42,43,44,48,49,52,53,54,55,57,58,59,60,61};
    static java.util.List<String> suitIds() {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (int k : COAT_OK) l.add(String.format(java.util.Locale.US, "303150%05d", k));
        l.add("30316000007"); l.add("30323000002");
        return l;
    }
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
            if (ok) { coatNow = id; if (coatLbl != null) coatLbl.setText("当前：" + id); return; }
            if (tries < 30) coatStep(a, dir, tries + 1); else toast(a, "连续 30 个编号都没有衣服");
        }, 600);
    }
    static TextView coatLbl;

    // v1.36：枪按名字搜（编号规律：201 + 类别(01步枪 02机枪 03霰弹 04狙击 05冲锋 06特殊) + 6位序号，序号=游戏枚举顺序）
    static final String[][] GUNS = {
        {"M416", "20101000001", "m4 m416"}, {"AKM", "20101000002", "ak ak47 akm"}, {"M16A4", "20101000003", "m16"},
        {"SCAR-L", "20101000004", "scar 斯卡"}, {"GROZA", "20101000005", "groza 狗砸"},
        {"M249", "20102000001", "m249 大菠萝 机枪"},
        {"S1897", "20103000001", "s1897 1897 喷子 霰弹"}, {"S686", "20103000002", "s686 686 喷子 双管"}, {"S12K", "20103000003", "s12k 12k 喷子"},
        {"98K", "20104000001", "98k kar98k 狙"}, {"AWM", "20104000002", "awm 狙"}, {"SKS", "20104000003", "sks 连狙"},
        {"M24", "20104000004", "m24 狙"}, {"MK14", "20104000005", "mk14 连狙"}, {"Mini14", "20104000006", "mini14 mini 连狙"}, {"VSS", "20104000007", "vss 连狙"},
        {"UMP9", "20105000001", "ump ump9 冲锋"}, {"UZI", "20105000002", "uzi 乌兹 冲锋"}, {"Vector", "20105000003", "vector 维克托 冲锋"}, {"汤姆逊", "20105000004", "tommy 汤姆 冲锋"},
        {"十字弩", "20106000001", "弩 crossbow"},
        {"P92", "20201000001", "p92 手枪"}, {"P1911", "20201000002", "p1911 1911 手枪"}, {"R1895", "20201000003", "r1895 1895 左轮 手枪"}, {"P18C", "20201000004", "p18c 18c 手枪"},
        {"平底锅", "20301000002", "锅 pan"}, {"砍刀", "20301000003", "刀 machete"}, {"镰刀", "20301000004", "镰 sickle"}, {"撬棍", "20301000005", "棍 crowbar"},
    };
    static final String[] MED = {"40100000001", "40100000002", "40100000003", "40101000001", "40101000002", "40101000003"};
    // v1.38：按分类选枪（编号 = 游戏枚举顺序，已实测 S12K=20103000003、平底锅=20301000002、92式=20201000005）；刷枪顺带对应子弹
    // 子弹编号 2060000000N，N = EItemAmmo：1=5.56 2=7.62 3=9mm 4=.45 5=12号 6=.300马格南 7=弩箭 8=5.8
    static final String[][] CATS = {
        {"步枪", "M416:20101000001:1", "AKM:20101000002:2", "M16A4:20101000003:1", "SCAR-L:20101000004:1", "GROZA:20101000005:2", "M249:20102000001:1"},
        {"冲锋枪", "UMP9:20105000001:3", "UZI:20105000002:3", "Vector:20105000003:3", "汤姆逊:20105000004:4"},
        {"喷子", "S1897:20103000001:5", "S686:20103000002:5", "S12K:20103000003:5"},
        {"狙击枪", "98K:20104000001:2", "AWM:20104000002:6", "M24:20104000004:2"},
        {"狙击步枪", "SKS:20104000003:2", "MK14:20104000005:2", "Mini14:20104000006:1", "VSS:20104000007:3", "QBU88:20104000008:1"},
        {"手枪", "P92:20201000001:3", "P1911:20201000002:4", "R1895:20201000003:2", "P18C:20201000004:3", "92式:20201000005:8", "R45:20201000006:4", "短管喷子:20201000007:5"},
        {"近战", "平底锅:20301000002:0", "砍刀:20301000003:0", "镰刀:20301000004:0", "撬棍:20301000005:0"},
        {"特殊武器", "十字弩:20106000001:7"},
        {"投掷物", "手雷:20401000001:0", "震爆弹:20401000002:0", "烟雾弹:20401000003:0", "燃烧瓶:20401000004:0", "苹果:20401000005:0"},
        {"子弹", "5.56:20600000001:0", "7.62:20600000002:0", "9mm:20600000003:0", ".45:20600000004:0", "12号:20600000005:0", ".300马格南:20600000006:0", "弩箭:20600000007:0", "5.8:20600000008:0", "信号弹:20600000009:0"},
    };
    static int cat = -1;
    // v1.39：信号枪编号不在武器枚举里 → 按顺序试候选，刷出来后从游戏日志读武器名（BP_NZWeap_xxx / WeaponBase/Wxxx_名字）
    static final String[] FLARE = {"20201000008", "20201000009", "20201000010", "20201000011", "20202000001", "20203000001",
            "20106000002", "20106000003", "20107000001", "20108000001", "20204000001", "20205000001", "20206000001", "20701000001"};
    static int flareAt = 0;
    static void flare(final Activity a) {
        String k = okId.get("信号枪");
        if (k == null && Mods.sp != null) { k = Mods.sp.getString("flare", null); if (k != null) okId.put("信号枪", k); }
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
            if (w.toLowerCase(java.util.Locale.ROOT).contains("flare")) {
                okId.put("信号枪", id); give(a, "20600000009", 5);
                if (Mods.sp != null) Mods.sp.edit().putString("flare", id).apply();
            } else toast(a, "刷出的是 " + (w.isEmpty() ? "别的东西" : w) + "，再点一次信号枪");
        }, 1500);
    }
    static void gunGrid(final Activity a, final Ui u, final LinearLayout root, final int c) {
        LinearLayout r = null; int n = 0;
        java.util.List<String[]> items = new java.util.ArrayList<>();
        if ("特殊武器".equals(CATS[c][0])) items.add(new String[]{"信号枪", "flare", "0"});
        for (int j = 1; j < CATS[c].length; j++) items.add(CATS[c][j].split(":"));
        if ("特殊武器".equals(CATS[c][0])) items.add(new String[]{"信号弹×5", "20600000009", "0"});
        for (final String[] it : items) {
            if (n % 3 == 0) r = row(a, root);
            btn(a, u, r, it[0], () -> {
                if ("flare".equals(it[1])) { flare(a); return; }
                if (it[1].startsWith("204")) { give(a, it[1], 3, it[0]); return; }
                if (it[1].startsWith("206")) { give(a, it[1], it[1].endsWith("9") ? 5 : it[1].endsWith("7") ? 30 : 150, it[0]); return; }
                give(a, it[1], 1);
                if (!"0".equals(it[2])) give(a, "2060000000" + it[2], "7".equals(it[2]) ? 30 : 60);
                after(a, it[0]);
            });
            n++;
        }
        while (n % 3 != 0) { View sp = new View(a); r.addView(sp, new LinearLayout.LayoutParams(0, 1, 1)); n++; }
    }

    interface FCb { void on(float f); }
    /** 1.0 ~ 10.0，步长 0.1 */
    static void slider(final Activity a, Ui u, LinearLayout root, String name, float v, final FCb cb) {
        LinearLayout r = row(a, root);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(a); t.setText(name); t.setTextColor(TXT); t.setTextSize(13);
        r.addView(t, new LinearLayout.LayoutParams(u.dp(36), -2));
        android.widget.SeekBar sb = new android.widget.SeekBar(a);
        sb.setMax(90); sb.setProgress(Math.round((v - 1f) * 10));
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) { if (user) cb.on(1f + p / 10f); }
            public void onStartTrackingTouch(android.widget.SeekBar s) {}
            public void onStopTrackingTouch(android.widget.SeekBar s) { if (giveSt == 1 || giveSt == 3) toast(a, "先在补丁更新里点「更新补丁」"); }
        });
        r.addView(sb, new LinearLayout.LayoutParams(0, u.dp(36), 1));
    }

    static String fmt(float f) { return f == (int) f ? String.valueOf((int) f) : String.format(java.util.Locale.US, "%.1f", f); }
    static void needGive(Activity a) {
        if (giveSt == 1 || giveSt == 3) toast(a, "先在补丁更新里点「更新补丁」");
        else toast(a, "加速×" + fmt(Mods.speed) + " 高跳×" + fmt(Mods.jump) + "（进图后生效）");
    }
    // v1.36 平板视角：控制器自带的 FOV 命令（0 = 恢复默认）
    static int fov = 0;
    static void setFov(Activity a, int f) {
        fov = f;
        boolean ok = Con.exec("FOV " + f);
        LogCap.note("平板视角 FOV " + f + " 发送=" + ok);
        if (!ok) toast(a, "视野没改成");
    }

    static void fill(final Activity a, final Ui u, final LinearLayout root) {
        root.removeAllViews();
        if (tab >= 1 && tab <= CATS.length) { gunGrid(a, u, root, tab - 1); return; }
        if (tab == CATS.length + 1) {
            head(a, u, root, "衣服（只显示能穿的，自动跳过没有的编号）");
            LinearLayout cr = row(a, root);
            btn(a, u, cr, "上一件", () -> giveCoat(a, -1));
            btn(a, u, cr, "下一件", () -> giveCoat(a, 1));
            coatLbl = label(a, root, coatNow.isEmpty() ? "" : "当前：" + coatNow, 12, false, SUBT);
            head(a, u, root, "输入编号刷衣服");
            LinearLayout ir = row(a, root);
            final android.widget.EditText et = new android.widget.EditText(a);
            et.setHint("例如 30323000002"); et.setTextColor(TXT); et.setHintTextColor(SUBT); et.setTextSize(14);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); et.setSingleLine(true);
            ir.addView(et, new LinearLayout.LayoutParams(0, -2, 2));
            btn(a, u, ir, "刷", () -> {
                String id = et.getText().toString().trim();
                if (!id.matches("\\d{11}")) { toast(a, "编号是 11 位数字"); return; }
                giveTry(a, java.util.Collections.singletonList(id), 0, "编号 " + id);
                coatNow = id; if (coatLbl != null) coatLbl.setText("当前：" + id);
            });
            label(a, root, "外套 30315…，连帽 30316…，连体衣 30323…，男款 301…，女款 302…", 11, false, SUBT);
            return;
        }
        if (tab == 0) {
            head(a, u, root, "功能");
            LinearLayout r1 = row(a, root);
            btn(a, u, r1, "无敌开/关", () -> { cmd(a, "EnableCheats"); cmd(a, "God", Con.godToggled() ? "无敌已开" : "无敌已关"); });
            btn(a, u, r1, "传送到地图中心", () -> toast(a, Con.tpCenter()));
            head(a, u, root, "游戏流程");
            LinearLayout fl = row(a, root);
            btn(a, u, fl, Mods.noPlane ? "非正常演习（不跳伞 不刷毒）" : "正常演习", () -> { String r = Mods.setFree(!Mods.noPlane); if (!"ok".equals(r)) toast(a, "没改成：" + r); fill(a, u, root); });
            LinearLayout fm = row(a, root);
            btn(a, u, fm, Mods.male ? "角色：男（大厅设置）" : "角色：女（大厅设置）", () -> { String r = Mods.setMale(!Mods.male); if (!"ok".equals(r)) toast(a, "没改成：" + r); fill(a, u, root); });
            final TextView sl = label(a, root, "", 14, true, TXT); sl.setPadding(0, u.dp(6), 0, u.dp(2));
            final Runnable sTxt = () -> sl.setText("● 人物加速 ×" + fmt(Mods.speed) + "　高跳 ×" + fmt(Mods.jump));
            sTxt.run();
            slider(a, u, root, "加速", Mods.speed, f -> { Mods.setMove(f, Mods.jump); sTxt.run(); });
            slider(a, u, root, "高跳", Mods.jump, f -> { Mods.setMove(Mods.speed, f); sTxt.run(); });
            if (!Mods.moveState.isEmpty() && !"已生效".equals(Mods.moveState) && (Mods.speed != 1f || Mods.jump != 1f)) label(a, root, "状态：" + Mods.moveState, 12, false, SUBT);
            head(a, u, root, "枪械");
            LinearLayout wm = row(a, root);
            btn(a, u, wm, Mods.noRecoil ? "无后座：开" : "无后座：关", () -> { Mods.setGun(!Mods.noRecoil, Mods.noShake, Mods.infAmmo); fill(a, u, root); });
            btn(a, u, wm, Mods.noShake ? "防抖：开" : "防抖：关", () -> { Mods.setGun(Mods.noRecoil, !Mods.noShake, Mods.infAmmo); fill(a, u, root); });
            btn(a, u, wm, Mods.infAmmo ? "无限子弹 ∞：开" : "无限子弹 ∞：关", () -> { Mods.setGun(Mods.noRecoil, Mods.noShake, !Mods.infAmmo); cmd(a, "InfiniteAmmo", null); fill(a, u, root); });
            head(a, u, root, "平板视角（视野 " + fov + "）");
            LinearLayout fv = row(a, root);
            for (final int f : new int[]{0, 90, 100, 110}) btn(a, u, fv, f == 0 ? "默认" : String.valueOf(f), () -> { setFov(a, f); fill(a, u, root); });
            head(a, u, root, "刷物品");
            String gt = giveText(a); if (giveSt != 2 && !gt.isEmpty()) label(a, root, gt, 12, false, SUBT);
            LinearLayout g0 = row(a, root);
            btn(a, u, g0, "一键开局装备", () -> {
                give(a, "20101000001", 1); give(a, "20600000001", 150);
                give(a, helmet3(), 1); give(a, "30305000003", 1); give(a, "30307000006", 1);
                for (String at : ATT) give(a, at, 1);
                after(a, "一键装备");
            });
            LinearLayout g3 = row(a, root);
            btn(a, u, g3, "全套配件", () -> { for (String at : ATT) give(a, at, 1); after(a, "全套配件"); });
            btn(a, u, g3, "手雷", () -> give(a, "20401000001", 3, "手雷"));
            LinearLayout g4 = row(a, root);
            btn(a, u, g4, "3级头", () -> giveTry(a, helmetIds(), 0, "3级头"));
            btn(a, u, g4, "3级甲", () -> giveTry(a, armorIds(), 0, "3级甲"));
            btn(a, u, g4, "背包", () -> give(a, "30307000006", 1, "背包"));
            LinearLayout s3 = row(a, root);
            btn(a, u, s3, "全套药品", () -> { for (String m : MED) give(a, m, 5); after(a, "全套药品"); });
        } else {
            head(a, u, root, "日志");
            final TextView st = label(a, root, LogCap.status(), 12, false, SUBT);
            LinearLayout r5 = row(a, root);
            btn(a, u, r5, "立即保存日志", () -> new Thread(() -> {
                LogCap.syncNow(a);
                a.runOnUiThread(() -> { st.setText(LogCap.status()); toast(a, "已保存到 下载/ZFrame"); });
            }).start());
            label(a, root, "内核 " + ZInit.CORE_VER, 11, false, SUBT).setPadding(0, u.dp(6), 0, 0);
        }
    }
}
