package com.openhwlab.desktop;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 显示设置 (原生版) —— 界面原生, 实际改动仍由 root 的 CGI(disp) 执行
 * (wm density / settings font_scale / ime / Firefox prefs 都需要 root)
 */
public class SettingsActivity extends BaseActivity {
    private final Handler h = new Handler();

    protected String title() { return "显示设置"; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
    }

    /** 读 /data/dispset 里的当前值 */
    private String get(String k) {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/data/dispset"));
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.startsWith(k + "=")) { r.close(); return ln.substring(k.length() + 1).trim(); }
            }
            r.close();
        } catch (Throwable e) { }
        return "";
    }

    private String curLabel(String k, String v) {
        if ("density".equals(k)) return (v.length() == 0 || "160".equals(v)) ? "标准" : ("自定义 " + v);
        if ("fontscale".equals(k)) return v.length() == 0 ? "标准(1.0)" : v;
        if ("kb".equals(k)) return "sogou".equals(v) ? "搜狗拼音(中文)" : ("remote".equals(v) ? "遥控器输入法" : "实体键盘-英文直输");
        if ("nav".equals(k)) return "always".equals(v) ? "常显" : ("off".equals(v) ? "已关闭" : "自动折叠(6秒)");
        if ("uamode".equals(k)) return "mobile".equals(v) ? "手机版" : "电脑版";
        if ("webzoom".equals(k)) return v.length() == 0 ? "100%" : v;
        if ("deskzoom".equals(k)) return v.length() == 0 ? "100%" : v;
        return v;
    }

    private void build() {
        body.removeAllViews();
        choice("整机界面大小（所有程序）", "density",
                new String[][]{{"标准", "160"}, {"大", "200"}, {"更大", "240"}, {"特大", "280"}, {"小", "140"}, {"更小", "110"}}, true);
        choice("字体大小（含输入法）", "fontscale",
                new String[][]{{"标准", "1.0"}, {"小", "0.9"}, {"更小", "0.8"}, {"最小", "0.7"}}, true);
        choice("输入方式", "kb",
                new String[][]{{"实体键盘-英文", "hw"}, {"搜狗拼音（中文）", "sogou"}, {"遥控器输入法", "remote"}}, false);
        choice("导航栏（右上角 开始/返回/桌面/关闭/确定）", "nav",
                new String[][]{{"自动折叠（闲置6秒收成 ▼）", "auto"}, {"常显", "always"}, {"关闭", "off"}}, false);
        choice("网页缩放（浏览器）", "webzoom",
                new String[][]{{"100%", "1.0"}, {"125%", "1.25"}, {"150%", "1.5"}, {"175%", "1.75"}, {"200%", "2.0"}}, false);
        choice("网页模式", "uamode",
                new String[][]{{"电脑版（1280宽屏推荐）", "desktop"}, {"手机版", "mobile"}}, false);

        LinearLayout row = section("恢复", null);
        row.addView(chip("恢复默认", false, new View.OnClickListener() {
            public void onClick(View v) { apply("reset", "", "", true); }
        }));
        body.addView(text("说明: 整机界面大小 / 字体立即生效; 网页缩放与模式会自动重启 Firefox 后生效;"
                + "这两项和输入法都需要系统权限, 由本机 root 服务执行。", 12, 0xFF8FA6C2, dp(16)));
    }

    private void choice(String label, final String key, String[][] opts, final boolean recreate) {
        String cur = get(key);
        LinearLayout row = section(label, curLabel(key, cur));
        for (int i = 0; i < opts.length; i++) {
            final String v = opts[i][1];
            boolean on = v.equals(cur) || (cur.length() == 0 && "density".equals(key) && "160".equals(v))
                    || (cur.length() == 0 && "webzoom".equals(key) && "1.0".equals(v));
            row.addView(chip(opts[i][0], on, new View.OnClickListener() {
                public void onClick(View view) { apply("set", key, v, recreate); }
            }));
        }
    }

    private void apply(String a, String k, String v, final boolean recreate) {
        String url = BASE + "cgi-bin/disp?a=" + a;
        if ("set".equals(a)) url += "&k=" + k + "&v=" + v;
        httpGetAsync(url);
        h.postDelayed(new Runnable() { public void run() { build(); } }, 900);
        if (recreate) h.postDelayed(new Runnable() { public void run() { recreate(); } }, 1600);
    }
}
