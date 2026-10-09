#!/bin/bash
# ============================================================
#  OpenHW Lab · 视频追剧版桌面小电脑 —— 一键安装（在电脑上跑）
#  用法：  ./一键安装.sh <盒子IP>
#  前提：  盒子已开启 ADB 调试、电脑装了 adb、盒子与电脑同一局域网
#  作用：  装桌面 App、瘦身、扩容、配开机自启/兜底，并安装 WPS Office(办公)。
#          全程可重复执行（幂等）。
# ============================================================
set -e
IP="$1"
[ -z "$IP" ] && { echo "用法: $0 <盒子IP>  （例：$0 192.168.1.100）"; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"
say(){ echo -e "\n\033[1;36m==> $*\033[0m"; }

say "连接盒子 $IP:5555"
adb connect "${IP}:5555"
adb wait-for-device

say "推文件到盒子"
adb shell 'mkdir -p /data'
adb push "$HERE/桌面.apk"            /data/桌面.apk
adb push "$HERE/1-系统瘦身.sh"        /data/瘦身.sh
adb push "$HERE/2-内存扩容.sh"        /data/扩容.sh
adb push "$HERE/3-开机自启.sh"        /data/startlinux.sh
adb push "$HERE/4-开机兜底.sh"        /data/bootfix.sh

say "安装桌面 App"
adb shell 'pm install -r /data/桌面.apk'

say "系统瘦身"
adb shell 'sh /data/瘦身.sh' || true

say "内存扩容"
adb shell 'sh /data/扩容.sh' || true

say "（把脚本放进 Linux 子系统，供开机链调用）"
echo "  如果你的盒子已建好 Linux 子系统(/data/linux)，把互传与系统工具服务拷进去："
adb push "$HERE/5-局域网互传.py" /data/linux/root/transfer.py 2>/dev/null || \
  echo "  ⚠️ /data/linux 不存在：先看教程“搭建 Linux 子系统”一节"
adb push "$HERE/6-系统工具服务.py" /data/linux/root/boxctl.py 2>/dev/null || true
adb push "$HERE/close_fg.sh"        /data/close_fg.sh 2>/dev/null || true

say "配置开机自启（各 ROM 的 init 钩子文件名不同，二选一或都加）"
echo "  在盒子里执行（示例，把 <init脚本> 换成你 ROM 的：如 /system/etc/init.*.sh 或 install-recovery.sh）："
echo "      echo 'sh /data/startlinux.sh &' >> /system/etc/<init脚本>"

say "安装 WPS Office（办公 · 可新建/编辑 Word/Excel/PPT）"
if adb shell pm list packages | grep -q cn.wps.moffice_eng; then
  echo "  已装 WPS，跳过"
else
  WPS_URL="http://android-apps.25pp.com/fs08/2018/12/28/10/106_1a2efec372f8da3fad13aa2cd3988bba.apk"
  UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  echo "  下载 WPS Office 11.4.1（armv7 · 兼容 Android 4.4）..."
  curl -L -A "$UA" "$WPS_URL" -o /tmp/wps1141.apk || echo "  ⚠️ 下载失败，请手动安装 WPS Office 11.x（armv7）"
  if [ -s /tmp/wps1141.apk ]; then
    adb push /tmp/wps1141.apk /data/local/tmp/wps1141.apk
    adb shell 'pm install -r /data/local/tmp/wps1141.apk; rm -f /data/local/tmp/wps1141.apk' \
      || echo "  ⚠️ 安装失败：多半是 /data 空间不足，先清理缓存/删无用应用再重跑"
  fi
fi

say "设为开机桌面"
adb shell 'am start -n com.openhwlab.desktop/.MainActivity'

say "完成！在盒子上：点一次桌面；以后开机自动进桌面。"
echo "  局域网互传：手机浏览器打开 http://$IP:8890/"
