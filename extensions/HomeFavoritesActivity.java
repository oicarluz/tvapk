package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Explicit TV-friendly editor for fixed channels and app shelf.
 *
 * Uses the existing HomeChannelPins and Prefs.setShelf. Does not collect TMDB
 * credentials or require READ/WRITE external storage permissions.
 */
public final class HomeFavoritesActivity extends Activity {
    private static final int BG=0xff090f1d, PANEL=0xff1c2a42, FOCUS=0xff355880;
    private static final int WHITE=0xfff6f8ff, MUTED=0xffb8c5db;
    private static final int ACCENT=0xff39c9ff;
    private LinearLayout rows;
    private ScrollView scroller;
    private final HashMap<String,View> focusTargets=new HashMap<>();

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,boolean focus){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(10));
        d.setStroke(dp(focus?2:1),focus?ACCENT:0xff304366);
        return d;
    }
    private TextView label(String value,int size,boolean strong){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextColor(WHITE);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }
    private Button button(String title,String key,Runnable action){
        Button b=new Button(this);
        b.setText(title);
        b.setTextColor(WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setPadding(dp(8),0,dp(8),0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setBackground(shape(PANEL,false));
        b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,focused)->b.setBackground(shape(focused?FOCUS:PANEL,focused)));
        if(key!=null)focusTargets.put(key,b);
        return b;
    }
    private void marginButton(LinearLayout row,Button b,int width){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(width),dp(37));
        lp.leftMargin=dp(7);
        row.addView(b,lp);
    }
    private void textHint(String hint){
        TextView t=label(hint,12,false);
        t.setTextColor(MUTED);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(32));
        rows.addView(t,lp);
    }

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20),dp(9),dp(20),dp(12));
        layout.setBackgroundColor(BG);
        setContentView(layout);
        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(nav,new LinearLayout.LayoutParams(-1,dp(57)));
        marginButton(nav,button("‹ Voltar",null,()->finish()),100);
        TextView heading=label("Personalizar Home",23,true);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-1,1);
        hp.leftMargin=dp(17);
        nav.addView(heading,hp);
        marginButton(nav,button("Agora na TV",null,()->startActivity(new Intent(this,EpgActivity.class))),140);

        LinearLayout tools=new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(tools,new LinearLayout.LayoutParams(-1,dp(57)));
        tools.addView(button("Copiar favoritos",null,()->exportFavorites()),
            new LinearLayout.LayoutParams(dp(179),dp(41)));
        marginButton(tools,button("Restaurar favoritos",null,()->importFavorites()),196);
        TextView note=label("Backup manual: canais e apps. Não inclui chave TMDB.",12,false);
        note.setTextColor(MUTED);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-1,1);
        np.leftMargin=dp(18);
        tools.addView(note,np);

        scroller=new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        rows=new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(rows);
        layout.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        TextView bottom=label("Use ↑ ↓ e OK para organizar. Os canais são fixados pelo OK longo em Agora na TV.",12,false);
        bottom.setTextColor(MUTED);
        layout.addView(bottom,new LinearLayout.LayoutParams(-1,dp(31)));
        render(null);
    }

    private void title(String value,String actionLabel,Runnable action){
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(49));
        lp.topMargin=dp(8);
        rows.addView(bar,lp);
        TextView header=label(value,18,true);
        bar.addView(header,new LinearLayout.LayoutParams(0,-1,1));
        if(action!=null){
            Button actionButton=button(actionLabel,null,action);
            bar.addView(actionButton,new LinearLayout.LayoutParams(dp(166),dp(38)));
        }
    }
    private void render(String wantedFocus){
        if(rows==null)return;
        final int previousScroll=scroller==null?0:scroller.getScrollY();
        focusTargets.clear();
        rows.removeAllViews();
        List<HomeChannelPins.Channel> channels=HomeChannelPins.list(this);
        title("Canais fixados ("+channels.size()+"/30)","Adicionar canais",
            ()->startActivity(new Intent(this,EpgActivity.class)));
        if(channels.isEmpty()){
            textHint("Nenhum canal fixado. Abra Agora na TV, selecione um canal e segure OK.");
        }else{
            for(int i=0;i<channels.size();i++){
                final int index=i;
                HomeChannelPins.Channel c=channels.get(i);
                LinearLayout row=new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(51));
                rp.bottomMargin=dp(4);
                rows.addView(row,rp);
                TextView name=label((i+1)+".  "+c.name,14,true);
                row.addView(name,new LinearLayout.LayoutParams(0,-1,1));
                Button up=button("↑","ch:"+c.key+":up",()->{
                    if(index>0&&HomeChannelPins.move(this,c.key,index-1)){
                        render("ch:"+c.key+":up");
                    }
                });
                marginButton(row,up,64);
                up.setEnabled(index>0);
                Button down=button("↓","ch:"+c.key+":down",()->{
                    if(index+1<channels.size()&&HomeChannelPins.move(this,c.key,index+1)){
                        render("ch:"+c.key+":down");
                    }
                });
                marginButton(row,down,64);
                down.setEnabled(index+1<channels.size());
                marginButton(row,button("Remover","ch:"+c.key+":remove",()->{
                    HomeChannelPins.unpin(this,c.key);
                    render(null);
                }),105);
            }
        }

        List<String> apps=new ArrayList<>(HomeApps.shelfPackages(this));
        title("Aplicativos fixados ("+apps.size()+")","Adicionar app",()->addApp());
        textHint("A ordem abaixo controla a faixa horizontal de apps da Home.");
        if(apps.isEmpty()){
            textHint("Nenhum app fixado. Use Adicionar app para escolher.");
        }
        for(int i=0;i<apps.size();i++){
            final int index=i;
            String pkg=apps.get(i);
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(51));
            rp.bottomMargin=dp(4);
            rows.addView(row,rp);
            TextView name=label((i+1)+".  "+appName(pkg),14,true);
            row.addView(name,new LinearLayout.LayoutParams(0,-1,1));
            Button up=button("↑","app:"+pkg+":up",()->{
                if(index>0){
                    ArrayList<String> next=new ArrayList<>(apps);
                    java.util.Collections.swap(next,index,index-1);
                    Prefs.setShelf(next);
                    render("app:"+pkg+":up");
                }
            });
            marginButton(row,up,64);
            up.setEnabled(index>0);
            Button down=button("↓","app:"+pkg+":down",()->{
                if(index+1<apps.size()){
                    ArrayList<String> next=new ArrayList<>(apps);
                    java.util.Collections.swap(next,index,index+1);
                    Prefs.setShelf(next);
                    render("app:"+pkg+":down");
                }
            });
            marginButton(row,down,64);
            down.setEnabled(index+1<apps.size());
            marginButton(row,button("Remover","app:"+pkg+":remove",()->{
                ArrayList<String> next=new ArrayList<>(apps);
                next.remove(pkg);
                Prefs.setShelf(next);
                render(null);
            }),105);
        }
        if(scroller!=null&&previousScroll>0)scroller.post(()->scroller.scrollTo(0,previousScroll));
        if(wantedFocus!=null){
            View focus=focusTargets.get(wantedFocus);
            if(focus!=null&&focus.isEnabled())focus.post(focus::requestFocus);
        }
    }
    private String appName(String pkg){
        try{
            android.content.pm.ApplicationInfo info=getPackageManager().getApplicationInfo(pkg,0);
            return String.valueOf(getPackageManager().getApplicationLabel(info));
        }catch(Exception ignored){return pkg;}
    }
    private void addApp(){
        List<String> pinned=HomeApps.shelfPackages(this);
        if(pinned.size()>=HomeApps.maxShelf(this)){
            Toast.makeText(this,"Essa TV atingiu o limite de apps fixados.",Toast.LENGTH_LONG).show();
            return;
        }
        HashSet<String> used=new HashSet<>(pinned);
        ArrayList<String> available=new ArrayList<>();
        ArrayList<String> labels=new ArrayList<>();
        for(String[] candidate:HomeApps.shelfCandidates(this)){
            String pkg=candidate[0];
            if(used.contains(pkg)||HomeApps.launchIntent(getPackageManager(),pkg)==null)continue;
            available.add(pkg);
            labels.add(candidate[1]);
            if(available.size()>=150)break;
        }
        if(available.isEmpty()){
            Toast.makeText(this,"Nenhum outro app disponível.",Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Fixar aplicativo na Home")
            .setItems(labels.toArray(new String[0]),(dialog,which)->{
                String pkg=available.get(which);
                ArrayList<String> next=new ArrayList<>(HomeApps.shelfPackages(this));
                if(!next.contains(pkg)&&next.size()<HomeApps.maxShelf(this)){
                    next.add(pkg);
                    Prefs.setShelf(next);
                    render("app:"+pkg+":remove");
                }
            }).setNegativeButton("Cancelar",null).show();
    }

    private void exportFavorites(){
        try{
            String backup=HomeFavoritesTransfer.export(this);
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard==null)throw new IllegalStateException("Área de transferência indisponível.");
            clipboard.setPrimaryClip(ClipData.newPlainText("Favoritos Free My TV",backup));
            Toast.makeText(this,"Favoritos copiados. Cole o código na próxima instalação.",Toast.LENGTH_LONG).show();
        }catch(Exception ex){
            Toast.makeText(this,"Falha ao copiar: "+ex.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void importFavorites(){
        EditText input=new EditText(this);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setHint("Cole aqui o código FREEMYTV-FAV-1");
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null){
            try{
                ClipData clip=clipboard.getPrimaryClip();
                if(clip!=null&&clip.getItemCount()>0){
                    CharSequence text=clip.getItemAt(0).coerceToText(this);
                    if(text!=null&&text.toString().startsWith("FREEMYTV-FAV-1:")){
                        input.setText(text);
                    }
                }
            }catch(Exception ignored){}
        }
        new AlertDialog.Builder(this)
            .setTitle("Restaurar canais e aplicativos")
            .setMessage("Use um código exportado pelo Free My TV a partir da Fase 6. Os favoritos atuais só serão substituídos depois da confirmação.")
            .setView(input)
            .setPositiveButton("Verificar",(dialog,which)->{
                try{
                    HomeFavoritesTransfer.Snapshot snapshot=HomeFavoritesTransfer.decode(
                        this,input.getText().toString());
                    new AlertDialog.Builder(this)
                        .setTitle("Confirmar restauração")
                        .setMessage("Substituir os favoritos atuais por "
                            +snapshot.channels.size()+" canais e "+snapshot.apps.size()
                            +" aplicativos?"+(snapshot.unavailableApps>0?
                                "\n"+snapshot.unavailableApps+" app(s) não instalado(s) serão ignorados.":"")
                            +"\nA chave TMDB não faz parte do backup.")
                        .setPositiveButton("Substituir",(confirm,w)->{
                            if(HomeFavoritesTransfer.restore(this,snapshot)){
                                render(null);
                                Toast.makeText(this,"Favoritos restaurados.",Toast.LENGTH_SHORT).show();
                            }else{
                                Toast.makeText(this,"Não foi possível salvar os canais.",Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("Cancelar",null).show();
                }catch(Exception ex){
                    new AlertDialog.Builder(this).setTitle("Backup inválido")
                        .setMessage(ex.getMessage())
                        .setPositiveButton("OK",null).show();
                }
            }).setNegativeButton("Cancelar",null).show();
    }

    @Override protected void onResume(){
        super.onResume();
        render(null);
    }
}
