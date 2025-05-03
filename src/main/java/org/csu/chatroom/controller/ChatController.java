package org.csu.chatroom.controller;


import jakarta.servlet.http.HttpSession;
import org.csu.chatroom.entity.Room;
import org.csu.chatroom.entity.User;
import org.csu.chatroom.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class ChatController {

    @Autowired
    private RoomService roomService;

    @GetMapping("/chat/{roomId}")
    public String chatPage(Model model, HttpSession session, @PathVariable int roomId) {
        User user = (User) session.getAttribute("user");  // 从 Session 中获取用户信息
        Room room=roomService.getRoomById(roomId);
        if (user == null) {
            return "redirect:/login";  // 如果用户未登录，重定向到登录页面
        }

        model.addAttribute("user", user);
        model.addAttribute("roomId", roomId);
        model.addAttribute("roomName", room.getName());
        model.addAttribute("username", user.getUsername());

        return "chat"; // chat.html
    }

    // 跳转到聊天首页，显示用户信息和聊天室列表
    @GetMapping("/chat-home")
    public String chatHome(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");  // 从 Session 中获取用户信息
        if (user == null) {
            return "redirect:/login";  // 如果用户未登录，重定向到登录页面
        }

        model.addAttribute("user", user);  // 将用户信息传递给视图

        // 获取所有聊天室
        List<Room> rooms = roomService.getRooms();
        model.addAttribute("rooms", rooms);

        return "chat-home";  // 返回聊天首页视图
    }

    @PostMapping("/create-room")
    public String createRoom(@RequestParam String name, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");  // 从 Session 获取用户信息
        if (user == null) {
            return "redirect:/login";  // 如果用户未登录，重定向到登录页面
        }

        // 创建新聊天室
        Room room = new Room();
        room.setName(name);
        roomService.createRoom(room);
        redirectAttributes.addAttribute("roomId", room.getId());

        // 创建聊天室后跳转到该聊天室的界面
        return "redirect:/chat/{roomId}";
    }

}
