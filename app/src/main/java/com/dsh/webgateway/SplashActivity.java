package com.dsh.webgateway;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int bg = getColor(R.color.og_bg);
        int surface = getColor(R.color.og_surface);
        int primary = getColor(R.color.og_primary);
        int primary2 = getColor(R.color.og_primary_2);
        int text = getColor(R.color.og_text);
        int muted = getColor(R.color.og_muted);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        LinearLayout center = UiKit.vertical(this);
        center.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout logoBox = new FrameLayout(this);
        logoBox.setBackground(UiKit.gradient(
                primary,
                primary2,
                UiKit.dp(this, 30)));
        logoBox.setElevation(UiKit.dp(this, 10));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_omnigate_mark);
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logoBox.addView(
                mark,
                new FrameLayout.LayoutParams(
                        UiKit.dp(this, 58),
                        UiKit.dp(this, 58),
                        Gravity.CENTER));

        center.addView(
                logoBox,
                new LinearLayout.LayoutParams(
                        UiKit.dp(this, 94),
                        UiKit.dp(this, 94)));

        TextView brand = UiKit.text(
                this,
                "OmniGate",
                30f,
                text,
                true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.025f);
        LinearLayout.LayoutParams brandLp = UiKit.matchWrap();
        brandLp.topMargin = UiKit.dp(this, 24);
        center.addView(brand, brandLp);

        TextView tagline = UiKit.text(
                this,
                "Web AI Gateway",
                14f,
                muted,
                false);
        tagline.setGravity(Gravity.CENTER);
        tagline.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams tagLp = UiKit.matchWrap();
        tagLp.topMargin = UiKit.dp(this, 8);
        center.addView(tagline, tagLp);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        centerLp.leftMargin = UiKit.dp(this, 28);
        centerLp.rightMargin = UiKit.dp(this, 28);
        root.addView(center, centerLp);

        TextView providers = UiKit.text(
                this,
                "DeepSeek · ChatGPT · Grok · GLM",
                12.5f,
                muted,
                false);
        providers.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams providersLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        providersLp.leftMargin = UiKit.dp(this, 24);
        providersLp.rightMargin = UiKit.dp(this, 24);
        providersLp.bottomMargin = UiKit.dp(this, 42);
        root.addView(providers, providersLp);

        setContentView(root);

        logoBox.setScaleX(0.82f);
        logoBox.setScaleY(0.82f);
        logoBox.setAlpha(0f);
        brand.setAlpha(0f);
        brand.setTranslationY(UiKit.dp(this, 10));
        tagline.setAlpha(0f);
        providers.setAlpha(0f);

        logoBox.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(420)
                .start();

        brand.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(180)
                .setDuration(360)
                .start();

        tagline.animate()
                .alpha(1f)
                .setStartDelay(320)
                .setDuration(320)
                .start();

        providers.animate()
                .alpha(1f)
                .setStartDelay(460)
                .setDuration(320)
                .start();

        root.postDelayed(() -> {
            root.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> {
                        Intent intent = new Intent(this, MainActivity.class);
                        startActivity(intent);
                        overridePendingTransition(0, 0);
                        finish();
                    })
                    .start();
        }, 1050);
    }
}
