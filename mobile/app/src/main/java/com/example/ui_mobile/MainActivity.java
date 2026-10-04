package com.example.ui_mobile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.ui_mobile.chat.ChatListActivity;
import com.example.ui_mobile.dating.DatingFragment;
import com.example.ui_mobile.feed.FeedFragment;
import com.example.ui_mobile.groups.GroupFragment;
import com.example.ui_mobile.notifications.NotificationsFragment;
import com.example.ui_mobile.profile.ProfileFragment;
import com.example.ui_mobile.reels.ReelsFragment;
import com.example.ui_mobile.ui.BaseActivity;
import com.example.ui_mobile.ui.Ui;

public class MainActivity extends BaseActivity {

    public static final int TAB_FEED = 0;
    public static final int TAB_REELS = 1;
    public static final int TAB_GROUPS = 2;
    public static final int TAB_DATING = 3;
    public static final int TAB_ALERTS = 4;
    public static final int TAB_PROFILE = 5;

    private static final String STATE_TAB = "tab";

    private final int[] navIds = {
            R.id.nav_feed, R.id.nav_reels, R.id.nav_groups,
            R.id.nav_dating, R.id.nav_alerts, R.id.nav_profile
    };

    private int currentTab = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        padForSystemBars(findViewById(R.id.top_bar), true, false);
        padForSystemBars(findViewById(R.id.bottom_bar), false, true);

        for (int i = 0; i < navIds.length; i++) {
            final int tab = i;
            findViewById(navIds[i]).setOnClickListener(v -> selectTab(tab));
        }

        findViewById(R.id.top_chat).setOnClickListener(v ->
                startActivity(new Intent(this, ChatListActivity.class)));
        findViewById(R.id.top_avatar).setOnClickListener(v -> selectTab(TAB_PROFILE));
        findViewById(R.id.top_search).setOnClickListener(v ->
                Ui.toast(this, "Tìm kiếm sẽ dùng search API của gateway"));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTab != TAB_FEED) {
                    selectTab(TAB_FEED);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        selectTab(savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, TAB_FEED) : TAB_FEED);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    public void selectTab(int tab) {
        if (tab == currentTab) return;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);

        // Keep every visited tab alive (hide/show) so scroll position and UI state survive switching.
        for (int i = 0; i < navIds.length; i++) {
            Fragment f = fm.findFragmentByTag(tag(i));
            if (f != null && i != tab) tx.hide(f);
        }
        Fragment target = fm.findFragmentByTag(tag(tab));
        if (target == null) {
            tx.add(R.id.fragment_container, createTab(tab), tag(tab));
        } else {
            tx.show(target);
        }
        tx.commit();

        currentTab = tab;
        renderNav();
        if (tab == TAB_ALERTS) findViewById(R.id.nav_alerts_dot).setVisibility(View.GONE);
    }

    private static String tag(int tab) {
        return "tab_" + tab;
    }

    private Fragment createTab(int tab) {
        switch (tab) {
            case TAB_REELS: return new ReelsFragment();
            case TAB_GROUPS: return new GroupFragment();
            case TAB_DATING: return new DatingFragment();
            case TAB_ALERTS: return new NotificationsFragment();
            case TAB_PROFILE: return new ProfileFragment();
            default: return new FeedFragment();
        }
    }

    private void renderNav() {
        int active = getColor(R.color.primary);
        int idle = getColor(R.color.text_secondary);
        for (int i = 0; i < navIds.length; i++) {
            ViewGroup item = findViewById(navIds[i]);
            int color = i == currentTab ? active : idle;
            View first = item.getChildAt(0);
            ImageView icon = first instanceof FrameLayout
                    ? (ImageView) ((FrameLayout) first).getChildAt(0)
                    : (ImageView) first;
            icon.setImageTintList(ColorStateList.valueOf(color));
            TextView label = (TextView) item.getChildAt(1);
            label.setTextColor(color);
            label.setTypeface(null, i == currentTab ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
        }
    }
}
