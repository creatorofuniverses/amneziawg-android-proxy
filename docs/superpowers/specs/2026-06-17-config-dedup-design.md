# Name-independent duplicate-tunnel detection — Design

**Status:** Implemented 2026-06-17 on branch `refresh-paste-import` (plan: `docs/superpowers/plans/2026-06-17-config-dedup.md`). Follow-on to the `awg://v1` import feature (`2026-06-17-config-share-string-design.md` / `…-import.md`); ships on the same branch before its squash into `refreshed_ui`.

## Problem

Duplicate detection today is **name-based only**: `TunnelManager.create(name, config)` rejects a colliding *name* (`tunnel_error_already_exists`) but nothing else. So the same credentials imported under a different name silently create a second tunnel. On-device testing of the paste flow surfaced this: pasting one config twice with two names yields two tunnels for one client.

## Goal

Detect a duplicate by **config identity, independent of the tunnel name**, and block it — across every import path.

## Matching rule (decided)

Two configs are the **same tunnel** iff **both** hold:
- their `[Interface]` **public keys** are equal, **and**
- their **first peer's endpoint** is equal (compared as the endpoint's string form; two configs that both lack an endpoint count as equal on this dimension).

Rationale for the public key: it is derived deterministically from the private key, so equal-private ⟺ equal-public — an identical identity test that avoids handling secret-key bytes at call sites. Consequence of including the endpoint: the **same key pointed at a different server endpoint is allowed** as a separate tunnel; same key **and** endpoint is a duplicate.

## Components

### 1. `sameTunnelIdentity(a: Config, b: Config): Boolean` — pure helper (new, unit-tested)
The single source of truth for "same tunnel," so the rule cannot drift between call sites.
- Returns `true` iff `a.interface.keyPair.publicKey == b.interface.keyPair.publicKey` **and** the first-peer endpoint string of `a` equals that of `b` (both-absent ⇒ equal on that dimension).
- Lives in the `ui` module (e.g. `ui/src/main/java/org/amnezia/awg/util/`), beside `ShareStringDecoder`. Pure JVM (operates on already-parsed `Config`), so it is unit-testable without Robolectric, like `ShareStringDecoderTest`.

### 2. `TunnelManager.findMatchingTunnel(config: Config): ObservableTunnel?` (new)
- Iterates existing tunnels (`getTunnels()` / `tunnelMap`), obtaining each tunnel's `Config` via the cached `config` field or `getConfigAsync()` when null, and returns the first one for which `sameTunnelIdentity(existing, config)` is true.
- **Defensive:** a tunnel whose config fails to load is skipped (treated as non-matching), never throwing out of the scan.
- `suspend`, consistent with the surrounding `TunnelManager` API.

### 3. `TunnelManager.create(...)` — the enforcement guarantee
Immediately after the existing name checks, before `addToList`:
```
findMatchingTunnel(config!!)?.let {
    throw IllegalArgumentException(context.getString(R.string.tunnel_error_duplicate_config, it.name))
}
```
Because **all** import flows (file import, QR scan, paste, `awg://` deep link) funnel through `create`, this one guard covers them all. The thrown `IllegalArgumentException` surfaces through the existing `ErrorMessages` → `Snackbar` path those callers already use (and the path `PasteConfigActivity.commit()` was just wired to).

### 4. `PasteConfigActivity.validate()` — live UX layer
After a successful decode (the existing valid state), launch a coroutine to call `findMatchingTunnel(d.config)`:
- On a match: show `⚠ Already added as "<existing name>"` (string `paste_duplicate`) in the validation line and **disable Add**; the decoded preview card stays visible.
- **Stale-guard:** the async result is applied only if the input field still holds the same decoded value (a later keystroke must win), so a slow check from a prior keystroke cannot clobber newer input.
- This is a best-effort convenience; `create()` (Component 3) remains the backstop if the check is mid-flight when Add is tapped.

### 5. New string resources
Added to `ui/src/main/res/values/` (alongside the feature's existing strings):
- `tunnel_error_duplicate_config` = `This config is already added as “%1$s”.` — thrown by `create()`, shown via Snackbar on file/QR/paste failure.
- `paste_duplicate` = `Already added as “%1$s”` — the live paste-screen validation label.

## Error handling

- `findMatchingTunnel` never propagates a config-load failure; an unreadable existing tunnel is simply not a match.
- `create()` throws a plain `IllegalArgumentException` carrying the user-facing message, identical in shape to the existing name-collision throw — no new exception type, no new `ErrorMessages` branch needed.
- The live check failing (e.g. tunnels not yet loaded) leaves the normal valid state intact; the user can still tap Add and `create()` enforces correctness.

## Testing

- **Unit tests for `sameTunnelIdentity`** (in `ui` unit tests, configs built via `Config.parse` as in `ShareStringDecoderTest`):
  - same key + same endpoint ⇒ `true`
  - same key + different endpoint ⇒ `false`
  - different key + same endpoint ⇒ `false`
  - byte-identical configs ⇒ `true`
  - both configs lacking an endpoint, same key ⇒ `true`
- **`create()` guard and the live label** are integration surfaces (require the running app / `TunnelManager` + `configStore`); verified in the on-device pass: import a config twice under different names ⇒ second blocked with the duplicate message; same key re-pointed at a different endpoint ⇒ allowed.

## Scope / non-goals

- The only change to **existing** behavior is the new `create()` check; file import and QR scan now also reject content-duplicates (the intended effect of global enforcement). No other refactoring.
- Not in scope: deduping across endpoint hostname-vs-resolved-IP differences (string comparison is sufficient for the share/import use case), merging or updating an existing tunnel from a re-imported config, or any change to the name-collision behavior.
