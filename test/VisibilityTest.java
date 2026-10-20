import io.github.locker95.screentranslator.Filters; import io.github.locker95.screentranslator.Filters.Box;
public class VisibilityTest { static int fail=0; static void ck(boolean c,String m){if(!c){fail++;System.out.println("FAIL "+m);}}
 public static void main(String[] a){
  ck(!Filters.shouldDraw(true,false),"image with only contentDescription is not drawn");
  ck(!Filters.shouldDraw(false,true),"invisible text node not drawn");
  ck(Filters.shouldDraw(true,true),"visible text drawn");
  Box list=new Box(0,400,1080,1900); // list viewport under toolbar+tabs
  ck(Filters.clipFrac(new Box(40,100,500,160),list,0.6)==null,"item scrolled under toolbar hidden");
  ck(Filters.clipFrac(new Box(40,370,500,430),list,0.6)==null,"50% inside -> hidden");
  Box c=Filters.clipFrac(new Box(40,390,500,450),list,0.6); ck(c!=null&&c.t==400,"83% inside -> drawn & clipped");
  ck(Filters.clipFrac(new Box(40,1000,500,1060),list,0.6)!=null,"fully inside drawn");
  ck(Filters.intersect(new Box(0,0,10,10),new Box(20,20,30,30))==null,"no intersect");
  System.out.println(fail==0?"ALL PASS":fail+" failures"); System.exit(fail);}}
