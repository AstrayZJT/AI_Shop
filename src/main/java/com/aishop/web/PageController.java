package com.aishop.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping({"/", "/index.html"})
    public String index() {
        return "redirect:/client/index.html";
    }

    @GetMapping("/assistant.html")
    public String assistant() {
        return "redirect:/client/index.html#assistant";
    }

    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin/index.html";
    }

    @GetMapping("/client")
    public String client() {
        return "redirect:/client/index.html";
    }
}
