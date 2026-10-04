package com.example.ui_mobile.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.example.ui_mobile.R;
import com.example.ui_mobile.data.Post;
import com.example.ui_mobile.fanpage.FanpageActivity;

/** Inflates and binds {@code item_post} for every screen that shows posts. */
public final class PostBinder {

    private PostBinder() {
    }

    public static View addTo(ViewGroup container, Post post) {
        View v = LayoutInflater.from(container.getContext())
                .inflate(R.layout.item_post, container, false);
        bind(v, post);
        container.addView(v);
        return v;
    }

    public static void bind(View v, Post p) {
        Context c = v.getContext();

        TextView pinned = v.findViewById(R.id.post_pinned);
        pinned.setVisibility(p.pinnedLabel != null ? View.VISIBLE : View.GONE);
        pinned.setText(p.pinnedLabel);

        AvatarView avatar = v.findViewById(R.id.post_avatar);
        avatar.setName(p.author);
        ((TextView) v.findViewById(R.id.post_author)).setText(p.author);
        v.findViewById(R.id.post_verified).setVisibility(p.verified ? View.VISIBLE : View.GONE);

        TextView badge = v.findViewById(R.id.post_badge);
        badge.setVisibility(p.badge != null ? View.VISIBLE : View.GONE);
        badge.setText(p.badge);

        ((TextView) v.findViewById(R.id.post_meta)).setText(meta(c, p));
        ((TextView) v.findViewById(R.id.post_content)).setText(p.content);

        View media = v.findViewById(R.id.post_media);
        if (p.scene != 0) {
            media.setVisibility(View.VISIBLE);
            v.findViewById(R.id.post_media_bg).setBackgroundResource(p.scene);
            setOptional(v.findViewById(R.id.post_media_caption), p.mediaCaption);
            setOptional(v.findViewById(R.id.post_media_counter), p.mediaCounter);
        } else {
            media.setVisibility(View.GONE);
        }

        ((TextView) v.findViewById(R.id.post_stats_right)).setText(p.statsRight);
        renderLike(v, p);
        renderSave(v, p);

        View commentBox = v.findViewById(R.id.post_comment_box);
        if (p.commentAuthor != null) {
            commentBox.setVisibility(View.VISIBLE);
            ((AvatarView) v.findViewById(R.id.post_comment_avatar)).setName(p.commentAuthor);
            ((TextView) v.findViewById(R.id.post_comment_author)).setText(p.commentAuthor);
            ((TextView) v.findViewById(R.id.post_comment_time)).setText(p.commentTime);
            ((TextView) v.findViewById(R.id.post_comment_text)).setText(p.commentText);
        } else {
            commentBox.setVisibility(View.GONE);
        }

        v.findViewById(R.id.post_like).setOnClickListener(x -> {
            p.liked = !p.liked;
            p.likes += p.liked ? 1 : -1;
            renderLike(v, p);
            if (p.liked) Anim.pop(x);
        });
        v.findViewById(R.id.post_save).setOnClickListener(x -> {
            p.saved = !p.saved;
            renderSave(v, p);
            Ui.toast(c, p.saved ? "Đã lưu vào bộ sưu tập" : "Đã bỏ lưu");
        });
        v.findViewById(R.id.post_comment).setOnClickListener(x ->
                Ui.toast(c, "Thảo luận sẽ kết nối với comment-service"));
        v.findViewById(R.id.post_share).setOnClickListener(x ->
                Ui.share(c, p.author + " trên Aura: " + p.content));
        v.findViewById(R.id.post_more).setOnClickListener(x -> showMenu(v, x));

        if (p.isPage) {
            View.OnClickListener openPage = x -> c.startActivity(new Intent(c, FanpageActivity.class));
            avatar.setOnClickListener(openPage);
            v.findViewById(R.id.post_author_block).setOnClickListener(openPage);
        }
    }

    private static CharSequence meta(Context c, Post p) {
        SpannableStringBuilder sb = new SpannableStringBuilder(p.time == null ? "" : p.time);
        if (p.place != null) {
            sb.append("  •  ");
            appendIcon(c, sb, R.drawable.ic_place, R.color.primary);
            int start = sb.length();
            sb.append(p.place);
            sb.setSpan(new ForegroundColorSpan(c.getColor(R.color.primary)), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (p.isPublic) {
            sb.append("  •  ");
            appendIcon(c, sb, R.drawable.ic_public, R.color.text_tertiary);
        }
        return sb;
    }

    private static void appendIcon(Context c, SpannableStringBuilder sb, int icon, int color) {
        Drawable d = c.getDrawable(icon);
        if (d == null) return;
        d = d.mutate();
        d.setTint(c.getColor(color));
        int size = Ui.dp(c, 12);
        d.setBounds(0, 0, size, size);
        int start = sb.length();
        sb.append(" ");
        sb.setSpan(new ImageSpan(d, ImageSpan.ALIGN_CENTER), start, start + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" ");
    }

    private static void renderLike(View v, Post p) {
        Context c = v.getContext();
        TextView like = v.findViewById(R.id.post_like);
        int color = c.getColor(p.liked ? R.color.heart : R.color.text_secondary);
        like.setCompoundDrawablesRelativeWithIntrinsicBounds(
                p.liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline, 0, 0, 0);
        like.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
        like.setTextColor(color);
        ((TextView) v.findViewById(R.id.post_likes)).setText(Ui.compact(p.likes) + " " + p.likeWord);
    }

    private static void renderSave(View v, Post p) {
        ImageView save = v.findViewById(R.id.post_save);
        save.setImageResource(p.saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        save.setColorFilter(v.getContext().getColor(p.saved ? R.color.primary : R.color.text_secondary));
    }

    private static void setOptional(TextView tv, String text) {
        tv.setVisibility(text != null ? View.VISIBLE : View.GONE);
        tv.setText(text);
    }

    private static void showMenu(View card, View anchor) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(0, 1, 0, "Ẩn bài viết");
        menu.getMenu().add(0, 2, 1, "Tắt thông báo bài viết");
        menu.getMenu().add(0, 3, 2, "Báo cáo nội dung");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                card.animate().alpha(0f).setDuration(200)
                        .withEndAction(() -> card.setVisibility(View.GONE)).start();
            } else {
                Ui.toast(anchor.getContext(), item.getTitle());
            }
            return true;
        });
        menu.show();
    }
}
