package com.example.widget20260420lasttimei;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LastTimeStorage {
    private static final String LOG_TAG = "LastTimeIWidget";
    private static final String PREFS_NAME = "last_time_i_storage";
    private static final String KEY_ITEMS_JSON = "items_json";

    private LastTimeStorage() {
    }

    public static List<LastTimeItem> getItems(Context context) {
        String rawJson = getPreferences(context).getString(KEY_ITEMS_JSON, null);

        if (rawJson == null || rawJson.isEmpty()) {
            List<LastTimeItem> defaultItems = createDefaultItems();
            saveItems(context, defaultItems, "seed default last-time items");
            return defaultItems;
        }

        try {
            JSONArray jsonArray = new JSONArray(rawJson);
            List<LastTimeItem> items = new ArrayList<>(jsonArray.length());
            boolean needsHistoryMigration = false;

            for (int index = 0; index < jsonArray.length(); index++) {
                org.json.JSONObject jsonObject = jsonArray.getJSONObject(index);

                if (!LastTimeItem.hasRefreshHistory(jsonObject)) {
                    needsHistoryMigration = true;
                }

                items.add(LastTimeItem.fromJson(jsonObject));
            }

            if (needsHistoryMigration) {
                saveItems(context, items, "migrate items to include refresh history");
            }

            return items;
        } catch (JSONException exception) {
            Log.e(LOG_TAG, "Failed to read last-time JSON. Re-seeding defaults.", exception);
            List<LastTimeItem> defaultItems = createDefaultItems();
            saveItems(context, defaultItems, "recover from unreadable last-time JSON");
            return defaultItems;
        }
    }

    public static LastTimeItem getItem(Context context, String itemId) {
        for (LastTimeItem item : getItems(context)) {
            if (item.getId().equals(itemId)) {
                return item;
            }
        }

        return null;
    }

    public static LastTimeItem addItem(Context context, String rawTitle) {
        return addItem(context, rawTitle, 0);
    }

    public static LastTimeItem addItem(Context context, String rawTitle, int intervalDays) {
        String title = sanitizeTitle(rawTitle);

        if (title == null) {
            return null;
        }

        List<LastTimeItem> items = getItems(context);
        LastTimeItem item = LastTimeItem.create(title, intervalDays);
        items.add(item);
        saveItems(context, items, "add item " + item.getId());
        return item;
    }

    public static boolean updateItemTitle(Context context, String itemId, String rawTitle) {
        LastTimeItem item = getItem(context, itemId);

        if (item == null) {
            return false;
        }

        return updateItem(context, itemId, rawTitle, item.getIntervalDays());
    }

    public static boolean updateItem(Context context, String itemId, String rawTitle, int intervalDays) {
        String title = sanitizeTitle(rawTitle);

        if (title == null) {
            return false;
        }

        List<LastTimeItem> items = getItems(context);

        for (LastTimeItem item : items) {
            if (item.getId().equals(itemId)) {
                item.setTitle(title);
                item.setIntervalDays(intervalDays);
                saveItems(context, items, "update item " + itemId);
                return true;
            }
        }

        return false;
    }

    public static boolean deleteItem(Context context, String itemId) {
        List<LastTimeItem> items = getItems(context);

        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).getId().equals(itemId)) {
                items.remove(index);
                saveItems(context, items, "delete item " + itemId);
                return true;
            }
        }

        return false;
    }

    public static boolean markNow(Context context, String itemId) {
        List<LastTimeItem> items = getItems(context);
        LocalDate today = LocalDate.now();

        for (LastTimeItem item : items) {
            if (item.getId().equals(itemId)) {
                item.recordRefreshOn(today);
                saveItems(context, items, "mark now for item " + itemId);
                return true;
            }
        }

        return false;
    }

    public static boolean toggleRefreshDay(Context context, String itemId, LocalDate refreshDate) {
        List<LastTimeItem> items = getItems(context);

        for (LastTimeItem item : items) {
            if (item.getId().equals(itemId)) {
                item.toggleRefreshOn(refreshDate);
                saveItems(context, items, "toggle refresh day " + refreshDate + " for item " + itemId);
                return true;
            }
        }

        return false;
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void saveItems(Context context, List<LastTimeItem> items, String reason) {
        JSONArray jsonArray = new JSONArray();

        try {
            for (LastTimeItem item : items) {
                jsonArray.put(item.toJson());
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize last-time items", exception);
        }

        getPreferences(context)
                .edit()
                .putString(KEY_ITEMS_JSON, jsonArray.toString())
                .apply();

        Log.d(LOG_TAG, "Saved " + items.size() + " last-time item(s): " + reason);
    }

    private static String sanitizeTitle(String rawTitle) {
        if (rawTitle == null) {
            return null;
        }

        String trimmedTitle = rawTitle.trim();
        return trimmedTitle.isEmpty() ? null : trimmedTitle;
    }

    private static List<LastTimeItem> createDefaultItems() {
        List<String> titles = Arrays.asList(
                "Cut my nails",
                "Mopped the floor",
                "Changed my toothbrush",
                "Stayed at home the whole day",
                "Had my nose congested",
                "Had painful acne somewhere on my body"
        );

        List<LastTimeItem> items = new ArrayList<>(titles.size());

        for (String title : titles) {
            items.add(LastTimeItem.create(title));
        }

        return items;
    }
}
