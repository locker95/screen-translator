import io.github.locker95.screentranslator.TransCache; import java.util.*;
public class TransCacheTest { static int fail=0; static void ck(boolean c,String m){if(!c){fail++;System.out.println("FAIL "+m);}}
 public static void main(String[] a){
  ck(TransCache.normalize("  Сочный   бургер\u00A0с сыром… ").equals("Сочный бургер с сыром"),"norm1");
  ck(TransCache.normalize("Корзина...").equals("Корзина"),"norm2");
  TransCache t=new TransCache();
  t.terms.put("сандерс баскет острый","桑德斯香辣桶"); t.terms.put("пати баскет","派对桶");
  t.fix.add(new String[]{"баскет","篮球","桶"}); t.fix.add(new String[]{"острый","辣味","香辣"});
  t.put("Сандерс баскет острый","桑德斯辣味篮球"); t.put("Пати баскет","派对篮球"); t.put("Баскет дуэт","双人篮球");
  ck("桑德斯香辣桶".equals(t.get("Сандерс баскет острый")),"kfc1 "+t.get("Сандерс баскет острый"));
  ck("派对桶".equals(t.get(" Пати  баскет ")),"kfc2");
  ck("双人桶".equals(t.get("Баскет дуэт")),"fix "+t.get("Баскет дуэт"));
  t.put("Горчица, кетчуп, оригинальная куриная котлета, огурцы","芥末酱、番茄酱、原味鸡排、黄瓜");
  String p=t.get("Горчица, кетчуп, оригинальная…"); ck(p!=null&&p.endsWith("…")&&p.startsWith("芥末酱"),"prefix "+p);
  ck(t.get("Горчица, кетчуп, оригинальная")==null,"no prefix without ellipsis");
  ck(t.promptGlossary(Arrays.asList("Пати баскет")).contains("派对桶"),"prompt gl");
  System.out.println(fail==0?"ALL PASS":fail+" failures"); System.exit(fail);}}
