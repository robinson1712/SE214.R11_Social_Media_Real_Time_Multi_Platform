package com.example.ui_mobile.feed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ui_mobile.MainActivity;
import com.example.ui_mobile.R;
import com.example.ui_mobile.data.MockData;
import com.example.ui_mobile.data.Post;
import com.example.ui_mobile.fanpage.FanpageActivity;
import com.example.ui_mobile.ui.AvatarView;
import com.example.ui_mobile.ui.PostBinder;
import com.example.ui_mobile.ui.Ui;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

public class FeedFragment extends Fragment {

    private static final int POLL_A_BASE = 219;
    private static final int POLL_B_BASE = 123;

    private LinearLayout posts;
    private int pollChoice = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView) view.findViewById(R.id.feed_composer))
                .setText(MockData.ME_FIRST_NAME + " ơi, hôm nay của bạn thế nào?");
        view.findViewById(R.id.feed_composer).setOnClickListener(v -> openComposer());
        view.findViewById(R.id.feed_add_photo).setOnClickListener(v -> openComposer());
        view.findViewById(R.id.feed_add_moment).setOnClickListener(v -> openComposer());
        view.findViewById(R.id.feed_add_mood).setOnClickListener(v -> openComposer());

        bindStories(view.findViewById(R.id.feed_stories));

        posts = view.findViewById(R.id.feed_posts);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<Post> feed = MockData.feedPosts();
        PostBinder.addTo(posts, feed.get(0));

        View poll = inflater.inflate(R.layout.item_poll, posts, false);
        bindPoll(poll);
        posts.addView(poll);

        View suggestion = inflater.inflate(R.layout.item_page_suggestion, posts, false);
        View.OnClickListener openPage = v -> startActivity(new Intent(requireContext(), FanpageActivity.class));
        suggestion.setOnClickListener(openPage);
        suggestion.findViewById(R.id.suggestion_open).setOnClickListener(openPage);
        posts.addView(suggestion);

        for (int i = 1; i < feed.size(); i++) PostBinder.addTo(posts, feed.get(i));

        ImageView refresh = view.findViewById(R.id.feed_refresh);
        refresh.setOnClickListener(v -> {
            v.animate().rotationBy(360f).setDuration(600).start();
            Ui.toast(requireContext(), "Bạn đã xem hết những khoảnh khắc mới nhất 🌿");
        });
    }

    private void bindStories(LinearLayout row) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View create = inflater.inflate(R.layout.item_story_create, row, false);
        create.setOnClickListener(v -> openComposer());
        row.addView(create);

        for (String[] story : MockData.STORIES) {
            View card = inflater.inflate(R.layout.item_story, row, false);
            card.findViewById(R.id.story_bg).setBackgroundResource(MockData.sceneOf(story[1]));
            ((AvatarView) card.findViewById(R.id.story_avatar)).setName(story[0]);
            ((TextView) card.findViewById(R.id.story_name)).setText(story[0]);
            card.setOnClickListener(v -> Ui.toast(requireContext(), "Tin của " + story[0]));
            row.addView(card);
        }
    }

    private void bindPoll(View poll) {
        poll.findViewById(R.id.poll_group).setOnClickListener(v ->
                ((MainActivity) requireActivity()).selectTab(MainActivity.TAB_GROUPS));
        poll.findViewById(R.id.poll_option_a).setOnClickListener(v -> {
            pollChoice = 0;
            renderPoll(poll);
        });
        poll.findViewById(R.id.poll_option_b).setOnClickListener(v -> {
            pollChoice = 1;
            renderPoll(poll);
        });
        TextView like = poll.findViewById(R.id.poll_like);
        like.setOnClickListener(v -> {
            boolean liked = !like.isSelected();
            like.setSelected(liked);
            like.setText(liked ? "50" : "49");
            int color = requireContext().getColor(liked ? R.color.heart : R.color.text_secondary);
            like.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline, 0, 0, 0);
            like.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
            like.setTextColor(color);
        });
        poll.findViewById(R.id.poll_share).setOnClickListener(v ->
                Ui.share(requireContext(), "Cách bạn sạc lại năng lượng sau giờ làm việc? — bình chọn trên Aura"));
        pollChoice = 0;
        renderPoll(poll);
    }

    private void renderPoll(View poll) {
        int a = POLL_A_BASE + (pollChoice == 0 ? 1 : 0);
        int b = POLL_B_BASE + (pollChoice == 1 ? 1 : 0);
        int total = a + b;
        int pa = Math.round(a * 100f / total);
        int pb = 100 - pa;

        setWeights(poll, R.id.poll_fill_a, R.id.poll_rest_a, pa);
        setWeights(poll, R.id.poll_fill_b, R.id.poll_rest_b, pb);
        ((TextView) poll.findViewById(R.id.poll_percent_a)).setText(pa + "%");
        ((TextView) poll.findViewById(R.id.poll_percent_b)).setText(pb + "%");
        ((TextView) poll.findViewById(R.id.poll_votes)).setText(total + " phiếu bình chọn");

        renderCheck(poll.findViewById(R.id.poll_check_a), pollChoice == 0);
        renderCheck(poll.findViewById(R.id.poll_check_b), pollChoice == 1);
        int primary = requireContext().getColor(R.color.primary);
        int secondary = requireContext().getColor(R.color.text_secondary);
        ((TextView) poll.findViewById(R.id.poll_percent_a)).setTextColor(pollChoice == 0 ? primary : secondary);
        ((TextView) poll.findViewById(R.id.poll_percent_b)).setTextColor(pollChoice == 1 ? primary : secondary);
    }

    private static void setWeights(View root, int fillId, int restId, int percent) {
        View fill = root.findViewById(fillId);
        View rest = root.findViewById(restId);
        LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) fill.getLayoutParams();
        LinearLayout.LayoutParams rp = (LinearLayout.LayoutParams) rest.getLayoutParams();
        fp.weight = percent;
        rp.weight = 100 - percent;
        fill.setLayoutParams(fp);
        rest.setLayoutParams(rp);
    }

    private void renderCheck(ImageView check, boolean selected) {
        int pad = selected ? 0 : Ui.dp(requireContext(), 8);
        check.setPadding(pad, pad, pad, pad);
        check.setImageResource(selected ? R.drawable.ic_verified : R.drawable.bg_dot_primary);
        check.setColorFilter(requireContext().getColor(selected ? R.color.primary : R.color.text_tertiary));
    }

    private void openComposer() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_compose, null, false);
        EditText input = content.findViewById(R.id.compose_input);
        content.findViewById(R.id.compose_submit).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                input.setError("Hãy viết đôi dòng nhé");
                return;
            }
            // TODO: POST to post-service; the new post will then arrive through the feed stream.
            Post post = new Post(MockData.ME).verified().time("Vừa xong").isPublic()
                    .content(text).likes(0, "đồng điệu").statsRight("Chưa có bình luận");
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_post, posts, false);
            PostBinder.bind(card, post);
            posts.addView(card, 0);
            sheet.dismiss();
            Ui.toast(requireContext(), "Đã chia sẻ khoảnh khắc của bạn");
        });
        sheet.setContentView(content);
        sheet.show();
    }
}
