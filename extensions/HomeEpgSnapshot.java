package io.freemytv;

import android.content.Context;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only current programming for pinned channels, on a background worker only. */
final class HomeEpgSnapshot {
    static final class Guide {
        final String key;
        String current = "", next = "";
        long start = 0, end = 0, nextAt = Long.MAX_VALUE;
        Guide(String key) { this.key = key; }
    }

    private static final String SOURCE = "https://raw.githubusercontent.com/limaalef/BrazilTVEPG/main/";
    private static final String[] FEEDS = {"epg.xml", "globo.xml", "maissbt.xml", "claro.xml"};
    private static final long TTL = 4L * 60L * 60L * 1000L;
    private static final long MAX_SIZE = 30L * 1024L * 1024L;

    private HomeEpgSnapshot() {}

    static Map<String, Guide> load(Context context, List<HomeChannelPins.Channel> pinned) {
        HashMap<String, Guide> result = new HashMap<>();
        if (pinned == null || pinned.isEmpty()) return result;
        HashMap<String,String> desired = new HashMap<>();
        for (HomeChannelPins.Channel channel : pinned) {
            if (channel.key.isEmpty()) continue;
            result.put(channel.key, new Guide(channel.key));
            desired.put(channel.key, channel.key);
        }
        for (String feed : FEEDS) {
            if (Thread.currentThread().isInterrupted()) break;
            try {
                File file = cached(context, feed);
                if (file != null && file.isFile()) parse(file, desired, result);
            } catch (Exception ignored) {
                // One provider cannot prevent rendering from the others.
            }
        }
        return result;
    }

    private static File cached(Context context, String feed) {
        File file = new File(context.getCacheDir(), "xmltv-" + feed);
        long age = System.currentTimeMillis() - file.lastModified();
        if (file.isFile() && file.length() > 100 && age >= 0 && age < TTL) return file;
        File temp = new File(context.getCacheDir(), "home-xmltv-" + feed + ".download");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(SOURCE + feed).openConnection();
            connection.setConnectTimeout(8500);
            connection.setReadTimeout(16500);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (connection.getResponseCode() != 200) return file.isFile() ? file : null;
            long length = 0;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp, false)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    length += n;
                    if (length > MAX_SIZE) throw new IllegalStateException("EPG grande");
                    out.write(buf, 0, n);
                }
            }
            if (length < 100) return file.isFile() ? file : null;
            if (file.exists() && !file.delete()) return file;
            if (!temp.renameTo(file)) return null;
            return file;
        } catch (Exception ignored) {
            return file.isFile() ? file : null;
        } finally {
            if (connection != null) connection.disconnect();
            temp.delete();
        }
    }

    private static String strip(String name) {
        String s = HomeChannelPins.canonical(name);
        return s.replaceAll("( hd| sd| fhd| uhd| 4k)$", "").trim();
    }

    private static String wantedKey(String display, Map<String,String> desired) {
        String direct = HomeChannelPins.canonical(display);
        if (desired.containsKey(direct)) return direct;
        String clean = strip(display);
        if (desired.containsKey(clean)) return clean;
        // Never guess affiliates with different regional schedules.
        return null;
    }

    private static long timestamp(String raw) {
        if (raw == null || raw.length() < 14) return 0;
        try {
            String offset = raw.length() >= 20 ? raw.substring(15, 20) : "+0000";
            SimpleDateFormat parser = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
            parser.setLenient(false);
            Date date = parser.parse(raw.substring(0,14) + " " + offset);
            return date == null ? 0 : date.getTime();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String clock(long time) {
        return new SimpleDateFormat("HH:mm", new Locale("pt","BR")).format(new Date(time));
    }

    private static String text(XmlPullParser parser) throws Exception {
        if (parser.next() == XmlPullParser.TEXT) {
            String content = parser.getText();
            parser.nextTag();
            return content == null ? "" : content.trim();
        }
        return "";
    }

    private static void parse(File file, Map<String,String> desired, Map<String,Guide> guides) throws Exception {
        HashMap<String,String> channelIds = new HashMap<>();
        long now = System.currentTimeMillis();
        try (FileInputStream in = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            String channelId = null, display = "";
            String programmeId = null, programmeTitle = "";
            long start = 0, end = 0;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (Thread.currentThread().isInterrupted()) return;
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("channel".equals(tag)) {
                        channelId = parser.getAttributeValue(null, "id");
                        display = "";
                    } else if (channelId != null && "display-name".equals(tag) && display.isEmpty()) {
                        display = text(parser);
                    } else if ("programme".equals(tag)) {
                        programmeId = parser.getAttributeValue(null, "channel");
                        programmeTitle = "";
                        start = timestamp(parser.getAttributeValue(null,"start"));
                        end = timestamp(parser.getAttributeValue(null,"stop"));
                    } else if (programmeId != null && "title".equals(tag) && programmeTitle.isEmpty()) {
                        programmeTitle = text(parser);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("channel".equals(tag) && channelId != null) {
                        String matched = wantedKey(display, desired);
                        if (matched != null) channelIds.put(channelId, matched);
                        channelId = null;
                    } else if ("programme".equals(tag) && programmeId != null) {
                        Guide g = guides.get(channelIds.get(programmeId));
                        if (g != null && !programmeTitle.isEmpty() && end > start) {
                            if (start <= now && now < end) {
                                if (g.current.isEmpty() || g.end < end) {
                                    g.current = programmeTitle;
                                    g.start = start;
                                    g.end = end;
                                }
                            } else if (start >= now && start < g.nextAt) {
                                g.nextAt = start;
                                g.next = clock(start) + " · " + programmeTitle;
                            }
                        }
                        programmeId = null;
                    }
                }
                event = parser.next();
            }
        }
    }
}
