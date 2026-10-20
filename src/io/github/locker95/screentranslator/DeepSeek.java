package io.github.locker95.screentranslator;
import java.io.*; import java.net.*; import java.util.*; import org.json.*;
public class DeepSeek {
  static final String SYS="You translate Russian (or other) UI strings of a Tajikistan food-delivery app (Milli Eats) to Simplified Chinese. Return ONLY a json object {\"translations\":[...]} with exactly %d strings in the same order. "
   +"Translate dish names the way Chinese KFC/McDonald's menus and Chinese delivery apps (美团/饿了么) name them; never use literal sports or other non-food meanings (баскет = 桶 bucket, NOT 篮球). Brand and person names: transliterate or keep (Milli Eats, KFC). "
   +"UI terms short and natural. Keep numbers, prices, placeholders exactly. Currency \"с\", \"сом\", \"сомони\", \"TJS\" is Tajik somoni: keep \"с\" unchanged, NEVER translate it as seconds. \"мин\" = 分钟. Keep each translation similar length to the source. No commentary.";
  static List<String> translate(String key,List<String> src,String glossary) throws Exception {
    StringBuilder u=new StringBuilder();for(int i=0;i<src.size();i++)u.append(i+1).append(". ").append(src.get(i).replace('\n',' ')).append('\n');
    JSONObject body=new JSONObject();body.put("model","deepseek-chat");body.put("temperature",0.1);
    body.put("response_format",new JSONObject().put("type","json_object"));
    JSONArray msgs=new JSONArray();
    msgs.put(new JSONObject().put("role","system").put("content",String.format(Locale.ROOT,SYS,src.size())+glossary));
    msgs.put(new JSONObject().put("role","user").put("content",u.toString()));body.put("messages",msgs);
    HttpURLConnection c=(HttpURLConnection)new URL("https://api.deepseek.com/chat/completions").openConnection();
    c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(60000);c.setDoOutput(true);
    c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");
    try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}
    int code=c.getResponseCode();InputStream in=code<300?c.getInputStream():c.getErrorStream();
    String r=read(in);if(code>=300)throw new IOException("HTTP "+code);
    String content=new JSONObject(r).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    JSONArray arr=content.trim().startsWith("[")?new JSONArray(content):new JSONObject(content).getJSONArray("translations");
    List<String> out=new ArrayList<>();for(int i=0;i<src.size();i++)out.add(i<arr.length()?arr.optString(i,src.get(i)):src.get(i));return out;}
  static String read(InputStream in) throws IOException{if(in==null)return "";ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return b.toString("UTF-8");}
}
