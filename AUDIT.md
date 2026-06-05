# Showlyfin — Audit plánu DEEP-COMET

**Datum:** 2026-06-05  
**Verze:** v0.5.2  
**Plán:** `/root/.claude/plans/je-realne-1-1-prenest-deep-comet.md`

---

## Fáze 0 — Gradle skeleton + CI/CD

| Bod z plánu | Stav | Důkaz | Poznámka |
|---|---|---|---|
| Gradle multimodule (Kotlin DSL + TOML version catalog) | ✅ hotovo | `settings.gradle.kts`, `gradle/libs.versions.toml` | |
| 16 modulů (app, core-*, data-*, feature-*, ui-*) | ✅ hotovo | 16× `build.gradle.kts` | |
| TV/phone runtime detection (FEATURE_LEANBACK) | ✅ hotovo | `app/…/MainActivity.kt` | |
| Hilt DI, OkHttp NetworkModule, Result<T> | ✅ hotovo | `core-network/NetworkModule.kt`, `core-domain/Result.kt` | |
| GitHub Actions: `release.yml` + `ci.yml` | ✅ hotovo | `.github/workflows/release.yml` — env vars správně předány | BUG-1 fix v0.5.2 |
| Keystore + GitHub secrets nastaveny | ✅ hotovo | git tagy v0.1.0–v0.5.2 (12 tagů) | |
| APK buildí a podpisuje se | ✅ hotovo | git tags: v0.1.0–v0.5.2 | |

---

## Fáze 1 — Data layer

| Bod z plánu | Stav | Důkaz | Poznámka |
|---|---|---|---|
| Port `data-trakt` | ✅ hotovo | `data/data-trakt/` — TraktApi.kt, TraktAuthManager.kt, TraktTokenProvider.kt, 27+ modelů | |
| Port `data-tmdb` | ✅ hotovo | `data/data-tmdb/` — TmdbApi.kt, TmdbService.kt, 10+ modelů | |
| Port `data-csfd` | ✅ hotovo | `data/data-csfd/CsfdScraper.kt`, `CsfdModule.kt` | |
| Port `data-uploader` | ✅ hotovo | `data/data-uploader/` — UploaderApi.kt, UploaderService.kt, UploaderModels.kt | |
| `data-jellyfin` — Jellyfin SDK | ✅ hotovo | `data/data-jellyfin/util/CoroutineContextApiClient.kt`, `JellyfinModule.kt` | |
| `core-data` — Room DB + DataStore | ✅ hotovo | `core/core-data/ShowlyfinDatabase.kt`, `CoreDataModule.kt` | Prázdné entity |
| **Testy pro každý data modul** | ❌ chybí | Žádné soubory v `*/test*/` adresářích | Explicitně v plánu, nikdy neimplementováno |

---

## Fáze 2 — Phone features

| Bod z plánu | Stav | Důkaz | Poznámka |
|---|---|---|---|
| `core-ui` — MediaCard composable | ✅ hotovo | `core/core-ui/src/…/MediaCard.kt` | |
| TMDB fetchMovieDetails + fetchShowDetails | ✅ hotovo | `data-tmdb/model/TmdbMovieDetails.kt`, `TmdbShowDetails.kt` | |
| `core-domain` — MediaItem + MediaType | ✅ hotovo | `core/core-domain/MediaItem.kt`, `MediaType.kt` | |
| `feature-discover` — Trakt discovery v Compose (tabs Movies/Shows, filtry) | ✅ hotovo | `feature-discover/ui/DiscoverScreen.kt`, `DiscoverViewModel.kt` | |
| `feature-discover` — search | ❌ chybí | `TraktSearchService.kt` + `TraktApi.fetchSearch()` existují v data vrstvě, ale žádný Search screen | Plán: "Trakt discovery, TMDB, search" |
| `feature-watchlist` — Watchlist (Trakt sync, isLoggedIn guard) | ✅ hotovo | `feature-watchlist/WatchlistScreen.kt`, `WatchlistViewModel.kt` | |
| `feature-watchlist` — Progress tracking | ❌ chybí | `WatchlistViewModel.kt` — žádné episode/progress endpointy | Plán: "Progress + watchlist v Compose" |
| `feature-detail` — Movie/Show detail (TMDB enrichment) | ✅ hotovo | `feature-detail/ui/DetailScreen.kt`, `DetailViewModel.kt` | |
| `feature-detail` — CSFD integrace | ❌ chybí | `grep -rn "csfd" feature-detail/` → prázdný výsledek; CsfdScraper v data-csfd není propojen | Plán: "TMDB + CSFD + Trakt" |
| `feature-jellyfin-browser` — setup form + library list | ✅ hotovo | `JellyfinBrowserScreen.kt:149` — onConnect button + username/password | |
| `feature-jellyfin-browser` — navigace do obsahu knihovny | ❌ chybí | `JellyfinBrowserScreen.kt` — žádný onClick na library cards; `ShowlyfinPhoneApp.kt` — žádný onLibraryClick callback | BUG-3 |
| `ui-phone` — BottomNavigation + state-based router | ✅ hotovo | `ShowlyfinPhoneApp.kt:42-65` — Destination sealed class + 5 tabů | V Fázi 4 rozšířeno na 5 tabů |

---

## Fáze 3 — TV mode

| Bod z plánu | Stav | Důkaz | Poznámka |
|---|---|---|---|
| `feature-playback` — ExoPlayer direct stream | ✅ hotovo | `PlaybackViewModel.kt:60` — `/Videos/$itemId/stream?static=true&api_key=$token`; `PlaybackScreen.kt:32` — `ExoPlayer.Builder` | |
| MPV přehrávač | ⚠️ odchylka | Žádný `mpv`/`libmpv` v codebase | Plán: "ExoPlayer/MPV" — explicitně scoped out "bez MPV, Fáze 3 scope" |
| `ui-tv` — TvNavDrawer, TvHomeScreen, TvItemCard, state-based nav | ✅ hotovo | `ui-tv/ui/TvNavDrawer.kt`, `TvHomeScreen.kt`, `TvItemCard.kt`, `TvDestination.kt` | |
| WebSocket PlayMessage handler | ✅ hotovo | `TvHomeViewModel.kt:103-122` — `setupPlayMessages()`, `subscribe<PlayMessage>()`, `postCapabilities` | |
| Jellyfin username/password auth | ✅ hotovo | `TvHomeViewModel.kt:66` — `accessToken = token` | |
| Trakt OAuth Settings tab + deep link `showlyfin://trakt` | ✅ hotovo | `AndroidManifest.xml:42` — `android:scheme="showlyfin" android:host="trakt"`; `SettingsScreen.kt` existuje | |
| Deep link `yellyfin://` (kompatibilita s Yellyfin) | ⚠️ odchylka | `AndroidManifest.xml` — žádný `yellyfin://` scheme registrován | Nový projekt má vlastní scheme; "Play on TV" funguje přes WebSocket |
| TV Jellyfin setup screen na TV | ❌ chybí | `TvHomeScreen.kt:46-63` — jen text "Otevři Showlyfin na telefonu", žádný formulář | BUG-2: TV nemůže nezávisle nastavit Jellyfin |
| TV drawer filter (Filmy/Seriály skutečně filtruje) | ❌ chybí | `TvNavDrawer.kt:44,57,70` — všechna 3 tlačítka volají `onNavigateHome` bez filtru | BUG-4 |
| D-Pad focus management | ❌ chybí | `grep -rn "FocusRequester" ui-tv/` → prázdný výsledek | Plán ho explicitně označil jako Opus 4.8 úkol |
| Resume items row (getResumeItems) | ❌ chybí | `TvHomeViewModel.kt` — žádná `getResumeItems` metoda | Open item |
| TV focus restorer | ❌ chybí | Žádný FocusRequester ani focus-restore logika v ui-tv | Open item |
| Title overlay fade-out animace | ❌ chybí | `TvItemCard.kt` — žádná animace/alpha fade | Open item |

---

## Fáze 4 — Remux + Uploader

| Bod z plánu | Stav | Důkaz | Poznámka |
|---|---|---|---|
| `feature-remux` — SmartDetect (LOADING→CONFIRM→PROGRESS→TRACK_SELECT→DONE/ERROR) | ✅ hotovo | `SmartDetectUiState.kt:8` — `enum class SmartDetectPhase { LOADING, CONFIRM, PROGRESS, TRACK_SELECT, DONE, ERROR }` | |
| `feature-remux` — RemuxPicker | ✅ hotovo | `RemuxPickerScreen.kt`, `RemuxPickerViewModel.kt` | |
| `feature-remux` — RemuxProgress | ✅ hotovo | `RemuxProgressScreen.kt`, `RemuxProgressViewModel.kt` | |
| `feature-remux` — RemuxHistory | ✅ hotovo | `RemuxHistoryScreen.kt`, `RemuxHistoryViewModel.kt` | |
| `feature-uploader` — UploaderScreen (TMM polling) | ✅ hotovo | `UploaderScreen.kt`, `UploaderViewModel.kt` | |
| `feature-uploader` — ReviewStep | ✅ hotovo | `ReviewStepScreen.kt`, `ReviewStepViewModel.kt` | |
| `feature-uploader` — MoveStep | ✅ hotovo | `MoveStepScreen.kt`, `MoveStepViewModel.kt` | |
| `feature-uploader` — LibraryBrowser | ✅ hotovo | `LibraryBrowserScreen.kt`, `LibraryBrowserViewModel.kt` | |
| `feature-uploader` — LibraryDetail + CollectionPickerDialog | ✅ hotovo | `LibraryDetailScreen.kt:70` — `CollectionPickerDialog(…)`; `LibraryDetailViewModel.kt:33` — `openCollectionPicker: Boolean` | |
| `feature-detail` — tlačítko "Smart Remux (4K + CZ audio)" | ✅ hotovo | `DetailScreen.kt:194-200` — `if (onSmartDetect != null && displayItem.imdbId != null)` | |
| `ShowlyfinPhoneApp` — 5 bottom tabů + hierarchická navigace | ✅ hotovo | `ShowlyfinPhoneApp.kt:44-60` — 5 Destination objektů + hierarchická back-stack logika | |

---

## Cross-cutting

| Bod z plánu | Stav | Důkaz | Poznámka |
|---|---|---|---|
| Launcher icon (plnohodnotná ikona) | ⚠️ odchylka | `app/src/main/res/drawable/ic_launcher.xml` — obdélník `#1A1A2E`, placeholder | Open item |
| Fáze 5 — Web companion / KMP | 🔄 otevřené | Žádné KMP soubory, žádný web modul | Plán: "budoucnost" |
| Verifikace (emulator + integrační testy) | ❌ chybí | Žádné test soubory, žádné emulator CI kroky | Plán má verifikační sekci |

---

## Shrnutí

| Kategorie | Počet |
|---|---|
| ✅ Hotovo | 28 |
| ❌ Chybí (plánováno, neimplementováno) | 9 |
| ⚠️ Odchylka (jinak než plán) | 3 |
| 🔄 Otevřené / future | 1 |

---

## Největší odchylky

**1. CSFD v detail screenu**  
`data-csfd/CsfdScraper.kt` existuje a kompiluje, ale není propojen do `feature-detail`. Detail zobrazuje jen TMDB data — žádné CZ překlady, žádné recenze.

**2. D-Pad focus management**  
Plán ho označil jako Opus 4.8 úkol ("focus bugs jsou zapeklité"). Žádný `FocusRequester` v ui-tv. Navigace D-Padem funguje jen díky výchozímu Compose TV chování, ne explicitnímu řízení.

**3. Search UI**  
`TraktSearchService` + `fetchSearch()` hotovy v data vrstvě, ale aplikace nemá žádný search screen.

---

## Otevřené bugy (z testu v0.5.1)

| Bug | Popis | Soubor |
|---|---|---|
| BUG-1 | `release.yml` nepředával API klíče → BuildConfig prázdný | **Opraveno** v0.5.2 (commit f10c657) |
| BUG-2 | TV nemá vlastní Jellyfin setup — jen instrukční text | `TvHomeScreen.kt:46` |
| BUG-3 | Jellyfin library karty neklikatelné — chybí onClick + navigace | `JellyfinBrowserScreen.kt:74` |
| BUG-4 | TV drawer Filmy/Seriály nefiltruji — volají `onNavigateHome` bez filtru | `TvNavDrawer.kt:44,57,70` |
| BUG-5 | Settings jen Trakt sekce, chybí Jellyfin status | Nízká priorita |
