# Gulab Astro — Complete Vedic Astrology Android Project

This project combines the requested Gulab Astro modules in one Android app foundation.

## Included modules
- Kundli / Birth Chart
- Rashi & Nakshatra + Pada
- Navgraha
- Daily Horoscope
- Love / Marriage
- Career & Finance
- Vastu / Remedies
- Vimshottari Dasha / Mahadasha
- Navtara Chakra
- KP Astrology (Star Lord / Sub Lord presentation)
- Planet-to-Planet hits and Planet-to-House mapping
- Transit vs natal hits
- Chandrama Astam status + notification worker scaffold
- Multi-page Kundli PDF + Android share/save flow
- Hindi / English switch

## Calculation engine
The included offline engine uses deterministic astronomical formulas and Lahiri-style sidereal conversion. It is not dummy/random data. For production-grade ephemeris accuracy, replace the `AstroEngine` planetary adapter with a licensed Swiss Ephemeris build.

Swiss Ephemeris is dual-licensed (AGPL or Professional). A closed-source/commercial APK should not ship Swiss Ephemeris until the appropriate license is obtained. See the official documentation at https://www.astro.com/swisseph/.

## Important accuracy note
KP cusp calculations, exact house cusps, and high-precision planetary positions should be validated against the exact Swiss Ephemeris release/data set selected for production. The UI and domain models are already separated so that a Swiss Ephemeris adapter can replace the current astronomical provider without rewriting the screens.

## Build
Open in Android Studio and let Gradle sync. Or use the included GitHub Actions workflow under `.github/workflows/android.yml` to build a debug APK in GitHub Actions.

## Package
`com.gulabastro.app`
