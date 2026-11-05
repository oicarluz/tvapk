package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phase 2 Android-TV Home layout. This View lives in HomeActivity, the real
 * HOME intent receiver. All remote interactions are native focusable views.
 *
 * The classic HomeView is still shipped and can be used as a crash fallback.
 * Guide/catalog Activities remain independent until their full redesign.
 */
final class HomeDashboardView extends LinearLayout {
    private static final int DARK=0xff090f1d, PANEL=0xff172138, PANEL2=0xff202c47;
    private static final int TEXT=0xfff6f8ff, MUTED=0xffb8c5db, ACCENT=0xff39c9ff;
    private static final int FOCUS=0xff405a99;
    private static final int PURPLE=0xff8e51e8;
    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final ArrayList<Feature> features = new ArrayList<>();
    private final ArrayList<Button> navigation = new ArrayList<>();
    private LinearLayout contents, channelRow, appRow;
    private ScrollView scroll;
    private FrameLayout banner;
    private ImageView bannerImage;
    private TextView bannerTitle, bannerSummary, bannerCategory, channelSubtitle;
    private boolean alive=true, active, showingApps;
    private int generation, featureIndex, epgRequestId, heroRequestId;
    private long lastHeroAttempt, lastEpgAt;
    private String lastEpgPins="";
    private boolean epgLoading, heroLoading;
    private Map<String,HomeEpgSnapshot.Guide> epg;
    private String featureImageUrl="";
    private static final class Feature {
        String title, description, poster, kind;
        Feature(String title,String description,String poster,String kind){
            this.title=title;this.description=description;this.poster=poster;this.kind=kind;
        }
    }

    HomeDashboardView(Activity a){
        super(a);
        activity=a;
        setOrientation(VERTICAL);
        setBackgroundColor(DARK);
        setPadding(dp(14),dp(9),dp(14),dp(5));
        setFocusable(false);
        buildHeader();
        scroll=new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        contents=new LinearLayout(a);
        contents.setOrientation(VERTICAL);
        scroll.addView(contents);
        addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        showHome(false);
        // Android TV requires a visible initial focus for D-pad navigation.
        post(()->{if(alive&&!navigation.isEmpty())navigation.get(0).requestFocus();});
    }

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color,int radius){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
    }
    private GradientDrawable focusShape(boolean focused){
        GradientDrawable shape=rounded(focused?FOCUS:PANEL2,14);
        shape.setStroke(dp(focused?2:1),focused?ACCENT:0xff303e5a);
        return shape;
    }
    private TextView text(String value,int size,boolean strong){
        TextView label=new TextView(activity);
        label.setText(value);
        label.setTextColor(TEXT);
        label.setTextSize(size);
        label.setGravity(Gravity.CENTER_VERTICAL);
        if(strong)label.setTypeface(null,android.graphics.Typeface.BOLD);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return label;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(activity);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setPadding(dp(10),0,dp(10),0);
        b.setBackground(focusShape(false));
        b.setFocusable(true);
        b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,focus)->b.setBackground(focusShape(focus)));
        return b;
    }
    private void headerButton(LinearLayout header,String title,int index,Runnable action){
        Button b=button(title,action);
        b.setMinWidth(0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
        lp.setMargins(dp(3),0,dp(3),0);
        header.addView(b,lp);
        navigation.add(b);
    }
    private void buildHeader(){
        LinearLayout header=new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView logo=text("▸  Free My TV",19,true);
        logo.setTextColor(ACCENT);
        header.addView(logo,new LinearLayout.LayoutParams(dp(150),-1));
        headerButton(header,"Início",0,()->showHome(true));
        headerButton(header,"Agora na TV",1,()->open(EpgActivity.class));
        headerButton(header,"Filmes",2,()->openDiscover(1));
        headerButton(header,"Séries",3,()->openDiscover(2));
        headerButton(header,"Apps",4,()->showApps());
        Button settings=button("⚙",()->open(SettingsActivity.class));
        header.addView(settings,new LinearLayout.LayoutParams(dp(53),dp(42)));
        navigation.add(settings);
    }
    private void setCurrent(int index){
        for(int i=0;i<navigation.size();i++){
            Button button=navigation.get(i);
            if(!button.isFocused()){
                GradientDrawable bg=rounded(i==index?0xff304c79:PANEL2,14);
                bg.setStroke(dp(1),i==index?0xff557edc:0xff303e5a);
                button.setBackground(bg);
            }
        }
    }
    private void open(Class<? extends Activity> klass){
        try{activity.startActivity(new Intent(activity,klass));}
        catch(Exception ignored){Toast.makeText(activity,"Não foi possível abrir esta seção.",Toast.LENGTH_SHORT).show();}
    }
    private void openDiscover(int kind){
        try{
            if(kind==1){
                activity.startActivity(new Intent(activity,MoviesActivity.class));
                return;
            }
            if(kind==2){
                activity.startActivity(new Intent(activity,SeriesActivity.class));
                return;
            }
            Intent intent=new Intent(activity,DiscoverActivity.class);
            intent.putExtra("initial_kind",kind);
            activity.startActivity(intent);
        }catch(Exception ignored){
            Toast.makeText(activity,"Catálogo indisponível.",Toast.LENGTH_SHORT).show();
        }
    }
    private void showHome(boolean requestFocus){
        showingApps=false;
        contents.removeAllViews();
        contents.setPadding(dp(4),dp(5),dp(4),dp(20));
        buildBanner();
        buildChannels();
        buildAppsShelf();
        setCurrent(0);
        if(requestFocus&&!navigation.isEmpty())navigation.get(0).requestFocus();
        if(scroll!=null)scroll.scrollTo(0,0);
    }
    private void section(String title,String action,Runnable callback){
        LinearLayout heading=new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));
        p.topMargin=dp(6);
        contents.addView(heading,p);
        TextView caption=text(title,19,true);
        heading.addView(caption,new LinearLayout.LayoutParams(0,-1,1));
        Button more=button(action,callback);
        heading.addView(more,new LinearLayout.LayoutParams(dp(110),dp(34)));
    }
    private void buildBanner(){
        banner=new FrameLayout(activity);
        banner.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff29335d,0xff172b52,0xff38204d}));
        banner.setClipToOutline(true);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(197));
        bp.bottomMargin=dp(5);
        contents.addView(banner,bp);
        bannerImage=new ImageView(activity);
        bannerImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.addView(bannerImage,new FrameLayout.LayoutParams(-1,-1));
        View overlay=new View(activity);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xf5081020,0xd50a1123,0x40101830}));
        banner.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout layer=new LinearLayout(activity);
        layer.setOrientation(VERTICAL);
        layer.setGravity(Gravity.CENTER_VERTICAL);
        layer.setPadding(dp(25),dp(13),dp(25),dp(13));
        banner.addView(layer,new FrameLayout.LayoutParams(-1,-1));
        bannerCategory=text("NOVIDADES • FILMES E SÉRIES",12,true);
        bannerCategory.setTextColor(0xff9ddcff);
        layer.addView(bannerCategory);
        bannerTitle=text("Novidades em destaque",28,true);
        bannerTitle.setMaxLines(2);
        layer.addView(bannerTitle);
        bannerSummary=text("Explore filmes e séries. Configure sua chave TMDB nos ajustes para receber destaques atualizados.",13,false);
        bannerSummary.setTextColor(MUTED);
        bannerSummary.setMaxLines(2);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(490),-2);
        sp.topMargin=dp(5);layer.addView(bannerSummary,sp);
        LinearLayout actions=new LinearLayout(activity);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(40));
        ap.topMargin=dp(10);layer.addView(actions,ap);
        Button explore=button("Explorar novidades",()->openDiscover(0));
        actions.addView(explore,new LinearLayout.LayoutParams(dp(178),-1));
        Button next=button("Próximo ›",()->{
            if(features.isEmpty())return;
            featureIndex=(featureIndex+1)%features.size();
            renderFeature();
        });
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(106),-1);
        np.leftMargin=dp(8);actions.addView(next,np);
        renderFeature();
    }
    private void renderFeature(){
        if(bannerTitle==null)return;
        if(features.isEmpty()){
            bannerTitle.setText("Novidades em destaque");
            bannerSummary.setText("Filmes e séries selecionados pelo TMDB. Configure sua chave TMDB nos ajustes para visualizar lançamentos.");
            bannerCategory.setText("FILMES E SÉRIES");
            bannerImage.setImageDrawable(null);
            featureImageUrl="";
            return;
        }
        Feature current=features.get(featureIndex%features.size());
        bannerTitle.setText(current.title);
        bannerSummary.setText(current.description.isEmpty()?"Confira novidades e títulos em destaque.":current.description);
        bannerCategory.setText("NOVIDADES • "+("tv".equals(current.kind)?"SÉRIE":"FILME"));
        bannerImage.setImageDrawable(null);
        featureImageUrl=current.poster;
        loadBackdrop(current.poster);
    }

    private void loadHero(){
        if(Recs.key()==null||Recs.key().isEmpty())return;
        // A separate request ID prevents an obsolete response from updating
        // a resumed Home, and a cancelled fetch no longer suppresses retry.
        long now=System.currentTimeMillis();
        if(heroLoading)return;
        if(lastHeroAttempt!=0&&now-lastHeroAttempt>=0
            &&now-lastHeroAttempt<5L*60_000)return;
        lastHeroAttempt=now;
        heroLoading=true;
        final int ticket=++heroRequestId;
        final int lifecycle=generation;
        io.execute(()->{
            ArrayList<Feature> found=new ArrayList<>();
            try{
                JSONObject response=DiscoverData.get("/trending/all/week?language=pt-BR");
                JSONArray list=response.optJSONArray("results");
                if(list!=null)for(int i=0;i<list.length()&&found.size()<7;i++){
                    JSONObject item=list.optJSONObject(i);
                    if(item==null||item.optBoolean("adult",false))continue;
                    String kind=item.optString("media_type","");
                    if(!"movie".equals(kind)&&!"tv".equals(kind))continue;
                    String title="tv".equals(kind)?item.optString("name",""):item.optString("title","");
                    String backdrop=item.optString("backdrop_path","");
                    if(title.isEmpty()||!backdrop.startsWith("/"))continue;
                    String overview=item.optString("overview","");
                    found.add(new Feature(title,overview,"https://image.tmdb.org/t/p/w780"+backdrop,kind));
                }
            }catch(Exception ignored){}
            ui.post(()->{
                if(ticket!=heroRequestId)return;
                heroLoading=false;
                if(!alive||!active||lifecycle!=generation){
                    // Interrupted by leaving the launcher: retry on return.
                    lastHeroAttempt=0;
                    return;
                }
                if(found.isEmpty())return;
                features.clear();features.addAll(found);
                featureIndex=0;renderFeature();
            });
        });
    }
    private void loadBackdrop(String url){
        if(url==null||url.isEmpty())return;
        int ticket=generation;
        io.execute(()->{
            Bitmap bitmap=downloadImage(url,900_000);
            ui.post(()->{
                if(alive&&active&&ticket==generation
                    &&url.equals(featureImageUrl)&&bannerImage!=null&&bitmap!=null){
                    bannerImage.setImageBitmap(bitmap);
                }
            });
        });
    }
    private Bitmap downloadImage(String url,int maxBytes){
        byte[] bytes=HomeImageCache.read(activity,url);
        if(bytes==null){
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(6500);
                connection.setReadTimeout(9000);
                if(connection.getResponseCode()==200){
                    try(InputStream in=connection.getInputStream();
                        ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] chunk=new byte[4096];int n,total=0;
                        while((n=in.read(chunk))!=-1){
                            total+=n;if(total>maxBytes)break;
                            out.write(chunk,0,n);
                        }
                        if(total>0&&total<=maxBytes)bytes=out.toByteArray();
                    }
                }
            }catch(Exception ignored){}
            finally{if(connection!=null)connection.disconnect();}
            if(bytes!=null)HomeImageCache.write(activity,url,bytes);
        }
        if(bytes==null)return null;
        try{
            BitmapFactory.Options opt=new BitmapFactory.Options();
            opt.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
            opt.inSampleSize=1;
            while(opt.outWidth/opt.inSampleSize>1150||opt.outHeight/opt.inSampleSize>640)opt.inSampleSize*=2;
            opt.inJustDecodeBounds=false;
            return BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
        }catch(Exception ignored){return null;}
    }

    private HorizontalScrollView horizontal(LinearLayout row){
        HorizontalScrollView scroller=new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.addView(row);
        contents.addView(scroller,new LinearLayout.LayoutParams(-1,-2));
        return scroller;
    }
    private void buildChannels(){
        section("Agora na TV · canais fixados","Organizar",()->open(HomeFavoritesActivity.class));
        channelSubtitle=text("Segure OK em um canal na aba Agora na TV para fixá-lo aqui (até 30).",12,false);
        channelSubtitle.setTextColor(MUTED);
        contents.addView(channelSubtitle);
        channelRow=new LinearLayout(activity);
        channelRow.setGravity(Gravity.CENTER_VERTICAL);
        horizontal(channelRow);
        refreshChannels();
    }
    private void refreshChannels(){
        if(channelRow==null)return;
        // Replacing rows after an EPG update used to drop TCL remote focus.
        View previouslyFocused=channelRow.getFocusedChild();
        String focusKey=previouslyFocused!=null&&previouslyFocused.getTag() instanceof String
            ?(String)previouslyFocused.getTag():null;
        HorizontalScrollView strip=channelRow.getParent() instanceof HorizontalScrollView
            ?(HorizontalScrollView)channelRow.getParent():null;
        int oldScroll=strip==null?0:strip.getScrollX();
        channelRow.removeAllViews();
        View focusReplacement=null;
        List<HomeChannelPins.Channel> pinned=HomeChannelPins.list(activity);
        if(channelSubtitle!=null){
            channelSubtitle.setText(pinned.isEmpty()?
              "Nenhum canal fixado. Vá em Agora na TV e segure OK para adicionar.":
              pinned.size()+" de 30 canais fixados · programação externa");
        }
        if(pinned.isEmpty()){
            Button add=button("+ Escolher canais",()->open(EpgActivity.class));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(250),dp(110));
            p.setMargins(dp(2),dp(5),dp(12),dp(8));
            channelRow.addView(add,p);
            return;
        }
        for(HomeChannelPins.Channel c:pinned){
            HomeEpgSnapshot.Guide current=epg==null?null:epg.get(c.key);
            LinearLayout panel=new LinearLayout(activity);
            panel.setTag(c.key);
            if(focusKey!=null&&focusKey.equals(c.key))focusReplacement=panel;
            panel.setOrientation(VERTICAL);
            panel.setPadding(dp(13),dp(9),dp(13),dp(8));
            panel.setBackground(focusShape(false));
            panel.setFocusable(true);
            panel.setOnFocusChangeListener((v,focus)->panel.setBackground(focusShape(focus)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(230),dp(123));
            cp.setMargins(dp(2),dp(5),dp(10),dp(8));
            channelRow.addView(panel,cp);
            LinearLayout header=new LinearLayout(activity);
            header.setGravity(Gravity.CENTER_VERTICAL);
            panel.addView(header,new LinearLayout.LayoutParams(-1,dp(34)));
            ImageView icon=new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            header.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(29)));
            if(!c.logo.isEmpty())loadChannelIcon(c.logo,icon);
            TextView name=text(c.name,15,true);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-1,1);
            np.leftMargin=dp(9);header.addView(name,np);
            TextView show=text(current==null||current.current.isEmpty()?"Programação não disponível":current.current,13,false);
            show.setSingleLine(true);
            panel.addView(show,new LinearLayout.LayoutParams(-1,dp(27)));
            TextView time=text(current!=null&&current.start>0&&current.end>current.start
               ?clock(current.start)+" – "+clock(current.end):"EPG externo",11,false);
            time.setTextColor(MUTED);
            panel.addView(time,new LinearLayout.LayoutParams(-1,dp(18)));
            ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            if(current!=null&&current.end>current.start) {
                long elapsed=System.currentTimeMillis()-current.start;
                long duration=current.end-current.start;
                progress.setProgress((int)Math.min(1000,Math.max(0,1000L*elapsed/duration)));
            }else progress.setProgress(0);
            panel.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
            panel.setOnClickListener(v->openUni());
        }
        if(strip!=null&&oldScroll>0)strip.post(()->strip.scrollTo(oldScroll,0));
        final View focusTarget=focusReplacement;
        if(focusTarget!=null)focusTarget.post(()->{
            if(active&&focusTarget.isAttachedToWindow())focusTarget.requestFocus();
        });
    }
    private String clock(long millis){
        return new java.text.SimpleDateFormat("HH:mm",new Locale("pt","BR"))
            .format(new java.util.Date(millis));
    }
    private void loadChannelIcon(String url,ImageView image){
        int ticket=generation;
        io.execute(()->{
            Bitmap bitmap=downloadImage(url,384000);
            ui.post(()->{
                if(alive&&active&&ticket==generation&&bitmap!=null)image.setImageBitmap(bitmap);
            });
        });
    }
    private void loadEpg(){
        List<HomeChannelPins.Channel> pinned=HomeChannelPins.list(activity);
        if(pinned.isEmpty()){
            epg=null;
            lastEpgPins="";
            lastEpgAt=0;
            return;
        }
        StringBuilder fingerprint=new StringBuilder();
        for(HomeChannelPins.Channel p:pinned)fingerprint.append(p.key).append('|');
        final String signature=fingerprint.toString();
        final long now=System.currentTimeMillis();
        // A return from UniTVnet should not parse multiple XML files again
        // when neither the fixed channels nor the cache window changed.
        if(epgLoading)return;
        if(epg!=null&&signature.equals(lastEpgPins)&&lastEpgAt>0
            &&now-lastEpgAt>=0&&now-lastEpgAt<5L*60_000)return;
        epgLoading=true;
        final int ticket=++epgRequestId;
        final int lifecycle=generation;
        io.execute(()->{
            Map<String,HomeEpgSnapshot.Guide> snapshot=HomeEpgSnapshot.load(activity,pinned);
            ui.post(()->{
                if(ticket!=epgRequestId)return;
                epgLoading=false;
                if(!alive||!active||lifecycle!=generation)return;
                epg=snapshot;
                lastEpgPins=signature;
                lastEpgAt=System.currentTimeMillis();
                if(!showingApps)refreshChannels();
            });
        });
    }

    private void buildAppsShelf(){
        section("Apps fixados","Organizar",()->open(HomeFavoritesActivity.class));
        appRow=new LinearLayout(activity);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        horizontal(appRow);
        fillApps();
    }
    private void fillApps(){
        if(appRow==null)return;
        appRow.removeAllViews();
        List<String> packages=HomeApps.shelfPackages(activity);
        if(packages.isEmpty()){
            TextView empty=text("Fixe aplicativos nas configurações do Free My TV.",13,false);
            appRow.addView(empty);
        }
        for(String pkg:packages){
            if(HomeApps.launchIntent(activity.getPackageManager(),pkg)==null)continue;
            appRow.addView(appCard(pkg,170,90));
        }
        Button more=button("+ Apps",()->showApps());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(136),dp(85));
        p.setMargins(dp(5),dp(4),dp(6),dp(4));appRow.addView(more,p);
    }
    private LinearLayout appCard(String pkg,int width,int height){
        LinearLayout tile=new LinearLayout(activity);
        tile.setPadding(dp(10),dp(10),dp(10),dp(9));
        tile.setOrientation(VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(focusShape(false));
        tile.setFocusable(true);
        tile.setOnFocusChangeListener((v,focus)->tile.setBackground(focusShape(focus)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(width),dp(height));
        lp.setMargins(dp(3),dp(4),dp(10),dp(4));
        tile.setLayoutParams(lp);
        PackageManager pm=activity.getPackageManager();
        ImageView icon=new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try{icon.setImageDrawable(pm.getApplicationIcon(pkg));}
        catch(Exception ignored){}
        tile.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
        String name=pkg;
        try{
            android.content.pm.ApplicationInfo info=pm.getApplicationInfo(pkg,0);
            name=String.valueOf(pm.getApplicationLabel(info));
        }catch(Exception ignored){}
        TextView label=text(name,12,true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-1,dp(24));
        l.topMargin=dp(4);tile.addView(label,l);
        tile.setOnClickListener(v->launchApp(pkg));
        return tile;
    }
    private void launchApp(String pkg){
        Intent launch=HomeApps.launchIntent(activity.getPackageManager(),pkg);
        if(launch==null){Toast.makeText(activity,"App indisponível",Toast.LENGTH_SHORT).show();return;}
        try{activity.startActivity(launch);}catch(Exception ignored){
            Toast.makeText(activity,"Não foi possível abrir o app.",Toast.LENGTH_SHORT).show();
        }
    }
    private void openUni(){
        launchApp("com.unitvnet.tvod");
    }
    private void showApps(){
        showingApps=true;
        contents.removeAllViews();
        setCurrent(4);
        section("Seus aplicativos","Personalizar",()->open(HomeFavoritesActivity.class));
        List<String[]> found=HomeApps.shelfCandidates(activity);
        if(found.isEmpty()){
            TextView empty=text("Nenhum aplicativo encontrado.",14,false);
            contents.addView(empty);
            return;
        }
        final int cols=5;
        for(int i=0;i<found.size();i+=cols){
            LinearLayout row=new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            contents.addView(row,new LinearLayout.LayoutParams(-1,dp(105)));
            for(int j=0;j<cols;j++){
                if(i+j>=found.size()){
                    View blank=new View(activity);
                    row.addView(blank,new LinearLayout.LayoutParams(0,-1,1));
                    continue;
                }
                String pkg=found.get(i+j)[0];
                LinearLayout tile=appCard(pkg,155,90);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(91),1);
                p.setMargins(dp(4),dp(3),dp(4),dp(3));
                row.addView(tile,p);
            }
        }
        if(scroll!=null)scroll.scrollTo(0,0);
    }
    void reset(boolean allApps){
        if(allApps)showApps();
        else showHome(true);
    }
    void resume(){
        active=true;
        generation++;
        if(showingApps)showApps();
        else{
            refreshChannels();
            fillApps();
            loadEpg();
            if(features.isEmpty())loadHero();
            else renderFeature();
        }
    }
    void pause(){
        active=false;
        generation++;
        epgRequestId++;
        epgLoading=false;
        if(heroLoading)lastHeroAttempt=0;
        heroRequestId++;
        heroLoading=false;
    }
    void destroy(){
        alive=false;
        active=false;
        generation++;
        epgRequestId++;
        heroRequestId++;
        heroLoading=false;
        io.shutdownNow();
    }
    boolean onKey(KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK&&showingApps){
            showHome(true);
            return true;
        }
        if(event.getKeyCode()==KeyEvent.KEYCODE_GUIDE){
            open(EpgActivity.class);
            return true;
        }
        return false;
    }
}
