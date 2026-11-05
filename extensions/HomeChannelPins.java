package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Phase 1 state for TV channels pinned on the HOME screen.
 *
 * Owns ONLY channel shortcuts, never the Free My TV app shelf. Runs in the
 * existing :home process shared by HomeActivity and EpgActivity, so updates
 * are synchronous and visible to both. No channels are pinned by default.
 *
 * Storage schema: {"version":1,"channels":[{"key":"...","name":"...","logo":"..."}]}
 * The key is based on the guide's canonical channel name, not transient
 * programme data, URL, regional sequence number or provider stream ID.
 */
final class HomeChannelPins {
    static final int MAX_PINNED = 30;
    static final int SCHEMA_VERSION = 1;
    private static final String PREFS = "home_channels_v1";
    private static final String KEY = "state";
    private static final Object LOCK = new Object();

    enum Result { PINNED, UNPINNED, ALREADY_PINNED, NOT_PINNED, FULL, INVALID, ERROR }

    static final class Channel {
        final String key;
        final String name;
        final String logo;
        Channel(String key, String name, String logo) {
            this.key = key;
            this.name = name;
            this.logo = logo;
        }
    }

    private HomeChannelPins() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String canonical(String input) {
        if (input == null) return "";
        String text = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120).trim() : text;
    }

    static String cleanName(String input) {
        if (input == null) return "";
        String text = input.trim().replaceAll("\\s+", " ");
        return text.length() > 120 ? text.substring(0, 120).trim() : text;
    }

    static String cleanLogo(String input) {
        if (input == null) return "";
        String text = input.trim();
        return text.length() <= 512 && text.startsWith("https://") ? text : "";
    }

    static Channel channel(String key, String name, String logo) {
        String normalized = canonical(key);
        String display = cleanName(name);
        if (normalized.isEmpty() || display.isEmpty()) return null;
        return new Channel(normalized, display, cleanLogo(logo));
    }

    static List<Channel> list(Context context) {
        synchronized (LOCK) {
            return Collections.unmodifiableList(new ArrayList<>(read(prefs(context))));
        }
    }

    static boolean isPinned(Context context, String key) {
        String normalized = canonical(key);
        if (normalized.isEmpty()) return false;
        synchronized (LOCK) {
            for (Channel c : read(prefs(context))) if (c.key.equals(normalized)) return true;
            return false;
        }
    }

    static Result toggle(Context context, String key, String name, String logo) {
        Channel incoming = channel(key, name, logo);
        if (incoming == null) return Result.INVALID;
        synchronized (LOCK) {
            SharedPreferences p = prefs(context);
            ArrayList<Channel> channels = read(p);
            int i = indexOf(channels, incoming.key);
            if (i >= 0) {
                channels.remove(i);
                return persist(p, channels) ? Result.UNPINNED : Result.ERROR;
            }
            if (channels.size() >= MAX_PINNED) return Result.FULL;
            channels.add(incoming);
            return persist(p, channels) ? Result.PINNED : Result.ERROR;
        }
    }

    static Result pin(Context context, String key, String name, String logo) {
        Channel incoming = channel(key, name, logo);
        if (incoming == null) return Result.INVALID;
        synchronized (LOCK) {
            SharedPreferences p = prefs(context);
            ArrayList<Channel> channels = read(p);
            if (indexOf(channels, incoming.key) >= 0) return Result.ALREADY_PINNED;
            if (channels.size() >= MAX_PINNED) return Result.FULL;
            channels.add(incoming);
            return persist(p, channels) ? Result.PINNED : Result.ERROR;
        }
    }

    static Result unpin(Context context, String key) {
        String normalized = canonical(key);
        if (normalized.isEmpty()) return Result.INVALID;
        synchronized (LOCK) {
            SharedPreferences p = prefs(context);
            ArrayList<Channel> channels = read(p);
            int i = indexOf(channels, normalized);
            if (i < 0) return Result.NOT_PINNED;
            channels.remove(i);
            return persist(p, channels) ? Result.UNPINNED : Result.ERROR;
        }
    }

    /** Future Home editor can reorder pinned channels without re-saving EPG data. */
    static boolean move(Context context, String key, int newIndex) {
        String normalized = canonical(key);
        synchronized (LOCK) {
            SharedPreferences p = prefs(context);
            ArrayList<Channel> channels = read(p);
            int current = indexOf(channels, normalized);
            if (current < 0 || newIndex < 0 || newIndex >= channels.size()) return false;
            if (current == newIndex) return true;
            Channel moved = channels.remove(current);
            channels.add(newIndex, moved);
            return persist(p, channels);
        }
    }

    private static int indexOf(List<Channel> channels, String key) {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).key.equals(key)) return i;
        }
        return -1;
    }

    private static ArrayList<Channel> read(SharedPreferences p) {
        ArrayList<Channel> channels = new ArrayList<>();
        try {
            String raw = p.getString(KEY, "");
            if (raw == null || raw.isEmpty()) return channels;
            JSONObject root = new JSONObject(raw);
            if (root.optInt("version", 0) != SCHEMA_VERSION) return channels;
            JSONArray arr = root.optJSONArray("channels");
            if (arr == null) return channels;
            for (int i = 0; i < arr.length() && channels.size() < MAX_PINNED; i++) {
                JSONObject object = arr.optJSONObject(i);
                if (object == null) continue;
                Channel c = channel(object.optString("key"), object.optString("name"), object.optString("logo"));
                if (c != null && indexOf(channels, c.key) < 0) channels.add(c);
            }
        } catch (Exception ignored) {
            // Bad state cannot crash the launcher. A subsequent explicit pin
            // overwrites an invalid document; we never write on a read.
        }
        return channels;
    }

    private static boolean persist(SharedPreferences p, List<Channel> channels) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", SCHEMA_VERSION);
            JSONArray arr = new JSONArray();
            for (Channel c : channels) {
                JSONObject item = new JSONObject();
                item.put("key", c.key);
                item.put("name", c.name);
                item.put("logo", c.logo);
                arr.put(item);
            }
            root.put("channels", arr);
            return p.edit().putString(KEY, root.toString()).commit();
        } catch (Exception ignored) {
            return false;
        }
    }
}
