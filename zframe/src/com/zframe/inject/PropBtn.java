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
 * 单机/服务端才有（客户端放道具要服务器做）。
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
        if (Ed.ED == 2) return;
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
                            if (moved) { lp.x = ox + (int) (e.getRawX() - sx); lp.y = oy + (int) (e.getRawY() - sy); try { a.getWindowManager().updateViewLayout(v, lp); } catch (Throwable ignored) {} }
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
            final Runnable[] poll = new Runnable[1];
            poll[0] = () -> { if (btn != t || t.getParent() == null) return; refresh(); t.postDelayed(poll[0], 2000); };
            t.postDelayed(poll[0], 1500);
        } catch (Throwable e) { LogCap.note("道具键：放不上 " + e); }
    }

    static void detach() {
        TextView t = btn; Activity a = act; btn = null;
        if (t != null && a != null) try { a.getWindowManager().removeViewImmediate(t); } catch (Throwable ignored) {}
    }

    static volatile boolean busy;
    /** 只读查背包，决定显不显示、写什么字 */
    static void refresh() {
        final TextView t = btn; if (t == null || busy) return;
        if (!Con.inMatch || Ed.ED == 2 || !GameOverlay.lobbySeen) { t.setVisibility(View.GONE); return; }
        new Thread(() -> {
            String r = Prop.scanUse(true);
            java.util.ArrayList<Prop.It> l = Prop.useList;
            final boolean show = "ok".equals(r) && !l.isEmpty();
            String s = "道具";
            if (show) {
                Prop.It sel = null; for (Prop.It it : l) if (it.sel) sel = it;
                if (sel != null) s = "放下\n" + cn(sel.cls);
                else { if (idx >= l.size()) idx = 0; s = cn(l.get(idx).cls) + (l.size() > 1 ? "\n" + (idx + 1) + "/" + l.size() : ""); }
            }
            final String txt = s;
            t.post(() -> { t.setVisibility(show ? View.VISIBLE : View.GONE); t.setText(txt); });
        }, "zf-propbtn").start();
    }

    static void tap(final boolean next) {
        if (busy) return; busy = true;
        final Activity a = act;
        new Thread(() -> {
            String msg = null;
            try {
                Prop.scanUse(true);
                java.util.ArrayList<Prop.It> l = Prop.useList;
                if (l.isEmpty()) { msg = "背包里没有道具"; return; }
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
                } else {
                    String r = Prop.use();
                    if (!"ok".equals(r) && !"ok2".equals(r)) msg = r;
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
