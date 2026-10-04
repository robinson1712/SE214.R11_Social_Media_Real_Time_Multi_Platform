package com.example.ui_mobile.api;

public class LoginBody {
    public final String email;
    public final String password;

    public LoginBody(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
