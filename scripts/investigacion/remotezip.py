"""Lista el contenido de un .zip remoto sin descargarlo: lee solo el directorio
central mediante peticiones HTTP Range. Uso: python3 -I remotezip.py <url>"""
import struct
import sys
import urllib.request

url = sys.argv[1]


def fetch(start, end):
    req = urllib.request.Request(url, headers={"Range": f"bytes={start}-{end}"})
    with urllib.request.urlopen(req) as resp:
        return resp.read(), resp.headers.get("Content-Range")


_, content_range = fetch(0, 0)
size = int(content_range.split("/")[1])
tail, _ = fetch(max(0, size - 65536), size - 1)
eocd = tail.rfind(b"PK\x05\x06")
cd_size, cd_offset = struct.unpack("<II", tail[eocd + 12:eocd + 20])
cd, _ = fetch(cd_offset, cd_offset + cd_size - 1)

pos = 0
while pos < len(cd):
    _, usize, name_len, extra_len, comment_len = struct.unpack("<IIHHH", cd[pos + 20:pos + 34])
    name = cd[pos + 46:pos + 46 + name_len].decode(errors="replace")
    print(f"{usize // 1048576:>5} MB  {name}")
    pos += 46 + name_len + extra_len + comment_len
