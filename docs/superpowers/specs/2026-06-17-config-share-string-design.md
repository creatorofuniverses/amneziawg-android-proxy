# AmneziaWG config share-string — design

**Date:** 2026-06-17
**Status:** Android app + Python CLI halves **implemented** (codec, CLI, interop vector, refreshed add-sheet, paste screen, deep link) per `docs/superpowers/plans/2026-06-17-config-share-string-import.md`; server-side UI for emitting share-strings remains future work.
**Scope:** cross-repo — Android app (`amneziawg-android`, Kotlin), server UI (`amnezia-wg2-easy`, Node.js + vanilla-JS/Vue frontend), and optional Python tooling.

## Problem

Importing a config by scanning a QR is awkward when the QR arrives **on the same phone** you'd scan it with. We want a single copy-pasteable **text string** that encodes a full AmneziaWG client config, so it can be sent over chat/email and pasted (or tapped, as a deep link) to import — the way proxy apps share `ss://` / `vmess://` links.

## Decisions (settled in brainstorm)

- **Closed loop:** only our server UI encodes and our app decodes. We own and version the scheme; no external format to conform to.
- **Simplicity over shortness:** encode the canonical `.conf` **text**, so there is **no field schema to maintain** across three languages — both ends already produce/parse `.conf` (`WireGuard.js` generates it; `Config.parse`/`toAwgQuickString` on Android).
- There is **no universal WireGuard/AmneziaWG share-link standard** (WG's "share" is a QR of the raw `.conf`; AmneziaVPN's `vpn://` is a Qt `qCompress` container that's awkward to reproduce). We compose the **standard building blocks** instead: `zlib` + `Base64URL`, behind a URI scheme.

## Format

```
awg://v1/<base64url( zlib( utf8(conf_text) ) )>
```

- **`awg://`** — URI scheme. Doubles as an Android deep link (tap → import) and is the prefix the paste-import field recognizes.
- **`v1`** — format version; the decoder dispatches on it so we can change compression/format later (`v2`) without breaking old strings.
- **payload** — the exact client `.conf` text, `zlib`-compressed, then **Base64URL, no padding**.

**Encode:** `conf` → UTF-8 bytes → `zlib`-deflate → base64url(no pad) → prepend `awg://v1/`.
**Decode:** strip prefix → base64url-decode (re-pad first) → `zlib`-inflate → UTF-8 `.conf` → existing `Config.parse`.

### Pinned choices (interop-critical — these are where naïve impls drift)

1. **Compression = zlib, RFC 1950** (2-byte header + adler-32 trailer) — the default output of `Deflater`, Python `zlib`, and JS `CompressionStream('deflate')`/`fflate.zlibSync`. In JS this is **`'deflate'`, NOT `'deflate-raw'`** (raw = RFC 1951, incompatible).
2. **Base64URL = RFC 4648 §5** — alphabet `-_`, **no `=` padding** on encode; decoders **must re-add** padding (`'=' * (-len % 4)`) before decoding.
3. **Android uses `android.util.Base64`** (API 1), **not** `java.util.Base64` (API 26 — project is minSdk 24).
4. **Charset = UTF-8.** Serialize the `.conf` with `\n` line endings; the parser tolerates the rest.
5. Compression **level does not affect decodeability** — encoders may use best-compression (9); any zlib stream decodes.

### Integrity

`zlib`'s adler-32 trailer makes a truncated/garbled string **fail loudly on inflate**; `Config.parse` then validates the result. No separate checksum/MAC is needed for corruption detection. (This is detection, not authentication — the channel is assumed trusted, same as sending a `.conf`.)

### Length expectation

- Plain config (keys + endpoint + allowed IPs + DNS): ≈ **150–250 chars**.
- Full obfuscation (Jc/Jmin/Jmax, S1–S4, H1–H4, I1–I5 special-junk templates, ImitateProtocol): ≈ **350–600 chars**.

The 3 keys (Interface private, Peer public, optional preshared) are ~132 chars of high-entropy base64 that don't compress; the rest (keywords, repeated structure, junk templates) compresses well. Acceptable for copy-paste; shorter configs remain QR-friendly too.

## Reference implementations

These are the whole feature's crypto-free core; keep them tiny and identical in behavior. A shared **test vector** (below) guards interop.

### Kotlin (Android — `android.util.Base64`, `java.util.zip`)
```kotlin
object ConfigShare {
    private const val PREFIX = "awg://v1/"
    private const val B64 = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    fun encode(conf: String): String {
        val raw = conf.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, /* nowrap = */ false) // false => zlib
        deflater.setInput(raw); deflater.finish()
        val out = ByteArrayOutputStream(); val tmp = ByteArray(1024)
        while (!deflater.finished()) out.write(tmp, 0, deflater.deflate(tmp))
        deflater.end()
        return PREFIX + Base64.encodeToString(out.toByteArray(), B64)
    }

    fun decode(s: String): String {
        require(s.startsWith(PREFIX)) { "not an awg://v1 string" }
        val comp = Base64.decode(s.substring(PREFIX.length), B64)
        val inflater = Inflater(/* nowrap = */ false); inflater.setInput(comp)
        val out = ByteArrayOutputStream(); val tmp = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(tmp)
            if (n == 0 && inflater.needsInput()) break // truncated input
            out.write(tmp, 0, n)
        }
        inflater.end()
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
```

### JavaScript (server UI — browser via vendored `fflate`; Node via `zlib`)
```js
// Browser (vendor fflate, ~8KB — same pattern as the already-vendored vue/sha256):
const PREFIX = 'awg://v1/';
function encodeConf(conf) {
  const comp = fflate.zlibSync(fflate.strToU8(conf), { level: 9 }); // zlib RFC1950
  return PREFIX + b64urlEncode(comp);
}
function decodeConf(s) {
  if (!s.startsWith(PREFIX)) throw new Error('not an awg://v1 string');
  return fflate.strFromU8(fflate.unzlibSync(b64urlDecode(s.slice(PREFIX.length))));
}
function b64urlEncode(bytes) {
  let bin = ''; for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64urlDecode(str) {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';
  const bin = atob(str), out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
// Node backend alternative: zlib.deflateSync(buf) + Buffer.from(comp).toString('base64url')
```
(`CompressionStream('deflate')` is an async, zero-dependency alternative once Safari ≥16.4 is acceptable; `fflate` is the safe sync choice today.)

### Python (tooling — stdlib only)
```python
import base64, zlib
PREFIX = "awg://v1/"

def encode(conf: str) -> str:
    comp = zlib.compress(conf.encode("utf-8"), 9)            # zlib RFC1950
    return PREFIX + base64.urlsafe_b64encode(comp).rstrip(b"=").decode("ascii")

def decode(s: str) -> str:
    assert s.startswith(PREFIX), "not an awg://v1 string"
    b = s[len(PREFIX):].encode("ascii")
    b += b"=" * (-len(b) % 4)                                 # re-pad
    return zlib.decompress(base64.urlsafe_b64decode(b)).decode("utf-8")
```

### Interop test vector (define before coding all three)
Pick one canonical `.conf` (fixed key bytes, full obfuscation) and record its expected `awg://v1/...` string. Because zlib output is deterministic for a fixed (input, level, library), the three implementations must reproduce it — but note **different zlib builds can emit different bytes at the same level**, so the contract is: *each impl's `encode` round-trips through every impl's `decode`*, and `decode(fixture_string) == fixture_conf` exactly. Encode-byte-equality across languages is a nice-to-have, not the contract.

## Integration points (high level — for the future plan, not built now)

- **Server UI (`amnezia-wg2-easy`):** generate the string next to the existing per-client QR (`src/lib/WireGuard.js` backend or `src/www/js/app.js` frontend), with a copy button. Optionally render a QR **of the same string** so one artifact serves both scan and paste.
- **Android app:** decode in the import path (`AddTunnelsSheet`) for (a) pasted strings, (b) a QR whose payload is the string, and (c) an `awg://` `intent-filter` for tap-to-import. After decode → `Config.parse` → existing add-tunnel flow (naming dialog, etc.).
- **Python:** a small `awgshare.py` (encode/decode + CLI) for scripting/CI.

## Security

The string **is the full credential** (contains the Interface private key) — identical sensitivity to the `.conf`/QR. As a deep-link URI it can leak into system logs / launcher history, so: do not log it, exclude it from analytics, and clear the paste buffer/import field after use. This is detection-only integrity, not authentication — trust of the delivery channel is the user's, exactly as today with a `.conf` file or QR.

## Rejected alternatives

- **Compact binary / MessagePack / CBOR** (raw 32-byte keys, varint-packed params): ~40–50% shorter, but a field schema to define, keep in sync, and version across Kotlin/JS/Python — rejected for maintenance cost (priority was simplicity).
- **Match AmneziaVPN `vpn://`:** would let the official app import too, but reproducing its Qt `qCompress` container exactly in JS/Kotlin/Python is finicky and binds us to their schema — rejected (closed loop chosen).

## Out of scope (this design)

- Any actual implementation (separate plan, when prioritized).
- A signed/authenticated variant (channel is trusted, as today).
- Multi-config bundles in one string (could be a `v2` if ever needed).
