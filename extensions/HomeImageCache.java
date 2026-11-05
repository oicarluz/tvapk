package io.freemytv;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Bounded image disk cache used by the TV guide and, in later phases, covers.
 *
 * No networking on the UI thread. Call read/write from a worker.
 * The cache is disposable and has no relation to pinned-channel state.
 */
final class HomeImageCache {
    private static final long TTL = 7L * 24L * 60L * 60L * 1000L;
    private static final int MAX_FILE_BYTES = 900_000; // Include TMDB w780 banners, not just small channel logos.
    private static final int MAX_FILES = 80;
    private static final long MAX_DIR_BYTES = 16L * 1024L * 1024L;
    private static final Object LOCK = new Object();

    private HomeImageCache() {}

    private static File root(Context c) {
        return new File(c.getApplicationContext().getCacheDir(), "home_images_v1");
    }

    private static String name(String url) {
        if (url == null || !url.startsWith("https://") || url.length() > 2048) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2 + 4);
            for (byte b : digest) {
                result.append(Character.forDigit((b >>> 4) & 15, 16));
                result.append(Character.forDigit(b & 15, 16));
            }
            return result.append(".img").toString();
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] read(Context c, String url) {
        String fileName = name(url);
        if (fileName == null) return null;
        synchronized (LOCK) {
            File file = new File(root(c), fileName);
            long age = System.currentTimeMillis() - file.lastModified();
            if (!file.isFile() || file.length() < 1 || file.length() > MAX_FILE_BYTES
                    || age < 0 || age > TTL) return null;
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] image = new byte[(int) file.length()];
                int off = 0;
                while (off < image.length) {
                    int n = in.read(image, off, image.length - off);
                    if (n < 0) return null;
                    off += n;
                }
                return image;
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    static void write(Context c, String url, byte[] bytes) {
        String fileName = name(url);
        if (fileName == null || bytes == null || bytes.length == 0
                || bytes.length > MAX_FILE_BYTES) return;
        synchronized (LOCK) {
            File directory = root(c);
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            File target = new File(directory, fileName);
            File temp = new File(directory, fileName + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp, false)) {
                out.write(bytes);
                out.flush();
            } catch (IOException ignored) {
                temp.delete();
                return;
            }
            if (target.exists() && !target.delete()) {
                temp.delete();
                return;
            }
            if (!temp.renameTo(target)) {
                temp.delete();
                return;
            }
            prune(directory);
        }
    }

    private static void prune(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".img"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long total = 0;
        for (File f : files) total += Math.max(f.length(), 0);
        int remaining = files.length;
        long now = System.currentTimeMillis();
        for (File f : files) {
            boolean stale = f.lastModified() <= 0 || now - f.lastModified() > TTL;
            if (stale || remaining > MAX_FILES || total > MAX_DIR_BYTES) {
                long bytes = Math.max(0, f.length());
                if (f.delete()) {
                    remaining--;
                    total -= bytes;
                }
            }
        }
    }
}
