package org.csu.chatroom.controller;

import jakarta.servlet.http.HttpSession;
import org.csu.chatroom.Netty.NettyServer;
import org.csu.chatroom.entity.User;
import org.csu.chatroom.service.UserService;
import org.csu.chatroom.util.OnlineUserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Set;

@Controller
public class LoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private NettyServer nettyServer;
    @GetMapping("/login")
    public String loginPage() {
        return "login";  // 返回登录页面
    }

    @PostMapping("/login")
    public String login(String username, String password, HttpSession session) {
        User user = userService.login(username, password);  // 调用服务层方法查询数据库
        if (user != null) {
            // 登录成功，跳转到聊天室页面
            OnlineUserManager.addUser(username); // 记录在线用户！
            // 登录成功，保存用户名到Session
            session.setAttribute("user", user);
            System.out.println(username);

            return "redirect:/chat-home";
        } else {
            // 登录失败，回到登录页面并显示错误提示
            return "login";
        }
    }


    @GetMapping("/online-users")
    @ResponseBody
    public Set<String> getOnlineUsers() {
        return OnlineUserManager.getOnlineUsers();
    }
}