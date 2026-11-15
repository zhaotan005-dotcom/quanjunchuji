import com.zframe.Patcher;
import java.io.*; import java.nio.file.*;
public class H { public static void main(String[] a) throws Exception {
  String Z="/workspace/projects/443459da-30fb-4b48-b362-d673d5b53e8f/zframe/assets/";
  File u=new File(a[1]+".unsigned");
  String pkg=Patcher.patchBase(new File(a[0]), u, s->System.out.println(s), Files.readAllBytes(Paths.get(Z+"inject.dex")));
  System.out.println("pkg "+pkg);
  Patcher.sign(Files.readAllBytes(Paths.get(Z+"gkey.pk8")), new FileInputStream(Z+"gcert.der"), u, new File(a[1]));
  u.delete(); System.out.println("done");
}}
