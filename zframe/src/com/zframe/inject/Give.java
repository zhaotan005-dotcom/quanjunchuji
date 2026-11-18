package com.zframe.inject;

/** v2.90 用户：联机客户端点刷 → 让服务端代刷在客户端脚下（客户端本地刷服务器不认，会一直换弹）。
 *  走 ntfy 主题：客户端发「编号 数量 x y z」，服务端（Lan.role==1）每 2 秒拉一次，用 Veh.dropAt 在那个坐标生成（服务器生成，游戏自己同步）。 */
final class Give {
    static final String T = "https://ntfy.sh/zf-qj-give-k7r2p9m4x1";
    static volatile String state = "代刷：没用过";
    /** 客户端：请求服务端代刷 */
    static void ask(final String id, final int n) {
        new Thread(() -> {
            try {
                String e = Veh.ahead(20, -80);
                if (e != null) { GameOverlay.toastAny("没拿到自己位置：" + e); return; }
                String body = id + " " + Math.max(1, n) + " " + Veh.buf.getFloat(40) + " " + Veh.buf.getFloat(44) + " " + Veh.buf.getFloat(48);
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(T).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setDoOutput(true); c.setRequestMethod("POST");
                try (java.io.OutputStream o = c.getOutputStream()) { o.write(body.getBytes("UTF-8")); }
                int rc = c.getResponseCode(); c.disconnect();
                state = "代刷：已请求 " + body + "（HTTP " + rc + "）";
                GameOverlay.toastAny(rc == 200 ? "已让服务端刷在你脚下，几秒后出现" : "请求失败 HTTP " + rc);
            } catch (Throwable t) { state = "代刷：请求失败 " + t; GameOverlay.toastAny("请求失败：" + t.getMessage()); }
        }, "zf-give-ask").start();
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
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(T).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setDoOutput(true); c.setRequestMethod("POST");
                try (java.io.OutputStream o = c.getOutputStream()) { o.write(body.getBytes("UTF-8")); }
                int rc = c.getResponseCode(); c.disconnect();
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
                    Thread.sleep(2000);
                    if (Lan.role != 1) continue;
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(T + "/json?poll=1&since=" + since).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(10000);
                    String all;
                    try (java.io.InputStream in = c.getInputStream()) { all = new String(readAll(in), "UTF-8"); } finally { c.disconnect(); }
                    for (String ln : all.split("\n")) {
                        if (!ln.contains("\"event\":\"message\"")) continue;
                        String mid = field(ln, "id"), msg = field(ln, "message");
                        if (mid != null) since = mid;
                        if (msg == null) continue;
                        String[] p = msg.trim().split(" ");
                        if (p.length < 5) continue;
                        float x0 = Float.parseFloat(p[2]), y0 = Float.parseFloat(p[3]), z0 = Float.parseFloat(p[4]);
                        if (p[0].startsWith("V")) { state = "代刷：车 " + Veh.spawnGroupAt(Integer.parseInt(p[0].substring(1)), x0, y0, z0); LogCap.note(state); continue; }
                        if (p[0].startsWith("S")) { String[] sj = p[0].substring(1).split("_"); state = "代刷：" + Crew.setFor(x0, y0, z0, Float.parseFloat(sj[0]), Float.parseFloat(sj[1])); LogCap.note(state); continue; }
                        long item = Long.parseLong(p[0]); int n = Math.min(3, Math.max(1, Integer.parseInt(p[1])));
                        float x = Float.parseFloat(p[2]), y = Float.parseFloat(p[3]), z = Float.parseFloat(p[4]);
                        String r = null;
                        for (int i = 0; i < n && r == null; i++) r = Veh.dropAt(item, x + (i == 0 ? 0 : (float) (Math.random() - 0.5) * 60), y + (i == 0 ? 0 : (float) (Math.random() - 0.5) * 60), z);
                        state = "代刷：" + item + "×" + n + " " + (r == null ? "ok" : r);
                        LogCap.note(state);
                    }
                } catch (Throwable t) { state = "代刷：拉取失败 " + t; }
            }
        }, "zf-give-serve").start();
    }
    static byte[] readAll(java.io.InputStream in) throws java.io.IOException { java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(); byte[] x = new byte[8192]; int r; while ((r = in.read(x)) > 0) b.write(x, 0, r); return b.toByteArray(); }
    static String field(String j, String k) {
        int i = j.indexOf("\"" + k + "\":\""); if (i < 0) return null; i += k.length() + 4;
        StringBuilder sb = new StringBuilder();
        for (; i < j.length(); i++) { char ch = j.charAt(i); if (ch == '\\' && i + 1 < j.length()) { sb.append(j.charAt(++i)); continue; } if (ch == '"') break; sb.append(ch); }
        return sb.toString();
    }
}
