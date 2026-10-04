package com.example.ui_mobile.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Edge-to-edge with dark system-bar icons (the whole app uses a light surface). */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
    }

    /**
     * Adds system-bar (and keyboard, for the bottom edge) insets on top of the view's own padding.
     */
    public static void padForSystemBars(View view, boolean top, boolean bottom) {
        final int l = view.getPaddingLeft();
        final int t = view.getPaddingTop();
        final int r = view.getPaddingRight();
        final int b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(l + bars.left,
                    t + (top ? bars.top : 0),
                    r + bars.right,
                    b + (bottom ? Math.max(bars.bottom, ime.bottom) : 0));
            return insets;
        });
    }
}
