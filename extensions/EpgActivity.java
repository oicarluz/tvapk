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
import java.util.List;
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
    private String category="Todos", focusedKey=null, focusOverrideKey=null;
    private boolean focusCategoryAfterRender=false;
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
        // Do not confuse a parent brand with its subscription channels.
        if(hasAny(n,"globo news","globonews","record news","recordnews",
            "band news","bandnews","bandsports","band sports","sbt news",
            "sbtnews","sport","premiere","combate"))return false;
        return n.equals("globo")||n.startsWith("globo ")||n.startsWith("tv globo")
            ||n.equals("sbt")||n.startsWith("sbt ")
            ||n.equals("record")||n.equals("record tv")
            ||n.startsWith("record tv ")||n.startsWith("record ")
            ||n.equals("band")||n.startsWith("band ")
            ||n.startsWith("rede tv")
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
            else b.setBackground(shape((b.getParent()==tabs&&category.equals(name))
                || (b.getParent()!=tabs&&"Agora na TV".equals(name))?SELECTED:CARD));
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
            Intent intent;
            if(tab==2){
                intent=new Intent(this,MoviesActivity.class);
            }else{
                intent=new Intent(this,SeriesActivity.class);
            }
            startActivity(intent);
            finish();
        }
    }

    private void loadAll(){
        for(String feed:EpgFeedCache.FEEDS){
            if(!alive)return;
            try{
                HashMap<String,Channel> result=parseFeed(EpgFeedCache.get(this,feed));
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
            if(all.isEmpty()){
                render();
                status.setText("Não foi possível carregar o EPG. Verifique a internet ou tente novamente.");
            }else updateStatus();
        });
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
                            ch.key=HomeChannelPins.canonical(ch.name);
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
    private boolean matches(Channel c){
        if("Fixados".equals(category))return HomeChannelPins.isPinned(this,c.key);
        if("Abertos".equals(category))return c.open;
        if("Fechados".equals(category))return !c.open;
        String n=normalize(c.name);
        if("Esportes".equals(category))
            return hasAny(n,"sport","esporte","espn","premiere","combate","xsports",
                "sportv","band sports","bandsports","nba","futebol");
        if("Filmes".equals(category))
            return hasAny(n,"cine","movie","filme","film","telecine","megapix",
                "hbo","tnt","space","amc","warner","universal","paramount");
        if("Infantil".equals(category))
            return hasAny(n,"kids","infantil","cartoon","nick","disney","baby",
                "boomerang","discovery kids","gloob","toon");
        if("Notícias".equals(category))
            return hasAny(n,"news","noticias","noticia","jornal","cnn",
                "senado","camara","jovem pan","bloomberg","bandnews");
        return true;
    }

    private static boolean hasAny(String name,String... tokens){
        for(String token:tokens)if(name.contains(token))return true;
        return false;
    }

    private void togglePin(Channel c){
        List<HomeChannelPins.Channel> before=HomeChannelPins.list(this);
        int previousIndex=0;
        for(int i=0;i<before.size();i++)
            if(before.get(i).key.equals(c.key)){previousIndex=i;break;}
        HomeChannelPins.Result result=HomeChannelPins.toggle(this,c.key,c.name,logoUrl(c));
        String msg;
        switch(result){
            case PINNED: msg="★ Canal fixado na Home ("+HomeChannelPins.list(this).size()+"/30).";break;
            case UNPINNED: msg="Canal removido da Home.";break;
            case FULL: msg="Limite de 30 canais. Remova outro antes de fixar.";break;
            default: msg="Não foi possível alterar este canal.";break;
        }
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
        if(result==HomeChannelPins.Result.PINNED||result==HomeChannelPins.Result.UNPINNED){
            focusedKey=c.key;
            if(result==HomeChannelPins.Result.UNPINNED&&"Fixados".equals(category)){
                List<HomeChannelPins.Channel> remaining=HomeChannelPins.list(this);
                if(remaining.isEmpty())focusCategoryAfterRender=true;
                else focusOverrideKey=remaining.get(Math.min(previousIndex,remaining.size()-1)).key;
            }
            render();
        }
    }

    private void render(){
        if(!alive)return;
        View previous=scroll==null?null:scroll.findFocus();
        // Keep the focused channel and scroll position when each source finishes.
        // A "Carregar mais" action has its own focus transition.
        final boolean restoreFocus=previous!=null&&previous.getTag() instanceof String;
        final String restoreKey=focusOverrideKey!=null
            ?focusOverrideKey:(restoreFocus?(String)previous.getTag():null);
        focusOverrideKey=null;
        final int previousScroll=scroll==null?0:scroll.getScrollY();
        final int generation=++viewGeneration;
        list.removeAllViews();
        ArrayList<Channel> channels=new ArrayList<>();
        if("Fixados".equals(category)){
            for(HomeChannelPins.Channel pin:HomeChannelPins.list(this)){
                Channel fromFeed=all.get(pin.key);
                if(fromFeed==null){
                    fromFeed=new Channel();
                    fromFeed.key=pin.key;
                    fromFeed.name=pin.name;
                    fromFeed.logo=pin.logo;
                    fromFeed.open=isOpen(pin.name);
                }
                channels.add(fromFeed);
            }
        }else{
            for(Channel c:all.values())if(matches(c))channels.add(c);
            channels.sort((first,second)->{
                int r=rank(first)-rank(second);
                return r!=0?r:first.name.compareToIgnoreCase(second.name);
            });
        }
        updateStatus();
        if(channels.isEmpty()){
            if(focusCategoryAfterRender&&tabs!=null&&tabs.getChildCount()>0){
                focusCategoryAfterRender=false;
                final View filter=tabs.getChildAt(tabs.getChildCount()-1);
                filter.post(filter::requestFocus);
            }
            TextView message=text("Nenhum canal nesta categoria. O EPG externo pode não oferecer programação para todos os canais.",14,false);
            message.setTextColor(MUTED);
            message.setPadding(dp(16),dp(18),dp(12),dp(18));
            list.addView(message);
            return;
        }

        int displayed=Math.min(channels.size(),visibleLimit);
        for(int index=0;index<displayed;index++){
            Channel c=channels.get(index);
            LinearLayout row=new LinearLayout(this);
            row.setTag(c.key);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(9),dp(4),dp(8),dp(4));
            row.setBackground(shape(CARD));
            row.setFocusable(true);
            row.setClickable(true);
            row.setContentDescription(c.name+". "+(c.now.isEmpty()?
                "Programação não disponível":c.now)+
                ". Pressione OK para abrir UniTVnet; segure OK para fixar.");
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(67));
            rp.setMargins(0,dp(2),0,dp(2));
            list.addView(row,rp);

            LinearLayout channel=new LinearLayout(this);
            channel.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(channel,new LinearLayout.LayoutParams(dp(135),-1));
            FrameLayout iconBox=new FrameLayout(this);
            channel.addView(iconBox,new LinearLayout.LayoutParams(dp(44),dp(40)));
            TextView fallback=text(c.name.substring(0,Math.min(3,c.name.length())).toUpperCase(Locale.ROOT),10,true);
            fallback.setGravity(Gravity.CENTER);
            fallback.setBackground(shape(0xff293a54));
            iconBox.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            ImageView logo=new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setVisibility(View.INVISIBLE);
            iconBox.addView(logo,new FrameLayout.LayoutParams(-1,-1));

            TextView name=text((HomeChannelPins.isPinned(this,c.key)?"★  ":"")+c.name,13,true);
            name.setSingleLine(false);
            LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(0,-1,1);
            nameLp.leftMargin=dp(7);
            channel.addView(name,nameLp);

            LinearLayout now=new LinearLayout(this);
            now.setGravity(Gravity.CENTER_VERTICAL);
            now.setPadding(dp(10),0,dp(7),0);
            row.addView(now,new LinearLayout.LayoutParams(0,-1,2));
            TextView program=text(c.now.isEmpty()?"Programação indisponível":c.now,14,true);
            program.setMaxLines(2);
            now.addView(program);

            LinearLayout progressArea=new LinearLayout(this);
            progressArea.setOrientation(LinearLayout.VERTICAL);
            progressArea.setGravity(Gravity.CENTER_VERTICAL);
            progressArea.setPadding(dp(6),0,dp(15),0);
            row.addView(progressArea,new LinearLayout.LayoutParams(0,-1,1));
            boolean hasTimes=c.end>c.start;
            TextView times=text(hasTimes?time(c.start)+" – "+time(c.end):"Horário não informado",11,false);
            times.setTextColor(MUTED);
            progressArea.addView(times);
            ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            if(hasTimes){
                long fraction=1000L*(System.currentTimeMillis()-c.start)/(c.end-c.start);
                progress.setProgress((int)Math.max(0,Math.min(1000,fraction)));
            }else progress.setProgress(0);
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(5));
            pp.topMargin=dp(5);
            progressArea.addView(progress,pp);

            LinearLayout next=new LinearLayout(this);
            next.setGravity(Gravity.CENTER_VERTICAL);
            next.setPadding(dp(8),0,dp(4),0);
            row.addView(next,new LinearLayout.LayoutParams(0,-1,1));
            TextView nextProgram=text(c.next.isEmpty()?"A seguir: sem dados":"A seguir: "+c.next,11,false);
            nextProgram.setTextColor(MUTED);
            next.addView(nextProgram);

            String imageUrl=logoUrl(c);
            Runnable showImage=()->showLogo(imageUrl,logo,fallback,generation);
            if(index<14)showImage.run();
            row.setOnFocusChangeListener((view,focused)->{
                row.setBackground(shape(focused?FOCUS:CARD));
                if(focused){
                    focusedKey=c.key;
                    showImage.run();
                }
            });
            TvRemotePress.bind(row,()->openUni(),()->togglePin(c));
            if(restoreKey!=null&&restoreKey.equals(c.key))row.requestFocus();
        }
        if(restoreFocus&&scroll!=null&&previousScroll>0)
            scroll.post(()->scroll.scrollTo(0,previousScroll));

        if(displayed<channels.size()){
            final String nextKey=channels.get(displayed).key;
            Button more=menuButton("Carregar mais canais   ("+displayed+" de "+channels.size()+")",()->{
                focusedKey=nextKey;
                visibleLimit+=PAGE;
                render();
            });
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(46));
            mp.topMargin=dp(9);
            list.addView(more,mp);
        }
    }

    @Override protected void onResume(){
        super.onResume();
        if(list!=null&&(!all.isEmpty()||!HomeChannelPins.list(this).isEmpty()))render();
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