package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.ProductDtos.ProductEventRequest;
import com.aishop.dto.ProductDtos.ProductEventResponse;
import com.aishop.service.AuthService;
import com.aishop.service.CustomerBehaviorService;

import jakarta.servlet.http.HttpSession;

@RestController
public class ProductEventController {

    private final AuthService authService;
    private final CustomerBehaviorService behaviorService;

    public ProductEventController(AuthService authService,
                                  CustomerBehaviorService behaviorService) {
        this.authService = authService;
        this.behaviorService = behaviorService;
    }

    @GetMapping("/api/products/events")
    public List<ProductEventResponse> recentEvents(HttpSession session) {
        return behaviorService.recentEvents(authService.requireUser(session), 20);
    }

    @PostMapping("/api/products/events")
    public ProductEventResponse recordEvent(HttpSession session, @RequestBody ProductEventRequest request) {
        return behaviorService.recordEvent(authService.requireUser(session), request);
    }
}
