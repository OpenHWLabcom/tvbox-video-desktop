package com.openhwlab.desktop;

import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 回收站 (原生): 收进来的文件可还原/彻底删除; 也可从 /sdcard 浏览并把文件收进回收站 */
public class RecycleBinActivity extends BaseActivity {
    private final Handler h = new Handler();
    private LinearLayout itemsBox, listBox;
    private String dir = "/storage/emulated/0";
    private boolean confirmEmpty = false;

    protected String title() { return "回收站"; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
        loadItems();
        loadDir();
    }

    private void build() {
        body.removeAllViews();

        LinearLayout row = section("回收站", null);
        row.addView(chip("刷新", false, new View.OnClickListener() {
            public void onClick(View v) { loadItems(); }
        }));
        row.addView(chip("清空回收站", false, new View.OnClickListener() {
            public void onClick(View v) {
                if (confirmEmpty) { httpGetCb(BASE + "cgi-bin/admin?cmd=emptyrecycle", reloadCb()); confirmEmpty = false; }
                else { confirmEmpty = true; loadItems(); }
            }
        }));
        if (confirmEmpty) {
            row.addView(chip("确认清空？", true, new View.OnClickListener() {
                public void onClick(View v) { confirmEmpty = false; loadItems(); }
            }));
        }
        itemsBox = new LinearLayout(this);
        itemsBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(itemsBox);
        itemsBox.addView(text("读取中…", 13, 0xFF8FA6C2, dp(6)));

        LinearLayout row2 = section("收进回收站（浏览 /sdcard）", dir);
        row2.addView(chip("上一级", false, new View.OnClickListener() {
            public void onClick(View v) {
                int i = dir.lastIndexOf('/');
                if (i > 14) { dir = dir.substring(0, i); loadDir(); }   // 不越过 /storage/emulated/0
            }
        }));
        row2.addView(chip("刷新", false, new View.OnClickListener() {
            public void onClick(View v) { loadDir(); }
        }));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(listBox);

        body.addView(text("说明: 收进回收站 = 文件移到 /sdcard/.recycle/ 并可还原; 彻底删除才是真删。"
                + "（盒子自带文件管理器里的删除不走这里）", 12, 0xFF8FA6C2, dp(16)));
    }

    // ---------- 回收站列表 ----------
    private void loadItems() {
        httpGetCb(BASE + "cgi-bin/admin?cmd=recycle", new Cb() {
            public void done(String r) {
                itemsBox.removeAllViews();
                String[] lines = r.split("\n");
                int n = 0; long total = 0;
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].startsWith("total=")) {
                        try { total = Long.parseLong(lines[i].substring(6).trim()); } catch (Throwable e) { }
                        continue;
                    }
                    if (!lines[i].startsWith("item|")) continue;
                    String[] f = lines[i].split("\\|", 5);
                    if (f.length < 5) continue;
                    itemsBox.addView(itemRow(f[1], f[2], f[3], f[4]));
                    n++;
                }
                if (n == 0) itemsBox.addView(text("（回收站是空的）", 13, 0xFF8FA6C2, dp(6)));
                else itemsBox.addView(text("共 " + n + " 项 · 合计约 " + (total / 1024) + " MB", 13, 0xFF8FD0FF, dp(6)));
            }
        });
    }

    private View itemRow(final String id, String orig, String sizeKb, String epoch) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView nm = new TextView(this);
        String name = orig.lastIndexOf('/') > 0 ? orig.substring(orig.lastIndexOf('/') + 1) : orig;
        nm.setText(name + "   " + (Long.parseLong(sizeKb) / 1024) + " MB");
        nm.setTextColor(0xFFFFFFFF);
        nm.setTextSize(14);
        TextView pk = new TextView(this);
        String when = epoch;
        try { when = new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(Long.parseLong(epoch) * 1000L)); }
        catch (Throwable e) { }
        pk.setText("来自 " + orig + "  ·  " + when);
        pk.setTextColor(0xFF7D8FA8);
        pk.setTextSize(11);
        col.addView(nm);
        col.addView(pk);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        row.addView(chip("还原", false, new View.OnClickListener() {
            public void onClick(View v) {
                httpGetCb(BASE + "cgi-bin/admin?cmd=restore&h=" + hex(id), reloadCb());
            }
        }));
        row.addView(chip("彻底删除", false, new View.OnClickListener() {
            public void onClick(View v) {
                httpGetCb(BASE + "cgi-bin/admin?cmd=purge&h=" + hex(id), reloadCb());
            }
        }));
        return row;
    }

    private Cb reloadCb() {
        return new Cb() {
            public void done(String r) {
                loadItems();
                loadDir();
            }
        };
    }

    // ---------- 文件浏览(收进回收站) ----------
    private void loadDir() {
        httpGetCb(BASE + "cgi-bin/admin?cmd=list&h=" + hex(dir), new Cb() {
            public void done(String r) {
                listBox.removeAllViews();
                String[] lines = r.split("\n");
                int n = 0;
                for (int i = 0; i < lines.length; i++) {
                    String ln = lines[i];
                    if (ln.startsWith("dir=")) { dir = ln.substring(4).trim(); continue; }
                    if (!ln.startsWith("entry|")) continue;
                    String[] f = ln.split("\\|", 5);
                    if (f.length < 5) continue;
                    listBox.addView(entryRow(f[1], f[2], f[3], f[4]));
                    n++;
                }
                if (n == 0) listBox.addView(text("（空目录）", 13, 0xFF8FA6C2, dp(6)));
            }
        });
    }

    private View entryRow(String type, String name, String sizeKb, final String path) {
        final boolean isDir = "d".equals(type);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView tv = new TextView(this);
        tv.setText((isDir ? "\uD83D\uDCC1  " : "") + name + (isDir ? "/" : ("    " + (Long.parseLong(sizeKb) / 1024) + " MB")));
        tv.setTextColor(isDir ? 0xFF8FD0FF : 0xFFFFFFFF);
        tv.setTextSize(13.5f);
        tv.setClickable(true);
        if (isDir) {
            tv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { dir = path; loadDir(); }
            });
        }
        row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));

        row.addView(chip("收进回收站", false, new View.OnClickListener() {
            public void onClick(View v) {
                httpGetCb(BASE + "cgi-bin/admin?cmd=trash&h=" + hex(path), reloadCb());
            }
        }));
        return row;
    }
}
