package com.example.ui_mobile.data;

import androidx.annotation.DrawableRes;

/** A feed / group / fanpage / profile post. Fluent setters keep the mock data readable. */
public class Post {
    public final String author;
    public boolean verified;
    public boolean isPage;
    public String badge;
    public String time;
    public String place;
    public boolean isPublic;
    public String pinnedLabel;

    public CharSequence content;
    @DrawableRes public int scene;
    public String mediaCaption;
    public String mediaCounter;

    public int likes;
    public String likeWord = "đồng điệu";
    public String statsRight;
    public boolean liked;
    public boolean saved;

    public String commentAuthor;
    public String commentText;
    public String commentTime;

    public Post(String author) {
        this.author = author;
    }

    public Post verified() { verified = true; return this; }
    public Post page() { isPage = true; verified = true; return this; }
    public Post badge(String v) { badge = v; return this; }
    public Post time(String v) { time = v; return this; }
    public Post place(String v) { place = v; return this; }
    public Post isPublic() { isPublic = true; return this; }
    public Post pinned(String label) { pinnedLabel = label; return this; }
    public Post content(CharSequence v) { content = v; return this; }
    public Post media(@DrawableRes int res) { scene = res; return this; }
    public Post mediaCaption(String v) { mediaCaption = v; return this; }
    public Post mediaCounter(String v) { mediaCounter = v; return this; }
    public Post likes(int n, String word) { likes = n; likeWord = word; return this; }
    public Post statsRight(String v) { statsRight = v; return this; }
    public Post topComment(String author, String text, String time) {
        commentAuthor = author;
        commentText = text;
        commentTime = time;
        return this;
    }
}
