package com.aishop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.ShippingAddress;
import com.aishop.dto.AddressDtos.AddressResponse;
import com.aishop.dto.AddressDtos.AddressUpsertRequest;
import com.aishop.repository.AppUserRepository;
import com.aishop.repository.ShippingAddressRepository;

@Service
public class ShippingAddressService {

    private final ShippingAddressRepository addressRepository;
    private final AppUserRepository userRepository;

    public ShippingAddressService(ShippingAddressRepository addressRepository,
                                  AppUserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(AppUser user) {
        return addressRepository.findByUserOrderByDefaultAddressDescCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(AppUser user) {
        return addressRepository.countByUser(user);
    }

    @Transactional(readOnly = true)
    public String resolveCheckoutAddress(AppUser user, Long addressId, String fallbackAddress) {
        if (addressId != null) {
            return addressRepository.findByIdAndUser(addressId, user)
                    .map(this::formatAddress)
                    .orElseThrow(() -> new IllegalArgumentException("收货地址不存在"));
        }
        String normalizedFallback = blankToNull(fallbackAddress);
        if (normalizedFallback != null) {
            return normalizedFallback;
        }
        return addressRepository.findTop1ByUserAndDefaultAddressTrueOrderByCreatedAtDesc(user)
                .map(this::formatAddress)
                .orElse(user.getShippingAddress() == null ? "待补充收货地址" : user.getShippingAddress());
    }

    @Transactional
    public AddressResponse create(AppUser user, AddressUpsertRequest request) {
        ShippingAddress address = new ShippingAddress();
        address.setUser(user);
        applyFields(address, request);
        boolean shouldDefault = Boolean.TRUE.equals(request.defaultAddress())
                || addressRepository.countByUser(user) == 0;
        address.setDefaultAddress(shouldDefault);
        if (shouldDefault) {
            clearDefaultAddresses(user, null);
        }
        ShippingAddress saved = addressRepository.save(address);
        if (saved.getDefaultAddress()) {
            syncUserDefaultAddress(user, saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public AddressResponse update(AppUser user, Long id, AddressUpsertRequest request) {
        ShippingAddress address = addressRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("收货地址不存在"));
        applyFields(address, request);
        if (Boolean.TRUE.equals(request.defaultAddress())) {
            clearDefaultAddresses(user, address.getId());
            address.setDefaultAddress(true);
        }
        ShippingAddress saved = addressRepository.save(address);
        if (saved.getDefaultAddress()) {
            syncUserDefaultAddress(user, saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public AddressResponse setDefault(AppUser user, Long id) {
        ShippingAddress address = addressRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("收货地址不存在"));
        clearDefaultAddresses(user, address.getId());
        address.setDefaultAddress(true);
        ShippingAddress saved = addressRepository.save(address);
        syncUserDefaultAddress(user, saved);
        return toResponse(saved);
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        ShippingAddress address = addressRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("收货地址不存在"));
        boolean wasDefault = address.getDefaultAddress();
        addressRepository.delete(address);
        if (wasDefault) {
            addressRepository.findByUserOrderByDefaultAddressDescCreatedAtDesc(user).stream()
                    .findFirst()
                    .ifPresentOrElse(next -> {
                        next.setDefaultAddress(true);
                        syncUserDefaultAddress(user, addressRepository.save(next));
                    }, () -> syncUserDefaultAddress(user, null));
        }
    }

    private void applyFields(ShippingAddress address, AddressUpsertRequest request) {
        address.setLabel(trimToNull(request.label()));
        address.setRecipientName(requireText(request.recipientName(), "请填写收货人"));
        address.setPhone(requireText(request.phone(), "请填写联系电话"));
        address.setAddressLine(requireText(request.addressLine(), "请填写详细收货地址"));
    }

    private void clearDefaultAddresses(AppUser user, Long exceptId) {
        for (ShippingAddress address : addressRepository.findByUserOrderByDefaultAddressDescCreatedAtDesc(user)) {
            if (!address.getDefaultAddress()) {
                continue;
            }
            if (exceptId != null && exceptId.equals(address.getId())) {
                continue;
            }
            address.setDefaultAddress(false);
            addressRepository.save(address);
        }
    }

    private void syncUserDefaultAddress(AppUser user, ShippingAddress address) {
        AppUser managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        managed.setShippingAddress(address == null ? null : formatAddress(address));
        if (address != null && blankToNull(managed.getPhone()) == null) {
            managed.setPhone(address.getPhone());
        }
        userRepository.save(managed);
    }

    private String formatAddress(ShippingAddress address) {
        return "%s，%s，%s".formatted(
                address.getRecipientName(),
                address.getPhone(),
                address.getAddressLine());
    }

    private AddressResponse toResponse(ShippingAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhone(),
                address.getAddressLine(),
                address.getDefaultAddress(),
                address.getCreatedAt());
    }

    private String requireText(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return blankToNull(value);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
