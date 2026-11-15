package com.zframe;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * 给注入过的游戏提供插件：
 *   content://com.zframe.mods/list/<包名>  → 该游戏启用的 .so 列表
 *   content://com.zframe.mods/file/<名字>  → 插件文件
 */
public class ModsProvider extends ContentProvider {
    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "plugins");
        d.mkdirs();
        return d;
    }

    /** 插件作用范围：* 表示全部注入游戏，否则是逗号分隔的包名 */
    public static String scope(Context c, String name) {
        return c.getSharedPreferences("plugins", 0).getString(name, "*");
    }

    public static void setScope(Context c, String name, String v) {
        c.getSharedPreferences("plugins", 0).edit().putString(name, v).apply();
    }

    public static boolean enabled(Context c, String name) {
        return !c.getSharedPreferences("plugins", 0).getBoolean("off:" + name, false);
    }

    public static void setEnabled(Context c, String name, boolean on) {
        c.getSharedPreferences("plugins", 0).edit().putBoolean("off:" + name, !on).apply();
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        List<String> seg = uri.getPathSegments();
        MatrixCursor mc = new MatrixCursor(new String[]{"name"});
        if (seg.size() == 2 && seg.get(0).equals("list")) {
            String pkg = seg.get(1);
            File[] fs = dir(getContext()).listFiles();
            if (fs != null) for (File f : fs) {
                String n = f.getName();
                if (!n.endsWith(".so") || !enabled(getContext(), n)) continue;
                String sc = scope(getContext(), n);
                if (sc.equals("*") || Arrays.asList(sc.split(",")).contains(pkg)) mc.addRow(new Object[]{n});
            }
        }
        return mc;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() == 1 && seg.get(0).equals("core")) {
            // 游戏里的注入桩会来拿最新内核 dex
            File f = new File(getContext().getCacheDir(), "core.dex");
            try (java.io.InputStream in = getContext().getAssets().open("inject.dex");
                 java.io.OutputStream o = new java.io.FileOutputStream(f)) {
                byte[] b = new byte[1 << 16]; int r;
                while ((r = in.read(b)) > 0) o.write(b, 0, r);
            } catch (java.io.IOException e) { throw new FileNotFoundException(e.toString()); }
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        if (seg.size() != 2 || !seg.get(0).equals("file") || seg.get(1).contains("/") || seg.get(1).contains(".."))
            throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(dir(getContext()), seg.get(1)), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri u) { return "application/octet-stream"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
