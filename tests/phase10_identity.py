#!/usr/bin/env python3
"""Phase 10 release identity regression checks, without any private signing key.

The test writes a dummy local FILE only to test the identity gate, NEVER to
sign or publish an APK. Tests fail-closed policy and immutability of package ID.
"""
from __future__ import annotations
from pathlib import Path
from tempfile import TemporaryDirectory
import importlib.util

spec=importlib.util.spec_from_file_location("personalizar",Path(__file__).resolve().parent.parent/"personalizar.py")
assert spec and spec.loader
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def must_fail(env, expected):
    try:
        mod.build_identity(env)
    except RuntimeError as err:
        assert expected in str(err), (expected, str(err))
    else:
        raise AssertionError(f"Expected rejection for: {list(env)}")


def main():
    preview=mod.build_identity({})
    assert preview==(False,"io.freemytv.homep10test","3.4-phase10-preview","10",
                     "Free My TV Fase 10 (Teste)")
    assert mod.build_identity({"FREEMYTV_RELEASE_MODE":"0"})==preview
    must_fail({"FREEMYTV_RELEASE_MODE":"yes"},"0 ou 1")
    must_fail({"FREEMYTV_RELEASE_VERSION_CODE":"100"},"exige FREEMYTV_RELEASE_MODE=1")
    must_fail({"FREEMYTV_RELEASE_MODE":"1"},"keystore permanente")
    must_fail({"FREEMYTV_RELEASE_MODE":"1","FREEMYTV_KEYSTORE":"/missing.jks",
               "FREEMYTV_KEYSTORE_PASS":"secret"},"keystore permanente")
    with TemporaryDirectory() as folder:
        dummy=Path(folder)/"not-a-real-keystore.test"
        dummy.write_text("This is a path-existence test only, never a signing key.")
        base={"FREEMYTV_RELEASE_MODE":"1","FREEMYTV_KEYSTORE":str(dummy),
              "FREEMYTV_KEYSTORE_PASS":"test-placeholder"}
        expected=(True,"io.freemytv.tvlauncher","1.0.0","100","Free My TV")
        assert mod.build_identity(base)==expected
        assert mod.build_identity(dict(base,FREEMYTV_RELEASE_VERSION_CODE="101",
                    FREEMYTV_RELEASE_VERSION_NAME="1.0.1"))==(True,"io.freemytv.tvlauncher","1.0.1","101","Free My TV")
        must_fail(dict(base,FREEMYTV_RELEASE_VERSION_CODE="0"),"versionCode")
        must_fail(dict(base,FREEMYTV_RELEASE_VERSION_CODE="-4"),"versionCode")
        must_fail(dict(base,FREEMYTV_RELEASE_VERSION_CODE="1e5"),"versionCode")
        must_fail(dict(base,FREEMYTV_RELEASE_VERSION_NAME="test"),"versionName")
        must_fail(dict(base,FREEMYTV_RELEASE_VERSION_NAME="1.0.0-beta"),"versionName")
        must_fail(dict(base,FREEMYTV_KEYSTORE_PASS=""),"keystore permanente")
    print("PASS phase10: preview separated; production package fixed; no release without key +"
          " password; monotonic release-code range and version format validated.")


if __name__=="__main__":
    main()
