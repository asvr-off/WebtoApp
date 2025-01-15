package com.webtoapp.minimal.app;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.animation.ObjectAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import com.google.android.material.snackbar.Snackbar;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.TextView;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    private WebView mywebView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private FrameLayout customViewContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View customView;
    private final WeakReference<Handler> handlerRef = new WeakReference<>(new Handler(Looper.getMainLooper()));
    private static final String TAG = "MainActivity";
    private GestureDetector gestureDetector;
    private Runnable reloadCheck;
    private boolean isCustomErrorShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupGestureDetector();
        setupWebView();
        setupSwipeRefresh();
        hideSystemUI();
        setupSystemUIListener();
    }

    private void initializeViews() {
        mywebView = findViewById(R.id.webview);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        customViewContainer = findViewById(R.id.customViewContainer);

        mywebView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) &&
                        Math.abs(diffX) > 100 &&
                        Math.abs(velocityX) > 100) {

                    if (diffX > 0 && mywebView.canGoBack()) {
                        mywebView.goBack();
                        Log.d(TAG, "Swipe detected: Navigating back in WebView");
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void setupWebView() {
        mywebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!isCustomErrorShowing) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "Page finished loading: " + url);
                    clearReloadCheck();
                    hideErrorScreen();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    isCustomErrorShowing = true;
                    String errorMessage;

                    switch (error.getErrorCode()) {
                        case ERROR_HOST_LOOKUP:
                        case ERROR_CONNECT:
                        case ERROR_TIMEOUT:
                            errorMessage = getString(R.string.no_internet_message);
                            break;
                        default:
                            errorMessage = getString(R.string.error_code_message, error.getErrorCode());
                    }

                    showErrorScreen(errorMessage);
                    view.stopLoading();
                    Log.d(TAG, "WebView Error: " + error.getDescription());
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString().toLowerCase();
                if (url.endsWith(".pdf")) {
                    return handlePdfUrl(request.getUrl().toString());
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        setupWebChromeClient();
        setupWebViewSettings();
    }

    private WebResourceResponse handlePdfUrl(String url) {
        try {
            String pdfViewer = "https://docs.google.com/gview?embedded=true&url=" + Uri.encode(url);
            String html = "<iframe src='" + pdfViewer + "' width='100%' height='100%' style='border: none;'></iframe>";
            return new WebResourceResponse("text/html", "UTF-8",
                    new java.io.ByteArrayInputStream(html.getBytes()));
        } catch (Exception e) {
            Log.e(TAG, "PDF handling error", e);
            return null;
        }
    }

    private void setupWebChromeClient() {
        mywebView.setWebChromeClient(new WebChromeClient() {
            private int lastProgress = 0;
            private ObjectAnimator progressAnimator;

            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progressBar.getVisibility() != View.VISIBLE) {
                    progressBar.setAlpha(0f);
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.animate().alpha(1f).setDuration(200).start();
                }

                if (progressAnimator != null) {
                    progressAnimator.cancel();
                }

                progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", lastProgress, progress);
                progressAnimator.setDuration(500);
                progressAnimator.setInterpolator(new DecelerateInterpolator());
                progressAnimator.start();

                lastProgress = progress;

                if (progress == 100) {
                    progressBar.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .setStartDelay(200)
                            .withEndAction(() -> {
                                progressBar.setVisibility(View.GONE);
                                lastProgress = 0;
                            })
                            .start();
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewContainer.addView(view);
                customViewContainer.setVisibility(View.VISIBLE);
                customViewCallback = callback;
                mywebView.setVisibility(View.GONE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                customViewContainer.removeView(customView);
                customView = null;
                customViewContainer.setVisibility(View.GONE);
                mywebView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
            }
        });
    }

    private void setupWebViewSettings() {
        WebSettings webSettings = mywebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        mywebView.loadUrl("https://www.speedhunters.com/");
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> mywebView.getScrollY() > 0);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (mywebView.getScrollY() == 0) {
                Log.d(TAG, "Refreshing the page");
                monitorReload();
                mywebView.reload();
                hideErrorScreen();
            }
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void showErrorScreen(String errorMessage) {
        mywebView.setVisibility(View.GONE);
        View errorScreen = findViewById(R.id.error_screen);
        if (errorScreen != null) {
            errorScreen.setVisibility(View.VISIBLE);
            TextView errorText = errorScreen.findViewById(R.id.error_text);
            if (errorText != null) {
                errorText.setText(errorMessage);
            }
        }
    }

    private void hideErrorScreen() {
        View errorScreen = findViewById(R.id.error_screen);
        if (errorScreen != null) {
            errorScreen.setVisibility(View.GONE);
        }
        mywebView.setVisibility(View.VISIBLE);
        isCustomErrorShowing = false;
    }

    private void monitorReload() {
        Handler handler = handlerRef.get();
        if (handler != null) {
            reloadCheck = () -> {
                if (mywebView.getProgress() < 100) {
                    Log.d(TAG, "Page is taking longer to load");
                    showReloadSnackbar();
                }
            };
            handler.postDelayed(reloadCheck, 10000);
        }
    }

    private void clearReloadCheck() {
        Handler handler = handlerRef.get();
        if (handler != null && reloadCheck != null) {
            handler.removeCallbacks(reloadCheck);
            reloadCheck = null;
        }
    }

    private void showReloadSnackbar() {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.main),
                        getString(R.string.page_load_timeout), Snackbar.LENGTH_LONG)
                .setAction("Retry", v -> mywebView.reload())
                .setBackgroundTint(Color.DKGRAY)
                .setActionTextColor(Color.parseColor("#00FF00"));
        snackbar.show();
        Log.d(TAG, "Showing reload Snackbar");
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        Log.d(TAG, "System UI hidden");
    }

    private void setupSystemUIListener() {
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                decorView.postDelayed(this::hideSystemUI, 2000);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mywebView.canGoBack()) {
            mywebView.goBack();
            Log.d(TAG, "Navigating back in WebView");
        } else {
            super.onBackPressed();
            Log.d(TAG, "Exiting the app");
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (mywebView != null) {
            mywebView.destroy();
            mywebView = null;
        }
        clearReloadCheck();
        super.onDestroy();
    }
}