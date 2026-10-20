package io.github.locker95.screentranslator;
import android.content.Context; import android.content.SharedPreferences;
public class Prefs {
  static SharedPreferences p(Context c){return c.getSharedPreferences("st",0);}
  static String key(Context c){String k=p(c).getString("key",null);return (k==null||k.isEmpty())?BuildConfig.KEY:k;}
  static void setKey(Context c,String k){p(c).edit().putString("key",k).apply();}
  static final java.util.Set<String> DEFAULT_APPS=java.util.Collections.singleton("com.zood.mobile");
  static java.util.Set<String> apps(Context c){return new java.util.HashSet<>(p(c).getStringSet("apps",DEFAULT_APPS));}
  static void setApps(Context c,java.util.Set<String> s){p(c).edit().putStringSet("apps",new java.util.HashSet<>(s)).apply();}
  static boolean debug(Context c){return p(c).getBoolean("debug",false);}
  static void setDebug(Context c,boolean b){p(c).edit().putBoolean("debug",b).apply();}
  static boolean on(Context c){return p(c).getBoolean("on",false);}
  static void setOn(Context c,boolean b){p(c).edit().putBoolean("on",b).apply();}
}
