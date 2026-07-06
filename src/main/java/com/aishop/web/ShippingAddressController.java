package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.AddressDtos.AddressResponse;
import com.aishop.dto.AddressDtos.AddressUpsertRequest;
import com.aishop.service.AuthService;
import com.aishop.service.ShippingAddressService;

import jakarta.servlet.http.HttpSession;

@RestController
public class ShippingAddressController {

    private final AuthService authService;
    private final ShippingAddressService addressService;

    public ShippingAddressController(AuthService authService,
                                     ShippingAddressService addressService) {
        this.authService = authService;
        this.addressService = addressService;
    }

    @GetMapping("/api/addresses")
    public List<AddressResponse> list(HttpSession session) {
        return addressService.list(authService.requireUser(session));
    }

    @PostMapping("/api/addresses")
    public AddressResponse create(HttpSession session, @RequestBody AddressUpsertRequest request) {
        return addressService.create(authService.requireUser(session), request);
    }

    @PutMapping("/api/addresses/{id}")
    public AddressResponse update(HttpSession session,
                                  @PathVariable Long id,
                                  @RequestBody AddressUpsertRequest request) {
        return addressService.update(authService.requireUser(session), id, request);
    }

    @PatchMapping("/api/addresses/{id}/default")
    public AddressResponse setDefault(HttpSession session, @PathVariable Long id) {
        return addressService.setDefault(authService.requireUser(session), id);
    }

    @DeleteMapping("/api/addresses/{id}")
    public void delete(HttpSession session, @PathVariable Long id) {
        addressService.delete(authService.requireUser(session), id);
    }
}
