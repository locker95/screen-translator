import io.github.locker95.screentranslator.*; import io.github.locker95.screentranslator.Filters.Box; import java.util.*;
public class MotionTest { static int fail=0; static void ck(boolean c,String m){if(!c){fail++;System.out.println("FAIL "+m);}}
 static Map<String,Box> read(int dy){ Map<String,Box> m=new LinkedHashMap<>();
  m.put(Motion.key("KFC",new Box(50,48,140,78)),new Box(50,48,140,78));
  m.put(Motion.key("Бургеры",new Box(10,105,130,150)),new Box(10,105,130,150));
  m.put(Motion.key("Еда",new Box(20,900,90,930)),new Box(20,900,90,930));
  Box a=new Box(170,300-dy,400,330-dy), b=new Box(170,500-dy,400,530-dy);
  m.put(Motion.key("Чизбургер",a),a); m.put(Motion.key("Лонгер",b),b); return m; }
 public static void main(String[] x){
  Motion mo=new Motion(); Box win=new Box(0,0,433,940);
  ck(!mo.update(read(0)),"first read no motion"); ck(mo.chrome.isEmpty(),"no chrome before scroll");
  ck(mo.update(read(40)),"second read moved");
  ck(mo.isChrome(Motion.key("KFC",new Box(50,48,140,78)))&&mo.isChrome(Motion.key("Бургеры",new Box(10,105,130,150)))&&mo.isChrome(Motion.key("Еда",new Box(20,900,90,930))),"toolbar/tab/nav = chrome");
  ck(!mo.isChrome(Motion.key("Чизбургер",new Box(170,260,400,290))),"list item not chrome");
  int[] cu=Motion.cuts(win,mo.chrome.values(),Collections.emptyList(),30); ck(cu[0]==150&&cu[1]==900,"cuts "+cu[0]+"-"+cu[1]);
  List<Box> bands=Motion.bands(win,mo.chrome.values(),4);
  ck(bands.get(1).l==0&&bands.get(1).r==433,"tab band full width");
  ck(Motion.listPill(new Box(300,110,420,140),0,2000,bands,win)==null,"pill intersecting tab row hidden by band even past cut");
  ck(Motion.listPill(new Box(170,60,400,95),cu[0],cu[1],bands,win)==null,"pill under toolbar hidden");
  ck(Motion.listPill(new Box(170,400,400,430),cu[0],cu[1],bands,win)!=null,"mid list drawn");
  int[] fb=Motion.cuts(win,Collections.emptyList(),Arrays.asList(new Box(10,105,130,150),new Box(10,45,40,80)),30); ck(fb[0]==150,"fallback cut "+fb[0]);
  // no-overlap: pills never exceed node rect
  Box n1=new Box(170,300,400,330), n2=new Box(170,330,400,380);
  ck(!Filters.pillBox(n1,win).intersects(Filters.pillBox(n2,win)),"adjacent pills don't overlap");
  ck(Filters.maxLines(30,17.5f)==1&&Filters.maxLines(50,17.5f)==2&&Filters.maxLines(5,17.5f)==1,"maxLines");
  System.out.println(fail==0?"ALL PASS":fail+" failures"); System.exit(fail);}}
