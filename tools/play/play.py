#!/usr/bin/env python3
"""Google Play's releases through the Play Developer API, with Google's own client and no third party
holding the service account's key. The key is the JSON in PLAY_SERVICE_ACCOUNT_JSON, read from the
environment and never written anywhere. Every change is one edit, committed at the end or not at all.

    play.py upload BUNDLE --track internal [--notes-sr TEXT] [--notes-en TEXT]
    play.py promote --from internal --to beta|production [--fraction 0.2] [--code 10002]
    play.py rollout --track production --fraction 0.5     a staged release's share; 1.0 completes it
    play.py halt --track production                       stops a staged release reaching more players
    play.py resume --track production                     starts a halted one again, at its share
    play.py status

Tracks: internal (Internal testing), beta (Open testing, the early access), production. Promoting copies
the release on --from's track (the one holding --code, if named), its version codes and notes; a fraction
under 1.0 stages it. A promotion Play refuses because the track is not live yet (its first release still in
review, so it targets no countries) exits with NOT_LIVE (3), which the queue (play-queue.yml) retries.
"""
import argparse
import json
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

PACKAGE = "io.ntole.kvizic"

# The exit status of a promotion to a track that is not live yet: try again later, nothing is wrong.
NOT_LIVE = 3
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]


def publisher():
    raw = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        sys.exit("PLAY_SERVICE_ACCOUNT_JSON is not set")
    credentials = service_account.Credentials.from_service_account_info(json.loads(raw), scopes=SCOPES)
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


class Edit:
    """One Play edit: opened, changed, and committed only if everything in it worked."""

    def __init__(self, api):
        self.api = api
        self.edits = api.edits()
        self.id = self.edits.insert(packageName=PACKAGE, body={}).execute()["id"]

    def track(self, name):
        return self.edits.tracks().get(packageName=PACKAGE, editId=self.id, track=name).execute()

    def set_releases(self, name, releases):
        body = {"track": name, "releases": releases}
        self.edits.tracks().update(packageName=PACKAGE, editId=self.id, track=name, body=body).execute()

    def commit(self):
        try:
            self.edits.commit(packageName=PACKAGE, editId=self.id).execute()
        except HttpError as refused:
            if "targeting no countries" in str(refused):
                print("the track is not live yet (its first release is still in review): nothing changed")
                sys.exit(NOT_LIVE)
            raise


def notes(sr, en):
    given = [("sr", sr), ("en-US", en)]
    return [{"language": language, "text": text} for language, text in given if text]


def staged(release, fraction):
    """[release] at [fraction] of players: in progress under 1.0, completed at it."""
    release = {key: value for key, value in release.items() if key not in ("userFraction", "status")}
    if fraction < 1.0:
        return {**release, "status": "inProgress", "userFraction": fraction}
    return {**release, "status": "completed"}


def beside(track, release):
    """A track's releases with [release] on it: a staged one keeps the completed release the rest still get."""
    if release["status"] == "completed":
        return [release]
    kept = [r for r in track.get("releases", []) if r.get("status") == "completed"]
    return kept + [release]


def latest(track):
    """The release a track is serving or rolling out: the newest by version code, never a draft."""
    live = [r for r in track.get("releases", []) if r.get("status") in ("completed", "inProgress", "halted")]
    if not live:
        sys.exit(f"the {track['track']} track has no release")
    return max(live, key=lambda r: max(int(code) for code in r.get("versionCodes", ["0"])))


def upload(args):
    edit = Edit(publisher())
    bundle = edit.edits.bundles().upload(
        packageName=PACKAGE,
        editId=edit.id,
        media_body=MediaFileUpload(args.bundle, mimetype="application/octet-stream", resumable=True),
    ).execute()
    code = str(bundle["versionCode"])
    release = {"versionCodes": [code], "status": "completed", "releaseNotes": notes(args.notes_sr, args.notes_en)}
    edit.set_releases(args.track, [release])
    edit.commit()
    print(f"version code {code} is on the {args.track} track")


def holding(track, code):
    """The release on [track] whose version codes include [code]."""
    for release in track.get("releases", []):
        if code in release.get("versionCodes", []):
            return release
    sys.exit(f"the {track['track']} track has no release of version code {code}")


def promote(args):
    edit = Edit(publisher())
    track = edit.track(getattr(args, "from"))
    source = holding(track, str(args.code)) if args.code else latest(track)
    release = staged(
        {key: source[key] for key in ("name", "versionCodes", "releaseNotes") if key in source},
        args.fraction,
    )
    edit.set_releases(args.to, beside(edit.track(args.to), release))
    edit.commit()
    print(f"version codes {', '.join(release['versionCodes'])} promoted to {args.to}: {describe(release)}")


def change(args, status=None):
    edit = Edit(publisher())
    track = edit.track(args.track)
    current = latest(track)
    if status == "halted":
        if current.get("status") != "inProgress":
            sys.exit(f"the {args.track} track's release is {current.get('status')}, not a staged rollout to halt")
        changed = {**current, "status": "halted"}
    elif status == "resume":
        if current.get("status") != "halted":
            sys.exit(f"the {args.track} track's release is {current.get('status')}, not halted")
        changed = {**current, "status": "inProgress"}
    else:
        changed = staged(current, args.fraction)
    others = [r for r in track.get("releases", []) if r is not current]
    edit.set_releases(args.track, [changed] if changed["status"] == "completed" else others + [changed])
    edit.commit()
    print(f"{args.track}: {describe(changed)}")


def describe(release):
    fraction = release.get("userFraction")
    share = f" to {fraction:.0%} of players" if fraction else ""
    return f"{', '.join(release.get('versionCodes', []))} {release.get('status')}{share}"


def newest(args):
    """Prints the newest version code on a track, for the queue to pin what it was asked to promote."""
    edit = Edit(publisher())
    try:
        print(max(int(code) for code in latest(edit.track(args.track)).get("versionCodes", [])))
    finally:
        edit.edits.delete(packageName=PACKAGE, editId=edit.id).execute()


def status(_):
    """Reads every track, and changes nothing: the edit it reads through is thrown away."""
    edit = Edit(publisher())
    try:
        tracks = edit.edits.tracks().list(packageName=PACKAGE, editId=edit.id).execute().get("tracks", [])
        print(f"Play answers for {PACKAGE}: {len(tracks)} tracks")
        for track in tracks:
            releases = track.get("releases", [])
            if not releases:
                print(f"{track['track']}: no release")
            for release in releases:
                print(f"{track['track']}: {release.get('name', '')} {describe(release)}")
    finally:
        edit.edits.delete(packageName=PACKAGE, editId=edit.id).execute()


def fraction(text):
    value = float(text)
    if not 0.0 < value <= 1.0:
        raise argparse.ArgumentTypeError("a share of players above 0 and at most 1.0")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    # Any track Play names: internal, alpha, beta (Open testing), production, and the console's own, such as the
    # Early access track, which the API calls by a name of its own (status lists them).
    tracks = None

    up = commands.add_parser("upload")
    up.add_argument("bundle")
    up.add_argument("--track", default="internal", choices=tracks)
    up.add_argument("--notes-sr", default="")
    up.add_argument("--notes-en", default="")
    up.set_defaults(run=upload)

    pro = commands.add_parser("promote")
    pro.add_argument("--from", required=True, choices=tracks)
    pro.add_argument("--to", required=True, choices=tracks)
    pro.add_argument("--fraction", type=fraction, default=1.0)
    pro.add_argument("--code", type=int, help="the version code to promote; the newest when left out")
    pro.set_defaults(run=promote)

    roll = commands.add_parser("rollout")
    roll.add_argument("--track", default="production", choices=tracks)
    roll.add_argument("--fraction", type=fraction, required=True)
    roll.set_defaults(run=change)

    for name, verb in (("halt", "halted"), ("resume", "resume")):
        sub = commands.add_parser(name)
        sub.add_argument("--track", default="production", choices=tracks)
        sub.set_defaults(run=lambda args, verb=verb: change(args, verb))

    commands.add_parser("status").set_defaults(run=status)

    new = commands.add_parser("newest")
    new.add_argument("--track", required=True, choices=tracks)
    new.set_defaults(run=newest)

    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
