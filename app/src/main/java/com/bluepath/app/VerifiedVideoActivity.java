package com.bluepath.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import com.bluepath.app.network.ApiModels;
import com.bluepath.app.repository.BluePathRepository;
import com.bluepath.app.storage.UserStore;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Records distinct, contiguous playback intervals and asks the server to verify coverage. */
public class VerifiedVideoActivity extends AppCompatActivity {
    public static final String EXTRA_CONTENT_ID = "content_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_MINUTES = "minutes";

    // Real https origin served by WebViewAssetLoader so YouTube receives a valid embedder Referer.
    private static final String PLAYER_HOST = "appassets.androidplatform.net";

    private static final Pattern YOUTUBE_ID = Pattern.compile(
            "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?v=|embed/|shorts/))([A-Za-z0-9_-]{6,})");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UserStore store;
    private BluePathRepository repository;
    private String contentId;
    private TextView status;
    private ProgressBar progress;
    private volatile boolean verificationRequested;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#06223F"));
        getWindow().setNavigationBarColor(Color.parseColor("#06223F"));

        contentId = value(EXTRA_CONTENT_ID);
        String title = value(EXTRA_TITLE);
        String url = value(EXTRA_URL);
        String videoId = extractYouTubeId(url);
        store = new UserStore(this);
        repository = new BluePathRepository(this);

        if (contentId.isEmpty() || videoId.isEmpty()) {
            Toast.makeText(this, "단일 YouTube 영상만 검증 재생할 수 있습니다. 재생목록이나 외부 영상은 학습 증거로 인정되지 않습니다.", Toast.LENGTH_LONG).show();
            if (!url.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#06223F"));

        TextView heading = new TextView(this);
        heading.setText(title.isEmpty() ? "BluePath 검증 영상 학습" : title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(18);
        heading.setPadding(dp(16), dp(14), dp(16), dp(8));
        root.addView(heading);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#C9FFFF"));
        status.setTextSize(13);
        status.setPadding(dp(16), 0, dp(16), dp(10));
        updateStatus();
        root.addView(status);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(store.getVideoProgressPercent(contentId));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));

        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(true);
        webView.addJavascriptInterface(new PlaybackBridge(), "BluePathAndroid");
        webView.setWebChromeClient(new WebChromeClient());
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (target != null && target.getHost() != null
                        && !target.getHost().contains(PLAYER_HOST)
                        && !target.getHost().contains("youtube.com")
                        && !target.getHost().contains("googlevideo.com")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, target));
                    return true;
                }
                return false;
            }
        });
        webView.loadUrl("https://" + PLAYER_HOST + "/assets/player.html?v=" + videoId);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView guide = new TextView(this);
        guide.setText("탐색으로 건너뛴 구간과 중복 재생은 인정되지 않습니다. 서로 다른 구간을 70% 이상 실제 재생한 뒤 서버 검증이 완료되어야 학습 완료로 기록됩니다.");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(12);
        guide.setPadding(dp(16), dp(10), dp(16), dp(14));
        root.addView(guide);
        setContentView(root);
    }

    private String value(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private String extractYouTubeId(String url) {
        if (url == null || url.contains("/playlist?") || (!url.contains("v=") && url.contains("list="))) return "";
        Matcher matcher = YOUTUBE_ID.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private final class PlaybackBridge {
        @JavascriptInterface
        public void onInterval(double startSeconds, double endSeconds, int durationSeconds) {
            store.recordVideoInterval(contentId, startSeconds, endSeconds, durationSeconds);
            runOnUiThread(() -> {
                updateStatus();
                maybeVerify(durationSeconds);
            });
        }
    }

    private void maybeVerify(int durationSeconds) {
        int coverage = store.getVideoProgressPercent(contentId);
        if (coverage < 70 || store.getVideoWatchSeconds(contentId) < 45 || verificationRequested) return;
        if (!store.hasCloudSession() || !repository.isCloudConfigured()) {
            status.setText(status.getText() + " · 로그인 후 서버 검증 필요");
            return;
        }
        verificationRequested = true;
        status.setText(status.getText() + " · 서버 검증 중");
        executor.execute(() -> {
            try {
                ApiModels.VideoEvidenceResponse result = repository.verifyVideo(
                        contentId, durationSeconds, store.getVideoIntervalsForVerification(contentId));
                store.markVideoServerVerified(contentId, result.verified, result.watchedSeconds, result.coveragePercent);
                runOnUiThread(() -> {
                    updateStatus();
                    Toast.makeText(this, result.message == null ? "영상 학습 검증을 완료했습니다." : result.message, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                verificationRequested = false;
                runOnUiThread(() -> {
                    updateStatus();
                    Toast.makeText(this, "서버 검증 실패: " + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateStatus() {
        int watchedSeconds = store.getVideoWatchSeconds(contentId);
        int coverage = store.getVideoProgressPercent(contentId);
        if (progress != null) progress.setProgress(coverage);
        if (status != null) {
            String verified = store.hasVerifiedVideoCompletion(contentId, 0) ? " · 서버 검증 완료" : "";
            status.setText(String.format(Locale.KOREA, "서로 다른 시청 구간 %d분 %02d초 · 실제 커버리지 %d%%%s",
                    watchedSeconds / 60, watchedSeconds % 60, coverage, verified));
        }
    }

    private String safeMessage(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value.trim();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
