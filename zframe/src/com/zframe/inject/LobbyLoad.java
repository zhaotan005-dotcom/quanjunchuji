package com.zframe.inject;

import android.app.Activity;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** v3.77 用户：进大厅太快，衣服/人物还没加载完就看到了 → 进大厅时盖一层加载页（进度条），
 *  日志看到人物装好（HideLoadingProgress / OnChangeHairColor）后再等 2.5 秒贴图加载，走到 100% 再淡出。最长 15 秒。 */
final class LobbyLoad {
    static volatile View view; static volatile long startAt, readyAt;
    static volatile String state = "大厅加载页：没出现过";

    static void line(String l) {
        if (l.contains("Browse: /Game/Maps/Erangle_RoleHall")) show();
        else if (view != null && readyAt == 0 && (l.contains("HideLoadingProgress") || l.contains("OnChangeHairColor"))) readyAt = System.currentTimeMillis();
    }

    static void show() {
        final Activity a = Con.act;
        if (a == null || view != null) return;
        startAt = System.currentTimeMillis(); readyAt = 0;
        a.runOnUiThread(() -> {
            try {
                if (view != null) return;
                FrameLayout root = new FrameLayout(a);
                root.setBackgroundColor(0xFF0E1015);
                root.setClickable(true);
                LinearLayout box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL);
                int pad = GameDlg.dp(a, 48);
                box.setPadding(pad, 0, pad, GameDlg.dp(a, 36));
                final TextView t = new TextView(a); t.setTextColor(GameDlg.SUB); t.setTextSize(13); t.setText("正在加载大厅…");
                box.addView(t);
                final View[] pb = GameDlg.progress(a, box);
                root.addView(box, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
                TextView title = new TextView(a); title.setTextColor(Color.WHITE); title.setTextSize(22); title.setText("全军出击");
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                root.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
                a.getWindowManager().addView(root, GameDlg.wl("zf-lobbyload"));
                view = root; state = "大厅加载页：显示中";
                root.post(new Runnable() { public void run() {
                    long now = System.currentTimeMillis(), el = now - startAt;
                    int pct;
                    if (readyAt == 0) pct = (int) (90 * (1 - Math.exp(-el / 3000.0)));
                    else pct = Math.min(100, 90 + (int) ((now - readyAt) / 250));
                    if (pct >= 100 || el > 15000) {
                        GameDlg.setPct(pb, 100); t.setText("加载完成");
                        state = "大厅加载页：" + (readyAt == 0 ? "15 秒超时关" : "人物装好后关") + "（" + el + " ms）"; LogCap.note(state);
                        root.animate().alpha(0f).setDuration(300).withEndAction(() -> { try { a.getWindowManager().removeView(root); } catch (Throwable ignored) {} view = null; }).start();
                        return;
                    }
                    GameDlg.setPct(pb, pct); t.setText("正在加载大厅… " + pct + "%");
                    root.postDelayed(this, 100);
                }});
            } catch (Throwable e) { view = null; state = "大厅加载页：出错 " + e; LogCap.note(state); }
        });
    }
}
