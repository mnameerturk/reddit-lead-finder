# Lead Finder

Android app that watches subreddits in the background and notifies you about posts from people
you could help (e.g. beginner YouTubers asking how to grow or get monetized), with an AI-drafted
reply ready to copy.

- Reads Reddit's public RSS feeds, with all subreddits in one request per check. No Reddit login or API key needed.
- Filters by your keywords, then Google Gemini (free tier) rates each match 0–10 against a plain-English
  description of the posts you want, and only posts above your minimum score are shown.
- Opening a lead writes a reply draft. "Copy reply & open on Reddit" copies it and opens the post so you can paste it.
  The app never posts or sends DMs by itself.
- Everything is editable in Settings: subreddits, include/skip keywords, post age, what you're looking for,
  reply style, check interval, Wi-Fi only, pause on low battery.

## Battery

Checks run with Android WorkManager (default every 30 min, minimum 15), only when there's internet,
and are skipped when the battery is low. Each check is one small download plus at most a few AI calls
when new matching posts appear.

## Download

Every push to `main` builds an APK and publishes it at
**[Releases → latest](https://github.com/mnameerturk/reddit-lead-finder/releases/latest)**.

## Signing

`signing/leadfinder.jks` is a fixed key so each new build installs over the previous one.
It's committed for convenience in this personal project.
