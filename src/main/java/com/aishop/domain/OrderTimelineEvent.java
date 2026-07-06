package com.aishop.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_timeline_events")
public class OrderTimelineEvent extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private ShopOrder order;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 1024)
    private String detail;

    @Column(nullable = false, length = 64)
    private String actorLabel;

    @Column(nullable = false)
    private Instant occurredAt;

    public ShopOrder getOrder() {
        return order;
    }

    public void setOrder(ShopOrder order) {
        this.order = order;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public void setActorLabel(String actorLabel) {
        this.actorLabel = actorLabel;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
