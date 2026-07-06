package com.aishop.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "after_sales_cases")
public class AfterSalesCase extends BaseEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id", unique = true)
    private ShopOrder order;

    @Column(nullable = false, length = 48)
    private String status;

    @Column(length = 1024)
    private String customerReason;

    @Column(length = 1024)
    private String adminReply;

    @Column
    private Boolean returnRequired = Boolean.FALSE;

    @Column(length = 512)
    private String returnAddress;

    @Column(length = 64)
    private String returnCarrier;

    @Column(length = 64)
    private String returnTrackingNo;

    @Column(length = 1024)
    private String returnNote;

    private Instant requestedAt;

    private Instant adminRespondedAt;

    private Instant customerShippedAt;

    private Instant resolvedAt;

    public ShopOrder getOrder() {
        return order;
    }

    public void setOrder(ShopOrder order) {
        this.order = order;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCustomerReason() {
        return customerReason;
    }

    public void setCustomerReason(String customerReason) {
        this.customerReason = customerReason;
    }

    public String getAdminReply() {
        return adminReply;
    }

    public void setAdminReply(String adminReply) {
        this.adminReply = adminReply;
    }

    public Boolean getReturnRequired() {
        return returnRequired;
    }

    public void setReturnRequired(Boolean returnRequired) {
        this.returnRequired = returnRequired;
    }

    public String getReturnAddress() {
        return returnAddress;
    }

    public void setReturnAddress(String returnAddress) {
        this.returnAddress = returnAddress;
    }

    public String getReturnCarrier() {
        return returnCarrier;
    }

    public void setReturnCarrier(String returnCarrier) {
        this.returnCarrier = returnCarrier;
    }

    public String getReturnTrackingNo() {
        return returnTrackingNo;
    }

    public void setReturnTrackingNo(String returnTrackingNo) {
        this.returnTrackingNo = returnTrackingNo;
    }

    public String getReturnNote() {
        return returnNote;
    }

    public void setReturnNote(String returnNote) {
        this.returnNote = returnNote;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getAdminRespondedAt() {
        return adminRespondedAt;
    }

    public void setAdminRespondedAt(Instant adminRespondedAt) {
        this.adminRespondedAt = adminRespondedAt;
    }

    public Instant getCustomerShippedAt() {
        return customerShippedAt;
    }

    public void setCustomerShippedAt(Instant customerShippedAt) {
        this.customerShippedAt = customerShippedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
