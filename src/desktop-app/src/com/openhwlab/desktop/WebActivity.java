package com.openhwlab.desktop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 通用网页窗口(原生 WebView):
 *  - 真全屏, 没有浏览器工具栏
 *  - 标题栏带窗口按钮: 最小化(回桌面) / 缩小⇄最大化 / 关闭
 *  - 由 Intent extra "url" 指定页面
 */
public class WebActivity extends Activity {
    private FrameLayout content;
    private boolean small = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A1120);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF1D2C46);
        bar.setPadding(dp(12), 0, dp(8), 0);

        final String tt = getIntent().getStringExtra("title");
        TextView title = new TextView(this);
        title.setText(tt == null ? "\u7a97\u53e3" : tt);
        title.setTextColor(0xFF9FB3CC);
        title.setTextSize(13);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(wbtn("\u2014", R_ID(0)));   // 最小化
        bar.addView(wbtn("\u2750", R_ID(1)));   // 缩小
        bar.addView(wbtn("\u25A1", R_ID(2)));   // 最大化
        bar.addView(wbtn("\u2715", R_ID(3)));   // 关闭
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(34)));

        content = new FrameLayout(this);
        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setBackgroundColor(0xFF0D1726);
        web.setWebViewClient(new WebViewClient());
        { String u = getIntent().getStringExtra("url"); web.loadUrl(u == null ? "http://127.0.0.1:8890/usb" : u); }
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private int R_ID(int id) { return id; }

    private TextView wbtn(String txt, final int id) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextColor(0xFFCFE0F5);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(4), dp(12), dp(4));
        GradientDrawable g = new GradientDrawable();
        g.setColor(id == 3 ? 0xFF7A2A24 : 0xFF24314C);
        g.setCornerRadius(dp(5));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { act(id); } });
        return t;
    }

    private void act(int id) {
        if (id == 0) {                 // 最小化 -> 回桌面
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } else if (id == 1) {          // 缩小 <-> 最大化
            small = !small;
            int m = small ? dp(70) : 0;
            content.setPadding(m, m, m, m);
        } else if (id == 2) {          // 最大化
            small = false;
            content.setPadding(0, 0, 0, 0);
        } else {                       // 关闭
            finish();
        }
    }

    private int dp(float v) { return (int) (getResources().getDisplayMetrics().density * v + 0.5f); }

    @Override public void onBackPressed() { finish(); }
}
