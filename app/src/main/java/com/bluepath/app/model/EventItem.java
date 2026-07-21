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
        this.id = id;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.target = target;
        this.category = category;
        this.description = description;
        this.source = source == null ? "" : source;
        this.applicationUrl = applicationUrl == null ? "" : applicationUrl;
    }
}
