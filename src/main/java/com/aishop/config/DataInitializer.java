package com.aishop.config;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aishop.domain.AppUser;
import com.aishop.domain.Product;
import com.aishop.domain.ProductCategory;
import com.aishop.repository.AppUserRepository;
import com.aishop.repository.ProductCategoryRepository;
import com.aishop.repository.ProductRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedData(AppUserRepository userRepository, ProductCategoryRepository categoryRepository, ProductRepository productRepository) {
        return args -> {
            var encoder = new BCryptPasswordEncoder();

            if (userRepository.count() == 0) {
                var user = new AppUser();
                user.setUsername("demo");
                user.setDisplayName("演示用户");
                user.setPasswordHash(encoder.encode("demo123"));
                user.setShippingAddress("上海市浦东新区演示路 88 号");
                user.setPreferencesSummary("喜欢性价比高的数码商品");
                userRepository.save(user);
            }

            var category = categoryRepository.findByName("数码").orElseGet(() -> {
                var c = new ProductCategory();
                c.setName("数码");
                c.setDescription("手机、耳机、配件");
                return categoryRepository.save(c);
            });

            if (productRepository.count() == 0) {
                var p = new Product();
                p.setSku("PHONE-001");
                p.setName("AI 推荐手机");
                p.setDescription("适合日常使用和拍照的入门旗舰");
                p.setPrice(new BigDecimal("2999.00"));
                p.setStock(20);
                p.setCategory(category);
                p.setImageUrl("https://picsum.photos/seed/aishop/640/480");
                productRepository.save(p);
            }
        };
    }
}
