package com.bluepath.app.model;

public class InstitutionItem {
    public final String id;
    public final String category;
    public final String name;
    public final String source;

    public InstitutionItem(String id, String category, String name, String source) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.source = source == null ? "" : source;
    }
}
