package com.hihu.donatefloat;

// Interface chạy trong tiến trình riêng do Shizuku spawn với quyền "shell",
// cho phép app gọi sang để thực thi lệnh có quyền cao.
interface IUserService {
    String exec(in String[] cmd);
    void destroy();
}
