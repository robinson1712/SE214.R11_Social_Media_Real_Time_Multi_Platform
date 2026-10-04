package com.example.ui_mobile.data;

import androidx.annotation.DrawableRes;

public class Reel {
    public final String author;
    public final String handle;
    public final String subtitle;
    public final String caption;
    public final String music;
    @DrawableRes public final int scene;
    public int likes;
    public final int comments;
    public final int shares;
    public boolean liked;
    public boolean saved;
    public boolean followed;

    public Reel(String author, String handle, String subtitle, String caption, String music,
                @DrawableRes int scene, int likes, int comments, int shares) {
        this.author = author;
        this.handle = handle;
        this.subtitle = subtitle;
        this.caption = caption;
        this.music = music;
        this.scene = scene;
        this.likes = likes;
        this.comments = comments;
        this.shares = shares;
    }
}
