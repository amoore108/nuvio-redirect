# Nuvio Redirect

Nuvio Redirect is a small, sideload-only Android TV utility. While the Google TV HOME launcher is active, it reads the focused recommendation card when you press OK, resolves the visible title through TMDB, and opens the matching title in Nuvio.

It does not modify Google TV, Netflix, Prime Video, or Nuvio. It uses an accessibility service because Google TV normally sends recommendation selections directly to the original provider.

## Install

Download the current APK from the [latest GitHub release](https://github.com/amoore108/nuvio-redirect/releases/latest/download/nuvio-redirect.apk).

1. Install the APK:

   ```bash
   adb install -r nuvio-redirect-0.1.0.apk
   ```

2. Open **Nuvio Redirect** from the TV Apps row.
3. Add either a TMDB v3 API key or a TMDB v4 Read Access Token.
4. Choose the installed Nuvio variant, or leave **Auto-detect** selected.
5. Use **Test Nuvio deep link** to confirm that Nuvio opens a detail page.
6. Select **Open accessibility settings**, find **Nuvio recommendation redirect**, and enable it.
7. Return to the Google TV home screen, focus a movie or series recommendation, and press OK.

The first lookup can take a moment. Exact title/year matches open automatically. Ambiguous matches display a picker.

## Diagnostics

The setup screen shows the last launcher card seen by the service, including:

- extracted title;
- launcher package;
- exposed Android view IDs;
- raw accessibility text;
- whether the card fingerprint is safe enough to intercept.

If a recommendation is captured but says **Will intercept: no**, save the diagnostics. Google TV launcher layouts differ between devices, and those details are what is needed to add a safe device-specific fingerprint.

## Safety behavior

- Only the current HOME launcher is eligible for interception.
- Navigation, app, settings, profile, search, and input-like view IDs are rejected.
- A configured TMDB credential and an installed Nuvio package are required before OK is consumed.
- A fuzzy or ambiguous title is never silently auto-opened.
- Disable **Redirect recommendation-card selections** for an immediate bypass.
- Pressing **Back to Google TV** from the resolver returns to the launcher; press the original tile again after disabling redirection if you want its provider action.

Android cannot safely replay a physical remote key after the utility has consumed it. This is why the service is conservative about deciding which cards to intercept.

## Privacy

The accessibility service can read text visible in the active launcher window. The app filters processing to the resolved HOME package and stores only the most recent card diagnostics. The TMDB credential is stored in this app's private preferences, excluded from cloud backup and device transfer. The only network request made by the utility is the title search sent to `api.themoviedb.org` after a card is selected.

## Nuvio compatibility

Supported package IDs:

- `com.nuvio.tv` — full/GitHub build;
- `com.nuvio.app` — Google Play build.

Generated deep links use these forms:

```text
nuvio://tmdb/movie/550
nuvio://tmdb/series/1399
```

## Build from source

Requirements: Android SDK 37, JDK 17 or newer, and network access for the first Gradle dependency resolution.

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

The release APK is written under `app/build/outputs/apk/release/` and is signed with the local Android debug key for straightforward sideloading.

## Known device-dependent behavior

Google TV launchers are updated independently of the operating system and OEMs can expose different accessibility trees. The utility starts with conservative generic fingerprints. A real-device diagnostics capture may be needed before interception works on a specific launcher version. Other accessibility services that filter remote keys, such as some key mappers or screen readers, can prevent this service from receiving key events.
