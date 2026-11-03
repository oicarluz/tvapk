package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** TV discovery data, with no streaming-service restriction and no third-party accounts. */
final class DiscoverData {
    static final String PKG = "com.unitvnet.tvod";
    static final String[] GENRES = {"Todos","Ação","Comédia","Drama","Terror","Ficção","Suspense","Animação"};
    static final int[] GENRE_MOVIE = {0,28,35,18,27,878,53,16};
    static final int[] GENRE_TV = {0,10759,35,18,9648,10765,9648,16};
    static final String[] STATES = {"Quero assistir","Assistindo","Já assisti"};
    private static final String API = "https://api.tmdb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p/w342";
    static String poster(String path) { return path == null || path.length() < 2 ? "" : IMG + path; }

    static final class Item {
        int id, seasons;
        String kind = "movie", title = "", date = "", poster = "", overview = "", genres = "", nextEpisode = "";
        double rating;
        String state = "";
        String key() { return kind + ":" + id; }
        boolean show() { return kind.equals("tv"); }
        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("id",id); o.put("kind",kind); o.put("title",title); o.put("date",date);
                o.put("poster",poster); o.put("overview",overview); o.put("genres",genres);
                o.put("rating",rating); o.put("seasons",seasons); o.put("nextEpisode",nextEpisode);
                o.put("state",state);
            } catch (Exception ignored) {}
            return o;
        }
        static Item parse(JSONObject o, String forcedKind) {
            Item it = new Item();
            it.id = o.optInt("id");
            String kind = forcedKind == null ? o.optString("media_type","movie") : forcedKind;
            it.kind = kind.equals("tv") ? "tv" : "movie";
            it.title = o.optString(it.show() ? "name" : "title",o.optString("title",o.optString("name","")));
            it.date = o.optString(it.show() ? "first_air_date" : "release_date",o.optString("date",""));
            it.poster = o.optString("poster_path",o.optString("poster",""));
            it.overview = o.optString("overview","");
            it.rating = o.optDouble("vote_average",o.optDouble("rating",0));
            JSONArray genres = o.optJSONArray("genre_ids");
            if (genres == null) genres = o.optJSONArray("genres");
            it.genres = genres != null ? genres.toString() : o.optString("genres","[]");
            it.seasons = o.optInt("number_of_seasons",o.optInt("seasons",0));
            it.nextEpisode = o.optString("nextEpisode","");
            it.state = o.optString("state","");
            return it;
        }
    }

    /** TMDB v3 API key OR v4 read access token, exactly as supported by Free My TV. */
    static JSONObject get(String path) throws Exception {
        String key = Recs.key();
        if (key == null || key.trim().isEmpty()) throw new IllegalStateException("Cadastre sua chave TMDB em Home Settings.");
        boolean bearer = key.startsWith("eyJ");
        String url = API + path + (bearer ? "" : (path.contains("?") ? "&" : "?") + "api_key=" + URLEncoder.encode(key,"UTF-8"));
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept","application/json");
        if (bearer) conn.setRequestProperty("Authorization","Bearer " + key);
        conn.setInstanceFollowRedirects(false);
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IllegalStateException(code == 401 ? "Chave TMDB inválida." : "TMDB retornou HTTP " + code);
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) != -1) {
                    if (out.size() + n > 1048576) throw new IllegalStateException("Resposta do TMDB excedeu o limite.");
                    out.write(b,0,n);
                }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { conn.disconnect(); }
    }

    static String enc(String q) throws Exception { return URLEncoder.encode(q,"UTF-8"); }
    static String day(int offset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR,offset);
        return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime());
    }
    static ArrayList<Item> from(JSONObject response, String kind) {
        ArrayList<Item> out = new ArrayList<>();
        JSONArray arr = response.optJSONArray("results");
        if (arr == null) return out;
        for (int i=0; i<arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || o.optBoolean("adult",false)) continue;
            String media = kind == null ? o.optString("media_type","") : kind;
            if (!media.equals("movie") && !media.equals("tv")) continue;
            Item it = Item.parse(o,media);
            if (it.id > 0 && !it.title.isEmpty()) out.add(it);
        }
        return out;
    }

    static final class Filter {
        int kind = 0, genre = 0, days = 30, rating = 0;
        String query = "";
    }

    static ArrayList<Item> browse(String mode, Filter f, ArrayList<Item> favorites) throws Exception {
        ArrayList<Item> out = new ArrayList<>();
        if ("Minha Lista".equals(mode)) return favorites;
        if ("Alertas".equals(mode)) return alerts(favorites);
        if (!f.query.trim().isEmpty()) {
            String path = "/search/multi?language=pt-BR&include_adult=false&query=" + enc(f.query.trim());
            for (Item it : from(get(path),null)) if (matchesType(it,f.kind)) out.add(it);
        } else {
            String[] kinds = f.kind == 1 ? new String[]{"movie"} : f.kind == 2 ? new String[]{"tv"} : new String[]{"movie","tv"};
            for (String kind : kinds) {
                String path;
                if ("Novidades".equals(mode)) {
                    String field = kind.equals("movie") ? "primary_release_date" : "first_air_date";
                    path = "/discover/" + kind + "?language=pt-BR&include_adult=false&sort_by=" + field
                        + ".desc&" + field + ".gte=" + day(-f.days) + "&" + field + ".lte=" + day(0);
                } else {
                    path = "/discover/" + kind + "?language=pt-BR&include_adult=false&sort_by=popularity.desc";
                    if ("Para você".equals(mode)) {
                        int genre = favoriteGenre(favorites,kind);
                        if (genre > 0) path += "&with_genres=" + genre;
                    }
                }
                if (f.genre != 0) path += "&with_genres=" + (kind.equals("movie") ? GENRE_MOVIE[f.genre] : GENRE_TV[f.genre]);
                if (f.rating != 0) path += "&vote_average.gte=" + f.rating + "&vote_count.gte=30";
                out.addAll(from(get(path),kind));
            }
        }
        if ("Novidades".equals(mode)) {
            Collections.sort(out,(a,b)->b.date.compareTo(a.date));
        } else if ("Para você".equals(mode)) {
            Set<String> seen = new HashSet<>();
            for (Item fItem : favorites) seen.add(fItem.key());
            out.removeIf(item -> seen.contains(item.key()));
            Collections.sort(out,(a,b)->Double.compare(b.rating,a.rating));
        } else {
            Collections.sort(out,(a,b)->Double.compare(b.rating*0.15 + (b.date.isEmpty()?0:1),
                                                       a.rating*0.15 + (a.date.isEmpty()?0:1)));
        }
        if (out.size()>40) return new ArrayList<>(out.subList(0,40));
        return out;
    }
    static boolean matchesType(Item it,int kind) {return kind==0 || kind==1 && !it.show() || kind==2 && it.show();}
    private static int favoriteGenre(ArrayList<Item> favorites, String kind) {
        Map<Integer,Integer> freq = new HashMap<>();
        for (Item it : favorites) {
            if (!it.kind.equals(kind)) continue;
            try {
                JSONArray arr = new JSONArray(it.genres);
                for (int i=0;i<arr.length();i++) {
                    int g = arr.optInt(i);
                    if (g > 0) freq.put(g,freq.containsKey(g)?freq.get(g)+1:1);
                }
            } catch (Exception ignored) {}
        }
        int best=0, count=0;
        for (Map.Entry<Integer,Integer> e : freq.entrySet()) if (e.getValue()>count) {count=e.getValue();best=e.getKey();}
        return best;
    }
    static ArrayList<Item> alerts(ArrayList<Item> favorites) throws Exception {
        ArrayList<Item> out = new ArrayList<>();
        for (Item orig : favorites) {
            if (!orig.show()) continue;
            JSONObject tv = get("/tv/" + orig.id + "?language=pt-BR");
            Item it = Item.parse(tv,"tv");
            it.state=orig.state;
            int current = tv.optInt("number_of_seasons");
            JSONObject upcoming = tv.optJSONObject("next_episode_to_air");
            JSONObject last = tv.optJSONObject("last_episode_to_air");
            if (current > orig.seasons && orig.seasons > 0) {
                it.nextEpisode = "Nova temporada: " + current + " (antes " + orig.seasons + ")";
            } else if (upcoming != null && !upcoming.optString("air_date","").isEmpty()) {
                it.nextEpisode = "Próximo episódio: " + upcoming.optString("air_date");
            } else if (last != null && !last.optString("air_date","").isEmpty()) {
                it.nextEpisode = "Último episódio: " + last.optString("air_date");
            } else {
                it.nextEpisode = "Sem nova data anunciada";
            }
            out.add(it);
        }
        return out;
    }
    static String trailer(Item it) throws Exception {
        for (String lang : new String[]{"pt-BR","en-US"}) {
            JSONObject obj = get("/" + it.kind + "/" + it.id + "/videos?language=" + lang);
            JSONArray a = obj.optJSONArray("results");
            if (a == null) continue;
            String any="";
            for (int i=0;i<a.length();i++) {
                JSONObject v=a.optJSONObject(i);
                if (v==null || !v.optString("site").equals("YouTube")) continue;
                String id=v.optString("key","");
                if (!id.matches("[A-Za-z0-9_-]{6,20}")) continue;
                if (any.isEmpty()) any=id;
                if ("Trailer".equalsIgnoreCase(v.optString("type",""))) return id;
            }
            if (!any.isEmpty()) return any;
        }
        return "";
    }

    static final class Store {
        final SharedPreferences sp;
        Store(Context c) { sp=c.getSharedPreferences("discover_v2",Context.MODE_PRIVATE); }
        ArrayList<Item> all() {
            ArrayList<Item> out = new ArrayList<>();
            try {
                JSONArray arr = new JSONArray(sp.getString("watchlist","[]"));
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.optJSONObject(i);
                    if(o!=null) out.add(Item.parse(o,o.optString("kind","movie")));
                }
            } catch (Exception ignored) {}
            return out;
        }
        void save(Item item,String state) {
            ArrayList<Item> l=all();
            boolean found=false;
            for (int i=0;i<l.size();i++) if(l.get(i).key().equals(item.key())){
                Item old=l.get(i);
                item.seasons = item.seasons==0 ? old.seasons : item.seasons;
                item.state=state;
                l.set(i,item);found=true;break;
            }
            if(!found){item.state=state;l.add(0,item);}
            persist(l);
        }
        void remove(Item item) {
            ArrayList<Item> l=all();
            l.removeIf(x->x.key().equals(item.key()));
            persist(l);
        }
        void seenSeason(Item it){
            ArrayList<Item> l=all();
            for(Item saved:l)if(saved.key().equals(it.key()))saved.seasons=it.seasons;
            persist(l);
        }
        private void persist(ArrayList<Item> items){
            JSONArray arr=new JSONArray();
            for(Item it:items)arr.put(it.json());
            sp.edit().putString("watchlist",arr.toString()).apply();
        }
        boolean shortcut() {return sp.getBoolean("shortcut",true);}
        void shortcut(boolean enable){sp.edit().putBoolean("shortcut",enable).apply();}
    }
}
