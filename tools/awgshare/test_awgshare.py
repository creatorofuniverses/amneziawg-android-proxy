# tools/awgshare/test_awgshare.py
import os

import awgshare

CONF = (
    "[Interface]\n"
    "PrivateKey = aP8A1234567890abcdefghijklmnopqrstuvwxyzABC=\n"
    "Address = 10.8.0.2/32\n"
    "[Peer]\n"
    "PublicKey = bQ9B1234567890abcdefghijklmnopqrstuvwxyzABC=\n"
    "Endpoint = 192.0.2.1:51820\n"
    "AllowedIPs = 0.0.0.0/0\n"
)

def test_round_trip():
    assert awgshare.decode(awgshare.encode(CONF)) == CONF

def test_prefix_and_no_padding():
    s = awgshare.encode(CONF)
    assert s.startswith("awg://v1/")
    assert "=" not in s[len("awg://v1/"):]

def test_decode_rejects_wrong_prefix():
    try:
        awgshare.decode("https://example.com")
        assert False, "expected ValueError"
    except ValueError:
        pass

def test_decodes_committed_vector():
    here = os.path.dirname(__file__)
    root = os.path.abspath(os.path.join(here, "..", ".."))
    conf = open(os.path.join(root, "tunnel/src/test/resources/share-vector.conf"), encoding="utf-8").read()
    s = open(os.path.join(root, "tunnel/src/test/resources/share-vector.awg"), encoding="utf-8").read().strip()
    assert awgshare.decode(s) == conf
