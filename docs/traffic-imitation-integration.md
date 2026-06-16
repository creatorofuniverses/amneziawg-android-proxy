# Client-side traffic imitation — Android integration

Integrates the `amneziawg-go-proxy` fork's **client-side traffic imitation** into the
Android app. Design rationale: `Client-side traffic imitation for AmneziaWG` design sketch.
Feature lives in the Go core; the Android app only wires the fork in and threads one new
config key through to the UAPI string.

## What was changed

### 1. Build wiring (required for all tiers)
`tunnel/tools/libwg-go/go.mod` — point the embedded core at the local fork (same module
path, so a `replace` is enough):

```
replace github.com/amnezia-vpn/amneziawg-go => ../../../../amneziawg-go-proxy
```

`go mod tidy` adds **no** new dependencies — the imitation code uses Go std crypto only.

> **Before a release APK:** push `amneziawg-go-proxy` and replace the local-path `replace`
> with a pinned tag (`require ...@<tag>`), so CI builds don't depend on a local checkout.

### 2. Config key `imitate_protocol` (Tiers 1 + 2)
`tunnel/.../config/Interface.java` + `BadConfigException.java` — a new optional interface
field, parsed/serialized exactly like the other AWG fields:

- INI (`[Interface]`): `ImitateProtocol = quic`
- UAPI (to the core): `imitate_protocol=quic`
- Accepted values (validated, mirrors the Go core): `none | quic | dns | stun | sip`.
  Anything else throws `BadConfigException(INVALID_VALUE)`.

## Tier → config mapping

| Tier | What it shapes | How to enable | Android code needed |
|---|---|---|---|
| 1 | S-padding on real packets | `ImitateProtocol = quic` | the new key (done) |
| 2 | `Jc` junk packets | `ImitateProtocol = quic` (same key) | the new key (done) |
| 3 | I-packet decoy datagram | `I1 = <q 600>` (or `<dns/stun/sip LEN>`) | none — `I1`–`I5` already pass through verbatim |
| 4 | fake QUIC Initial + SNI | `I1 = <qinit example.com>` | none — same path |

## Example `[Interface]`

```ini
[Interface]
PrivateKey = ...
Address = 10.8.0.2/32
Jc = 4
Jmin = 40
Jmax = 70
S1 = 100
S2 = 100
ImitateProtocol = quic
I1 = <qinit www.google.com>
```

## Not done (out of current scope)

- **No UI** for `ImitateProtocol` — config-file driven only. The editor doesn't render a
  selector yet; an imported config round-trips correctly. A dropdown is the follow-up.

## Verification performed

- `go mod tidy` clean against the fork; fork `device`/`conn`/`tun`/`ipc` compile (host).
- `:tunnel:compileReleaseJavaWithJavac` — BUILD SUCCESSFUL (config plumbing).
- `:tunnel:externalNativeBuildRelease` (arm64) — BUILD SUCCESSFUL; the resulting
  `libwg-go.so` contains the fork's imitate symbols (`imitate_protocol`, `imitateFill`,
  `qinitObf`, `obf_imitate*.go`), confirming the replace is in effect.
- **Not** runtime-tested against a live server (needs a device + patched/vanilla peer).
