package io.github.locker95.screentranslator;
import android.content.Context; import android.graphics.*; import android.text.*; import android.view.View; import java.util.*;
public class OverlayView extends View {
  public static final class Pill { final String zh,orig; final boolean list; final Rect node=new Rect(), clip=new Rect(), box=new Rect(); /* box = drawn rect (covers source + padding) */ Pill(String z,String o,boolean l){zh=z;orig=o;list=l;} }
  volatile List<Pill> pills=Collections.emptyList(); volatile List<Rect> debugChrome=Collections.emptyList(); volatile String debugText=null;
  final Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG), red=new Paint(), green=new Paint(), dbgBg=new Paint(); final TextPaint tp=new TextPaint(Paint.ANTI_ALIAS_FLAG), dtp=new TextPaint(Paint.ANTI_ALIAS_FLAG);
  final int[] loc=new int[2]; final float dp; final Map<String,StaticLayout> layouts=new HashMap<>();
  public OverlayView(Context c){super(c);dp=c.getResources().getDisplayMetrics().density;bg.setColor(0xF0202020);tp.setColor(Color.WHITE);
    red.setColor(0xFFFF3030);red.setStyle(Paint.Style.STROKE);red.setStrokeWidth(dp); green.setColor(0xFF30E030);green.setStyle(Paint.Style.STROKE);green.setStrokeWidth(dp);
    dbgBg.setColor(0xC0000000); dtp.setColor(Color.YELLOW); dtp.setTextSize(11*dp);}
  void set(List<Pill> p){pills=p;invalidate();}
  /** Hide list pills only (static chrome pills stay). */
  void hideList(){ List<Pill> o=new ArrayList<>(); for(Pill p:pills)if(!p.list)o.add(p); pills=o; invalidate(); }
  @Override protected void onDraw(Canvas c){ getLocationOnScreen(loc); int ox=loc[0], oy=loc[1];
    for(Pill p:pills){ Rect r=p.box; if(r.width()<4||r.height()<4)continue;
      String k=p.zh+"|"+r.width()+"x"+r.height(); StaticLayout sl=layouts.get(k);
      if(sl==null){sl=fit(p.zh,r.width()-(int)(2*dp),r.height()); if(layouts.size()>600)layouts.clear(); layouts.put(k,sl);}
      c.save(); c.clipRect(r.left-ox,r.top-oy,r.right-ox,r.bottom-oy); // box already clipped to cuts/screen
      c.drawRect(r.left-ox,r.top-oy,r.right-ox,r.bottom-oy,bg);
      boolean multi=sl.getLineCount()>1; c.translate(r.left-ox+1*dp,r.top-oy+(multi?0:Math.max(0,(r.height()-sl.getHeight())/2f))); sl.draw(c); c.restore(); }
    if(debugText!=null){ for(Rect r:debugChrome)c.drawRect(r.left-ox,r.top-oy,r.right-ox,r.bottom-oy,red);
      for(Pill p:pills)if(p.list)c.drawRect(p.box.left-ox,p.box.top-oy,p.box.right-ox,p.box.bottom-oy,green);
      float w=dtp.measureText(debugText); c.drawRect(8*dp,getHeight()/2f-14*dp,12*dp+w,getHeight()/2f+4*dp,dbgBg); c.drawText(debugText,10*dp,getHeight()/2f,dtp); } }
  /** Shrink font (min 9sp) then truncate with … so text never exceeds node height. */
  StaticLayout fit(String s,int w,int h){ float sp=getResources().getDisplayMetrics().scaledDensity; float size=Math.min(16*dp,Math.max(9*sp,h*0.75f)); StaticLayout sl;
    while(true){ tp.setTextSize(size); int lines=Filters.maxLines(h,size*1.17f);
      sl=StaticLayout.Builder.obtain(s,0,s.length(),new TextPaint(tp),Math.max(1,w)).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setLineSpacing(0,1f).build();
      boolean ell=sl.getLineCount()>0&&sl.getEllipsisCount(sl.getLineCount()-1)>0;
      if((!ell&&sl.getHeight()<=h)||size<=9*sp)return sl; size-=1*dp; } }
}
