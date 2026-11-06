package com.gathin.tvhome.data.model;

import java.io.Serializable;

/**
 * Streaming service provider metadata (e.g. Netflix, Prime Video, Hotstar, YouTube).
 */
public class ProviderInfo implements Serializable {
    public final String name;
    public final String logoUrl;
    public final String type; // "flatrate" (stream), "rent", "buy", "free"

    public ProviderInfo(String name, String logoUrl, String type) {
        this.name = name != null ? name : "";
        this.logoUrl = logoUrl != null ? logoUrl : "";
        this.type = type != null ? type : "flatrate";
    }

    public boolean isStream() {
        return "flatrate".equalsIgnoreCase(type) || "free".equalsIgnoreCase(type);
    }
}
