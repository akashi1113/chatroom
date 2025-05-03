package org.csu.chatroom.controller;

import jakarta.servlet.http.HttpSession;
import org.csu.chatroom.entity.Room;
import org.csu.chatroom.entity.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PrivateChatController {

    @GetMapping("/private-chat")
    public String privateChatPage(@RequestParam("target") String targetUser,Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");  // 从 Session 中获取用户信息
        if (user == null) {
            return "redirect:/login";  // 如果用户未登录，重定向到登录页面
        }

        model.addAttribute("username", user.getUsername());  // 当前登录用户
        model.addAttribute("targetUser", targetUser); // 要私聊的目标用户

        return "private-chat";  // 👉 对应 templates/private-chat.html
    }
}
