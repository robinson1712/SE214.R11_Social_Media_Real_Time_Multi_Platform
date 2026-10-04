package com.example.ui_mobile.data;

import androidx.annotation.DrawableRes;

public class DatingProfile {
    public final String name;
    public final int age;
    public final String job;
    public final String distance;
    public final int match;
    public final String song;
    @DrawableRes public final int scene;
    public final String[] tags;
    public final String prompt;
    public final String answer;
    public final String common;

    public DatingProfile(String name, int age, String job, String distance, int match, String song,
                         @DrawableRes int scene, String[] tags, String prompt, String answer,
                         String common) {
        this.name = name;
        this.age = age;
        this.job = job;
        this.distance = distance;
        this.match = match;
        this.song = song;
        this.scene = scene;
        this.tags = tags;
        this.prompt = prompt;
        this.answer = answer;
        this.common = common;
    }
}
