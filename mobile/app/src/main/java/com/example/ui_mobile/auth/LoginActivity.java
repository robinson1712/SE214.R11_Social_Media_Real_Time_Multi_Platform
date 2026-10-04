package com.example.ui_mobile.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import com.example.ui_mobile.MainActivity;
import com.example.ui_mobile.R;
import com.example.ui_mobile.api.ApiClient;
import com.example.ui_mobile.api.ApiResponse;
import com.example.ui_mobile.api.AuthData;
import com.example.ui_mobile.api.LoginBody;
import com.example.ui_mobile.api.SessionStore;
import com.example.ui_mobile.ui.BaseActivity;
import com.example.ui_mobile.ui.Ui;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends BaseActivity {

    private boolean passwordVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        padForSystemBars(findViewById(R.id.login_root), true, true);

        EditText email = findViewById(R.id.login_email);
        EditText password = findViewById(R.id.login_password);
        ImageView toggle = findViewById(R.id.login_toggle_password);

        toggle.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            togglePassword(password, toggle, passwordVisible);
        });

        findViewById(R.id.login_submit).setOnClickListener(v -> {
            String id = email.getText().toString().trim();
            String pw = password.getText().toString();
            if (id.isEmpty()) {
                email.setError("Vui lòng nhập email hoặc số điện thoại");
                email.requestFocus();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(id).matches() && !Patterns.PHONE.matcher(id).matches()) {
                email.setError("Email hoặc số điện thoại chưa hợp lệ");
                email.requestFocus();
                return;
            }
            if (pw.length() < 6) {
                password.setError("Mật khẩu tối thiểu 6 ký tự");
                password.requestFocus();
                return;
            }
            submitLogin(id, pw, v);
        });

        findViewById(R.id.login_forgot).setOnClickListener(v ->
                Ui.toast(this, "Liên kết đặt lại mật khẩu sẽ được gửi qua email"));
        findViewById(R.id.login_google).setOnClickListener(v -> openMain());
        findViewById(R.id.login_apple).setOnClickListener(v -> openMain());
        findViewById(R.id.login_facebook).setOnClickListener(v -> openMain());
        findViewById(R.id.login_go_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    static void togglePassword(EditText field, ImageView toggle, boolean visible) {
        field.setInputType(InputType.TYPE_CLASS_TEXT | (visible
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        field.setSelection(field.getText().length());
        toggle.setImageResource(visible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
    }

    private void submitLogin(String email, String password, View submit) {
        submit.setEnabled(false);
        ApiClient.service(this).login(new LoginBody(email, password)).enqueue(new Callback<ApiResponse<AuthData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthData>> call, Response<ApiResponse<AuthData>> response) {
                runOnUiThread(() -> {
                    submit.setEnabled(true);
                    if (isFinishing()) return;
                    if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                        new SessionStore(LoginActivity.this).save(response.body().data);
                        openMain();
                    } else {
                        Ui.toast(LoginActivity.this, ApiClient.errorMessage(response));
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthData>> call, Throwable t) {
                runOnUiThread(() -> {
                    submit.setEnabled(true);
                    Ui.toast(LoginActivity.this, "Không kết nối được máy chủ");
                });
            }
        });
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
