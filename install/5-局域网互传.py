#!/usr/bin/env python3
# 局域网互传: 手机 <-> 盒子 (贴文字 / 上传下载文件). 纯表单 + 自动刷新(AJAX) + 图片/文字内联预览.
# 跑在 chroot 内: busybox chroot /data/linux /usr/bin/nohup /usr/bin/python3 /root/transfer.py &
# 目录 /sdcard/transfer (=> 盒子 /storage/emulated/0/transfer)
import os, cgi, html, time, socket, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from qrcodegen import QrCode

DIR = os.environ.get("TDIR", "/mnt/sdcard/transfer")
PORT = int(os.environ.get('TPORT', '8890'))
IMG = ('png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp')
TXT = ('txt', 'log', 'md', 'json', 'csv', 'xml')
os.makedirs(DIR, exist_ok=True)


def lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return '127.0.0.1'


def qr_svg(text, scale=8, border=4):
    qr = QrCode.encode_text(text, QrCode.Ecc.MEDIUM)
    n = qr.get_size()
    dim = (n + border * 2) * scale
    d = []
    for y in range(n):
        for x in range(n):
            if qr.get_module(x, y):
                d.append('M%d %dh%dv%dh-%dz' % ((x + border) * scale, (y + border) * scale,
                                                scale, scale, scale))
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
            'viewBox="0 0 %d %d" shape-rendering="crispEdges">'
            '<rect width="%d" height="%d" fill="#ffffff"/>'
            '<path d="%s" fill="#000000"/></svg>') % (dim, dim, dim, dim, dim, dim, ''.join(d))


PAGE = '''<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>传文件 · 盒子</title>
<style>body{font-family:sans-serif;margin:16px;background:#0f1420;color:#dce6f5}
a{color:#7fc4ff}h1{font-size:18px}textarea{width:100%;height:80px;box-sizing:border-box}
.box{background:#1a2232;border:1px solid #2a3550;border-radius:8px;padding:12px;margin:12px 0}
button{padding:9px 16px;border:0;border-radius:6px;background:#2f6fd0;color:#fff;font-size:15px}
input[type=file]{color:#dce6f5}small{color:#8aa}
.item{margin:10px 0;padding:9px;background:#141c2b;border:1px solid #24304a;border-radius:6px}
.thumb{max-height:220px;max-width:100%;border-radius:4px;display:block;margin-top:8px}
pre.txt{background:#0b1018;padding:8px;border-radius:4px;white-space:pre-wrap;word-break:break-word;
max-height:240px;overflow:auto;font-size:13px;margin:8px 0 0}
.qrbox{flex:0 0 300px;text-align:center;background:#1a2232;border:1px solid #2a3550;border-radius:8px;padding:14px}
.qr{background:#fff;display:inline-block;padding:8px;border-radius:8px;line-height:0;max-width:100%}
.qr svg{max-width:100%;height:auto}#upd{color:#8aa;font-size:12px}
</style></head><body>
<h1>&#128229; 传文件 / 文字 &middot; 盒子</h1>
<div style="display:flex;gap:18px;align-items:flex-start;flex-wrap:wrap">
<div style="flex:1 1 430px;min-width:300px">
<div class="box"><form method="post" enctype="multipart/form-data" action="/upload">
<b>上传文件(手机 &rarr; 盒子)</b><br><br>
<input type="file" name="f" multiple> <button>上传</button></form></div>
<div class="box"><form method="post" action="/text">
<b>发文字(手机 &rarr; 盒子)</b><br>
<textarea name="text" placeholder="输入文字..."></textarea>
<button>发送</button></form></div>
<div class="box"><b>盒子上的内容(自动刷新; 图片/文字直接预览)</b> <small id="upd"></small>
<div id="lst">__LIST__</div></div>
<p><small>目录 /storage/emulated/0/transfer</small></p>
</div>
<div class="qrbox">
<div class="qr">__QR__</div>
<p style="margin:10px 0 0;font-size:15px"><b>手机扫一扫</b><br>用<b>微信</b>或相机扫描<br>即可进入传输界面</p>
<p style="color:#ffd479;margin:10px 0 0;font-size:13px">&#9888; 手机需连接<b>同一个网络(WiFi)</b></p>
<p style="margin:8px 0 0"><small>__URL__</small></p>
</div>
</div>
<script>
function refreshList(){
  var x=new XMLHttpRequest();
  x.onreadystatechange=function(){
    if(x.readyState===4 && x.status===200){
      document.getElementById('lst').innerHTML=x.responseText;
      var u=document.getElementById('upd'); if(u) u.textContent='· '+new Date().toLocaleTimeString();
    }
  };
  try{ x.open('GET','/list?t='+new Date().getTime(),true); x.send(); }catch(e){}
}
setInterval(refreshList, 3000);
</script>
</body></html>'''


def safe(n):
    n = os.path.basename(n)
    keep = ''.join(c for c in n if c.isalnum() or c in '._- ()[]（）【】')
    return keep.strip() or ('file_%s' % time.strftime('%m%d-%H%M%S'))


def item_html(n):
    p = os.path.join(DIR, n)
    try:
        sz = os.path.getsize(p)
    except Exception:
        sz = 0
    q = urllib.parse.quote(n)
    ext = n.rsplit('.', 1)[-1].lower() if '.' in n else ''
    prev = ''
    if ext in IMG:
        prev = '<a href="/raw/%s" target="_blank"><img class="thumb" src="/raw/%s" alt=""></a>' % (q, q)
    elif ext in TXT:
        try:
            with open(p, encoding='utf-8', errors='replace') as fh:
                txt = fh.read()
            if len(txt) > 4000:
                txt = txt[:4000] + '\u2026'
            prev = '<pre class="txt">%s</pre>' % html.escape(txt)
        except Exception:
            pass
    return ('<div class="item"><a href="/dl/%s">%s</a> <small>(%dB)</small>'
            ' &middot; <a href="/raw/%s" target="_blank">查看</a>'
            ' &middot; <a href="/del/%s">删除</a>%s</div>') % (q, html.escape(n), sz, q, q, prev)


def list_html():
    try:
        fs = sorted(os.listdir(DIR), key=lambda n: os.path.getmtime(os.path.join(DIR, n)), reverse=True)
    except Exception:
        fs = []
    return '\n'.join(item_html(n) for n in fs) or '<div class="item"><small>暂无</small></div>'


def full_page():
    url = 'http://%s:%d/' % (lan_ip(), PORT)
    try:
        qr = qr_svg(url)
    except Exception as e:
        qr = '<small>QR err %s</small>' % html.escape(str(e))
    return (PAGE.replace('__LIST__', list_html())
                .replace('__QR__', qr)
                .replace('__URL__', html.escape(url)))


class H(BaseHTTPRequestHandler):
    def _send(self, code, body=b'', ctype='text/html; charset=utf-8', extra=None):
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-cache')
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _file(self, name, inline):
        n = safe(urllib.parse.unquote(name))
        fp = os.path.join(DIR, n)
        if not os.path.isfile(fp):
            self._send(404, b'not found')
            return
        with open(fp, 'rb') as fh:
            data = fh.read()
        ext = n.rsplit('.', 1)[-1].lower() if '.' in n else ''
        ctype = {'png': 'image/png', 'jpg': 'image/jpeg', 'jpeg': 'image/jpeg', 'gif': 'image/gif',
                 'webp': 'image/webp', 'bmp': 'image/bmp', 'txt': 'text/plain; charset=utf-8'}.get(ext, 'application/octet-stream')
        disp = 'inline' if inline else 'attachment'
        self._send(200, data, ctype,
                   {'Content-Disposition': '%s; filename="%s"' % (disp, urllib.parse.quote(n))})

    def _serve_path(self, fp, inline=True):
        # 带 Range 支持(视频/音频可流式播放 + 拖动进度)
        if not fp or not os.path.isfile(fp):
            self._send(404, b'not found'); return
        n = os.path.basename(fp)
        ctype = MIME.get(_ext(n), 'application/octet-stream')
        size = os.path.getsize(fp)
        start, end, code = 0, size - 1, 200
        extra = {'Content-Disposition': '%s; filename="%s"' % ('inline' if inline else 'attachment', urllib.parse.quote(n)),
                 'Accept-Ranges': 'bytes'}
        rng = self.headers.get('Range')
        if rng and rng.startswith('bytes='):
            try:
                a, _, b = rng[6:].split(',')[0].partition('-')
                if a: start = int(a)
                if b: end = int(b)
                if start > end or start >= size:
                    raise ValueError
                code = 206
                extra['Content-Range'] = 'bytes %d-%d/%d' % (start, end, size)
            except Exception:
                start, end, code = 0, size - 1, 200
        length = end - start + 1
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(length))
        self.send_header('Cache-Control', 'no-cache')
        for k, v in extra.items():
            self.send_header(k, v)
        self.end_headers()
        remain = length
        with open(fp, 'rb') as fh:
            fh.seek(start)
            while remain > 0:
                chunk = fh.read(min(262144, remain))
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except Exception:
                    break
                remain -= len(chunk)

    def _redirect(self):
        self.send_response(303)
        self.send_header('Location', '/')
        self.end_headers()

    def do_GET(self):
        p = urllib.parse.urlparse(self.path).path
        if p in ('/', ''):
            self._send(200, full_page().encode('utf-8'))
        elif p == '/list':
            self._send(200, list_html().encode('utf-8'))
        elif p == '/usb':
            self._send(200, usb_full(urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).get('d', [''])[0]).encode('utf-8'))
        elif p == '/usbfile':
            self._serve_path(usb_file(urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).get('p', [''])[0]), True)
        elif p == '/usbview':
            self._send(200, usb_view_page(urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).get('p', [''])[0]).encode('utf-8'))
        elif p == '/usbplay':
            self._send(200, usb_play_page(urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).get('p', [''])[0]).encode('utf-8'))
        elif p.startswith('/raw/'):
            self._file(p[5:], True)
        elif p.startswith('/dl/'):
            self._file(p[4:], False)
        elif p.startswith('/del/'):
            try:
                os.remove(os.path.join(DIR, safe(urllib.parse.unquote(p[5:]))))
            except Exception:
                pass
            self._redirect()
        else:
            self._send(404, b'?')

    def do_POST(self):
        p = urllib.parse.urlparse(self.path).path
        ct = self.headers.get('Content-Type', '')
        if p == '/text':
            ln = int(self.headers.get('Content-Length') or 0)
            txt = urllib.parse.parse_qs(self.rfile.read(ln).decode('utf-8', 'replace')).get('text', [''])[0]
            if txt.strip():
                with open(os.path.join(DIR, 'text_%s.txt' % time.strftime('%m%d-%H%M%S')), 'w', encoding='utf-8') as fh:
                    fh.write(txt)
            self._redirect()
        elif p == '/upload' and ct.startswith('multipart/form-data'):
            fs = cgi.FieldStorage(fp=self.rfile, headers=self.headers,
                                  environ={'REQUEST_METHOD': 'POST', 'CONTENT_TYPE': ct})
            for f in getattr(fs, 'list', []):
                if getattr(f, 'filename', None):
                    with open(os.path.join(DIR, safe(f.filename)), 'wb') as fh:
                        fh.write(f.file.read())
            self._redirect()
        else:
            self._send(404, b'?')

    def log_message(self, *a):
        pass




# ---------------- USB 浏览 ----------------
USB_MOUNT = "/mnt/usb"
USB_ROOTS = ["/mnt/usb", "/proc/1/root/storage/sda", "/proc/1/root/storage/sdb",
             "/proc/1/root/storage/sdc", "/proc/1/root/mnt/usb"]


def _is_mount(p):
    try:
        with open("/proc/mounts") as f:
            for ln in f:
                if len(ln.split()) > 1 and ln.split()[1] == p:
                    return True
    except Exception:
        pass
    return False


def ensure_usb_mount():
    # NTFS/exFAT 盘 vold 不认 -> 用 ntfs-3g 挂到 /mnt/usb
    # 关键: 处理"设备已拔但挂载还在"的陈旧挂载 + 重插后盘符变化(sda->sdb)
    import glob, subprocess
    try:
        os.makedirs(USB_MOUNT, exist_ok=True)
    except Exception:
        return
    dev = _mount_dev(USB_MOUNT)
    if dev:
        if os.path.exists(dev):
            return                      # 挂载健康
        # 底层设备已不在 -> 陈旧挂载, 先卸
        subprocess.call(["umount", USB_MOUNT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if _mount_dev(USB_MOUNT):
            subprocess.call(["umount", "-l", USB_MOUNT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if _mount_dev(USB_MOUNT):
            return                      # 卸不掉就算了, 避免乱挂
    for d in sorted(glob.glob("/dev/block/sd*[0-9]")):
        for cmd in (["ntfs-3g", "-o", "rw,uid=1000,gid=1015,umask=0002,allow_other", d, USB_MOUNT],
                    ["mount", "-t", "exfat", "-o", "rw", d, USB_MOUNT],
                    ["mount", "-t", "vfat", "-o", "rw", d, USB_MOUNT]):
            try:
                if subprocess.call(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0:
                    return
            except Exception:
                pass


def _mount_dev(p):
    try:
        for ln in open("/proc/mounts"):
            f = ln.split()
            if len(f) > 1 and f[1] == p:
                return f[0]
    except Exception:
        pass
    return None


def usb_root():
    ensure_usb_mount()
    for r in USB_ROOTS:
        try:
            if os.path.isdir(r) and os.listdir(r):
                return r
        except Exception:
            pass
    for r in USB_ROOTS:
        if os.path.isdir(r):
            return r
    return None


def _human(sz):
    for u in ("B", "K", "M", "G"):
        if sz < 1024:
            return "%d%s" % (sz, u)
        sz /= 1024.0
    return "%.1fT" % sz


VIDEO_EXT = {'mp4', 'mkv', 'webm', 'avi', 'mov', 'flv', 'ts', 'm4v', '3gp', 'wmv', 'mpg', 'mpeg', 'rmvb'}
AUDIO_EXT = {'mp3', 'wav', 'flac', 'aac', 'm4a', 'ogg', 'wma', 'ape', 'opus'}
IMAGE_EXT = {'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'}
MIME = {'png': 'image/png', 'jpg': 'image/jpeg', 'jpeg': 'image/jpeg', 'gif': 'image/gif', 'webp': 'image/webp',
        'bmp': 'image/bmp', 'txt': 'text/plain; charset=utf-8', 'log': 'text/plain; charset=utf-8',
        'mp3': 'audio/mpeg', 'm4a': 'audio/mp4', 'wav': 'audio/wav', 'flac': 'audio/flac', 'aac': 'audio/aac',
        'ogg': 'audio/ogg', 'mp4': 'video/mp4', 'm4v': 'video/mp4', 'webm': 'video/webm', 'mkv': 'video/x-matroska',
        'avi': 'video/x-msvideo', 'mov': 'video/quicktime', 'ts': 'video/mp2t', '3gp': 'video/3gpp', 'pdf': 'application/pdf'}


def _ext(n):
    return n.rsplit('.', 1)[-1].lower() if '.' in n else ''


def _icon(n, isd):
    if isd:
        return "\U0001F4C1"
    e = _ext(n)
    if e in IMAGE_EXT: return "\U0001F5BC"
    if e in VIDEO_EXT: return "\U0001F3AC"
    if e in AUDIO_EXT: return "\U0001F3B5"
    if e == 'pdf': return "\U0001F4D5"
    return "\U0001F4C4"


def _q(s):
    return urllib.parse.quote(s)


def usb_page(rel):
    root = usb_root()
    if not root:
        return "<div class='empty'>未检测到 U 盘。请插上 U 盘后点刷新。</div>"
    rel = (rel or "").strip("/")
    base = os.path.normpath(os.path.join(root, rel)) if rel else root
    if not base.startswith(root):
        base = root
    rel = "" if base == root else os.path.relpath(base, root)
    head = "<div class='crumb'><a href='/usb?d='>\U0001F4BE U盘</a> / <b>%s</b></div>" % html.escape(rel or "/")
    nav = ""
    if rel:
        up = "/".join(rel.split("/")[:-1])
        nav = "<a class='up' href='/usb?d=%s'>\u2B06 上一级</a>" % _q(up)
    try:
        names = sorted(os.listdir(base))
    except Exception as e:
        return head + ("<div class='empty'>读取失败: %s</div>" % html.escape(str(e)))
    cards = []
    for n in names:
        if n.startswith("."):
            continue
        p = os.path.join(base, n)
        try:
            isd = os.path.isdir(p); sz = 0 if isd else os.path.getsize(p)
        except Exception:
            isd, sz = False, 0
        r = (rel + "/" + n).strip("/")
        e = _ext(n)
        if isd:
            href = "/usb?d=%s" % _q(r); thumb = "<div class='big'>%s</div>" % _icon(n, True)
        elif e in IMAGE_EXT:
            href = "/usbview?p=%s" % _q(r); thumb = "<div class='th' style=\"background-image:url('/usbfile?p=%s')\"></div>" % _q(r)
        elif e in VIDEO_EXT or e in AUDIO_EXT:
            href = "/usbplay?p=%s" % _q(r); thumb = "<div class='big'>%s</div>" % _icon(n, False)
        else:
            href = "/usbfile?p=%s" % _q(r); thumb = "<div class='big'>%s</div>" % _icon(n, False)
        cards.append(("<a class='card' href='%s'>%s<div class='nm'>%s</div><div class='sz'>%s</div></a>" % (href, thumb, html.escape(n), "" if isd else _human(sz))))
    grid = "<div class='grid'>%s</div>" % "".join(cards) if cards else "<div class='empty'>(空)</div>"
    return head + nav + grid


USBPAGE = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>外接U盘</title>
<style>
body{font-family:sans-serif;margin:14px;background:#0f1420;color:#dce6f5}
a{color:#dce6f5;text-decoration:none}
.crumb{font-size:16px;margin-bottom:10px}.crumb a{color:#7fc4ff}
.up{display:inline-block;margin-bottom:10px;padding:8px 14px;background:#1b2740;border-radius:8px;color:#9fd0ff}
.grid{font-size:0}
.card{display:inline-block;vertical-align:top;width:158px;margin:6px;background:#141c2b;border:1px solid #24304a;border-radius:10px;padding:8px;text-align:center;overflow:hidden;font-size:13px;box-sizing:border-box}
.card .big{font-size:44px;line-height:1.25}
.card .th{height:92px;border-radius:6px;background-size:cover;background-position:center;background-color:#0b111c}
.card .nm{margin-top:6px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.card .sz{font-size:11px;color:#7d8fa8}
.empty{color:#8fa6c2;padding:20px;font-size:14px}
</style></head><body>
<h1 style="font-size:19px">&#128190; 外接U盘</h1>__LIST__
</body></html>"""


def usb_full(rel):
    return USBPAGE.replace("__LIST__", usb_page(rel))


def usb_view_page(rel):
    return ("<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            "<body style='margin:0;background:#000;text-align:center'>"
            "<div style='padding:8px'><a style='color:#9fd0ff;text-decoration:none' href='/usb?d=%s'>\u2B05 返回</a></div>"
            "<img src='/usbfile?p=%s' style='max-width:100%%;max-height:88vh'></body>") % (_q(os.path.dirname(rel)), _q(rel))


def usb_play_page(rel):
    e = _ext(rel)
    src = "/usbfile?p=%s" % _q(rel)
    if e in AUDIO_EXT:
        tag = "<audio src='%s' controls autoplay style='width:92%%'></audio>" % src
    else:
        tag = "<video src='%s' controls autoplay style='max-width:100%%;max-height:84vh'></video>" % src
    return ("<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            "<body style='margin:0;background:#000;color:#ddd;text-align:center'>"
            "<div style='padding:8px'><a style='color:#9fd0ff;text-decoration:none' href='/usb?d=%s'>\u2B05 返回</a> "
            "<span style='color:#8fa6c2'>%s</span></div>%s</body>") % (_q(os.path.dirname(rel)), html.escape(os.path.basename(rel)), tag)


def usb_file(rel):
    root = usb_root()
    if not root:
        return None
    p = os.path.normpath(os.path.join(root, (rel or "").strip("/")))
    if not p.startswith(root) or not os.path.isfile(p):
        return None
    return p


if __name__ == '__main__':
    srv = ThreadingHTTPServer(('0.0.0.0', PORT), H)
    print('transfer server on 0.0.0.0:%d dir=%s' % (PORT, DIR), flush=True)
    srv.serve_forever()
