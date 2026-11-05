package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User-initiated cross-install transfer of only favorite channel/app order.
 * Never includes TMDB/API keys, account info, playback history or settings.
 *
 * Format: FREEMYTV-FAV-1:<url-safe-base64(json)>
 * The user explicitly copies from one app and imports into the new package.
 */
final class HomeFavoritesTransfer {
    private static final String PREFIX = "FREEMYTV-FAV-1:";
    private static final int MAX_BACKUP_CHARS = 40_000;
    private static final int MAX_APP_ENTRIES = 128;
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");

    static final class Snapshot {
        final ArrayList<HomeChannelPins.Channel> channels;
        final ArrayList<String> apps;
        final int unavailableApps;
        Snapshot(ArrayList<HomeChannelPins.Channel> channels,ArrayList<String> apps,int unavailableApps) {
            this.channels=channels;this.apps=apps;this.unavailableApps=unavailableApps;
        }
    }

    private HomeFavoritesTransfer() {}

    static String export(Context context) throws Exception {
        JSONArray channelArray=new JSONArray();
        for(HomeChannelPins.Channel item:HomeChannelPins.list(context)) {
            JSONObject obj=new JSONObject();
            obj.put("key",item.key);
            obj.put("name",item.name);
            obj.put("logo",item.logo);
            channelArray.put(obj);
        }
        JSONArray appArray=new JSONArray();
        HashSet<String> seen=new HashSet<>();
        for(String name:HomeApps.shelfPackages(context)) {
            if(name!=null&&PACKAGE.matcher(name).matches()&&seen.add(name)){
                appArray.put(name);
                if(appArray.length()>=MAX_APP_ENTRIES)break;
            }
        }
        JSONObject root=new JSONObject();
        root.put("version",1);
        root.put("channels",channelArray);
        root.put("apps",appArray);
        String encoded=Base64.encodeToString(root.toString().getBytes(StandardCharsets.UTF_8),
             Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
        String result=PREFIX+encoded;
        if(result.length()>MAX_BACKUP_CHARS)throw new Exception("Favoritos acima do limite de exportação.");
        return result;
    }

    static Snapshot decode(Context context,String input) throws Exception {
        if(input==null)throw new Exception("O campo está vazio.");
        String code=input.trim();
        if(!code.startsWith(PREFIX))throw new Exception("Código não pertence ao Free My TV.");
        if(code.length()<PREFIX.length()+10||code.length()>MAX_BACKUP_CHARS)
            throw new Exception("Código incompleto ou acima do limite.");
        byte[] decoded;
        try{decoded=Base64.decode(code.substring(PREFIX.length()),Base64.URL_SAFE|Base64.NO_WRAP);}
        catch(Exception ignored){throw new Exception("Código inválido.");}
        JSONObject root;
        try{root=new JSONObject(new String(decoded,StandardCharsets.UTF_8));}
        catch(Exception ignored){throw new Exception("Formato do código inválido.");}
        if(root.optInt("version",-1)!=1)throw new Exception("Versão do backup não suportada.");
        JSONArray rawChannels=root.optJSONArray("channels");
        JSONArray rawApps=root.optJSONArray("apps");
        if(rawChannels==null||rawApps==null)throw new Exception("Lista de favoritos incompleta.");
        if(rawChannels.length()>HomeChannelPins.MAX_PINNED||rawApps.length()>MAX_APP_ENTRIES)
            throw new Exception("Backup excede o limite de itens.");
        ArrayList<HomeChannelPins.Channel> channels=new ArrayList<>();
        HashSet<String> channelIds=new HashSet<>();
        for(int i=0;i<rawChannels.length();i++){
            JSONObject item=rawChannels.optJSONObject(i);
            if(item==null)throw new Exception("Canal malformado no backup.");
            String rawKey=item.optString("key","");
            String rawName=item.optString("name","");
            String rawLogo=item.optString("logo","");
            HomeChannelPins.Channel channel=HomeChannelPins.channel(rawKey,rawName,rawLogo);
            if(channel==null||!channel.key.equals(rawKey)
                ||!channel.name.equals(rawName)
                ||!channel.logo.equals(rawLogo)
                ||!channelIds.add(channel.key))
                throw new Exception("Canal inválido ou duplicado.");
            channels.add(channel);
        }
        ArrayList<String> apps=new ArrayList<>();
        HashSet<String> appIds=new HashSet<>();
        int unavailable=0;
        for(int i=0;i<rawApps.length();i++){
            String pkg=rawApps.optString(i,"");
            if(!PACKAGE.matcher(pkg).matches()||!appIds.add(pkg))
                throw new Exception("Aplicativo inválido ou duplicado.");
            Intent launch=HomeApps.launchIntent(context.getPackageManager(),pkg);
            if(launch==null)unavailable++;
            else apps.add(pkg);
        }
        int max=HomeApps.maxShelf(context);
        if(apps.size()>max)throw new Exception("Essa TV suporta até "+max+" apps na faixa principal.");
        return new Snapshot(channels,apps,unavailable);
    }

    static boolean restore(Context context,Snapshot snapshot) {
        if(snapshot==null||snapshot.channels==null||snapshot.apps==null)return false;
        if(!HomeChannelPins.replaceAll(context,snapshot.channels))return false;
        Prefs.setShelf(new ArrayList<>(snapshot.apps));
        return true;
    }
}
