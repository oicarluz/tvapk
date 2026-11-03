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
    if s.count(start) != 1 or s.count(end) != 1:
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
