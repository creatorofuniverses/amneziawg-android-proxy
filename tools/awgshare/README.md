# awgshare — AmneziaWG awg://v1 share-string tool

Reference encoder/decoder for the config share-string the Android app imports.

    awg://v1/<base64url( zlib( utf8(conf) ) )>

## Use

    uv run awgshare.py encode client.conf      # -> awg://v1/... (paste into the app)
    uv run awgshare.py decode "awg://v1/..."    # -> the original .conf
    cat client.conf | uv run awgshare.py encode -

Stdlib only (`zlib`, `base64`); `uv` just provides the runner. Plain `python3 awgshare.py ...` also works.

## Optional tunnel name

Prefix the .conf with a comment to carry a display name into the app's preview:

    # Name = awg-fi-01
    [Interface]
    ...

## Test

    uv run --with pytest pytest -q
