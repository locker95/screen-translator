import io.github.locker95.screentranslator.Filters; import io.github.locker95.screentranslator.Filters.Box; import java.util.*;
/** v1.6.2: pills must fully cover their source text (no Russian tail peeking out), with padding, clamped sensibly. */
public class CoverTest { static int fail=0; static void ck(boolean c,String m){if(!c){fail++;System.out.println("FAIL "+m);}}
 static boolean covers(Box a,Box b){return a.contains(b);}
 public static void main(String[] x){
  Box screen=new Box(0,30,1080,2340);
  // section header 「Горячие Блюда」 fully visible: pill >= source + padding on every side
  Box hdr=new Box(40,600,520,660); Box p=Filters.coverBox(hdr,hdr,screen,8,3);
  ck(covers(p,hdr),"covers source"); ck(p.l==32&&p.r==528&&p.t==597&&p.b==663,"padded "+p.l+","+p.t+","+p.r+","+p.b);
  ck(p.w()>=hdr.w()&&p.h()>=hdr.h(),"never narrower/shorter than source");
  // tab at screen edge: padding clamped to screen, but still covers the source
  Box tab=new Box(0,200,180,250); p=Filters.coverBox(tab,tab,screen,8,3); ck(p.l==0&&covers(p,tab),"clamped at left edge, still covers");
  Box tabR=new Box(960,200,1080,250); p=Filters.coverBox(tabR,tabR,screen,8,3); ck(p.r==1080&&covers(p,tabR),"clamped at right edge");
  // list item cut by toolbar cut line at y=300: top stays at the cut (no bleed onto toolbar), rest padded
  Box dish=new Box(170,280,900,340), clip=new Box(170,300,900,340); p=Filters.coverBox(dish,clip,screen,8,3);
  ck(p.t==300,"top stays at cut "+p.t); ck(p.l==162&&p.r==908&&p.b==343,"other sides padded"); ck(covers(p,clip),"covers visible source");
  // long dish name 「...эзогелином」 wider than its pill text would be: pill width >= source width
  Box name=new Box(170,800,1000,880); p=Filters.coverBox(name,name,screen,8,3); ck(p.w()>=name.w()+16,"long name fully covered");
  // neighbours: padding trimmed so a pill never covers another pill's source, but each still covers its own source
  Box a=new Box(100,200,300,250), b=new Box(304,200,500,250), c=new Box(100,252,300,300);
  List<Box> cov=Arrays.asList(a,b,c); List<Box> boxes=new ArrayList<>(); for(Box s:cov)boxes.add(Filters.coverBox(s,s,screen,8,3));
  Filters.trimOverlaps(boxes,cov);
  for(int i=0;i<3;i++){ ck(covers(boxes.get(i),cov.get(i)),"own source covered "+i);
    for(int j=0;j<3;j++) if(i!=j) ck(!boxes.get(i).intersects(cov.get(j)),"pill "+i+" doesn't cover source "+j); }
  ck(boxes.get(0).l==92,"padding kept where no neighbour");
  // touching sources (gap 0): no trim below source
  Box d=new Box(100,400,300,450), e=new Box(300,400,500,450); List<Box> cv2=Arrays.asList(d,e); List<Box> b2=new ArrayList<>(Arrays.asList(Filters.coverBox(d,d,screen,8,3),Filters.coverBox(e,e,screen,8,3)));
  Filters.trimOverlaps(b2,cv2); ck(b2.get(0).r==300&&b2.get(1).l==300,"touching: trimmed exactly to source edge");
  System.out.println(fail==0?"ALL PASS":fail+" failures"); System.exit(fail);}}
