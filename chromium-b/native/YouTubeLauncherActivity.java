package app.tubeext.chromium;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Opens YouTube in the same Chromium-based browser process and profile.
 * The real Chrome activities, extensions and tab manager remain intact.
 */
public final class YouTubeLauncherActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Uri youtube = Uri.parse("https://m.youtube.com/");
        Intent browse = new Intent(Intent.ACTION_VIEW, youtube);
        browse.setPackage(getPackageName());
        browse.addCategory(Intent.CATEGORY_BROWSABLE);
        browse.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(browse);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_VIEW, youtube);
            fallback.setClassName(getPackageName(),
                "org.chromium.chrome.browser.ChromeTabbedActivity");
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(fallback);
            } catch (RuntimeException ex) {
                android.widget.Toast.makeText(this,
                    "TubeExt 浏览器内核无法打开 YouTube，请检查 APK 与内核兼容性",
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
}
