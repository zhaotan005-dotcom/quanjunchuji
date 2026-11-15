package com.zframe;

import android.content.Context;

import com.android.apksig.ApkSigner;
import com.zframe.axml.Axml;

import java.io.*;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** 免 root 注入：把修改器 dex 和启动入口注入游戏 APK，再重新签名。 */
public class Patcher {
    public interface Log { void log(String s); }

    public static final String SHARED_UID = "com.zframe.shared";
    static final int RES_SHARED_UID = 0x0101000b, RES_DEBUGGABLE = 0x0101000f;
    static final int RES_NAME = 0x01010003, RES_EXPORTED = 0x01010010, RES_AUTHORITIES = 0x01010018,
            RES_INIT_ORDER = 0x0101001a;

    /** 修补 base.apk：注入 provider + dex，去掉旧签名。返回包名。 */
    public static String patchBase(Context ctx, File in, File outUnsigned, Log log) throws Exception {
        return patchBase(ctx, in, outUnsigned, log, true);
    }

    /** 设置/替换元素上的属性 */
    static void setAttr(Axml.Node n, Axml.Attr a) {
        n.attrs.removeIf(x -> x.resId == a.resId || (a.name.equals(x.name) && a.ns != null && a.ns.equals(x.ns)));
        n.attrs.add(a);
        n.attrs.sort((x, y) -> Integer.compareUnsigned(x.resId == 0 ? -1 : x.resId, y.resId == 0 ? -1 : y.resId));
    }

    /**
     * inject=true：游戏，注入修改器 dex；inject=false：工具（GG 等），只改共享 UID。
     * 两者都加 sharedUserId=com.zframe.shared 且用同一把钥匙签名，于是在同一个 Linux 用户下运行；
     * 游戏另设 debuggable，进程可被同 UID 的工具读写内存。
     */
    public static String patchBase(Context ctx, File in, File outUnsigned, Log log, boolean inject) throws Exception {
        return patchBase(in, outUnsigned, log, inject ? readAll(ctx.getAssets().open("inject.dex")) : null);
    }

    public static String patchBase(File in, File outUnsigned, Log log, byte[] dex) throws Exception {
        boolean inject = dex != null;
        String pkg;
        int maxDex = 0;
        Pattern dexP = Pattern.compile("^classes(\\d*)\\.dex$");
        try (ZipFile zf = new ZipFile(in)) {
            ZipEntry me = zf.getEntry("AndroidManifest.xml");
            if (me == null) throw new IOException("APK 里没有 AndroidManifest.xml（可能是分包，不是 base）");
            byte[] mraw = readAll(zf.getInputStream(me));
            if (inject && (new String(mraw, "UTF-16LE").contains("com.zframe.inject.ZInit")
                    || new String(mraw, "ISO-8859-1").contains("com.zframe.inject.ZInit")))
                throw new IOException("这个游戏已经注入过 ZFrame 了，不用再注入。直接在游戏卡片上点「声音补丁」。");
            Axml x = Axml.parse(mraw);
            int mi = x.findStart("manifest");
            pkg = x.attr(mi, "package");
            log.log("包名：" + pkg);
            int ai = x.findStart("application");
            if (ai < 0) throw new IOException("找不到 <application>");
            setAttr(x.nodes.get(mi), Axml.str("sharedUserId", RES_SHARED_UID, SHARED_UID));
            setAttr(x.nodes.get(ai), Axml.bool("debuggable", RES_DEBUGGABLE, true));
            if (!inject) {
                byte[] manifest = x.write();
                log.log("工具模式：共享 UID " + SHARED_UID + "，重新打包中…");
                copyZip(zf, outUnsigned, manifest, null, null);
                return pkg;
            }
            int ae = x.findEnd(ai);
            int line = x.nodes.get(ai).line;
            List<Axml.Node> add = new ArrayList<>();
            add.add(Axml.start("provider", line,
                    Axml.str("name", RES_NAME, "com.zframe.inject.ZInit"),
                    Axml.bool("exported", RES_EXPORTED, false),
                    Axml.str("authorities", RES_AUTHORITIES, pkg + ".zframe.init"),
                    new Axml.Attr(Axml.ANDROID_NS, "initOrder", RES_INIT_ORDER, Axml.TYPE_INT, 9999, null, null)));
            add.add(Axml.end("provider", line));
            x.nodes.addAll(ae, add);
            // <queries><package android:name="com.zframe"/></queries>，让游戏能读到框架里的插件
            int me2 = x.findEnd(x.findStart("manifest"));
            List<Axml.Node> q = new ArrayList<>();
            q.add(Axml.start("queries", line));
            q.add(Axml.start("package", line, Axml.str("name", RES_NAME, "com.zframe")));
            q.add(Axml.end("package", line));
            q.add(Axml.end("queries", line));
            x.nodes.addAll(me2, q);
            byte[] manifest = x.write();

            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                Matcher m = dexP.matcher(en.nextElement().getName());
                if (m.matches()) maxDex = Math.max(maxDex, m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1)));
            }
            String dexName = "classes" + (maxDex + 1) + ".dex";
            log.log("注入 " + dexName + "，重新打包中（大游戏需要几分钟）…");
            copyZip(zf, outUnsigned, manifest, dexName, dex);
        }
        return pkg;
    }

    /** 复制 zip，去掉旧签名；可替换 manifest、追加 dex。保持 STORED 条目不压缩。 */
    public static void copyZip(ZipFile zf, File out, byte[] manifest, String dexName, byte[] dex) throws IOException {
        byte[] buf = new byte[1 << 16];
        try (ZipOutputStream zo = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out), 1 << 16))) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA")
                        || n.endsWith(".EC") || n.equals("META-INF/MANIFEST.MF"))) continue;
                if (manifest != null && n.equals("AndroidManifest.xml")) {
                    put(zo, n, manifest, ZipEntry.DEFLATED);
                    continue;
                }
                ZipEntry ne = new ZipEntry(n);
                if (e.getMethod() == ZipEntry.STORED) {
                    ne.setMethod(ZipEntry.STORED);
                    ne.setSize(e.getSize());
                    ne.setCompressedSize(e.getSize());
                    ne.setCrc(e.getCrc());
                }
                zo.putNextEntry(ne);
                try (InputStream is = zf.getInputStream(e)) {
                    int r;
                    while ((r = is.read(buf)) > 0) zo.write(buf, 0, r);
                }
                zo.closeEntry();
            }
            if (dex != null) put(zo, dexName, dex, ZipEntry.DEFLATED);
        }
    }

    static void put(ZipOutputStream zo, String name, byte[] data, int method) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(method);
        if (method == ZipEntry.STORED) {
            CRC32 c = new CRC32(); c.update(data);
            e.setSize(data.length); e.setCompressedSize(data.length); e.setCrc(c.getValue());
        }
        zo.putNextEntry(e);
        zo.write(data);
        zo.closeEntry();
    }

    /** 用框架自带的密钥签名（v1+v2+v3），并按 16KB 对齐 .so */
    public static void sign(Context ctx, File in, File out) throws Exception {
        sign(readAll(ctx.getAssets().open("gkey.pk8")), ctx.getAssets().open("gcert.der"), in, out);
    }

    public static void sign(byte[] k, InputStream certIn, File in, File out) throws Exception {
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(k));
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(certIn);
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("zframe", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(in).setOutputApk(out)
                .setMinSdkVersion(24)
                .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .setAlignmentPreserved(false).setLibraryPageAlignmentBytes(16384)
                .build().sign();
    }

    /** 分包只需去掉旧签名再用同一把钥匙签名 */
    public static void resignSplit(Context ctx, File in, File out) throws Exception {
        File tmp = new File(out.getPath() + ".tmp");
        try (ZipFile zf = new ZipFile(in)) { copyZip(zf, tmp, null, null, null); }
        sign(ctx, tmp, out);
        tmp.delete();
    }

    // ================= 声音补丁（v1.20） =================
    // 游戏的 Wwise 模块（AkAudio）从来没被加载。引擎开机约 3 秒时会在游戏线程里加载一个不存在的模块
    // "SoftwareOcclusionCulling"（只加载一次，返回值只存起来不用）。把这个 UTF-32 名字改成 "AkAudio"，
    // 引擎就会自己把 Wwise 模块加载起来。只改 libUE4.so 里 32 字节，可以随时还原。
    public static final String UE4_LIB = "lib/armeabi-v7a/libUE4.so";
    public static final long SND_OFF = 0x45e7104L;
    public static final byte[] SND_ORIG = u32("SoftwareOcclusionCulling");   // 100 字节（含结尾 0）
    public static final byte[] SND_NEW;
    static {
        byte[] ak = u32("AkAudio");            // 32 字节
        SND_NEW = SND_ORIG.clone();
        System.arraycopy(ak, 0, SND_NEW, 0, ak.length);
    }

    static byte[] u32(String s) {
        byte[] b = new byte[(s.length() + 1) * 4];
        for (int i = 0; i < s.length(); i++) b[i * 4] = (byte) s.charAt(i);
        return b;
    }

    /** 返回 1=原版，2=已打声音补丁，0=对不上（不是这个版本的 libUE4，不能改） */
    public static int soundState(File apk) throws IOException {
        try (ZipFile zf = new ZipFile(apk)) {
            ZipEntry e = zf.getEntry(UE4_LIB);
            if (e == null) return 0;
            byte[] cur = new byte[SND_ORIG.length];
            try (InputStream is = zf.getInputStream(e)) {
                skipFully(is, SND_OFF);
                readFully(is, cur);
            }
            if (Arrays.equals(cur, SND_ORIG)) return 1;
            if (Arrays.equals(cur, SND_NEW)) return 2;
            return 0;
        }
    }


    // v1.26 刷物品补丁：游戏自带的 GiveItem 命令原本是空函数（bx lr）。把它接到游戏给机器人发装备用的那段真代码上
    // （0xccecbc 查能放几个 + 0xcd3bdc 真正放进身上/背包），代码放在没用的 GM 面板“给手雷”函数体里。
    // 用法：控制台 GiveItem <编号>[x数量]。和声音补丁一起打、一起还原。
    public static final long UPD_OFF = 0x15de030L; static final String UPD_O = "f04f2de9", UPD_N = "1eff2fe1";
    public static final long[] GI_OFF = {0xe40cccL, 0x173c91cL, 0x17169c0L};
    static final String[] GI_O = {
        "578bf4eb",
        "2768ffea",
        "f04f2de91cb08de20cd04de2c40190e5000050e3c000000a700090e5000050e31840901500005413bb00000a9e71ddeb0c1094e55c2090e55c3091e5030052e1b50000ca581091e5580080e2021191e7000051e1b000001a9f2cebeb0c1094e55c2090e55c3091e5030052e1aa0000ca581091e5580080e2021191e7000051e1a500001ab44294e5000054e3a200000a74beeaeb0c1094e55c2090e55c3091e5030052e19c0000ca581091e5580080e2021191e7000051e19700001a0060a0e30c00a0e30d80a0e100608de508608de50010a0e304008de50800a0e1b777d1eb00009de532a0a0e33070a0e33490a0e33150a0e30810a0e100a080e5047080e5089080e50c7080e5105080e5147080e5187080e51c7080e5207080e5247080e5285080e52c6080e50400a0e1cf33d1eb00009de5000050e30000000ab7cc09eb0c00a0e30d80a0e100608de50010a0e308608de504008de50800a0e19777d1eb00009de50810a0e100a080e5800280e90c7080e5105080e5147080e5187080e51c7080e5207080e5247080e5285080e52c6080e50400a0e1b433d1eb00009de5000050e30000000a9ccc09eb0060a0e30c00a0e30d80a0e100608de508608de50010a0e304008de50800a0e17b77d1eb00009de532a0a0e33070a0e33490a0e33150a0e30810a0e100a080e5047080e5089080e50c7080e5105080e5147080e5187080e51c7080e5207080e5247080e5285080e52c6080e50400a0e19333d1eb00009de5000050e30000000a7bcc09eb0c00a0e30d80a0e100608de50010a0e308608de504008de50800a0e1"};
    static final String[] GI_N = {
        "3b5723eb",
        "0000a0e1",
        "f04f2de944d04de20040a0e1005091e5046091e5000055e38900000a020056e3870000ba000095e5500050e38400001ac04294e5000054e38100000a0070a0e30080a0e30090a0e3060059e17c0000aa090195e7300040e2090050e301908982f8ffff8a060059e10a0000aa090195e7300040e2090050e30600008a0a10a0e3972183e0983123e0007092e00080a3e2019089e2f2ffffea00a0a0e3060059e10e0000aa090195e7300040e2090050e301908982f8ffff8a060059e1070000aa090195e7300040e2090050e30300008a0aa18ae08aa080e0019089e2f5ffffea00005ae301a0a003e70300e300005ae100a0a0c100c0a0e3060059e10e0000aa090195e7300040e2090050e301908982f8ffff8a060059e1070000aa090195e7300040e2090050e30300008a0cc18ce08cc080e0019089e2f5ffffea30c08de500005ce30300000a010a05e300008ce504408ce514a08ce5080097e13800000a0700a0e10810a0e1102702e30030a0e38c59c6eb0070a0e10280a0e138b894e530c09de500005ce308b08c150c708c1510808c1500005be32900000a0000a0e300008de50c008de50000e0e304008de508008de50b00a0e10710a0e10820a0e10a30a0e14ce0d6eb30c09de500005ce318008c15010050e30a00a0b10060a0e10000a0e340008de508008de50c008de500608de50010e0e304108de510608de540108de214108de518008de51c008de520008de524008de528008de50400a0e10710a0e10820a0e10030a0e3faf3d6eb30c09de500005ce31c008c15020a051300008c1544d08de2f08fbde8"};
    // v1.26 打进去的旧版代码（没有诊断），认得它，好直接升级
    static final String[] GI_OLD2 = {"f04f2de944d04de20040a0e1005091e5046091e5000055e35b00000a0070a0e30080a0e30090a0e3060059e1560000aa090195e7300040e2090050e301908982f8ffff8a060059e10a0000aa090195e7300040e2090050e30600008a0a10a0e3972183e0983123e0007092e00080a3e2019089e2f2ffffea00a0a0e3060059e10e0000aa090195e7300040e2090050e301908982f8ffff8a060059e1070000aa090195e7300040e2090050e30300008a0aa18ae08aa080e0019089e2f5ffffea00005ae301a0a003e70300e300005ae100a0a0c1080097e12b00000a0700a0e10810a0e1102702e30030a0e3ad59c6eb0070a0e10280a0e138b894e500005be32100000a0000a0e300008de50c008de50000e0e304008de508008de50b00a0e10710a0e10820a0e10a30a0e172e0d6eb010050e30a00a0b10060a0e10000a0e340008de508008de50c008de500608de50010e0e304108de510608de540108de214108de518008de51c008de520008de524008de528008de50400a0e10710a0e10820a0e10030a0e323f4d6eb44d08de2f08fbde80400a0e1b433d1eb00009de5000050e30000000a9ccc09eb0060a0e30c00a0e30d80a0e100608de508608de50010a0e304008de50800a0e17b77d1eb00009de532a0a0e33070a0e33490a0e33150a0e30810a0e100a080e5047080e5089080e50c7080e5105080e5147080e5187080e51c7080e5207080e5247080e5285080e52c6080e50400a0e19333d1eb00009de5000050e30000000a7bcc09eb0c00a0e30d80a0e100608de50010a0e308608de504008de50800a0e1", "f04f2de944d04de20040a0e1005091e5046091e5000055e38100000a0070a0e30080a0e30090a0e3060059e17c0000aa090195e7300040e2090050e301908982f8ffff8a060059e10a0000aa090195e7300040e2090050e30600008a0a10a0e3972183e0983123e0007092e00080a3e2019089e2f2ffffea00a0a0e3060059e10e0000aa090195e7300040e2090050e301908982f8ffff8a060059e1070000aa090195e7300040e2090050e30300008a0aa18ae08aa080e0019089e2f5ffffea00005ae301a0a003e70300e300005ae100a0a0c100c0a0e3060059e10e0000aa090195e7300040e2090050e301908982f8ffff8a060059e1070000aa090195e7300040e2090050e30300008a0cc18ce08cc080e0019089e2f5ffffea30c08de500005ce30300000a010a05e300008ce504408ce514a08ce5080097e13800000a0700a0e10810a0e1102702e30030a0e39459c6eb0070a0e10280a0e138b894e530c09de500005ce308b08c150c708c1510808c1500005be32900000a0000a0e300008de50c008de50000e0e304008de508008de50b00a0e10710a0e10820a0e10a30a0e154e0d6eb30c09de500005ce318008c15010050e30a00a0b10060a0e10000a0e340008de508008de50c008de500608de50010e0e304108de510608de540108de214108de518008de51c008de520008de524008de528008de50400a0e10710a0e10820a0e10030a0e302f4d6eb30c09de500005ce31c008c15020a051300008c1544d08de2f08fbde87bcc09eb0c00a0e30d80a0e100608de50010a0e308608de504008de50800a0e1"};
    static byte[] hex(String h) { byte[] b = new byte[h.length() / 2]; for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16); return b; }

    /** 1=原版 2=已打刷物品补丁 3=打的是 v1.26 旧版 0=对不上 */
    public static int giveState(File apk) throws IOException {
        try (ZipFile zf = new ZipFile(apk)) {
            ZipEntry e = zf.getEntry(UE4_LIB);
            if (e == null) return 0;
            boolean allO = true, allN = true; boolean[] allOld = new boolean[GI_OLD2.length]; Arrays.fill(allOld, true);
            for (int k = 0; k < GI_OFF.length; k++) {
                byte[] o = hex(GI_O[k]), n = hex(GI_N[k]), cur = new byte[o.length];

                try (InputStream is = zf.getInputStream(e)) { skipFully(is, GI_OFF[k]); readFully(is, cur); }
                if (!Arrays.equals(cur, o)) allO = false;
                if (!Arrays.equals(cur, n)) allN = false;
                for (int j = 0; j < GI_OLD2.length; j++) if (!Arrays.equals(cur, k == 2 ? hex(GI_OLD2[j]) : n)) allOld[j] = false;
            }
            if (allN) return 2; if (allO) return 1; for (boolean b : allOld) if (b) return 3; return 0;
        }
    }

    static void skipFully(InputStream is, long n) throws IOException {
        byte[] b = new byte[1 << 16];
        while (n > 0) { int r = is.read(b, 0, (int) Math.min(b.length, n)); if (r < 0) throw new EOFException(); n -= r; }
    }

    static void readFully(InputStream is, byte[] b) throws IOException {
        int o = 0;
        while (o < b.length) { int r = is.read(b, o, b.length - o); if (r < 0) throw new EOFException(); o += r; }
    }

    /** 把 libUE4.so 从 InputStream 复制到 out，同时把 offs[k] 处的字节换成 reps[k] */
    static void copyPatched(InputStream is, OutputStream out, long[] offs, byte[][] reps, CRC32 crc) throws IOException {
        byte[] b = new byte[1 << 16];
        long pos = 0; int r;
        while ((r = is.read(b)) > 0) {
            long s = pos, e = pos + r;
            for (int k = 0; k < offs.length; k++) {
                long ps = offs[k], pe = offs[k] + reps[k].length;
                if (e > ps && s < pe) {
                    long from = Math.max(s, ps), to = Math.min(e, pe);
                    System.arraycopy(reps[k], (int) (from - ps), b, (int) (from - s), (int) (to - from));
                }
            }
            if (crc != null) crc.update(b, 0, r);
            if (out != null) out.write(b, 0, r);
            pos = e;
        }
    }

    /** 复制已注入的游戏 base.apk，只改 libUE4.so 那 32 字节（toAk=true 打补丁，false 还原），其它原样 */
    public static void patchSound(File in, File outUnsigned, boolean toAk, Log log) throws IOException { patchSound(in, outUnsigned, toAk, null, log); }

    static boolean isOurDex(byte[] d) {
        byte[] k = "Lcom/zframe/inject/ZInit;".getBytes();
        outer:
        for (int i = 0; i + k.length <= d.length; i++) {
            for (int j = 0; j < k.length; j++) if (d[i + j] != k[j]) continue outer;
            return true;
        }
        return false;
    }

    /** newDex 不为空时，顺便把游戏里注入的 ZFrame dex 换成新的（游戏内置内核也更新） */
    public static final String MCZI = "lib/armeabi-v7a/libMczi.so", ZLIB = "lib/armeabi-v7a/libzlib.so";
    /** 原作者窗口状态：0=没有这个库，1=在，2=已关（换成了空壳） */
    public static int mcziState(File apk) throws IOException {
        try (ZipFile zf = new ZipFile(apk)) {
            ZipEntry m = zf.getEntry(MCZI), z = zf.getEntry(ZLIB);
            if (m == null) return 0;
            if (z != null && m.getCrc() == z.getCrc() && m.getSize() == z.getSize()) return 2;
            return 1;
        }
    }

    public static void patchSound(File in, File outUnsigned, boolean toAk, byte[] newDex, Log log) throws IOException {
        patchSound(in, outUnsigned, toAk, newDex, 0, null, log);
    }

    /** mczi：0=不动，1=关掉原作者窗口（备份原库到 backup 再换成空壳），2=恢复（从 backup 放回） */
    public static void patchSound(File in, File outUnsigned, boolean toAk, byte[] newDex, int mczi, File backup, Log log) throws IOException {
        byte[] want = toAk ? SND_NEW : SND_ORIG;
        long[] offs; byte[][] reps;
        int gs = 0;
        try { gs = giveState(in); } catch (Throwable ignored) {}
        if (gs == 0) {
            log.log("刷物品补丁：这个 libUE4 对不上，跳过");
            offs = new long[]{SND_OFF}; reps = new byte[][]{want};
        } else {
            offs = new long[GI_OFF.length + 2]; reps = new byte[GI_OFF.length + 2][];
            offs[0] = SND_OFF; reps[0] = want;
            // v1.37：去掉启动时「更新中(100) 模块其他错误 Err:107」黄框（UNZUpdateUIView::ShowUpdateError 直接返回）
            offs[GI_OFF.length + 1] = UPD_OFF; reps[GI_OFF.length + 1] = hex(toAk ? UPD_N : UPD_O);
            for (int k = 0; k < GI_OFF.length; k++) { offs[k + 1] = GI_OFF[k]; reps[k + 1] = hex(toAk ? GI_N[k] : GI_O[k]); }
            log.log(toAk ? "刷物品补丁" : "还原刷物品补丁");
        }
        byte[] buf = new byte[1 << 16];
        try (ZipFile zf = new ZipFile(in);
             ZipOutputStream zo = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outUnsigned), 1 << 16))) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA")
                        || n.endsWith(".EC") || n.equals("META-INF/MANIFEST.MF"))) continue;
                if (newDex != null && n.matches("^classes\\d*\\.dex$")) {
                    byte[] d = readAll(zf.getInputStream(e));
                    if (isOurDex(d)) {
                        log.log("更新内核");
                        ZipEntry de = new ZipEntry(n);
                        de.setMethod(ZipEntry.DEFLATED);
                        zo.putNextEntry(de); zo.write(newDex); zo.closeEntry();
                        continue;
                    }
                }
                if (mczi != 0 && n.equals(MCZI)) {
                    byte[] d;
                    if (mczi == 1) {
                        byte[] cur = readAll(zf.getInputStream(e));
                        ZipEntry z = zf.getEntry(ZLIB);
                        byte[] stub = readAll(zf.getInputStream(z));
                        if (!java.util.Arrays.equals(cur, stub)) {
                            try (OutputStream o = new FileOutputStream(backup)) { o.write(cur); }
                            
                        }
                        d = stub;
                    } else {
                        if (backup == null || !backup.isFile()) throw new IOException("没有找到原作者库的备份，不能恢复");
                        try (InputStream bi = new FileInputStream(backup)) { d = readAll(bi); }
                        log.log("恢复原作者窗口");
                    }
                    put(zo, n, d, e.getMethod() == ZipEntry.STORED ? ZipEntry.STORED : ZipEntry.DEFLATED);
                    continue;
                }
                if (n.equals("assets/QCC_APM_PUBGM")) {
                    byte[] d = readAll(zf.getInputStream(e));
                    String js = com.zframe.inject.Qcc.removeModel(new String(d, "UTF-8"), android.os.Build.MODEL);
                    if (js != null) { d = js.getBytes("UTF-8"); log.log("机型表还原"); }
                    put(zo, n, d, e.getMethod() == ZipEntry.STORED ? ZipEntry.STORED : ZipEntry.DEFLATED);
                    continue;
                }
                ZipEntry ne = new ZipEntry(n);
                boolean lib = n.equals(UE4_LIB);
                if (e.getMethod() == ZipEntry.STORED) {
                    ne.setMethod(ZipEntry.STORED);
                    ne.setSize(e.getSize());
                    ne.setCompressedSize(e.getSize());
                    if (lib) {
                        CRC32 c = new CRC32();
                        try (InputStream is = zf.getInputStream(e)) { copyPatched(is, null, offs, reps, c); }
                        ne.setCrc(c.getValue());
                    } else ne.setCrc(e.getCrc());
                }
                if (lib) log.log("改游戏库…");
                zo.putNextEntry(ne);
                try (InputStream is = zf.getInputStream(e)) {
                    if (lib) copyPatched(is, zo, offs, reps, null);
                    else { int r; while ((r = is.read(buf)) > 0) zo.write(buf, 0, r); }
                }
                zo.closeEntry();
            }
        }
    }

    /** 已安装的游戏是不是用 ZFrame 的钥匙签的（是才能覆盖安装，不用卸载） */
    public static boolean signedByUs(Context ctx, String pkg) {
        try {
            byte[] mine = readAll(ctx.getAssets().open("gcert.der"));
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            android.content.pm.Signature[] sg = pi.signingInfo.hasMultipleSigners()
                    ? pi.signingInfo.getApkContentsSigners() : pi.signingInfo.getSigningCertificateHistory();
            for (android.content.pm.Signature s : sg) if (Arrays.equals(s.toByteArray(), mine)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    public static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[1 << 16]; int r;
            while ((r = i.read(b)) > 0) bo.write(b, 0, r);
            return bo.toByteArray();
        }
    }
}
