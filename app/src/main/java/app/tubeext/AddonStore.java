package app.tubeext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

/** First-party native catalog backed by Mozilla's public AMO add-on search API. */
public final class AddonStore {
    public interface Installer { void install(String signedXpiUrl); }
    private final Activity activity;
    private final Installer installer;
    private final LinearLayout results;
    private final EditText query;
    private final AlertDialog dialog;
    private int serial = 0;

    private AddonStore(Activity activity, Installer installer) {
        this.activity = activity;
        this.installer = installer;
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(5), dp(12), dp(4));
        query = new EditText(activity);
        query.setSingleLine(true);
        query.setHint("搜索 Firefox 安卓插件");
        query.setText("translate");
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        panel.addView(query, new LinearLayout.LayoutParams(-1, dp(51)));
        query.setOnEditorActionListener((v,id,event) -> {
            search(query.getText().toString().trim()); return true;
        });
        LinearLayout filters = new LinearLayout(activity);
        panel.addView(filters, new LinearLayout.LayoutParams(-1, dp(44)));
        menuButton(filters, "搜索", () -> search(query.getText().toString().trim()));
        menuButton(filters, "翻译", () -> searchPreset("translate"));
        menuButton(filters, "去广告", () -> searchPreset("adblock"));
        menuButton(filters, "字幕", () -> searchPreset("subtitles"));

        ScrollView scroll = new ScrollView(activity);
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(410)));
        TextView hint = new TextView(activity);
        hint.setText("Mozilla 官方插件索引 · 点击安装直接装进 TubeExt\n只支持 Mozilla 签名的 Firefox 兼容版 XPI，不支持 Chrome CRX。");
        hint.setTextSize(11);
        hint.setPadding(0, dp(7), 0, 0);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        dialog = new AlertDialog.Builder(activity)
            .setTitle("插件市场").setView(panel)
            .setPositiveButton("关闭", (d,w)-> {}).create();
    }

    public static void open(Activity activity, Installer installer) {
        AddonStore store = new AddonStore(activity, installer);
        store.dialog.show();
        if (store.dialog.getWindow() != null)
            store.dialog.getWindow().setLayout(
                activity.getResources().getDisplayMetrics().widthPixels - store.dp(12), -2);
        store.search("translate");
    }
    private int dp(int px) {
        return (int)(px * activity.getResources().getDisplayMetrics().density + .5f);
    }
    private void searchPreset(String text) {
        query.setText(text);
        search(text);
    }
    private void menuButton(LinearLayout row, String name, Runnable r) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setText(name);
        row.addView(button, new LinearLayout.LayoutParams(0, dp(43), 1));
        button.setOnClickListener(v -> r.run());
    }
    private void label(String value) {
        results.removeAllViews();
        TextView text = new TextView(activity);
        text.setPadding(dp(10), dp(14), dp(10), dp(14));
        text.setText(value);
        results.addView(text);
    }
    private void search(String input) {
        if (input.isEmpty()) { label("输入插件名称或用途"); return; }
        final int token = ++serial;
        label("正在向 Mozilla 插件市场查询…");
        new Thread(() -> {
            try {
                String address = "https://addons.mozilla.org/api/v5/addons/search/?"
                    + "app=android&type=extension&page_size=20&lang=zh-CN&q="
                    + URLEncoder.encode(input, "UTF-8");
                HttpURLConnection connection = (HttpURLConnection)new URL(address).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "TubeExt/0.2");
                try {
                    if (connection.getResponseCode() != 200)
                        throw new IllegalStateException("HTTP " + connection.getResponseCode());
                    try (InputStream stream = connection.getInputStream()) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = stream.read(buffer)) != -1) {
                            if (out.size() + length > 2_500_000)
                                throw new IllegalStateException("插件列表太大");
                            out.write(buffer, 0, length);
                        }
                        JSONArray data = new JSONObject(out.toString("UTF-8")).getJSONArray("results");
                        activity.runOnUiThread(() -> {
                            if (token != serial || !dialog.isShowing()) return;
                            results.removeAllViews();
                            if (data.length() == 0) { label("无匹配插件，尝试英文关键词"); return; }
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject addon = data.optJSONObject(i);
                                if (addon != null) addResult(addon);
                            }
                        });
                    }
                } finally { connection.disconnect(); }
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (token == serial && dialog.isShowing())
                        label("插件市场连接失败：" + error.getMessage() +
                            "\n请检查网络，或使用【插件 → 输入 XPI 链接】安装。");
                });
            }
        }, "amo-search").start();
    }
    private String title(JSONObject addon) {
        Object name = addon.opt("name");
        if (name instanceof String) return (String)name;
        if (name instanceof JSONObject) {
            JSONObject map = (JSONObject)name;
            String zh = map.optString("zh-CN");
            return !zh.isEmpty() ? zh : map.optString("en-US", addon.optString("slug"));
        }
        return addon.optString("slug", "未命名扩展");
    }
    private String xpi(JSONObject addon) {
        JSONObject version = addon.optJSONObject("current_version");
        if (version != null) {
            JSONObject file = version.optJSONObject("file");
            if (file != null && file.optString("url").startsWith("https://"))
                return file.optString("url");
        }
        String slug = addon.optString("slug");
        return slug.matches("[a-zA-Z0-9_-]+") ?
            "https://addons.mozilla.org/firefox/downloads/latest/" + slug + "/latest.xpi" : "";
    }
    private void addResult(JSONObject addon) {
        String title = title(addon);
        String slug = addon.optString("slug");
        String file = xpi(addon);
        int users = addon.optInt("average_daily_users", 0);
        LinearLayout entry = new LinearLayout(activity);
        entry.setOrientation(LinearLayout.VERTICAL);
        entry.setPadding(dp(6), dp(8), dp(6), dp(8));
        TextView name = new TextView(activity);
        name.setText(title);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextSize(16);
        entry.addView(name);
        TextView info = new TextView(activity);
        info.setText(slug + " · 用户 " + users);
        info.setTextSize(12);
        entry.addView(info);
        LinearLayout actions = new LinearLayout(activity);
        entry.addView(actions, new LinearLayout.LayoutParams(-1, dp(44)));
        Button install = new Button(activity);
        install.setText("安装到本 APP");
        install.setAllCaps(false);
        install.setTextSize(12);
        actions.addView(install, new LinearLayout.LayoutParams(0, -1, 2));
        install.setEnabled(!file.isEmpty() && !addon.optBoolean("is_disabled_by_mozilla", false));
        install.setOnClickListener(v -> {
            new AlertDialog.Builder(activity).setTitle("安装 " + title + "？")
                .setMessage("来自 Mozilla 官方插件目录。安装前将再次确认所需权限。")
                .setPositiveButton("安装", (d,w) -> {
                    dialog.dismiss();
                    installer.install(file);
                }).setNegativeButton("取消",null).show();
        });
        Button detail = new Button(activity);
        detail.setText("官网");
        detail.setAllCaps(false);
        detail.setTextSize(12);
        actions.addView(detail, new LinearLayout.LayoutParams(0, -1, 1));
        detail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://addons.mozilla.org/android/addon/" + slug + "/"));
            try { activity.startActivity(intent); }
            catch (Exception error) {
                Toast.makeText(activity,"无法打开浏览器",Toast.LENGTH_SHORT).show();
            }
        });
        results.addView(entry, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(activity);
        divider.setBackgroundColor(Color.GRAY);
        results.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }
}
