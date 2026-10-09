package com.openhwlab.desktop;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 程序和功能 (原生): 列出已装 App(图标/名称/包名), 打开 / 卸载(走 root CGI) */
public class ProgramsActivity extends BaseActivity {
    private final Handler h = new Handler();
    private boolean showSystem = false;
    private String pending = null;   // 待确认卸载的包名(两次点击确认, 不用弹窗)

    protected String title() { return "程序和功能"; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
    }

    private void build() {
        body.removeAllViews();

        LinearLayout row = section("已安装的程序", showSystem ? "含系统程序" : "仅第三方程序");
        row.addView(chip(showSystem ? "只看第三方" : "显示系统程序", false, new View.OnClickListener() {
            public void onClick(View v) { showSystem = !showSystem; build(); }
        }));
        row.addView(chip("刷新", false, new View.OnClickListener() {
            public void onClick(View v) { build(); }
        }));

        final PackageManager pm = getPackageManager();
        List<PackageInfo> all = pm.getInstalledPackages(0);
        List<PackageInfo> show = new ArrayList<PackageInfo>();
        for (PackageInfo pi : all) {
            if (pi.applicationInfo == null) continue;
            boolean sys = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!showSystem && sys) continue;
            if (!showSystem && "com.openhwlab.desktop".equals(pi.packageName)) continue;
            show.add(pi);
        }
        Collections.sort(show, new Comparator<PackageInfo>() {
            public int compare(PackageInfo a, PackageInfo b) {
                String x = pm.getApplicationLabel(a.applicationInfo).toString();
                String y = pm.getApplicationLabel(b.applicationInfo).toString();
                return x.compareToIgnoreCase(y);
            }
        });
        for (int i = 0; i < show.size(); i++) body.addView(appRow(show.get(i)));
        if (show.isEmpty()) body.addView(text("（没有可显示的程序）", 14, 0xFF8FA6C2, dp(10)));
    }

    private View appRow(final PackageInfo pi) {
        final PackageManager pm = getPackageManager();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(10), dp(6), dp(10));

        ImageView im = new ImageView(this);
        try {
            Drawable d = pm.getApplicationIcon(pi.applicationInfo);
            im.setImageDrawable(d);
        } catch (Throwable e) { }
        row.addView(im, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        TextView nm = new TextView(this);
        try { nm.setText(pm.getApplicationLabel(pi.applicationInfo).toString()); }
        catch (Throwable e) { nm.setText(pi.packageName); }
        nm.setTextColor(0xFFFFFFFF);
        nm.setTextSize(15);
        TextView pk = new TextView(this);
        pk.setText(pi.packageName);
        pk.setTextColor(0xFF7D8FA8);
        pk.setTextSize(11.5f);
        col.addView(nm);
        col.addView(pk);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        final String pkg = pi.packageName;
        row.addView(chip("打开", false, new View.OnClickListener() {
            public void onClick(View v) { launch(pkg); }
        }));
        if (!"com.openhwlab.desktop".equals(pkg)) {
            final boolean pend = pkg.equals(pending);
            row.addView(chip(pend ? "确认卸载" : "卸载", pend, new View.OnClickListener() {
                public void onClick(View v) {
                    Log.i("kiosk", "uninstall tap " + pkg + " pend=" + pend);
                    if (pend) { doUninstall(pkg); }
                    else { pending = pkg; build(); }
                }
            }));
            if (pend) {
                row.addView(chip("取消", false, new View.OnClickListener() {
                    public void onClick(View v) { pending = null; build(); }
                }));
            }
        }
        return row;
    }

    private void launch(String pkg) {
        if ("cn.wps.moffice_eng".equals(pkg)) {
            // WPS 主页活动未导出 → 只能由 root 的 CGI 起 (am start)
            Toast.makeText(this, "正在打开 WPS…", Toast.LENGTH_SHORT).show();
            httpGetAsync(BASE + "cgi-bin/run?app=wps");
            return;
        }
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) { Toast.makeText(this, "这个程序没有可打开的界面", Toast.LENGTH_SHORT).show(); return; }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) {
            Toast.makeText(this, "打开失败: " + e, Toast.LENGTH_SHORT).show();
        }
    }

    private void doUninstall(final String pkg) {
        pending = null;
        Toast.makeText(this, "正在卸载 " + pkg + " ...", Toast.LENGTH_SHORT).show();
        httpGetAsync(BASE + "cgi-bin/admin?cmd=uninstall&pkg=" + pkg);
        h.postDelayed(new Runnable() { public void run() { build(); } }, 3000);
    }
}
