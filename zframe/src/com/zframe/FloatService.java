package com.zframe;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.*;
import android.widget.TextView;

/** 游戏内悬浮球：点一下回到框架的修改器页，再点"返回游戏"回去。 */
public class FloatService extends Service {
    private WindowManager wm;
    private View ball;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("zf", "ZFrame悬浮窗", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "zf")
                .setContentTitle("ZFrame 悬浮修改器运行中")
                .setSmallIcon(android.R.drawable.ic_menu_manage).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);

        wm = getSystemService(WindowManager.class);
        TextView tv = new TextView(this);
        tv.setText("Z");
        tv.setTextSize(20);
        tv.setTextColor(0xffffffff);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xcc3366ff);
        tv.setBackground(bg);
        ball = tv;
        int sz = (int) (48 * getResources().getDisplayMetrics().density);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(sz, sz,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 20;
        lp.y = 300;
        tv.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int nx = sx + (int) (e.getRawX() - dx), ny = sy + (int) (e.getRawY() - dy);
                        if (Math.abs(nx - sx) > 10 || Math.abs(ny - sy) > 10) moved = true;
                        lp.x = nx; lp.y = ny; wm.updateViewLayout(ball, lp); return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            Intent it = new Intent(FloatService.this, MainActivity.class);
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            it.putExtra("tab", 3);
                            startActivity(it);
                        }
                        return true;
                }
                return false;
            }
        });
        wm.addView(ball, lp);
    }

    @Override
    public void onDestroy() {
        if (ball != null) wm.removeView(ball);
        super.onDestroy();
    }
}
