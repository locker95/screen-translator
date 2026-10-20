package io.github.locker95.screentranslator;
import android.accessibilityservice.AccessibilityService; import android.app.*; import android.graphics.*; import android.os.*; import android.util.Log;
import android.view.*; import android.view.accessibility.*; import android.widget.Toast; import java.util.*; import java.util.concurrent.*;
import io.github.locker95.screentranslator.Filters.Box; import io.github.locker95.screentranslator.Filters.Item;
/**
 * v1.6 sync design (Compose-robust, no reliance on scrollable flags or scroll events):
 *  - Full read (walker thread) collects visible text nodes; Motion compares positions with the previous read:
 *    nodes that stay still while others move become "static chrome" (toolbar/tabs/bottom nav) for this screen.
 *  - Chrome defines occlusion: topCut/bottomCut + full-width bands; list pills intersecting a band are hidden.
 *  - Any VIEW_SCROLLED / CONTENT_CHANGED starts a 16ms motion loop that refresh()es 3 sample list nodes.
 *    If refresh gives live bounds -> TRACK mode (all list pills re-positioned every frame);
 *    else SNAP mode (list pills hidden on first detected movement, chrome pills stay).
 *    A full read runs every ~100ms during motion; 100ms without movement -> immediate full read + draw from cache.
 */
public class TranslatorService extends AccessibilityService {
  static final String TAG="ScreenTr"; static volatile TranslatorService inst; static volatile Store store;
  final Map<String,Long> failed=new ConcurrentHashMap<>();
  final Handler h=new Handler(Looper.getMainLooper()); final ExecutorService net=Executors.newSingleThreadExecutor();
  HandlerThread wt; Handler wh;
  final LinkedHashSet<String> pending=new LinkedHashSet<>(), prePending=new LinkedHashSet<>(); // main thread only
  WindowManager wm; OverlayView ov; boolean busy=false; volatile Set<String> whitelist=new HashSet<>(); int statusH=0; float dp=1;
  final android.content.SharedPreferences.OnSharedPreferenceChangeListener pl=(sp,k)->{ if("apps".equals(k)){whitelist=Prefs.apps(this); h.post(()->{clearNow();requestWalk();});} if("debug".equals(k))requestWalk(); };
  static final class Tracked { AccessibilityNodeInfo n; String text,zh,key; boolean chrome; int lastTop; Tracked(AccessibilityNodeInfo n,String t,String z,String k,boolean c,int top){this.n=n;text=t;zh=z;key=k;chrome=c;lastTop=top;} }
  // walker-thread state
  final Motion motion=new Motion(); List<Tracked> tracked=new ArrayList<>(); String screenId=""; volatile int gen=0;
  int cutTop=0,cutBottom=Integer.MAX_VALUE; List<Box> bands=new ArrayList<>(); Box winBox=new Box(0,0,100000,100000);
  boolean moving=false, trackMode=false, loopOn=false; long lastMove=0, lastFull=0; double walkMs=0;
  volatile String curPkg=null;

  @Override protected void onServiceConnected(){inst=this;wm=(WindowManager)getSystemService(WINDOW_SERVICE); dp=getResources().getDisplayMetrics().density;
    whitelist=Prefs.apps(this); Prefs.p(this).registerOnSharedPreferenceChangeListener(pl);
    int sid=getResources().getIdentifier("status_bar_height","dimen","android"); if(sid>0)statusH=getResources().getDimensionPixelSize(sid);
    wt=new HandlerThread("walker");wt.start();wh=new Handler(wt.getLooper());
    if(store==null)wh.post(()->{store=new Store(getApplicationContext());fullRead();}); onToggle();}
  @Override public boolean onUnbind(android.content.Intent i){inst=null;removeOverlay();if(wt!=null)wt.quitSafely();return super.onUnbind(i);}
  @Override public void onInterrupt(){}
  void onToggle(){ if(Prefs.on(this)){startFg();ensureOverlay();requestWalk();} else {clearNow();removeOverlay();stopForeground(true);} }
  void startFg(){NotificationManager nm=getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("st","屏幕译",NotificationManager.IMPORTANCE_LOW));
    Notification n=new Notification.Builder(this,"st").setContentTitle("屏幕译：中文翻译已开启").setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();
    try{ if(Build.VERSION.SDK_INT>=34) startForeground(1,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); else startForeground(1,n);}catch(Exception e){nm.notify(1,n);} }
  void ensureOverlay(){ if(ov!=null)return; ov=new OverlayView(this);
    WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
    lp.gravity=Gravity.TOP|Gravity.START; if(Build.VERSION.SDK_INT>=28)lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    try{wm.addView(ov,lp);}catch(Exception e){ov=null;} }
  void removeOverlay(){ if(ov!=null){try{wm.removeView(ov);}catch(Exception e){} ov=null;} }
  void clearNow(){ gen++; if(ov!=null)ov.set(Collections.emptyList()); if(wh!=null)wh.post(()->{tracked=new ArrayList<>();}); }

  @Override public void onAccessibilityEvent(AccessibilityEvent e){
    if(!Prefs.on(this)||e.getPackageName()==null)return; String pkg=e.getPackageName().toString();
    if(pkg.equals("com.android.systemui"))return;
    if(!Filters.allowedPackage(pkg,whitelist,getPackageName())){ if(!pkg.equals(curPkg)){clearNow();curPkg=pkg;} return; }
    ensureOverlay(); int type=e.getEventType();
    if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||!pkg.equals(curPkg)){ curPkg=pkg; clearNow(); requestWalk(); return; }
    if(type==AccessibilityEvent.TYPE_VIEW_SCROLLED&&!trackMode&&ov!=null) ov.hideList(); // instant, same frame; chrome stays
    wh.post(this::startLoop); }
  void requestWalk(){ if(wh!=null){wh.removeCallbacks(fullR); wh.post(fullR);} }
  final Runnable fullR=this::fullRead;

  // ---------- motion loop (walker thread) ----------
  final Runnable tick=this::tick;
  void startLoop(){ if(loopOn)return; loopOn=true; lastMove=SystemClock.uptimeMillis(); wh.post(tick); }
  void tick(){ long now=SystemClock.uptimeMillis();
    boolean live=sampleMoved(); if(live){ lastMove=now; if(!trackMode){trackMode=true;Log.d(TAG,"mode=TRACK (refresh gives live bounds)");} }
    if(moving||live){ if(trackMode)trackFrame(); }
    if(now-lastFull>=100){ boolean mv=fullRead(); if(mv)lastMove=now; }
    if(now-lastMove>100){ loopOn=false; if(moving){moving=false;fullRead();} return; }
    wh.postDelayed(tick,16); }
  /** refresh() 3 sample list nodes; true if any Y changed (live bounds). */
  boolean sampleMoved(){ int n=0; boolean ch=false; Rect r=new Rect();
    for(Tracked t:tracked){ if(t.chrome)continue; if(n++>=3)break; if(!t.n.refresh())continue; t.n.getBoundsInScreen(r); if(r.top!=t.lastTop){ch=true;t.lastTop=r.top;} }
    if(ch)moving=true; return ch; }
  void trackFrame(){ Rect r=new Rect(); List<Tracked> keep=new ArrayList<>(); for(Tracked t:tracked){ if(t.chrome||t.n.refresh())keep.add(t);} tracked=keep; publish(); }

  // ---------- full read ----------
  /** Returns true if list content moved vs previous read. */
  boolean fullRead(){ lastFull=SystemClock.uptimeMillis(); if(!Prefs.on(this)||store==null)return false; final int g=gen; long t0=System.nanoTime();
    AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return false; CharSequence rp=root.getPackageName(); if(rp==null)return false; String pkg=rp.toString();
    if(!Filters.allowedPackage(pkg,whitelist,getPackageName())){h.post(this::clearNow);return false;}
    Rect wr=new Rect(); AccessibilityWindowInfo w=root.getWindow(); if(w!=null)w.getBoundsInScreen(wr); else root.getBoundsInScreen(wr);
    String sid=root.getWindowId()+"/"+root.getClassName()+"/"+pkg; if(!sid.equals(screenId)){screenId=sid;motion.reset();trackMode=false;}
    Box win=new Box(wr.left,wr.top,wr.right,wr.bottom); int topInset=wr.top<statusH?statusH:wr.top; winBox=new Box(wr.left,topInset,wr.right,wr.bottom);
    List<Item> items=new ArrayList<>(); List<AccessibilityNodeInfo> nodes=new ArrayList<>(); List<String> off=new ArrayList<>(); List<Box> chipLike=new ArrayList<>();
    collect(root,items,nodes,0,win,off,chipLike);
    Map<String,Box> keyed=new HashMap<>(); for(Item it:items)keyed.put(Motion.key(it.text,it.box),it.box);
    boolean mv=motion.update(keyed); if(mv&&loopOn)moving=true;
    int[] cu=Motion.cuts(win,motion.chrome.values(),chipLike,topInset); cutTop=cu[0]; cutBottom=cu[1];
    bands=Motion.bands(win,motion.chrome.values(),(int)(4*dp));
    List<Item> dd=Filters.dedupe(items); Set<Item> keepSet=Collections.newSetFromMap(new IdentityHashMap<>()); keepSet.addAll(dd);
    List<Tracked> ts=new ArrayList<>(); List<String> need=new ArrayList<>(); long now=SystemClock.uptimeMillis();
    for(int i=0;i<items.size();i++){ Item it=items.get(i); if(!keepSet.contains(it))continue; String zh=store.tc.get(it.text);
      if(zh==null){ String k=TransCache.normalize(it.text); Long f=failed.get(k); if(f==null||now-f>15000)need.add(k); continue; }
      if(zh.trim().isEmpty()||zh.equals(it.text))continue; String k=Motion.key(it.text,it.box);
      ts.add(new Tracked(nodes.get(i),it.text,zh,k,motion.isChrome(k),it.box.t)); }
    tracked=ts;
    List<String> pre=new ArrayList<>(); for(String o:off)if(store.tc.get(o)==null)pre.add(TransCache.normalize(o));
    walkMs=walkMs*0.7+(System.nanoTime()-t0)/1e6*0.3;
    if(!(moving&&loopOn&&!trackMode))publish(); else h.post(()->{ if(g==gen&&ov!=null)ov.hideList(); });
    h.post(()->{ if(g!=gen)return; boolean nu=false; for(String k:need)if(pending.add(k))nu=true;
      for(String k:pre)if(!pending.contains(k)&&!failed.containsKey(k)&&prePending.size()<120&&prePending.add(k))nu=true;
      if(nu){h.removeCallbacks(fetchR);h.postDelayed(fetchR,150);} });
    return mv; }
  /** Build pills from tracked nodes with current bounds and post to the overlay. */
  void publish(){ final int g=gen; List<OverlayView.Pill> out=new ArrayList<>(); Rect r=new Rect();
    List<Box> boxes=new ArrayList<>(), covers=new ArrayList<>(); int px=Math.round(3*dp), py=Math.round(1*dp);
    for(Tracked t:tracked){ t.n.getBoundsInScreen(r); Box nb=new Box(r.left,r.top,r.right,r.bottom); Box c;
      if(t.chrome) c=Filters.intersect(nb,winBox); else { c=Motion.listPill(nb,cutTop,cutBottom,bands,winBox); }
      if(c==null)continue; OverlayView.Pill p=new OverlayView.Pill(t.zh,t.text,!t.chrome); p.node.set(r); p.clip.set(c.l,c.t,c.r,c.b); out.add(p);
      boxes.add(Filters.coverBox(nb,c,winBox,px,py)); covers.add(c); }
    Filters.trimOverlaps(boxes,covers);
    for(int i=0;i<out.size();i++){ Box b=boxes.get(i); out.get(i).box.set(b.l,b.t,b.r,b.b); }
    final boolean dbg=Prefs.debug(this); final List<Rect> dr=new ArrayList<>();
    if(dbg){ for(Box b:bands)dr.add(new Rect(b.l,b.t,b.r,b.b)); dr.add(new Rect(winBox.l,cutTop,winBox.r,cutTop+1)); if(cutBottom<winBox.b)dr.add(new Rect(winBox.l,cutBottom,winBox.r,cutBottom+1)); }
    final String dt=dbg?String.format(Locale.ROOT,"%s | read %.0fms | chrome %d | cut %d-%d",trackMode?"track":"snap",walkMs,motion.chrome.size(),cutTop,cutBottom):null;
    h.post(()->{ if(g!=gen||ov==null)return; ov.debugChrome=dr; ov.debugText=dt; ov.set(out); }); }
  void collect(AccessibilityNodeInfo n,List<Item> out,List<AccessibilityNodeInfo> nodes,int depth,Box win,List<String> off,List<Box> chipLike){
    if(n==null||depth>60||out.size()>300)return;
    boolean vis=n.isVisibleToUser(); CharSequence t=n.getText(); boolean hasText=t!=null&&t.toString().trim().length()>0;
    Rect r=new Rect(); n.getBoundsInScreen(r); Box nb=new Box(r.left,r.top,r.right,r.bottom);
    if(vis&&nb.t<win.t+0.3*win.h()&&nb.h()<0.12*win.h()&&((n.isScrollable()&&nb.w()>3*nb.h())||n.isHeading()||n.isSelected()||n.isCheckable()||(n.isClickable()&&nb.t<win.t+0.15*win.h())))chipLike.add(nb);
    if(Filters.shouldDraw(vis,hasText)){ String s=t.toString().trim(); if(s.length()>500)s=s.substring(0,500);
      if(Filters.want(s)&&Filters.intersect(nb,win)!=null){ out.add(new Item(s,nb,false,"")); nodes.add(n); } }
    else if(off.size()<150){ CharSequence o=hasText?t:n.getContentDescription(); if(o!=null){String s=o.toString().trim(); if(Filters.want(s))off.add(s.length()>500?s.substring(0,500):s);} }
    for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),out,nodes,depth+1,win,off,chipLike); }

  // ---------- translation fetch (main thread schedules, net thread calls) ----------
  final Runnable fetchR=this::fetch;
  void fetch(){ if(busy){h.removeCallbacks(fetchR);h.postDelayed(fetchR,150);return;}
    final List<String> todo=new ArrayList<>(pending); pending.clear(); for(String p:prePending)if(!todo.contains(p))todo.add(p); prePending.clear();
    if(todo.isEmpty()||store==null)return; busy=true; final String key=Prefs.key(this); final Store st=store;
    net.execute(()->{ String err=null; List<String> failedNow=new ArrayList<>();
      if(key==null||key.isEmpty()){err="未设置 API Key";failedNow.addAll(todo);} else {
        List<List<String>> batches=new ArrayList<>(); int first=Math.min(10,todo.size()); batches.add(todo.subList(0,first));
        for(int s=first;s<todo.size();s+=40)batches.add(todo.subList(s,Math.min(todo.size(),s+40)));
        for(int bi=0;bi<batches.size();bi++){ List<String> b=batches.get(bi);
          try{ List<String> t=DeepSeek.translate(key,b,st.tc.promptGlossary(b)); Map<String,String> got=new LinkedHashMap<>();
            for(int k=0;k<b.size();k++){st.tc.put(b.get(k),t.get(k)); got.put(b.get(k),st.tc.get(b.get(k)));}
            st.save(got); if(bi==0)requestWalk(); }
          catch(Exception x){err=x.getClass().getSimpleName()+(x.getMessage()!=null?": "+x.getMessage().replace(key,"***"):""); for(int r=bi;r<batches.size();r++)failedNow.addAll(batches.get(r)); break;} } }
      final String fe=err; h.post(()->{busy=false; long now=SystemClock.uptimeMillis(); for(String f:failedNow)failed.put(f,now);
        if(fe!=null)Toast.makeText(this,"翻译失败: "+fe,Toast.LENGTH_SHORT).show(); requestWalk(); if(!pending.isEmpty()||!prePending.isEmpty())h.postDelayed(fetchR,150);});}); }
}
