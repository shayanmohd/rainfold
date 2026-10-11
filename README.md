# Rainfold

Rainfold is a noise and ambience mixer for people who want a constant sound over traffic, a snoring partner or an open-plan office, all night or all day. White, pink, brown and green noise, a fan and rain are synthesised live in plain Kotlin (no loop files, so there is no seam), mixed in layers you shape on the Fold, and played by a Media3 foreground service with a sleep timer that fades out over minutes. Three mixes save for free, every mix exports to a JSON file, and a home-screen widget resumes the last mix in one tap.

Everything runs on the device. The app declares no network permission and sends nothing anywhere.

The app is free. One optional one-time purchase (`full_unlock`) unlocks everything, through Google
Play Billing: the Play Billing Library talks to the Google Play Store app on the phone, and the
INTERNET permission its telemetry dependency would add is removed in `AndroidManifest.xml`. The
unlock is read back from Google Play on every start and cached in DataStore (`entitlement`), which is
left out of backups.

## Build

Requires JDK 17 and the Android SDK with platform 36.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew bundleRelease       # needs keystore.properties, see below
```

`keystore.properties` and the `.jks` are not committed. Without them the release build stays
unsigned instead of failing:

```properties
storeFile=<slug>-upload.jks
storePassword=...
keyAlias=<slug>
keyPassword=...
```

## Layout

```
app/src/main/kotlin/com/mohdshayan/rainfold/
  App.kt, MainActivity.kt   process and activity entry points
  di/                       ServiceLocator, the manual dependency container
  core/                     pure Kotlin, no Android imports, unit tested on the JVM:
    dsp/                    Rng, noise colours, Biquad, Tilt, Smoother, Limiter
    engines/                every sound engine, the droplet pool and its Poisson scheduler
    mix/                    EngineKind, Mixer, FreeTier, FadeCurve and SleepPlan, EvolvingDrift, MixCodec
    spectrum/FoldCurve.kt   the band edges the Fold draws, from the engines' own filter maths
  audio/                    AudioOut (float AudioTrack and render thread), PreviewPlayer
  playback/                 PlaybackService (MediaSessionService), NoisePlayer, FocusController, PlayerLink
  widget/, tile/            the RemoteViews widget and the Quick Settings tile
  billing/                  the template's billing core (Entitlement, UnlockRepository)
  data/                     MixRepository, SessionRepository, MixFiles; data/db Room; data/prefs DataStore
  ui/theme/                 colour, type, shape and motion tokens, AppTheme
  ui/nav/                   AppNav and the type-safe routes
  ui/listen, ui/mixes, ui/settings, ui/info, ui/unlock, ui/resume   screens and the widget trampoline
  ui/components/            reusable composables, the unlock gate helpers
app/src/test/               JVM unit tests
store/                      Play listing copy, icon, feature graphic, raw and captioned screenshots
docs/                       GitHub Pages site: landing page, privacy policy, fonts and font licences
```

## Licence

Copyright SocialSure Private Limited.

Google Play Billing Library: Android Software Development Kit License.

Fonts, bundled under `app/src/main/res/font/`: Bricolage Grotesque (Copyright 2022 The Bricolage
Grotesque Project Authors) and Figtree (Copyright 2022 The Figtree Project Authors), both under the
SIL Open Font License 1.1, with the licence texts in `docs/OFL-BricolageGrotesque.txt` and
`docs/OFL-Figtree.txt`. The static weights were instanced from the Google Fonts variable files. The
same files are copied to `docs/fonts/` for the landing page and to `store/fonts/` for the screenshot
captions, under the same licence.

Sounds: none are bundled. Every sound, including thunder, crickets and cafe murmur, is synthesised
on the device, so there are no third-party audio licences.

AndroidX, Media3, Kotlin and kotlinx libraries: Apache License 2.0.
