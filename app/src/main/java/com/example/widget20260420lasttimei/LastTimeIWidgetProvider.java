package com.example.widget20260420lasttimei;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class LastTimeIWidgetProvider extends AppWidgetProvider {
    private static final String LOG_TAG = "LastTimeIWidget";
    public static final String ACTION_REFRESH = "com.example.widget20260420lasttimei.action.REFRESH";
    public static final String ACTION_MARK_NOW = "com.example.widget20260420lasttimei.action.MARK_NOW";
    public static final String ACTION_OPEN_ADD_ITEM = "com.example.widget20260420lasttimei.action.OPEN_ADD_ITEM";
    public static final String ACTION_OPEN_EDIT_ITEM = "com.example.widget20260420lasttimei.action.OPEN_EDIT_ITEM";
    public static final String EXTRA_ITEM_ID = "com.example.widget20260420lasttimei.extra.ITEM_ID";
    private static final int VISIBLE_ITEM_COUNT = 6;

    private static final int[] ROW_IDS = new int[] {
            R.id.widget_row_1,
            R.id.widget_row_2,
            R.id.widget_row_3,
            R.id.widget_row_4,
            R.id.widget_row_5,
            R.id.widget_row_6
    };

    private static final int[] TITLE_IDS = new int[] {
            R.id.widget_row_1_title,
            R.id.widget_row_2_title,
            R.id.widget_row_3_title,
            R.id.widget_row_4_title,
            R.id.widget_row_5_title,
            R.id.widget_row_6_title
    };

    private static final int[] DAYS_IDS = new int[] {
            R.id.widget_row_1_days,
            R.id.widget_row_2_days,
            R.id.widget_row_3_days,
            R.id.widget_row_4_days,
            R.id.widget_row_5_days,
            R.id.widget_row_6_days
    };

    private static final int[] REFRESH_BUTTON_IDS = new int[] {
            R.id.widget_row_1_refresh,
            R.id.widget_row_2_refresh,
            R.id.widget_row_3_refresh,
            R.id.widget_row_4_refresh,
            R.id.widget_row_5_refresh,
            R.id.widget_row_6_refresh
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
        List<LastTimeItem> items = LastTimeStorage.getItems(context);
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

        int visibleCount = Math.min(items.size(), VISIBLE_ITEM_COUNT);

        for (int index = 0; index < VISIBLE_ITEM_COUNT; index++) {
            if (index >= visibleCount) {
                views.setViewVisibility(ROW_IDS[index], View.GONE);
                continue;
            }

            LastTimeItem item = items.get(index);
            views.setViewVisibility(ROW_IDS[index], View.VISIBLE);
            views.setTextViewText(TITLE_IDS[index], item.getTitle());
            views.setTextViewText(DAYS_IDS[index], LastTimeFormatter.getDayCountLabel(context, item.getLastRefreshedAtMillis()));
            views.setOnClickPendingIntent(TITLE_IDS[index], createOpenAppPendingIntent(context, buildRequestCode(appWidgetId, 300 + index), ACTION_OPEN_EDIT_ITEM, item.getId()));
            views.setOnClickPendingIntent(DAYS_IDS[index], createOpenAppPendingIntent(context, buildRequestCode(appWidgetId, 400 + index), ACTION_OPEN_EDIT_ITEM, item.getId()));
            views.setOnClickPendingIntent(REFRESH_BUTTON_IDS[index], createMarkNowPendingIntent(context, appWidgetId, index, item.getId()));
        }

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
