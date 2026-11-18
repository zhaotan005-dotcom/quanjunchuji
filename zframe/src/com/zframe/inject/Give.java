package com.zframe.inject;

/** v2.90 用户：联机客户端点刷 → 让服务端代刷在客户端脚下（客户端本地刷服务器不认，会一直换弹）。
 *  走 ntfy 主题：客户端发「编号 数量 x y z」，服务端（Lan.role==1）每 2 秒拉一次，用 Veh.dropAt 在那个坐标生成（服务器生成，游戏自己同步）。 */
final class Give {
    static final String T = "https://ntfy.sh/zf-qj-give-k7r2p9m4x1";
    static volatile String state = "代刷：没用过";
    /** 客户端：请求服务端代刷 */
    /** v3.03 稳定性：用户截图「请求失败 HTTP 429」—— ntfy 按 IP 限流（突发 60 次，之后约 5 秒 1 次），两台手机同一出口 IP 共用。
     *  以前刷一把枪 = 枪+子弹+十几件配件，每件一个 POST；服务端还每 2 秒拉一次。改成：客户端 0.5 秒内的请求合成一条（多行），服务端改长连接订阅。 */
    static final java.util.ArrayList<String> pend = new java.util.ArrayList<>();
    static volatile long lastAdd;
    static void ask(final String id, final int n) {
        synchronized (pend) { pend.add(id + " " + Math.max(1, n)); lastAdd = System.currentTimeMillis(); if (pend.size() > 1) return; }
        new Thread(() -> {
            try {
                while (System.currentTimeMillis() - lastAdd < 500) Thread.sleep(100);
                java.util.ArrayList<String> items;
                synchronized (pend) { items = new java.util.ArrayList<>(pend); pend.clear(); }
                String e = Veh.ahead(20, -80);
                if (e != null) { GameOverlay.toastAny("没拿到自己位置：" + e); return; }
                String xyz = " " + Veh.buf.getFloat(40) + " " + Veh.buf.getFloat(44) + " " + Veh.buf.getFloat(48);
                StringBuilder body = new StringBuilder();
                for (String it : items) body.append(body.length() == 0 ? "" : "\n").append(it).append(xyz);
                int rc = send(body.toString());
                state = "代刷：已请求 " + items.size() + " 件（HTTP " + rc + "）";
                GameOverlay.toastAny(rc == 200 ? "已让服务端刷在你脚下（" + items.size() + " 件），几秒后出现" : "请求失败 HTTP " + rc);
            } catch (Throwable t) { state = "代刷：请求失败 " + t; GameOverlay.toastAny("请求失败：" + t.getMessage()); }
        }, "zf-give-ask").start();
    }
    /** 发一条；429 时等 6 秒重试，最多 3 次 */
    static int send(String body) throws Exception {
        int rc = 0;
        for (int k = 0; k < 3; k++) {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(T).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setDoOutput(true); c.setRequestMethod("POST");
            try (java.io.OutputStream o = c.getOutputStream()) { o.write(body.getBytes("UTF-8")); }
            rc = c.getResponseCode(); c.disconnect();
            if (rc != 429) return rc;
            Thread.sleep(6000);
        }
        return rc;
    }
    /** v2.92 客户端刷车：发「V 车组序号 x y z」，服务端在你前面刷 */
    static void askCar(final int gi, final String label) { post("V" + gi, "车 " + label); }
    /** v2.92 客户端加速/高跳：拉条停 0.7 秒后发「S 加速 高跳 x y z」，服务端找离这个坐标最近的加入玩家，只给他改 */
    static volatile float mySpeed = 1f, myJump = 1f; static volatile long moveAt;
    static void askMove(float s, float j) {
        mySpeed = Math.max(1f, s); myJump = Math.max(1f, j); final long t = moveAt = System.currentTimeMillis();
        new Thread(() -> { try { Thread.sleep(700); } catch (InterruptedException e) { return; } if (moveAt == t) post("S" + mySpeed + "_" + myJump, "加速 " + mySpeed + " 高跳 " + myJump); }, "zf-move-ask").start();
    }
    static void post(final String head, final String what) {
        new Thread(() -> {
            try {
                String e = Veh.ahead(20, -80);
                if (e != null) { GameOverlay.toastAny("没拿到自己位置：" + e); return; }
                String body = head + " 1 " + Veh.buf.getFloat(40) + " " + Veh.buf.getFloat(44) + " " + Veh.buf.getFloat(48);
                int rc = send(body);
                state = "代刷：已请求 " + body + "（HTTP " + rc + "）";
                GameOverlay.toastAny(rc == 200 ? "已发给服务端：" + what : "请求失败 HTTP " + rc);
            } catch (Throwable t) { state = "代刷：请求失败 " + t; GameOverlay.toastAny("请求失败：" + t.getMessage()); }
        }, "zf-give-post").start();
    }
    static volatile boolean polling;
    static String since = "";
    /** 服务端：开房后一直拉 */
    static synchronized void serve() {
        if (polling) return; polling = true;
        since = String.valueOf(System.currentTimeMillis() / 1000);
        new Thread(() -> {
            while (true) {
                try {
                    if (Lan.role != 1) { Thread.sleep(2000); continue; }
                    // v3.03：长连接订阅（一次请求一直收），断了 5 秒后重连并从上次的消息 id 接着收；以前每 2 秒拉一次，很快就被 ntfy 限流 429
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(T + "/json?since=" + since).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(120000);
                    try (java.io.BufferedReader rd = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"))) {
                        String ln;
                        while ((ln = rd.readLine()) != null) {
                            if (!ln.contains("\"event\":\"message\"")) continue;
                            String mid = field(ln, "id"), msg = field(ln, "message");
                            if (mid != null) since = mid;
                            if (msg == null) continue;
                            for (String one : msg.split("\n")) handle(one);
                            if (Lan.role != 1) break;
                        }
                    } finally { c.disconnect(); }
                    Thread.sleep(5000);
                } catch (Throwable t) { state = "代刷：订阅断开 " + t; try { Thread.sleep(5000); } catch (InterruptedException e) { return; } }
            }
        }, "zf-give-serve").start();
    }
    static void handle(String one) {
        try {
            String[] p = one.trim().split(" ");
            if (p.length < 5) return;
            float x0 = Float.parseFloat(p[2]), y0 = Float.parseFloat(p[3]), z0 = Float.parseFloat(p[4]);
            if (p[0].startsWith("V")) { state = "代刷：车 " + Veh.spawnGroupAt(Integer.parseInt(p[0].substring(1)), x0, y0, z0); LogCap.note(state); return; }
            if (p[0].startsWith("S")) { String[] sj = p[0].substring(1).split("_"); state = "代刷：" + Crew.setFor(x0, y0, z0, Float.parseFloat(sj[0]), Float.parseFloat(sj[1])); LogCap.note(state); return; }
            long item = Long.parseLong(p[0]); int n = Math.min(3, Math.max(1, Integer.parseInt(p[1])));
            String r = null;
            for (int i = 0; i < n && r == null; i++) r = Veh.dropAt(item, x0 + (i == 0 ? 0 : (float) (Math.random() - 0.5) * 60), y0 + (i == 0 ? 0 : (float) (Math.random() - 0.5) * 60), z0);
            state = "代刷：" + item + "×" + n + " " + (r == null ? "ok" : r);
            LogCap.note(state);
        } catch (Throwable t) { state = "代刷：处理出错 " + t; }
    }
    static byte[] readAll(java.io.InputStream in) throws java.io.IOException { java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(); byte[] x = new byte[8192]; int r; while ((r = in.read(x)) > 0) b.write(x, 0, r); return b.toByteArray(); }
    static String field(String j, String k) {
        int i = j.indexOf("\"" + k + "\":\""); if (i < 0) return null; i += k.length() + 4;
        StringBuilder sb = new StringBuilder();
        for (; i < j.length(); i++) { char ch = j.charAt(i); if (ch == '\\' && i + 1 < j.length()) { char nx = j.charAt(++i); sb.append(nx == 'n' ? '\n' : nx); continue; } if (ch == '"') break; sb.append(ch); }
        return sb.toString();
    }
}
