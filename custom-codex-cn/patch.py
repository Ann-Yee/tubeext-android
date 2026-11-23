#!/usr/bin/env python3
"""Apply auditable Chinese UI and balance shortcuts to GPL-3.0 Mobile Codex v0.4.0.
Upstream: https://github.com/nokryong/mobile-codex
The unchanged phone control is Accessibility-based; Shizuku is NOT claimed here.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

root=Path(sys.argv[1]).resolve()
apk=Path(sys.argv[2]).resolve()
def patch(rel, before, after):
    f=root/rel
    data=f.read_text(encoding="utf-8")
    assert before in data, "Missing patch target: "+rel+" "+before[:70]
    f.write_text(data.replace(before,after,1),encoding="utf-8")
    print("patched",rel)

# Reuse the EXACT native libraries and devtools from the published signed APK.
# The original Android source is freshly checked out at the matching v0.4.0 tag.
with zipfile.ZipFile(apk) as z:
    names=set(z.namelist())
    count=0
    for entry in z.infolist():
        name=entry.filename
        if name.endswith("/"):continue
        if name.startswith("assets/runtime/") or name.startswith("assets/devtools/"):
            dest=root/"app/src/main"/name
        elif name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
            dest=root/"app/src/main/jniLibs/arm64-v8a"/Path(name).name
        else:continue
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes(z.read(entry))
        count+=1
    assert "assets/devtools/manifest.json" in names and "assets/devtools/payload.zip" in names, "APK missing bundled devtools"
    assert "lib/arm64-v8a/libcodex.so" in names, "APK missing native Codex runtime"
print("Restored",count,"published runtime files")

patch("app/build.gradle", "applicationId 'dev.mobilecodex.app'", "applicationId 'app.annyee.codexcn'")
patch("app/build.gradle", "versionCode 75", "versionCode 40001")
patch("app/build.gradle", "versionName '0.4.0'", "versionName '0.4.0-cn1'")
patch("app/src/main/AndroidManifest.xml", 'android:label="Mobile Codex"', 'android:label="Codex 手机助手"')
patch("app/src/main/java/dev/mobilecodex/app/AppLanguage.java",
      'Set.of("system", "en", "ko")', 'Set.of("system", "en", "ko", "zh")')
app_lang=root/"app/src/main/java/dev/mobilecodex/app/AppLanguage.java"
s=app_lang.read_text()
s=s.replace('    private static Map<String, String> english = Map.of();','    private static Map<String, String> english = Map.of();\n    private static Map<String, String> chinese = Map.of();',1)
s=s.replace('        configure(context);\n    }\n    static String choice', '''        try (var stream = context.getAssets().open("translations-zh.json")) {
            var bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONObject json = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            var values = new HashMap<String, String>();
            for (var keys = json.keys(); keys.hasNext();) { String key = keys.next(); values.put(key, json.getString(key)); }
            chinese = Map.copyOf(values);
        } catch (Exception failure) { android.util.Log.e("AppLanguage", "Unable to load Chinese translations", failure); }
        configure(context);
    }
    static String choice''',1)
s=s.replace('Texts.configure(locale(context).getLanguage().equals("ko"), english);','Texts.configure(locale(context).getLanguage(), english, chinese);')
app_lang.write_text(s)
(root/"app/src/main/java/dev/mobilecodex/app/core/Texts.java").write_text('''package dev.mobilecodex.app.core;
import java.util.Map;
import java.util.HashMap;
/** Explicit UI translation. Never translate model responses, files or user messages. */
public final class Texts {
    private record Catalog(String lang, Map<String,String> en, Map<String,String> ko, Map<String,String> zh) {}
    private static volatile Catalog catalog = new Catalog("ko",Map.of(),Map.of(),Map.of());
    private Texts() {}
    public static void configure(boolean korean, Map<String,String> english) {
        configure(korean ? "ko" : "en",english,Map.of());
    }
    public static void configure(String language, Map<String,String> english, Map<String,String> chinese) {
        var reverse = new HashMap<String,String>();
        english.forEach((key,value)->reverse.putIfAbsent(value,key));
        String lang = language.startsWith("zh") ? "zh" : language.startsWith("ko") ? "ko" : "en";
        catalog = new Catalog(lang,Map.copyOf(english),Map.copyOf(reverse),Map.copyOf(chinese));
    }
    public static String t(String text) {
        if (text == null) return null;
        var c = catalog;
        String en = c.en.getOrDefault(text,text);
        return switch(c.lang) {
            case "ko" -> c.ko.getOrDefault(text,text);
            case "zh" -> c.zh.getOrDefault(en,en);
            default -> en;
        };
    }
}
''',encoding="utf-8")
# Chinese text is keyed by the upstream English UI translation.  Untranslated text
# intentionally falls back to English rather than mis-translating arbitrary content.
zh={
"Settings":"设置","General":"通用","Account":"账户","Accounts":"账户","Tools":"工具","Advanced":"高级","Updates":"更新",
"Add ChatGPT account":"添加 ChatGPT 账户","Connect your ChatGPT account":"登录 ChatGPT 账户",
"Sign in with your account":"使用你的账户登录","Sign in":"登录","Sign-in required":"需要登录",
"Signing in…":"正在登录…","Sign-in code":"登录验证码","Copy sign-in code":"复制登录验证码",
"Open browser to sign in":"打开浏览器登录","Back to this screen after signing in.":"登录后返回此页面",
"Check status":"检查状态","Status unavailable":"无法获取状态","Not checked":"尚未检查",
"Runtime check required":"需要检查运行环境","Update required":"需要更新","Installing…":"正在安装…",
"CLI installation required":"需要安装命令行工具","Install / update CLI":"安装或更新 CLI",
"Models":"模型","Model":"模型","Reasoning":"推理强度","Fast mode":"快速模式",
"New chat":"新对话","Projects":"项目","Add project":"添加项目",
"Project":"项目","General chats":"一般对话","Chat history":"历史对话",
"Select folder":"选择文件夹","Choose a folder":"选择文件夹","Select working folder":"选择工作文件夹",
"Settings and usage":"设置与用量","Usage limits":"使用额度","Usage":"用量","Account and usage limits":"账户与使用额度",
"Refresh":"刷新","Remaining":"剩余","Reset time":"重置时间","Primary":"主要","Secondary":"次要",
"ChatGPT account":"ChatGPT 账户","Codex · ChatGPT":"Codex · ChatGPT",
"Connect ChatGPT account":"连接 ChatGPT 账户","Connect ChatGPT account":"连接 ChatGPT 账户",
"Log out of current account":"退出当前账户","Add ChatGPT account":"添加 ChatGPT 账户",
"Check account status":"查看账户状态","Account settings":"账户设置",
"Connect":"连接","Connected":"已连接","Disconnected":"未连接",
"Loading…":"加载中…","Working":"正在执行","Codex is working":"Codex 正在执行",
"Task stopped":"任务已停止","Stop":"停止","Start":"开始",
"Send":"发送","Cancel":"取消","Close":"关闭","Done":"完成",
"Save":"保存","Delete":"删除","Rename":"重命名","Move":"移动","Copy":"复制","Edit":"编辑",
"Retry":"重试","Open":"打开","Apply":"应用","Continue":"继续",
"File":"文件","Files":"文件","Attach file":"添加附件","Plugins · Skills · MCP":"插件 · 技能 · MCP",
"Terminal":"终端","Recovery copies":"恢复备份","Changes":"更改记录","Review changes":"审查更改",
"Theme":"主题","System":"跟随系统","Light":"浅色","Dark":"深色",
"Language":"语言","Follow device language":"跟随设备语言",
"App updates":"应用更新","Check for updates":"检查更新","Install update":"安装更新",
"Download":"下载","Download complete":"下载完成","Cancel / Close":"取消／关闭",
"Phone controls":"手机控制","Phone control":"手机控制","Phone control on":"手机控制已开启",
"Stop phone control":"停止手机控制","Open accessibility settings":"打开无障碍设置",
"Enable phone control":"启用手机控制","Phone control stopped":"手机控制已停止",
"Accessibility permission required":"需要无障碍权限",
"Accessibility connected · controls off":"无障碍已连接 · 控制关闭",
"Open floating chat":"打开悬浮聊天","Notifications":"通知",
"Voice settings":"语音设置","Live voice conversation":"实时语音对话",
"Voice conversation in progress":"正在语音对话","Listening":"正在聆听",
"Microphone muted":"麦克风已静音","Mute microphone":"静音","Unmute microphone":"取消静音",
"End voice conversation":"结束语音对话","Reconnect":"重新连接",
"Message Codex":"向 Codex 发送消息","Send input":"发送输入",
"Status check failed":"检查状态失败","No voices are available.":"没有可用语音",
"Send error report":"发送错误报告","Cancel / stop":"取消／停止",
"Permission":"权限","Allow":"允许","Deny":"拒绝","Ask":"每次询问",
"Refresh quota":"刷新额度","Basic":"基本","Low":"低","Medium":"中","High":"高",
"Select a model":"选择模型","Text size":"字体大小","Default · 100%":"默认 · 100%",
"Could not save text size.":"无法保存字体大小","All conversations":"所有对话",
"Codex · ChatGPT":"Codex · ChatGPT","Device file access":"访问设备文件",
"Check tools":"检查工具","Git":"Git","Python":"Python","Node.js":"Node.js",
"File attachment":"文件附件","Invite agents":"邀请智能体","Participants":"参与者",
"Sign-in input":"登录输入","Installer input":"安装程序输入",
"American English":"美式英语","Korean":"韩语","German":"德语","Other":"其他",
"The selected model does not support Fast mode.":"当前模型不支持快速模式。",
"Could not open the link. Please try again.":"无法打开链接，请重试。",
"Log in to view usage limits.":"登录后查看使用额度。",
"Usage limit information is unavailable.":"使用额度信息不可用。",
"This is the current account's Codex usage limit.":"这是当前账户的 Codex 使用额度。",
"Sending…":"正在发送…","Preparing error report.":"正在准备错误报告。",
"Undo":"撤销","Confirm":"确认","Expand":"展开","Collapse":"收起"
}
assets=root/"app/src/main/assets"
(assets/"translations-zh.json").write_text(json.dumps(zh,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
(assets/"web/translations-zh.js").write_text("/* UI-only explicit catalog; content is not translated. */\nwindow.MobileCodexChinese = "+json.dumps(zh,ensure_ascii=False,indent=2)+";\n",encoding="utf-8")
patch("app/src/main/assets/web/index.html",'<script src="locale.js" defer></script>','<script src="translations-zh.js" defer></script>\n  <script src="locale.js" defer></script>')
patch("app/src/main/java/dev/mobilecodex/app/MainActivity.java",
      'path.equals("/app.js") || path.equals("/ui-core.js") || path.equals("/translations.js") || path.equals("/locale.js")',
      'path.equals("/app.js") || path.equals("/ui-core.js") || path.equals("/translations.js") || path.equals("/translations-zh.js") || path.equals("/locale.js")')
loc=assets/"web/locale.js"
s=loc.read_text(encoding="utf-8")
s=s.replace('  const catalog = window.MobileCodexEnglish || {};','  const catalog = window.MobileCodexEnglish || {};\n  const chinese = window.MobileCodexChinese || {};',1)
s=s.replace("const language = () => (choice === 'system' ? deviceLanguage : choice).toLowerCase().startsWith('ko') ? 'ko' : 'en';",
"""const language = () => {
    const value = (choice === 'system' ? deviceLanguage : choice).toLowerCase();
    return value.startsWith('zh') ? 'zh' : value.startsWith('ko') ? 'ko' : 'en';
  };""",1)
s=s.replace("const value = language() === 'ko' ? key : (catalog[key] ?? key);",
"""const value = language() === 'ko' ? key :
      language() === 'zh' ? (chinese[catalog[key]] ?? catalog[key] ?? key) : (catalog[key] ?? key);""",1)
s=s.replace("['system','en','ko'].includes(value)","['system','en','ko','zh'].includes(value)",1)
loc.write_text(s,encoding="utf-8")
patch("app/src/main/assets/web/index.html",'<option value="ko">한국어</option></select>','<option value="ko">한국어</option><option value="zh">简体中文</option></select>')
# Include a trustworthy official balance entrypoint: never misrepresent usage % as cash credits.
patch("app/src/main/assets/web/index.html",'<div id="usage-limits" class="usage-limits" aria-live="polite"></div></section>',
'''<div id="usage-limits" class="usage-limits" aria-live="polite"></div>
<p class="muted" style="margin-top:10px">Codex 剩余额度显示在上方。已购 Credits 与 API 余额不同；如账户支持，请在 ChatGPT → 设置 → 使用情况查看实际 Credits 余额。</p>
<button id="open-official-credits" type="button" class="secondary-button" style="margin-top:10px">查看官方 Credits 余额</button>
</section>''')
patch("app/src/main/assets/web/app.js",
      "on('usage-refresh', loadUsage);",
      "on('usage-refresh', loadUsage);\n  on('open-official-credits', () => call('ui.externalBrowser', {url:'https://chatgpt.com/'}));")
# No third-party credit scraping / credentials exfiltration. Keep no Shizuku claims.
patch("app/src/main/assets/web/index.html",'<title>Mobile Codex</title>','<title>Codex 手机助手</title>')
print("PATCH OK: CN language/official Credits entrypoint, independent package, upstream phone controls preserved")
