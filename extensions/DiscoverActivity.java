package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate TV-first discovery screen. Never changes the HOME role or the existing launcher. */
public final class DiscoverActivity extends Activity {
    private static final String[] TABS={"Novidades","Descobrir","Para você","Minha Lista","Alertas","TV ao vivo"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private DiscoverData.Store store;
    private DiscoverData.Filter filter=new DiscoverData.Filter();
    private int tab=0, selection=0, request=0;
    private ArrayList<DiscoverData.Item> items=new ArrayList<>();
    private LinearLayout root, list, toolbar;
    private TextView heading, status;
    private boolean alive=true;
    private static final int BG=0xff101421, PANEL=0xff1b2334, FOCUS=0xff324b76, WHITE=0xfff4f7ff, MUTED=0xffb5c1d4;
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color);d.setCornerRadius(radius);return d;
    }
    private TextView label(String s,int size,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextColor(WHITE);v.setTextSize(size);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private Button button(String s,Runnable action){
        Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(WHITE);
        b.setBackground(bg(PANEL,12));b.setPadding(16,7,16,7);
        b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,focused)->b.setBackground(bg(focused?FOCUS:PANEL,12)));
        return b;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        store=new DiscoverData.Store(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setPadding(38,18,38,18);setContentView(root);
        heading=label("DESCOBRIR  ·  Free My TV",25,true);root.addView(heading);
        toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,56));
        LinearLayout filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(filters,new LinearLayout.LayoutParams(-1,55));
        addFilter(filters,"Tipo",()->choose("Tipo",new String[]{"Filmes e séries","Filmes","Séries"},filter.kind,x->{filter.kind=x;load();}));
        addFilter(filters,"Gênero",()->choose("Gênero",DiscoverData.GENRES,filter.genre,x->{filter.genre=x;load();}));
        addFilter(filters,"Período",()->choose("Novidades",new String[]{"7 dias","30 dias","90 dias"},filter.days==7?0:filter.days==30?1:2,x->{filter.days=x==0?7:x==1?30:90;load();}));
        addFilter(filters,"Nota",()->choose("Nota mínima",new String[]{"Todas","6+","7+","8+"},filter.rating==0?0:filter.rating-5,x->{filter.rating=x==0?0:x+5;load();}));
        addFilter(filters,"Busca",()->search());
        status=label("",13,false);status.setTextColor(MUTED);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=label("OK: detalhes  •  Segure OK: salvar  •  Menu: pesquisar  •  Voltar: início",12,false);
        hint.setTextColor(MUTED);root.addView(hint);
        showTabs();load();
    }
    private void addFilter(LinearLayout row,String name,Runnable r){
        Button b=button(name,r);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,47,1);p.setMargins(3,2,3,2);row.addView(b,p);
    }
    private void showTabs(){
        toolbar.removeAllViews();
        for(int i=0;i<TABS.length;i++){
            final int n=i;
            Button b=button((tab==i?"● ":"")+TABS[i],()->{if(n==5){startActivity(new Intent(this,EpgActivity.class));return;}tab=n;filter.query="";showTabs();load();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,50,1);p.setMargins(3,2,3,2);
            toolbar.addView(b,p);
        }
    }
    private interface Choice {void selected(int i);}
    private void choose(String title,String[] options,int selected,Choice choice){
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options,selected,(dialog,which)->{
            dialog.dismiss();choice.selected(which);
        }).setNegativeButton("Cancelar",null).show();
    }
    private void search(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Título de filme ou série");
        input.setText(filter.query);
        new AlertDialog.Builder(this).setTitle("Pesquisar no TMDB").setView(input)
            .setPositiveButton("Buscar",(d,w)->{filter.query=input.getText().toString().trim();load();})
            .setNeutralButton("Limpar",(d,w)->{filter.query="";load();})
            .setNegativeButton("Cancelar",null).show();
    }
    private void load(){
        final int ticket=++request;
        status.setText("Carregando "+TABS[tab]+"…");
        list.removeAllViews();
        final int current=tab;
        final DiscoverData.Filter copy=new DiscoverData.Filter();
        copy.kind=filter.kind;copy.genre=filter.genre;copy.days=filter.days;copy.rating=filter.rating;copy.query=filter.query;
        final ArrayList<DiscoverData.Item> favorites=store.all();
        worker.execute(()->{
            try {
                ArrayList<DiscoverData.Item> result=DiscoverData.browse(TABS[current],copy,favorites);
                main.post(()->{if(alive&&ticket==request){items=result;render();}});
            } catch(Exception ex){
                String msg=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                main.post(()->{if(alive&&ticket==request)status.setText("Não foi possível carregar: "+msg);});
            }
        });
    }
    private void render(){
        list.removeAllViews();selection=0;
        status.setText(items.size()+" títulos  ·  "+TABS[tab]+(filter.query.isEmpty()?"":"  ·  "+filter.query));
        if(items.isEmpty()){list.addView(label("Nenhum título encontrado. Altere os filtros ou cadastre sua chave TMDB.",17,false));return;}
        int n=0;
        for(DiscoverData.Item it:items){
            final int index=n++;
            String date=it.date.length()>=4?it.date.substring(0,4):"";
            String line=(it.show()?"SÉRIE":"FILME")+"   "+date+"   ★ "+String.format(java.util.Locale.US,"%.1f",it.rating);
            if(!it.state.isEmpty())line+="   ·   "+it.state;
            if(!it.nextEpisode.isEmpty())line+="   ·   "+it.nextEpisode;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(17,10,17,10);card.setBackground(bg(PANEL,10));
            TextView title=label(it.title,18,true);card.addView(title);
            TextView subtitle=label(line,12,false);subtitle.setTextColor(MUTED);card.addView(subtitle);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,3,0,3);
            list.addView(card,p);card.setFocusable(true);
            card.setOnFocusChangeListener((v,focused)->{
                card.setBackground(bg(focused?FOCUS:PANEL,10));
                if(focused)selection=index;
            });
            card.setOnClickListener(v->details(it));
            card.setOnLongClickListener(v->{saveMenu(it);return true;});
            if(index==0)card.requestFocus();
        }
    }
    private void saveMenu(DiscoverData.Item it){
        String[] opts={"Quero assistir","Assistindo","Já assisti","Remover da lista"};
        new AlertDialog.Builder(this).setTitle(it.title).setItems(opts,(d,which)->{
            if(which==3)store.remove(it);else store.save(it,DiscoverData.STATES[which]);
            Toast.makeText(this,"Minha Lista atualizada",Toast.LENGTH_SHORT).show();
            if(tab==3||tab==4||tab==2)load();
        }).show();
    }
    private void details(DiscoverData.Item it){
        String body=(it.show()?"Série":"Filme")+"  ·  "+it.date+"  ·  ★ "+String.format(java.util.Locale.US,"%.1f",it.rating)
            +"\n\n"+it.overview+(it.nextEpisode.isEmpty()?"":"\n\n"+it.nextEpisode);
        String[] actions={"Pesquisar no UniTVnet","Ver trailer","Minha Lista","Marcar temporada vista"};
        new AlertDialog.Builder(this).setTitle(it.title).setMessage(body)
            .setItems(actions,(d,which)->{
                if(which==0)openUni(it);
                else if(which==1)trailer(it);
                else if(which==2)saveMenu(it);
                else if(which==3&&it.show()){store.seenSeason(it);Toast.makeText(this,"Temporada registrada",Toast.LENGTH_SHORT).show();}
            }).setNegativeButton("Fechar",null).show();
    }
    private void openUni(DiscoverData.Item it){
        Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage(DiscoverData.PKG);
        if(launch==null)launch=getPackageManager().getLaunchIntentForPackage(DiscoverData.PKG);
        if(launch==null){Toast.makeText(this,"UniTVnet não está instalado ou visível",Toast.LENGTH_LONG).show();return;}
        PendingUniSearch.remember(this,it.title);
        try{startActivity(launch);}catch(Exception ex){Toast.makeText(this,"Não foi possível abrir o UniTVnet",Toast.LENGTH_LONG).show();}
    }
    private void trailer(DiscoverData.Item it){
        status.setText("Procurando trailer de "+it.title+"…");
        worker.execute(()->{
            try{
                String id=DiscoverData.trailer(it);
                main.post(()->{
                    if(!alive)return;
                    if(id.isEmpty()){Toast.makeText(this,"Trailer indisponível no TMDB",Toast.LENGTH_LONG).show();return;}
                    Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+id));
                    try{startActivity(intent);}catch(Exception ex){Toast.makeText(this,"Instale um aplicativo capaz de abrir YouTube",Toast.LENGTH_LONG).show();}
                });
            }catch(Exception ex){main.post(()->{if(alive)Toast.makeText(this,"Falha ao carregar trailer",Toast.LENGTH_SHORT).show();});}
        });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_DOWN && e.getKeyCode()==KeyEvent.KEYCODE_MENU){search();return true;}
        if(e.getAction()==KeyEvent.ACTION_DOWN && e.getKeyCode()==KeyEvent.KEYCODE_INFO && !items.isEmpty()){
            saveMenu(items.get(Math.max(0,Math.min(selection,items.size()-1))));return true;
        }
        return super.dispatchKeyEvent(e);
    }
    @Override protected void onDestroy(){alive=false;request++;worker.shutdownNow();super.onDestroy();}
}
