package com.aishop.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "assistant_sessions")
public class AssistantSession extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 4000)
    private String summary;

    @Column(length = 128)
    private String lastIntent;

    @Column(nullable = false, length = 32)
    private String serviceStatus;

    @ManyToOne
    @JoinColumn(name = "assigned_admin_id")
    private AppUser assignedAdmin;

    private Instant assignedAt;

    private Instant firstSupportReplyAt;

    private Instant resolvedAt;

    private Instant lastCustomerMessageAt;

    private Instant lastSupportMessageAt;

    @Column
    private Long supportUnreadCount = 0L;

    @Column
    private Long customerUnreadCount = 0L;

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLastIntent() {
        return lastIntent;
    }

    public void setLastIntent(String lastIntent) {
        this.lastIntent = lastIntent;
    }

    public String getServiceStatus() {
        return serviceStatus;
    }

    public void setServiceStatus(String serviceStatus) {
        this.serviceStatus = serviceStatus;
    }

    public AppUser getAssignedAdmin() {
        return assignedAdmin;
    }

    public void setAssignedAdmin(AppUser assignedAdmin) {
        this.assignedAdmin = assignedAdmin;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getFirstSupportReplyAt() {
        return firstSupportReplyAt;
    }

    public void setFirstSupportReplyAt(Instant firstSupportReplyAt) {
        this.firstSupportReplyAt = firstSupportReplyAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getLastCustomerMessageAt() {
        return lastCustomerMessageAt;
    }

    public void setLastCustomerMessageAt(Instant lastCustomerMessageAt) {
        this.lastCustomerMessageAt = lastCustomerMessageAt;
    }

    public Instant getLastSupportMessageAt() {
        return lastSupportMessageAt;
    }

    public void setLastSupportMessageAt(Instant lastSupportMessageAt) {
        this.lastSupportMessageAt = lastSupportMessageAt;
    }

    public Long getSupportUnreadCount() {
        return supportUnreadCount;
    }

    public void setSupportUnreadCount(Long supportUnreadCount) {
        this.supportUnreadCount = supportUnreadCount;
    }

    public Long getCustomerUnreadCount() {
        return customerUnreadCount;
    }

    public void setCustomerUnreadCount(Long customerUnreadCount) {
        this.customerUnreadCount = customerUnreadCount;
    }
}
