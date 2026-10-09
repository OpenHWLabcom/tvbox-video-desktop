#!/system/bin/sh
# ============================================================
#  OpenHW Lab · 视频追剧版桌面小电脑
#  开机自启链（放到盒子 /data/startlinux.sh）
#  由 init 脚本调用：/system/etc/init.*.sh 里加一行
#      sh /data/startlinux.sh &
#  作用：挂载 Linux 子系统 + 拉起 SSH / 局域网互传 / 系统工具服务，
#        再触发开机兜底（确保桌面在前台）。
#  幂等：重复执行不会起两份服务（start-stop-daemon 的 pidfile 去重）。
# ============================================================
R=/data/linux
CPATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
log -t startlinux "begin"

# USB 稳定：关自动省电（外接U盘/硬盘容易因省电掉线）
echo -1 > /sys/module/usbcore/parameters/autosuspend 2>/dev/null
for d in /sys/bus/usb/devices/*/; do echo on > "$d/power/control" 2>/dev/null; done

# 内存扩容（见 2-内存扩容.sh；文件存在才启用）
[ -f /data/swapfile ] && swapon /data/swapfile 2>/dev/null
[ -f /data/swap2 ]    && swapon /data/swap2    2>/dev/null

# 挂载 Linux 子系统(chroot)所需的内核文件系统
mount -t proc  proc  $R/proc 2>/dev/null
mount -t sysfs sysfs $R/sys  2>/dev/null
mount -o bind /dev   $R/dev  2>/dev/null
mkdir -p $R/mnt/sdcard 2>/dev/null
mount -o bind /storage/emulated/0 $R/mnt/sdcard 2>/dev/null
mkdir -p $R/run/sshd $R/var/run/sshd 2>/dev/null
chmod 755 $R/run/sshd $R/var/run/sshd 2>/dev/null

# SSH（远程登录到 Linux 子系统）
chroot $R /usr/sbin/sshd 2>/dev/null

# 局域网互传 :8890（手机浏览器打开 http://<盒子IP>:8890/）
chroot $R /usr/bin/env PATH=$CPATH /sbin/start-stop-daemon --start --background \
  --make-pidfile --pidfile /run/transfer.pid --exec /usr/bin/python3 -- /root/transfer.py 2>/dev/null

# 系统工具服务 :8899（桌面「关机/重启/任务切换/显示设置」用）
chroot $R /usr/bin/env PATH=$CPATH /sbin/start-stop-daemon --start --background \
  --make-pidfile --pidfile /run/boxctl.pid --exec /usr/bin/python3 -- /root/boxctl.py 2>/dev/null

# 开机兜底：关系统弹窗 + 确保桌面在前台
[ -f /data/bootfix.sh ] && /system/xbin/busybox nohup /system/bin/sh /data/bootfix.sh >/dev/null 2>&1 &

# ADB over TCP 常驻（远程救急通道；无授权公钥连不上）
setprop service.adb.tcp.port 5555
setprop ctl.restart adbd

log -t startlinux "done"
