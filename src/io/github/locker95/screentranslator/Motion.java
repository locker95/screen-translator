package io.github.locker95.screentranslator;
import io.github.locker95.screentranslator.Filters.Box; import java.util.*;
/** Pure-Java motion classifier: nodes that stay put while others move are static chrome (toolbar, tabs, bottom nav). */
public class Motion {
  public final Map<String,Box> prev=new HashMap<>(); public final Map<String,Box> chrome=new HashMap<>();
  public boolean scrolledOnce=false;
  public static String key(String text,Box b){ return text+"|"+(b.l/8)+"|"+(b.w()/8); }
  public void reset(){prev.clear();chrome.clear();scrolledOnce=false;}
  /** Feed one read (key -> bounds). Returns true if list content moved since the previous read. */
  public boolean update(Map<String,Box> cur){
    List<String> stat=new ArrayList<>(); int moved=0;
    for(Map.Entry<String,Box> e:cur.entrySet()){ Box p=prev.get(e.getKey()); if(p==null)continue; Box c=e.getValue();
      if(p.t==c.t&&p.b==c.b&&p.l==c.l&&p.r==c.r)stat.add(e.getKey()); else if(p.t!=c.t)moved++; }
    boolean mv=moved>0;
    if(mv){ scrolledOnce=true; for(String k:stat)chrome.put(k,cur.get(k)); }
    for(String k:new ArrayList<>(chrome.keySet())){ Box c=cur.get(k); if(c!=null)chrome.put(k,c); }
    // a "chrome" node that later moves is demoted
    if(mv) for(Map.Entry<String,Box> e:cur.entrySet()){ Box p=prev.get(e.getKey()); if(p!=null&&p.t!=e.getValue().t)chrome.remove(e.getKey()); }
    prev.clear(); prev.putAll(cur); return mv; }
  public boolean isChrome(String key){return chrome.containsKey(key);}
  /** Occlusion bands: chrome in top half / bottom 25% expanded to full window width; other chrome expanded by pad. */
  public static List<Box> bands(Box win,Collection<Box> chrome,int pad){ List<Box> out=new ArrayList<>(); int H=win.h();
    for(Box c:chrome){ if(c.t<win.t+H/2||c.t>win.b-H/4) out.add(new Box(win.l,c.t-pad,win.r,c.b+pad)); else out.add(new Box(c.l-pad,c.t-pad,c.r+pad,c.b+pad)); }
    return out; }
  /** [topCut,bottomCut] from chrome; fallback (no scroll seen yet) from chip/title-like nodes in top 30%. */
  public static int[] cuts(Box win,Collection<Box> chrome,Collection<Box> fallbackTop,int topInset){ int H=win.h(); int top=Math.max(win.t,topInset), bot=win.b;
    if(!chrome.isEmpty()){ for(Box c:chrome){ if(c.t<win.t+H/2)top=Math.max(top,c.b); if(c.t>win.b-H/4)bot=Math.min(bot,c.t);} }
    else for(Box c:fallbackTop) if(c.t<win.t+0.3*H&&c.b<win.t+0.4*H)top=Math.max(top,c.b);
    return new int[]{top,bot}; }
  /** Final rect for a list (non-chrome) pill, or null if hidden. */
  public static Box listPill(Box node,int top,int bottom,List<Box> bands,Box win){
    Box c=Filters.clipV(node,win,top,bottom,0.7); if(c==null)return null;
    for(Box b:bands) if(b.intersects(node))return null; return c; }
}
