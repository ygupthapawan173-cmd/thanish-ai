#!/usr/bin/env python3
"""Only for CI: turn the debug APK into a side-by-side Gemini diagnostic build.

Do not publish this APK as a release: the additional Android package is not
registered as a Firebase app. Direct user-key Gemini API testing still works.
The regular production package and credential vault remain unchanged in git.
"""
import copy
import json
from pathlib import Path

gradle = Path("app/build.gradle.kts")
source = gradle.read_text(encoding="utf-8")
marker = "    debug {\n"
assert source.count(marker) == 1, "Unexpected Android debug build configuration"
gradle.write_text(
    source.replace(marker, marker + '      applicationIdSuffix = ".diagnostic"\n', 1),
    encoding="utf-8",
)

google = Path("app/google-services.json")
cfg = json.loads(google.read_text(encoding="utf-8"))
assert len(cfg["client"]) == 1, "Unexpected Firebase project client configuration"
original = cfg["client"][0]["client_info"]["android_client_info"]["package_name"]
assert original == "com.aistudio.pandubabu.kxmpzq", "Unexpected app package"
extra = copy.deepcopy(cfg["client"][0])
extra["client_info"]["android_client_info"]["package_name"] = original + ".diagnostic"
cfg["client"].append(extra)
google.write_text(json.dumps(cfg, indent=2), encoding="utf-8")

strings = Path("app/src/main/res/values/strings.xml")
content = strings.read_text(encoding="utf-8")
old = '<string name="app_name">OMEGA JARVIS</string>'
assert content.count(old) == 1, "Unexpected app name"
strings.write_text(content.replace(old, '<string name="app_name">OMEGA DIAGNOSTIC</string>'), encoding="utf-8")
print("Prepared isolated debug APK: " + original + ".diagnostic")
