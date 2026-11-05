package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.HorizontalScrollView;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV-first, read-only channel guide in the working Discover v2 application.
 *
 * Data belongs to its upstream provider, BrazilTVEPG (limaalef). No streams,
 * authorization bypass, or UniTVnet private activities are used here.
 * Files are read from a bounded disk cache, never fully in memory.
 */
public final class EpgActivity extends Activity {
    private static final String BASE="https://raw.githubusercontent.com/limaalef/BrazilTVEPG/main/";
    private static final String[] FEEDS={"globo.xml","maissbt.xml","epg.xml","claro.xml","plutotv.xml","xsports.xml"};
    private static final long TTL=4L*60*60*1000;
    private static final long MAX_XML=30L*1024*1024;
    private static final int BG=0xff101421, CARD=0xff1b2334, FOCUS=0xff324b76, WHITE=0xfff4f7ff, MUTED=0xffb5c1d4;
    private static final String LOGO_BASE="https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/brazil/";
    private final ExecutorService jobs=Executors.newFixedThreadPool(3);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,Channel> all=new LinkedHashMap<>();
    private final LinkedHashMap<String,Bitmap> logos=new LinkedHashMap<String,Bitmap>(48,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> entry) {return size()>32;}
    };
    private LinearLayout list, tabs;
    private ScrollView scroll;
    private TextView status;
    private String category="Todos", focusedKey=null;
    private boolean alive=true;
    private int visibleLimit=64;
    private static final int PAGE=64;
    private static final int SELECTED=0xff254879;
    private int viewGeneration=0, loadedFeeds=0, failedFeeds=0;
    private static final String[] CATEGORIES={
        "Todos","Abertos","Fechados","Esportes","Filmes","Infantil","Notícias","Fixados"
    };
    static final class Channel {
        String key,id,name,logo="",now="",next="";
        long start,end,nextAt=Long.MAX_VALUE;
        boolean open;
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(16);return d;
    }
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(WHITE);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private static String normalize(String value) {
        if(value==null)return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replace('-', ' ').replace('_',' ')
            .replaceAll("\\s+"," ").trim();
    }
    private static boolean isOpen(String s){
        String n=normalize(s);
        return n.equals("globo")||n.startsWith("tv globo")||n.equals("sbt")||n.startsWith("sbt ")
            ||n.equals("record")||n.startsWith("record ")||n.equals("band")
            ||n.startsWith("band ")||n.startsWith("rede tv")
            ||n.equals("tv brasil")||n.equals("cultura")||n.startsWith("tv cultura")
            ||n.equals("gazeta")||n.equals("tv aparecida")||n.startsWith("tv senado")
            ||n.startsWith("tv camara")||n.equals("futura");
    }
    private static boolean hidden(Channel c){
        String n=normalize(c.name);
        return n.contains("adult")||n.contains("sexprive")||n.contains("sexy hot")
            ||n.contains("playboy")||n.equals("private");
    }
    private static int rank(Channel c){
        String n=normalize(c.name);
        if(n.equals("globo")||n.equals("tv globo"))return 0;
        if(n.equals("sbt"))return 1;
        if(n.equals("record")||n.equals("record tv"))return 2;
        if(n.equals("band"))return 3;
        if(n.equals("rede tv"))return 4;
        if(n.equals("tv brasil"))return 5;
        if(n.equals("cultura"))return 6;
        return c.open?10:20;
    }
    private static long timestamp(String value){
        if(value==null||value.length()<14)return 0;
        try {
            String tz=(value.length()>=20)?value.substring(15,20):"+0000";
            SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);
            format.setLenient(false);
            return format.parse(value.substring(0,14)+" "+tz).getTime();
        }catch(Exception ignored){return 0;}
    }
    private static String time(long when){
        return new SimpleDateFormat("HH:mm",new Locale("pt","BR")).format(new Date(when));
    }
    private static String read(XmlPullParser parser) throws Exception{
        int event=parser.next();
        if(event==XmlPullParser.TEXT) {
            String value=parser.getText();
            parser.nextTag();
            return value==null?"":value.trim();
        }
        return "";
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(17),dp(7),dp(17),dp(10));
        setContentView(root);
        buildNavigation(root);

        LinearLayout heading=new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(43));
        hp.topMargin=dp(3);
        root.addView(heading,hp);
        TextView title=text("Agora na TV",22,true);
        heading.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        TextView hint=text("OK: abre UniTVnet    ·    Segurar OK: fixa/desafixa na Home",12,false);
        hint.setTextColor(MUTED);
        heading.addView(hint);

        status=text("Buscando programação externa…",12,false);
        status.setTextColor(MUTED);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(25)));

        HorizontalScrollView categoryScroller=new HorizontalScrollView(this);
        categoryScroller.setHorizontalScrollBarEnabled(false);
        tabs=new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        categoryScroller.addView(tabs);
        root.addView(categoryScroller,new LinearLayout.LayoutParams(-1,dp(48)));
        for(String name:CATEGORIES){
            Button button=menuButton(name,()->{
                if(!category.equals(name)){
                    category=name;
                    visibleLimit=PAGE;
                    focusedKey=null;
                    if(scroll!=null)scroll.scrollTo(0,0);
                    render();
                }
                updateTabColors();
            });
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(112),dp(37));
            bp.setMargins(0,0,dp(8),0);
            tabs.addView(button,bp);
        }
        updateTabColors();

        LinearLayout columns=new LinearLayout(this);
        columns.setPadding(dp(10),dp(3),dp(8),dp(3));
        columns.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(columns,new LinearLayout.LayoutParams(-1,dp(27)));
        headerText(columns,"CANAL",135,0);
        headerText(columns,"PROGRAMA AGORA",0,2);
        headerText(columns,"HORÁRIO / PROGRESSO",0,1);
        headerText(columns,"A SEGUIR",0,1);

        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        TextView footer=text("★ fixado na Home   ·   ↑↓ navegar   ·   Categorias na faixa superior   ·   Voltar: Início",11,false);
        footer.setTextColor(MUTED);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(25)));
        list.addView(text("Carregando canais…",15,false));
        jobs.execute(this::loadAll);
    }

    private void headerText(LinearLayout row,String label,int fixed,int weight){
        TextView t=text(label,10,true);
        t.setTextColor(MUTED);
        row.addView(t,fixed>0
            ?new LinearLayout.LayoutParams(dp(fixed),-1)
            :new LinearLayout.LayoutParams(0,-1,weight));
    }

    private Button menuButton(String name,Runnable action){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(name);
        b.setTextColor(WHITE);
        b.setTextSize(12);
        b.setPadding(dp(8),0,dp(8),0);
        b.setBackground(shape(CARD));
        b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,focus)->{
            if(focus)b.setBackground(shape(FOCUS));
            else b.setBackground(shape(b.getParent()==tabs&&category.equals(name)?SELECTED:CARD));
        });
        return b;
    }

    private void updateTabColors(){
        for(int i=0;i<tabs.getChildCount()&&i<CATEGORIES.length;i++){
            View child=tabs.getChildAt(i);
            if(!child.isFocused())
                child.setBackground(shape(CATEGORIES[i].equals(category)?SELECTED:CARD));
        }
    }

    private void buildNavigation(LinearLayout root){
        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView logo=text("▸  Free My TV",19,true);
        logo.setTextColor(0xff39c9ff);
        nav.addView(logo,new LinearLayout.LayoutParams(dp(160),-1));
        String[] labels={"Início","Agora na TV","Filmes","Séries","Apps"};
        for(int i=0;i<labels.length;i++){
            final int target=i;
            Button b=menuButton(labels[i],()->navigate(target));
            if(i==1)b.setBackground(shape(SELECTED));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(38),1);
            bp.setMargins(dp(3),0,dp(3),0);
            nav.addView(b,bp);
        }
        Button settings=menuButton("⚙",()->startActivity(new Intent(this,SettingsActivity.class)));
        nav.addView(settings,new LinearLayout.LayoutParams(dp(56),dp(38)));
    }

    private void navigate(int tab){
        if(tab==1)return;
        if(tab==0||tab==4){
            Intent intent=new Intent(this,HomeActivity.class);
            if(tab==4)intent.putExtra(HomeActivity.EXTRA_ALL_APPS,true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }else{
            Intent intent=new Intent(this,DiscoverActivity.class);
            intent.putExtra("initial_kind",tab==2?1:2);
            startActivity(intent);
            finish();
        }
    }

    private void loadAll(){
        for(String feed:FEEDS){
            if(!alive)return;
            try{
                HashMap<String,Channel> result=parseFeed(cacheFile(feed));
                main.post(()->{
                    if(!alive)return;
                    for(Channel c:result.values()) {
                        Channel old=all.get(c.key);
                        if(old==null)all.put(c.key,c);
                        else{
                            if(old.now.isEmpty()&&!c.now.isEmpty()){
                                old.now=c.now;old.start=c.start;old.end=c.end;
                            }
                            if(old.next.isEmpty()&&!c.next.isEmpty()){
                                old.next=c.next;old.nextAt=c.nextAt;
                            }
                            if(old.logo.isEmpty())old.logo=c.logo;
                        }
                    }
                    loadedFeeds++;render();
                });
            }catch(Exception ex){
                main.post(()->{
                    if(!alive)return;
                    failedFeeds++;
                    if(all.isEmpty())status.setText("Fonte indisponível: "+ex.getClass().getSimpleName()+" · tentando outra…");
                    else updateStatus();
                });
            }
        }
        main.post(()->{
            if(!alive)return;
            if(all.isEmpty())status.setText("Não foi possível carregar os guias. Verifique internet e tente reabrir.");
            else updateStatus();
        });
    }
    private File cacheFile(String name) throws Exception{
        File cache=new File(getCacheDir(),"xmltv-"+name);
        if(cache.isFile() && cache.length()>100 && System.currentTimeMillis()-cache.lastModified()<TTL)return cache;
        HttpURLConnection con=null;
        File temp=new File(getCacheDir(),"xmltv-"+name+".download");
        try{
            con=(HttpURLConnection)new URL(BASE+name).openConnection();
            con.setConnectTimeout(9000);con.setReadTimeout(18000);
            con.setRequestProperty("Accept-Encoding","identity");
            if(con.getResponseCode()!=200)throw new Exception("EPG HTTP "+con.getResponseCode());
            long length=0;
            try(InputStream in=con.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
                byte[] block=new byte[16384];int n;
                while((n=in.read(block))!=-1){
                    length+=n;
                    if(length>MAX_XML)throw new Exception("EPG excedeu limite de segurança");
                    out.write(block,0,n);
                }
            }
            if(length<100)throw new Exception("Guia EPG vazio");
            if(cache.exists()&&!cache.delete())throw new Exception("Falha ao atualizar cache");
            if(!temp.renameTo(cache))throw new Exception("Falha ao salvar cache EPG");
            return cache;
        }catch(Exception err){
            temp.delete();
            if(cache.isFile()&&cache.length()>100)return cache;
            throw err;
        }finally{if(con!=null)con.disconnect();}
    }
    private HashMap<String,Channel> parseFeed(File file) throws Exception{
        HashMap<String,Channel> byId=new HashMap<>();
        HashMap<String,Channel> out=new HashMap<>();
        long now=System.currentTimeMillis();
        try(FileInputStream input=new FileInputStream(file)){
            XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false);
            p.setInput(input,"UTF-8");
            Channel ch=null;
            String programmeId=null,programmeTitle="";
            long start=0,end=0;
            int event=p.getEventType();
            while(event!=XmlPullParser.END_DOCUMENT){
                if(event==XmlPullParser.START_TAG){
                    String tag=p.getName();
                    if("channel".equals(tag)) {
                        ch=new Channel();ch.id=p.getAttributeValue(null,"id");
                    }else if(ch!=null){
                        if("display-name".equals(tag)&&(ch.name==null||ch.name.isEmpty()))ch.name=read(p);
                        else if("icon".equals(tag)&&ch.logo.isEmpty()) {
                            String src=p.getAttributeValue(null,"src");
                            if(src!=null&&src.startsWith("https://"))ch.logo=src;
                        }
                    }else if("programme".equals(tag)){
                        programmeId=p.getAttributeValue(null,"channel");
                        programmeTitle="";
                        start=timestamp(p.getAttributeValue(null,"start"));
                        end=timestamp(p.getAttributeValue(null,"stop"));
                    }else if(programmeId!=null && "title".equals(tag) && programmeTitle.isEmpty()){
                        programmeTitle=read(p);
                    }
                }else if(event==XmlPullParser.END_TAG){
                    String tag=p.getName();
                    if("channel".equals(tag)&&ch!=null){
                        if(ch.name!=null&&ch.id!=null&&!ch.name.isEmpty()){
                            ch.key=normalize(ch.name);
                            ch.open=isOpen(ch.name);
                            if(!hidden(ch)) {byId.put(ch.id,ch);out.put(ch.key,ch);}
                        }
                        ch=null;
                    }else if("programme".equals(tag)&&programmeId!=null){
                        Channel c=byId.get(programmeId);
                        if(c!=null&&start>0&&end>start&&!programmeTitle.isEmpty()){
                            if(start<=now&&now<end){
                                c.now=programmeTitle;c.start=start;c.end=end;
                            }else if(start>now&&start<c.nextAt){
                                c.nextAt=start;
                                c.next=time(start)+" · "+programmeTitle;
                            }
                        }
                        programmeId=null;
                    }
                }
                event=p.next();
            }
        }
        return out;
    }
    private void updateStatus(){
        status.setText(all.size()+" canais no EPG externo  ·  "+HomeChannelPins.list(this).size()+"/30 fixados  ·  "+loadedFeeds+" fontes carregadas"+(failedFeeds>0?"  ·  "+failedFeeds+" indisponível(is)":""));
    }
    private void render(){
        if(!alive)return;
        int generation=++viewGeneration;
        list.removeAllViews();
        ArrayList<Channel> channels=new ArrayList<>(all.values());
        channels.sort((a,b)->{
            int r=rank(a)-rank(b);
            return r!=0?r:a.name.compareToIgnoreCase(b.name);
        });
        updateStatus();
        int displayed=0;
        boolean restored=false;
        for(Channel c:channels){
            if("Abertos".equals(category)&&!c.open)continue;
            if("Fechados".equals(category)&&c.open)continue;
            if(displayed>=120)break;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(14,11,14,11);
            card.setBackground(shape(CARD));card.setFocusable(true);card.setMinimumHeight(96);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,4,0,4);
            list.addView(card,cp);
            FrameLayout holder=new FrameLayout(this);
            card.addView(holder,new LinearLayout.LayoutParams(100,76));
            TextView fallback=text(c.name.length()>3?c.name.substring(0,3).toUpperCase(Locale.ROOT):c.name.toUpperCase(Locale.ROOT),14,true);
            fallback.setGravity(Gravity.CENTER);fallback.setBackground(shape(0xff29384a));
            holder.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            ImageView logo=new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setVisibility(View.INVISIBLE);
            holder.addView(logo,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout details=new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);details.setPadding(18,0,0,0);
            card.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            String name=c.name.replace('-', ' ');
            details.addView(text((HomeChannelPins.isPinned(this,c.key)?"★  ":"")+name,18,true));
            TextView now=text(c.now.isEmpty()?"Programação indisponível no momento":c.now,14,false);
            details.addView(now);
            if(c.start>0&&c.end>c.start){
                TextView timeLabel=text(time(c.start)+" – "+time(c.end),12,false);
                timeLabel.setTextColor(MUTED);details.addView(timeLabel);
                ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                progress.setMax(1000);
                long elapsed=System.currentTimeMillis()-c.start, duration=c.end-c.start;
                progress.setProgress((int)Math.max(0,Math.min(1000,1000L*elapsed/duration)));
                details.addView(progress,new LinearLayout.LayoutParams(-1,5));
            }
            if(!c.next.isEmpty()){
                TextView next=text("A seguir: "+c.next,12,false);next.setTextColor(MUTED);
                details.addView(next);
            }
            final String imageUrl=logoUrl(c);
            final Runnable loadImage=()->showLogo(imageUrl,logo,fallback,generation);
            if(displayed<12)loadImage.run();
            card.setOnFocusChangeListener((v,focus)->{
                card.setBackground(shape(focus?FOCUS:CARD));
                if(focus){focusedKey=c.key;loadImage.run();}
            });
            card.setOnClickListener(v->openUni());
            card.setOnLongClickListener(v->{
                HomeChannelPins.Result outcome=HomeChannelPins.toggle(
                    this,c.key,c.name,logoUrl(c));
                String message;
                switch(outcome){
                    case PINNED: message="Canal fixado ("+HomeChannelPins.list(this).size()+"/30)."; break;
                    case UNPINNED: message="Canal removido dos fixados."; break;
                    case FULL: message="Limite de 30 canais. Desafixe outro canal primeiro."; break;
                    default: message="Não foi possível salvar esse canal."; break;
                }
                Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
                if(outcome==HomeChannelPins.Result.PINNED||outcome==HomeChannelPins.Result.UNPINNED){
                    focusedKey=c.key;
                    render();
                }
                return true;
            });
            if(focusedKey!=null&&focusedKey.equals(c.key)){card.requestFocus();restored=true;}
            else if(displayed==0&&focusedKey==null){card.requestFocus();restored=true;}
            displayed++;
        }
        if(displayed==0)list.addView(text("Nenhum canal nessa categoria. Outras fontes podem estar carregando.",16,false));
    }
    private static String logoUrl(Channel c){
        if(c.logo.startsWith("https://"))return c.logo;
        String n=normalize(c.name);
        String path=null;
        if(n.equals("globo")||n.equals("tv globo"))path="globo-br.png";
        else if(n.equals("sbt"))path="sbt-br.png";
        else if(n.equals("sbt news"))path="sbt-news-br.png";
        else if(n.equals("record")||n.equals("record tv"))path="record-br.png";
        else if(n.equals("band"))path="band-br.png";
        else if(n.equals("band news"))path="band-news-br.png";
        else if(n.equals("band sports"))path="band-sports-br.png";
        else if(n.equals("globonews")||n.equals("globo news"))path="globo-news-br.png";
        else if(n.equals("gnt"))path="gnt-br.png";
        else if(n.equals("sportv"))path="sportv-br.png";
        else if(n.equals("sportv 2"))path="sportv2-br.png";
        else if(n.equals("sportv 3"))path="sportv3-br.png";
        else if(n.equals("multishow"))path="multishow-br.png";
        else if(n.equals("hbo"))path="hbo-br.png";
        else if(n.equals("tnt"))path="tnt-br.png";
        else if(n.equals("cnn brasil"))path="cnn-brasil-br.png";
        else if(n.equals("record news"))path="record-news-br.png";
        else if(n.equals("globoplay novelas"))path="globoplay-novelas-br.png";
        return path==null?"":LOGO_BASE+path;
    }
    private void showLogo(String url,ImageView image,TextView fallback,int generation){
        if(url.isEmpty()||image.getTag()!=null)return;
        image.setTag(url);
        synchronized(logos){
            Bitmap cached=logos.get(url);
            if(cached!=null) {
                image.setImageBitmap(cached);image.setVisibility(View.VISIBLE);fallback.setVisibility(View.INVISIBLE);
                return;
            }
        }
        jobs.execute(()->{
            Bitmap result=null;
            byte[] raw=HomeImageCache.read(this,url);
            if(raw==null){
                HttpURLConnection connection=null;
                try{
                    connection=(HttpURLConnection)new URL(url).openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(6000);
                    if(connection.getResponseCode()==200){
                        try(InputStream in=connection.getInputStream();
                            ByteArrayOutputStream out=new ByteArrayOutputStream()){
                            byte[] buf=new byte[4096];
                            int n,total=0;
                            while((n=in.read(buf))!=-1){
                                total+=n;
                                if(total>384000)break;
                                out.write(buf,0,n);
                            }
                            if(total>0&&total<=384000)raw=out.toByteArray();
                        }
                    }
                }catch(Exception ignored){}
                finally{if(connection!=null)connection.disconnect();}
            }
            if(raw!=null){
                try{
                    BitmapFactory.Options opt=new BitmapFactory.Options();
                    opt.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(raw,0,raw.length,opt);
                    opt.inSampleSize=1;
                    while(opt.outWidth/opt.inSampleSize>240
                        ||opt.outHeight/opt.inSampleSize>240)opt.inSampleSize*=2;
                    opt.inJustDecodeBounds=false;
                    result=BitmapFactory.decodeByteArray(raw,0,raw.length,opt);
                    if(result!=null)HomeImageCache.write(this,url,raw);
                }catch(Exception ignored){}
            }
            Bitmap bitmap=result;
            if(bitmap!=null) {
                synchronized(logos){logos.put(url,bitmap);}
                main.post(()->{
                    if(alive&&generation==viewGeneration&&url.equals(image.getTag())){
                        image.setImageBitmap(bitmap);image.setVisibility(View.VISIBLE);
                        fallback.setVisibility(View.INVISIBLE);
                    }
                });
            }
        });
    }
    private void openUni(){
        Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage("com.unitvnet.tvod");
        if(launch==null)launch=getPackageManager().getLaunchIntentForPackage("com.unitvnet.tvod");
        if(launch==null){Toast.makeText(this,"UniTVnet não encontrado. Guia apenas informativo.",Toast.LENGTH_LONG).show();return;}
        try{startActivity(launch);}
        catch(Exception ex){Toast.makeText(this,"Não foi possível abrir UniTVnet",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onDestroy(){
        alive=false;viewGeneration++;jobs.shutdownNow();super.onDestroy();
    }
}