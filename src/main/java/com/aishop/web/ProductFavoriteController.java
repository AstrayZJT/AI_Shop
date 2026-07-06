package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.ProductDtos.FavoriteProductResponse;
import com.aishop.service.AuthService;
import com.aishop.service.ProductFavoriteService;

import jakarta.servlet.http.HttpSession;

@RestController
public class ProductFavoriteController {

    private final AuthService authService;
    private final ProductFavoriteService favoriteService;

    public ProductFavoriteController(AuthService authService,
                                     ProductFavoriteService favoriteService) {
        this.authService = authService;
        this.favoriteService = favoriteService;
    }

    @GetMapping("/api/favorites")
    public List<FavoriteProductResponse> listFavorites(HttpSession session) {
        return favoriteService.listFavorites(authService.requireUser(session));
    }

    @PostMapping("/api/favorites/products/{productId}")
    public FavoriteProductResponse addFavorite(HttpSession session, @PathVariable Long productId) {
        return favoriteService.addFavorite(authService.requireUser(session), productId);
    }

    @DeleteMapping("/api/favorites/products/{productId}")
    public void removeFavorite(HttpSession session, @PathVariable Long productId) {
        favoriteService.removeFavorite(authService.requireUser(session), productId);
    }
}
