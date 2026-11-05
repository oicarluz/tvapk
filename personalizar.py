#!/usr/bin/env python3
"""Customizes ONLY the Free My TV v1.3.0 source for a specific personal use case.

Source: https://github.com/simbasjungles/free-my-tv (MIT)
No user credentials, TMDB key or installed applications are accessed here.
Changes are source-level and must be compiled and tested on a device.
"""
from pathlib import Path
import re
import sys

TARGET_PKG = 'com.unitvnet.tvod'
MARKER = 'UNITVNET_CUSTOM_v1'


def once(s: str, old: str, new: str, label: str) -> str:
    hits = s.count(old)
    if hits != 1:
        raise RuntimeError(f'{label}: esperava 1 ocorrencia; encontrei {hits}. Nao modifiquei o arquivo.')
    return s.replace(old, new, 1)


def between(s: str, start: str, end: str, replacement: str, label: str) -> str:
    if s.count(start) != 1 or end not in s[s.index(start) + len(start):]:
        raise RuntimeError(f'{label}: bloco mudou na versao do projeto. Pare aqui.')
    a = s.index(start)
    b = s.index(end, a)
    return s[:a] + replacement + s[b:]


def patch_recs(src: str) -> str:
    s = src
    s = once(s, 'private static final int CACHE_VERSION = 5;', 'private static final int CACHE_VERSION = 6; // '+MARKER, 'cache')
    s = once(s, 'static final String REGION = Place.region();', 'static final String REGION = "BR";', 'regiao')
    s = between(s,
        '        // None of the services we know is on the TV',
        '        // 1. Trending this week',
        '''        // UNITVNET_CUSTOM_v1: catalogo livre do TMDB, independente de assinaturas e aplicativos.
        String common = "&language=pt-BR&include_adult=false&sort_by=popularity.desc"
                + (Config.HIDE_ANIMATION ? "&without_genres=" + ANIMATION : "");
''', 'filtro de plataformas')
    s = once(s,
        '"?language=" + lang() + "&append_to_response=watch/providers,images&include_image_language=null," + Ui.language.replaceAll("-.*", "") + ",en"',
        '"?language=" + lang() + "&append_to_response=images&include_image_language=null,pt,en"', 'detalhes tmdb')
    s = between(s,
        '        JSONObject wp = o.optJSONObject("watch/providers");',
        '        p.meta = String.join(" · ", parts);',
        '''        // O card abre o UniTVnet, independentemente dos provedores do TMDB.
        p.app = "unitv";
        p.pkg = "com.unitvnet.tvod";
        p.link = null;
        parts.add("UniTVnet");
''', 'destino UniTVnet')
    s = once(s,
        'return p == null || p.app == null ? "" : SERVICE_NAME.get(p.app);',
        'return p != null && "unitv".equals(p.app) ? "UniTVnet" : p == null || p.app == null ? "" : SERVICE_NAME.get(p.app);', 'nome do app')
    s = between(s,
        '    private static String lang() {',
        '    /** A list of just one of `list`',
        '    private static String lang() { return "pt-BR"; } // UNITVNET_CUSTOM_v1\n',
        'idioma pt-BR')
    # The Watchmode service has no UniTVnet search provider: never spend its API quota.
    s = once(s,
        'return linksOn() && p.id > 0 && p.pkg != null && p.link == null',
        'return !"unitv".equals(p.app) && linksOn() && p.id > 0 && p.pkg != null && p.link == null', 'evitar Watchmode')
    s = once(s,
        'if (!linksOn() || p.id < 0 || p.pkg == null || p.link != null) return;',
        'if ("unitv".equals(p.app) || !linksOn() || p.id < 0 || p.pkg == null || p.link != null) return;', 'evitar consulta Watchmode')
    # Trending should not promote adult titles (unlike /discover, /trending has no include_adult arg).
    s = once(s,
        '        if (Config.HIDE_ANIMATION && animated(o)) return;',
        '        if (o.optBoolean("adult", false)) return;\n        if (Config.HIDE_ANIMATION && animated(o)) return;', 'filtrar adulto')
    return s


def patch_home(src: str) -> str:
    s = src
    s = once(s,
        '            it = HomeApps.app(p.pkg, Recs.serviceName(p), p.link);\n            r = heroButton;',
        '            PendingUniSearch.remember(activity, p.title); // UNITVNET_CUSTOM_v1\n'
        '            it = HomeApps.app(p.pkg, Recs.serviceName(p), p.link);\n            r = heroButton;', 'heroi')
    s = once(s,
        '            it = HomeApps.app(p.pkg, Recs.serviceName(p), p.link);\n            r = cardRects[cardIndex];',
        '            PendingUniSearch.remember(activity, p.title); // UNITVNET_CUSTOM_v1\n'
        '            it = HomeApps.app(p.pkg, Recs.serviceName(p), p.link);\n            r = cardRects[cardIndex];', 'card')
    return s


def patch_manifest(src: str) -> str:
    return once(src,
        '    <queries>\n',
        '    <queries>\n        <package android:name="com.unitvnet.tvod" /> <!-- UNITVNET_CUSTOM_v1 -->\n',
        'consultar pacote Android 11+')


def patch_remote(src: str) -> str:

    return once(src,
        '@Override public void onAccessibilityEvent(AccessibilityEvent event) {}',
        '@Override public void onAccessibilityEvent(AccessibilityEvent event) { PendingUniSearch.tryFill(this, event); }',
        'evento de acessibilidade')

def patch_remote_xml(src: str) -> str:
    s = once(src, 'android:accessibilityEventTypes="typeWindowStateChanged"',
        'android:accessibilityEventTypes="typeWindowStateChanged|typeViewFocused|typeWindowContentChanged"', 'eventos')
    s = once(s, 'android:accessibilityFlags="flagRequestFilterKeyEvents"',
        'android:accessibilityFlags="flagRequestFilterKeyEvents|flagReportViewIds"', 'flags')
    return once(s, 'android:canRequestFilterKeyEvents="true"',
        'android:canRequestFilterKeyEvents="true"\n    android:canRetrieveWindowContent="true"', 'permissao de acessibilidade')

PENDING_JAVA = r"""package io.freemytv;
import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;

/** Optional, user-authorized assist for a search explicitly initiated on the home screen. */
final class PendingUniSearch {
    private static final String PKG = "com.unitvnet.tvod";
    private static final long TTL = 120000L;
    private static long lastTry;
    private static File pending(Context c) { return new File(c.getFilesDir(), "pending-unitvnet.txt"); }
    static void remember(Context context, String title) {
        if (title == null || title.trim().isEmpty()) return;
        Context c = context.getApplicationContext();
        try {
            ClipboardManager cb = (ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("Filme", title));
        } catch (RuntimeException ignored) {}
        try (FileOutputStream out = new FileOutputStream(pending(c))) {
            out.write((Long.toString(System.currentTimeMillis() + TTL) + "\n" + title).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
    private static String read(Context c) {
        try {
            File f = pending(c);
            if (!f.exists() || f.length() > 4096) return null;
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            int k = s.indexOf('\n');
            if (k < 0) return null;
            if (System.currentTimeMillis() > Long.parseLong(s.substring(0, k))) { f.delete(); return null; }
            return s.substring(k + 1);
        } catch (Exception ignored) { return null; }
    }
    static void tryFill(AccessibilityService service, AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null || !PKG.contentEquals(event.getPackageName())) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_FOCUSED
            && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastTry < 300) return;
        lastTry = now;
        String title = read(service);
        if (title == null || title.isEmpty()) return;
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 120) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isEditable() && node.isFocused() && !node.isPassword()) {
                Bundle b = new Bundle();
                b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title);
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) {
                    pending(service).delete();
                    return;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
    }
}
"""

def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("Uso: python3 personalizar.py pasta_do_projeto")
    root = Path(sys.argv[1]).resolve()
    manifest = root / 'app/AndroidManifest.xml'
    recs = root / 'app/src/io/freemytv/Recs.java'
    home = root / 'app/src/io/freemytv/HomeView.java'
    remote = root / 'app/src/io/freemytv/RemoteKeyService.java'
    cfg = root / 'app/res/xml/remote_key_service.xml'
    pending = root / 'app/src/io/freemytv/PendingUniSearch.java'
    paths = (manifest, recs, home, remote, cfg)
    if not (root / "build.sh").is_file() or any(not p.is_file() for p in paths):
        raise RuntimeError("Codigo-fonte Free My TV v1.3.0 ausente ou com estrutura diferente.")
    originals = {p: p.read_text(encoding="utf-8") for p in paths}
    if 'android:versionName="1.3"' not in originals[manifest]:
        raise RuntimeError("Apenas Free My TV v1.3.0 e suportado.")
    if MARKER in originals[recs] or pending.exists():
        raise RuntimeError("Personalizacao ja aplicada.")
    changes = {
        manifest: patch_manifest(originals[manifest]),
        recs: patch_recs(originals[recs]),
        home: patch_home(originals[home]),
        remote: patch_remote(originals[remote]),
        cfg: patch_remote_xml(originals[cfg]),
    }
    # Discovery v2 is a separate activity, leaving the existing HOME handler unchanged.
    discovery_manifest = '''        <activity
            android:name=".DiscoverActivity"
            android:process=":home"
            android:exported="false"
            android:label="Descobrir"
            android:screenOrientation="landscape"
            android:theme="@style/FreeMyTV.Home" />
'''
    # The new activity is internal; the existing launcher remains the only HOME intent receiver.
    changes[manifest] = once(changes[manifest],
        '        <!-- The app\'s icon opens the settings',
        discovery_manifest + '        <!-- The app\'s icon opens the settings',
        'discover activity')
    home_activity = root / 'app/src/io/freemytv/HomeActivity.java'
    home_source = home_activity.read_text(encoding='utf-8')
    home_source = once(home_source,
        '        if (RemoteKeys.onKey(this, event)) return true;',
        '''        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
                startActivity(new Intent(this, DiscoverActivity.class));
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_GUIDE) {
                startActivity(new Intent(this, EpgActivity.class));
                return true;
            }
        }
        if (RemoteKeys.onKey(this, event)) return true;''',
        'atalho Descobrir')
    changes[home_activity] = home_source
    # Preserve the original glass Home UI. Press DOWN from its open drawer
    # to see the channel guide, without replacing or redrawing the home itself.
    changes[home] = once(changes[home],
        '                } else if (talkBack() && !widgets().isEmpty()) {',
        '''                } else if (dy > 0) {
                    activity.startActivity(new Intent(activity, EpgActivity.class));
                    return;
                } else if (talkBack() && !widgets().isEmpty()) {''',
        'abrir guia abaixo do painel')
    changes[home] = once(changes[home],
        '        if (Prefs.weather()) drawWeatherCard(c, weatherCard);',
        '''        rowTitle.setColor(drawerInk(0.90f));
        c.drawText("↓  TV AO VIVO  ·  GRADE DE CANAIS E PROGRAMACAO", 80 * k, 1044 * k, rowTitle);
        if (Prefs.weather()) drawWeatherCard(c, weatherCard);''',
        'indicacao do EPG no painel')
    for path, source in changes.items():
        path.write_text(source, encoding="utf-8")
    ext = Path(__file__).resolve().parent / 'extensions'
    for name in ('DiscoverData.java', 'DiscoverActivity.java', 'EpgActivity.java'):
        path = ext / name
        if not path.is_file():
            raise RuntimeError(f'Extensao ausente: {path}')
        (root / 'app/src/io/freemytv' / name).write_text(path.read_text(encoding='utf-8'), encoding='utf-8')
    pending.write_text(PENDING_JAVA, encoding="utf-8")
    # External EPG is an internal activity only; Discover stays the TV entry point.
    manifest_text_epg = manifest.read_text(encoding="utf-8")
    manifest_text_epg = once(manifest_text_epg,
        "        <!-- The app's icon opens the settings",
        '''        <activity
            android:name=".EpgActivity"
            android:process=":home"
            android:exported="false"
            android:label="Guia de TV ao vivo"
            android:screenOrientation="landscape"
            android:theme="@style/FreeMyTV.Home" />
        <!-- The app's icon opens the settings''',
        'registrar EPG')
    manifest.write_text(manifest_text_epg)


    # REAL LAUNCHER VARIANT: can be selected as Android's HOME app.
    # Uses a DISTINCT application ID for safe TCL testing; do not uninstall
    # or replace the already-working io.freemytv application.
    old_package = 'io.freemytv'
    test_package = 'io.freemytv.launchertvtest'
    manifest_text = manifest.read_text(encoding='utf-8')
    manifest_text = once(manifest_text, 'package="io.freemytv"',
        'package="' + test_package + '"', 'applicationId do launcher')
    manifest_text = once(manifest_text, 'android:versionName="1.3"',
        'android:versionName="2.2-launcher-epg"', 'versao')
    manifest_text = manifest_text.replace('io.freemytv.photos', test_package + '.photos')
    manifest_text = manifest_text.replace('io.freemytv.settings', test_package + '.settings')
    # Code remains in the io.freemytv Java namespace. Explicit components
    # are mandatory since the Android app ID differs from the source package.
    manifest_text = re.sub(r'android:name="\.(\w+)"',
        lambda match: 'android:name="' + old_package + '.' + match.group(1) + '"',
        manifest_text)
    # Keep HOME and DEFAULT categories on the REAL HomeActivity.
    # The icon also opens the original Home UI on Android TV and on phones.
    home_start = manifest_text.index('android:name="io.freemytv.HomeActivity"')
    home_end = manifest_text.index('</activity>', home_start)
    home_part = manifest_text[home_start:home_end]
    home_filter = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>'''
    if home_part.count(home_filter) != 1:
        raise RuntimeError("Nao achei o filtro HOME original; abortando para proteger a TV.")
    home_part = home_part.replace(home_filter, home_filter + '''
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>''')
    manifest_text = manifest_text[:home_start] + home_part + manifest_text[home_end:]
    # The original Settings remain accessible from the HOME gear. They must
    # NOT be the activity launched by the app icon (old test's TV/phone split).
    settings_start = manifest_text.index('android:name="io.freemytv.SettingsActivity"')
    settings_end = manifest_text.index('</activity>', settings_start)
    settings_part = manifest_text[settings_start:settings_end]
    settings_filter = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>'''
    if settings_part.count(settings_filter) != 1:
        raise RuntimeError("Nao achei o filtro antigo de Settings; abortando.")
    settings_part = settings_part.replace(settings_filter, '')
    manifest_text = manifest_text[:settings_start] + settings_part + manifest_text[settings_end:]
    # Discover and EPG stay internal; neither receives HOME or the app icon.
    if 'android:name="io.freemytv.DiscoverActivity"' not in manifest_text:
        raise RuntimeError("DiscoverActivity nao registrada")
    if 'android:name="io.freemytv.EpgActivity"' not in manifest_text:
        raise RuntimeError("EpgActivity nao registrada")
    manifest_text = manifest_text.replace('android:label="Free My TV"',
        'android:label="Free My TV + EPG (Teste)"')
    manifest.write_text(manifest_text, encoding='utf-8')

    # HomeMode issues ADB operations against PACKAGE/CLASS components.
    # The original constant targeted io.freemytv and would accidentally
    # switch back to the user's preexisting installation.
    mode = root / 'app/src/io/freemytv/HomeMode.java'
    mode_text = mode.read_text(encoding='utf-8')
    mode_text = once(mode_text, 'static final String PKG = "io.freemytv";',
        'static final String PKG = "' + test_package + '";',
        'HomeMode pacote novo')
    for kind in ('HomeActivity', 'SettingsActivity', 'RemoteKeyService', 'NowPlaying'):
        mode_text = once(mode_text, 'PKG + "/" + PKG + ".' + kind + '"',
            'PKG + "/io.freemytv.' + kind + '"', kind + ' componente')
    mode_text = once(mode_text,
        'PKG + "/" + PKG + (DREAM_ALONE ? ".ScreensaverAlone" : ".Screensaver")',
        'PKG + "/io.freemytv" + (DREAM_ALONE ? ".ScreensaverAlone" : ".Screensaver")',
        'screensaver componente')
    mode_text = once(mode_text,
        'PKG + "/" + PKG + (DREAM_ALONE ? ".Screensaver" : ".ScreensaverAlone")',
        'PKG + "/io.freemytv" + (DREAM_ALONE ? ".Screensaver" : ".ScreensaverAlone")',
        'screensaver alternativo')
    mode.write_text(mode_text, encoding='utf-8')

    # Native aapt must write R.java into the existing Java package.
    build = root / 'build.sh'
    build_text = build.read_text(encoding='utf-8')
    build_text = once(build_text,
        '--java out/gen out/res.zip',
        '--java out/gen --custom-package io.freemytv out/res.zip',
        'pacote R.java')
    build.write_text(build_text, encoding='utf-8')

    print('REAL HOME LAUNCHER: ' + test_package
          + ' with HOME/DEFAULT + LEANBACK/LAUNCHER on HomeActivity.')
    print('Settings opens from Home gear. Discover and EPG are internal.')
    print('Safe parallel install: no auto-switch at installation.')

if __name__ == "__main__":
    main()
