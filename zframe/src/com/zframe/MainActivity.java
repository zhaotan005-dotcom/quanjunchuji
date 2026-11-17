package com.zframe;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import com.zframe.ui.Ui;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    static final int REQ_APK = 1, REQ_ZIP = 2, REQ_SO = 3, REQ_TOOL = 4;
    static final String OWN_MODS = "/data/adb/zframe/modules";
    static final String ACTION_INSTALL = "com.zframe.INSTALL_STATUS";

    final Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences sp;
    Ui u;
    LinearLayout content;
    LinearLayout nav;
    int tab = 0;
    volatile boolean root = false;
    volatile String backend = "none";
    String target = "";

    // 安装流程状态
    List<File> pendingApks;
    String pendingPkg;
    AlertDialog progress;
    TextView progressLog;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = new Ui(this);
        sp = getSharedPreferences("zf", MODE_PRIVATE);
        target = sp.getString("target", "");
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.CARD);

        LinearLayout rootView = u.col();
        rootView.setBackgroundColor(Ui.BG);
        rootView.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom());
            return ins.consumeSystemWindowInsets();
        });
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        content = u.col();
        int p = u.dp(16);
        content.setPadding(p, p, p, p);
        sv.addView(content);
        rootView.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = u.row();
        nav.setBackgroundColor(Ui.CARD);
        nav.setPadding(u.dp(6), u.dp(6), u.dp(6), u.dp(6));
        rootView.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        setContentView(rootView);

        IntentFilter f = new IntentFilter(ACTION_INSTALL);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(installRx, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(installRx, f);

        show(getIntent().getIntExtra("tab", 0));
        detectRoot();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(installRx); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.hasExtra("tab")) show(i.getIntExtra("tab", 0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tab == 1) show(1);
    }

    /** 启动时静默检测 root（有 root 的手机会弹一次授权），没有就走免 root 模式 */
    void detectRoot() {
        bg(() -> {
            boolean r = Root.hasRoot();
            String b = r ? Root.backend() : "none";
            root = r; backend = b;
            h.post(() -> show(tab));
        });
    }

    void bg(Runnable r) { new Thread(r).start(); }
    void toast(String s) { h.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }

    void buildNav() {
        nav.removeAllViews();
        String[] names = {"首页", "游戏", "模块"};
        String[] icons = {"◆", "▶", "▣"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout item = u.col();
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, u.dp(6), 0, u.dp(6));
            if (i == tab) item.setBackground(u.round(Ui.CARD2, 14));
            u.text(item, icons[i], 16, i == tab ? Ui.CYAN : Ui.SUB, true).setGravity(Gravity.CENTER);
            u.text(item, names[i], 12, i == tab ? Ui.TEXT : Ui.SUB, i == tab).setGravity(Gravity.CENTER);
            item.setOnClickListener(v -> show(idx));
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    void show(int t) {
        tab = 1;
        content.removeAllViews();
        nav.removeAllViews(); nav.setVisibility(android.view.View.GONE);
        showMain();
        if (true) return;
        switch (t) {
            case 0: showHome(); break;
            case 1: showGames(); break;
            case 2: showModules(); break;
            default: showModules();
        }
    }

    void header(String title, String subtitle) {
        u.text(content, title, 24, Ui.TEXT, true);
        if (subtitle != null) u.sub(content, subtitle).setPadding(0, 0, 0, u.dp(10));
    }


    // ================= v1.29 艾挽补丁更新：只留更新 =================
    static final String GAME = "com.tencent.tmgp.pubgm";
    void showMain() {
        header("补丁更新", "v" + version());
        PackageManager pm = getPackageManager();
        ApplicationInfo ai = null;
        try { ai = pm.getApplicationInfo(GAME, 0); } catch (Exception ignored) {}
        boolean ours = ai != null && Patcher.signedByUs(this, GAME);
        if (ours) { Set<String> g = games(); if (g.add(GAME)) saveGames(g); }
        LinearLayout c = u.card(content);
        LinearLayout r = u.row();
        c.addView(r);
        if (ai != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(ai.loadIcon(pm));
            r.addView(iv, new LinearLayout.LayoutParams(u.dp(48), u.dp(48)));
        }
        LinearLayout info = u.col();
        info.setPadding(u.dp(12), 0, 0, 0);
        r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        u.text(info, ai != null ? ai.loadLabel(pm).toString() : "全军出击", 16, Ui.TEXT, true);
        LinearLayout chips = u.row();
        info.addView(chips);
        if (ai == null) u.chip(chips, "未安装", Ui.RED);
        else if (ours) u.chip(chips, "已注入", Ui.CYAN);
        else u.chip(chips, "未注入", Ui.PURPLE);
        if (ours) {
            // v1.31：自动检测补丁/框架是不是最新，有更新才显示「更新补丁」
            final long stamp = gameStamp();
            if (updStamp != stamp) {
                u.sub(c, "正在检查补丁…");
                if (!updChecking) { updChecking = true; final ApplicationInfo fai = ai; bg(() -> {
                    String res = checkUpdate(fai);
                    updWhy = res; updStamp = stamp; updChecking = false;
                    h.post(() -> { if (tab == 1) show(1); });
                }); }
            } else if (updWhy != null && !updWhy.isEmpty()) {
                u.sub(c, "有更新：" + updWhy);
                u.primary(c, "更新补丁", v -> soundPatch(GAME));
            } else {
                u.chip(chips, "补丁已是最新", Ui.CYAN);
            }
            u.ghost(c, "启动游戏", v -> launchGame(GAME, false));
        } else {
            u.primary(c, "导入游戏安装包", v -> pick(REQ_APK));
            if (ai != null) u.ghost(c, "从已安装的游戏注入", v -> pickInstalled());
            if (pendingApks != null) u.ghost(c, "继续安装", v -> install(pendingApks, pendingPkg));
        }
    }

    static volatile long updStamp = -1; static volatile String updWhy; static volatile boolean updChecking;
    long gameStamp() {
        try { return getPackageManager().getPackageInfo(GAME, 0).lastUpdateTime ^ (long) Integer.parseInt(version().replace(".", "")) << 40; } catch (Throwable e) { return 0; }
    }
    /** 返回空串 = 都是最新；否则返回要更新的内容 */
    String checkUpdate(ApplicationInfo ai) {
        StringBuilder sb = new StringBuilder();
        File apk = new File(ai.sourceDir);
        try { if (Patcher.soundState(apk) == 1) sb.append("声音补丁 "); } catch (Throwable ignored) {}
        try { int g = Patcher.giveState(apk); if (g == 1 || g == 3) sb.append("刷物品补丁 "); } catch (Throwable ignored) {}
        try {
            byte[] mine = Patcher.readAll(getAssets().open("inject.dex"));
            boolean same = false, found = false;
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(apk)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry e = en.nextElement();
                    if (!e.getName().matches("^classes\\d*\\.dex$")) continue;
                    if (e.getSize() != mine.length) { if (e.getSize() > 400000) continue; }
                    byte[] d = Patcher.readAll(zf.getInputStream(e));
                    if (!Patcher.isOurDex(d)) continue;
                    found = true; same = java.util.Arrays.equals(d, mine); break;
                }
            }
            // v2.52：游戏每次启动都从 ZFrame/GitHub 热加载内核，游戏里自带的框架旧了不用重新打包
            if (false && found && !same) sb.append("游戏里的框架 ");
        } catch (Throwable ignored) {}
        return sb.toString().trim();
    }

    // ================= 首页 =================
    void showHome() {
        LinearLayout hero = u.card(content);
        hero.setBackground(u.round(Ui.CARD, 20));
        LinearLayout hr = u.row();
        hero.addView(hr);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        hr.addView(icon, new LinearLayout.LayoutParams(u.dp(64), u.dp(64)));
        LinearLayout ht = u.col();
        ht.setPadding(u.dp(14), 0, 0, 0);
        hr.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        u.text(ht, "ZFrame", 26, Ui.TEXT, true);
        u.sub(ht, "游戏模块框架 · v" + version());

        LinearLayout st = u.card(content);
        u.title(st, "运行环境");
        LinearLayout chips = u.row();
        chips.setPadding(0, u.dp(6), 0, u.dp(6));
        st.addView(chips);
        if (root) u.chip(chips, "ROOT · " + backendName(), Ui.GREEN);
        else u.chip(chips, "免 ROOT 模式", Ui.CYAN);
        u.chip(chips, "Android " + Build.VERSION.RELEASE, Ui.PURPLE);
        u.sub(st, Build.MANUFACTURER + " " + Build.MODEL);
        u.sub(st, root ? "已检测到 Root：可用系统模块，也能用免 Root 注入。"
                : "没有 Root 也能用：导入游戏时自动注入框架，进游戏点悬浮球 Z 打开游戏菜单。");

        LinearLayout q = u.card(content);
        u.title(q, "快速开始");
        u.sub(q, "1  游戏页导入 APK，或从已安装的游戏注入\n2  按提示卸载原版并安装注入版（原版存档会清空）\n3  打开游戏，点悬浮球 Z 打开游戏菜单\n4  更新 ZFrame 后游戏里的功能自动更新，不用重装游戏\n5  模块页可导入 .so 插件，注入的游戏启动时自动加载");
        u.primary(q, "去导入游戏", v -> show(1));

        LinearLayout n = u.card(content);
        u.title(n, "注意");
        u.sub(n, "只建议用于单机游戏。网游有反作弊，修改会封号；部分游戏有签名校验，注入后可能闪退。");
    }

    String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    String backendName() {
        switch (backend) {
            case "magisk": return "Magisk";
            case "ksu": return "KernelSU";
            case "apatch": return "APatch";
            case "su": return "SU";
            default: return "无";
        }
    }

    // ================= 游戏 =================
    Set<String> games() { return new TreeSet<>(sp.getStringSet("games", new HashSet<>())); }
    void saveGames(Set<String> s) { sp.edit().putStringSet("games", new HashSet<>(s)).apply(); }
    Set<String> tools() { return new HashSet<>(sp.getStringSet("tools", new HashSet<>())); }
    Set<String> patched() { return new HashSet<>(sp.getStringSet("patched", new HashSet<>())); }

    void showGames() {
        header("游戏", "导入后自动注入框架");
        LinearLayout c = u.card(content);
        u.primary(c, "＋ 导入游戏安装包（apk / apks / xapk）", v -> pick(REQ_APK));
        u.ghost(c, "从已安装的游戏注入", v -> pickInstalled());
        if (pendingApks != null) u.ghost(c, "继续安装上次注入的 " + pendingPkg, v -> install(pendingApks, pendingPkg));

        PackageManager pm = getPackageManager();
        Set<String> gs = games();
        Set<String> pt = patched();
        if (gs.isEmpty()) {
            LinearLayout e = u.card(content);
            u.sub(e, "还没有游戏。");
            return;
        }
        for (String pkg : gs) {
            LinearLayout g = u.card(content);
            LinearLayout r = u.row();
            g.addView(r);
            ImageView iv = new ImageView(this);
            String label;
            boolean installed = true;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                Drawable d = ai.loadIcon(pm);
                iv.setImageDrawable(d);
                label = ai.loadLabel(pm).toString();
            } catch (Exception e) { label = pkg; installed = false; }
            r.addView(iv, new LinearLayout.LayoutParams(u.dp(48), u.dp(48)));
            LinearLayout info = u.col();
            info.setPadding(u.dp(12), 0, 0, 0);
            r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            u.text(info, label, 16, Ui.TEXT, true);
            u.sub(info, pkg);
            LinearLayout chips = u.row();
            info.addView(chips);
            boolean isTool = tools().contains(pkg);
            if (isTool) u.chip(chips, "工具 · 共享用户", Ui.PURPLE);
            else if (pt.contains(pkg)) u.chip(chips, "已注入", Ui.CYAN);
            if (!installed) u.chip(chips, "未安装", Ui.RED);
            LinearLayout br = u.row();
            g.addView(br);
            u.primary(br, "启动", v -> launchGame(pkg, true));
            if (installed && !isTool) u.ghost(br, "修补游戏", v -> soundPatch(pkg));
            if (root) u.ghost(br, "Root改", v -> { target = pkg; sp.edit().putString("target", pkg).apply(); launchGame(pkg, true); show(3); });
            u.danger(br, "移除", v -> { Set<String> s = games(); s.remove(pkg); saveGames(s); show(1); });
        }
    }

    void pick(int req) {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        startActivityForResult(it, req);
    }

    void pickInstalled() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        final List<ApplicationInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : ris) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if (ai.packageName.equals(getPackageName()) || !seen.add(ai.packageName)) continue;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            apps.add(ai);
        }
        Collections.sort(apps, (a, b) -> a.loadLabel(pm).toString().compareTo(b.loadLabel(pm).toString()));
        String[] labels = new String[apps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).loadLabel(pm) + "\n" + apps.get(i).packageName;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("选择要注入的游戏")
                .setItems(labels, (d, w) -> {
                    ApplicationInfo ai = apps.get(w);
                    if (Patcher.signedByUs(this, ai.packageName)) {
                        Set<String> g = games(); g.add(ai.packageName); saveGames(g);
                        Set<String> p = patched(); p.add(ai.packageName); sp.edit().putStringSet("patched", p).apply();
                        toast("这个游戏已经注入过了，已加回列表");
                        show(1);
                        return;
                    }
                    List<File> in = new ArrayList<>();
                    in.add(new File(ai.sourceDir));
                    if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) in.add(new File(s));
                    startPatch(in, true);
                }).show();
    }

    void showProgress(String title) {
        LinearLayout l = u.col();
        l.setPadding(u.dp(20), u.dp(16), u.dp(20), u.dp(8));
        ProgressBar pb = new ProgressBar(this);
        l.addView(pb);
        progressLog = u.text(l, "", 13, Ui.SUB, false);
        progress = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title).setView(l).setCancelable(false).show();
    }

    void plog(String s) {
        h.post(() -> { if (progressLog != null) progressLog.append(s + "\n"); });
    }

    void endProgress() {
        h.post(() -> { if (progress != null) progress.dismiss(); progress = null; });
    }

    /** 从 SAF 文件导入：apk 直接用；apks/xapk 解出里面的 apk */
    void importFile(Uri uri, boolean tool) {
        showProgress("导入中");
        bg(() -> {
            try {
                File work = new File(getCacheDir(), "import");
                deleteRec(work);
                work.mkdirs();
                File src = new File(work, "src.bin");
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(src)) {
                    copy(in, out);
                }
                List<File> apks = new ArrayList<>();
                boolean isBundle = false;
                try (ZipFile zf = new ZipFile(src)) {
                    isBundle = zf.getEntry("AndroidManifest.xml") == null;
                    if (isBundle) {
                        Enumeration<? extends ZipEntry> en = zf.entries();
                        while (en.hasMoreElements()) {
                            ZipEntry e = en.nextElement();
                            String n = e.getName();
                            if (!n.endsWith(".apk") || n.contains("/")) continue;
                            File f = new File(work, n);
                            try (InputStream in = zf.getInputStream(e); OutputStream o = new FileOutputStream(f)) { copy(in, o); }
                            apks.add(f);
                        }
                    }
                }
                if (!isBundle) apks.add(src);
                if (apks.isEmpty()) throw new IOException("安装包里没有找到 apk");
                // base 放第一个
                File base = null;
                for (File f : apks) {
                    try (ZipFile zf = new ZipFile(f)) {
                        PackageInfo pi = getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
                        if (pi != null && (pi.splitNames == null || pi.splitNames.length == 0) && !f.getName().startsWith("config.")
                                && !f.getName().startsWith("split_")) { base = f; break; }
                    }
                }
                if (base == null) base = apks.get(0);
                apks.remove(base);
                apks.add(0, base);
                plog("找到 " + apks.size() + " 个 apk");
                endProgress();
                h.post(() -> { if (tool) doPatch(apks, false); else startPatch(apks, false); });
            } catch (Exception e) {
                endProgress();
                toast("导入失败：" + e.getMessage());
            }
        });
    }

    void startPatch(List<File> apks, boolean fromInstalled) {
        if (root && !fromInstalled) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("安装方式")
                    .setItems(new String[]{"注入框架（推荐）", "原样安装"}, (d, w) -> {
                        if (w == 0) doPatch(apks, true); else rootInstall(apks);
                    }).show();
        } else doPatch(apks, true);
    }

    void doPatch(List<File> apks, boolean inject) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            toast("请允许安装应用，然后回来再点");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        showProgress(inject ? "注入框架" : "打包");
        bg(() -> {
            try {
                File out = new File(getExternalFilesDir(null), "patched");
                deleteRec(out);
                out.mkdirs();
                File tmp = new File(out, "base_unsigned.apk");
                String pkg = Patcher.patchBase(this, apks.get(0), tmp, this::plog, inject);
                plog("签名中…");
                File base = new File(out, "base.apk");
                Patcher.sign(this, tmp, base);
                tmp.delete();
                List<File> result = new ArrayList<>();
                result.add(base);
                for (int i = 1; i < apks.size(); i++) {
                    plog("重签分包 " + apks.get(i).getName());
                    File so = new File(out, "split" + i + ".apk");
                    Patcher.resignSplit(this, apks.get(i), so);
                    result.add(so);
                }
                plog("完成");
                endProgress();
                Set<String> s = games(); s.add(pkg); saveGames(s);
                if (!inject) { Set<String> t = tools(); t.add(pkg); sp.edit().putStringSet("tools", t).apply(); }
                h.post(() -> {
                    pendingApks = result;
                    pendingPkg = pkg;
                    install(result, pkg);
                });
            } catch (Throwable e) {
                endProgress();
                h.post(() -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("注入失败").setMessage(String.valueOf(e)).setPositiveButton("好", null).show());
            }
        });
    }

    /** v1.20 声音补丁：读已安装的游戏，只改 libUE4.so 里一个模块名，用同一把钥匙重签后覆盖安装（不卸载，存档和数据包都在） */
    void soundPatch(String pkg) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            toast("请允许安装应用，然后回来再点");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (!Patcher.signedByUs(this, pkg)) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("不能更新")
                    .setMessage("这个游戏不是用本工具装的，覆盖安装会要求卸载，所以不做。").setPositiveButton("好", null).show();
            return;
        }
        final ApplicationInfo ai;
        try { ai = getPackageManager().getApplicationInfo(pkg, 0); } catch (Exception e) { toast("找不到游戏"); return; }
        showProgress("检查游戏");
        bg(() -> {
            int st;
            int mst = 0, gst = 0;
            try { gst = Patcher.giveState(new File(ai.sourceDir)); } catch (Throwable ignored) {}
            try { st = Patcher.soundState(new File(ai.sourceDir)); } catch (Throwable e) { st = -1; }
            try { mst = Patcher.mcziState(new File(ai.sourceDir)); } catch (Throwable ignored) {}
            endProgress();
            final int fs = st, fm = mst, fg = gst;
            h.post(() -> {
                if (fs == 1 || fs == 2) runSound(pkg, ai, true, fm == 1 ? 1 : 0);
                else new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("不能更新")
                        .setMessage(fs == 0 ? "libUE4.so 对应位置的内容和预期不一样（游戏版本不同），为了不闪退，不改。" : "读取游戏安装包失败。")
                        .setPositiveButton("好", null).show();
            });
        });
    }

    void confirmSound(String pkg, ApplicationInfo ai, boolean toAk, String msg) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(toAk ? "打声音补丁" : "还原声音补丁").setMessage(msg)
                .setPositiveButton(toAk ? "开始" : "还原", (d, w) -> runSound(pkg, ai, toAk))
                .setNegativeButton("取消", null).show();
    }

    void runSound(String pkg, ApplicationInfo ai, boolean toAk) { runSound(pkg, ai, toAk, 0); }

    void runSound(String pkg, ApplicationInfo ai, boolean toAk, int mczi) {
        showProgress(toAk ? "更新中" : "还原中");
        bg(() -> {
            try {
                File out = new File(getExternalFilesDir(null), "soundfix");
                deleteRec(out);
                out.mkdirs();
                File tmp = new File(out, "base_unsigned.apk");
                plog("准备…");
                Patcher.patchSound(new File(ai.sourceDir), tmp, toAk, Patcher.readAll(getAssets().open("inject.dex")), mczi, new File(getFilesDir(), "libMczi.orig.so"), this::plog);
                plog("签名（要几分钟）…");
                File base = new File(out, "base.apk");
                Patcher.sign(this, tmp, base);
                tmp.delete();
                if (Patcher.soundState(base) != (toAk ? 2 : 1)) throw new IOException("校验失败，没有安装，游戏没变");
                List<File> result = new ArrayList<>();
                result.add(base);
                if (ai.splitSourceDirs != null) for (String sd : ai.splitSourceDirs) {
                    File f = new File(sd);
                    File c = new File(out, "split_" + f.getName());
                    try (InputStream in = new FileInputStream(f); OutputStream o = new FileOutputStream(c)) { copy(in, o); }
                    result.add(c);
                }
                plog("安装…");
                endProgress();
                updStamp = -1;
                h.post(() -> {
                    // v1.31：先提示，2 秒后自动跳到安装界面
                    AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("补丁已做好").setMessage("即将跳转安装…\n在安装界面点「更新」/「安装」，装完打开游戏就行。")
                            .setCancelable(false).create();
                    d.show();
                    h.postDelayed(() -> { try { d.dismiss(); } catch (Throwable ignored) {} soundInstall = true; install(result, pkg); }, 2000);
                });
            } catch (Throwable e) {
                endProgress();
                h.post(() -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("更新失败").setMessage(String.valueOf(e) + "\n游戏没有被改动。").setPositiveButton("好", null).show());
            }
        });
    }

    boolean soundInstall = false;

    boolean isInstalled(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    void install(List<File> apks, String pkg) {
        if (!soundInstall && isInstalled(pkg) && !patched().contains(pkg) && !Patcher.signedByUs(this, pkg)) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("需要先卸载原版")
                    .setMessage("重新打包后的签名和原版不同，系统要求先卸载原版。\n原版本地存档会被清空（云存档不受影响）。\n卸载后回到 ZFrame，在游戏页点“继续安装”。")
                    .setPositiveButton("卸载原版", (d, w) -> startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))))
                    .setNegativeButton("取消", null).show();
            return;
        }
        bg(() -> {
            try {
                PackageInstaller pi = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(pkg);
                // v1.36：游戏只有 32 位库，安卓 16 每次启动都弹「与最新版 Android 不兼容」。
                // 系统规则：安装时指定了 ABI（abiOverride）的应用不弹这个框，所以安装时指定 armeabi-v7a（仍是 32 位，不改游戏）。
                if (!pkg.equals(getPackageName())) sp = withAbi(sp, "armeabi-v7a");
                int id = pi.createSession(sp);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    for (File f : apks) {
                        try (OutputStream o = s.openWrite(f.getName(), 0, f.length()); InputStream in = new FileInputStream(f)) {
                            copy(in, o);
                            s.fsync(o);
                        }
                    }
                    Intent it = new Intent(ACTION_INSTALL).setPackage(getPackageName()).putExtra("pkg", pkg);
                    PendingIntent p = PendingIntent.getBroadcast(this, id, it,
                            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
                    s.commit(p.getIntentSender());
                }
            } catch (Exception e) { toast("安装失败：" + e.getMessage()); }
        });
    }

    // v1.39：abiOverride 是隐藏字段，反射被系统挡了 → 用 Parcel 重写一份带 abiOverride 的参数
    // 安卓 16 SessionParams 写出顺序：mode, installFlags, installLocation, installReason, installScenario, sizeBytes(long),
    // appPackageName, appIcon(null=-1), appLabel(null=-1), originatingUri(null=-1), originatingUid, referrerUri(null=-1), abiOverride, ...
    static PackageInstaller.SessionParams withAbi(PackageInstaller.SessionParams sp, String abi) {
        android.os.Parcel p = android.os.Parcel.obtain(), q = android.os.Parcel.obtain();
        try {
            sp.writeToParcel(p, 0);
            p.setDataPosition(0);
            for (int i = 0; i < 5; i++) p.readInt();
            p.readLong(); p.readString();
            if (p.readInt() != -1 || p.readString() != null || p.readInt() != -1) throw new IllegalStateException("布局不对1");
            p.readInt();
            if (p.readInt() != -1) throw new IllegalStateException("布局不对2");
            int pos = p.dataPosition();
            if (p.readString() != null) throw new IllegalStateException("布局不对3");
            int after = p.dataPosition();
            q.appendFrom(p, 0, pos);
            q.writeString(abi);
            q.appendFrom(p, after, p.dataSize() - after);
            q.setDataPosition(0);
            PackageInstaller.SessionParams r = PackageInstaller.SessionParams.CREATOR.createFromParcel(q);
            android.util.Log.i("ZFrame", "abiOverride=" + abi + " 已写入安装参数");
            return r;
        } catch (Throwable t) {
            android.util.Log.w("ZFrame", "abiOverride 写入失败 " + t);
            return sp;
        } finally { p.recycle(); q.recycle(); }
    }

    final BroadcastReceiver installRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
            String pkg = i.getStringExtra("pkg");
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(confirm); }
            } else if (st == PackageInstaller.STATUS_SUCCESS) {
                if (soundInstall) { soundInstall = false; updStamp = -1; toast("更新完成，打开游戏就行"); show(1); return; }
                Set<String> p = patched(); p.add(pkg);
                sp.edit().putStringSet("patched", p).apply();
                pendingApks = null;
                toast("安装成功！打开游戏点悬浮球 Z 打开游戏菜单");
                show(1);
            } else if (soundInstall && st != PackageInstaller.STATUS_SUCCESS) {
                soundInstall = false;
                if (st != PackageInstaller.STATUS_FAILURE_ABORTED)
                    toast("更新没装上：" + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            } else if (st == PackageInstaller.STATUS_FAILURE_CONFLICT || st == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                Set<String> p = patched(); p.remove(pkg);
                sp.edit().putStringSet("patched", p).apply();
                if (pendingApks != null) install(pendingApks, pkg);
            } else if (st != PackageInstaller.STATUS_FAILURE_ABORTED) {
                toast("安装失败：" + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    };

    void rootInstall(List<File> apks) {
        showProgress("Root 安装");
        bg(() -> {
            StringBuilder files = new StringBuilder();
            String pkg = null;
            for (File f : apks) {
                files.append(' ').append(Root.q(f.getAbsolutePath()));
                if (pkg == null) {
                    PackageInfo pi = getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
                    if (pi != null) pkg = pi.packageName;
                }
            }
            String out = Root.run("total=0; for f in" + files + "; do s=$(stat -c %s \"$f\"); total=$((total+s)); done; "
                    + "sid=$(pm install-create -r -d -S $total | grep -o '[0-9]\\+'); i=0; for f in" + files + "; do "
                    + "cat \"$f\" | pm install-write -S $(stat -c %s \"$f\") $sid $i.apk -; i=$((i+1)); done; pm install-commit $sid");
            endProgress();
            if (out.contains("Success") && pkg != null) {
                Set<String> s = games(); s.add(pkg); saveGames(s);
                toast("安装成功");
            } else toast("安装失败：" + out);
            h.post(() -> show(1));
        });
    }

    void launchGame(String pkg, boolean hooks) {
        Intent it = getPackageManager().getLaunchIntentForPackage(pkg);
        if (it == null) { toast("找不到启动入口：" + pkg); return; }
        if (hooks && root) bg(() -> Root.run(gameHookScript(pkg)));
        startActivity(it);
    }

    String gameHookScript(String pkg) {
        return "for d in /data/adb/modules/* " + OWN_MODS + "/*; do "
                + "[ -f \"$d/zframe.sh\" ] && [ ! -f \"$d/disable\" ] && "
                + "(GAME_PKG=" + Root.q(pkg) + " MODDIR=\"$d\" sh \"$d/zframe.sh\" >/dev/null 2>&1 &); done";
    }

    // ================= 模块 =================
    void showModules() {
        header("模块", "游戏插件免 Root；系统模块需要 Root");
        // ---- 免 root 游戏插件 ----
        LinearLayout pc = u.card(content);
        u.title(pc, "游戏插件 (.so)");
        u.sub(pc, "注入过的游戏启动时自动加载。可设置作用于全部游戏或指定游戏。");
        u.primary(pc, "＋ 导入插件", v -> pick(REQ_SO));
        File[] fs = ModsProvider.dir(this).listFiles();
        if (fs == null || fs.length == 0) u.sub(pc, "暂无插件");
        else for (File f : fs) {
            final String n = f.getName();
            u.divider(pc);
            boolean on = ModsProvider.enabled(this, n);
            String sc = ModsProvider.scope(this, n);
            u.text(pc, n, 15, Ui.TEXT, true);
            u.sub(pc, (on ? "已启用" : "已停用") + " · " + (sc.equals("*") ? "全部注入游戏" : sc.replace(",", "、")) + " · " + (f.length() / 1024) + " KB");
            LinearLayout r = u.row();
            pc.addView(r);
            u.ghost(r, on ? "停用" : "启用", v -> { ModsProvider.setEnabled(this, n, !on); show(2); });
            u.ghost(r, "作用范围", v -> chooseScope(n));
            u.danger(r, "删除", v -> { f.delete(); show(2); });
        }

        // ---- root 系统模块 ----
        LinearLayout mc = u.card(content);
        u.title(mc, "系统模块 (.zip)");
        if (!root) {
            u.sub(mc, "未检测到 Root，系统模块不可用。游戏插件和注入框架不受影响。");
            return;
        }
        u.sub(mc, backend.equals("su") ? "未发现面具，使用 ZFrame 自带模块目录，启动游戏时运行模块的 zframe.sh。"
                : "通过 " + backendName() + " 刷入标准模块，重启生效。模块里放 zframe.sh 可在启动游戏时运行。");
        u.primary(mc, "＋ 刷入模块", v -> pick(REQ_ZIP));
        final LinearLayout list = u.col();
        mc.addView(list);
        bg(() -> {
            String out = Root.run("for d in /data/adb/modules/* " + OWN_MODS + "/*; do [ -f \"$d/module.prop\" ] || continue; "
                    + "echo \"@@$d\"; cat \"$d/module.prop\"; echo; [ -f \"$d/disable\" ] && echo __DISABLED; "
                    + "[ -f \"$d/remove\" ] && echo __REMOVE; done");
            h.post(() -> renderModules(list, out));
        });
    }

    void chooseScope(String name) {
        final String[] gs = games().toArray(new String[0]);
        final boolean[] checked = new boolean[gs.length];
        String sc = ModsProvider.scope(this, name);
        List<String> cur = Arrays.asList(sc.split(","));
        for (int i = 0; i < gs.length; i++) checked[i] = !sc.equals("*") && cur.contains(gs[i]);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("作用于哪些游戏（不选=全部）")
                .setMultiChoiceItems(gs, checked, (d, w, c) -> checked[w] = c)
                .setPositiveButton("保存", (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < gs.length; i++) if (checked[i]) sb.append(sb.length() > 0 ? "," : "").append(gs[i]);
                    ModsProvider.setScope(this, name, sb.length() == 0 ? "*" : sb.toString());
                    show(2);
                }).show();
    }

    void importSo(Uri uri) {
        bg(() -> {
            try {
                String name = "plugin.so";
                try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (i >= 0) name = c.getString(i);
                    }
                }
                name = name.replaceAll("[^A-Za-z0-9._-]", "_");
                if (!name.endsWith(".so")) name += ".so";
                File out = new File(ModsProvider.dir(this), name);
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream o = new FileOutputStream(out)) { copy(in, o); }
                toast("已导入 " + name);
                h.post(() -> show(2));
            } catch (Exception e) { toast("导入失败：" + e.getMessage()); }
        });
    }

    void renderModules(LinearLayout list, String out) {
        int n = 0;
        for (String blk : out.split("@@")) {
            if (blk.trim().isEmpty() || blk.startsWith("ERR")) continue;
            String[] lines = blk.split("\n");
            final String dir = lines[0].trim();
            Map<String, String> prop = new HashMap<>();
            boolean dis = false, rem = false;
            for (String l : lines) {
                int i = l.indexOf('=');
                if (i > 0) prop.put(l.substring(0, i).trim(), l.substring(i + 1).trim());
                if (l.equals("__DISABLED")) dis = true;
                if (l.equals("__REMOVE")) rem = true;
            }
            n++;
            final boolean disabled = dis, own = dir.startsWith(OWN_MODS);
            u.divider(list);
            u.text(list, prop.getOrDefault("name", dir) + "  " + prop.getOrDefault("version", ""), 15, Ui.TEXT, true);
            u.sub(list, "作者 " + prop.getOrDefault("author", "?") + (own ? " · ZFrame 模块" : "") + "\n" + prop.getOrDefault("description", ""));
            LinearLayout chips = u.row();
            list.addView(chips);
            u.chip(chips, disabled ? "已禁用" : "已启用", disabled ? Ui.SUB : Ui.GREEN);
            if (rem) u.chip(chips, "重启后删除", Ui.RED);
            LinearLayout r = u.row();
            list.addView(r);
            u.ghost(r, disabled ? "启用" : "禁用", v -> bg(() -> {
                Root.run(disabled ? "rm -f " + Root.q(dir + "/disable") : "touch " + Root.q(dir + "/disable"));
                h.post(() -> show(2));
            }));
            u.danger(r, "删除", v -> bg(() -> {
                Root.run(own ? "rm -rf " + Root.q(dir) : "touch " + Root.q(dir + "/remove"));
                h.post(() -> show(2));
            }));
        }
        if (n == 0) u.sub(list, "暂无模块");
    }

    void flashModule(Uri uri) {
        showProgress("刷入模块");
        bg(() -> {
            try {
                File zip = new File(getCacheDir(), "module.zip");
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(zip)) { copy(in, out); }
                String z = Root.q(zip.getAbsolutePath());
                String res;
                switch (backend) {
                    case "magisk": res = Root.run("magisk --install-module " + z); break;
                    case "ksu": res = Root.run("(command -v ksud >/dev/null && ksud module install " + z + ") || /data/adb/ksud module install " + z); break;
                    case "apatch": res = Root.run("(command -v apd >/dev/null && apd module install " + z + ") || /data/adb/apd module install " + z); break;
                    default: res = installOwn(zip);
                }
                zip.delete();
                endProgress();
                h.post(() -> {
                    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("刷入日志").setMessage(res).setPositiveButton("好", null).show();
                    show(2);
                });
            } catch (Exception e) { endProgress(); toast("刷入失败：" + e); }
        });
    }

    String installOwn(File zip) throws IOException {
        File stage = new File(getCacheDir(), "stage");
        deleteRec(stage);
        stage.mkdirs();
        String id = null;
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                File f = new File(stage, e.getName());
                if (!f.getCanonicalPath().startsWith(stage.getCanonicalPath())) continue;
                if (e.isDirectory()) { f.mkdirs(); continue; }
                f.getParentFile().mkdirs();
                try (OutputStream o = new FileOutputStream(f)) { copyNoClose(zin, o); }
            }
        }
        File prop = new File(stage, "module.prop");
        if (!prop.exists()) return "失败：zip 里没有 module.prop";
        try (BufferedReader r = new BufferedReader(new FileReader(prop))) {
            String l;
            while ((l = r.readLine()) != null) if (l.startsWith("id=")) id = l.substring(3).trim();
        }
        if (id == null || !id.matches("[A-Za-z0-9._-]+")) return "失败：module.prop 的 id 无效";
        String dst = OWN_MODS + "/" + id;
        return Root.run("mkdir -p " + OWN_MODS + " && rm -rf " + Root.q(dst) + " && cp -r " + Root.q(stage.getAbsolutePath()) + " " + Root.q(dst)
                + " && rm -rf " + Root.q(dst + "/META-INF") + " && chmod -R 755 " + Root.q(dst)
                + " && cd " + Root.q(dst) + " && { [ -f customize.sh ] && MODPATH=" + Root.q(dst) + " sh customize.sh; true; }"
                + " && { [ -f service.sh ] && [ ! -f zframe.sh ] && cp service.sh zframe.sh; true; }"
                + " && echo '安装成功: " + id + "（启动游戏时生效）'");
    }

    // ================= 通用 =================
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_APK) importFile(data.getData(), false);
        else if (req == REQ_TOOL) importFile(data.getData(), true);
        else if (req == REQ_ZIP) { if (root) flashModule(data.getData()); }
        else if (req == REQ_SO) importSo(data.getData());
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[1 << 16]; int r;
        while ((r = in.read(b)) > 0) out.write(b, 0, r);
    }

    static void copyNoClose(InputStream in, OutputStream out) throws IOException { copy(in, out); }

    static void deleteRec(File f) {
        File[] cs = f.listFiles();
        if (cs != null) for (File c : cs) deleteRec(c);
        f.delete();
    }
}
