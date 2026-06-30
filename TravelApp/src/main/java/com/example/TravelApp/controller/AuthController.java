package com.example.TravelApp.controller;

import com.example.TravelApp.model.User;
import com.example.TravelApp.service.EmailService; 
import com.example.TravelApp.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final UserService userService;
    private final EmailService emailService; 

    // Constructor Injection
    public AuthController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam String email, @RequestParam String password,
                              HttpSession session, Model model) {
        
        // 🚨 [FIX] පැරණි සෙෂන් එකක් ඉතිරි වී ඇත්නම් එය සම්පූර්ණයෙන්ම මකා දමයි (Refresh ලෙඩේ වැළැක්වීමට)
        session.invalidate(); 

        var user = userService.findByEmail(email);

        if (user.isPresent() && user.get().getPassword().equals(password)) {
            
            // 🚨 [FIX] වීඩියෝ බැක්ග්‍රවුන්ඩ් එක නිසා සෙෂන් එක හිරවීම වැළැක්වීමට අලුත්ම පිරිසිදු සෙෂන් එකක් සාදා ගනී
            session = jakarta.servlet.http.HttpServletRequest.class.cast(
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()
                .resolveReference(org.springframework.web.context.request.RequestAttributes.REFERENCE_REQUEST)
            ).getSession(true);

            session.setAttribute("user", user.get());
            session.setAttribute("userId", user.get().getId());
            session.setAttribute("userRole", user.get().getRole());
            session.setAttribute("userName", user.get().getName());

            if ("ADMIN".equals(user.get().getRole())) {
                return "redirect:/admin";
            }
            return "redirect:/?loginSuccess=true"; // 🎯 හෝම් පේජ් එකට සේෆ් පැරාමීටර් එකක් සමඟ රීඩිරෙක්ට් කරයි
        }

        model.addAttribute("error", "Invalid email or password");
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(@RequestParam String name, @RequestParam String email,
                                 @RequestParam String password, @RequestParam String phone,
                                 Model model) {
        if (userService.findByEmail(email).isPresent()) {
            model.addAttribute("error", "Email already exists");
            return "register";
        }

        User newUser = new User(name, email, password, "USER");
        newUser.setPhone(phone);
        userService.save(newUser);

        model.addAttribute("success", "Registration successful! Please login.");
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    // ==========================================
    // REAL WORLD EMAIL FORGOT PASSWORD OPTION
    // ==========================================

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam String email, Model model) {
        var userOptional = userService.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // රහස්‍ය ටෝකන් එකක් සහ විනාඩි 15ක Expiry කාලයක් දීම
            String token = java.util.UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setTokenExpiry(java.time.LocalDateTime.now().plusMinutes(15));
            userService.save(user);

            // මුලින්ම Localhost ලින්ක් එක සාදාගන්නවා
            String resetLink = "http://localhost:8080/reset-password?token=" + token;

            try {
                // ⚡ පරණ SimpleMailMessage එක වෙනුවට අපේ EmailService එකේ තියෙන HTML මෙතඩ් එක කෝල් කළා
                emailService.sendForgotPasswordEmail(email, user.getName(), resetLink);

                model.addAttribute("success", "Reset link has been sent to your email address successfully!");
            } catch (Exception e) {
                model.addAttribute("error", "Failed to send email. Please check your SMTP settings.");
                e.printStackTrace();
            }

        } else {
            model.addAttribute("error", "Email address not found!");
        }
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        var userOptional = userService.findByResetToken(token);

        if (userOptional.isEmpty() || userOptional.get().getTokenExpiry().isBefore(java.time.LocalDateTime.now())) {
            model.addAttribute("error", "Invalid or expired password reset token!");
            return "forgot-password";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(@RequestParam String token, @RequestParam String password, Model model) {
        var userOptional = userService.findByResetToken(token);

        if (userOptional.isEmpty() || userOptional.get().getTokenExpiry().isBefore(java.time.LocalDateTime.now())) {
            model.addAttribute("error", "Invalid or expired token!");
            return "forgot-password";
        }

        User user = userOptional.get();
        userService.updatePassword(user, password);

        model.addAttribute("success", "Password reset successful! Please login with your new password.");
        return "login";
    }
}
