import json,os,urllib.request,concurrent.futures as cf
# 原始俄语文本不在仓库里：放在 DATA_DIR（默认 ../screen-translator-private）
D=os.environ.get('DATA_DIR',os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','screen-translator-private'))
K=os.environ["DEEPSEEK_API_KEY"]; src=json.load(open(os.path.join(D,'ru_all.json')))
G=json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)),'assets','glossary_ru_zh.json')))
SYS=('You translate Russian UI strings of a Tajik food-delivery app (Milli Eats) to Simplified Chinese. Return ONLY JSON {"translations":[...]} with exactly {N} strings (json) in order. '
 'UI terms short and natural (buttons 2-4 chars where possible). Keep brand names (Milli Eats, ZoodFood, KFC) as-is. Keep numbers, prices, placeholders like %s %d %.2f {0}, and date-format patterns exactly. '
 'Context: Tajikistan food-delivery app Milli Eats. Translate dish names the way Chinese KFC/McDonald\'s menus and Chinese delivery apps (美团/饿了么) name them; never use literal sports or other non-food meanings (баскет = 桶 bucket, NOT 篮球). Brand/person names: transliterate or keep. '
 'Currency "с", "сом", "сомони", "TJS" is Tajik somoni: keep "с" unchanged — NEVER translate it as seconds. "мин" means minutes (分钟). Food item names: translate meaningfully (e.g. Чизбургер=芝士汉堡, Твистеры=卷饼). No commentary.')
def gl(b):
    low=' '.join(b).lower(); m=[f"{k}={v}" for k,v in G['terms'].items() if k in low]
    return ('\nMandatory glossary (ru=zh): '+'; '.join(m)) if m else ''
def post(src,zh):
    l=src.lower().strip()
    if l in G['terms']: return G['terms'][l]
    for st,bad,good in G['fix']:
        if st in l: zh=zh.replace(bad,good)
    return zh
def call(b):
    body={"model":"deepseek-chat","temperature":0.1,"response_format":{"type":"json_object"},
      "messages":[{"role":"system","content":SYS.replace("{N}",str(len(b)))+gl(b)},{"role":"user","content":"\n".join(f"{i+1}. {s.replace(chr(10),' ')}" for i,s in enumerate(b))}]}
    for _ in range(3):
        try:
            r=urllib.request.urlopen(urllib.request.Request("https://api.deepseek.com/chat/completions",json.dumps(body).encode(),{"Authorization":"Bearer "+K,"Content-Type":"application/json"}),timeout=120)
            t=json.loads(json.loads(r.read())["choices"][0]["message"]["content"])["translations"]
            if len(t)==len(b): return {x:post(x,y) for x,y in zip(b,t)}
        except urllib.error.HTTPError as e: print("retry",e.code,e.read()[:200].decode().replace(K,"***"))
        except Exception as e: print("retry",type(e).__name__)
    return {}
bs=[src[i:i+40] for i in range(0,len(src),40)]; out={}
with cf.ThreadPoolExecutor(6) as ex:
    for d in ex.map(call,bs): out.update(d)
print(len(out),"/",len(src)); json.dump(out,open(os.path.join(D,'dict_raw.json'),'w'),ensure_ascii=False,indent=0)
