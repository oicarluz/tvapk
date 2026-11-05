#!/usr/bin/env python3
"""Phase 9 source regression checks, run on generated Android project.

These protect navigation, retries and the existing HOME contract.
Real TCL device tests must still be performed by the device owner.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root=Path(sys.argv[1])
src=root/"app/src/io/freemytv"
ns="{http://schemas.android.com/apk/res/android}"

def read(name):
    return (src/name).read_text(encoding="utf-8")

def has(source,*values):
    for value in values:
        assert value in source, "Missing: "+value

def main():
    doc=ET.parse(root/"app/AndroidManifest.xml").getroot()
    assert doc.get("package")=="io.freemytv.homep9test"
    assert doc.get(ns+"versionCode")=="9"
    assert doc.get(ns+"versionName")=="3.3-qa-phase9"

    app=doc.find("application")
    acts={a.get(ns+"name"):a for a in app.findall("activity")}
    home=acts["io.freemytv.HomeActivity"]
    assert any(c.get(ns+"name")=="android.intent.category.HOME"
        for f in home.findall("intent-filter") for c in f.findall("category"))
    for name in ("EpgActivity","MoviesActivity","SeriesActivity","HomeFavoritesActivity"):
        act=acts["io.freemytv."+name]
        assert act.get(ns+"exported")=="false"
        assert act.get(ns+"process")==":home"

    media=read("MediaCatalogActivity.java")
    has(media,
        "autoLoadBlocked=false;",
        "if(!alive||loading||!hasMore||autoLoadBlocked)return;",
        "autoLoadBlocked=true;",
        "private void retryPage(){",
        "retry.setOnClickListener(v->retryPage());",
        "PendingUniSearch.remember(this,item.title)")
    failure=media[media.index("if(failure!=null){"):media.index("int before=items.size();")]
    assert failure.index("autoLoadBlocked=true;") < failure.index("retry.setVisibility")
    reset=media[media.index("private void resetCatalog(){"):media.index("private String queryPath(")]
    has(reset, "autoLoadBlocked=false;","loadPage();")
    resume=media[media.index("@Override protected void onResume()"):]
    has(resume,"!autoLoadBlocked","hasMore=true;loadPage();")
    assert "TvRemotePress.bind" in read("EpgActivity.java")
    guide=read("EpgActivity.java")
    has(guide,"focusOverrideKey","focusCategoryAfterRender",
        'if(result==HomeChannelPins.Result.UNPINNED&&"Fixados".equals(category))',
        "filter.post(filter::requestFocus);")
    has(read("HomeDashboardView.java"),"HomeFavoritesActivity.class",
        "HomeEpgSnapshot.load(activity,pinned)")
    assert "HomeChannelPins.canonical(ch.name)" in guide
    assert "for(String feed:EpgFeedCache.FEEDS)" in guide
    assert "for (String feed : EpgFeedCache.FEEDS)" in read("HomeEpgSnapshot.java")
    has(read("TvRemotePress.java"),"HOLD_MS = 625L",
        "KEYCODE_DPAD_CENTER", "handler.removeCallbacks(delayed)")
    assert "android:canRetrieveWindowContent=\"true\"" not in (
        root/"app/res/xml/remote_key_service.xml").read_text()

    print("PASS phase9: isolated Android HOME, no auto TMDB retry storm, "
          "D-pad focus after last unpin, six shared XMLTV sources, clipboard-only UniTVnet.")


if __name__=="__main__":
    main()
