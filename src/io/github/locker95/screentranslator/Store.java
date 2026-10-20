package io.github.locker95.screentranslator;
import android.content.Context; import java.io.*; import java.util.*; import org.json.*;
/** Loads bundled glossary + dictionary and the on-disk cache; appends new translations. */
public class Store {
  static final int CAP=20000; static final String PROMPT_VER="p3";
  final TransCache tc=new TransCache(); File file; int lines=0; final Object lock=new Object();
  Store(Context c){
    int gv=0;
    try{ JSONObject g=new JSONObject(asset(c,"glossary_ru_zh.json")); gv=g.optInt("version");
      JSONObject t=g.getJSONObject("terms"); for(Iterator<String> it=t.keys();it.hasNext();){String k=it.next();tc.terms.put(TransCache.normalize(k).toLowerCase(Locale.ROOT),t.getString(k));}
      JSONArray f=g.getJSONArray("fix"); for(int i=0;i<f.length();i++){JSONArray a=f.getJSONArray(i);tc.fix.add(new String[]{a.getString(0),a.getString(1),a.getString(2)});}
    }catch(Exception e){}
    try{ JSONObject d=new JSONObject(asset(c,"dict_ru_zh.json")); for(Iterator<String> it=d.keys();it.hasNext();){String k=it.next();tc.put(k,d.getString(k));} }catch(Exception e){}
    String ver="g"+gv+PROMPT_VER; file=new File(c.getFilesDir(),"cache_"+ver+".tsv");
    File[] old=c.getFilesDir().listFiles((dir,n)->n.startsWith("cache_")&&!n.equals(file.getName())); if(old!=null)for(File o:old)o.delete();
    List<String[]> rows=new ArrayList<>();
    if(file.exists()) try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){String l;
      while((l=br.readLine())!=null){int i=l.indexOf('\t'); if(i>0)rows.add(new String[]{un(l.substring(0,i)),un(l.substring(i+1))});}}catch(Exception e){}
    for(String[] r:rows)tc.put(r[0],r[1]); lines=rows.size();
    if(lines>CAP){ LinkedHashMap<String,String> keep=new LinkedHashMap<>(); for(int i=rows.size()-CAP/2;i<rows.size();i++)keep.put(rows.get(i)[0],rows.get(i)[1]);
      try(Writer w=new OutputStreamWriter(new FileOutputStream(file,false),"UTF-8")){for(Map.Entry<String,String> e:keep.entrySet())w.write(es(e.getKey())+"\t"+es(e.getValue())+"\n");}catch(Exception e){}
      lines=keep.size(); } }
  void save(Map<String,String> m){ synchronized(lock){ try(Writer w=new OutputStreamWriter(new FileOutputStream(file,true),"UTF-8")){
      for(Map.Entry<String,String> e:m.entrySet()){w.write(es(TransCache.normalize(e.getKey()))+"\t"+es(e.getValue())+"\n");lines++;} }catch(Exception e){} } }
  static String es(String s){return s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n");}
  static String un(String s){StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\\'&&i+1<s.length()){char n=s.charAt(++i);b.append(n=='t'?'\t':n=='n'?'\n':n);}else b.append(c);}return b.toString();}
  static String asset(Context c,String n) throws IOException{try(InputStream in=c.getAssets().open(n)){return DeepSeek.read(in);}}
}
