package com.zframe.inject;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.TextView;

/**
 * v2.53 用户：「以前游戏里有使用道具的专用键，加上去就行」。
 * 游戏自己的道具键在 Lua/UMG 界面里（pak 里，经典模式不显示，改不了），所以在对局画面上自己放一个圆形「道具」键：
 *  - 背包里有功能道具（草丛/伪装岩/人偶/盾/雷…）才出现，每 2 秒查一次（只读）。
 *  - 点一下：没拿出 → 拿出（背包 vt+0x4d0，和游戏道具键同一个函数）；已拿出 → 放下（vt+0x484）。
 *  - 长按：换下一个道具。拖动可以挪位置（记住）。
 * v3.00 起客户端也有（拿出/放下/收回都走游戏原生入口，客户端那边游戏自己发 RPC 给服务器）。
 */
final class PropBtn {
    static TextView btn; static Activity act; static WindowManager.LayoutParams lp; static int idx;

    /** 类名 → 中文名（类名像 BP_40201000005_Shrub_C） */
    static final String[][] CN = { {"Shrub", "草丛"}, {"Bush", "草丛"}, {"Grass", "草丛"}, {"Camou", "伪装岩"}, {"Rock", "伪装岩"}, {"Stone", "伪装岩"},
        {"Doll", "人偶标靶"}, {"Dummy", "人偶标靶"}, {"Chara", "人偶标靶"}, {"Target", "人偶标靶"}, {"Shiled", "盾"}, {"Shield", "盾"},
        {"LandMine", "地雷"}, {"Mine", "地雷"}, {"Trap", "陷阱"}, {"Scan", "扫描雷"}, {"LaunchPad", "弹射板"}, {"Jump", "弹射板"},
        {"Lift", "升降台"}, {"Tree", "伪装树"}, {"Box", "箱子"}, {"Barrel", "油桶"} };
    static String cn(String c) {
        if (c == null) return "道具";
        for (String[] x : CN) if (c.contains(x[0])) return x[1];
        String t = c.replace("BP_", "").replace("_C", "").replaceAll("\\d{6,}_?", "").replace('_', ' ').trim();
        return t.isEmpty() ? "道具" : t.length() > 8 ? t.substring(0, 8) : t;
    }

    static void attach(final Activity a) {
        // v3.00 用户：捡起道具后没有道具键。客户端包（ED 2）以前直接不放键 → 加入别人房间时永远没有。
        //  RE：拿出 DoSelectedFunctionalProp 0xfe9574 末尾 人物 Role==2（自主代理）时自己调 0xd11a88 发服务器；放下/收回也是游戏原生入口，客户端照用即可。
        try {
            detach();
            act = a;
            final float d = a.getResources().getDisplayMetrics().density;
            TextView t = new TextView(a);
            t.setTextColor(0xffffffff); t.setTextSize(11f); t.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0x99000000); g.setStroke((int) (1.5f * d), 0xccffffff);
            t.setBackground(g);
            int sz = (int) (58 * d);
            android.content.SharedPreferences sp = a.getSharedPreferences("zframe", 0);
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
            lp = new WindowManager.LayoutParams(sz, sz, WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = sp.getInt("propbtn_x", (int) (sw * 0.70f)); lp.y = sp.getInt("propbtn_y", (int) (sh * 0.30f));
            lp.setTitle("zframe_propbtn");
            t.setVisibility(View.GONE);
            t.setOnTouchListener(new View.OnTouchListener() {
                float sx, sy; int ox, oy; boolean moved, longDone; final Runnable lng = () -> { longDone = true; tap(true); };
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; moved = false; longDone = false; v.postDelayed(lng, 600); v.setAlpha(0.6f); return true;
                        case MotionEvent.ACTION_MOVE:
                            if (Math.abs(e.getRawX() - sx) + Math.abs(e.getRawY() - sy) > 12 * d) { moved = true; v.removeCallbacks(lng); }
                            if (moved) { lp.x = ox + (int) (e.getRawX() - sx); lp.y = oy + (int) (e.getRawY() - sy); try { a.getWindowManager().updateViewLayout(v, lp); place2(d); if (btn2 != null) a.getWindowManager().updateViewLayout(btn2, lp2); } catch (Throwable ignored) {} }
                            return true;
                        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                            v.removeCallbacks(lng); v.setAlpha(1f);
                            if (moved) a.getSharedPreferences("zframe", 0).edit().putInt("propbtn_x", lp.x).putInt("propbtn_y", lp.y).apply();
                            else if (!longDone && e.getActionMasked() == MotionEvent.ACTION_UP) tap(false);
                            return true;
                    }
                    return false;
                }
            });
            a.getWindowManager().addView(t, lp);
            btn = t;
            attach2(a, d);
            final Runnable[] poll = new Runnable[1];
            poll[0] = () -> { if (btn != t || t.getParent() == null) return; refresh(); t.postDelayed(poll[0], 2000); };
            t.postDelayed(poll[0], 1500);
        } catch (Throwable e) { LogCap.note("道具键：放不上 " + e); }
    }

    static void detach() {
        TextView t = btn, t2 = btn2; Activity a = act; btn = null; btn2 = null;
        if (t != null && a != null) try { a.getWindowManager().removeViewImmediate(t); } catch (Throwable ignored) {}
        if (t2 != null && a != null) try { a.getWindowManager().removeViewImmediate(t2); } catch (Throwable ignored) {}
    }
    /** v2.59 用户：人偶没有模型 → 没有场景模型类（TargetProp 空）的不进道具键 */
    static java.util.ArrayList<Prop.It> usable() {
        java.util.ArrayList<Prop.It> o = new java.util.ArrayList<>();
        for (Prop.It it : Prop.useList) if (it.target != null) o.add(it);
        return o;
    }
    /** v2.60 用户：盾收不回、伪装石头取消不了 → 道具键下面的小键「收回 / 取消伪装」，有放下的东西才出现 */
    static TextView btn2; static WindowManager.LayoutParams lp2;
    static void attach2(final Activity a, float d) {
        TextView t = new TextView(a);
        t.setTextColor(0xffffffff); t.setTextSize(10.5f); t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(); g.setCornerRadius(14 * d); g.setColor(0x99000000); g.setStroke((int) (1.2f * d), 0xccffffff);
        t.setBackground(g);
        lp2 = new WindowManager.LayoutParams((int) (64 * d), (int) (28 * d), WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp2.gravity = Gravity.TOP | Gravity.START; lp2.setTitle("zframe_propbtn2");
        place2(d);
        t.setVisibility(View.GONE);
        t.setOnClickListener(v -> {
            if (busy) return; busy = true;
            new Thread(() -> {
                String msg = null;
                try {
                    Prop.Placed p = Prop.lastPlaced();
                    if (p == null) msg = "附近没有放下的道具";
                    else { String r = Prop.recycle(p); if (!"ok".equals(r)) msg = r; else if (!Prop.camo(p.cls)) msg = "收回中（游戏读条）"; }   // v2.98 原生回收，结果和日志由 Prop.watchRecycle 处理
                } catch (Throwable e) { msg = "出错 " + e; }
                finally {
                    busy = false; final String m = msg;
                    if (m != null) a.runOnUiThread(() -> GameOverlay.toast(a, m));
                    try { Thread.sleep(600); } catch (Throwable ignored) {}
                    refresh();
                }
            }, "zf-propbtn2").start();
        });
        try { a.getWindowManager().addView(t, lp2); btn2 = t; } catch (Throwable e) { LogCap.note("收回键：放不上 " + e); }
    }
    static void place2(float d) { if (lp2 != null && lp != null) { lp2.x = lp.x - (int) (3 * d); lp2.y = lp.y + (int) (62 * d); } }

    static volatile boolean busy;
    /** 只读查背包，决定显不显示、写什么字 */
    static void refresh() {
        final TextView t = btn; if (t == null || busy) return;
        if (!Con.inMatch || !GameOverlay.lobbySeen) { t.setVisibility(View.GONE); TextView b2 = btn2; if (b2 != null) b2.setVisibility(View.GONE); if (!Con.inMatch) synchronized (Prop.placed) { Prop.placed.clear(); } return; }
        new Thread(() -> {
            try { Jet.tick(); } catch (Throwable ignored) {}   // v2.66 喷气背包卡键位
            String r = Prop.scanUse(true);
            java.util.ArrayList<Prop.It> l = usable();
            final boolean show = true;   // v2.56：对局里一直显示（认不出道具时点一下会把背包信息传给我）
            final boolean has = "ok".equals(r) && !l.isEmpty();
            String s = "道具";
            if (has) {
                Prop.It sel = null; for (Prop.It it : l) if (it.sel) sel = it;
                if (sel != null) s = "放下\n" + cn(sel.cls);
                else { if (idx >= l.size()) idx = 0; s = cn(l.get(idx).cls) + (l.size() > 1 ? "\n" + (idx + 1) + "/" + l.size() : ""); }
            }
            final String txt = s;
            Prop.Placed pl = Prop.lastPlaced();
            final String t2 = pl == null ? null : Prop.camo(pl.cls) ? "取消伪装" : "收回" + cn(pl.cls);
            t.post(() -> {
                t.setVisibility(show ? View.VISIBLE : View.GONE); t.setText(txt); t.setAlpha(has ? 1f : 0.45f);
                TextView b2 = btn2; if (b2 != null) { b2.setVisibility(t2 != null ? View.VISIBLE : View.GONE); if (t2 != null) b2.setText(t2.length() > 5 ? t2.substring(0, 5) : t2); }
            });
        }, "zf-propbtn").start();
    }

    static void tap(final boolean next) {
        if (busy) return; busy = true;
        final Activity a = act;
        new Thread(() -> {
            String msg = null;
            try {
                Prop.scanUse(true);
                java.util.ArrayList<Prop.It> l = usable();
                if (l.isEmpty() && !Prop.useList.isEmpty()) { msg = "背包里的道具手机上没有模型，放不出来"; return; }
                if (l.isEmpty()) {   // v2.56 用户：捡起来了但没有道具键 → 把道具槽/背包类名传上来查
                    Prop.useState = Prop.rawDump();
                    LogCap.note("道具键：" + Prop.useState);
                    if (a != null) AvatarLog.export(a, "prop-");
                    msg = "没认出道具，背包信息已自动上传"; return;
                }
                Prop.It sel = null; for (Prop.It it : l) if (it.sel) sel = it;
                if (next) {   // 长按：换下一个（已拿出的话直接拿出下一个）
                    if (l.size() < 2) { msg = "只有一个道具"; return; }
                    int cur = sel != null ? l.indexOf(sel) : idx;
                    idx = (cur + 1) % l.size();
                    if (sel != null) { String r = Prop.select(l.get(idx)); if (!"ok".equals(r)) msg = r; }
                    else msg = "换成 " + cn(l.get(idx).cls);
                    return;
                }
                if (sel == null) {
                    if (idx >= l.size()) idx = 0;
                    String r = Prop.select(l.get(idx));
                    if (!"ok".equals(r)) msg = r;
                    else if (l.get(idx).target == null) msg = cn(l.get(idx).cls) + "：手机上没有它的场景模型，可能放不出来";
                } else {   // v2.98：游戏原生放下（背包 vt+0x484 → 读条 → FinishUsingItemCastTime → 生成 + 扣数量），结果由 Prop.watchPlace 记录/上传
                    String r = Prop.place();
                    if (!"ok".equals(r)) msg = r;
                }
            } catch (Throwable e) { msg = "出错 " + e; }
            finally {
                busy = false;
                final String m = msg;
                if (m != null && a != null) a.runOnUiThread(() -> GameOverlay.toast(a, m));
                try { Thread.sleep(400); } catch (Throwable ignored) {}
                refresh();
            }
        }, "zf-propbtn").start();
    }
}
