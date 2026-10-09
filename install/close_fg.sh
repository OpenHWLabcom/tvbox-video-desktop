#!/system/bin/sh
# OpenHW Lab · 关闭「当前前台程序」(不加桌面自己)
export PATH=/system/bin:/system/xbin:/sbin
CUR=$(dumpsys window windows 2>/dev/null | busybox grep -m1 'mCurrentFocus' | busybox sed -e 's/.*u0 //' -e 's#/.*##')
case "$CUR" in
  ""|com.openhwlab.desktop|com.android.systemui) exit 0 ;;
  *) am force-stop "$CUR" ;;
esac
