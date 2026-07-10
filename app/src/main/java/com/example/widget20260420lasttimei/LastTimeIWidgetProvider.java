package com.example.widget20260420lasttimei;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LastTimeIWidgetProvider extends AppWidgetProvider {
    private static final String LOG_TAG = "LastTimeIWidget";
    public static final String ACTION_REFRESH = "com.example.widget20260420lasttimei.action.REFRESH";
    public static final String ACTION_MARK_NOW = "com.example.widget20260420lasttimei.action.MARK_NOW";
    public static final String ACTION_OPEN_ADD_ITEM = "com.example.widget20260420lasttimei.action.OPEN_ADD_ITEM";
    public static final String ACTION_OPEN_EDIT_ITEM = "com.example.widget20260420lasttimei.action.OPEN_EDIT_ITEM";
    public static final String EXTRA_ITEM_ID = "com.example.widget20260420lasttimei.extra.ITEM_ID";
    private static final int SECTION_ITEM_COUNT = 3;
    private static final int LATEST_SECTION_START_INDEX = 0;
    private static final int OLDEST_SECTION_START_INDEX = 3;

    private static final int[] ROW_IDS = new int[] {
            R.id.widget_row_1,
            R.id.widget_row_2,
            R.id.widget_row_3,
            R.id.widget_row_4,
            R.id.widget_row_5,
            R.id.widget_row_6,
            R.id.widget_row_7,
            R.id.widget_row_8,
            R.id.widget_row_9,
            R.id.widget_row_10
    };

    private static final int[] TITLE_IDS = new int[] {
            R.id.widget_row_1_title,
            R.id.widget_row_2_title,
            R.id.widget_row_3_title,
            R.id.widget_row_4_title,
            R.id.widget_row_5_title,
            R.id.widget_row_6_title,
            R.id.widget_row_7_title,
            R.id.widget_row_8_title,
            R.id.widget_row_9_title,
            R.id.widget_row_10_title
    };

    private static final int[] DAYS_IDS = new int[] {
            R.id.widget_row_1_days,
            R.id.widget_row_2_days,
            R.id.widget_row_3_days,
            R.id.widget_row_4_days,
            R.id.widget_row_5_days,
            R.id.widget_row_6_days,
            R.id.widget_row_7_days,
            R.id.widget_row_8_days,
            R.id.widget_row_9_days,
            R.id.widget_row_10_days
    };

    private static final int[] REFRESH_BUTTON_IDS = new int[] {
            R.id.widget_row_1_refresh,
            R.id.widget_row_2_refresh,
            R.id.widget_row_3_refresh,
            R.id.widget_row_4_refresh,
            R.id.widget_row_5_refresh,
            R.id.widget_row_6_refresh,
            R.id.widget_row_7_refresh,
            R.id.widget_row_8_refresh,
            R.id.widget_row_9_refresh,
            R.id.widget_row_10_refresh
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(LOG_TAG, "onUpdate for " + appWidgetIds.length + " widget(s)");
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        Log.d(LOG_TAG, "onReceive action=" + action);

        if (!ACTION_REFRESH.equals(action) && !ACTION_MARK_NOW.equals(action)) {
            return;
        }

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (ACTION_MARK_NOW.equals(action)) {
            String itemId = intent.getStringExtra(EXTRA_ITEM_ID);

            if (itemId != null && LastTimeStorage.markNow(context, itemId)) {
                Log.d(LOG_TAG, "Marked item as today from widget: " + itemId);
            }
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            updateWidgets(context, manager, getWidgetIds(context));
            return;
        }

        updateWidgets(context, manager, new int[] { appWidgetId });
    }

    public static int getInstalledWidgetCount(Context context) {
        return getWidgetIds(context).length;
    }

    public static int requestRefreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] widgetIds = getWidgetIds(context);

        Log.d(LOG_TAG, "requestRefreshAll for " + widgetIds.length + " widget(s)");

        if (widgetIds.length > 0) {
            updateWidgets(context, manager, widgetIds);
        }

        return widgetIds.length;
    }

    private static int[] getWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, LastTimeIWidgetProvider.class);
        return manager.getAppWidgetIds(provider);
    }

    private static void updateWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateSingleWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_last_time_i);
        List<LastTimeItem> items = getItemsByLastUpdated(context, false);
        List<LastTimeItem> latestItems = getSectionItems(items, SECTION_ITEM_COUNT);
        List<LastTimeItem> oldestItems = getOldestItemsExcluding(context, latestItems, SECTION_ITEM_COUNT);
        String updatedAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());

        Log.d(LOG_TAG, "Updating widget id=" + appWidgetId + " with " + items.size() + " tracked item(s) at " + updatedAt);

        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title));
        views.setTextViewText(R.id.widget_summary, buildSummaryText(context, items.size()));
        views.setTextViewText(R.id.widget_timestamp, context.getString(R.string.widget_last_updated, updatedAt));

        if (items.isEmpty()) {
            views.setTextViewText(R.id.widget_empty_state, context.getString(R.string.widget_empty_state));
            views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_empty_state, View.GONE);
        }

        for (int index = 0; index < ROW_IDS.length; index++) {
            views.setViewVisibility(ROW_IDS[index], View.GONE);
        }

        views.setViewVisibility(R.id.widget_latest_section_header, latestItems.isEmpty() ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.widget_oldest_section_header, oldestItems.isEmpty() ? View.GONE : View.VISIBLE);
        bindWidgetRows(context, views, appWidgetId, LATEST_SECTION_START_INDEX, latestItems);
        bindWidgetRows(context, views, appWidgetId, OLDEST_SECTION_START_INDEX, oldestItems);

        int visibleCount = latestItems.size() + oldestItems.size();
        int moreCount = items.size() - visibleCount;

        if (moreCount > 0) {
            views.setViewVisibility(R.id.widget_more_indicator, View.VISIBLE);
            views.setTextViewText(R.id.widget_more_indicator, context.getString(R.string.widget_more_items, moreCount));
        } else {
            views.setViewVisibility(R.id.widget_more_indicator, View.GONE);
        }

        views.setOnClickPendingIntent(R.id.widget_add_button, createOpenAppPendingIntent(context, buildRequestCode(appWidgetId, 10), ACTION_OPEN_ADD_ITEM, null));
        views.setOnClickPendingIntent(R.id.widget_open_app_button, createOpenAppPendingIntent(context, buildRequestCode(appWidgetId, 20), null, null));

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void bindWidgetRows(Context context, RemoteViews views, int appWidgetId, int startIndex, List<LastTimeItem> items) {
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            int rowIndex = startIndex + itemIndex;
            LastTimeItem item = items.get(itemIndex);
            boolean isOverdue = item.isOverdue();
            String dayCountLabel = LastTimeFormatter.getDayCountLabel(context, item.getLastRefreshedAtMillis());
            String displayedDayCount = isOverdue
                    ? context.getString(R.string.widget_overdue_day_count, dayCountLabel)
                    : dayCountLabel;

            views.setViewVisibility(ROW_IDS[rowIndex], View.VISIBLE);
            views.setTextViewText(TITLE_IDS[rowIndex], item.getTitle());
            views.setTextViewText(DAYS_IDS[rowIndex], buildWidgetDetails(context, item, displayedDayCount));
            views.setTextColor(TITLE_IDS[rowIndex], context.getColor(isOverdue ? R.color.widget_overdue : R.color.widget_text_primary));
            views.setTextColor(DAYS_IDS[rowIndex], context.getColor(isOverdue ? R.color.widget_overdue : R.color.widget_text_secondary));
            views.setOnClickPendingIntent(TITLE_IDS[rowIndex], createOpenAppPendingIntent(context, buildRequestCode(appWidgetId, 300 + rowIndex), ACTION_OPEN_EDIT_ITEM, item.getId()));
            views.setOnClickPendingIntent(DAYS_IDS[rowIndex], createOpenAppPendingIntent(context, buildRequestCode(appWidgetId, 400 + rowIndex), ACTION_OPEN_EDIT_ITEM, item.getId()));
            views.setOnClickPendingIntent(REFRESH_BUTTON_IDS[rowIndex], createMarkNowPendingIntent(context, appWidgetId, rowIndex, item.getId()));
        }
    }

    private static CharSequence buildWidgetDetails(Context context, LastTimeItem item, String dayCountLabel) {
        SpannableStringBuilder details = new SpannableStringBuilder(dayCountLabel);

        if (!item.hasTags()) {
            return details;
        }

        int tagsStart = details.length();
        details.append("  ·  ");

        for (int index = 0; index < item.getTags().size(); index++) {
            if (index > 0) {
                details.append("  ");
            }

            details.append('#').append(item.getTags().get(index));
        }

        details.setSpan(
                new ForegroundColorSpan(context.getColor(R.color.widget_text_muted)),
                tagsStart,
                details.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return details;
    }

    private static List<LastTimeItem> getItemsByLastUpdated(Context context, boolean oldestFirst) {
        List<LastTimeItem> items = new ArrayList<>(LastTimeStorage.getActiveItems(context));
        Collections.sort(items, (left, right) -> oldestFirst
                ? Long.compare(left.getLastRefreshedAtMillis(), right.getLastRefreshedAtMillis())
                : Long.compare(right.getLastRefreshedAtMillis(), left.getLastRefreshedAtMillis()));
        return items;
    }

    private static List<LastTimeItem> getSectionItems(List<LastTimeItem> items, int sectionItemCount) {
        return new ArrayList<>(items.subList(0, Math.min(items.size(), sectionItemCount)));
    }

    private static List<LastTimeItem> getOldestItemsExcluding(Context context, List<LastTimeItem> latestItems, int sectionItemCount) {
        Set<String> latestItemIds = new HashSet<>();

        for (LastTimeItem item : latestItems) {
            latestItemIds.add(item.getId());
        }

        List<LastTimeItem> oldestItems = new ArrayList<>();

        for (LastTimeItem item : getItemsByLastUpdated(context, true)) {
            if (latestItemIds.contains(item.getId())) {
                continue;
            }

            oldestItems.add(item);

            if (oldestItems.size() == sectionItemCount) {
                break;
            }
        }

        return oldestItems;
    }

    private static String buildSummaryText(Context context, int totalCount) {
        if (totalCount == 0) {
            return context.getString(R.string.widget_summary_empty);
        }

        return context.getString(R.string.widget_summary_tracking, totalCount);
    }

    private static PendingIntent createMarkNowPendingIntent(Context context, int appWidgetId, int rowIndex, String itemId) {
        Intent markNowIntent = new Intent(context, LastTimeIWidgetProvider.class);
        markNowIntent.setAction(ACTION_MARK_NOW);
        markNowIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        markNowIntent.putExtra(EXTRA_ITEM_ID, itemId);

        return PendingIntent.getBroadcast(
                context,
                buildRequestCode(appWidgetId, 100 + rowIndex),
                markNowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent createOpenAppPendingIntent(Context context, int requestCode, String action, String itemId) {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (action != null) {
            openIntent.setAction(action);
        }

        if (itemId != null) {
            openIntent.putExtra(EXTRA_ITEM_ID, itemId);
        }

        return PendingIntent.getActivity(
                context,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int buildRequestCode(int appWidgetId, int offset) {
        return (appWidgetId * 1000) + offset;
    }
}
