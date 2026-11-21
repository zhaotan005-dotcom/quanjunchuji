package com.zframe.inject;

/** v3.69 用户：游戏自带「创建房间」能用，每个人进大厅都能看到并加入我的房间（云手机挂服务端）。
 *  游戏的房间协议（cs_roomsvr / cs_roomcenter，从 libUE4 内嵌 proto 导出）原本走腾讯房间服务器，这里由自建服务端（Server.handle）回包：
 *    26001 建房 → 26002（房间信息，我是房主）；26025 改房 → 26026；26017 取房间信息 → 26018；26005 加入 → 26006；26007 退出 → 26008；
 *    27005 房间列表 → 27006（列表来自 ntfy 主题，所有装了框架的手机共享）；27001 按房号搜 → 27002；
 *    26011 / 15001（房主在房间里点开始）→ 回成功，房主本机开图当服务端（Lan.host / ModeProbe.host），进图后在主题里发「开始」，
 *    加入的人收到后自动 Lan.join(房主地址)（游戏原生 open ip，跟「加入艾挽服务器」同一条路）。
 *  房主地址：服务端包（云手机）= frp-put.com:47937（艾挽服务器），其它手机 = 本机局域网 IP（只有同一个 Wi-Fi 能进）。
 *  主题消息一行：R\t房号\t名字\t地图\t模式\t队伍\t人数上限\t当前人数\t房主名\t地址\t状态(W 等人/P 已开始/X 关了)\t密码\t成员（逗号分开）
 *              J\t房号\t玩家id\t名字   Q\t房号\t玩家id */
final class Room {
    static final String T = "https://ntfy.sh/zf-qj-room-k7r2p9m4x1";
    static volatile String state = "房间：没用过";

    static final class Info {
        long id; String name = "我的房间", owner = "", addr = "", st = "W", pwd = "";
        int map = 1, mode = 1, team = 1, max = 100, count = 1; long seen; String members = "";
    }
    /** 我自己建的房（房主）/ 我加入的房 */
    static volatile Info mine, joined;
    static final java.util.Map<Long, Info> list = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.LinkedHashMap<Long, String> mem = new java.util.LinkedHashMap<>();

    static long myId() {
        try {
            android.content.SharedPreferences p = Con.act.getSharedPreferences("zframe", 0);
            long v = p.getLong("room_pid", 0);
            if (v == 0) { v = 1000000L + (long) (Math.random() * 8999999); p.edit().putLong("room_pid", v).apply(); }
            return v;
        } catch (Throwable t) { return 1234567L; }
    }
    static String myNick() {
        try { String n = Con.act.getSharedPreferences("zframe", 0).getString("room_nick", null); if (n != null && !n.isEmpty()) return n; } catch (Throwable ignored) {}
        return "玩家" + (myId() % 10000);
    }
    static String myAddr() {
        try { String a = Con.act.getSharedPreferences("zframe", 0).getString("room_addr", ""); if (a != null && !a.isEmpty()) return a; } catch (Throwable ignored) {}   // v3.70 用户自己的内网穿透地址
        if (Ed.ED == 1) return GameOverlay.AIWAN;
        String ip = Lan.ip(); int k = ip.indexOf('（');
        return k > 0 ? ip.substring(0, k) : ip;
    }

    // ===== 拼包 =====
    static PB seat(int idx, long pid, String nick) {
        return new PB().u(1, 0).u(2, idx).u(4, 1).u(5, pid).s(6, nick).u(8, 1);
    }
    static PB player(long pid, String nick) { return new PB().u(1, pid).s(2, nick).u(4, 30).u(5, 1).u(7, 1); }
    static PB simple(Info r) {
        PB p = new PB().u(1, r.id).u(2, 1).u(3, r.pwd.isEmpty() ? 0 : 1);
        if (!r.pwd.isEmpty()) p.s(19, r.pwd);
        p.s(4, r.name).u(5, r.map).u(6, r.mode).u(7, r.team).u(8, r.max).u(9, 12).u(10, Math.max(1, r.count)).u(11, 0)
         .m(12, seat(0, ownerId(r), r.owner)).m(20, player(ownerId(r), r.owner)).u(15, 0).u(21, r.seen / 1000);
        return p;
    }
    static long ownerId(Info r) { return r == mine ? myId() : r.id * 7 + 1; }
    static PB team() { return new PB().u(1, 0).u(2, 0); }
    /** 房间里的座位：房主 0 号，成员按加入顺序 */
    static PB[] seats(Info r) {
        java.util.ArrayList<PB> out = new java.util.ArrayList<>();
        out.add(seat(0, ownerId(r), r.owner));
        int i = 1;
        if (r == mine) { synchronized (mem) { for (java.util.Map.Entry<Long, String> e : mem.entrySet()) out.add(seat(i++, e.getKey(), e.getValue())); } }
        else if (!r.members.isEmpty()) for (String m : r.members.split(",")) { if (m.isEmpty()) continue; String[] kv = m.split(":", 2); try { out.add(seat(i++, Long.parseLong(kv[0]), kv.length > 1 ? kv[1] : "玩家")); } catch (Throwable ignored) {} }
        return out.toArray(new PB[0]);
    }
    static PB roomRes(Info r, int selfIdx) {
        PB p = new PB().u(1, 0).m(2, simple(r));
        PB[] s = seats(r);
        p.m(r == mine ? 5 : 3, selfIdx < s.length ? s[selfIdx] : s[0]);
        return p;
    }

    // ===== 回包（Server.handle 调，返回 null = 不归我管） =====
    static String handle(int cmd, int seq, boolean key, byte[] body) {
        switch (cmd) {
            case 26001: {   // 建房
                Info r = new Info();
                r.id = 100000 + (long) (Math.random() * 899999);
                PB.R rd = new PB.R(body);
                while (rd.next()) {
                    if (rd.wire == 0) { if (rd.field == 1) r.map = (int) rd.val; else if (rd.field == 2) r.mode = (int) rd.val; else if (rd.field == 3) r.team = (int) rd.val; else if (rd.field == 4) r.max = (int) rd.val; }
                    else if (rd.wire == 2) { String s = new String(rd.raw(), java.nio.charset.StandardCharsets.UTF_8); if (rd.field == 5) r.name = s; else if (rd.field == 6) r.pwd = s; }
                }
                r.owner = myNick(); r.addr = myAddr(); r.seen = System.currentTimeMillis();
                synchronized (mem) { mem.clear(); }
                mine = r; joined = null;
                Server.push(26002, seq, key, new PB().u(1, 0).m(3, simple(r)).m(4, team()).bytes(), "建房 " + r.id);
                startHostLoop();
                state = "房间：建好 " + r.id + "「" + r.name + "」地图 " + r.map + "，地址 " + r.addr;
                return "建房 " + r.id;
            }
            case 26025: {   // 改房（密码/地图/队伍）
                Info r = mine;
                if (r == null) { Server.push(26026, seq, key, new PB().u(1, 0).bytes()); return "改房（没有房）"; }
                PB.R rd = new PB.R(body);
                while (rd.next()) {
                    if (rd.wire == 0) { if (rd.field == 2) r.map = (int) rd.val; else if (rd.field == 3) r.mode = (int) rd.val; else if (rd.field == 4) r.team = (int) rd.val; else if (rd.field == 5) r.max = (int) rd.val; }
                    else if (rd.wire == 2) { String s = new String(rd.raw(), java.nio.charset.StandardCharsets.UTF_8); if (rd.field == 6) r.name = s; else if (rd.field == 7) r.pwd = s; }
                }
                Server.push(26026, seq, key, new PB().u(1, 0).m(3, simple(r)).m(4, team()).bytes(), "改房");
                publish(r);
                return "改房 地图 " + r.map + (r.pwd.isEmpty() ? "" : " 有密码");
            }
            case 26017: {   // 取房间信息
                Info r = mine != null ? mine : joined;
                if (r == null) { Server.push(26018, seq, key, new PB().u(1, 1).bytes()); return "房间信息（没有房）"; }
                PB p = new PB().u(1, 0).m(2, simple(r));
                PB[] s = seats(r); p.m(5, r == mine ? s[0] : s[s.length - 1]);
                for (PB x : s) p.m(3, x);
                p.m(6, team());
                Server.push(26018, seq, key, p.bytes(), "房间信息");
                return "房间信息 " + r.id;
            }
            case 26005: case 26033: {   // 加入
                long id = 0; String pwd = "";
                PB.R rd = new PB.R(body);
                while (rd.next()) { if (rd.field == 1 && rd.wire == 0) id = rd.val; else if (rd.field == 3 && rd.wire == 2) pwd = new String(rd.raw(), java.nio.charset.StandardCharsets.UTF_8); }
                Info r = list.get(id);
                if (r == null || "X".equals(r.st)) { Server.push(cmd + 1, seq, key, new PB().u(1, 1).bytes()); toast("房间 " + id + " 已经没了"); return "加入 " + id + "（没有这个房）"; }
                if (!r.pwd.isEmpty() && !r.pwd.equals(pwd)) { Server.push(cmd + 1, seq, key, new PB().u(1, 1).bytes()); toast("房间密码不对"); return "加入 " + id + "（密码不对）"; }
                joined = r; mine = null;
                r.members = r.members + (r.members.isEmpty() ? "" : ",") + myId() + ":" + myNick();
                PB[] s = seats(r);
                PB p = new PB().u(1, 0).m(2, simple(r)).m(3, s[s.length - 1]);
                for (PB x : s) p.m(4, x);
                p.m(6, team());
                if (cmd == 26033) p.u(6, 1);
                Server.push(cmd + 1, seq, key, p.bytes(), "加入房间 " + id);
                send("J\t" + id + "\t" + myId() + "\t" + myNick());
                startJoinLoop();
                if ("P".equals(r.st)) goJoin(r);
                state = "房间：加入 " + id + "「" + r.name + "」房主 " + r.owner + "，地址 " + r.addr;
                return "加入 " + id;
            }
            case 26007: {   // 退出
                Info r = mine != null ? mine : joined;
                Server.push(26008, seq, key, new PB().u(1, 0).u(2, r == null ? 0 : r.id).bytes(), "退出房间");
                if (r != null && r == mine) { r.st = "X"; publish(r); }
                else if (r != null) send("Q\t" + r.id + "\t" + myId());
                mine = null; joined = null;
                state = "房间：已退出";
                return "退出房间";
            }
            case 26011: case 15001: {   // 房主点开始
                Info r = mine;
                if (r == null) return null;   // 不在房间里的 15001 照旧走 Server
                if (cmd == 26011) Server.push(26012, seq, key, new PB().u(1, 0).u(2, r.id).u(3, 1).bytes(), "开始");
                else Server.push(15002, seq, key, new PB().u(1, 1).bytes(), "房间开始（本机开服）");
                launch(r);
                return "房主开始 " + r.id;
            }
            case 27005: {   // 房间列表
                fetchSoon();
                PB p = new PB().u(1, 0).u(2, 0);
                long now = System.currentTimeMillis();
                int n = 0;
                for (Info r : list.values()) {
                    if ("X".equals(r.st) || now - r.seen > 90000) continue;
                    p.m(3, listItem(r)); n++;
                }
                Server.push(27006, seq, key, p.bytes(), "房间列表 " + n + " 个");
                return "房间列表 " + n;
            }
            case 27001: {
                long id = 0; PB.R rd = new PB.R(body); while (rd.next()) if (rd.field == 1 && rd.wire == 0) id = rd.val;
                Info r = list.get(id);
                if (r == null) { Server.push(27002, seq, key, new PB().u(1, 1).bytes(), "搜房号 " + id + "（没有）"); return "搜房号 " + id + " 没有"; }
                Server.push(27002, seq, key, new PB().u(1, 0).u(2, r.id).u(3, 1).m(4, listItem(r)).bytes(), "搜房号 " + id);
                return "搜房号 " + id;
            }
        }
        return null;
    }
    static PB listItem(Info r) {
        return new PB().u(1, r.id).u(2, 1).u(3, ownerId(r)).s(4, r.owner).u(5, "P".equals(r.st) ? 1 : 0).u(7, 30).s(8, r.name)
                .u(10, r.map).u(11, Math.max(1, r.count)).u(12, r.max).u(13, r.pwd.isEmpty() ? 0 : 1).u(14, r.team);
    }

    // ===== 房主 =====
    static volatile boolean hostLoop;
    static void startHostLoop() {
        if (hostLoop) { publish(mine); return; }
        hostLoop = true;
        new Thread(() -> {
            try {
                String since = String.valueOf(System.currentTimeMillis() / 1000 - 5);
                long lastPub = 0;
                while (mine != null) {
                    Info r = mine;
                    // 收加入/退出
                    try {
                        String body = get(T + "/json?poll=1&since=" + since);
                        for (String ln : body.split("\n")) {
                            if (!ln.contains("\"event\":\"message\"")) continue;
                            String id = jstr(ln, "id"); if (id != null) since = id;
                            String m = jstr(ln, "message"); if (m == null) continue;
                            String[] f = m.split("\t");
                            if (f.length >= 4 && "J".equals(f[0]) && String.valueOf(r.id).equals(f[1])) { synchronized (mem) { mem.put(Long.parseLong(f[2]), f[3]); } toast(f[3] + " 进了你的房间"); lastPub = 0; }
                            else if (f.length >= 3 && "Q".equals(f[0]) && String.valueOf(r.id).equals(f[1])) { synchronized (mem) { mem.remove(Long.parseLong(f[2])); } lastPub = 0; }
                        }
                    } catch (Throwable ignored) {}
                    synchronized (mem) { r.count = 1 + mem.size(); }
                    if ("W".equals(r.st) && Con.inMatch && launching) { r.st = "P"; lastPub = 0; }
                    if (System.currentTimeMillis() - lastPub > 30000) { publish(r); lastPub = System.currentTimeMillis(); }
                    Thread.sleep(6000);
                }
            } catch (Throwable t) { state = "房间：房主循环出错 " + t; LogCap.note(state); }
            finally { hostLoop = false; }
        }, "zf-room-host").start();
    }
    static volatile boolean launching;
    static void launch(final Info r) {
        launching = true;
        toast("开始：本机开服，大家会自动进来");
        GameOverlay.MAIN.postDelayed(() -> {
            String msg;
            if (r.map == 3) msg = ModeProbe.host("Props", "战术模式", Con.MAP_ERANGEL2);
            else if (r.map == 4) msg = ModeProbe.host("Props", "战术模式", Con.MAP_SAVAGE);
            else msg = Lan.host(Con.MAP_ERANGEL);
            state = "房间：开始 " + r.id + " → " + msg;
            LogCap.note(state);
        }, 800);
        // 进图后（hostLoop 里 Con.inMatch）发「P」，加入的人看到就连
        new Thread(() -> {
            for (int i = 0; i < 120 && mine == r; i++) {
                if (Con.inMatch) { r.st = "P"; publish(r); state = "房间：已开始 " + r.id + "，大家正在连 " + r.addr; return; }
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
        }, "zf-room-launch").start();
    }
    static void publish(Info r) {
        if (r == null) return;
        StringBuilder ms = new StringBuilder();
        synchronized (mem) { for (java.util.Map.Entry<Long, String> e : mem.entrySet()) ms.append(ms.length() == 0 ? "" : ",").append(e.getKey()).append(':').append(e.getValue().replace(",", " ").replace(":", " ")); }
        send("R\t" + r.id + "\t" + clean(r.name) + "\t" + r.map + "\t" + r.mode + "\t" + r.team + "\t" + r.max + "\t" + r.count + "\t" + clean(r.owner) + "\t" + r.addr + "\t" + r.st + "\t" + clean(r.pwd) + "\t" + ms);
    }
    static String clean(String s) { return s == null ? "" : s.replace("\t", " ").replace("\n", " "); }

    // ===== 列表 / 加入的人 =====
    static volatile long fetchAt;
    static void fetchSoon() {
        if (System.currentTimeMillis() - fetchAt < 8000) return;
        fetchAt = System.currentTimeMillis();
        new Thread(Room::fetch, "zf-room-list").start();
    }
    static void fetch() {
        try {
            String body = get(T + "/json?poll=1&since=3m");
            for (String ln : body.split("\n")) {
                if (!ln.contains("\"event\":\"message\"")) continue;
                String m = jstr(ln, "message"); if (m == null) continue;
                String t = jstr(ln, "time");
                String[] f = m.split("\t", -1);
                if (f.length < 12 || !"R".equals(f[0])) continue;
                long id = Long.parseLong(f[1]);
                Info mineR = mine; if (mineR != null && mineR.id == id) continue;
                Info r = list.get(id); if (r == null) { r = new Info(); r.id = id; list.put(id, r); }
                r.name = f[2]; r.map = Integer.parseInt(f[3]); r.mode = Integer.parseInt(f[4]); r.team = Integer.parseInt(f[5]); r.max = Integer.parseInt(f[6]);
                r.count = Integer.parseInt(f[7]); r.owner = f[8]; r.addr = f[9]; r.st = f[10]; r.pwd = f[11]; r.members = f.length > 12 ? f[12] : "";
                r.seen = t != null ? Long.parseLong(t) * 1000 : System.currentTimeMillis();
            }
            Info mr = mine; if (mr != null) list.put(mr.id, mr);
            state = state.startsWith("房间：列表") || mine == null && joined == null ? "房间：列表 " + list.size() + " 个（主题里最近 3 分钟）" : state;
        } catch (Throwable t) { state = "房间：拉列表失败 " + t; }
    }
    static volatile boolean joinLoop;
    static void startJoinLoop() {
        if (joinLoop) return;
        joinLoop = true;
        new Thread(() -> {
            try {
                while (joined != null && !Con.inMatch) {
                    fetchAt = 0; fetch();
                    Info r = joined; if (r == null) break;
                    Info cur = list.get(r.id);
                    if (cur != null) joined = r = cur;
                    if ("X".equals(r.st)) { toast("房主关了房间"); joined = null; break; }
                    if ("P".equals(r.st)) { goJoin(r); break; }
                    Thread.sleep(6000);
                }
            } catch (Throwable t) { state = "房间：等开始出错 " + t; }
            finally { joinLoop = false; }
        }, "zf-room-join").start();
    }
    static void goJoin(final Info r) {
        state = "房间：房主开始了，连 " + r.addr;
        LogCap.note(state);
        GameOverlay.MAIN.post(() -> toast(Lan.join(r.addr)));
        joined = null;
    }

    // ===== ntfy =====
    static void send(final String msg) {
        new Thread(() -> {
            for (int k = 0; k < 3; k++) {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(T).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setDoOutput(true); c.setRequestMethod("POST");
                    try (java.io.OutputStream o = c.getOutputStream()) { o.write(msg.getBytes("UTF-8")); }
                    int rc = c.getResponseCode(); c.disconnect();
                    if (rc != 429) return;
                    Thread.sleep(6000);
                } catch (Throwable t) { state = "房间：发主题失败 " + t; return; }
            }
        }, "zf-room-send").start();
    }
    static String get(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(10000);
        try (java.io.InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toString("UTF-8");
        } finally { c.disconnect(); }
    }
    /** 从一行 json 取字符串/数字字段（ntfy 的 message 里 \t 会写成 \\t） */
    static String jstr(String ln, String k) {
        String q = "\"" + k + "\":";
        int i = ln.indexOf(q); if (i < 0) return null;
        i += q.length();
        if (ln.charAt(i) != '"') { int e = i; while (e < ln.length() && (Character.isDigit(ln.charAt(e)))) e++; return ln.substring(i, e); }
        StringBuilder sb = new StringBuilder();
        for (int j = i + 1; j < ln.length(); j++) {
            char ch = ln.charAt(j);
            if (ch == '"') break;
            if (ch == '\\' && j + 1 < ln.length()) {
                char n = ln.charAt(++j);
                if (n == 't') sb.append('\t'); else if (n == 'n') sb.append('\n');
                else if (n == 'u' && j + 4 < ln.length()) { sb.append((char) Integer.parseInt(ln.substring(j + 1, j + 5), 16)); j += 4; }
                else sb.append(n);
            } else sb.append(ch);
        }
        return sb.toString();
    }
    static void toast(final String s) { GameOverlay.toastAny(s); }
    static String text() {
        Info m = mine, j = joined;
        return state + (m != null ? "；我是房主 " + m.id + " 成员 " + mem.size() + " 状态 " + m.st : "") + (j != null ? "；已加入 " + j.id + " 状态 " + j.st : "") + "；列表 " + list.size();
    }
}
