package com.aishop.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.Product;
import com.aishop.domain.ProductFavorite;
import com.aishop.dto.ProductDtos.FavoriteProductResponse;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.repository.ProductFavoriteRepository;

@Service
public class ProductFavoriteService {

    private final ProductFavoriteRepository favoriteRepository;
    private final ProductService productService;

    public ProductFavoriteService(ProductFavoriteRepository favoriteRepository,
                                  ProductService productService) {
        this.favoriteRepository = favoriteRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public List<FavoriteProductResponse> listFavorites(AppUser user) {
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toFavoriteResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> recentFavoriteProducts(AppUser user, int limit) {
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .limit(Math.max(0, limit))
                .map(ProductFavorite::getProduct)
                .map(productService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> favoriteProductIds(AppUser user) {
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(favorite -> favorite.getProduct().getId())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public long favoriteCount(AppUser user) {
        return favoriteRepository.countByUser(user);
    }

    @Transactional
    public FavoriteProductResponse addFavorite(AppUser user, Long productId) {
        Product product = productService.getProduct(productId);
        return favoriteRepository.findByUserAndProduct(user, product)
                .map(this::toFavoriteResponse)
                .orElseGet(() -> {
                    ProductFavorite favorite = new ProductFavorite();
                    favorite.setUser(user);
                    favorite.setProduct(product);
                    return toFavoriteResponse(favoriteRepository.save(favorite));
                });
    }

    @Transactional
    public void removeFavorite(AppUser user, Long productId) {
        Product product = productService.getProduct(productId);
        favoriteRepository.deleteByUserAndProduct(user, product);
    }

    private FavoriteProductResponse toFavoriteResponse(ProductFavorite favorite) {
        return new FavoriteProductResponse(
                favorite.getId(),
                favorite.getCreatedAt(),
                productService.toResponse(favorite.getProduct()));
    }
}
