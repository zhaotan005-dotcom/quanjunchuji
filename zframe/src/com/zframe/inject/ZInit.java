package com.zframe.inject;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.*;

/** 注入到游戏里的启动入口：游戏一启动就挂上游戏菜单悬浮球，并加载框架里给这个游戏启用的插件(.so)。 */
public class ZInit extends ContentProvider {
    static final String TAG = "ZFrame";
    static final Uri MODS = Uri.parse("content://com.zframe.mods");

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        // v3.76 用户：删掉跨进程（不再找 ZFrame App 拿内核/插件），内核只走游戏包自带 + 在线更新（NetCore）
        boot(ctx);
        return true;
    }

    public static final int CORE_VER = 285;

    public static void boot(Context ctx) {
        try {
            Application app = (Application) ctx.getApplicationContext();
            String proc = android.os.Build.VERSION.SDK_INT >= 28 ? Application.getProcessName() : ctx.getPackageName();
            boolean main = ctx.getPackageName().equals(proc);
            if (NetCore.chain(ctx, main)) return;   // v2.52：有从 GitHub 下好的更新内核就交给它
            Bots.loadWant(ctx); MapMark.load(ctx);   // v2.55 记住的「自动刷人机」开关
            if (main) { Qcc.fix(ctx); Con.startWatcher(app);
                Patches.start(ctx); }
            if (main) app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityResumed(Activity a) { GameOverlay.attach(a); }
                public void onActivityPaused(Activity a) { GameOverlay.detach(a); }
                public void onActivityCreated(Activity a, Bundle b) {}
                public void onActivityStarted(Activity a) {}
                public void onActivityStopped(Activity a) {}
                public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                public void onActivityDestroyed(Activity a) {}
            });
            Log.i(TAG, "ZFrame 内核 " + CORE_VER + " 已注入 " + proc);
        } catch (Throwable t) {
            Log.e(TAG, "init fail", t);
        }
    }

    /** 从框架 App 的 ContentProvider 读取本游戏启用的 .so 插件，复制到私有目录后加载 */
    static void loadPlugins(Context ctx) {
        try {
            Uri list = MODS.buildUpon().appendPath("list").appendPath(ctx.getPackageName()).build();
            Cursor c = ctx.getContentResolver().query(list, null, null, null, null);
            if (c == null) return;
            File dir = new File(ctx.getCodeCacheDir(), "zframe_mods");
            dir.mkdirs();
            while (c.moveToNext()) {
                String name = c.getString(0);
                try {
                    Uri f = MODS.buildUpon().appendPath("file").appendPath(name).build();
                    File out = new File(dir, name);
                    try (ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(f, "r");
                         InputStream in = new FileInputStream(pfd.getFileDescriptor());
                         OutputStream o = new FileOutputStream(out)) {
                        byte[] b = new byte[1 << 16]; int r;
                        while ((r = in.read(b)) > 0) o.write(b, 0, r);
                    }
                    out.setReadOnly();
                    System.load(out.getAbsolutePath());
                    Log.i(TAG, "插件已加载 " + name);
                } catch (Throwable t) {
                    Log.e(TAG, "插件加载失败 " + name, t);
                }
            }
            c.close();
        } catch (Throwable t) {
            Log.w(TAG, "读取插件失败（框架未安装？）", t);
        }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
