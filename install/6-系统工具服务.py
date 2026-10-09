#!/usr/bin/env python3
# 盒子 CGI 端点 (监听 127.0.0.1:8899), 给 kiosk 用:
#   /cgi-bin/run?app=poweroff|reboot|suspend|suspendmem|home|back|tasks|close|enter
#   /cgi-bin/disp?a=set&k=<key>&v=<val>   |   a=reset   (显示/输入设置, 状态存 /data/dispset)
import os, subprocess, urllib.parse, glob
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8899
AND = "/system/bin"
DISPSET = "/proc/1/root/data/dispset"   # boxctl 跑在 chroot 里, /proc/1/root/data = Android 的 /data
SOGOU = "com.sohu.inputmethod.sogou/.SogouIME"
REMOTE = "com.amlogic.inputmethod.remote/.RemoteIME"
ENV = dict(os.environ)
ENV.update({
 "ANDROID_ROOT": "/system", "ANDROID_DATA": "/data",
 "LD_LIBRARY_PATH": "/vendor/lib:/system/lib",
 "PATH": "/system/bin:/system/xbin:/sbin:/vendor/bin",
 "BOOTCLASSPATH": "/system/framework/core.jar:/system/framework/conscrypt.jar:/system/framework/okhttp.jar:/system/framework/core-junit.jar:/system/framework/bouncycastle.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/framework2.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/mms-common.jar:/system/framework/android.policy.jar:/system/framework/services.jar:/system/framework/apache-xml.jar:/system/framework/webviewchromium.jar:/system/framework/ZTESettingImpl.jar:/system/framework/McspCompCommun.jar:/system/framework/McspCfgmanager.jar:/system/framework/MCSPLog.jar:/system/framework/McspEvt.jar:/system/framework/ZTEDisplaySetting.jar:/system/framework/McspCfgDispatcher.jar:/system/framework/MCPorting.jar:/system/framework/FrameworkAdaptImpl.jar:/system/framework/OSDCompIfaceJava.jar:/system/framework/GifView.jar:/system/framework/DialogActivity.jar",
})

def sh(cmd):
    # 在 Android 根上下文里执行 (chroot /proc/1/root):
    # 否则 ime/settings/am 这类 app_process 脚本会 Aborted(找不到 Android 的 /data/dalvik-cache)
    try:
        p = subprocess.Popen(["/usr/sbin/chroot", "/proc/1/root", "/system/bin/sh", "-c", cmd],
                             env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        out, _ = p.communicate()
        try:
            open("/proc/1/root/data/boxctl.log", "a").write("%s => [%s] %s\n" % (cmd, p.returncode, out.decode("utf-8", "ignore").strip()[:300]))
        except Exception: pass
    except Exception as e:
        return str(e)
    return "ok"

def read_disp():
    d = {}
    try:
        for ln in open(DISPSET):
            if "=" in ln:
                k, v = ln.split("=", 1); d[k.strip()] = v.strip()
    except Exception: pass
    return d

def write_disp(d):
    try:
        with open(DISPSET, "w") as f:
            for k, v in d.items(): f.write("%s=%s\n" % (k, v))
    except Exception: pass

def apply_disp(a, k, v):
    d = read_disp()
    if a == "reset":
        sh("%s/wm density reset" % AND)
        sh("%s/settings put system font_scale 1.0" % AND)
        d = {"kb": "sogou", "uamode": "desktop", "webzoom": "1.0"}
        apply_kb("sogou")
        write_ff_prefs(d)
        write_disp(d); return "ok"
    if a != "set": return "ok"
    d[k] = v
    if k == "kb":
        apply_kb(v)
    elif k == "density":
        sh("%s/wm density reset" % AND) if v in ("", "160") else sh("%s/wm density %s" % (AND, v))
    elif k == "fontscale":
        sh("%s/settings put system font_scale %s" % (AND, v))
    elif k in ("uamode", "webzoom"):
        write_ff_prefs(d)
    # nav: 只记录
    write_disp(d); return "ok"

def write_ff_prefs(d):
    # 把 "网页模式/缩放" 写进 Firefox 的 user.js, 然后重启 Firefox
    dirs = glob.glob("/proc/1/root/data/data/org.mozilla.firefox/files/mozilla/*.default")
    if not dirs:
        return
    up = os.path.join(dirs[0], "user.js")
    mode = d.get("uamode", "desktop")
    if mode == "mobile":
        try: os.remove(up)
        except Exception: pass
    else:
        DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        lines = ["// OpenHW Lab auto-generated",
                 'user_pref("general.useragent.override", "%s");' % DESKTOP_UA,
                 'user_pref("browser.viewport.desktopWidth", 1280);',
                 'user_pref("browser.viewport.scaleRatio", 1.0);',
                 'user_pref("browser.ui.zoom.force-user-scalable", true);',
                 'user_pref("privacy.trackingprotection.enabled", false);']
        z = d.get("webzoom", "")
        if z and z not in ("1.0", "100%"):
            try:
                lines.append('user_pref("layout.css.devPixelsPerPx", %.2f);' % float(z))
            except Exception: pass
        with open(up, "w") as f:
            f.write("\n".join(lines) + "\n")
    sh("%s/am force-stop org.mozilla.firefox" % AND)


def apply_kb(v):
    if v == "hw":
        # 实体键盘-英文: 关掉所有输入法 => 不弹软键盘, 硬件键盘直输
        sh("%s/ime disable %s" % (AND, SOGOU))
        sh("%s/ime disable %s" % (AND, REMOTE))
    elif v == "remote":
        sh("%s/ime enable %s" % (AND, REMOTE))
        sh("%s/ime set %s" % (AND, REMOTE))
        sh("%s/settings put secure default_input_method %s" % (AND, REMOTE))
    else:  # sogou
        sh("%s/ime enable %s" % (AND, SOGOU))
        sh("%s/ime set %s" % (AND, SOGOU))
        sh("%s/settings put secure default_input_method %s" % (AND, SOGOU))

class H(BaseHTTPRequestHandler):
    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(u.query)
        r = "ok"
        if u.path.endswith("/disp"):
            r = apply_disp(q.get("a", [""])[0], q.get("k", [""])[0], q.get("v", [""])[0])
        else:
            app = q.get("app", [""])[0]
            if app == "poweroff":   r = sh("%s/am start -a android.intent.action.ACTION_REQUEST_SHUTDOWN --ez android.intent.extra.KEY_CONFIRM false" % AND)
            elif app == "reboot":   r = sh(AND + "/reboot")
            elif app in ("suspend", "sleep", "hibernate"): r = sh(AND + "/input keyevent 26")
            elif app == "suspendmem": r = sh("echo mem > /sys/power/state")
            elif app == "home":     r = sh(AND + "/am start -a android.intent.action.MAIN -c android.intent.category.HOME")
            elif app == "back":     r = sh(AND + "/input keyevent 4")
            elif app == "tasks":    r = sh(AND + "/input keyevent 187")
            elif app == "close":    r = sh(AND + "/sh /data/close_fg.sh")
            elif app == "enter":    r = sh(AND + "/input keyevent 66")
            elif app == "play":     r = sh("%s/am start -a android.intent.action.VIEW -d 'file://%s' -t video/mp4" % (AND, q.get("f", [""])[0]))
            elif app == "wps":      r = sh("%s/am start -n cn.wps.moffice_eng/cn.wps.moffice.main.local.HomeRootActivity" % AND)
        b = ("%s %s" % (u.path, r)).encode()
        self.send_response(200); self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def log_message(self, *a): pass

if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
