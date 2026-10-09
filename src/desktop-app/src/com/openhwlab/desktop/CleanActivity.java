package com.openhwlab.desktop;

import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** 系统清理: 系统状态 + 正在运行的程序(可结束) + 一键清理/停后台; 实际动作由 root CGI 执行 */
public class CleanActivity extends BaseActivity {
    private final Handler h = new Handler();
    private TextView info, result;
    private LinearLayout runBox;
    private int procCount = 0;

    protected String title() { return "系统清理"; }

    private static class Proc {
        String label, pkg, imp;
        int pid, mem;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
        loadStats();
        loadRunning();
    }

    private void build() {
        body.removeAllViews();

        LinearLayout row = section("系统状态", null);
        row.addView(chip("刷新", false, new View.OnClickListener() {
            public void onClick(View v) { loadStats(); }
        }));
        info = text("读取中…", 14, 0xFFCFE0F5, dp(4));
        info.setLineSpacing(dp(5), 1f);
        body.addView(info);

        LinearLayout row2 = section("正在运行的程序", null);
        row2.addView(chip("刷新", false, new View.OnClickListener() {
            public void onClick(View v) { loadRunning(); }
        }));
        runBox = new LinearLayout(this);
        runBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(runBox);
        runBox.addView(text("读取中…", 13, 0xFF8FA6C2, dp(6)));

        LinearLayout row3 = section("清理", null);
        row3.addView(chip("一键清理", false, new View.OnClickListener() {
            public void onClick(View v) { call("clean", "清理完成"); }
        }));
        row3.addView(chip("停止后台应用", false, new View.OnClickListener() {
            public void onClick(View v) { call("bgstop", "已停止后台应用"); }
        }));

        result = text("", 14, 0xFFB8F0C8, dp(14));
        body.addView(result);

        body.addView(text("清理内容: 各应用缓存 / 临时文件 / 系统日志 / 已释放内存页缓存(drop_caches)。"
                + "只清缓存, 不会删除你的文档; 结束程序只是强制关掉它, 下次打开会自动重启。",
                12, 0xFF8FA6C2, dp(18)));
    }

    // ---------- 系统状态 ----------
    private void loadStats() {
        httpGetCb(BASE + "cgi-bin/admin?cmd=stats", new Cb() {
            public void done(String r) {
                HashMap<String, String> m = kv(r);
                StringBuilder sb = new StringBuilder();
                if (m.get("data_total") != null) sb.append("存储 /data ：可用 ").append(kb2mb(m.get("data_free")))
                        .append(" / 共 ").append(kb2mb(m.get("data_total"))).append("\n");
                if (m.get("sd_total") != null) sb.append("存储 /sdcard：可用 ").append(kb2mb(m.get("sd_free")))
                        .append(" / 共 ").append(kb2mb(m.get("sd_total"))).append("\n");
                if (m.get("mem_total") != null) sb.append("内存     ：可用 ").append(kb2mb(m.get("mem_free")))
                        .append(" / 共 ").append(kb2mb(m.get("mem_total"))).append("\n");
                if (m.get("zram") != null) sb.append("交换(zram)：").append("on".equals(m.get("zram")) ? "已开启" : "未开启");
                if (procCount > 0) sb.append("   ·   运行中的应用：").append(procCount).append(" 个");
                info.setText(sb.length() == 0 ? "获取失败（服务没响应）" : sb.toString());
            }
        });
    }

    // ---------- 正在运行 ----------
    private void loadRunning() {
        runBox.removeAllViews();
        runBox.addView(text("读取中…", 13, 0xFF8FA6C2, dp(6)));
        new Thread(new Runnable() {
            public void run() {
                final List<Proc> ps = collect();
                runOnUiThread(new Runnable() {
                    public void run() {
                        procCount = ps.size();
                        renderRunning(ps);
                        loadStats();
                    }
                });
            }
        }).start();
    }

    private List<Proc> collect() {
        List<Proc> out = new ArrayList<Proc>();
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            PackageManager pm = getPackageManager();
            List<ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
            if (ps == null) return out;
            int[] pids = new int[ps.size()];
            for (int i = 0; i < ps.size(); i++) pids[i] = ps.get(i).pid;
            Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(pids);
            for (int i = 0; i < ps.size(); i++) {
                ActivityManager.RunningAppProcessInfo p = ps.get(i);
                String base = p.processName.indexOf(':') > 0 ? p.processName.substring(0, p.processName.indexOf(':')) : p.processName;
                String lb;
                try { lb = pm.getApplicationLabel(pm.getApplicationInfo(base, 0)).toString(); }
                catch (Throwable e) { continue; }   // 非 App 进程(内核/守护) 不列
                Proc r = new Proc();
                r.pkg = base;
                r.label = lb;
                r.pid = p.pid;
                r.mem = (mi != null && i < mi.length) ? mi[i].getTotalPss() : 0;
                switch (p.importance) {
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND: r.imp = "前台"; break;
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE: r.imp = "可见"; break;
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE: r.imp = "服务"; break;
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND: r.imp = "后台"; break;
                    default: r.imp = "空闲";
                }
                out.add(r);
            }
            Collections.sort(out, new Comparator<Proc>() {
                public int compare(Proc a, Proc b) { return b.mem - a.mem; }
            });
        } catch (Throwable e) { }
        return out;
    }

    private void renderRunning(List<Proc> ps) {
        runBox.removeAllViews();
        if (ps.isEmpty()) { runBox.addView(text("（读取不到运行中的应用）", 13, 0xFF8FA6C2, dp(6))); return; }
        int total = 0;
        for (int i = 0; i < ps.size(); i++) total += ps.get(i).mem;
        runBox.addView(text("共 " + ps.size() + " 个 · 合计约 " + (total / 1024) + " MB", 13, 0xFF8FD0FF, dp(4)));
        for (int i = 0; i < ps.size(); i++) runBox.addView(procRow(ps.get(i)));
    }

    private View procRow(final Proc p) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        ImageView im = new ImageView(this);
        try {
            Drawable d = getPackageManager().getApplicationIcon(p.pkg);
            im.setImageDrawable(d);
        } catch (Throwable e) { }
        row.addView(im, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, dp(8), 0);
        TextView nm = new TextView(this);
        nm.setText(p.label + "   " + p.mem + " MB   [" + p.imp + "]");
        nm.setTextColor(0xFFFFFFFF);
        nm.setTextSize(14);
        TextView pk = new TextView(this);
        pk.setText(p.pkg + "  (pid " + p.pid + ")");
        pk.setTextColor(0xFF7D8FA8);
        pk.setTextSize(11);
        col.addView(nm);
        col.addView(pk);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!"com.openhwlab.desktop".equals(p.pkg)) {
            row.addView(chip("结束", false, new View.OnClickListener() {
                public void onClick(View v) { stop(p); }
            }));
        }
        return row;
    }

    private void stop(final Proc p) {
        result.setText("正在结束 " + p.label + " …");
        httpGetCb(BASE + "cgi-bin/admin?cmd=stop&pkg=" + p.pkg, new Cb() {
            public void done(String r) {
                HashMap<String, String> m = kv(r);
                result.setText("stopped".equals(m.get("res")) ? ("✓ 已结束 " + p.label) : ("未能结束 " + p.label + "（受保护）"));
                h.postDelayed(new Runnable() { public void run() { loadRunning(); loadStats(); } }, 1500);
            }
        });
    }

    // ---------- 清理 ----------
    private void call(final String cmd, final String okText) {
        result.setText("执行中…");
        httpGetCb(BASE + "cgi-bin/admin?cmd=" + cmd, new Cb() {
            public void done(String r) {
                HashMap<String, String> m = kv(r);
                if ("clean".equals(cmd)) {
                    String f = m.get("freed");
                    result.setText(f != null ? ("✓ " + okText + "，释放约 " + kb2mb(f) + "（缓存已清、内存缓存已回收）") : "✓ " + okText);
                } else if ("bgstop".equals(cmd)) {
                    String n = m.get("stopped");
                    result.setText(n != null ? ("✓ " + okText + " " + n + " 个") : ("✓ " + okText));
                } else {
                    result.setText("✓ " + okText);
                }
                infoRefreshFrom(m);
                loadRunning();
            }
        });
    }

    private void infoRefreshFrom(HashMap<String, String> m) {
        if (m.get("data_total") == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("存储 /data ：可用 ").append(kb2mb(m.get("data_free"))).append(" / 共 ").append(kb2mb(m.get("data_total"))).append("\n");
        if (m.get("sd_total") != null) sb.append("存储 /sdcard：可用 ").append(kb2mb(m.get("sd_free"))).append(" / 共 ").append(kb2mb(m.get("sd_total"))).append("\n");
        if (m.get("mem_total") != null) sb.append("内存     ：可用 ").append(kb2mb(m.get("mem_free"))).append(" / 共 ").append(kb2mb(m.get("mem_total"))).append("\n");
        if (m.get("zram") != null) sb.append("交换(zram)：").append("on".equals(m.get("zram")) ? "已开启" : "未开启");
        info.setText(sb.toString());
    }
}
