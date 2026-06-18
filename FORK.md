# Fork features

This fork adds three things on top of upstream Grayjay. Each change is intentionally
small and localized to keep merges with upstream easy.

## 1. Open a video without playing (read comments only)

Opens a video paused instead of auto-playing, so you can read the comments without
streaming the video.

- **Global setting:** Settings → Player → *Open videos without playing*. Every video
  then opens paused; press play to start it.
- **Per-video action:** long-press a video in a feed → *Open without playing*.

Implementation: a single `playWhenReady` flag threaded into
`VideoDetailView.setVideo()/setVideoOverview()` (gated by
`Settings.playback.openWithoutPlaying`). Files: `Settings.kt`, `VideoDetailView.kt`,
`VideoDetailFragment.kt`, `models/PlatformVideoWithTime.kt`, `Utility.kt`,
`ContentFeedView.kt`, `res/values/strings.xml`.

Note: with the player paused, ExoPlayer may still pre-buffer a small amount; it will
not play or download the full video. A fully "zero bytes until play" mode would
require deeper player changes.

## 2. Auto-download new subscription videos matching filters

Automatically downloads **new** uploads (never the back-catalog) from subscribed
channels that match title/length filters.

- **Global defaults:** Settings → *Subscription auto-download*: master enable, target
  quality, min/max length, and a default title regex.
- **Per-channel override:** long-press a subscription → *Auto-download new videos*
  toggle + *Auto-download filters…* (title regex + min/max minutes; blank = inherit
  the global default).

How "only new" works: each subscription stores `autoDownloadSince`, armed to "now" the
first time auto-download becomes active, so only uploads published afterwards qualify.
Evaluation runs in the existing background subscription poll
(`BackgroundWorker` → `SubscriptionAutoDownloader`) and reuses
`StateDownloads.download(...)`, so downloads respect your normal download network
settings and dedupe automatically.

Files: `models/Subscription.kt`, `Settings.kt`,
`subscription/SubscriptionAutoDownloader.kt`, `background/BackgroundWorker.kt`,
`UISlideOverlays.kt`, `res/values/strings.xml`.

## 3. Build & deliver an APK, stay current with upstream

GitHub Actions workflow `.github/workflows/build-release.yml` (manual trigger) builds
an APK and publishes it as a GitHub Release.

Run it from the repo's **Actions** tab → *Build & Release APK* → *Run workflow*.
Defaults: `unstable` flavor (installs side-by-side with official Grayjay) + `debug`
build (no signing secrets needed → immediately installable). For a signed `release`
build, add the `KEYSTORE_*` secrets documented at the top of the workflow file.

### Keeping up to date with upstream

Upstream lives on GitLab. Sync locally, then run the workflow:

```bash
git remote add upstream https://gitlab.futo.org/videostreaming/grayjay.git   # once
git fetch upstream
git checkout claude/android-player-fork-brainstorm-0sv2ly
git merge upstream/master      # resolve conflicts in the few feature files if any
git push origin claude/android-player-fork-brainstorm-0sv2ly
```

### Build prerequisite (important)

The app depends on git submodules — the Polycentric / FutoPay `includeBuild` libraries
(`dep/polycentricandroid`, `dep/futopay`) and the bundled plugin sources. These are
private FUTO repositories referenced by relative submodule URLs. CI checks out
submodules recursively, but the build will only succeed if those submodules are
reachable from your GitHub account/runner. If they are not, mirror them into your org
or build locally where you have access.
