import io.github.locker95.screentranslator.Filters; import io.github.locker95.screentranslator.Filters.Box; import java.util.*;
public class OccluderTest { static int fail=0; static void ck(boolean c,String m){if(!c){fail++;System.out.println("FAIL "+m);}}
 public static void main(String[] a){
  Box win=new Box(0,0,433,940); // edge-to-edge app window, status bar 0-30
  Box list=new Box(0,0,433,860); // Compose LazyColumn spans under header
  List<Box> v=Arrays.asList(list);
  List<Box> c=new ArrayList<>(Arrays.asList(
    new Box(0,0,433,940),            // root (ancestor of list) -> ignored
    new Box(10,45,40,80), new Box(50,48,140,78), new Box(380,45,410,80), // back, 'KFC', info
    new Box(0,100,433,155),          // tab chip row (horizontal scroll)
    new Box(10,105,130,150),         // chip
    new Box(0,860,433,940), new Box(20,870,90,930))); // bottom nav
  int[] cu=Filters.cuts(win,c,v,30);
  ck(cu[0]==155,"topCut "+cu[0]); ck(cu[1]==860,"bottomCut "+cu[1]);
  ck(Filters.clipV(new Box(170,80,300,100),list,cu[0],cu[1],0.7)==null,"'牛肉汉堡' under toolbar hidden");
  ck(Filters.clipV(new Box(180,5,400,45),list,cu[0],cu[1],0.7)==null,"description under status bar hidden");
  Box p=Filters.clipV(new Box(170,140,400,200),list,cu[0],cu[1],0.7); ck(p!=null&&p.t==155,"75% visible -> drawn, clipped at tab row");
  ck(Filters.clipV(new Box(170,120,400,180),list,cu[0],cu[1],0.7)==null,"58% visible -> hidden");
  ck(Filters.clipV(new Box(170,400,400,460),list,cu[0],cu[1],0.7)!=null,"mid-list drawn");
  System.out.println(fail==0?"ALL PASS":fail+" failures"); System.exit(fail);}}
