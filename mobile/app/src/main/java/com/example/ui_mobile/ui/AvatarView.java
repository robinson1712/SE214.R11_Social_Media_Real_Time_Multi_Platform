package com.example.ui_mobile.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.example.ui_mobile.R;

/**
 * Placeholder avatar: takes a person's full name via android:text / setName() and renders
 * the initial of the last word (Vietnamese given name) on a pastel colour derived from the name.
 * Swap for an ImageView + image loader once the media-service returns real avatar URLs.
 */
public class AvatarView extends AppCompatTextView {

    private static final int[] PALETTE = {
            0xFFB3A6EC, 0xFFF0AE9F, 0xFF8FCBBE, 0xFFF2C77E,
            0xFF9DBBEE, 0xFFE59EC3, 0xFFA9CF8C, 0xFFC5A68A
    };

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean online;
    private boolean ring;
    private float corner;
    private String name = "";

    public AvatarView(@NonNull Context context) {
        this(context, null);
    }

    public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AvatarView);
        online = a.getBoolean(R.styleable.AvatarView_avatarOnline, false);
        ring = a.getBoolean(R.styleable.AvatarView_avatarRing, false);
        corner = a.getDimension(R.styleable.AvatarView_avatarCorner, 0f);
        a.recycle();

        setGravity(Gravity.CENTER);
        setTextColor(Color.WHITE);
        setTypeface(Typeface.DEFAULT_BOLD);
        setIncludeFontPadding(false);
        setMaxLines(1);
        setName(getText());
    }

    public void setName(@Nullable CharSequence fullName) {
        name = fullName == null ? "" : fullName.toString().trim();
        super.setText(initialOf(name));

        GradientDrawable bg = new GradientDrawable();
        if (corner > 0) {
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(corner);
        } else {
            bg.setShape(GradientDrawable.OVAL);
        }
        bg.setColor(PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)]);
        if (ring) bg.setStroke(dp(3), Color.WHITE);
        setBackground(bg);
    }

    public String getName() {
        return name;
    }

    public void setRing(boolean ring) {
        this.ring = ring;
        setName(name);
    }

    public void setOnline(boolean online) {
        this.online = online;
        invalidate();
    }

    private static String initialOf(String fullName) {
        if (fullName.isEmpty()) return "";
        String[] parts = fullName.split("\\s+");
        String last = parts[parts.length - 1];
        int cp = last.codePointAt(0);
        return new String(Character.toChars(cp)).toUpperCase();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(w, h) * 0.4f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!online) return;
        float size = Math.min(getWidth(), getHeight());
        float r = Math.max(dp(5), size * 0.13f);
        float cx = getWidth() - r - size * 0.02f;
        float cy = getHeight() - r - size * 0.02f;
        dotPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, r + dp(2), dotPaint);
        dotPaint.setColor(getContext().getColor(R.color.online));
        canvas.drawCircle(cx, cy, r, dotPaint);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
