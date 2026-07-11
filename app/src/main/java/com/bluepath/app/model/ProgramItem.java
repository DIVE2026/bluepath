package com.bluepath.app.model;

public class ProgramItem {
    public final String id;
    public final String title;
    public final String target;
    public final String startDate;
    public final String endDate;
    public final String method;
    public final String topic;
    public final String description;
    public final String source;

    public ProgramItem(String id, String title, String target, String startDate, String endDate,
                       String method, String topic, String description) {
        this(id, title, target, startDate, endDate, method, topic, description, "제공 데이터");
    }

    public ProgramItem(String id, String title, String target, String startDate, String endDate,
                       String method, String topic, String description, String source) {
        this.id = id;
        this.title = title;
        this.target = target;
        this.startDate = startDate;
        this.endDate = endDate;
        this.method = method;
        this.topic = topic;
        this.description = description;
        this.source = source == null ? "" : source;
    }
}
