import com.zframe.Patcher;
import java.io.*;
import java.nio.file.*;

/** 沙盒打整包：原版 game.apk + 内核 dex → patchBase(注入) → patchSound(声音+刷物品补丁, mczi=1 关原作者窗口) → gkey 签名。
 *  用法：java Host game.apk core/bN.dex 输出.apk 工作目录 */
public class Host {
    public static void main(String[] a) throws Exception {
        File in = new File(a[0]); byte[] dex = Files.readAllBytes(Paths.get(a[1]));
        File out = new File(a[2]); File wd = new File(a[3]); wd.mkdirs();
        Patcher.Log log = s -> System.out.println(s);
        File t1 = new File(wd, "t1.apk"), t2 = new File(wd, "t2.apk"), bak = new File(wd, "libMczi.orig.so");
        String pkg = Patcher.patchBase(in, t1, log, dex);
        Patcher.patchSound(t1, t2, true, null, 1, bak, log);
        t1.delete();
        byte[] key = Files.readAllBytes(Paths.get(a[4]));
        try (InputStream cert = new FileInputStream(a[5])) { Patcher.sign(key, cert, t2, out); }
        t2.delete();
        System.out.println("OK " + pkg + " -> " + out + " " + out.length());
    }
}
