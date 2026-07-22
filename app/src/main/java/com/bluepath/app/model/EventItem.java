package com.bluepath.app.model;

public class EventItem {
    public final String id;
    public final String title;
    public final String startDate;
    public final String endDate;
    public final String target;
    public final String category;
    public final String description;
    public final String source;
    public final String applicationUrl;
    public final String applicationDeadline;
    public final int capacity;
    public final boolean waitlistAvailable;
    public final String timezone;

    public EventItem(String id, String title, String startDate, String endDate, String target,
                     String category, String description) {
        this(id, title, startDate, endDate, target, category, description, "제공 데이터", "");
    }

    public EventItem(String id, String title, String startDate, String endDate, String target,
                     String category, String description, String source) {
        this(id, title, startDate, endDate, target, category, description, source, "");
    }

    public EventItem(String id, String title, String startDate, String endDate, String target,
                     String category, String description, String source, String applicationUrl) {
        this(id, title, startDate, endDate, target, category, description, source, applicationUrl,
                "", 0, false, "Asia/Seoul");
    }

    public EventItem(String id, String title, String startDate, String endDate, String target,
                     String category, String description, String source, String applicationUrl,
                     String applicationDeadline, int capacity, boolean waitlistAvailable, String timezone) {
        this.id = id;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.target = target;
        this.category = category;
        this.description = description;
        this.source = source == null ? "" : source;
        this.applicationUrl = applicationUrl == null ? "" : applicationUrl;
        this.applicationDeadline = applicationDeadline == null ? "" : applicationDeadline;
        this.capacity = Math.max(0, capacity);
        this.waitlistAvailable = waitlistAvailable;
        this.timezone = timezone == null || timezone.trim().isEmpty() ? "Asia/Seoul" : timezone;
    }
}
