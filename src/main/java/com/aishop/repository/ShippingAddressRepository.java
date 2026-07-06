package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AppUser;
import com.aishop.domain.ShippingAddress;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {
    List<ShippingAddress> findByUserOrderByDefaultAddressDescCreatedAtDesc(AppUser user);
    Optional<ShippingAddress> findByIdAndUser(Long id, AppUser user);
    Optional<ShippingAddress> findTop1ByUserAndDefaultAddressTrueOrderByCreatedAtDesc(AppUser user);
    long countByUser(AppUser user);
}
