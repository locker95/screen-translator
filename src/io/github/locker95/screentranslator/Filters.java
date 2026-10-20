package io.github.locker95.screentranslator;
import java.util.*; import java.util.regex.Pattern;
/** Pure-Java logic (no Android deps) so it can be unit tested on the JVM. */
public class Filters {
  public static final class Box { public int l,t,r,b; public Box(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}
    public int w(){return r-l;} public int h(){return b-t;} public long area(){return (long)Math.max(0,w())*Math.max(0,h());}
    public boolean contains(Box o){return l<=o.l&&t<=o.t&&r>=o.r&&b>=o.b;}
    public boolean intersects(Box o){return l<o.r&&o.l<r&&t<o.b&&o.t<b;} }
  public static final class Item { public final String text; public final Box box; public final boolean fromDesc; public final String id;
    public Item(String text,Box box,boolean fromDesc,String id){this.text=text;this.box=box;this.fromDesc=fromDesc;this.id=id;} }
  static final Pattern NUMERIC=Pattern.compile("(?iu)^[\\p{Punct}\\s]*(от|до|from|~|≈)?[\\s\\d.,:+\\-–—%×x*/()★☆]*\\d[\\s\\d.,:+\\-–—%×x*/()★☆]*\\s*(с|сом|сомони|смн|tjs|₽|руб|р|\\$|€|usd|мин|min|ч|сек|км|km|м|m|г|g|кг|kg|мл|ml|л|шт|отзыв\\w*)?\\.?\\s*$");
  /** Should this on-screen string be sent for translation? */
  public static boolean want(String s){
    if(s==null)return false; s=s.trim(); if(s.isEmpty())return false;
    int letters=0,digits=0; boolean nonCjk=false;
    for(int i=0;i<s.length();i++){char c=s.charAt(i);
      if(Character.isDigit(c))digits++;
      else if(Character.isLetter(c)){letters++; if(!(c>=0x4E00&&c<=0x9FFF)&&!(c>=0x3400&&c<=0x4DBF)&&!(c>=0x3040&&c<=0x30FF))nonCjk=true;}}
    if(letters==0||!nonCjk)return false;
    if(NUMERIC.matcher(s).matches())return false;
    if(digits>0&&letters<=3)return false; // "26 с", "4.7 ★", "15:23"
    return true; }
  /** Remove doubled labels: desc duplicating a text node, parent wrapping child with same text, identical text at overlapping/adjacent rects. */
  public static List<Item> dedupe(List<Item> in){
    List<Item> out=new ArrayList<>();
    for(int i=0;i<in.size();i++){Item a=in.get(i); boolean drop=false;
      for(int j=0;j<in.size()&&!drop;j++){ if(i==j)continue; Item b=in.get(j); if(!a.text.equals(b.text))continue;
        // prefer: real text over desc; smaller (child) over containing parent; earlier over later on tie
        boolean aWorse = (a.fromDesc&&!b.fromDesc) || (a.fromDesc==b.fromDesc && (a.box.contains(b.box)&&a.box.area()>b.box.area() || (a.box.area()==b.box.area()&&j<i)));
        if(!aWorse)continue;
        if(a.box.intersects(b.box)||a.box.contains(b.box)||near(a.box,b.box))drop=true; }
      if(!drop)out.add(a);}
    return out; }
  static boolean near(Box a,Box b){ // same column, vertically adjacent (icon desc above label)
    boolean hOverlap=a.l<b.r&&b.l<a.r; int gap=Math.max(a.t,b.t)-Math.min(a.b,b.b); int mh=Math.max(a.h(),b.h());
    return hOverlap&&gap<=mh; }
  /** Whitelist: translate only selected packages; never our own app or system UI. */
  public static boolean allowedPackage(String pkg,Set<String> whitelist,String self){
    return pkg!=null&&!pkg.equals(self)&&!pkg.equals("com.android.systemui")&&whitelist!=null&&whitelist.contains(pkg); }
  /** Sticky header/footer cuts for a vertical list. cands = nodes outside any vertical scroll container.
   *  topCut = max bottom of candidates starting in top 35% (ending above 50%); bottomCut = min top of candidates starting in bottom 20%.
   *  Candidates containing a vertical scroll container (its ancestors) are ignored. Result never extends past top/bottom inset. */
  public static int[] cuts(Box win,List<Box> cands,List<Box> vscrolls,int topInset){
    int H=win.h(); int top=Math.max(win.t,topInset), bottom=win.b;
    for(Box c:cands){ boolean anc=false; for(Box v:vscrolls) if(c.contains(v)){anc=true;break;} if(anc||c.h()<=0||c.w()<=0)continue;
      if(c.t<win.t+0.35*H&&c.b<=win.t+0.5*H) top=Math.max(top,c.b);
      if(c.t>win.b-0.2*H) bottom=Math.min(bottom,c.t); }
    return new int[]{top,bottom}; }
  /** Clip a list item to viewport and [top,bottom]; hide if under minHFrac of its height remains. */
  public static Box clipV(Box n,Box vp,int top,int bottom,double minHFrac){ Box c=intersect(n,vp); if(c==null)return null;
    c=intersect(c,new Box(c.l,top,c.r,bottom)); if(c==null)return null; if(n.h()>0&&c.h()<minHFrac*n.h())return null; return c; }
  /** Lines that fit in h (at least 1). */
  public static int maxLines(int h,float lineH){ return Math.max(1,(int)Math.floor(h/Math.max(1f,lineH))); }
  /** Pill rect is always the node rect cut by clip; never taller than the node. */
  public static Box pillBox(Box node,Box clip){ return intersect(node,clip); }
  /** v1.6.2 pill sizing: the drawn pill always covers the visible part of the source text (clip) and, where the
   *  source edge is not cut, extends past it by padX/padY so glyph overshoot can't peek out. Clamped to screen;
   *  edges cut by toolbar/tab cut lines stay at the cut (never bleed onto chrome). Never narrower/shorter than clip. */
  public static Box coverBox(Box node,Box clip,Box screen,int padX,int padY){
    int l=clip.l>node.l?clip.l:Math.max(screen.l,node.l-padX), r=clip.r<node.r?clip.r:Math.min(screen.r,node.r+padX);
    int t=clip.t>node.t?clip.t:Math.max(screen.t,node.t-padY), b=clip.b<node.b?clip.b:Math.min(screen.b,node.b+padY);
    return new Box(Math.min(l,clip.l),Math.min(t,clip.t),Math.max(r,clip.r),Math.max(b,clip.b)); }
  /** Trim padding so no pill covers another pill's source text; a pill is never trimmed below its own source (cover[i]). */
  public static void trimOverlaps(List<Box> boxes,List<Box> cover){
    int n=boxes.size(); List<Box> out=new ArrayList<>(n);
    for(int i=0;i<n;i++){ Box b=boxes.get(i), s=cover.get(i); int l=b.l,t=b.t,r=b.r,bo=b.b;
      for(int j=0;j<n;j++){ if(i==j)continue; Box o=cover.get(j); if(o.intersects(s))continue;
        if(!new Box(l,t,r,bo).intersects(o))continue;
        boolean vOv=o.t<s.b&&s.t<o.b, hOv=o.l<s.r&&s.l<o.r;
        if(vOv||!hOv){ if(o.l>=s.r)r=Math.min(r,Math.max(s.r,o.l)); else if(o.r<=s.l)l=Math.max(l,Math.min(s.l,o.r)); }
        if(!vOv){ if(o.t>=s.b)bo=Math.min(bo,Math.max(s.b,o.t)); else if(o.b<=s.t)t=Math.max(t,Math.min(s.t,o.b)); } }
      out.add(new Box(l,t,r,bo)); }
    for(int i=0;i<n;i++)boxes.set(i,out.get(i)); }
  /** Overlay drawing rule: only visible nodes with real getText() are drawn; contentDescription-only (images) never drawn. */
  public static boolean shouldDraw(boolean visibleToUser,boolean hasText){ return visibleToUser&&hasText; }
  public static Box intersect(Box a,Box b){ Box c=new Box(Math.max(a.l,b.l),Math.max(a.t,b.t),Math.min(a.r,b.r),Math.min(a.b,b.b)); return (c.w()<=0||c.h()<=0)?null:c; }
  /** Visible part of node inside viewport, or null if under minFrac of the node is inside. */
  public static Box clipFrac(Box n,Box viewport,double minFrac){ Box c=intersect(n,viewport); if(c==null)return null; if(n.area()>0&&c.area()<minFrac*n.area())return null; return c; }
  /** Clip to window; return null if less than half visible. */
  public static Box clip(Box n,Box win){ Box c=new Box(Math.max(n.l,win.l),Math.max(n.t,win.t),Math.min(n.r,win.r),Math.min(n.b,win.b));
    if(c.w()<=0||c.h()<=0)return null; if(n.area()>0&&c.area()*2<n.area())return null; return c; }
}
