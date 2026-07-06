package com.aishop.dto;

import java.time.Instant;

public final class AddressDtos {
    private AddressDtos() {
    }

    public record AddressUpsertRequest(String label,
                                       String recipientName,
                                       String phone,
                                       String addressLine,
                                       Boolean defaultAddress) {}

    public record AddressResponse(Long id,
                                  String label,
                                  String recipientName,
                                  String phone,
                                  String addressLine,
                                  boolean defaultAddress,
                                  Instant createdAt) {}
}
