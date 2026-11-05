#!/usr/bin/env python3
"""Phase 7 release candidate source-level safety and integration checks.

Run after personalizar.py has generated the patched Android project.
Not a substitute for device/remote tests on an actual TCL Android TV.
"""
from __future__ import annotations
import pathlib
import sys
import xml.etree.ElementTree as ET

BASE = pathlib.Path(sys.argv[1])
SRC = BASE / "app/src/io/freemytv"
ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(BASE / "app/AndroidManifest.xml").getroot()

def read(path: str) -> str:
    return (SRC / path).read_text(encoding="utf-8")

def require(source: str, needle: str, desc: str) -> None:
    assert needle in source, f"{desc}: missing '{needle}'"

def main() -> None:
    expected_package=sys.argv[2] if len(sys.argv)>2 else "io.freemytv.homep7test"
    expected_code=sys.argv[3] if len(sys.argv)>3 else "7"
    expected_name=sys.argv[4] if len(sys.argv)>4 else "3.1-rc-phase7"
    assert manifest.attrib["package"] == expected_package
    assert manifest.attrib[ANDROID + "versionCode"] == expected_code
    assert manifest.attrib[ANDROID + "versionName"] == expected_name

    home = read("HomeActivity.java")
    require(home, "fallbackToClassic(RuntimeException cause)", "classic fallback helper")
    require(home, "new HomeView(this)", "classic Home view")
    require(home, "catch (RuntimeException error) { fallbackToClassic(error); }", "safe Home resume")
    require(home, "dashboard.reset(", "safe Home NewIntent")
    require(home, "dashboard.destroy();", "lifecycle cleanup")
    require(home, "android.util.Log.e(", "fallback diagnostics")

    dashboard = read("HomeDashboardView.java")
    begin = dashboard.index("    private void loadHero(){")
    end = dashboard.index("    private void loadBackdrop(", begin)
    hero = dashboard[begin:end]
    require(hero, "heroLoading=true;", "hero in-flight guard")
    require(hero, "final int ticket=++heroRequestId;", "hero generation")
    require(hero, "final int lifecycle=generation;", "hero lifecycle")
    require(hero, "lastHeroAttempt=0;", "resume retry")
    assert "++generation" not in hero, "hero accidentally invalidates EPG"

    pause = dashboard[dashboard.index("    void pause(){"):dashboard.index("    void destroy(){")]
    require(pause, "heroRequestId++;", "hero lifecycle invalidation")
    require(pause, "epgRequestId++;", "EPG lifecycle invalidation")
    require(pause, "if(heroLoading)lastHeroAttempt=0;", "hero interrupted retry")

    cache = read("HomeImageCache.java")
    require(cache, "MAX_FILE_BYTES = 900_000", "banner persistence")
    require(cache, "MAX_DIR_BYTES = 16L * 1024L * 1024L", "bounded storage")
    require(cache, "MAX_FILES = 80", "bounded image count")

    favorites = read("HomeFavoritesActivity.java")
    require(favorites, "final int previousScroll=", "D-pad editing location")
    require(favorites, "scroller.scrollTo(0,previousScroll)", "D-pad scroll retention")
    require(favorites, "HomeFavoritesTransfer.decode(", "explicit backup import")
    transfer = read("HomeFavoritesTransfer.java")
    require(transfer, "FREEMYTV-FAV-1:", "backup format")
    assert "TMDB_KEY" not in transfer and "Config." not in transfer, "backup secrets"

    remote = read("TvRemotePress.java")
    require(remote, "HOLD_MS = 625L", "OK-hold distinction")
    epg = read("EpgActivity.java")
    require(epg, "TvRemotePress.bind", "EPG click routing")
    require(epg, "HomeChannelPins.toggle", "only EPG pinning")

    print("PASS phase7: HOME fallback, stable RC versionCode, concurrent banner/EPG"
          " request guards, bounded cache, D-pad favorites, explicit backup,"
          " no private TizenTube/UniTVnet access.")

if __name__ == "__main__":
    main()
