package com.example.ui_mobile.api;

public class RegisterBody {
    public final String email;
    public final String password;
    public final String fullName;

    public RegisterBody(String email, String password, String fullName) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }
}
