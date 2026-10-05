package com.scsb.bomhelper.controller;

import com.scsb.bomhelper.security.GitLabUserPrincipal;
import com.scsb.bomhelper.service.UserManagementService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/users")
public class UserManagementController {
    private final UserManagementService service;

    public UserManagementController(UserManagementService service) { this.service = service; }

    @GetMapping
    public String list(@AuthenticationPrincipal GitLabUserPrincipal actor,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("usersPage", service.list(actor, page));
        return "users";
    }

    @PostMapping("/status")
    public String updateStatus(@AuthenticationPrincipal GitLabUserPrincipal actor,
                               @RequestParam String userId, @RequestParam String status,
                               RedirectAttributes redirect) {
        try {
            service.updateStatus(actor, userId, status);
            redirect.addFlashAttribute("successMessage", "使用者「" + userId.trim() + "」狀態已更新為 " + status + "。");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal GitLabUserPrincipal actor,
                         @RequestParam String userId, @RequestParam(required = false) String password,
                         @RequestParam(required = false) String confirmation, @RequestParam String authorityCode,
                         @RequestParam String status, Model model, RedirectAttributes redirect,
                         HttpServletResponse response) {
        try {
            service.create(actor, userId, password, confirmation, authorityCode, status);
            redirect.addFlashAttribute("successMessage", "使用者帳號「" + userId.trim() + "」已建立。");
            return "redirect:/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            response.setStatus(400);
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("errorMessage", "帳號建立衝突，請確認此帳號是否已存在。");
            response.setStatus(409);
        }
        model.addAttribute("enteredUserId", userId);
        model.addAttribute("usersPage", service.list(actor, 0));
        return "users"; // Never echo either password back to the browser.
    }
}
