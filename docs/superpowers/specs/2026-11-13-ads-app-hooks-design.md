# Ads: two app hooks (hide a placement, end ad-free time)

2026-11-13 · foundation · Android and iOS (shared Kotlin)

## Why

An app adopting the ads module (the first is a standalone app with one Home banner) needs two
things the module can't do today:

1. **Its own reason to hide a banner.** The shell draws a placement's banner under its tab, and
   `AdsController` alone decides when. An app can't add a rule like "no banner while this screen is
   in its empty state", although the screen's state is the app's to know.
2. **An ad-free window that can end early.** `AdFree.grantUntil` never shortens a window, and
   `extend` only lengthens it. An app whose own store is the source of its ad-free time (bought with
   points, say) needs to end the foundation's window when its own ends early: a debug "end ad-free"
   tool, a refund, a reset.

Both are generic. Neither knows any app.

## Goals, non-goals, anti-goals

- Goal: an app hides or shows a declared placement at runtime, on both platforms, from shared code.
- Goal: an app ends the ad-free window now.
- Non-goal: new ad formats, new placements in the shell, or changing the house rules' meaning.
- Anti-goal: an app hook that **shows** a banner the house rules hide. The hook can only add a
  reason to hide. Consent, first launch, ad-free time and ads-off still win.
- Anti-goal: a hidden placement still loading an ad or triggering the tracking prompt.

## Approaches

| | Approach | Trade-off |
|---|---|---|
| **A (picked)** | `AdsController.setHidden(placement, hidden)` plus `AdFree.clear()` | Two small methods on existing types. The hide flag joins the inputs `AdsController` already combines, so the banner reacts the same way it does to consent or ad-free time |
| B | A `visibleWhen: Flow<Boolean>` argument on `placement(...)` in the DSL | The condition is wired at definition time, but the screen's state lives in a ViewModel created later, so apps would need a global flow anyway. Also needs a Swift mirror on `AdPlacement` |
| C | Apps skip the shell placement and draw `AdBanner` themselves | Breaks the shell's ownership of where banners go (one per tab, never under Settings or sign-in) |

## Design

**Hide a placement.**
- `HideReason` gains `APP_HIDDEN` ("the app asked").
- `AdsController` gains `fun setHidden(placement: String, hidden: Boolean)`. It keeps a
  `MutableStateFlow<Set<String>>` of hidden placements and adds it to the inputs `createBanner`
  combines.
- `adGate` gains a `hiddenByApp: Boolean` parameter, checked **after** `UNDECLARED` and before
  consent. A hidden placement reads `APP_HIDDEN`, and `awaitTracking` is never reached, so no ad
  loads and no prompt shows.
- It's idempotent. An undeclared placement or ads-off is a no-op (no error), so an app can call it
  unconditionally.
- It lasts for the process. It isn't stored: the app sets it from live state, which it re-derives
  after a restart.

**End ad-free time.**
- `AdFree` gains `suspend fun clear()`. It removes the stored end (`untilEpochMillis = null`),
  publishes `Inactive`, and marks the window loaded. Banners come back as with a window that ran out.
- From Swift: through `SharedGraph.shared.adFree()`, like `extend` and `grantUntil`.

**Docs.** `docs/ads.md` gets an "App hooks" section with both, and an example per platform. Shared
code gets both through Koin: `get<AdsController>()` and `get<AdFree>()`.

## Tests (unit, shared)

- `adGate`: `hiddenByApp` hides with `APP_HIDDEN`. Ads-off and undeclared still take precedence.
- `AdsController`: hiding a showing placement turns it `Hidden(APP_HIDDEN)` and un-hiding brings it
  back. A hidden placement never calls `TrackingPort`. `setHidden` on an undeclared placement changes
  nothing.
- `AdFree.clear()`: an active window turns `Inactive`, the store is cleared, and a later `extend`
  starts from now.

## Left to the builder

- Where the hidden set lives inside `AdsController`, and the KDoc.
- Showcase needs no change. A short line in Showcase's docs is optional.
