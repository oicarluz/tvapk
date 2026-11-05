#!/usr/bin/env python3
"""Phase 8 static regression checks on a generated Android TV project.

Run after personalizar.py. Compilation in Android SDK and real TCL tests still
required; this catches drift in critical EPG / D-pad integration invariants.
"""
from __future__ import annotations
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root=Path(sys.argv[1])
src=root/"app/src/io/freemytv"
android="{http://schemas.android.com/apk/res/android}"

def read(name: str) -> str:
    return (src/name).read_text(encoding="utf-8")

def require(source: str, markers: list[str], where: str) -> None:
    missing=[s for s in markers if s not in source]
    assert not missing, f"{where}: missing {missing}"

def main() -> None:
    manifest=ET.parse(root/"app/AndroidManifest.xml").getroot()
    assert manifest.attrib["package"]=="io.freemytv.homep8test"
    assert manifest.attrib[android+"versionCode"]=="8"
    assert manifest.attrib[android+"versionName"]=="3.2-epg-phase8"

    cache=read("EpgFeedCache.java")
    require(cache, [
        '"globo.xml","maissbt.xml","epg.xml","claro.xml","plutotv.xml","xsports.xml"',
        '"xmltv-"+feed',
        "synchronized(LOCK)", "MAX_BYTES=30L*1024L*1024L",
        'connection.setRequestProperty("Accept-Encoding","identity")',
        "if(usable(cache))return cache",
    ], "shared XMLTV cache")
    for name in ["globo.xml","maissbt.xml","epg.xml","claro.xml","plutotv.xml","xsports.xml"]:
        assert cache.count('"'+name+'"')==1, f"duplicate or missing {name}"

    guide=read("EpgActivity.java")
    home=read("HomeEpgSnapshot.java")
    require(guide, [
        "for(String feed:EpgFeedCache.FEEDS)",
        "parseFeed(EpgFeedCache.get(this,feed))",
        "ch.key=HomeChannelPins.canonical(ch.name);",
        "row.setTag(c.key);", "scroll.getScrollY()",
        "scroll.scrollTo(0,previousScroll)",
        "TvRemotePress.bind"
    ], "Agora na TV")
    require(home, [
        "for (String feed : EpgFeedCache.FEEDS)",
        "EpgFeedCache.get(context, feed)",
        "HomeChannelPins.canonical(display)",
    ], "Home EPG")
    assert "private File cacheFile(" not in guide, "obsolete guide downloader persists"
    assert "private static File cached(" not in home, "obsolete Home downloader persists"

    dash=read("HomeDashboardView.java")
    require(dash, [
        "View previouslyFocused=channelRow.getFocusedChild();",
        "panel.setTag(c.key);",
        "focusKey.equals(c.key)",
        "strip.getScrollX()",
        "strip.scrollTo(oldScroll,0)",
        "focusTarget.requestFocus()",
        "HomeFavoritesActivity.class",
        "HomeApps.shelfPackages(activity)",
    ], "Home D-pad focus")
    assert "int ticket=++generation;" not in dash[dash.index("private void loadHero()"):dash.index("private void loadBackdrop(")]

    pins=read("HomeChannelPins.java")
    require(pins,["MAX_PINNED = 30","static String canonical(", "static boolean move("], "pin state")
    assert read("HomeFavoritesTransfer.java").count("FREEMYTV-FAV-1:")>=1
    assert "PendingUniSearch.remember(this,item.title)" in read("MediaCatalogActivity.java")
    print("PASS phase 8: one XMLTV feed/cache source, six EPGs, consistent pinned-channel keys,"
          " TCL focus/scroll retention, HOME/catálogo/favorites preserved.")


if __name__=="__main__":
    main()
