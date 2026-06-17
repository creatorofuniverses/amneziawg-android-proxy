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
