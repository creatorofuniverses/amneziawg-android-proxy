# Android GUI for [AmneziaWG](https://amnezia.org/learn-more/31_amneziawg)

**[Download from the Play Store](https://play.google.com/store/apps/details?id=org.amnezia.awg)**

This is an Android GUI for [AmneziaWG](https://amnezia.org/learn-more/31_amneziawg).

## Fork notice

This is an independent fork, distributed as **AWG Proxy** under the application ID
`org.amnezia.awg.proxy`, and is **not affiliated with or endorsed by** the original
AmneziaWG / Amnezia VPN developers or WireGuard LLC. It installs alongside the
official app rather than replacing it. All upstream code remains under its original
Apache-2.0 license and copyright (© WireGuard LLC); see `COPYING`.

## Building

```
$ git clone --recurse-submodules https://github.com/creatorofuniverses/amneziawg-android-proxy
$ cd amneziawg-android-proxy
$ ./gradlew assembleRelease
```

The `--recurse-submodules` flag is required: the native core is built from the
`tunnel/tools/amneziawg-go-proxy` submodule (the amneziawg-go fork with client-side traffic
imitation), alongside `amneziawg-tools` and `elf-cleaner`. If you already cloned without it:

```
$ git submodule update --init --recursive
```

macOS users may need [flock(1)](https://github.com/discoteq/flock).

## Importing tunnels

The app can import a tunnel by **scanning a QR code**, **picking a QR image from
the gallery**, importing a `.conf` file, or pasting an `awg://v1` share-string.

### Generate a QR code from a config

Using [`qrencode`](https://fukuchi.org/works/qrencode/):

```
# Print to the terminal (scan it with the in-app camera scanner):
$ qrencode -t ansiutf8 < tunnel.conf

# Or write a PNG to import via "Import from gallery":
$ qrencode -o tunnel.png < tunnel.conf
```

### Share-string (`awg://v1`)

`tools/awgshare/awgshare.py` is the reference encoder/decoder for the compact
`awg://v1` share-string the app pastes in (zlib-compressed, base64url-encoded
config). It has no third-party dependencies:

```
# .conf -> awg://v1/...  (paste the output into the app's "Paste" import)
$ python3 tools/awgshare/awgshare.py encode tunnel.conf

# awg://v1/... -> .conf
$ python3 tools/awgshare/awgshare.py decode "awg://v1/..."
```

The script also carries [PEP 723](https://peps.python.org/pep-0723/) inline
metadata, so it can be run with [`uv`](https://docs.astral.sh/uv/) directly:
`uv run tools/awgshare/awgshare.py encode tunnel.conf`.

You can chain both — turn a config straight into a scannable share-string QR:

```
$ python3 tools/awgshare/awgshare.py encode tunnel.conf | qrencode -t ansiutf8
```
