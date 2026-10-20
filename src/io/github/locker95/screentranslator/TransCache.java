package io.github.locker95.screentranslator;
import java.util.*; import java.util.concurrent.ConcurrentSkipListMap;
/** Pure-Java translation cache: normalization, glossary overrides, ellipsis/prefix lookup. */
public class TransCache {
  public final ConcurrentSkipListMap<String,String> map=new ConcurrentSkipListMap<>();
  public final Map<String,String> terms=new HashMap<>(); // lowercase ru -> zh (hard overrides)
  public final List<String[]> fix=new ArrayList<>();     // {ruStem, badZh, goodZh}
  public static String normalize(String s){ if(s==null)return ""; s=s.replaceAll("[\\s\\u00A0]+"," ").trim();
    while(s.endsWith("…")||s.endsWith("...")){ s=s.endsWith("…")?s.substring(0,s.length()-1):s.substring(0,s.length()-3); s=s.trim(); }
    return s; }
  public static boolean ellipsized(String s){ s=s.trim(); return s.endsWith("…")||s.endsWith("..."); }
  /** Apply glossary to a model output for source text. */
  public String applyGlossary(String src,String zh){ String l=normalize(src).toLowerCase(Locale.ROOT);
    String t=terms.get(l); if(t!=null)return t;
    if(zh==null)return null; for(String[] f:fix) if(l.contains(f[0])) zh=zh.replace(f[1],f[2]); return zh; }
  public void put(String src,String zh){ String k=normalize(src); if(k.isEmpty()||zh==null)return; map.put(k,applyGlossary(k,zh)); }
  /** Lookup with exact/override/prefix-of-longer-string fallback. */
  public String get(String raw){ String k=normalize(raw); if(k.isEmpty())return null;
    String v=map.get(k); if(v!=null)return v;
    String t=terms.get(k.toLowerCase(Locale.ROOT)); if(t!=null)return t;
    if(ellipsized(raw)&&k.length()>=8){ String c=map.ceilingKey(k);
      if(c!=null&&c.startsWith(k)){ String full=map.get(c); if(full==null)return null;
        int n=Math.max(1,Math.min(full.length(),(int)Math.ceil(full.length()*(double)k.length()/c.length())+1));
        return n>=full.length()?full:full.substring(0,n)+"…"; } }
    return null; }
  /** Glossary lines relevant to a batch, for the system prompt. */
  public String promptGlossary(List<String> batch){ String low=String.join(" ",batch).toLowerCase(Locale.ROOT); StringBuilder sb=new StringBuilder();
    for(Map.Entry<String,String> e:terms.entrySet()) if(e.getKey().length()>=3&&low.contains(e.getKey())) sb.append(e.getKey()).append('=').append(e.getValue()).append("; ");
    return sb.length()==0?"":"\nMandatory glossary (ru=zh): "+sb; }
}
