package com.example.widget20260420lasttimei;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAppWidgetManager;
import org.robolectric.shadows.ShadowSystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.time.Instant;

@RunWith(RobolectricTestRunner.class)
// Instrument app calls to System.currentTimeMillis so advancing the test clock also advances expiry.
@Config(sdk = 35, qualifiers = "w411dp-h891dp-mdpi",
        instrumentedPackages = "com.example.widget20260420lasttimei")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetSnoozeTest {
    private Context context;

    @Before
    public void setUp() {
        SystemClock.setCurrentTimeMillis(Instant.parse("2026-09-13T05:00:00Z").toEpochMilli());
        context = RuntimeEnvironment.getApplication();
        save(new JSONArray());
    }

    @Test
    public void legacyItemsDefaultToVisibleAndSnoozePreservesTheirData() throws Exception {
        JSONObject legacy = fixture("wet-tissues", "Buy wet tissues", "2026-08-30");
        legacy.put("tags", new JSONArray().put("shopping"));
        legacy.put("intervalDays", 14);
        save(new JSONArray().put(legacy));
        LastTimeItem before = LastTimeStorage.getItem(context, "wet-tissues");
        assertFalse(before.isWidgetSnoozed());
        long startedAt = SystemClock.uptimeMillis();

        assertTrue(LastTimeStorage.setWidgetSnoozed(context, "wet-tissues", true));
        LastTimeItem snoozed = LastTimeStorage.getItem(context, "wet-tissues");
        assertEquals(startedAt + 604_800_000L, snoozed.getWidgetSnoozedUntilMillis());
        assertTrue(snoozed.isWidgetSnoozed());
        JSONObject expected = before.toJson();
        expected.put("widgetSnoozedUntilMillis", startedAt + 604_800_000L);
        assertEquals(expected.toString(), snoozed.toJson().toString());
        assertEquals(1, LastTimeStorage.getActiveItems(context).size());

        // Editing and history changes must not accidentally clear the snooze.
        assertTrue(LastTimeStorage.updateItemTitle(context, "wet-tissues", "Buy more wet tissues"));
        assertTrue(LastTimeStorage.markNow(context, "wet-tissues"));
        assertEquals(startedAt + 604_800_000L,
                LastTimeStorage.getItem(context, "wet-tissues").getWidgetSnoozedUntilMillis());
        assertTrue(LastTimeStorage.setWidgetSnoozed(context, "wet-tissues", false));
        assertEquals(0L, LastTimeStorage.getItem(context, "wet-tissues").getWidgetSnoozedUntilMillis());
    }

    @Test
    public void expiryIsExactlySevenDaysAndSurvivesReloads() throws Exception {
        save(new JSONArray().put(fixture("item", "Buy wet tissues", "2026-08-30")));
        LastTimeStorage.setWidgetSnoozed(context, "item", true);
        ShadowSystemClock.advanceBy(Duration.ofMillis(604_799_999L));
        assertTrue(LastTimeStorage.getItem(context, "item").isWidgetSnoozed());
        ShadowSystemClock.advanceBy(Duration.ofMillis(1));
        assertFalse(LastTimeStorage.getItem(context, "item").isWidgetSnoozed());
        ShadowSystemClock.advanceBy(Duration.ofDays(1));
        assertFalse(LastTimeStorage.getItem(context, "item").isWidgetSnoozed());
    }

    @Test
    public void widgetRefillsBothSectionsAndBringsExpiredItemsBack() throws Exception {
        JSONArray items = new JSONArray();
        for (int i = 0; i < 10; i++) {
            items.put(fixture("item-" + i, "Item " + i, "2026-08-" + (30 - i)));
        }
        items.put(fixture("health", "Hidden health", "2026-09-01")
                .put("tags", new JSONArray().put("HeAlTh")));
        items.put(fixture("deleted", "Deleted item", "2026-01-01").put("deletedAtMillis", 1));
        save(items);
        LastTimeStorage.setWidgetSnoozed(context, "item-0", true);
        LastTimeStorage.setWidgetSnoozed(context, "item-9", true);

        ShadowAppWidgetManager manager = shadowOf(AppWidgetManager.getInstance(context));
        int id = manager.createWidget(LastTimeIWidgetProvider.class, R.layout.widget_last_time_i);
        View widget = manager.getViewFor(id);
        assertTitles(widget, "Item 1", "Item 2", "Item 3", "Item 4", "Item 8", "Item 7", "Item 6", "Item 5");
        assertEquals("8 tracked", text(widget, R.id.widget_summary));
        assertEquals(View.GONE, widget.findViewById(R.id.widget_more_indicator).getVisibility());

        ShadowSystemClock.advanceBy(Duration.ofDays(7));
        // Android's periodic widget callback, with no Activity launch.
        new LastTimeIWidgetProvider().onUpdate(context, AppWidgetManager.getInstance(context), new int[] {id});
        widget = manager.getViewFor(id);
        assertTitles(widget, "Item 0", "Item 1", "Item 2", "Item 3", "Item 9", "Item 8", "Item 7", "Item 6");
        assertEquals("10 tracked", text(widget, R.id.widget_summary));
        assertEquals("+2 more in app", text(widget, R.id.widget_more_indicator));
        assertEquals(View.VISIBLE, widget.findViewById(R.id.widget_more_indicator).getVisibility());

        try (android.content.res.XmlResourceParser xml = context.getResources().getXml(R.xml.widget_last_time_i_info)) {
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
            assertEquals(1_800_000, xml.getAttributeIntValue(
                    "http://schemas.android.com/apk/res/android", "updatePeriodMillis", 0));
        }
    }

    @Test
    public void everyWidgetSnoozeButtonTargetsItsItemAndRefreshesBothWidgets() throws Exception {
        // Robolectric wraps manifest receivers on SDK 35, hiding their class from explicit-intent matching.
        context.registerReceiver(new LastTimeIWidgetProvider(),
                new IntentFilter(LastTimeIWidgetProvider.ACTION_SNOOZE), Context.RECEIVER_NOT_EXPORTED);
        String[] titles = {"Buy wet tissues", "Water balcony plants", "Wash bath towels", "Vacuum living room",
                "Clean kitchen sink", "Replace water filter", "Wash bed sheets", "Clean air conditioner filters",
                "Mop the floor", "Change toothbrush"};
        JSONArray items = new JSONArray();
        for (int i = 0; i < titles.length; i++) {
            items.put(fixture("item-" + i, titles[i], "2026-08-" + (30 - i))
                    .put("intervalDays", i == 9 ? 7 : 0)
                    .put("tags", new JSONArray().put(i == 0 ? "shopping" : "home")));
        }
        save(items);
        ShadowAppWidgetManager manager = shadowOf(AppWidgetManager.getInstance(context));
        int[] ids = manager.createWidgets(LastTimeIWidgetProvider.class, R.layout.widget_last_time_i, 2);
        int[] buttons = {R.id.widget_row_1_snooze, R.id.widget_row_2_snooze, R.id.widget_row_3_snooze,
                R.id.widget_row_4_snooze, R.id.widget_row_5_snooze, R.id.widget_row_6_snooze,
                R.id.widget_row_7_snooze, R.id.widget_row_8_snooze};
        int[] expectedItems = {0, 1, 2, 3, 9, 8, 7, 6};

        for (int slot = 0; slot < buttons.length; slot++) {
            save(items);
            LastTimeIWidgetProvider.requestRefreshAll(context);
            View widget = manager.getViewFor(ids[0]);
            View button = widget.findViewById(buttons[slot]);
            String itemId = "item-" + expectedItems[slot];
            assertEquals("Snooze " + titles[expectedItems[slot]] + " on the widget for 7 days",
                    button.getContentDescription().toString());
            if (slot == 0) {
                capture(widget, "widget-eight-items");
                assertTrue("Eight rows and footer fit within 560dp", widget.getHeight() <= 560);
                assertTrue(widget.findViewById(R.id.widget_row_4).getBottom()
                        <= widget.findViewById(R.id.widget_oldest_section_header).getTop());
                assertTrue(widget.findViewById(R.id.widget_oldest_section_header).getBottom()
                        <= widget.findViewById(R.id.widget_row_5).getTop());
                assertEquals(12f, ((TextView) widget.findViewById(R.id.widget_row_1_title)).getTextSize(), 0.01f);
                assertEquals(10f, ((TextView) widget.findViewById(R.id.widget_row_1_days)).getTextSize(), 0.01f);
                capture(widget, "widget-eight-items-narrow", 280);
            }
            View.OnClickListener originalClick = shadowOf(button).getOnClickListener();
            assertTrue(button.performClick());
            shadowOf(Looper.getMainLooper()).idle();
            for (LastTimeItem item : LastTimeStorage.getItems(context)) {
                assertEquals(itemId.equals(item.getId()), item.isWidgetSnoozed());
                JSONObject expected = LastTimeItem.fromJson(items.getJSONObject(Integer.parseInt(item.getId().substring(5)))).toJson();
                expected.put("widgetSnoozedUntilMillis", item.getWidgetSnoozedUntilMillis());
                assertEquals(expected.toString(), item.toJson().toString());
            }
            for (int id : ids) {
                assertEquals("9 tracked", text(manager.getViewFor(id), R.id.widget_summary));
                assertEquals("+1 more in app", text(manager.getViewFor(id), R.id.widget_more_indicator));
            }
            if (slot == 0) {
                assertEquals("Water balcony plants", text(manager.getViewFor(ids[0]), R.id.widget_row_1_title));
                capture(manager.getViewFor(ids[0]), "widget-after-snooze");
                // A stale launcher click still belongs to the old item, not its replacement.
                originalClick.onClick(button);
                shadowOf(Looper.getMainLooper()).idle();
                assertFalse(LastTimeStorage.getItem(context, "item-1").isWidgetSnoozed());
                assertTrue(manager.getViewFor(ids[0]).findViewById(R.id.widget_row_1_refresh).performClick());
                shadowOf(Looper.getMainLooper()).idle();
                assertEquals("Today remains a history action", 2,
                        LastTimeStorage.getItem(context, "item-1").getRefreshHistoryDates().size());
            }
        }
    }

    @Test
    public void buttonRefreshesAllWidgetsAndCanUndoWithoutChangingHistory() throws Exception {
        save(new JSONArray().put(fixture("wet-tissues", "Buy wet tissues", "2026-08-30")));
        ShadowAppWidgetManager manager = shadowOf(AppWidgetManager.getInstance(context));
        int[] ids = manager.createWidgets(LastTimeIWidgetProvider.class, R.layout.widget_last_time_i, 2);
        JSONObject before = LastTimeStorage.getItem(context, "wet-tissues").toJson();

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            View row = firstRow(activity);
            assertEquals("Snooze widget for 7 days", text(row, R.id.item_snooze_button));
            capture(row, "snooze-available");
            row.findViewById(R.id.item_snooze_button).performClick();
            for (int id : ids) {
                View widget = manager.getViewFor(id);
                assertEquals(View.VISIBLE, widget.findViewById(R.id.widget_empty_state).getVisibility());
                assertEquals(View.GONE, widget.findViewById(R.id.widget_row_1).getVisibility());
                assertEquals(View.GONE, widget.findViewById(R.id.widget_latest_section_header).getVisibility());
                assertEquals(View.GONE, widget.findViewById(R.id.widget_oldest_section_header).getVisibility());
                assertEquals(View.GONE, widget.findViewById(R.id.widget_more_indicator).getVisibility());
            }
            capture(manager.getViewFor(ids[0]), "widget-all-snoozed");
        }

        // Reopen the app: the persisted state must render an undo action and return date.
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            View row = firstRow(controller.get());
            assertEquals("Unsnooze now", text(row, R.id.item_snooze_button));
            assertEquals(View.VISIBLE, row.findViewById(R.id.item_snoozed_hint).getVisibility());
            assertTrue(text(row, R.id.item_snoozed_hint).startsWith("Snoozed for 7 days, until "));
            capture(row, "snoozed-item");
            row.findViewById(R.id.item_snooze_button).performClick();
            assertEquals(before.toString(), LastTimeStorage.getItem(context, "wet-tissues").toJson().toString());
            for (int id : ids) {
                assertEquals("Buy wet tissues", text(manager.getViewFor(id), R.id.widget_row_1_title));
                assertEquals(View.VISIBLE, manager.getViewFor(id).findViewById(R.id.widget_row_1).getVisibility());
            }

            firstRow(controller.get()).findViewById(R.id.item_snooze_button).performClick();
            row = firstRow(controller.get());
            ShadowSystemClock.advanceBy(Duration.ofDays(7));
            // A still-visible Unsnooze button must not start another snooze after expiry.
            row.findViewById(R.id.item_snooze_button).performClick();
            assertEquals(before.toString(), LastTimeStorage.getItem(context, "wet-tissues").toJson().toString());
            assertEquals("Snooze widget for 7 days", text(firstRow(controller.get()), R.id.item_snooze_button));
        }
    }

    @Test
    public void missingAndDeletedItemsCannotBeSnoozedOrRestoredBySnooze() throws Exception {
        save(new JSONArray().put(fixture("deleted", "Old item", "2026-08-30").put("deletedAtMillis", 42)));
        assertFalse(LastTimeStorage.setWidgetSnoozed(context, "missing", true));
        assertFalse(LastTimeStorage.setWidgetSnoozed(context, "deleted", true));
        assertFalse(LastTimeStorage.setWidgetSnoozed(context, "deleted", false));
        assertTrue(LastTimeStorage.getItem(context, "deleted").isDeleted());
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertEquals(View.GONE, firstRow(controller.get()).findViewById(R.id.item_snooze_button).getVisibility());
        }
    }

    private JSONObject fixture(String id, String title, String date) throws Exception {
        return new JSONObject().put("id", id).put("title", title)
                .put("tags", new JSONArray()).put("refreshHistoryDates", new JSONArray().put(date));
    }

    private void save(JSONArray items) {
        context.getSharedPreferences("last_time_i_storage", Context.MODE_PRIVATE)
                .edit().putString("items_json", items.toString()).commit();
    }

    private View firstRow(MainActivity activity) {
        return ((LinearLayout) activity.findViewById(R.id.tracked_items_container)).getChildAt(0);
    }

    private String text(View root, int id) {
        return ((TextView) root.findViewById(id)).getText().toString();
    }

    private void assertTitles(View widget, String... expected) {
        int[] titles = {R.id.widget_row_1_title, R.id.widget_row_2_title, R.id.widget_row_3_title,
                R.id.widget_row_4_title, R.id.widget_row_5_title, R.id.widget_row_6_title,
                R.id.widget_row_7_title, R.id.widget_row_8_title};
        for (int i = 0; i < titles.length; i++) {
            assertEquals(expected[i], text(widget, titles[i]));
        }
    }

    private void capture(View view, String name) throws Exception {
        capture(view, name, 363);
    }

    private void capture(View view, String name, int width) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        shadowOf(Looper.getMainLooper()).idle();
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File directory = new File("build/reports/snooze");
        directory.mkdirs();
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
}
