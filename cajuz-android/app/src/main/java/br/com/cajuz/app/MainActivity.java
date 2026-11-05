package br.com.cajuz.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
  private static final int FILE_PICKER = 9124;
  private static final int BG = Color.rgb(16, 19, 21);
  private static final int GREEN = Color.rgb(15, 158, 107);
  private WebView web;
  private ProgressBar progress;
  private ValueCallback<Uri[]> files;
  private String startUrl = "";
  private String host = "";
  private static final String LOCK_PREF = "lock_enabled";
  private CajuzGuard guard;
  private FrameLayout appFrame;
  private View cover;
  private View offlineView;
  private FrameLayout webFrame;
  private boolean locked=false;
  private boolean lockEnabled(){return getPreferences(MODE_PRIVATE).getBoolean(LOCK_PREF,false);}


  private int px(int dp) { return (int)(dp * getResources().getDisplayMetrics().density + .5f); }
  private TextView label(String value, int size, int color) {
    TextView t=new TextView(this); t.setText(value); t.setTextColor(color); t.setTextSize(size);
    t.setGravity(Gravity.CENTER_VERTICAL); return t;
  }
  private LinearLayout stack() { LinearLayout l=new LinearLayout(this); l.setOrientation(1); return l; }
  private LinearLayout.LayoutParams size(int width, int height) { return new LinearLayout.LayoutParams(width,height); }

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    guard=new CajuzGuard(this);
    startUrl=getPreferences(MODE_PRIVATE).getString("site_url","");
    if(startUrl.isEmpty()) setup();
    else if(lockEnabled()){ locked=true; showLockedStart(); }
    else browser();
  }
  private void setup() {
    if(web!=null) { web.destroy(); web=null; }
    appFrame=null; cover=null; offlineView=null; webFrame=null;
    ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
    LinearLayout body=stack(); body.setPadding(px(26),px(65),px(26),px(26)); scroll.addView(body);
    TextView logo=label("C",43,Color.WHITE); logo.setGravity(Gravity.CENTER);
    logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD); logo.setBackgroundColor(GREEN);
    LinearLayout.LayoutParams p=size(px(76),px(76)); p.bottomMargin=px(28); body.addView(logo,p);
    TextView title=label("Cajuz",32,Color.WHITE); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); body.addView(title);
    TextView intro=label("Seu workspace, onde você estiver.",16,0xFFB8BFB9);
    p=size(-1,-2); p.topMargin=px(8); p.bottomMargin=px(42); body.addView(intro,p);
    TextView caption=label("ENDEREÇO DA SUA INSTALAÇÃO",12,0xFF9EB1A8);
    caption.setTypeface(Typeface.DEFAULT,Typeface.BOLD); body.addView(caption);
    EditText field=new EditText(this); field.setTextSize(15); field.setTextColor(Color.WHITE);
    field.setHintTextColor(0xFF7D8882); field.setSingleLine(true);
    field.setHint("https://seusite.com.br");
    field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
    p=size(-1,px(64)); p.topMargin=px(14); body.addView(field,p);
    TextView help=label("Informe a URL HTTPS do WordPress no qual o Cajuz está instalado. O login acontece no próprio site.",13,0xFFA2ADA9);
    p=size(-1,-2); p.topMargin=px(8); p.bottomMargin=px(28); body.addView(help,p);
    Button go=new Button(this); go.setAllCaps(false); go.setText("Conectar ao Cajuz");
    go.setTextColor(Color.WHITE); go.setBackgroundTintList(ColorStateList.valueOf(GREEN));
    body.addView(go,size(-1,px(54)));
    go.setOnClickListener(v->{
      String value=field.getText().toString().trim();
      if(!value.startsWith("https://")) value="https://"+value;
      try {
        Uri uri=Uri.parse(value);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||!uri.getHost().contains(".")||uri.getUserInfo()!=null) throw new IllegalArgumentException();
        if(uri.getPort()!=-1&&uri.getPort()!=443) throw new IllegalArgumentException();
        String path=uri.getPath()==null?"/":uri.getPath();
        if(path.isEmpty()||path.endsWith("/")) value=uri.buildUpon().clearQuery().fragment(null).path((path.isEmpty()?"/":path)+"wp-admin/").build().toString();
        startUrl=value;
        getPreferences(MODE_PRIVATE).edit().putString("site_url",startUrl).apply();
        browser();
      } catch(Exception e) { field.setError("Digite um endereço HTTPS válido."); }
    });
    setContentView(scroll);
  }
  private void browser() {
    host=Uri.parse(startUrl).getHost().toLowerCase(java.util.Locale.ROOT);
    LinearLayout root=stack(); root.setBackgroundColor(Color.WHITE);
    LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(px(16),0,px(10),0); top.setBackgroundColor(BG);
    TextView heading=label("Cajuz",19,Color.WHITE); heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    top.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
    TextView reload=label("↻",30,Color.WHITE); reload.setGravity(Gravity.CENTER);
    top.addView(reload,size(px(46),-1)); reload.setOnClickListener(v->{if(web!=null)web.reload();});
    TextView more=label("⋮",26,Color.WHITE); more.setGravity(Gravity.CENTER);
    top.addView(more,size(px(46),-1));
    more.setOnClickListener(v->new AlertDialog.Builder(this)
      .setItems(new String[]{"Atualizar página","Compartilhar endereço","Copiar endereço","Abrir no navegador",
        lockEnabled()?"Desativar bloqueio do app":"Ativar biometria / PIN",
        "Encerrar sessão neste dispositivo","Trocar instalação"},(d,choice)->{
        switch(choice){
          case 0: if(web!=null)web.reload(); break;
          case 1: shareCurrent(); break;
          case 2: copyCurrent(); break;
          case 3: external(currentAddress()); break;
          case 4: changeLock(); break;
          case 5: signOut(); break;
          case 6: new AlertDialog.Builder(this).setTitle("Trocar instalação")
            .setMessage("Conectar a outro WordPress? A sessão local será encerrada, mas os dados permanecerão no servidor.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Trocar",(dd,ww)->{
              clearLocalSession(()->{getPreferences(MODE_PRIVATE).edit().remove("site_url").apply();startUrl="";locked=false;setup();});
            }).show(); break;
        }
      }).show());
    root.addView(top,size(-1,px(48)));
    progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100); progress.setVisibility(View.GONE);
    progress.setProgressTintList(ColorStateList.valueOf(GREEN));
    root.addView(progress,size(-1,px(3)));
    FrameLayout frame=new FrameLayout(this);
    webFrame=frame;
    root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
    web=new WebView(this); frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
    android.webkit.WebSettings settings=web.getSettings();
    settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false); settings.setAllowContentAccess(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
    settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false);
    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
        if(!request.isForMainFrame())return false;
        Uri u=request.getUrl();
        String destination=u.getHost()==null?"":u.getHost().toLowerCase(java.util.Locale.ROOT);
        if("https".equalsIgnoreCase(u.getScheme())&&destination.equals(host))return false;
        String scheme=u.getScheme();
        if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)||"mailto".equalsIgnoreCase(scheme)||"tel".equalsIgnoreCase(scheme))external(u.toString());
        return true;
      }
      @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,SslError error){ handler.cancel();Toast.makeText(MainActivity.this,"Certificado HTTPS inválido.",Toast.LENGTH_LONG).show(); }
      @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){hideOffline();}
      @Override public void onPageCommitVisible(WebView view,String url){hideOffline();}
      @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){
        if(r.isForMainFrame())showOffline();
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public void onProgressChanged(WebView v,int n){ progress.setVisibility(n<100?View.VISIBLE:View.GONE);progress.setProgress(n); }
      @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> result,FileChooserParams params){
        if(files!=null)files.onReceiveValue(null); files=result;
        try { startActivityForResult(params.createIntent(),FILE_PICKER);return true; }
        catch(ActivityNotFoundException e){files=null;result.onReceiveValue(null);return false;}
      }
    });
    web.setDownloadListener((url,agent,disposition,mimetype,length)->{
      try {
        if(!"https".equalsIgnoreCase(Uri.parse(url).getScheme()))throw new IllegalArgumentException();
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));
        String filename=URLUtil.guessFileName(url,disposition,mimetype);
        r.setTitle(filename);r.setMimeType(mimetype);
        String cookie=CookieManager.getInstance().getCookie(url);
        if(cookie!=null)r.addRequestHeader("Cookie",cookie);
        if(agent!=null)r.addRequestHeader("User-Agent",agent);
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,filename);
        ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
        Toast.makeText(this,"Download iniciado.",Toast.LENGTH_SHORT).show();
      } catch(Exception e){external(url);}
    });
    appFrame=new FrameLayout(this);
    appFrame.addView(root,new FrameLayout.LayoutParams(-1,-1));
    setContentView(appFrame);
    if(locked)showCover();
    web.loadUrl(startUrl);
  }

  private void showLockedStart(){
    appFrame=new FrameLayout(this);appFrame.setBackgroundColor(BG);
    setContentView(appFrame);
    showCover();
  }
  private void showCover(){
    if(appFrame==null||cover!=null)return;
    LinearLayout panel=stack();panel.setBackgroundColor(BG);
    panel.setGravity(Gravity.CENTER);
    panel.setPadding(px(30),px(28),px(30),px(28));
    TextView emblem=label("C",46,Color.WHITE);
    emblem.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    emblem.setGravity(Gravity.CENTER);emblem.setBackgroundColor(GREEN);
    LinearLayout.LayoutParams el=size(px(78),px(78));
    el.bottomMargin=px(24);panel.addView(emblem,el);
    TextView heading=label("Cajuz protegido",24,Color.WHITE);
    heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    heading.setGravity(Gravity.CENTER);
    panel.addView(heading,size(-1,px(52)));
    TextView note=label("Use a biometria ou o bloqueio do dispositivo para continuar.",15,0xFFBECAC2);
    note.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams nl=size(-1,px(82));
    nl.bottomMargin=px(8);panel.addView(note,nl);
    Button unlock=new Button(this);unlock.setAllCaps(false);
    unlock.setText("Desbloquear");
    unlock.setTextColor(Color.WHITE);
    unlock.setBackgroundTintList(ColorStateList.valueOf(GREEN));
    panel.addView(unlock,size(-1,px(54)));
    unlock.setOnClickListener(v->guard.verify(()->{
      locked=false;hideCover();
      if(web==null)browser();
    }));
    cover=panel;
    appFrame.addView(panel,new FrameLayout.LayoutParams(-1,-1));
  }
  private void hideCover(){
    if(appFrame!=null&&cover!=null)appFrame.removeView(cover);
    cover=null;
  }
  private void changeLock(){
    boolean enabled=lockEnabled();
    guard.verify(()->{
      getPreferences(MODE_PRIVATE).edit().putBoolean(LOCK_PREF,!enabled).apply();
      if(enabled){locked=false;hideCover();}
      Toast.makeText(this,enabled?"Bloqueio desativado.":"Bloqueio ativado.",Toast.LENGTH_SHORT).show();
    });
  }


  private String currentAddress(){
    String value=web==null?startUrl:web.getUrl();
    return value==null||value.isEmpty()?startUrl:value;
  }
  private String addressForSharing(){
    try{return Uri.parse(currentAddress()).buildUpon().clearQuery().fragment(null).build().toString();}
    catch(Exception e){return startUrl;}
  }
  private void shareCurrent(){
    Intent share=new Intent(Intent.ACTION_SEND);
    share.setType("text/plain");
    share.putExtra(Intent.EXTRA_TEXT,addressForSharing());
    try{startActivity(Intent.createChooser(share,"Compartilhar endereço do Cajuz"));}
    catch(Exception e){Toast.makeText(this,"Compartilhamento indisponível.",Toast.LENGTH_SHORT).show();}
  }
  private void copyCurrent(){
    ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("Link Cajuz",addressForSharing()));
    Toast.makeText(this,"Endereço copiado.",Toast.LENGTH_SHORT).show();
  }
  private void clearLocalSession(Runnable done){
    CookieManager.getInstance().removeAllCookies(value->{
      CookieManager.getInstance().flush();
      WebStorage.getInstance().deleteAllData();
      if(web!=null){web.clearCache(true);web.clearHistory();}
      if(done!=null)done.run();
    });
  }
  private void signOut(){
    new AlertDialog.Builder(this).setTitle("Encerrar sessão?")
      .setMessage("Os cookies e dados locais serão apagados. Isso não exclui sua conta ou os dados no WordPress.")
      .setNegativeButton("Cancelar",null)
      .setPositiveButton("Encerrar",(d,w)->clearLocalSession(()->{if(web!=null)web.loadUrl(startUrl);}))
      .show();
  }
  private void showOffline(){
    if(webFrame==null||offlineView!=null)return;
    LinearLayout panel=stack();panel.setGravity(Gravity.CENTER);
    panel.setPadding(px(26),px(26),px(26),px(26));
    panel.setBackgroundColor(0xFFF7F9F8);
    TextView title=label("Não foi possível conectar",22,BG);
    title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    title.setGravity(Gravity.CENTER);panel.addView(title,size(-1,px(56)));
    TextView info=label("Confira sua conexão com a internet e o endereço do servidor Cajuz.",15,0xFF536159);
    info.setGravity(Gravity.CENTER);panel.addView(info,size(-1,px(88)));
    Button retry=new Button(this);retry.setAllCaps(false);retry.setText("Tentar novamente");
    retry.setTextColor(Color.WHITE);retry.setBackgroundTintList(ColorStateList.valueOf(GREEN));
    panel.addView(retry,size(-1,px(54)));
    retry.setOnClickListener(v->{hideOffline();if(web!=null)web.reload();});
    offlineView=panel;
    webFrame.addView(panel,new FrameLayout.LayoutParams(-1,-1));
  }
  private void hideOffline(){
    if(offlineView!=null&&webFrame!=null)webFrame.removeView(offlineView);
    offlineView=null;
  }

  private void external(String url){
    try{ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
    catch(Exception e){Toast.makeText(this,"Não foi possível abrir o link.",Toast.LENGTH_SHORT).show();}
  }
  @Override protected void onActivityResult(int code,int result,Intent data){
    super.onActivityResult(code,result,data);
    if(code==FILE_PICKER&&files!=null){ files.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));files=null; }
    if(code==CajuzGuard.DEVICE_CREDENTIAL&&guard!=null)guard.credentialResult(result);
  }
  @Override public void onBackPressed(){
    if(locked){finish();return;}
    if(web!=null&&web.canGoBack())web.goBack();
    else if(web!=null)new AlertDialog.Builder(this).setMessage("Sair do Cajuz?")
      .setNegativeButton("Cancelar",null).setPositiveButton("Sair",(d,w)->finish()).show();
    else super.onBackPressed();
  }
  @Override protected void onPause(){
    if(lockEnabled()&&!startUrl.isEmpty()){locked=true;showCover();}
    if(web!=null)web.onPause();
    CookieManager.getInstance().flush();
    super.onPause();
  }
  @Override protected void onResume(){
    super.onResume();
    if(web!=null)web.onResume();
    if(lockEnabled()&&locked)showCover();
  }
  @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}