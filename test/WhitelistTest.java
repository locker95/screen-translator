import io.github.locker95.screentranslator.Filters; import java.util.*;
public class WhitelistTest { static int fail=0; static void ck(boolean c,String m){if(!c){fail++;System.out.println("FAIL "+m);}}
 public static void main(String[] a){ Set<String> w=new HashSet<>(Collections.singleton("com.zood.mobile")); String self="io.github.locker95.screentranslator";
  ck(Filters.allowedPackage("com.zood.mobile",w,self),"milli allowed");
  ck(!Filters.allowedPackage("com.android.chrome",w,self),"other blocked");
  ck(!Filters.allowedPackage(self,new HashSet<>(Arrays.asList(self)),self),"self never");
  ck(!Filters.allowedPackage("com.android.systemui",new HashSet<>(Arrays.asList("com.android.systemui")),self),"systemui never");
  ck(!Filters.allowedPackage(null,w,self),"null"); ck(!Filters.allowedPackage("com.zood.mobile",Collections.emptySet(),self),"empty list");
  System.out.println(fail==0?"ALL PASS":fail+" failures"); System.exit(fail);}}
