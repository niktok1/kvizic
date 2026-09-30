# :app:designsystem

Kvizić's skin engine and the components every screen draws with (package `io.ntole.kvizic.design`).
No Material: its components draw themselves their own way, and a skin here changes shapes, depth,
motion, renderers and art, not only colours.

## Structure

| Package | What |
|---|---|
| `skin` | `Skin` and its tokens: `SkinColors`, `SkinFonts`, `SkinTypeScale`, `SkinShapes`, `SkinDepth` (`HardOffset`, `Soft`, `Sketched`), `SkinSpace`, `SkinMotion`, `SkinParts`, `Backdrop`, `AvatarPalette`; `KvizicSkin { }`, `KvizicTheme`, `Skins` |
| `component` | What screens call: `AnswerTile`, `AnswerGrid`, `QuestionText`, `WaitingFor`, `FlipNumber`, `CodeDisplay`, `QuestionTimer`, `Avatar`, `AvatarStack`, `StageButton`, `StageIconButton`, `Panel`, `Chip`, `Podium`, `ReactionBurst`, `Spinner`, `Wordmark`, `Stage`, `KvizicText`, `KvizicIcon` |
| `font` | The bundled faces (`Faces`) and their licences (`FontLicences`) |
| `avatar` | The avatars, drawn in code for any `AvatarPalette` (fox, owl, hedgehog, bear; a silhouette for the rest) |
| `icon` | Every icon, drawn by hand on a 24 grid (`KvizicIcons`) |
| `skins.buzzers` | Skin #1, the game show |
| `skins.notebook` | Skin #2, rough, the proof that a skin swaps more than colours |

A component owns its layout, semantics, clicks and touch target, and asks its skin's part only how to
look: which `SurfaceLook` to stand on (drawn by the skin's depth) and what to draw in the draw phase.

## Rules the tests hold

- **One source of truth**: no colour, length or type size outside a skin's own package
  (`NoLiteralsTest` reads the component, engine and mockup sources).
- **Motion is draw-only**: every animation is read in a draw, layer or placement lambda, so a frame
  composes nothing and changes no semantics. A press is collected by the surface's own `Modifier.Node`;
  flaps and the timer lay their text out once and draw the rest. `MotionCompositionTest` counts every
  scope composed across the frames of each motion, in every skin.
- **Contrast**: every text and icon pair a skin draws, its backdrop's colours under the page's text
  included, at WCAG AA (`SkinContrastTest`).
- **Every component in every skin** (`ComponentsDrawTest`), **2 to 5 answers** laid out by count, never
  as four (`AnswerGridLayoutTest`), and **fonts** that cover both Serbian alphabets (`FontCoverageTest`).

## Adding a skin

One package under `skins/` with a `Skin` value (tokens, parts, backdrop, avatar palette) and one line in
`Skins.ALL`. Every test iterates `Skins.ALL`, so a new skin is held to all of the above at once.

## Fonts

| Face | Files | Source |
|---|---|---|
| Fira Sans Compressed (Google Fonts' *Extra Condensed*), Heavy and Bold: the default display | `fira_sans_compressed_*.ttf` | bBoxType/FiraSans 4.301, `f54eeb3` |
| Fira Sans, Regular and SemiBold: the text face | `fira_sans_*.ttf` | bBoxType/FiraSans 4.301, `f54eeb3` |
| Oswald, Bold and SemiBold: to compare | `oswald_*.ttf` | googlefonts/OswaldFont, `8979526` |
| Sofia Sans Extra Condensed, Black and Bold: to compare, and Notebook's display | `sofia_sans_extra_condensed_*.ttf` | lettersoup/Sofia-Sans, `9a1f0ba` |

Each is under the SIL Open Font License, bundled beside it in `composeResources/files/licenses` for the
About screen (`FontLicences`). The display face is switchable to compare: `Skins.Buzzers.withDisplay(Faces.Oswald)`.

**Serbian forms.** Serbian and Macedonian draw б (and italic г д п т) differently from Russian. A font
that carries both switches with the language, through its `locl` feature, so every style here sets
`localeList` to `sr-Cyrl` or `sr-Latn` (`Script`). What was found:

- Only the foundry's **Fira 4.301** has the Serbian forms. Google Fonts' copy of Fira (4.203) has no
  Serbian language system at all, which is why the foundry's files are bundled.
- **Oswald** and **Sofia Sans** have none. Sofia's Cyrillic defaults are its foundry's Bulgarian forms
  (в, г, д, к, т as Bulgarian letters), with Russian ones only for a Russian locale: wrong for Serbian
  in either script.
- The locale reaches the shaper on **Android**. On **desktop, iOS and web**, Compose 1.11.1's text
  (`ParagraphBuilder.skiko`) does not pass `localeList` to Skia, so there б shows its Russian form,
  and so it does in the desktop-rendered mockups. The glyph sheets are shaped with the locale, as
  Android shapes them.

A later step freezes the Serbian forms as the default glyphs (e.g. opentype-feature-freezer's
`pyftfeatfreeze -f locl -s cyrl -l SRB -R 'Fira Sans/Kvizic Sans'`), so every platform draws them, and
renames the families, since the OFL keeps Reserved Font Names from a modified font (these copies
declare none, but a modified font under its original name misleads anyway).

## Mockups and sheets

```
KVIZIC_DESIGN_DIR=/Users/nikolatokic/Projects/kvizic/design-review \
  ./gradlew :app:designsystem:jvmTest --tests '*DesignShotsTest*' --rerun
```

writes the screens, at 375 by 667 and twice a pixel a dp: `buzzers-01-home` to `buzzers-09-results`,
three of them in Notebook (`notebook-*`), Home and play in the other display faces (`face-*`), and the
other tile scheme to choose from, a buzzer in each answer's colour (`tiles-coloured-*`, a test-only
part, `ColouredBuzzers.kt`). A whole `jvmTest` with the variable set also writes each skin's component
sheet (`components-*`) and each face's glyph sheet (`glyphs-*`). Without it the tests draw and check,
and write nothing. The screens are built in `Mockups.kt` from the components and tokens alone, as a
real screen will be.
