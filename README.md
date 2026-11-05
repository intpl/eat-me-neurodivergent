# Eat Me, Neurodivergent! — landing page

A single static page for the Android app in
`/root/ZabawkaNotEncryptedSync/ObsidianOnZabawka/Projects/Eat-Me-Neurodivergent.md`
(app source: `/home/b/dev/eat-me-neurodivergent`, version 3.4.0).

No build step, no framework, no JavaScript. `index.html` opens straight from
disk; everything it needs is in this folder.

## Files

    index.html                 the page
    styles.css                 the page's CSS: the Hallmark stamp, then @imports
    tokens.css                 colour and type tokens, sampled from the app's colors.xml
    base.css                   reset and the rules Hallmark's floor already decided
    assets/screen-front.png    the app's front screen with a day of meals (hero)
    assets/screen-empty.png    a fresh install, nothing logged (Two buttons)
    assets/screen-gate.png     the permission gate (Install)
    assets/screen-settings.png the settings list (Privacy)
    assets/band.png            the 24-hour strip, cropped from the band screenshot
    assets/mark.png            launcher icon, 96px (nav and footer)
    assets/og.png              1200x630 card for link previews
    assets/fonts/              Instrument Sans (latin), the source for the base64 face in tokens.css
    download/                  3.4.0 APK and its .sha256, copied from the vault
    favicon.png, assets/apple-touch-icon.png

The page loads nothing from another domain. That is deliberate: the app has no
INTERNET permission and the page should not phone home either.

## The screenshots are real

They are Robolectric renders of the app's own screens, written by its screenshot
suite, not photographs of a phone and not mockups. To regenerate them:

    cd /home/b/dev/eat-me-neurodivergent
    ./env.sh
    gradle :app:testDebugUnitTest --tests '*Screenshot*'
    ls app/build/shots

Then re-crop and copy into `assets/` (see "Cropping" below). If the app's layout
changes, the crops need redoing: the page shows the shots at their natural size,
never upscaled.

## Publishing on GitHub Pages

The link the page points at is `https://github.com/intpl/eat-me-neurodivergent`,
which does not exist yet. When it does:

1. Push this folder to that repository (the page is at the root).
2. Repository → Settings → Pages → Source: `Deploy from a branch`, branch `main`,
   folder `/ (root)`.
3. The page is then served at `https://intpl.github.io/eat-me-neurodivergent/`
   and the repo link resolves. `.nojekyll` is already here so Pages serves the
   files as they are.

Until then every internal link works and the GitHub link is the only dead one.

## Store links

Neither listing exists. In `index.html`, the `#get` section (search for
`not listed yet`) has one row each for F-Droid and Google Play. Replace the
row's note with the listing URL and put the same URL on the name:

    <li>
      <a class="get__name" href="https://f-droid.org/...">F-Droid</a>
      <span class="get__note">version 3.4.0</span>
      <a class="btn" href="https://f-droid.org/...">Open F-Droid</a>
    </li>

Keep one filled `.btn` in the list: the direct APK, or move it to the row you
want to lead. Do not add a second filled button beside it.

Google Play may never work: the app declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
which is a Play policy hazard. The vault note records that trade.

## The APK

`download/eat-me-neurodivergent-3.4.0.apk` is a copy of the signed release in
`Projects/Eat-Me-Neurodivergent/`. Its SHA-256 is printed on the page and shipped
beside it:

    683f22c48fa8ebb08dd0bdee409f4dbfd9645c36ab309d8679024fb64d4fd3c9

When 3.5.0 exists, drop the new APK and `.sha256` in `download/`, update the
filename in the two `index.html` links, update the `3.4.0` heading, the size
line and the hash. The old APK can go.

## Cropping

The crops were made with PIL from the app's 1080 x 2340 renders:

- front screen: `(0, 48, 1080, 1890)`
- fresh install: `(0, 48, 1080, 1845)`
- gate: `(0, 40, 1080, 1295)`
- settings: `(0, 60, 1080, 2060)`
- band: `(74, 1352, 1006, 1624)` from `front-band.png`

## Design notes

The page follows the app's own palette (paper `#FFFBF5`, band `#F4EADD`, ink
`#1F1710`, accent `#B4652B`) rather than inventing one. `--color-accent` is the
app's orange darkened to `#A2551E` so small type and the button label clear
4.5:1 on paper; the screenshots keep the original.

The type is Instrument Sans, embedded as base64 in `tokens.css` so the page keeps
its own face when `index.html` is opened from disk: Chrome refuses to load a
woff2 over `file://`. The `.woff2` beside it is the source that base64 came from.
It is not Roboto: the page has to sit beside its own screenshots without looking
like Android System UI, and Roboto is the most worn display face on the web.

Checks run with Hallmark's `sloplint.mjs --render` (0 FAIL, 0 REFLEX, 0 WARN) at
1280x800, 1440x900, 1920x1080 and 375px. The render shots are in
`.hallmark/shots/`, which is gitignored.
