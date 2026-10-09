package com.example.pcaExamAnalyze.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The app is the MCQ exam system only: the site root is the exam portal and login is the admin login. */
@Controller
public class AuthController {

    @GetMapping("/")
    public String home() {
        return "redirect:/exam";
    }

    @GetMapping("/login")
    public String login() {
        return "redirect:/exam/admin-login";
    }
}
