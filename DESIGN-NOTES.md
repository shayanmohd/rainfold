# Rainfold design notes

**Design read.** Reading this as: a night-and-focus sound instrument for adults who want sound to run untouched for hours, with a rain-on-glass and mixing-desk language (stacked sheets of water, a spectrum edge, one cyan light), leaning toward Bricolage Grotesque plus Figtree on a rain cyan and storm petrol palette. Dark mode carries the brand.

**Dials.**
- DESIGN_VARIANCE 4: one composed screen (Listen, with the Fold), calm lists and plain rows everywhere else.
- MOTION_INTENSITY 2: nothing moves unless touched. The Fold redraws inside the drag frame; the Play glyph crossfades in 150 ms; sheets use the platform slide. Every animation reads LocalReducedMotion and snaps.
- VISUAL_DENSITY 3: big targets (48dp) for a hand in the dark, generous spacing, few words per screen.

**Tokens** (blueprint section 7, unchanged): Mist #E9F1F2 / #0E1E23, Pane #F6FAFA / #16292F, Ink #12262C / #E1EDEE, Drizzle #4A6066 / #93ABB0, Rain Cyan #1A7486 / #6FC2D3, Rule #C6D5D8 / #27393F. On-primary is Pane in light, Mist in dark. No red: an error is Ink plus an icon.

**Type.** Bricolage Grotesque SemiBold for titles (24sp) and the timer readout (40sp, tabular digits), Medium for mix names (18sp). Figtree Regular body 16sp, Medium labels 14sp, SemiBold buttons 15sp.

**Shape.** RadiusSm 6dp (chips, fields), RadiusMd 14dp (buttons, rows), RadiusLg 28dp (sheet tops, the Fold). The Play button is a full circle, the one documented exception.

**The one memorable thing.** The Fold: stacked horizontal bands, one per layer, each band's top edge the layer's real spectrum shape (sampled from the same filter maths the audio uses), height its volume, filled with Rain Cyan at falling opacity and edged with a 2dp full-opacity cyan stroke. Drag up or down for volume, sideways for tone. It never moves on its own. Everything around it stays quiet: no cards on Listen, layer rows separated by spacing, one accent.

**Plan review against the generic default.** The default for a "sleep sounds" app is a grid of round sound tiles with photos and a purple night gradient. Rainfold drops the tile grid entirely: sounds live in a plain picker sheet, and the main screen is the Fold, a drawn object specific to this app. No gradients inside the app, no glow, no illustration, no all-caps labels.

**Deviations recorded during the build** (also in the final report):
- One Gradle module, with the pure Kotlin engine under `com.mohdshayan.rainfold.core` (no Android imports), instead of a separate `:core` module, because `build.sh` runs and counts only `:app:testDebugUnitTest`.
- Thunder, crickets and cafe murmur are synthesised like every other sound, so no CC0 recordings are bundled, there is no TextureLoader or MediaExtractor path, and the listing says "Crickets and cafe murmur" instead of "Recorded crickets and cafe murmur".
- The in-app review prompt (review-ktx) is not included; the recipe marks it optional.
- Export goes through the system document picker (CreateDocument); there is no separate share-sheet export.
- All buttons use RadiusMd (14dp); the Play circle is the one exception, as section 7 says.
