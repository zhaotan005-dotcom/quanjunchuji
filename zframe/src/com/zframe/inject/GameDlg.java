package com.zframe.inject;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/**
 * v2.56 用户：选模式框 / 更新弹窗照游戏自己的「提示」框做（截图：深色底、金色标题条、白字、灰「取消」金「确认」），
 * 更新时用进度条显示下载进度（游戏自带的更新进度条在腾讯 UI 里用不了，这里仿一个）。
 */
final class GameDlg {
    static final int BG = 0xF21A1D24, HEAD_L = 0xFFC9A34A, HEAD_R = 0xFF2A2620, GOLD = 0xFFF2B33A, GOLD2 = 0xFFE09A1E,
        GRAY = 0xFFE9ECF2, GRAY2 = 0xFFC9CED8, WHITE = 0xFFFFFFFF, SUB = 0xFFB7BCC6, DARK = 0xFF2A2A2A, TRACK = 0xFF3A3E48;

    static int dp(Activity a, float v) { return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f); }

    /** 整个框：返回 [外层全屏背景, 内容区] */
    static View[] frame(Activity a, String title, final Runnable onClose) {
        FrameLayout bg = new FrameLayout(a);
        bg.setBackgroundColor(0x88000000); bg.setClickable(true);
        LinearLayout box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bd = new GradientDrawable(); bd.setColor(BG); bd.setStroke(Math.max(1, dp(a, 1)), 0x55C9A34A);
        box.setBackground(bd);
        // 标题条：左亮金到右暗
        FrameLayout head = new FrameLayout(a);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{HEAD_L, 0xFF8A6A2A, HEAD_R});
        head.setBackground(hg);
        View bar = new View(a); bar.setBackgroundColor(0xFFFFE08A);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(a, 3), -1); bl.gravity = Gravity.START; bl.setMargins(dp(a, 8), dp(a, 7), 0, dp(a, 7));
        head.addView(bar, bl);
        TextView t = new TextView(a); t.setText(title); t.setTextColor(WHITE); t.setTextSize(16); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(a, 18), 0, dp(a, 48), 0); t.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(t, new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER_VERTICAL | Gravity.START));
        if (onClose != null) {
            TextView x = new TextView(a); x.setText("✕"); x.setTextColor(0xFF3A2A10); x.setTextSize(18); x.setGravity(Gravity.CENTER);
            GradientDrawable xg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFF7D774, GOLD2}); x.setBackground(xg);
            x.setOnClickListener(v -> onClose.run());
            head.addView(x, new FrameLayout.LayoutParams(dp(a, 40), -1, Gravity.END));
        }
        box.addView(head, new LinearLayout.LayoutParams(-1, dp(a, 42)));   // v2.58：固定高度（-2 时竖条 MATCH_PARENT 把标题条撑满全屏）
        LinearLayout body = new LinearLayout(a); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a, 26), dp(a, 18), dp(a, 26), dp(a, 18));
        box.addView(body, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(a, 380), -2); blp.gravity = Gravity.CENTER;
        bg.addView(box, blp);
        return new View[]{bg, body};
    }

    static TextView text(Activity a, LinearLayout p, String s, float size, int color) {
        TextView t = new TextView(a); t.setText(s); t.setTextColor(color); t.setTextSize(size); t.setGravity(Gravity.CENTER);
        p.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    /** 游戏风按钮：gold=true 金底深字（确认），false 灰白底蓝灰字（取消） */
    static TextView button(Activity a, String s, boolean gold, final Runnable r) {
        TextView b = new TextView(a); b.setText(s); b.setTextSize(15); b.setGravity(Gravity.CENTER); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(gold ? 0xFF3A2400 : 0xFF2F3E5C);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, gold ? new int[]{0xFFFFC85A, GOLD2} : new int[]{WHITE, GRAY2});
        g.setStroke(Math.max(1, dp(a, 1)), gold ? 0xFFFFE08A : 0xFF9FB0D0);
        b.setBackground(g);
        b.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    static LinearLayout row(Activity a, LinearLayout p, int topDp) {
        LinearLayout r = new LinearLayout(a); r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(a, topDp);
        p.addView(r, lp);
        return r;
    }
    static void add(Activity a, LinearLayout r, View b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(a, 10), 0, dp(a, 10), 0);
        r.addView(b, lp);
    }

    /** 进度条：返回 [条外框, 填充] */
    static View[] progress(Activity a, LinearLayout p) {
        FrameLayout tr = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable(); g.setColor(TRACK); g.setCornerRadius(dp(a, 3)); tr.setBackground(g);
        View fill = new View(a);
        GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFD36A, GOLD2}); fg.setCornerRadius(dp(a, 3));
        fill.setBackground(fg);
        tr.addView(fill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(a, 6)); lp.topMargin = dp(a, 8);
        p.addView(tr, lp);
        return new View[]{tr, fill};
    }
    static void setPct(View[] pb, int pct) {
        View tr = pb[0], fill = pb[1];
        tr.post(() -> {
            int w = tr.getWidth(); if (w <= 0) { tr.postDelayed(() -> setPct(pb, pct), 100); return; }
            ViewGroup.LayoutParams l = fill.getLayoutParams(); l.width = w * Math.max(0, Math.min(100, pct)) / 100; fill.setLayoutParams(l);
        });
    }

    static WindowManager.LayoutParams wl(String title) {
        WindowManager.LayoutParams wl = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (android.os.Build.VERSION.SDK_INT >= 28) wl.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        wl.setTitle(title);
        return wl;
    }

    // ---------------- 大厅里：新版本下好了 → 弹「提示」框 ----------------
    static volatile boolean prompted; static volatile View upView;
    static void updatePrompt() {
        final Activity a = Con.act;
        if (a == null || prompted || GameOverlay.chooser != null || NetCore.readyVer <= ZInit.CORE_VER) return;
        prompted = true;
        a.runOnUiThread(() -> {
            try {
                final Runnable close = () -> { View v = upView; upView = null; if (v != null) try { a.getWindowManager().removeViewImmediate(v); } catch (Throwable ignored) {} };
                View[] f = frame(a, "更新", close);
                LinearLayout body = (LinearLayout) f[1];
                text(a, body, "新版本已下载完成，是否立即重启更新？", 15, WHITE).setPadding(0, dp(a, 8), 0, dp(a, 4));
                text(a, body, "当前 " + GameOverlay.VER + "　·　新版内核 " + NetCore.readyVer, 11.5f, SUB);
                View[] pb = progress(a, body); setPct(pb, 100);
                LinearLayout r = row(a, body, 18);
                add(a, r, button(a, "取消", false, close));
                add(a, r, button(a, "确认", true, () -> NetCore.restart(a)));
                a.getWindowManager().addView(f[0], wl("zframe_update"));
                upView = f[0];
            } catch (Throwable t) { LogCap.note("更新弹窗：" + t); }
        });
    }
}
