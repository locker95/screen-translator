package io.github.locker95.screentranslator;
import android.app.Activity; import android.content.Intent; import android.content.pm.*; import android.graphics.drawable.Drawable; import android.os.*;
import android.text.*; import android.view.*; import android.widget.*; import java.util.*;
public class AppPickerActivity extends Activity {
  static final class App { String pkg,name; Drawable icon; App(String p,String n,Drawable i){pkg=p;name=n;icon=i;} }
  final List<App> all=new ArrayList<>(), shown=new ArrayList<>(); Set<String> sel; BaseAdapter ad; String q=""; TextView count;
  @Override protected void onCreate(Bundle b){ super.onCreate(b); sel=Prefs.apps(this);
    LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(24,48,24,0);
    count=new TextView(this); count.setTextSize(16); l.addView(count);
    EditText f=new EditText(this); f.setHint("搜索 App 名称或包名"); f.setSingleLine(); l.addView(f);
    ListView lv=new ListView(this); l.addView(lv,new LinearLayout.LayoutParams(-1,-1)); setContentView(l);
    final int px=(int)(40*getResources().getDisplayMetrics().density);
    ad=new BaseAdapter(){ public int getCount(){return shown.size();} public Object getItem(int i){return shown.get(i);} public long getItemId(int i){return i;}
      public View getView(int i,View v,ViewGroup pa){ LinearLayout row; ImageView iv; TextView tv; CheckBox cb;
        if(v==null){ row=new LinearLayout(AppPickerActivity.this); row.setPadding(8,16,8,16); row.setGravity(Gravity.CENTER_VERTICAL);
          iv=new ImageView(AppPickerActivity.this); row.addView(iv,new LinearLayout.LayoutParams(px,px));
          tv=new TextView(AppPickerActivity.this); tv.setPadding(24,0,8,0); row.addView(tv,new LinearLayout.LayoutParams(0,-2,1));
          cb=new CheckBox(AppPickerActivity.this); cb.setClickable(false); cb.setFocusable(false); row.addView(cb); }
        else { row=(LinearLayout)v; iv=(ImageView)row.getChildAt(0); tv=(TextView)row.getChildAt(1); cb=(CheckBox)row.getChildAt(2); }
        App a=shown.get(i); iv.setImageDrawable(a.icon); tv.setText(a.name+"\n"+a.pkg); cb.setChecked(sel.contains(a.pkg)); return row; } };
    lv.setAdapter(ad);
    lv.setOnItemClickListener((p,v,i,id)->{ String pk=shown.get(i).pkg; if(!sel.remove(pk))sel.add(pk); Prefs.setApps(this,sel); ad.notifyDataSetChanged(); upd(); });
    f.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int b2,int c){} public void onTextChanged(CharSequence s,int a,int b2,int c){}
      public void afterTextChanged(Editable e){ q=e.toString().trim().toLowerCase(Locale.ROOT); filter(); } });
    upd();
    new Thread(()->{ PackageManager pm=getPackageManager(); Intent it=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
      Map<String,App> m=new LinkedHashMap<>();
      for(ResolveInfo r:pm.queryIntentActivities(it,0)){ String pk=r.activityInfo.packageName; if(pk.equals(getPackageName())||m.containsKey(pk))continue;
        m.put(pk,new App(pk,String.valueOf(r.loadLabel(pm)),r.loadIcon(pm))); }
      runOnUiThread(()->{ all.clear(); all.addAll(m.values()); sortAll(); filter(); }); }).start(); }
  void sortAll(){ Collections.sort(all,(x,y)->{ boolean a=sel.contains(x.pkg),b=sel.contains(y.pkg); if(a!=b)return a?-1:1; return x.name.compareToIgnoreCase(y.name);}); }
  void filter(){ shown.clear(); for(App a:all) if(q.isEmpty()||a.name.toLowerCase(Locale.ROOT).contains(q)||a.pkg.toLowerCase(Locale.ROOT).contains(q))shown.add(a); ad.notifyDataSetChanged(); }
  void upd(){ count.setText("已选 "+sel.size()+" 个 App（勾选后立即生效）"); }
  @Override protected void onResume(){super.onResume(); sortAll(); filter();}
}
