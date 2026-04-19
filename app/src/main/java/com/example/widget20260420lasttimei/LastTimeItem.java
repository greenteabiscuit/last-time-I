package com.example.widget20260420lasttimei;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class LastTimeItem {
    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_LAST_REFRESHED_AT_MILLIS = "lastRefreshedAtMillis";

    private final String id;
    private String title;
    private long lastRefreshedAtMillis;

    public LastTimeItem(String id, String title, long lastRefreshedAtMillis) {
        this.id = id;
        this.title = title;
        this.lastRefreshedAtMillis = lastRefreshedAtMillis;
    }

    public static LastTimeItem create(String title) {
        return new LastTimeItem(UUID.randomUUID().toString(), title, System.currentTimeMillis());
    }

    public static LastTimeItem fromJson(JSONObject jsonObject) throws JSONException {
        String id = jsonObject.optString(KEY_ID, "");
        String title = jsonObject.optString(KEY_TITLE, "").trim();

        if (title.isEmpty()) {
            throw new JSONException("Last-time item title was empty");
        }

        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        long lastRefreshedAtMillis = jsonObject.optLong(KEY_LAST_REFRESHED_AT_MILLIS, System.currentTimeMillis());

        if (lastRefreshedAtMillis <= 0L) {
            lastRefreshedAtMillis = System.currentTimeMillis();
        }

        return new LastTimeItem(id, title, lastRefreshedAtMillis);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(KEY_ID, id);
        jsonObject.put(KEY_TITLE, title);
        jsonObject.put(KEY_LAST_REFRESHED_AT_MILLIS, lastRefreshedAtMillis);
        return jsonObject;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getLastRefreshedAtMillis() {
        return lastRefreshedAtMillis;
    }

    public void setLastRefreshedAtMillis(long lastRefreshedAtMillis) {
        this.lastRefreshedAtMillis = lastRefreshedAtMillis;
    }
}
