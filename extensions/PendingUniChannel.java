package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityManager;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

/**
 * User-authorized, bounded UI interaction with UniTVnet.
 * No private API, hidden activity, DRM, credentials or media URL is used.
 * Only visible and non-password accessibility nodes are inspected, and only
 * while a channel was explicitly selected by the user.
 */
final class PendingUniChannel {
    private static final String UNI="com.unitvnet.tvod";
    private static final long TTL=75_000L;
    private static final long THROTTLE=850L;
    private static long lastInspection;
    private static final int MAX_NODES=240, MAX_SCROLLS=10;
    private static File requestFile(Context c){return new File(c.getFilesDir(),"unitv-channel-request.json");}
    private static File statusFile(Context c){return new File(c.getFilesDir(),"unitv-channel-status.txt");}
    private PendingUniChannel(){}

    static boolean enabled(Context context){
        AccessibilityManager mgr=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(mgr==null||!mgr.isEnabled())return false;
        for(AccessibilityServiceInfo info:mgr.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)){
            if(info.getResolveInfo()!=null&&info.getResolveInfo().serviceInfo!=null){
                String pkg=info.getResolveInfo().serviceInfo.packageName;
                String name=info.getResolveInfo().serviceInfo.name;
                if(context.getPackageName().equals(pkg)&&(name.equals("io.freemytv.UniChannelService")||name.equals(".UniChannelService")))return true;
            }
        }
        return false;
    }

    static void request(Context context,String channel){
        if(channel==null||channel.trim().isEmpty())return;
        JSONObject object=new JSONObject();
        try{
            object.put("channel",channel.trim());
            object.put("expires",System.currentTimeMillis()+TTL);
            object.put("phase",0); // 0=seeking list; 1=entered live TV
            object.put("scrolls",0);
            object.put("last",0L);
            save(requestFile(context),object.toString());
            save(statusFile(context),"Procurando "+channel+" no UniTVnet…");
        }catch(Exception ignored){}
    }

    static void cancel(Context c,String message){
        requestFile(c).delete();
        save(statusFile(c),message);
    }

    static String status(Context c){
        try{
            File f=statusFile(c);
            if(f.length()>900)return "";
            return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);
        }catch(Exception ignored){return "";}
    }

    private static void save(File file,String value){
        try(FileOutputStream o=new FileOutputStream(file,false)){
            o.write(value.getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }

    private static JSONObject load(Context c){
        try{
            File f=requestFile(c);
            if(!f.isFile()||f.length()<5||f.length()>2048)return null;
            return new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
        }catch(Exception ignored){return null;}
    }

    private static String normalized(String text){
        if(text==null)return "";
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }

    private static String baseName(String name){
        String n=normalized(name);
        n=n.replaceAll("^(tv |canal |rede )","");
        n=n.replaceAll("( hd| sd| fhd| full hd| 4k| uhd)$","");
        if(n.equals("globo tv"))return "globo";
        if(n.equals("sbt tv"))return "sbt";
        if(n.equals("record tv"))return "record";
        return n;
    }

    private static String nodeLabel(AccessibilityNodeInfo n){
        if(n.isPassword())return "";
        CharSequence t=n.getText();
        if(t!=null&&t.length()>0)return t.toString();
        CharSequence d=n.getContentDescription();
        return d==null?"":d.toString();
    }

    private static boolean isChannel(String label,String desired){
        String candidate=baseName(label),want=baseName(desired);
        return !candidate.isEmpty()&&!want.isEmpty()&&candidate.equals(want);
    }

    private static boolean isLiveNav(String label){
        String n=normalized(label);
        return n.equals("tv ao vivo")||n.equals("ao vivo")
            ||n.equals("canais ao vivo")||n.equals("canais")
            ||n.equals("live tv")||n.equals("live");
    }

    private static AccessibilityNodeInfo actionable(AccessibilityNodeInfo node){
        AccessibilityNodeInfo n=node;
        for(int i=0;n!=null&&i<4;i++){
            if(n.isVisibleToUser()&&n.isClickable()&&n.isEnabled())return n;
            n=n.getParent();
        }
        return null;
    }

    static void inspect(AccessibilityService service,AccessibilityEvent event){
        if(event==null||event.getPackageName()==null
           ||!UNI.contentEquals(event.getPackageName()))return;
        int type=event.getEventType();
        if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
           &&type!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
           &&type!=AccessibilityEvent.TYPE_VIEW_FOCUSED
           &&type!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
        long uptime=SystemClock.elapsedRealtime();
        if(uptime-lastInspection<THROTTLE)return;
        lastInspection=uptime;
        JSONObject task=load(service);
        if(task==null)return;
        String desired=task.optString("channel","");
        if(desired.isEmpty())return;
        if(System.currentTimeMillis()>task.optLong("expires",0)){
            cancel(service,"Tempo esgotado. Abra TV ao vivo e escolha o canal manualmente.");
            return;
        }
        // Never read another application's window or collect passwords.
        AccessibilityNodeInfo root=service.getRootInActiveWindow();
        if(root==null||root.getPackageName()==null
           ||!UNI.contentEquals(root.getPackageName()))return;
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();
        ArrayList<AccessibilityNodeInfo> matches=new ArrayList<>();
        AccessibilityNodeInfo liveNav=null,scrollable=null;
        q.add(root);
        int seen=0;
        while(!q.isEmpty()&&seen++<MAX_NODES){
            AccessibilityNodeInfo n=q.removeFirst();
            if(!n.isVisibleToUser()||n.isPassword())continue;
            String label=nodeLabel(n);
            if(isChannel(label,desired)){
                AccessibilityNodeInfo action=actionable(n);
                if(action!=null&&!matches.contains(action))matches.add(action);
            }
            if(liveNav==null&&isLiveNav(label))liveNav=actionable(n);
            if(n.isScrollable()&&n.isEnabled())scrollable=n;
            for(int i=0;i<n.getChildCount();i++){
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)q.addLast(child);
            }
        }
        try {
            if(matches.size()==1){
                if(matches.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                    cancel(service,"Canal "+desired+" selecionado no UniTVnet. Confirme a reprodução.");
                }else{
                    cancel(service,"Canal encontrado, mas o UniTVnet recusou o clique automatizado.");
                }
                return;
            }
            if(matches.size()>1){
                cancel(service,"Há várias opções para "+desired+". Selecione a versão/afiliada manualmente.");
                return;
            }
            int phase=task.optInt("phase",0);
            long last=task.optLong("last",0);
            if(System.currentTimeMillis()-last<1600L)return;
            if(phase==0&&liveNav!=null){
                if(liveNav.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                    task.put("phase",1);
                    task.put("last",System.currentTimeMillis());
                    save(requestFile(service),task.toString());
                    save(statusFile(service),"Abrindo a lista de canais do UniTVnet…");
                }
            }else if(phase==1&&scrollable!=null){
                int scrolls=task.optInt("scrolls",0);
                if(scrolls>=MAX_SCROLLS){
                    cancel(service,"Canal "+desired+" não apareceu na lista acessível. Tente localizar manualmente.");
                }else if(scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)){
                    task.put("scrolls",scrolls+1);
                    task.put("last",System.currentTimeMillis());
                    save(requestFile(service),task.toString());
                }else{
                    cancel(service,"A lista do UniTVnet não permitiu procurar mais canais.");
                }
            }
        }catch(Exception ignored){
            cancel(service,"Não foi possível navegar automaticamente. Escolha o canal no UniTVnet.");
        }
    }
}
