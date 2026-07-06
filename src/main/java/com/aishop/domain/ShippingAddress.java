package com.aishop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "shipping_addresses")
public class ShippingAddress extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(length = 32)
    private String label;

    @Column(nullable = false, length = 64)
    private String recipientName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 512)
    private String addressLine;

    @Column(nullable = false)
    private Boolean defaultAddress = false;

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public Boolean getDefaultAddress() {
        return Boolean.TRUE.equals(defaultAddress);
    }

    public void setDefaultAddress(Boolean defaultAddress) {
        this.defaultAddress = Boolean.TRUE.equals(defaultAddress);
    }
}
