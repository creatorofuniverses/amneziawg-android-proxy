# Android GUI for [AmneziaWG](https://amnezia.org/learn-more/31_amneziawg)

**[Download from the Play Store](https://play.google.com/store/apps/details?id=org.amnezia.awg)**

This is an Android GUI for [AmneziaWG](https://amnezia.org/learn-more/31_amneziawg).

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
