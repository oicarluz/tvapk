package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only public XMLTV guide. Never accesses UniTVnet credentials or streams. */
public final class EpgActivity extends Activity {
    private static final String SOURCE="https://raw.githubusercontent.com/iptv-com/epg/main/guides/brazil.xml";
    private static final int BG=0xff10141d, CARD=0xff202a38, WHITE=0xfff7f8fb, MUTED=0xffb4c2d3;
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LinearLayout list, tabs;
    private TextView state;
    private ArrayList<Channel> channels=new ArrayList<>();
    private String filter="Todos";
    private boolean alive=true;
    private static final String[] CATEGORIES={"Todos","Abertos","Fechados"};
    private static final String[] OPEN={"globo","sbt","record","band","rede tv","tv brasil","cultura","gazeta","aparecida","tv senado","tv camara"};
    static class Channel {
        String id,name,logo="",now="",next="";
        long start,end;
        boolean open;
    }
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(18);return d;}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(WHITE);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private static boolean isOpen(String s){
        String v=java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");
        for(String n:OPEN)if(v.equals(n)||v.startsWith(n+" ")||v.startsWith(n+" -"))return true;
        return false;
    }
    private static long date(String raw){
        if(raw==null||raw.length()<14)return 0;
        try{
            String base=raw.substring(0,14);
            String offset=raw.length()>=20?raw.substring(15,20):"+0000";
            SimpleDateFormat fmt=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);
            return fmt.parse(base+" "+offset).getTime();
        }catch(Exception e){return 0;}
    }
    private static String clock(long ms){return new SimpleDateFormat("HH:mm",new Locale("pt","BR")).format(new Date(ms));}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(35,20,35,15);root.setBackgroundColor(BG);setContentView(root);
        TextView title=text("TV AO VIVO   ·   Agora na programação",25,true);root.addView(title);
        state=text("Buscando guia externo…",13,false);state.setTextColor(MUTED);root.addView(state);
        tabs=new LinearLayout(this);root.addView(tabs,new LinearLayout.LayoutParams(-1,54));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView foot=text("EPG externo · informações podem variar por região · OK abre UniTVnet",12,false);
        foot.setTextColor(MUTED);root.addView(foot);
        for(String category:CATEGORIES){
            Button button=new Button(this);button.setAllCaps(false);button.setText(category);button.setTextColor(WHITE);button.setTextSize(14);
            button.setOnClickListener(v->{filter=category;render();});
            tabs.addView(button,new LinearLayout.LayoutParams(0,50,1));
        }
        io.execute(this::fetch);
    }
    private static String read(XmlPullParser p) throws Exception {
        String result="";
        if(p.next()==XmlPullParser.TEXT){result=p.getText();p.nextTag();}
        return result==null?"":result.trim();
    }
    private void fetch(){
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(SOURCE).openConnection();
            connection.setConnectTimeout(12000);connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept-Encoding","identity");
            if(connection.getResponseCode()!=200)throw new Exception("HTTP "+connection.getResponseCode());
            HashMap<String,Channel> map=new HashMap<>();
            ArrayList<Channel> order=new ArrayList<>();
            long now=System.currentTimeMillis();
            try(InputStream stream=connection.getInputStream()){
                XmlPullParser p=Xml.newPullParser();p.setInput(stream,"UTF-8");
                int event=p.getEventType();
                Channel current=null;
                String programmeChannel=null, programmeTitle="";
                long start=0,end=0;
                while(event!=XmlPullParser.END_DOCUMENT){
                    if(event==XmlPullParser.START_TAG){
                        String tag=p.getName();
                        if("channel".equals(tag)){
                            current=new Channel();current.id=p.getAttributeValue(null,"id");
                        }else if(current!=null){
                            if("display-name".equals(tag)&&current.name==null)current.name=read(p);
                            else if("icon".equals(tag)&&current.logo.isEmpty()){
                                String icon=p.getAttributeValue(null,"src");if(icon!=null&&icon.startsWith("https://"))current.logo=icon;
                            }
                        }else if("programme".equals(tag)){
                            programmeChannel=p.getAttributeValue(null,"channel");
                            start=date(p.getAttributeValue(null,"start"));
                            end=date(p.getAttributeValue(null,"stop"));
                            programmeTitle="";
                        }else if(programmeChannel!=null&&"title".equals(tag)&&programmeTitle.isEmpty()){
                            programmeTitle=read(p);
                        }
                    }else if(event==XmlPullParser.END_TAG){
                        String tag=p.getName();
                        if("channel".equals(tag)&&current!=null){
                            if(current.name!=null&&current.id!=null){
                                current.open=isOpen(current.name);map.put(current.id,current);order.add(current);
                            }
                            current=null;
                        }else if("programme".equals(tag)&&programmeChannel!=null){
                            Channel c=map.get(programmeChannel);
                            if(c!=null&&!programmeTitle.isEmpty()){
                                if(start<=now&&now<end){c.now=programmeTitle;c.start=start;c.end=end;}
                                else if(start>now&&start<now+86400000L&&(c.next.isEmpty()||start<c.end)){
                                    if(c.now.isEmpty()||start>=c.end)c.next=clock(start)+" · "+programmeTitle;
                                }
                            }
                            programmeChannel=null;
                        }
                    }
                    event=p.next();
                }
            }
            order.sort((a,b)->{
                if(a.open!=b.open)return a.open?-1:1;
                return a.name.compareToIgnoreCase(b.name);
            });
            ui.post(()->{if(!alive)return;channels=order;state.setText(order.size()+" canais no guia · atualizado pela fonte externa");render();});
        }catch(Exception e){
            final String message=e.getMessage()==null?"erro de rede":e.getMessage();
            ui.post(()->{if(alive)state.setText("Não foi possível atualizar o EPG: "+message);});
        }finally{if(connection!=null)connection.disconnect();}
    }
    private void render(){
        list.removeAllViews();
        int shown=0;
        for(Channel c:channels){
            if("Abertos".equals(filter)&&!c.open)continue;
            if("Fechados".equals(filter)&&c.open)continue;
            if(shown++>=150)break;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(16,9,16,9);row.setBackground(shape(CARD));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,4,0,4);list.addView(row,rp);
            ImageView logo=new ImageView(this);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(logo,new LinearLayout.LayoutParams(96,62));
            if(!c.logo.isEmpty())io.execute(()->{
                try{
                    HttpURLConnection con=(HttpURLConnection)new URL(c.logo).openConnection();
                    con.setConnectTimeout(5000);con.setReadTimeout(5000);
                    try(InputStream input=con.getInputStream()){
                        Bitmap bmp=BitmapFactory.decodeStream(input);
                        if(bmp!=null)ui.post(()->{if(alive)logo.setImageBitmap(bmp);});
                    }finally{con.disconnect();}
                }catch(Exception ignored){}
            });
            LinearLayout detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);detail.setPadding(18,2,2,2);
            row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
            detail.addView(text(c.name,18,true));
            TextView now=text(c.now.isEmpty()?"Programação indisponível":c.now,14,false);detail.addView(now);
            if(c.end>c.start){
                TextView time=text(clock(c.start)+" – "+clock(c.end),12,false);time.setTextColor(MUTED);detail.addView(time);
                android.widget.ProgressBar progress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                progress.setMax(1000);progress.setProgress((int)Math.min(1000,Math.max(0,1000L*(System.currentTimeMillis()-c.start)/(c.end-c.start))));
                detail.addView(progress,new LinearLayout.LayoutParams(-1,5));
            }
            if(!c.next.isEmpty()){TextView next=text("Depois: "+c.next,12,false);next.setTextColor(MUTED);detail.addView(next);}
            row.setFocusable(true);row.setOnClickListener(v->openUni());
            row.setOnFocusChangeListener((v,focus)->row.setBackground(shape(focus?0xff3b577e:CARD)));
        }
        if(shown==0)list.addView(text("Nenhum canal encontrado nesta categoria.",16,false));
    }
    private void openUni(){
        String pkg="com.unitvnet.tvod";
        Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if(intent==null)intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null){Toast.makeText(this,"UniTVnet não encontrado",Toast.LENGTH_LONG).show();return;}
        try{startActivity(intent);}catch(Exception e){Toast.makeText(this,"Falha ao abrir UniTVnet",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onDestroy(){alive=false;io.shutdownNow();super.onDestroy();}
}