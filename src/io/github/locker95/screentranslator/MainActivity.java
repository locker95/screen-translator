package io.github.locker95.screentranslator;
import android.app.Activity; import android.content.Intent; import android.net.Uri; import android.os.Bundle;
import android.provider.Settings; import android.widget.*; import android.text.InputType; import android.view.View;
public class MainActivity extends Activity {
  TextView status; Button appsBtn; Switch sw; EditText key;
  @Override protected void onCreate(Bundle b){super.onCreate(b);
    LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(48,64,48,48);
    TextView t=new TextView(this);t.setText("屏幕译 · DeepSeek 实时中文翻译");t.setTextSize(20);l.addView(t);
    sw=new Switch(this);sw.setText("中文翻译 开/关");sw.setTextSize(18);sw.setChecked(Prefs.on(this));
    sw.setOnCheckedChangeListener((v,c)->{Prefs.setOn(this,c);if(TranslatorService.inst!=null)TranslatorService.inst.onToggle();refresh();});l.addView(sw);
    key=new EditText(this);key.setHint("DeepSeek API Key");key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setText(Prefs.key(this));l.addView(key);
    Button save=new Button(this);save.setText("保存 Key");save.setOnClickListener(v->{Prefs.setKey(this,key.getText().toString().trim());Toast.makeText(this,"已保存",0).show();});l.addView(save);
    Switch dbg=new Switch(this);dbg.setText("显示调试信息");dbg.setChecked(Prefs.debug(this));dbg.setOnCheckedChangeListener((v,c)->Prefs.setDebug(this,c));l.addView(dbg);
    appsBtn=new Button(this);appsBtn.setOnClickListener(v->startActivity(new Intent(this,AppPickerActivity.class)));l.addView(appsBtn);
    Button a=new Button(this);a.setText("打开无障碍设置（启用“屏幕译 翻译服务”）");a.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));l.addView(a);
    Button o=new Button(this);o.setText("授予悬浮窗权限（可选）");o.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));l.addView(o);
    if(android.os.Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
    status=new TextView(this);status.setPadding(0,32,0,0);l.addView(status);
    ScrollView s=new ScrollView(this);s.addView(l);setContentView(s);}
  @Override protected void onResume(){super.onResume();refresh();}
  void refresh(){appsBtn.setText("选择要翻译的 App（已选 "+Prefs.apps(this).size()+" 个）");status.setText("无障碍服务: "+(TranslatorService.inst!=null?"已启用":"未启用")+"\n悬浮窗权限: "+(Settings.canDrawOverlays(this)?"已授予":"未授予（无障碍悬浮层通常不需要）")
    +"\n翻译: "+(Prefs.on(this)?"开":"关")
    +"\n\n用法: 打开「中文翻译 开/关」后，切到已选的 App。中文会叠在原文上并跟随滚动；词典或缓存里有的文字立即显示，新文字等 DeepSeek 返回后出现。"
    +"\n中文层不拦截点击，可以直接点按原 App 的按钮；不用时回来关掉开关即可。");}
}
