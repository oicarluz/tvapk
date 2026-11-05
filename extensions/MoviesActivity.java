package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Android TV movie catalog. Search, browse by genre and progressively
 * fetch TMDB pages as the user approaches the bottom of the poster grid.
 *
 * OK only copies the selected title to clipboard and launches UniTVnet. This
 * does not control UniTVnet's playback and requires no accessibility service.
 */
public final class MoviesActivity extends Activity {
    private static final int BG=0xff090f1d;
    private static final int PANEL=0xff172138;
    private static final int CARD=0xff1f2b44;
    private static final int FOCUS=0xff395989;
    private static final int WHITE=0xfff6f8ff;
    private static final int MUTED=0xffb8c5db;
    private static final int ACCENT=0xff39c9ff;

    // TMDB movie-genre IDs: https://developer.themoviedb.org/reference/genre-movie-list
    private static final String[] GENRES={
        "Todos","Ação","Aventura","Animação","Comédia","Crime","Documentário",
        "Drama","Família","Fantasia","História","Terror","Música","Mistério",
        "Romance","Ficção científica","Suspense","Guerra","Faroeste"
    };
    private static final int[] GENRE_IDS={
        0,28,12,16,35,80,99,18,10751,14,36,27,10402,9648,10749,878,53,10752,37
    };
    private static final String[] SORTS={"Populares","Bem avaliados","Mais recentes"};

    private final ExecutorService pages=Executors.newSingleThreadExecutor();
    private final ExecutorService artwork=Executors.newFixedThreadPool(3);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayList<DiscoverData.Item> movies=new ArrayList<>();
    private final HashSet<Integer> ids=new HashSet<>();
    private final LinkedHashMap<String,Bitmap> memory=new LinkedHashMap<String,Bitmap>(56,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> entry){return size()>45;}
    };

    private GridView grid;
    private MovieAdapter adapter;
    private HorizontalScrollView genreScroll;
    private LinearLayout genreRow,root;
    private TextView status;
    private Button retry;
    private ProgressBar loadingView;
    private int genre=0,sort=0,nextPage=1,generation=0;
    private boolean loading=false,hasMore=true,alive=true;
    private String query="";

    private int dp(float size){return Math.round(getResources().getDisplayMetrics().density*size);}
    private GradientDrawable rounded(int color,boolean focused){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(focused?2:1),focused?ACCENT:0xff304366);
        return shape;
    }
    private TextView label(String value,int size,boolean bold){
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(size);
        v.setTextColor(WHITE);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }
    private Button actionButton(String value,Runnable run){
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(WHITE);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10),0,dp(10),0);
        button.setBackground(rounded(PANEL,false));
        button.setOnFocusChangeListener((v,focus)->button.setBackground(rounded(focus?FOCUS:PANEL,focus)));
        button.setOnClickListener(v->run.run());
        return button;
    }

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(17),dp(7),dp(17),dp(10));
        root.setBackgroundColor(BG);
        setContentView(root);
        buildNavigation();

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView title=label("Filmes",23,true);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        Button sorting=actionButton("Ordenar: Populares ▾",()->chooseSort());
        sorting.setTag("sort");
        titleRow.addView(sorting,new LinearLayout.LayoutParams(dp(192),dp(40)));
        Button search=actionButton("⌕  Buscar",()->search());
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(dp(125),dp(40));
        searchLp.leftMargin=dp(8);
        titleRow.addView(search,searchLp);

        genreScroll=new HorizontalScrollView(this);
        genreScroll.setHorizontalScrollBarEnabled(false);
        genreRow=new LinearLayout(this);
        genreRow.setGravity(Gravity.CENTER_VERTICAL);
        genreScroll.addView(genreRow);
        root.addView(genreScroll,new LinearLayout.LayoutParams(-1,dp(51)));
        buildGenres();

        LinearLayout controls=new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(controls,new LinearLayout.LayoutParams(-1,dp(31)));
        status=label("Preparando catálogo…",12,false);
        status.setTextColor(MUTED);
        controls.addView(status,new LinearLayout.LayoutParams(0,-1,1));
        loadingView=new ProgressBar(this);
        loadingView.setVisibility(View.GONE);
        controls.addView(loadingView,new LinearLayout.LayoutParams(dp(28),dp(28)));
        retry=actionButton("Tentar novamente",()->loadPage());
        retry.setVisibility(View.GONE);
        LinearLayout.LayoutParams retryLp=new LinearLayout.LayoutParams(dp(155),dp(30));
        retryLp.leftMargin=dp(8);
        controls.addView(retry,retryLp);

        grid=new GridView(this);
        int available=getResources().getDisplayMetrics().widthPixels-dp(50);
        int columns=Math.max(3,Math.min(7,available/Math.max(1,dp(180))));
        grid.setNumColumns(columns);
        grid.setHorizontalSpacing(dp(11));
        grid.setVerticalSpacing(dp(12));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);
        grid.setPadding(dp(3),dp(5),dp(3),dp(8));
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setFocusable(true);
        adapter=new MovieAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent,view,position,id)->{
            if(position>=0&&position<movies.size())openInUni(movies.get(position));
        });
        grid.setOnScrollListener(new AbsListView.OnScrollListener(){
            @Override public void onScrollStateChanged(AbsListView view,int state){}
            @Override public void onScroll(AbsListView view,int first,int visible,int total){
                if(total>0&&first+visible>=total-8)loadPage();
            }
        });
        grid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position>=movies.size()-8)loadPage();
            }
        });
        root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));

        TextView footer=label("OK: copiar título e abrir UniTVnet   ·   ↓: carregar mais filmes   ·   ←→: navegar   ·   Voltar: Início",11,false);
        footer.setTextColor(MUTED);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(27)));

        resetCatalog();
        // Initial focus stays in the filters; once posters arrive the remote
        // can navigate down naturally without jumping unexpectedly.
        if(genreRow.getChildCount()>0)genreRow.getChildAt(0).post(()->{
            if(alive&&genreRow.getChildCount()>0)genreRow.getChildAt(0).requestFocus();
        });
    }

    private void buildNavigation(){
        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView brand=label("▸  Free My TV",19,true);
        brand.setTextColor(ACCENT);
        nav.addView(brand,new LinearLayout.LayoutParams(dp(160),-1));
        String[] tabs={"Início","Agora na TV","Filmes","Séries","Apps"};
        for(int i=0;i<tabs.length;i++){
            final int index=i;
            Button b=actionButton(tabs[i],()->navigate(index));
            if(i==2)b.setBackground(rounded(0xff284775,false));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1);
            lp.setMargins(dp(3),0,dp(3),0);
            nav.addView(b,lp);
        }
        Button settings=actionButton("⚙",()->startActivity(new Intent(this,SettingsActivity.class)));
        nav.addView(settings,new LinearLayout.LayoutParams(dp(56),dp(38)));
    }

    private void navigate(int destination){
        if(destination==2)return;
        if(destination==0||destination==4){
            Intent intent=new Intent(this,HomeActivity.class);
            if(destination==4)intent.putExtra(HomeActivity.EXTRA_ALL_APPS,true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }else if(destination==1){
            startActivity(new Intent(this,EpgActivity.class));
        }else{
            Intent intent=new Intent(this,DiscoverActivity.class);
            intent.putExtra("initial_kind",2);
            startActivity(intent);
        }
        finish();
    }

    private void buildGenres(){
        genreRow.removeAllViews();
        for(int i=0;i<GENRES.length;i++){
            final int current=i;
            Button b=actionButton(GENRES[i],()->{
                if(genre==current)return;
                genre=current;
                query="";
                resetCatalog();
                updateGenres();
                if(grid!=null)grid.setSelection(0);
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(Math.max(81,GENRES[i].length()*8+30)),dp(38));
            lp.setMargins(0,0,dp(7),0);
            genreRow.addView(b,lp);
        }
        updateGenres();
    }
    private void updateGenres(){
        for(int i=0;i<genreRow.getChildCount();i++){
            View chip=genreRow.getChildAt(i);
            if(!chip.isFocused())chip.setBackground(rounded(i==genre?0xff284775:PANEL,false));
        }
    }
    private void chooseSort(){
        new AlertDialog.Builder(this).setTitle("Organizar filmes")
            .setSingleChoiceItems(SORTS,sort,(dialog,which)->{
                dialog.dismiss();
                sort=which;
                query="";
                resetCatalog();
            }).setNegativeButton("Cancelar",null).show();
    }
    private void search(){
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setText(query);
        field.setHint("Buscar pelo nome do filme");
        new AlertDialog.Builder(this).setTitle("Pesquisar filmes no TMDB")
            .setView(field)
            .setPositiveButton("Buscar",(dialog,which)->{
                query=field.getText().toString().trim();
                resetCatalog();
            }).setNeutralButton("Limpar",(dialog,which)->{
                query="";resetCatalog();
            }).setNegativeButton("Cancelar",null).show();
    }

    private void resetCatalog(){
        generation++;
        movies.clear();
        ids.clear();
        nextPage=1;
        hasMore=true;
        loading=false;
        if(adapter!=null)adapter.notifyDataSetChanged();
        if(grid!=null)grid.setSelection(0);
        if(retry!=null)retry.setVisibility(View.GONE);
        status.setText(query.isEmpty()?("Filmes · "+GENRES[genre]):("Busca: "+query));
        loadPage();
    }

    private String queryPath(int page) throws Exception{
        if(!query.isEmpty())
            return "/search/movie?language=pt-BR&include_adult=false&query="
                +DiscoverData.enc(query)+"&page="+page;
        String sortKey=sort==1?"vote_average.desc":sort==2?"primary_release_date.desc":"popularity.desc";
        String path="/discover/movie?language=pt-BR&include_adult=false&include_video=false"
            +"&sort_by="+sortKey+"&page="+page;
        if(genre!=0)path+="&with_genres="+GENRE_IDS[genre];
        if(sort==1)path+="&vote_count.gte=150";
        if(sort==2)path+="&primary_release_date.lte="+DiscoverData.day(0);
        return path;
    }

    private void loadPage(){
        if(!alive||loading||!hasMore)return;
        if(Recs.key()==null||Recs.key().isEmpty()){
            hasMore=false;
            status.setText("Cadastre sua chave TMDB nos Ajustes do Free My TV para carregar filmes.");
            retry.setVisibility(View.VISIBLE);
            retry.setText("Configurar TMDB");
            retry.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
            return;
        }
        loading=true;
        final int ticket=generation;
        final int page=nextPage;
        final String path;
        try{path=queryPath(page);}
        catch(Exception ex){loading=false;status.setText("Busca inválida.");return;}
        loadingView.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        status.setText(movies.isEmpty()?"Buscando filmes…":movies.size()+" filmes · carregando mais…");
        pages.execute(()->{
            ArrayList<DiscoverData.Item> batch=new ArrayList<>();
            int lastPage=page;
            String error=null;
            try{
                JSONObject response=DiscoverData.get(path);
                lastPage=Math.min(500,Math.max(1,response.optInt("total_pages",1)));
                batch=DiscoverData.from(response,"movie");
            }catch(Exception ex){
                error=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
            }
            final ArrayList<DiscoverData.Item> result=batch;
            final int endPage=lastPage;
            final String failure=error;
            ui.post(()->{
                if(!alive||ticket!=generation)return;
                loading=false;
                loadingView.setVisibility(View.GONE);
                if(failure!=null){
                    status.setText("Não foi possível carregar: "+failure);
                    retry.setVisibility(View.VISIBLE);
                    retry.setText("Tentar novamente");
                    retry.setOnClickListener(v->loadPage());
                    return;
                }
                int before=movies.size();
                for(DiscoverData.Item item:result){
                    if(item.id>0&&ids.add(item.id))movies.add(item);
                }
                if(page>=endPage||result.isEmpty())hasMore=false;
                else nextPage=page+1;
                adapter.notifyDataSetChanged();
                status.setText(movies.size()+" filmes  ·  "+GENRES[genre]
                    +(query.isEmpty()?"":"  ·  "+query)
                    +(hasMore?"  ·  desça para carregar mais":"  ·  fim dos resultados"));
                if(before==0&&!movies.isEmpty()&&!genreRow.hasFocus()&&!grid.hasFocus()){
                    grid.requestFocus();
                }
            });
        });
    }

    private void openInUni(DiscoverData.Item item){
        // Use the existing, explicitly requested copy-to-clipboard helper.
        PendingUniSearch.remember(this,item.title);
        Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(DiscoverData.PKG);
        if(intent==null)intent=getPackageManager().getLaunchIntentForPackage(DiscoverData.PKG);
        if(intent==null){
            Toast.makeText(this,"Título copiado. UniTVnet não encontrado na TV.",Toast.LENGTH_LONG).show();
            return;
        }
        try{
            Toast.makeText(this,"Título copiado: "+item.title,Toast.LENGTH_SHORT).show();
            startActivity(intent);
        }catch(Exception ignored){
            Toast.makeText(this,"Título copiado, mas não foi possível abrir UniTVnet.",Toast.LENGTH_LONG).show();
        }
    }

    private void loadPoster(String rawPath,ImageView image){
        if(rawPath==null||!rawPath.startsWith("/")||rawPath.length()>180){
            image.setImageDrawable(null);image.setTag(null);return;
        }
        final String url="https://image.tmdb.org/t/p/w185"+rawPath;
        image.setTag(url);
        synchronized(memory){
            Bitmap cached=memory.get(url);
            if(cached!=null){image.setImageBitmap(cached);return;}
        }
        image.setImageDrawable(null);
        artwork.execute(()->{
            Bitmap bitmap=null;
            byte[] data=HomeImageCache.read(this,url);
            if(data==null){
                HttpURLConnection connection=null;
                try{
                    connection=(HttpURLConnection)new URL(url).openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(7000);
                    if(connection.getResponseCode()==200){
                        try(InputStream in=connection.getInputStream();
                            ByteArrayOutputStream out=new ByteArrayOutputStream()){
                            byte[] buf=new byte[4096];int n,total=0;
                            while((n=in.read(buf))!=-1){
                                total+=n;
                                if(total>360_000)break;
                                out.write(buf,0,n);
                            }
                            if(total>0&&total<=360_000)data=out.toByteArray();
                        }
                    }
                }catch(Exception ignored){}
                finally{if(connection!=null)connection.disconnect();}
            }
            if(data!=null){
                try{
                    BitmapFactory.Options options=new BitmapFactory.Options();
                    options.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(data,0,data.length,options);
                    options.inSampleSize=1;
                    while(options.outWidth/options.inSampleSize>200
                        ||options.outHeight/options.inSampleSize>300)options.inSampleSize*=2;
                    options.inJustDecodeBounds=false;
                    bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);
                    if(bitmap!=null)HomeImageCache.write(this,url,data);
                }catch(Exception ignored){}
            }
            final Bitmap loaded=bitmap;
            if(loaded!=null){
                synchronized(memory){memory.put(url,loaded);}
                ui.post(()->{
                    if(alive&&url.equals(image.getTag()))image.setImageBitmap(loaded);
                });
            }
        });
    }

    private final class MovieAdapter extends BaseAdapter {
        @Override public int getCount(){return movies.size();}
        @Override public Object getItem(int p){return movies.get(p);}
        @Override public long getItemId(int p){return movies.get(p).id;}
        @Override public boolean hasStableIds(){return true;}
        @Override public View getView(int position,View reused,android.view.ViewGroup parent){
            final Holder holder;
            final LinearLayout card;
            if(reused instanceof LinearLayout&&reused.getTag() instanceof Holder){
                card=(LinearLayout)reused;
                holder=(Holder)card.getTag();
            }else{
                card=new LinearLayout(MoviesActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(7),dp(7),dp(7),dp(7));
                card.setBackground(rounded(CARD,false));
                card.setFocusable(false);
                ImageView poster=new ImageView(MoviesActivity.this);
                poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                poster.setBackground(rounded(0xff29334b,false));
                card.addView(poster,new LinearLayout.LayoutParams(-1,dp(176)));
                TextView title=label("",13,true);
                title.setMaxLines(2);
                LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,dp(39));
                tl.topMargin=dp(4);card.addView(title,tl);
                TextView meta=label("",11,false);
                meta.setTextColor(MUTED);
                card.addView(meta,new LinearLayout.LayoutParams(-1,dp(23)));
                holder=new Holder(poster,title,meta);
                card.setTag(holder);
                card.setOnFocusChangeListener((view,focused)->{
                    card.setBackground(rounded(focused?FOCUS:CARD,focused));
                });
            }
            DiscoverData.Item item=movies.get(position);
            holder.title.setText(item.title);
            String year=item.date.length()>=4?item.date.substring(0,4):"";
            String rating=item.rating>0?String.format(Locale.US,"★ %.1f",item.rating):"";
            holder.meta.setText(year+(rating.isEmpty()?"":"   "+rating));
            loadPoster(item.poster,holder.poster);
            return card;
        }
    }
    private static final class Holder{
        final ImageView poster;
        final TextView title,meta;
        Holder(ImageView p,TextView t,TextView m){poster=p;title=t;meta=m;}
    }

    @Override protected void onResume(){
        super.onResume();
        // A TMDB key entered in Settings should work without reopening Movies.
        if(!loading&&movies.isEmpty()&&Recs.key()!=null&&!Recs.key().isEmpty()){
            hasMore=true;loadPage();
        }
    }
    @Override protected void onDestroy(){
        alive=false;generation++;
        pages.shutdownNow();
        artwork.shutdownNow();
        super.onDestroy();
    }
}
