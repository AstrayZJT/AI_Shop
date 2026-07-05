package com.aishop.config;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aishop.domain.AppUser;
import com.aishop.domain.Product;
import com.aishop.domain.ProductCategory;
import com.aishop.domain.UserRole;
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

            userRepository.findByUsername("demo").ifPresentOrElse(user -> {
                user.setRole(UserRole.CUSTOMER);
                userRepository.save(user);
            }, () -> {
                var user = new AppUser();
                user.setUsername("demo");
                user.setDisplayName("演示用户");
                user.setPasswordHash(encoder.encode("demo123"));
                user.setShippingAddress("上海市浦东新区演示路 88 号");
                user.setPreferencesSummary("喜欢性价比高的数码商品");
                user.setRole(UserRole.CUSTOMER);
                userRepository.save(user);
            });

            userRepository.findByUsername("admin").ifPresentOrElse(user -> {
                if (user.getRole() != UserRole.ADMIN) {
                    user.setRole(UserRole.ADMIN);
                    user.setDisplayName("平台管理员");
                    userRepository.save(user);
                }
            }, () -> {
                var admin = new AppUser();
                admin.setUsername("admin");
                admin.setDisplayName("平台管理员");
                admin.setPasswordHash(encoder.encode("admin123"));
                admin.setShippingAddress("上海市徐汇区管理中心 1 号");
                admin.setPreferencesSummary("负责商品、订单和知识库运营");
                admin.setRole(UserRole.ADMIN);
                userRepository.save(admin);
            });

            var electronics = categoryRepository.findByName("数码").orElseGet(() -> {
                var c = new ProductCategory();
                c.setName("数码");
                c.setDescription("手机、耳机、平板、智能穿戴");
                return categoryRepository.save(c);
            });

            var home = categoryRepository.findByName("家居").orElseGet(() -> {
                var c = new ProductCategory();
                c.setName("家居");
                c.setDescription("智能家电与品质生活用品");
                return categoryRepository.save(c);
            });

            var sports = categoryRepository.findByName("运动").orElseGet(() -> {
                var c = new ProductCategory();
                c.setName("运动");
                c.setDescription("通勤健身与户外装备");
                return categoryRepository.save(c);
            });

            seedProduct(productRepository, electronics, "PHONE-001", "曜石 AI 手机", "1 英寸主摄、全天续航，适合拍照与重度办公。", "5299.00", 18,
                    "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, electronics, "AUDIO-002", "降噪头戴耳机 Pro", "42dB 主动降噪，支持空间音频和多设备切换。", "1899.00", 32,
                    "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, electronics, "PAD-003", "轻办公平板 12", "适合会议记录、追剧和轻量绘图，附带磁吸键盘。", "3299.00", 14,
                    "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, home, "HOME-004", "空气净化器 Max", "适合卧室与客厅，支持睡眠模式和空气质量联动。", "2399.00", 10,
                    "https://images.unsplash.com/photo-1585771724684-38269d6639fd?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, home, "COFFEE-005", "意式胶囊咖啡机", "3 档浓度调节，适合晨间快速出杯。", "1299.00", 22,
                    "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, sports, "SPORT-006", "城市通勤跑鞋", "缓震中底和轻量鞋面，适合日常跑步与步行通勤。", "699.00", 40,
                    "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80");
        };
    }

    private void seedProduct(ProductRepository productRepository,
                             ProductCategory category,
                             String sku,
                             String name,
                             String description,
                             String price,
                             int stock,
                             String imageUrl) {
        if (productRepository.findBySku(sku).isPresent()) {
            return;
        }
        var product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setCategory(category);
        product.setImageUrl(imageUrl);
        productRepository.save(product);
    }
}
