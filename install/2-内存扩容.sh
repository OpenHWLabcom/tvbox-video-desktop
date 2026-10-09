#!/system/bin/sh
# ============================================================
#  OpenHW Lab · 视频追剧版桌面小电脑
#  内存扩容（把"存储当内存"）——放到盒子 /data/，开机执行：sh /data/扩容.sh
#  原理：老盒子只有 1GB RAM，跑桌面/浏览器会 OOM 杀后台。
#        建"分层交换"：zram(压缩内存，最快先用) > 磁盘swap文件(慢但能扩容)。
#  分层优先级：zram(-1) > /cache/swapfile(-2) > /sdcard/swapfile(-3)
#  幂等：已挂载就跳过；可反复执行。
#  ⚠️ swap 文件必须放在 ext4 分区（别放 vfat）。
# ============================================================
BB=/system/xbin/busybox
LOG=/data/tune.log
{
  echo "=== 扩容 $(date) ==="

  # 1) 性能模式（CPU 拉满，桌面/浏览器更跟手）
  for c in 0 1 2 3; do echo performance > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_governor 2>/dev/null; done

  # 2) zram（压缩内存；必须先于磁盘 swap，优先级才最高）
  if $BB grep -q zram /proc/swaps 2>/dev/null; then
    echo "zram 已开"
  else
    $BB swapoff /dev/block/zram0 2>/dev/null
    echo 1 > /sys/block/zram0/reset 2>/dev/null
    echo 536870912 > /sys/block/zram0/disksize 2>/dev/null   # 512MB
    $BB mkswap /dev/block/zram0 >/dev/null 2>&1
    $BB swapon /dev/block/zram0 2>/dev/null && echo "zram on" || echo "zram FAIL"
  fi

  # 3) 磁盘 swap（/cache 有空闲就用；+400MB）
  SWAPF=/cache/swapfile
  if $BB grep -q "$SWAPF" /proc/swaps 2>/dev/null; then
    echo "disk swap 已开"
  else
    [ -f "$SWAPF" ] || { $BB dd if=/dev/zero of="$SWAPF" bs=1M count=400; $BB mkswap "$SWAPF"; }
    $BB swapon "$SWAPF" && echo "disk swap on" || echo "disk swap FAIL"
  fi

  # 4) /sdcard 动态 swap（把它当"安全网"扩容层，取空余 60%，256MB~1.5GB）
  SWAPF2=/storage/emulated/0/swapfile
  i=0; while [ $i -lt 30 ]; do $BB mount | $BB grep -q " /storage/emulated/0 " && break; sleep 3; i=$((i+1)); done
  if ! $BB mount | $BB grep -q " /storage/emulated/0 "; then
    echo "sdcard 未挂载，跳过"
  elif $BB grep -q "$SWAPF2" /proc/swaps 2>/dev/null; then
    echo "sdcard swap 已开"
  else
    FREE_KB=$($BB df -k /sdcard | $BB awk '/emulated/{print (NF>=6?$4:$3)}')
    CUR=0; [ -f "$SWAPF2" ] && CUR=$(( $($BB ls -l "$SWAPF2" | $BB awk '{print $5}') / 1048576 ))
    FREE_KB=$((FREE_KB + CUR*1024))            # 把已有 swapfile 自身算回可用空间
    TK=$((FREE_KB*60/100)); [ $TK -gt 1572864 ] && TK=1572864; [ $TK -lt 262144 ] && TK=262144
    TM=$((TK/1024))
    if [ "$CUR" -lt $((TM*8/10)) ]; then
      $BB swapoff "$SWAPF2" 2>/dev/null; $BB rm -f "$SWAPF2"
      $BB dd if=/dev/zero of="$SWAPF2" bs=1M count=$TM >/dev/null 2>&1; $BB mkswap "$SWAPF2" >/dev/null 2>&1
    fi
    $BB swapon "$SWAPF2" && echo "sdcard swap on (${TM}MB)" || echo "sdcard swap FAIL"
  fi

  # 5) 精简后台动画
  settings put global window_animation_scale 0.5
  settings put global transition_animation_scale 0.5
  settings put global animator_duration_scale 0.5

  echo "--- /proc/swaps ---"; cat /proc/swaps
  echo "=== done ==="
} >> "$LOG" 2>&1
