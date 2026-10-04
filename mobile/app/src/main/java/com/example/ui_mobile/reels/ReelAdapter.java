package com.example.ui_mobile.reels;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ui_mobile.R;
import com.example.ui_mobile.data.Reel;
import com.example.ui_mobile.ui.Anim;
import com.example.ui_mobile.ui.AvatarView;
import com.example.ui_mobile.ui.Ui;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

public class ReelAdapter extends RecyclerView.Adapter<ReelAdapter.Holder> {

    private final List<Reel> reels;

    public ReelAdapter(List<Reel> reels) {
        this.reels = reels;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reel, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        h.bind(reels.get(position));
    }

    @Override
    public int getItemCount() {
        return reels.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View bg;
        final ImageView like;
        final ImageView save;
        final ImageView bigHeart;
        final TextView likeCount;
        final TextView follow;
        final View disc;
        final LinearProgressIndicator progress;
        Reel reel;

        @SuppressLint("ClickableViewAccessibility")
        Holder(@NonNull View v) {
            super(v);
            bg = v.findViewById(R.id.reel_bg);
            like = v.findViewById(R.id.reel_like);
            save = v.findViewById(R.id.reel_save);
            bigHeart = v.findViewById(R.id.reel_big_heart);
            likeCount = v.findViewById(R.id.reel_like_count);
            follow = v.findViewById(R.id.reel_follow);
            disc = v.findViewById(R.id.reel_disc);
            progress = v.findViewById(R.id.reel_progress);

            Context c = v.getContext();
            like.setOnClickListener(x -> toggleLike());
            save.setOnClickListener(x -> {
                reel.saved = !reel.saved;
                renderSave();
                Ui.toast(c, reel.saved ? "Đã lưu reel" : "Đã bỏ lưu");
            });
            follow.setOnClickListener(x -> {
                reel.followed = !reel.followed;
                renderFollow();
                Anim.pop(follow);
            });
            v.findViewById(R.id.reel_comment).setOnClickListener(x ->
                    Ui.toast(c, "Bình luận reel sẽ kết nối với comment-service"));
            v.findViewById(R.id.reel_share).setOnClickListener(x ->
                    Ui.share(c, reel.handle + " trên Aura Reels: " + reel.caption));

            GestureDetector detector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(@NonNull MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onDoubleTap(@NonNull MotionEvent e) {
                    if (!reel.liked) toggleLike();
                    bigHeart.animate().cancel();
                    bigHeart.setAlpha(1f);
                    bigHeart.setScaleX(0.4f);
                    bigHeart.setScaleY(0.4f);
                    bigHeart.animate().scaleX(1.1f).scaleY(1.1f).setDuration(220)
                            .withEndAction(() -> bigHeart.animate().alpha(0f).setStartDelay(250)
                                    .setDuration(300).start())
                            .start();
                    return true;
                }
            });
            bg.setOnTouchListener((view, e) -> detector.onTouchEvent(e));
        }

        void bind(Reel r) {
            reel = r;
            bg.setBackgroundResource(r.scene);
            ((AvatarView) itemView.findViewById(R.id.reel_avatar)).setName(r.author);
            ((TextView) itemView.findViewById(R.id.reel_handle)).setText(r.handle);
            ((TextView) itemView.findViewById(R.id.reel_subtitle)).setText("● " + r.subtitle);
            ((TextView) itemView.findViewById(R.id.reel_caption)).setText(r.caption);
            ((TextView) itemView.findViewById(R.id.reel_music)).setText(r.music);
            ((TextView) itemView.findViewById(R.id.reel_comment_count)).setText(Ui.compact(r.comments));
            ((TextView) itemView.findViewById(R.id.reel_share_count)).setText(Ui.compact(r.shares));
            progress.setProgressCompat(0, false);
            renderLike();
            renderSave();
            renderFollow();
        }

        private void toggleLike() {
            reel.liked = !reel.liked;
            reel.likes += reel.liked ? 1 : -1;
            renderLike();
            Anim.pop(like);
        }

        private void renderLike() {
            like.setImageResource(reel.liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
            like.setColorFilter(reel.liked ? itemView.getContext().getColor(R.color.heart) : 0xFFFFFFFF);
            likeCount.setText(Ui.compact(reel.likes));
        }

        private void renderSave() {
            save.setImageResource(reel.saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        }

        private void renderFollow() {
            follow.setText(reel.followed ? "Đang theo dõi" : "Theo dõi");
            follow.setBackgroundResource(reel.followed ? R.drawable.bg_pill_glass : R.drawable.bg_pill_primary);
        }
    }
}
