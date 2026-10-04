package com.example.ui_mobile.ui;

import android.view.View;
import android.view.animation.OvershootInterpolator;

public final class Anim {

    private Anim() {
    }

    /** Quick scale bounce used for like / follow feedback. */
    public static void pop(View v) {
        v.animate().cancel();
        v.setScaleX(0.8f);
        v.setScaleY(0.8f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(250)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }
}
