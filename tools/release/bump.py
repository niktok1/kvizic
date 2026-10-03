#!/usr/bin/env python3
"""Bumps the app's version where it is named: kvizic.app.version in gradle.properties, and the iOS app's
MARKETING_VERSION and CURRENT_PROJECT_VERSION in app/iosApp/Configuration/Config.xcconfig, which every build
checks say the same (gradle/kvizic-version.gradle.kts). Prints the new version.

    tools/release/bump.py patch|minor|major     1.0.1 -> 1.0.2, 1.1.0, 2.0.0
    tools/release/bump.py check                 prints the version, failing if the two files differ
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GRADLE = ROOT / "gradle.properties"
XCCONFIG = ROOT / "app/iosApp/Configuration/Config.xcconfig"


def build_number(version):
    major, minor, patch = version
    return major * 10_000 + minor * 100 + patch


def current():
    found = re.search(r"^kvizic\.app\.version=(\d+)\.(\d+)\.(\d+)\s*$", GRADLE.read_text(), re.M)
    if not found:
        sys.exit("gradle.properties names no kvizic.app.version=MAJOR.MINOR.PATCH")
    version = tuple(int(part) for part in found.groups())
    ios = XCCONFIG.read_text()
    marketing = re.search(r"^MARKETING_VERSION\s*=\s*(\S+)", ios, re.M)
    project = re.search(r"^CURRENT_PROJECT_VERSION\s*=\s*(\S+)", ios, re.M)
    shown = ".".join(map(str, version))
    if not marketing or not project or marketing.group(1) != shown or project.group(1) != str(build_number(version)):
        sys.exit(f"Config.xcconfig does not say {shown} ({build_number(version)})")
    return version


def main():
    part = sys.argv[1] if len(sys.argv) == 2 else ""
    major, minor, patch = current()
    if part == "check":
        print(f"{major}.{minor}.{patch}")
        return
    bumped = {"patch": (major, minor, patch + 1), "minor": (major, minor + 1, 0), "major": (major + 1, 0, 0)}
    if part not in bumped:
        sys.exit(__doc__)
    version = bumped[part]
    if version[1] >= 100 or version[2] >= 100:
        sys.exit("MINOR and PATCH must stay below 100: bump the next part up")
    shown = ".".join(map(str, version))
    GRADLE.write_text(re.sub(r"^kvizic\.app\.version=.*$", f"kvizic.app.version={shown}", GRADLE.read_text(), flags=re.M))
    ios = re.sub(r"^MARKETING_VERSION\s*=.*$", f"MARKETING_VERSION={shown}", XCCONFIG.read_text(), flags=re.M)
    ios = re.sub(r"^CURRENT_PROJECT_VERSION\s*=.*$", f"CURRENT_PROJECT_VERSION={build_number(version)}", ios, flags=re.M)
    XCCONFIG.write_text(ios)
    current()
    print(shown)


if __name__ == "__main__":
    main()
