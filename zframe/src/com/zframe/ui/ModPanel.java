package com.zframe.ui;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.*;

/** GG 风格内存修改器界面：搜索 / 结果 / 收藏 / 浏览。root 模式和游戏内免 root 模式共用。 */
public class ModPanel {
    public interface Backend { String cmd(String c) throws Exception; }

    static final String[] TYPES = {"dword", "float", "qword", "double", "byte"};
    static final String[] TYPE_NAMES = {"整数D", "浮点F", "长整Q", "双精E", "字节B"};
    static final String[] REGION_NAMES = {"常用区域", "+Java堆", "全部"};
    static int typeSel = 0, regionSel = 0, page = 0;
    static String lastSearch = "", lastNew = "", viewAddr = "";

    final Ui u;
    final Backend be;
    static final java.util.concurrent.ExecutorService EXEC = java.util.concurrent.Executors.newSingleThreadExecutor();
    final Handler h = new Handler(Looper.getMainLooper());
    TextView status;
    LinearLayout body;
    EditText val, newVal, addrIn;
    int[] type, region;

    public ModPanel(Ui u, Backend be) { this.u = u; this.be = be; }

    public void build(LinearLayout root) {
        LinearLayout top = u.card(root);
        LinearLayout hr = u.row();
        top.addView(hr);
        u.text(hr, "修改器", 16, Ui.TEXT, true).setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        status = u.text(hr, "就绪", 12, Ui.CYAN, true);
        final int[] pg = u.segmented(top, new String[]{"搜索", "结果", "收藏", "浏览"}, page, null);
        body = u.col();
        root.addView(body);
        Runnable sw = () -> { page = pg[0]; render(); };
        // segmented 的回调需要引用 pg，所以重新绑定
        LinearLayout seg = (LinearLayout) top.getChildAt(top.getChildCount() - 1);
        for (int i = 0; i < seg.getChildCount(); i++) {
            final int idx = i;
            seg.getChildAt(i).setOnClickListener(v -> {
                pg[0] = idx;
                for (int j = 0; j < seg.getChildCount(); j++) u.style((TextView) seg.getChildAt(j), j == idx);
                sw.run();
            });
        }
        render();
    }

    void render() {
        body.removeAllViews();
        switch (page) {
            case 0: buildSearch(); break;
            case 1: buildResults(); run("list 200", this::renderList); break;
            case 2: buildSaved(); run("saved", this::renderSaved); break;
            default: buildViewer();
        }
    }

    // ---------- 搜索 ----------
    void buildSearch() {
        LinearLayout c = u.card(body);
        u.sub(c, "数值类型");
        type = u.segmented(c, TYPE_NAMES, typeSel, () -> typeSel = type[0]);
        u.sub(c, "内存区域");
        region = u.segmented(c, REGION_NAMES, regionSel, () -> { regionSel = region[0]; run("regions " + regionSel, null); });
        val = u.input(c, "100 · 100~200 · 100;200;300 · ? 未知值", false);
        val.setText(lastSearch);
        LinearLayout r1 = u.row(); c.addView(r1);
        u.primary(r1, "新搜索", v -> { lastSearch = t(val); run("regions " + regionSel, null); run("search " + TYPES[type[0]] + " " + q(lastSearch), this::afterSearch); });
        u.ghost(r1, "筛选", v -> { lastSearch = t(val); run("refine " + q(lastSearch), this::afterSearch); });
        u.ghost(r1, "清空", v -> run("reset", null));
        u.sub(c, "未知值（模糊）筛选");
        LinearLayout r2 = u.row(); c.addView(r2);
        u.ghost(r2, "变大", v -> run("refine increased", this::afterSearch));
        u.ghost(r2, "变小", v -> run("refine decreased", this::afterSearch));
        u.ghost(r2, "没变", v -> run("refine unchanged", this::afterSearch));
        u.ghost(r2, "变了", v -> run("refine changed", this::afterSearch));

        LinearLayout g = u.card(body);
        u.title(g, "用法");
        u.sub(g, "知道数值：搜 1234 → 游戏里让它变 → 输新值点筛选\n"
                + "不知道数值（血条等）：搜 ? → 掉血后点 变小 → 反复几次\n"
                + "范围：100~200　联合：100;200;300（默认 512 字节内，可写 ::256）\n"
                + "浮点数选 浮点F，金币多为 整数D，Unity 游戏常有加密");
    }

    void afterSearch(String r) {
        if (!r.startsWith("ERR") && !r.contains("快照") && !r.contains("已记录")) {
            try {
                int n = Integer.parseInt(r.replace("OK", "").trim().split("\\s+")[0]);
                if (n > 0 && n <= 200) { page = 1; h.post(this::render); }
            } catch (Exception ignored) {}
        }
    }

    static String q(String s) { return s.replace(' ', '\u0000').replace("\u0000", ""); }

    // ---------- 结果 ----------
    LinearLayout listBox;

    void buildResults() {
        LinearLayout c = u.card(body);
        newVal = u.input(c, "改成多少", false);
        newVal.setText(lastNew);
        LinearLayout r = u.row(); c.addView(r);
        u.primary(r, "全部修改", v -> { lastNew = t(newVal); run("writeall " + lastNew, x -> refreshList()); });
        u.ghost(r, "全部冻结", v -> { lastNew = t(newVal); run("freezeall " + lastNew, x -> refreshList()); });
        LinearLayout r2 = u.row(); c.addView(r2);
        u.ghost(r2, "刷新", v -> refreshList());
        u.danger(r2, "全部解冻", v -> run("unfreezeall", x -> refreshList()));
        listBox = u.col();
        body.addView(listBox);
    }

    void refreshList() { run("list 200", this::renderList); }

    void renderList(String l) {
        h.post(() -> {
            if (listBox == null || page != 1) return;
            listBox.removeAllViews();
            String[] lines = l.trim().split("\n");
            u.sub(listBox, "共 " + lines[0].replace("OK", "").trim() + " 条" + (lines.length > 200 ? "（显示前 200）" : ""));
            for (int i = 1; i < lines.length; i++) {
                String[] p = lines[i].trim().split(" ");
                if (p.length < 2) continue;
                final String addr = p[0];
                final boolean fz = p.length > 2 && p[2].equals("F");
                LinearLayout row = item(listBox, addr, p[1], fz, null);
                btn(row, "改", 44, v -> { lastNew = t(newVal); run("write " + addr + " " + lastNew, x -> refreshList()); });
                btn(row, fz ? "解" : "冻", 44, v -> { lastNew = t(newVal); run(fz ? "unfreeze " + addr : "freeze " + addr + " " + lastNew, x -> refreshList()); });
                btn(row, "存", 44, v -> run("save " + addr + " " + TYPES[typeSel], x -> toastStatus("已收藏")));
                btn(row, "看", 44, v -> { viewAddr = addr; page = 3; renderAgain(); });
            }
        });
    }

    // ---------- 收藏 ----------
    LinearLayout savedBox;

    void buildSaved() {
        LinearLayout c = u.card(body);
        newVal = u.input(c, "改成多少", false);
        newVal.setText(lastNew);
        u.ghost(c, "刷新", v -> run("saved", this::renderSaved));
        savedBox = u.col();
        body.addView(savedBox);
    }

    void renderSaved(String l) {
        h.post(() -> {
            if (savedBox == null || page != 2) return;
            savedBox.removeAllViews();
            String[] lines = l.trim().split("\n");
            if (lines.length <= 1) { u.sub(savedBox, "还没有收藏。在结果里点“存”。"); return; }
            for (int i = 1; i < lines.length; i++) {
                String[] p = lines[i].trim().split(" ", 5);
                if (p.length < 4) continue;
                final String addr = p[0], tp = p[3];
                final boolean fz = p[2].equals("F");
                LinearLayout row = item(savedBox, addr, p[1] + "  " + tp, fz, p.length > 4 ? p[4] : null);
                btn(row, "改", 44, v -> { lastNew = t(newVal); run("write " + addr + " " + lastNew + " " + tp, x -> run("saved", this::renderSaved)); });
                btn(row, fz ? "解" : "冻", 44, v -> { lastNew = t(newVal); run(fz ? "unfreeze " + addr : "freeze " + addr + " " + lastNew + " " + tp, x -> run("saved", this::renderSaved)); });
                btn(row, "删", 44, v -> run("unsave " + addr, x -> run("saved", this::renderSaved)));
            }
        });
    }

    // ---------- 浏览 ----------
    LinearLayout viewBox;

    void buildViewer() {
        LinearLayout c = u.card(body);
        addrIn = u.input(c, "地址（十六进制）", false);
        addrIn.setText(viewAddr);
        newVal = u.input(c, "改成多少", false);
        newVal.setText(lastNew);
        LinearLayout r = u.row(); c.addView(r);
        u.ghost(r, "◀ 上一页", v -> shiftView(-1));
        u.primary(r, "跳转", v -> { viewAddr = t(addrIn); loadView(); });
        u.ghost(r, "下一页 ▶", v -> shiftView(1));
        viewBox = u.col();
        body.addView(viewBox);
        if (!viewAddr.isEmpty()) loadView();
    }

    void shiftView(int dir) {
        try {
            long a = Long.parseUnsignedLong(t(addrIn).replace("0x", ""), 16);
            a += (long) dir * 32 * ("qword".equals(TYPES[typeSel]) || "double".equals(TYPES[typeSel]) ? 8 : "byte".equals(TYPES[typeSel]) ? 1 : 4);
            viewAddr = Long.toHexString(a);
            addrIn.setText(viewAddr);
            loadView();
        } catch (Exception e) { toastStatus("地址无效"); }
    }

    void loadView() {
        if (viewAddr.isEmpty()) return;
        run("view " + viewAddr.replace("0x", "") + " 32 " + TYPES[typeSel], l -> h.post(() -> {
            if (viewBox == null || page != 3) return;
            viewBox.removeAllViews();
            String[] lines = l.trim().split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] p = lines[i].trim().split(" ");
                if (p.length < 2) continue;
                final String addr = p[0];
                final boolean fz = p.length > 2 && p[2].equals("F");
                LinearLayout row = item(viewBox, addr, p[1], fz, null);
                btn(row, "改", 44, v -> { lastNew = t(newVal); run("write " + addr + " " + lastNew, x -> loadView()); });
                btn(row, fz ? "解" : "冻", 44, v -> { lastNew = t(newVal); run(fz ? "unfreeze " + addr : "freeze " + addr + " " + lastNew, x -> loadView()); });
                btn(row, "存", 44, v -> run("save " + addr + " " + TYPES[typeSel], x -> toastStatus("已收藏")));
            }
        }));
    }

    // ---------- 公共 ----------
    void renderAgain() { h.post(this::render); }

    LinearLayout item(LinearLayout box, String addr, String value, boolean fz, String note) {
        LinearLayout r = u.row();
        r.setBackground(u.round(Ui.CARD2, 10));
        r.setPadding(u.dp(10), u.dp(2), u.dp(4), u.dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = u.dp(6);
        box.addView(r, lp);
        LinearLayout info = u.col();
        r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView a = u.text(info, "0x" + addr + (note != null && !note.isEmpty() ? "  " + note : ""), 11, Ui.SUB, false);
        a.setTypeface(Typeface.MONOSPACE);
        u.text(info, value + (fz ? "  ❄" : ""), 15, fz ? Ui.CYAN : Ui.TEXT, true);
        return r;
    }

    void btn(LinearLayout row, String s, int w, android.view.View.OnClickListener l) {
        TextView b = u.ghost(row, s, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u.dp(w), -2);
        lp.setMargins(u.dp(2), u.dp(4), u.dp(2), u.dp(4));
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, u.dp(9), 0, u.dp(9));
    }

    void toastStatus(String s) {
        h.post(() -> { status.setText(s); status.setTextColor(Ui.GREEN); });
    }

    static String t(EditText e) { return e.getText().toString().trim(); }

    public interface Cb { void on(String r); }

    public void run(String cmd) { run(cmd, null); }

    /** 兼容旧调用 */
    public void run(String cmd, boolean list) { run(cmd, list ? (Cb) r -> { if (page == 1) refreshList(); } : null); }

    public void run(String cmd, Cb cb) {
        h.post(() -> { status.setText("执行中…"); status.setTextColor(Ui.SUB); });
        EXEC.execute(() -> {
            String res;
            try { res = be.cmd(cmd).trim(); } catch (Exception e) { res = "ERR " + e.getMessage(); }
            final String r = res;
            h.post(() -> {
                boolean err = r.startsWith("ERR");
                String first = r.split("\n")[0].replace("OK", "").trim();
                if (cmd.startsWith("list") || cmd.startsWith("saved") || cmd.startsWith("view")) first = "共 " + first.split("\\s+")[0] + " 条";
                else if (cmd.startsWith("attach")) first = "已附加 " + first;
                else if (cmd.startsWith("search") || cmd.startsWith("refine")) first = first.matches("\\d+.*") ? "找到 " + first : first;
                status.setText(err ? r : (first.isEmpty() ? "完成" : first));
                status.setTextColor(err ? Ui.RED : Ui.GREEN);
            });
            if (cb != null && !r.startsWith("ERR")) cb.on(r);
        });
    }
}
