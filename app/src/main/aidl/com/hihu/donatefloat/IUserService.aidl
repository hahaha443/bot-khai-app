package com.hihu.donatefloat;

// Interface chạy trong tiến trình riêng do Shizuku spawn với quyền "shell",
// cho phép app gọi sang để thực thi lệnh có quyền cao.
interface IUserService {
    String exec(in String[] cmd);

    // Mã transaction cố định theo quy ước của Shizuku để dọn dẹp tiến trình
    // khi unbind — giữ đúng số 16777114 (không tự đổi).
    void destroy() = 16777114;
}
