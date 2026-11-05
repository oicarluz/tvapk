#!/usr/bin/env python3
"""Static smoke tests against the generated Android project.

These assertions guard integration regressions; they do not replace TCL
remote/UI or real EPG/network verification.
"""
from __future__ import annotations
import pathlib
import sys
import xml.etree.ElementTree as ET


def assert_has(file: pathlib.Path, *tokens: str) -> str:
    source = file.read_text(encoding="utf-8")
    missing = [token for token in tokens if token not in source]
    assert not missing, f"{file.name}: required markers absent: {missing}"
    return source


def main() -> None:
    root = pathlib.Path(sys.argv[1])
    src = root / "app/src/io/freemytv"
    ns = "{http://schemas.android.com/apk/res/android}"
    xml = ET.parse(root / "app/AndroidManifest.xml").getroot()
    pkg = xml.attrib["package"]
    assert pkg == "io.freemytv.homep6test", pkg

    app = xml.find("application")
    assert app is not None
    activities = {
        item.attrib.get(ns + "name"): item
        for item in app.findall("activity")
    }
    required = [
        "HomeActivity", "SettingsActivity", "EpgActivity",
        "MoviesActivity", "SeriesActivity", "HomeFavoritesActivity"
    ]
    for name in required:
        assert "io.freemytv." + name in activities, name

    home = activities["io.freemytv.HomeActivity"]
    filters = home.findall("intent-filter")
    categories = {
        child.attrib.get(ns + "name")
        for element in filters
        for child in element.findall("category")
    }
    assert "android.intent.category.HOME" in categories
    assert "android.intent.category.DEFAULT" in categories
    assert "android.intent.category.LEANBACK_LAUNCHER" in categories
    assert "android.intent.category.LAUNCHER" in categories
    for name in ("EpgActivity", "MoviesActivity", "SeriesActivity", "HomeFavoritesActivity"):
        item = activities["io.freemytv." + name]
        assert item.attrib.get(ns + "exported") == "false", name
        assert item.attrib.get(ns + "process") == ":home", name
        assert not item.findall("intent-filter"), name + ": must not receive HOME"
    for name, element in activities.items():
        if name == "io.freemytv.HomeActivity":
            continue
        for intent in element.findall("intent-filter"):
            cats = [c.attrib.get(ns + "name") for c in intent.findall("category")]
            assert "android.intent.category.HOME" not in cats, name

    act = assert_has(src / "HomeActivity.java",
                     "dashboard = new HomeDashboardView(this)",
                     "dashboard.resume()",
                     "dashboard.pause()",
                     "dashboard.destroy()",
                     "new HomeView(this)")
    mode = assert_has(src / "HomeMode.java",
                      'static final String PKG = "io.freemytv.homep6test";')
    pins = assert_has(src / "HomeChannelPins.java",
                      "MAX_PINNED = 30",
                      "static boolean replaceAll(",
                      "static boolean move(",
                      "p.edit().putString(KEY, root.toString()).commit()")
    assert "if (replacement == null || replacement.size() > MAX_PINNED)" in pins

    home_ui = assert_has(src / "HomeDashboardView.java",
                         "new Intent(activity,MoviesActivity.class)",
                         "new Intent(activity,SeriesActivity.class)",
                         "HomeFavoritesActivity.class",
                         "HomeApps.shelfPackages(activity)",
                         "HomeEpgSnapshot.load(activity,pinned)",
                         "private int epgRequestId")
    assert "int ticket=++generation;" not in home_ui[home_ui.index("private void loadHero()"):home_ui.index("private void loadBackdrop(")]
    assert "int ticket=generation;" in home_ui[home_ui.index("private void loadHero()"):home_ui.index("private void loadBackdrop(")]
    assert "epgRequestId++;" in home_ui

    guide = assert_has(src / "EpgActivity.java",
                       "TvRemotePress.bind",
                       "HomeChannelPins.toggle",
                       '"Fixados"', "MoviesActivity.class",
                       "SeriesActivity.class")
    assert "com.unitvnet.tvod" in guide
    remote = assert_has(src / "TvRemotePress.java",
                        "HOLD_MS = 625L",
                        "KEYCODE_DPAD_CENTER",
                        "handler.removeCallbacks(delayed)")

    catalog = assert_has(src / "MediaCatalogActivity.java",
                         '"/discover/"+media', '"/search/"+media',
                         "TV_GENRE_IDS", "MOVIE_GENRE_IDS",
                         "loadPage();", "PendingUniSearch.remember(this,item.title)")
    assert_has(src / "HomeFavoritesActivity.java",
               "HomeFavoritesTransfer.export(this)",
               "HomeFavoritesTransfer.decode(",
               "HomeChannelPins.move(this",
               "Prefs.setShelf(next)")
    backup = assert_has(src / "HomeFavoritesTransfer.java",
                        "FREEMYTV-FAV-1:", "Base64.URL_SAFE",
                        "HomeChannelPins.replaceAll",
                        "Prefs.setShelf")
    assert "TMDB_KEY" not in backup

    # Existing Free My TV remote service is left with no external content reader.
    service_config = root / "app/res/xml/remote_key_service.xml"
    assert 'android:canRetrieveWindowContent="true"' not in service_config.read_text(encoding="utf-8")
    assert "Config.TMDB_KEY" not in assert_has(src / "PendingUniSearch.java",
                                              "ClipData.newPlainText")

    print("PASS: original Android HOME preserved; TV guide, movies, series and "
          "favorites manager registered; EPG/hero race guard; clipboard backup; "
          "no accessibility screen scraping.")


if __name__ == "__main__":
    main()
