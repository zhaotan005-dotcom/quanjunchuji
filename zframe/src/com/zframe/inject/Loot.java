package com.zframe.inject;
import android.app.Activity;

/**
 * v2.20 联机房主：地图物资 / 门 / 空投（只在房主版装，客户端版和单机版什么都不改）。
 *
 * 原因：地图的物资点、门、车点在 ServerOnly 流式层里。流式管理器 BeginPlay（0x115beb4）只在专用服务器（NetMode 1）时
 * 把这个层放进 CurrentLayers；房主是 listen server（NetMode 2），走客户端路径，这些关卡从来不建、不加载。
 * 另外物资点组件 UNZBRPickupConfigComponent::BeginPlay（0x10c0b5c）只认 NetMode 0/1，房主的物资点也不交给 PickupManager。
 *
 * 1. SL（启动时装）：客户端路径末尾 0x115c498 → cave 0x171e720：NetMode==2 时把 ServerOnly 层（+0x2d4）加进 CurrentLayers，
 *    之后游戏自己给它建流式关卡、按房主视野加载（cave 源码和 unicorn 测试：game/re/lootcave.py、emu_loot.py）。
 * 2. PK（启动时装）：0x10c0ba4 三条指令，NetMode 只排除客户端(3)，房主(2)的物资点也交给 PickupManager。
 *    物资由服务器（房主）生成，客户端靠游戏自己的同步看到，客户端不自己刷。
 * 3. 空投兜底：游戏日志说空投箱物品列表是空的（"PickupItemIDList.Num() = 0"，Warning）时，
 *    在箱子位置（同一次碰撞前一行 "LockServer--Pos: x, y, z"）用 cave 'G' 放一套物资。每个箱子只放一次。
 */
final class Loot {
    static final long SL_HOOK = 0x115c498L, SL_CAVE = 0x171e720L;
    static final String SL_HOOK_O = "a90000ea", SL_HOOK_N = "a00817ea";
    static final String SL_CAVE_O = "0700000a38301be50020a0e30d50a0e10d10a0e10070a0e3000053e30300001a0d0000ead0009fe50d50a0e100308fe00000a0e30070a0e1010087e2071193e7000051e3faffff1a0000a0e30510a0e10320a0e1010077e31800000a170087e21f00c2e700304de00f0083e21f00c3e703d0a0e1000052e3000050130e00000a007092e50030a0e30060a0e3000057e30800000a042082e20060a0e3010c57e33f70a0230670c0e7067192e7";
    static final String SL_CAVE_N = "0c009de5000050e32600000af31d5deb020050e32300001ad46294e5000056e32000000af85294e5f41294e50020a0e3050052e1040000aa823191e7060053e11800000a012082e2f8ffffea010085e2f80284e5fc0294e5000055e1020000babd0f84e20510a0e17c9bd2ebf41294e5851181e0006081e5d80294e5040081e5000050e30700000a040080e25bf07ff59f1f90e1011081e2912f80e1000052e3faffff1a5bf07ff5ddf7e8ea";
    static final long PK = 0x10c0ba4L;
    static final String PK_O = "01008003010050031200001a", PK_N = "1400001a030050e31200000a";

    static volatile String pre = "地图物资/门补丁：" + (Ed.ED == 1 ? "没装" : "只在服务端装");

    /** 启动早装（Lan.preinstall 里调）。代码在进图前就要改好（运行中改已经跑过的代码不生效） */
    /** v2.21：云手机房主进图一会儿闪退，怀疑是这块。改成大厅开关，默认关；开了才装，关掉就还原。 */
    static volatile boolean on;
    static void preinstall() {
        if (Ed.ED != 1) return;
        pre = "地图物资/门：关（大厅开房页可打开）";
    }
    static String set(boolean want) {
        if (Ed.ED != 1) return "只在服务端有";
        String c, h, p;
        if (want) {
            c = Mods.write(SL_CAVE, SL_CAVE_O, SL_CAVE_N, true);
            h = "ok".equals(c) ? Mods.write(SL_HOOK, SL_HOOK_O, SL_HOOK_N, true) : "没装（cave " + c + "）";
            p = Mods.write(PK, PK_O, PK_N, true);
            on = "ok".equals(h) || "ok".equals(p);
        } else {
            h = Mods.write(SL_HOOK, SL_HOOK_O, SL_HOOK_N, false);
            c = Mods.write(SL_CAVE, SL_CAVE_O, SL_CAVE_N, false);
            p = Mods.write(PK, PK_O, PK_N, false);
            on = false;
        }
        pre = "地图物资/门：" + (on ? "开" : "关") + "（物资层 " + h + "，物资点 " + p + "）";
        LogCap.note(pre);
        try { Activity a = Con.act; if (a != null) a.getSharedPreferences("zframe", 0).edit().putBoolean("loot_on", on).apply(); } catch (Throwable t) {}
        return on ? "地图物资/门 已开" : "地图物资/门 已关";
    }

    // ---- 一局里的计数，只在导出诊断时显示 ----
    static volatile int layerAdds, pickupSpawn, doorLines, emptyBox, boxFilled;
    static volatile String lastDoor = "", lastLayer = "", boxState = "";
    static volatile float bx, by, bz; static volatile long boxAt;
    static final java.util.List<float[]> filled = new java.util.ArrayList<>();

    static void newMatch() {
        layerAdds = pickupSpawn = doorLines = emptyBox = boxFilled = 0;
        lastDoor = lastLayer = boxState = ""; boxAt = 0;
        synchronized (filled) { filled.clear(); }
    }

    /** Con 的日志监视每行都调（只做字符串判断） */
    static void line(String l) {
        if (Ed.ED != 1 || Lan.role != 1 || !on) return;
        if (l.contains("StreamingServerLayer Add")) { layerAdds++; if (lastLayer.isEmpty()) lastLayer = cut(l); return; }
        if (l.contains("[Pickup] SPAWN")) { pickupSpawn++; return; }
        if (l.contains("RegisterDoor") || l.contains("[DS Load]") || l.contains("Add %d Doors") || l.contains("Doors to GridMap")) { doorLines++; lastDoor = cut(l); return; }
        int i = l.indexOf("LockServer--Pos:");
        if (i >= 0) {
            try {
                String[] p = l.substring(i + 16).trim().split(",");
                bx = Float.parseFloat(p[0].trim()); by = Float.parseFloat(p[1].trim()); bz = Float.parseFloat(p[2].trim().split("\\s")[0]);
                boxAt = System.currentTimeMillis();
            } catch (Throwable ignored) {}
            return;
        }
        if (l.contains("PickupItemIDList.Num() = 0")) { emptyBox++; fillBox(); }
    }
    static String cut(String l) { l = l.trim(); return l.length() > 200 ? l.substring(0, 200) : l; }

    /** 空投箱是空的：在箱子位置放一套（狙 + 子弹 + 3 级头甲）。没拿到箱子位置就不放 */
    static void fillBox() {
        if (boxAt == 0 || System.currentTimeMillis() - boxAt > 5000) { boxState = "空投箱是空的，但没读到箱子位置，没放"; return; }
        final float x = bx, y = by, z = bz;
        synchronized (filled) {
            for (float[] f : filled) if (Math.abs(f[0] - x) < 500 && Math.abs(f[1] - y) < 500) return;   // 同一个箱子只放一次
            filled.add(new float[]{x, y});
        }
        new Thread(() -> {
            long[] kit = {20104000002L, 20600000006L, 20600000006L, 30304000005L, 30305000003L};   // AWM、.300 马格南×2、3 级头、3 级甲
            int ok = 0; String err = null;
            for (int k = 0; k < kit.length; k++) {
                double a = Math.PI * 2 * k / kit.length;
                try {
                    String e = Veh.dropAt(kit[k], (float) (x + 60 * Math.cos(a)), (float) (y + 60 * Math.sin(a)), z);
                    if (e == null) ok++; else err = e;
                } catch (Throwable t) { err = String.valueOf(t); }
            }
            boxFilled++;
            boxState = "空投箱补了 " + ok + "/" + kit.length + " 件（" + Math.round(x) + "," + Math.round(y) + "," + Math.round(z) + "）" + (err == null ? "" : "，" + err);
            LogCap.note(boxState);
        }, "zf-airdrop").start();
    }

    static volatile String flareState = "信号枪：v2.97 听服弹匣同步修好后服务端才认这一枪（叫飞机未实测）";
    /** 联机页一行 */
    static String shortText() {
        if (Ed.ED != 1) return "";
        return "地图物资/门：" + pre + (boxFilled > 0 ? "；" + boxState : "");
    }
    /** 导出诊断 */
    static String text() {
        return pre + "\n  这局：服务器层关卡 " + layerAdds + " 行" + (lastLayer.isEmpty() ? "" : "（" + lastLayer + "）")
            + "，物资生成日志 " + pickupSpawn + " 行，门日志 " + doorLines + " 行" + (lastDoor.isEmpty() ? "" : "（" + lastDoor + "）")
            + "，空投空箱 " + emptyBox + " 次，补箱 " + boxFilled + " 次" + (boxState.isEmpty() ? "" : "，" + boxState);
    }
}
