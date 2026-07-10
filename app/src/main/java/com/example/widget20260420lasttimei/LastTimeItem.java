package com.example.widget20260420lasttimei;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.TreeSet;

public class LastTimeItem {
    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_TAG = "tag";
    private static final String KEY_TAGS = "tags";
    private static final String KEY_LAST_REFRESHED_AT_MILLIS = "lastRefreshedAtMillis";
    private static final String KEY_REFRESH_HISTORY_DATES = "refreshHistoryDates";
    private static final String KEY_REFRESH_HISTORY_MILLIS = "refreshHistoryMillis";
    private static final String KEY_INTERVAL_DAYS = "intervalDays";
    private static final String KEY_DELETED_AT_MILLIS = "deletedAtMillis";

    private final String id;
    private String title;
    private final List<String> tags;
    private long lastRefreshedAtMillis;
    private int intervalDays;
    private long deletedAtMillis;
    private final List<LocalDate> refreshHistoryDates;

    public LastTimeItem(
            String id,
            String title,
            List<String> tags,
            long lastRefreshedAtMillis,
            int intervalDays,
            long deletedAtMillis,
            List<LocalDate> refreshHistoryDates
    ) {
        this.id = id;
        this.title = title;
        this.tags = normalizeTags(tags);
        this.lastRefreshedAtMillis = lastRefreshedAtMillis;
        this.intervalDays = Math.max(intervalDays, 0);
        this.deletedAtMillis = Math.max(deletedAtMillis, 0L);
        this.refreshHistoryDates = refreshHistoryDates;
    }

    public static LastTimeItem create(String title) {
        return create(title, 0);
    }

    public static LastTimeItem create(String title, int intervalDays) {
        return create(title, intervalDays, Collections.emptyList());
    }

    public static LastTimeItem create(String title, int intervalDays, List<String> tags) {
        LocalDate today = LocalDate.now();
        List<LocalDate> refreshHistoryDates = new ArrayList<>();
        refreshHistoryDates.add(today);
        return new LastTimeItem(
                UUID.randomUUID().toString(),
                title,
                tags,
                LastTimeFormatter.getStartOfDayMillis(today),
                intervalDays,
                0L,
                refreshHistoryDates
        );
    }

    public static boolean hasRefreshHistory(JSONObject jsonObject) {
        return jsonObject.has(KEY_REFRESH_HISTORY_DATES);
    }

    public static boolean hasTags(JSONObject jsonObject) {
        return jsonObject.has(KEY_TAGS);
    }

    public static LastTimeItem fromJson(JSONObject jsonObject) throws JSONException {
        String id = jsonObject.optString(KEY_ID, "");
        String title = jsonObject.optString(KEY_TITLE, "").trim();
        List<String> tags = new ArrayList<>();
        JSONArray tagsJson = jsonObject.optJSONArray(KEY_TAGS);

        if (tagsJson != null) {
            for (int index = 0; index < tagsJson.length(); index++) {
                tags.add(tagsJson.optString(index, ""));
            }
        } else {
            tags.add(jsonObject.optString(KEY_TAG, ""));
        }

        if (title.isEmpty()) {
            throw new JSONException("Last-time item title was empty");
        }

        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        long lastRefreshedAtMillis = jsonObject.has(KEY_LAST_REFRESHED_AT_MILLIS)
                ? jsonObject.optLong(KEY_LAST_REFRESHED_AT_MILLIS, 0L)
                : 0L;
        int intervalDays = Math.max(jsonObject.optInt(KEY_INTERVAL_DAYS, 0), 0);
        long deletedAtMillis = Math.max(jsonObject.optLong(KEY_DELETED_AT_MILLIS, 0L), 0L);

        TreeSet<LocalDate> refreshHistoryDates = new TreeSet<>();
        JSONArray refreshHistoryDatesJson = jsonObject.optJSONArray(KEY_REFRESH_HISTORY_DATES);

        if (refreshHistoryDatesJson != null) {
            for (int index = 0; index < refreshHistoryDatesJson.length(); index++) {
                String dateKey = refreshHistoryDatesJson.optString(index, "").trim();

                if (dateKey.isEmpty()) {
                    continue;
                }

                try {
                    refreshHistoryDates.add(LocalDate.parse(dateKey));
                } catch (RuntimeException ignored) {
                    // Skip unreadable day keys and fall back to older fields if needed.
                }
            }
        }

        if (refreshHistoryDates.isEmpty()) {
            JSONArray refreshHistoryMillisJson = jsonObject.optJSONArray(KEY_REFRESH_HISTORY_MILLIS);

            if (refreshHistoryMillisJson != null) {
                for (int index = 0; index < refreshHistoryMillisJson.length(); index++) {
                    long eventAtMillis = refreshHistoryMillisJson.optLong(index, -1L);

                    if (eventAtMillis > 0L) {
                        refreshHistoryDates.add(LastTimeFormatter.getLocalDate(eventAtMillis));
                    }
                }
            }
        }

        if (refreshHistoryDates.isEmpty() && lastRefreshedAtMillis > 0L) {
            refreshHistoryDates.add(LastTimeFormatter.getLocalDate(lastRefreshedAtMillis));
        }

        long latestRefreshAtMillis = refreshHistoryDates.isEmpty()
                ? 0L
                : LastTimeFormatter.getStartOfDayMillis(refreshHistoryDates.last());
        return new LastTimeItem(id, title, tags, latestRefreshAtMillis, intervalDays, deletedAtMillis, new ArrayList<>(refreshHistoryDates));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray refreshHistoryJson = new JSONArray();
        JSONArray tagsJson = new JSONArray();

        for (LocalDate refreshDate : refreshHistoryDates) {
            refreshHistoryJson.put(LastTimeFormatter.getDateKey(refreshDate));
        }

        for (String tag : tags) {
            tagsJson.put(tag);
        }

        jsonObject.put(KEY_ID, id);
        jsonObject.put(KEY_TITLE, title);
        jsonObject.put(KEY_TAGS, tagsJson);
        jsonObject.put(KEY_LAST_REFRESHED_AT_MILLIS, lastRefreshedAtMillis);
        jsonObject.put(KEY_INTERVAL_DAYS, intervalDays);
        jsonObject.put(KEY_DELETED_AT_MILLIS, deletedAtMillis);
        jsonObject.put(KEY_REFRESH_HISTORY_DATES, refreshHistoryJson);
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

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public boolean hasTags() {
        return !tags.isEmpty();
    }

    public void setTags(List<String> tags) {
        this.tags.clear();
        this.tags.addAll(normalizeTags(tags));
    }

    public long getLastRefreshedAtMillis() {
        return lastRefreshedAtMillis;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public long getDeletedAtMillis() {
        return deletedAtMillis;
    }

    public boolean isDeleted() {
        return deletedAtMillis > 0L;
    }

    public void markDeleted(long deletedAtMillis) {
        this.deletedAtMillis = Math.max(deletedAtMillis, 1L);
    }

    public void restore() {
        deletedAtMillis = 0L;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = Math.max(intervalDays, 0);
    }

    public long getDaysSince() {
        return LastTimeFormatter.getDaysSince(lastRefreshedAtMillis);
    }

    public boolean isOverdue() {
        return intervalDays > 0 && getDaysSince() > intervalDays;
    }

    public List<LocalDate> getRefreshHistoryDates() {
        return Collections.unmodifiableList(refreshHistoryDates);
    }

    public void recordRefreshOn(LocalDate refreshDate) {
        if (refreshDate == null) {
            refreshDate = LocalDate.now();
        }

        if (!refreshHistoryDates.contains(refreshDate)) {
            refreshHistoryDates.add(refreshDate);
            Collections.sort(refreshHistoryDates);
        }

        syncLastRefreshedAtMillis();
    }

    public void toggleRefreshOn(LocalDate refreshDate) {
        if (refreshDate == null) {
            refreshDate = LocalDate.now();
        }

        if (refreshHistoryDates.contains(refreshDate)) {
            refreshHistoryDates.remove(refreshDate);
        } else {
            refreshHistoryDates.add(refreshDate);
            Collections.sort(refreshHistoryDates);
        }

        syncLastRefreshedAtMillis();
    }

    private void syncLastRefreshedAtMillis() {
        if (refreshHistoryDates.isEmpty()) {
            lastRefreshedAtMillis = 0L;
            return;
        }

        lastRefreshedAtMillis = LastTimeFormatter.getStartOfDayMillis(refreshHistoryDates.get(refreshHistoryDates.size() - 1));
    }

    public static List<String> parseTags(String rawTags) {
        if (rawTags == null || rawTags.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] tagParts = rawTags.split(",");
        List<String> tags = new ArrayList<>(tagParts.length);
        Collections.addAll(tags, tagParts);
        return normalizeTags(tags);
    }

    private static List<String> normalizeTags(List<String> rawTags) {
        List<String> normalizedTags = new ArrayList<>();
        Set<String> normalizedTagKeys = new HashSet<>();

        if (rawTags == null) {
            return normalizedTags;
        }

        for (String rawTag : rawTags) {
            String tag = rawTag == null ? "" : rawTag.trim();

            if (tag.startsWith("#")) {
                tag = tag.substring(1).trim();
            }

            String tagKey = tag.toLowerCase(Locale.ROOT);

            if (!tag.isEmpty() && normalizedTagKeys.add(tagKey)) {
                normalizedTags.add(tag);
            }
        }

        return normalizedTags;
    }
}
