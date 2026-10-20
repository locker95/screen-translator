import io.github.locker95.screentranslator.Filters; import io.github.locker95.screentranslator.Filters.*; import java.util.*;
public class FiltersTest { static int fail=0; static void ck(boolean c,String m){if(!c){fail++;System.out.println("FAIL "+m);}}
 public static void main(String[] a){
  for(String s:new String[]{"26 с","18 с","450 сомони","4.7","5.0","15:23","12 TJS","от 30 с","1 200 ₽","★ 4.7","26с","","   ","汉堡","...","10"}) ck(!Filters.want(s),"skip "+s);
  for(String s:new String[]{"Чизбургер","Лонгер","Нужна помощь?","Корзина","KFC Бургеры","2 сырных соуса","Итого: 450 с за заказ"}) ck(Filters.want(s),"want "+s);
  List<Item> in=new ArrayList<>();
  in.add(new Item("Еда",new Box(0,900,100,940),true,"icon"));   // icon desc above
  in.add(new Item("Еда",new Box(0,945,100,975),false,"label"));
  in.add(new Item("Корзина",new Box(100,880,200,980),true,"parent"));
  in.add(new Item("Корзина",new Box(110,945,190,975),false,"child"));
  in.add(new Item("Корзина",new Box(110,100,190,130),false,"far")); // different place, keep
  List<Item> o=Filters.dedupe(in); Set<String> ids=new HashSet<>(); for(Item i:o)ids.add(i.id);
  ck(ids.equals(new HashSet<>(Arrays.asList("label","child","far"))),"dedupe "+ids);
  ck(Filters.clip(new Box(0,-50,100,10),new Box(0,0,1080,2000))==null,"clip offscreen");
  ck(Filters.clip(new Box(0,-5,100,95),new Box(0,0,1080,2000)).t==0,"clip partial");
  System.out.println(fail==0?"ALL PASS":fail+" failures"); System.exit(fail);}}
