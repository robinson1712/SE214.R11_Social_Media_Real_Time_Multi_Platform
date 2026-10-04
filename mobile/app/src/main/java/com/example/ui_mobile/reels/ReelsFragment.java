package com.example.ui_mobile.reels;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.ui_mobile.R;
import com.example.ui_mobile.data.MockData;
import com.example.ui_mobile.ui.Ui;

/** Vertical reel pager. Each reel "plays" for 15 s (progress bar + spinning disc), then auto-advances. */
public class ReelsFragment extends Fragment {

    private static final long REEL_DURATION_MS = 15_000;

    private ViewPager2 pager;
    private ValueAnimator progressAnim;
    private ObjectAnimator discAnim;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reels, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        pager = view.findViewById(R.id.reels_pager);
        pager.setAdapter(new ReelAdapter(MockData.reels()));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                pager.post(() -> play(position));
            }
        });

        TextView following = view.findViewById(R.id.reels_tab_following);
        TextView forYou = view.findViewById(R.id.reels_tab_foryou);
        following.setOnClickListener(v -> selectFeedTab(following, forYou));
        forYou.setOnClickListener(v -> selectFeedTab(forYou, following));
        view.findViewById(R.id.reels_camera).setOnClickListener(v ->
                Ui.toast(requireContext(), "Quay reel sẽ dùng CameraX + media-service"));
        view.findViewById(R.id.reels_search).setOnClickListener(v ->
                Ui.toast(requireContext(), "Tìm kiếm reel"));
    }

    private void selectFeedTab(TextView selected, TextView other) {
        selected.setTextColor(0xFFFFFFFF);
        selected.setTypeface(null, android.graphics.Typeface.BOLD);
        other.setTextColor(0xB3FFFFFF);
        other.setTypeface(null, android.graphics.Typeface.NORMAL);
        pager.setCurrentItem(0, true);
    }

    private void play(int position) {
        stop();
        if (!isResumed() || isHidden()) return;
        RecyclerView rv = (RecyclerView) pager.getChildAt(0);
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
        if (!(vh instanceof ReelAdapter.Holder)) return;
        ReelAdapter.Holder holder = (ReelAdapter.Holder) vh;

        progressAnim = ValueAnimator.ofInt(0, 1000);
        progressAnim.setDuration(REEL_DURATION_MS);
        progressAnim.setInterpolator(new LinearInterpolator());
        progressAnim.addUpdateListener(a -> holder.progress.setProgressCompat((int) a.getAnimatedValue(), false));
        progressAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                RecyclerView.Adapter<?> adapter = pager.getAdapter();
                if (!cancelled && adapter != null) {
                    pager.setCurrentItem((position + 1) % adapter.getItemCount(), true);
                }
            }
        });
        progressAnim.start();

        discAnim = ObjectAnimator.ofFloat(holder.disc, View.ROTATION, 0f, 360f);
        discAnim.setDuration(4000);
        discAnim.setRepeatCount(ValueAnimator.INFINITE);
        discAnim.setInterpolator(new LinearInterpolator());
        discAnim.start();
    }

    private void stop() {
        if (progressAnim != null) progressAnim.cancel();
        if (discAnim != null) discAnim.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) pager.post(() -> play(pager.getCurrentItem()));
    }

    @Override
    public void onPause() {
        super.onPause();
        stop();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) stop();
        else pager.post(() -> play(pager.getCurrentItem()));
    }

    @Override
    public void onDestroyView() {
        stop();
        super.onDestroyView();
    }
}
