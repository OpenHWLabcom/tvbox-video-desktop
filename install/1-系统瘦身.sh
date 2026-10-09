#!/system/bin/sh
# ============================================================
#  OpenHW Lab · 视频追剧版桌面小电脑
#  系统瘦身——去掉运营商/广告/无用后台（放到盒子 /data/，执行：sh /data/瘦身.sh）
#  说明：系统预装应用只能"禁用(pm disable)"，第三方可以用"卸载(pm uninstall)"。
#        下面的列表是示例（不同 ROM 包名不同），先 `pm list packages` 看清再改。
# ============================================================
export PATH=/system/bin:/system/xbin:/sbin
export ANDROID_ROOT=/system ANDROID_DATA=/data
export BOOTCLASSPATH=/system/framework/core.jar:/system/framework/conscrypt.jar:/system/framework/okhttp.jar:/system/framework/core-junit.jar:/system/framework/bouncycastle.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/framework2.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/mms-common.jar:/system/framework/android.policy.jar:/system/framework/services.jar:/system/framework/apache-xml.jar

echo "== 先看看系统里有什么（可据此改下面的列表）=="
pm list packages | sort > /data/pkgs.txt
echo "已写入 /data/pkgs.txt（共 $(wc -l < /data/pkgs.txt) 个包）"

echo "== 禁用：运营商/商店/首页/广告/诊断/无用后台（按需增删）=="
for p in \
  com.hb.tv.appstore hb.tv.appstore com.zte.homemedia com.zte.newlauncher.appslistapi \
  com.ztestb.upgrade com.zte.remoteota com.zte.browser \
  com.iflytek.xiri com.android.printspooler com.android.musicfx \
  com.android.quicksearchbox com.android.htmlviewer \
  com.android.wallpaper.livepicker com.hisilicon.android.gallery3d; do
  pm disable "$p" >/dev/null 2>&1 && echo "禁用 $p" || true
done

echo "== 卸载：第三方广告/冗余 App（按需增删）=="
for p in com.holyblade.platform2 com.kingnut.video com.holyblade.tv.sdk.plug; do
  pm uninstall "$p" >/dev/null 2>&1 && echo "卸载 $p" || true
done

echo "== 想恢复某个：pm enable <包名>  或重新安装 =="
echo "== 清垃圾示例：删旧镜像/安装包/日志（确认后再删）=="
echo "   busybox df -k           # 看谁占地方"
echo "   ls /sdcard/*.img /sdcard/*.apk /data/*.log"
