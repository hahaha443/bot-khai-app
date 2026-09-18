package com.hihu.donatefloat;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Chạy TRONG tiến trình riêng mà Shizuku spawn với quyền UID "shell".
 * Vì cả tiến trình này đã chạy với quyền shell, Runtime.exec() bình thường
 * ở đây vẫn đọc được dumpsys/top của app khác — không cần thêm gì đặc biệt.
 */
public class UserService implements IUserService {

    // Constructor không tham số — bắt buộc, Shizuku dùng reflection để khởi tạo.
    public UserService() {
    }

    @Override
    public String exec(String[] cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "__ERROR__:" + e.getMessage();
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
