# Client-side traffic imitation — Android integration

Integrates the `amneziawg-go-proxy` fork's **client-side traffic imitation** into the
Android app. Design rationale: `Client-side traffic imitation for AmneziaWG` design sketch.
Feature lives in the Go core; the Android app only wires the fork in and threads one new
config key through to the UAPI string.

## What was changed

### 1. Build wiring (required for all tiers)
The fork is vendored as a **git submodule** at `tunnel/tools/amneziawg-go-proxy` (pinned to a
commit), and `tunnel/tools/libwg-go/go.mod` points the embedded core at it. The fork keeps the
upstream module path (`module github.com/amnezia-vpn/amneziawg-go`), so a `replace` is enough:

```
replace github.com/amnezia-vpn/amneziawg-go => ../amneziawg-go-proxy
```

`go mod tidy` adds **no** new dependencies — the imitation code uses Go std crypto only.

> A *remote* `replace => github.com/.../amneziawg-go-proxy v…` does **not** work: Go rejects
> the module-path mismatch (the fork's go.mod still declares the upstream path). The submodule
> keeps the path intact (clean upstream rebases) while pinning an exact commit.

**Clone / CI:** `git submodule update --init --recursive` before building (same as the existing
`amneziawg-tools` / `elf-cleaner` submodules). **Updating the fork:** push to the fork, then
`cd tunnel/tools/amneziawg-go-proxy && git pull`, and commit the bumped submodule pointer.

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
