package io.freemytv;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Single bounded XMLTV cache shared by Agora na TV and the Home EPG strip.
 * All callers MUST invoke get() from a background worker.
 */
final class EpgFeedCache {
    static final String[] FEEDS={
        "globo.xml","maissbt.xml","epg.xml","claro.xml","plutotv.xml","xsports.xml"
    };
    private static final String BASE=
        "https://raw.githubusercontent.com/limaalef/BrazilTVEPG/main/";
    private static final long TTL_MS=4L*60L*60L*1000L;
    private static final long MAX_BYTES=30L*1024L*1024L;
    private static final Object LOCK=new Object();

    private EpgFeedCache() {}

    private static boolean allowed(String feed){
        if(feed==null)return false;
        for(String permitted:FEEDS)if(permitted.equals(feed))return true;
        return false;
    }
    private static boolean usable(File file){
        return file.isFile()&&file.length()>100&&file.length()<=MAX_BYTES;
    }
    static File get(Context context,String feed) throws Exception{
        if(!allowed(feed))throw new IllegalArgumentException("Fonte EPG não autorizada");
        // Synchronizes cache lifetime across both readers in the :home process.
        synchronized(LOCK){
            File dir=context.getApplicationContext().getCacheDir();
            File cache=new File(dir,"xmltv-"+feed);
            long age=System.currentTimeMillis()-cache.lastModified();
            if(usable(cache)&&age>=0&&age<TTL_MS)return cache;
            File temp=new File(dir,"xmltv-"+feed+".download");
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(BASE+feed).openConnection();
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(18000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept-Encoding","identity");
                if(connection.getResponseCode()!=200)
                    throw new IllegalStateException("EPG HTTP "+connection.getResponseCode());
                long bytes=0;
                try(InputStream in=connection.getInputStream();
                    FileOutputStream out=new FileOutputStream(temp,false)){
                    byte[] buffer=new byte[16384];
                    int n;
                    while((n=in.read(buffer))!=-1){
                        bytes+=n;
                        if(bytes>MAX_BYTES)throw new IllegalStateException("EPG excede limite");
                        out.write(buffer,0,n);
                    }
                }
                if(bytes<100)throw new IllegalStateException("EPG vazio");
                if(cache.exists()&&!cache.delete())
                    throw new IllegalStateException("Cache ocupado");
                if(!temp.renameTo(cache))
                    throw new IllegalStateException("Falha ao salvar cache EPG");
                return cache;
            }catch(Exception error){
                if(usable(cache))return cache; // offline: use last successful guide
                throw error;
            }finally{
                temp.delete();
                if(connection!=null)connection.disconnect();
            }
        }
    }
}
