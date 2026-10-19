package com.chatapp.controller;

import com.chatapp.dto.UserResponse;
import com.chatapp.dto.UserSummary;
import com.chatapp.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserDetails principal) {
        return userService.getByUsername(principal.getUsername());
    }

    @GetMapping
    public List<UserSummary> search(@RequestParam(name = "search", defaultValue = "") String search,
                                    @AuthenticationPrincipal UserDetails principal) {
        return userService.search(principal.getUsername(), search);
    }
}
