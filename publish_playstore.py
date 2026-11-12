#!/usr/bin/env python3
import argparse
import os
import sys
import random
import time
import httplib2
import socket

from google_auth_httplib2 import AuthorizedHttp
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
from googleapiclient.errors import HttpError
from googleapiclient.http import build_http

SCOPE = "https://www.googleapis.com/auth/androidpublisher"
socket.setdefaulttimeout(30 * 60)

def die(msg: str, code: int = 1):
    print(msg, file=sys.stderr)
    raise SystemExit(code)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sa", required=True, help="Service account JSON file path")
    ap.add_argument("--package", required=True, help="ApplicationId / package name")
    ap.add_argument("--aab", required=True, help="Path to .aab file")
    ap.add_argument("--track", default="internal", help="internal|alpha|beta|production")
    ap.add_argument("--status", default="completed", help="draft|inProgress|halted|completed")
    ap.add_argument("--name", default=None, help="Release name (defaults to CI_COMMIT_TAG)")
    ap.add_argument("--rollout", type=float, default=None, help="For staged rollout: 0 < rollout < 1")
    args = ap.parse_args()

    if not os.path.isfile(args.sa):
        die(f"Missing service account JSON: {args.sa}")
    if not os.path.isfile(args.aab):
        die(f"Missing AAB: {args.aab}")

    release_name = args.name or os.environ.get("CI_COMMIT_TAG")
    if not release_name:
        die("Missing release name: pass --name or set CI_COMMIT_TAG")

    staged = args.status in ("inProgress", "halted")
    if staged:
        if args.rollout is None:
            die("--rollout is required when --status is inProgress or halted")
        if not (0.0 < args.rollout < 1.0):
            die("--rollout must satisfy 0 < rollout < 1")
    else:
        args.rollout = None

    print(f"Loading service account")

    creds = service_account.Credentials.from_service_account_file(
        args.sa, scopes=[SCOPE]
    )

    print(f"Loaded service account")


    print(f"Building service")
    http = build_http()
    authed_http = AuthorizedHttp(creds, http=http)
    service = build("androidpublisher", "v3", http=authed_http, cache_discovery=False)
    print(f"Built service")

    try:
        print(f"Creating edit")

        edit = service.edits().insert(body={}, packageName=args.package).execute()
        edit_id = edit["id"]

        UPLOAD_CHUNK_SIZE = 10 * 1024 * 1024
        MAX_RETRIES = 8

        print(f"Media upload started")

        media = MediaFileUpload(
            args.aab,
            mimetype="application/octet-stream",
            resumable=True,
            chunksize=UPLOAD_CHUNK_SIZE,
        )

        request = service.edits().bundles().upload(
            packageName=args.package,
            editId=edit_id,
            media_body=media,
        )

        response = None
        last_pct = -1
        attempt = 0

        while response is None:
            try:
                status, response = request.next_chunk(num_retries=3)
                attempt = 0  # reset after any successful chunk

                if status:
                    pct = int(status.progress() * 100)
                    if pct != last_pct:
                        last_pct = pct
                        print(f"Upload progress: {pct}%", flush=True)

            except HttpError as e:
                # Retry transient server-side errors with exponential backoff
                code = getattr(getattr(e, "resp", None), "status", None)
                if code in (500, 502, 503, 504) and attempt < MAX_RETRIES:
                    sleep_s = min(60, (2 ** attempt)) + random.random()
                    print(f"Transient HTTP {code}; retrying in {sleep_s:.1f}s...", flush=True)
                    time.sleep(sleep_s)
                    attempt += 1
                    continue
                raise

        print("Media upload finished")
        bundle = response
        version_code = bundle["versionCode"]

        release = {
            "name": release_name,
            "status": args.status,
            "versionCodes": [str(version_code)],
        }
        if args.rollout is not None:
            release["userFraction"] = args.rollout

        track_body = {"releases": [release]}

        print(f"Updating track")

        service.edits().tracks().update(
            packageName=args.package,
            editId=edit_id,
            track=args.track,
            body=track_body,
        ).execute()

        print(f"Updated track")
        print(f"Committing")

        service.edits().commit(packageName=args.package, editId=edit_id).execute()
        print(f"Committed")

        print(f"OK: package={args.package} track={args.track} status={args.status} versionCode={version_code} name={release_name}")
    except HttpError as e:
        content = e.content.decode("utf-8", errors="replace") if getattr(e, "content", None) else str(e)
        die(f"Google API error (HTTP {e.resp.status if e.resp else '??'}):\n{content}")
    except Exception as e:
        die(f"Unexpected error: {e}")

if __name__ == "__main__":
    main()
