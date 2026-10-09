#!/system/bin/sh
# ============================================================
#  OpenHW Lab · 视频追剧版桌面小电脑
#  开机兜底（放到盒子 /data/bootfix.sh）
#  由 3-开机自启.sh 后台调用。
#  作用：开机时有些 ROM 会卡在「正在升级/正在启动」的系统弹窗把桌面挡住，
#        本脚本在开机后反复「关系统弹窗 + 拉起桌面」直到桌面真的在前台。
# ============================================================
BB=/system/xbin/busybox
export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin:/system/sbin
LOG=/data/bootfix.log
HOME_PKG=com.openhwlab.desktop   # 桌面 App 的包名（改过包名就改这里）

echo "=== bootfix start ===" >> $LOG

# 1) 等系统启动完成（最多 ~5min）
n=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ $n -lt 150 ]; do
  $BB sleep 2; n=$((n+1))
done
echo "boot_completed (waited $((n*2))s)" >> $LOG

# 2) 准备 am/dumpsys 需要的环境（开机脚本缺 BOOTCLASSPATH 时 am 会静默失效）
SP=$($BB pidof system_server)
if [ -n "$SP" ]; then
  BC=$($BB tr '\0' '\n' < /proc/$SP/environ 2>/dev/null | $BB grep ^BOOTCLASSPATH= | $BB cut -d= -f2-)
fi
[ -z "$BC" ] && BC="/system/framework/core.jar:/system/framework/conscrypt.jar:/system/framework/okhttp.jar:/system/framework/core-junit.jar:/system/framework/bouncycastle.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/framework2.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/mms-common.jar:/system/framework/android.policy.jar:/system/framework/services.jar:/system/framework/apache-xml.jar:/system/framework/webviewchromium.jar"
export ANDROID_ROOT=/system ANDROID_DATA=/data LD_LIBRARY_PATH=/vendor/lib:/system/lib BOOTCLASSPATH="$BC"

# 3) 反复：关系统弹窗 + 拉起桌面，直到桌面在前台（最多 ~2min）
i=0
while [ $i -lt 8 ]; do
  $BB sleep 15
  am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
  if dumpsys window windows 2>/dev/null | $BB grep -q "mCurrentFocus=.*$HOME_PKG"; then
    echo "desktop foreground @iter $i" >> $LOG
    break
  else
    am start -n $HOME_PKG/.MainActivity >/dev/null 2>&1
  fi
  i=$((i+1))
done

echo "=== bootfix done (iter=$i) ===" >> $LOG
