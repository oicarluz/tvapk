#!/usr/bin/env python3
"""Preflight policy checks for the manual production workflow.

Checks the checked-in release workflow so a refactor cannot accidentally
remove required approval, package ID, signing gate or certificate pinning.
Does not sign APKs and does not access secrets.
"""
from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
workflow=(root/".github/workflows/release-definitivo.yml").read_text(encoding="utf-8")
required=[
    "workflow_dispatch:",
    "if: github.ref == 'refs/heads/main'",
    "environment: production",
    "TCL_APROVADA",
    "secrets.FREEMYTV_KEYSTORE_BASE64",
    "secrets.FREEMYTV_KEYSTORE_PASS",
    "vars.FREEMYTV_RELEASE_CERT_SHA256",
    "FREEMYTV_RELEASE_MODE: \"1\"",
    'io.freemytv.tvlauncher',
    'build.sh --generic',
    'zipalign',
    'apksigner',
    'SIGNING_PASSWORD',
    'test "$CERT" = "$EXPECTED"',
    "actions/upload-artifact@v4",
    "LICENSE-MIT-Free-My-TV",
]
# The release bundle ships the MIT notice under a distinct but equivalent name.
required[-1]="LICENSE-MIT-Free-My-TV.txt"
# Name is LICENSE-MIT-Free-My-TV.txt in produced package.
for token in required:
    assert token in workflow, f"Missing release guard: {token}"
assert not re.search(r"(?m)^\s{2}push:",workflow), "Production must never trigger on push"
assert "actions/create-release" not in workflow
assert "gh release create" not in workflow
assert "adb install" not in workflow, "CI cannot install on user's TCL"
assert ".jks" not in workflow.split("path: dist/*")[-1], "Keystore in published artifact"
assert "github.ref == 'refs/heads/main'" in workflow
print("PASS: production CI is manual, main-only, signed, certificate-pinned, no automatic release.")
