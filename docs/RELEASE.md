# MaizoSNES v1 signed release

A stable release requires passing Android CI, both ARM native ABIs, network
unit tests, APK signature verification, SHA-256, licenses and hardware tests.
Never call a CI debug-signed preview a production-signed release.

Prepare an offline, backed-up STABLE Android signing keystore and configure
GitHub Secrets: KEYSTORE_BASE64 (base64 keystore), KEYSTORE_PASSWORD,
KEY_ALIAS, KEY_PASSWORD. Legacy secrets MAIZO_KEYSTORE_B64,
MAIZO_STORE_PASSWORD, MAIZO_KEY_ALIAS, MAIZO_KEY_PASSWORD also work.
Never commit keystore, passwords or key.properties. Keep the same key for
all future updates or Android will reject app upgrades.

1. Review LICENSE_DECISION.md and noncommercial Snes9x obligations.
2. Confirm Android CI, and DEVICE_SMOKE_TEST.md on armv7 and arm64.
3. With signing secrets present, trigger Publish MaizoSNES 1.0 (signed)
   under GitHub Actions manually on validated branch work/android-foundation.
4. The workflow tests, builds assembleRelease, verifies both ABIs and signed
   certificate, writes SHA-256 and publishes v1.0.0 as latest only on success.
5. Preserve signing keystore securely for future releases.

Current shared Gemini icon URL is not a directly accessible image, so do
not replace existing graphics with invented art or claim it was applied.
