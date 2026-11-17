# Third-party notices

Source code copied into this repository from other projects, with its licence. Apps built on the
foundation ship this code too: list it in the app's open-source notices (licences screen or
acknowledgements) along with its other libraries.

## Google Mobile Ads examples (Apache License 2.0)

- **File:** `core/ads/google/src/main/kotlin/com/whitelabel/core/ads/google/compose/NativeAdViews.kt`
- **Upstream:** [googleads/googleads-mobile-android-examples](https://github.com/googleads/googleads-mobile-android-examples),
  `kotlin/advanced/JetpackComposeDemo/app/src/main/java/com/google/android/gms/example/jetpackcomposedemo/formats/compose_utils/NativeAdView.kt`
  at commit `eac5db1a64ff2858676c98889c275cef926e3ab6`.
- **Copyright:** 2024 Google LLC.
- **Changes:** package, `internal` visibility, and the unused wrappers (AdChoices, price, star rating,
  store, attribution, button) removed. The file's header keeps Google's notice and says what changed.
- **Licence:** Apache License 2.0, full text in
  [`third_party/googleads-mobile-android-examples/LICENSE`](third_party/googleads-mobile-android-examples/LICENSE)
  (copied from the upstream repository at the same commit). The upstream repository has no `NOTICE` file.
