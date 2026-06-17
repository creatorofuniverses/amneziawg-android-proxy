# /// script
# requires-python = ">=3.9"
# dependencies = []
# ///
"""Reference encoder/decoder for the AmneziaWG awg://v1 config share-string.

  awg://v1/<base64url( zlib( utf8(conf) ) )>   (zlib RFC1950, base64url RFC4648 §5 no padding)

Usage:
  uv run awgshare.py encode <client.conf>     # .conf file -> awg://v1/... (prints to stdout)
  uv run awgshare.py decode <"awg://v1/...">   # share-string -> .conf  (arg or - for stdin)
"""
import argparse
import base64
import sys
import zlib

PREFIX = "awg://v1/"


def encode(conf: str) -> str:
    comp = zlib.compress(conf.encode("utf-8"), 9)  # zlib RFC1950
    return PREFIX + base64.urlsafe_b64encode(comp).rstrip(b"=").decode("ascii")


def decode(s: str) -> str:
    s = s.strip()
    if not s.startswith(PREFIX):
        raise ValueError("not an awg://v1 string")
    b = s[len(PREFIX):].encode("ascii")
    b += b"=" * (-len(b) % 4)  # re-pad
    return zlib.decompress(base64.urlsafe_b64decode(b)).decode("utf-8")


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="AmneziaWG awg://v1 share-string codec")
    sub = p.add_subparsers(dest="cmd", required=True)
    e = sub.add_parser("encode", help=".conf file -> awg://v1 string")
    e.add_argument("conf", help="path to a .conf file (or - for stdin)")
    d = sub.add_parser("decode", help="awg://v1 string -> .conf")
    d.add_argument("string", help="the awg://v1 string (or - for stdin)")
    args = p.parse_args(argv)

    if args.cmd == "encode":
        text = sys.stdin.read() if args.conf == "-" else open(args.conf, encoding="utf-8").read()
        sys.stdout.write(encode(text) + "\n")
    elif args.cmd == "decode":
        text = sys.stdin.read() if args.string == "-" else args.string
        sys.stdout.write(decode(text))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
