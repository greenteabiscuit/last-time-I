package com.example.widget20260420lasttimei;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends Activity {
    private static final String LOG_TAG = "LastTimeIWidget";
    private static final LocalDate HISTORY_START_DATE = LocalDate.of(2010, 1, 1);
    private static final int RECENT_EVENT_COUNT = 6;
    private static final int HISTORY_MONTH_SWIPE_MIN_DISTANCE_DP = 28;
    private static final int HISTORY_MONTH_SWIPE_INTERCEPT_DISTANCE_DP = 8;
    private static final float HISTORY_MONTH_SWIPE_HORIZONTAL_RATIO = 0.65f;
    private static final int HISTORY_MONTH_SLIDE_DURATION_MS = 180;
    private static final int ITEM_CALENDAR_CIRCLE_SIZE_DP = 42;
    private static final int ITEM_CALENDAR_DAY_GAP_DP = 8;
    private static final int ITEM_CALENDAR_WEEKDAY_HEIGHT_DP = 16;
    private static final int ITEM_CALENDAR_WEEKDAY_GAP_DP = 3;
    private static final int SORT_LATEST_UPDATED = 0;
    private static final int SORT_OLDEST_UPDATED = 1;
    private static final int SORT_ALPHABETICAL = 2;

    private TextView statusText;
    private TextView emptyStateText;
    private LinearLayout itemsContainer;
    private final Map<String, Integer> itemCalendarScrollPositions = new HashMap<>();
    private int selectedSortOrder = SORT_LATEST_UPDATED;
    private String trackedItemsSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(LOG_TAG, "MainActivity created");

        statusText = findViewById(R.id.status_text);
        emptyStateText = findViewById(R.id.empty_state_text);
        itemsContainer = findViewById(R.id.tracked_items_container);
        Button addItemButton = findViewById(R.id.add_item_button);
        Button refreshButton = findViewById(R.id.refresh_widget_button);
        Spinner sortSpinner = findViewById(R.id.tracked_items_sort_spinner);
        EditText searchInput = findViewById(R.id.tracked_items_search_input);

        setupSortSpinner(sortSpinner);
        setupSearchInput(searchInput);

        addItemButton.setOnClickListener(view -> showItemEditorDialog(null));

        refreshButton.setOnClickListener(view -> {
            int widgetCount = LastTimeIWidgetProvider.requestRefreshAll(this);
            Log.d(LOG_TAG, "Refresh requested from app for " + widgetCount + " widget(s)");
            List<LastTimeItem> items = LastTimeStorage.getItems(this);
            renderItems(items);
            updateStatusText(widgetCount, getActiveItemCount(items), true);
        });

        List<LastTimeItem> items = LastTimeStorage.getItems(this);
        renderItems(items);
        updateStatusText(LastTimeIWidgetProvider.getInstalledWidgetCount(this), getActiveItemCount(items), false);
        handleLaunchIntent(getIntent());
    }

    private void setupSortSpinner(Spinner sortSpinner) {
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[] {
                        getString(R.string.sort_latest_updated),
                        getString(R.string.sort_oldest_updated),
                        getString(R.string.sort_alphabetical)
                }
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setSelection(selectedSortOrder);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selectedSortOrder == position) {
                    return;
                }

                selectedSortOrder = position;
                renderItems(LastTimeStorage.getItems(MainActivity.this));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Keep the current sort order.
            }
        });
    }

    private void setupSearchInput(EditText searchInput) {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // No-op.
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                trackedItemsSearchQuery = text == null ? "" : text.toString().trim();
                renderItems(LastTimeStorage.getItems(MainActivity.this));
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // No-op.
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        List<LastTimeItem> items = LastTimeStorage.getItems(this);
        renderItems(items);
        updateStatusText(LastTimeIWidgetProvider.getInstalledWidgetCount(this), getActiveItemCount(items), false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void updateStatusText(int widgetCount, int itemCount, boolean justRefreshed) {
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());

        if (justRefreshed) {
            statusText.setText(getString(R.string.status_refreshed, widgetCount, itemCount, time));
            return;
        }

        if (widgetCount == 0) {
            statusText.setText(getString(R.string.status_no_widget, itemCount, time));
            return;
        }

        statusText.setText(getString(R.string.status_widget_ready, itemCount, widgetCount, time));
    }

    private void renderItems(List<LastTimeItem> items) {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        List<LastTimeItem> filteredItems = getFilteredAppItems(items);
        List<LastTimeItem> sortedItems = getSortedAppItems(filteredItems);
        itemsContainer.removeAllViews();

        if (sortedItems.isEmpty()) {
            emptyStateText.setText(trackedItemsSearchQuery.isEmpty()
                    ? R.string.empty_tracked_items_state
                    : R.string.empty_tracked_items_search_state);
            emptyStateText.setVisibility(View.VISIBLE);
            return;
        }

        emptyStateText.setVisibility(View.GONE);

        for (LastTimeItem item : sortedItems) {
            View row = layoutInflater.inflate(R.layout.last_time_item_row, itemsContainer, false);
            TextView titleView = row.findViewById(R.id.item_title);
            TextView summaryView = row.findViewById(R.id.item_summary);
            TextView tagView = row.findViewById(R.id.item_tag);
            TextView calendarMonthLabel = row.findViewById(R.id.item_calendar_month_label);
            HorizontalScrollView calendarScrollView = row.findViewById(R.id.item_calendar_scroll_view);
            LinearLayout calendarContainer = row.findViewById(R.id.item_calendar_container);
            Button historyButton = row.findViewById(R.id.item_history_button);
            Button editButton = row.findViewById(R.id.item_edit_button);
            Button deleteButton = row.findViewById(R.id.item_delete_button);

            boolean isDeleted = item.isDeleted();
            titleView.setText(item.getTitle());
            updateItemStatusViews(titleView, summaryView, item);
            tagView.setText(formatTags(item.getTags()));
            tagView.setTextColor(getColor(R.color.widget_text_muted));
            tagView.setVisibility(item.hasTags() ? View.VISIBLE : View.GONE);
            renderItemCalendarStrip(
                    calendarMonthLabel,
                    calendarScrollView,
                    calendarContainer,
                    item,
                    () -> updateItemStatusViews(
                            titleView,
                            summaryView,
                            LastTimeStorage.getItem(MainActivity.this, item.getId())
                    )
            );

            View.OnClickListener editClickListener = view -> showItemEditorDialog(item.getId());
            row.setEnabled(!isDeleted);
            row.setAlpha(isDeleted ? 0.55f : 1f);
            row.setOnClickListener(isDeleted ? null : editClickListener);
            titleView.setOnClickListener(isDeleted ? null : editClickListener);
            summaryView.setOnClickListener(isDeleted ? null : editClickListener);
            tagView.setOnClickListener(isDeleted ? null : editClickListener);
            editButton.setEnabled(!isDeleted);
            editButton.setOnClickListener(isDeleted ? null : editClickListener);
            historyButton.setEnabled(!isDeleted);
            deleteButton.setText(isDeleted ? R.string.restore_item_button : R.string.delete_item_button);

            historyButton.setOnClickListener(view -> showHistoryDialog(item.getId()));
            deleteButton.setOnClickListener(view -> {
                if (isDeleted) {
                    restoreItem(item.getId());
                    return;
                }

                confirmDeleteItem(item.getId());
            });
            itemsContainer.addView(row);
        }
    }

    private void renderItemCalendarStrip(
            TextView monthLabel,
            HorizontalScrollView scrollView,
            LinearLayout container,
            LastTimeItem item,
            Runnable onDateToggled
    ) {
        container.removeAllViews();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        ItemCalendarStripView stripView = new ItemCalendarStripView(item, today, scrollView, onDateToggled);
        container.addView(stripView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        monthLabel.setText(formatItemCalendarMonth(today));

        int savedScrollPosition = itemCalendarScrollPositions.getOrDefault(item.getId(), -1);
        scrollView.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            itemCalendarScrollPositions.put(item.getId(), scrollX);
            updateItemCalendarMonthLabel(monthLabel, stripView, scrollView, scrollX);
            stripView.invalidate();
        });
        scrollView.post(() -> {
            if (savedScrollPosition >= 0) {
                scrollView.scrollTo(savedScrollPosition, 0);
            } else {
                scrollView.scrollTo(stripView.getWidth(), 0);
            }

            updateItemCalendarMonthLabel(monthLabel, stripView, scrollView, scrollView.getScrollX());
        });
    }

    private void updateItemCalendarMonthLabel(
            TextView monthLabel,
            ItemCalendarStripView stripView,
            HorizontalScrollView scrollView,
            int scrollX
    ) {
        float viewportCenterX = scrollX + (scrollView.getWidth() / 2f);
        monthLabel.setText(formatItemCalendarMonth(stripView.getDateAtContentX(viewportCenterX)));
    }

    private String formatItemCalendarMonth(LocalDate date) {
        return getString(
                R.string.item_calendar_month_label,
                date.getYear(),
                date.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
        );
    }

    private final class ItemCalendarStripView extends View {
        private final LocalDate today;
        private final String itemId;
        private final Set<LocalDate> recordedDates;
        private final HorizontalScrollView scrollView;
        private final Runnable onDateToggled;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF circleBounds = new RectF();
        private final int circleSize;
        private final int cellWidth;
        private final int weekdayHeight;
        private final int weekdayGap;
        private final int dayCount;
        private final int touchSlop;
        private float touchDownX;
        private float touchDownY;
        private LocalDate pendingClickDate;

        private ItemCalendarStripView(
                LastTimeItem item,
                LocalDate today,
                HorizontalScrollView scrollView,
                Runnable onDateToggled
        ) {
            super(MainActivity.this);
            this.today = today;
            this.scrollView = scrollView;
            this.onDateToggled = onDateToggled;
            itemId = item.getId();
            recordedDates = new HashSet<>(item.getRefreshHistoryDates());
            circleSize = dpToPx(ITEM_CALENDAR_CIRCLE_SIZE_DP);
            cellWidth = circleSize + dpToPx(ITEM_CALENDAR_DAY_GAP_DP);
            weekdayHeight = dpToPx(ITEM_CALENDAR_WEEKDAY_HEIGHT_DP);
            weekdayGap = dpToPx(ITEM_CALENDAR_WEEKDAY_GAP_DP);
            dayCount = (int) ChronoUnit.DAYS.between(HISTORY_START_DATE, today) + 1;
            touchSlop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
            setClickable(!item.isDeleted());
            setEnabled(!item.isDeleted());
            setFocusable(!item.isDeleted());
            setContentDescription(getString(
                    R.string.item_calendar_strip_description,
                    LastTimeFormatter.getDateLabel(HISTORY_START_DATE),
                    LastTimeFormatter.getDateLabel(today)
            ));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int desiredWidth = (dayCount * cellWidth) - dpToPx(ITEM_CALENDAR_DAY_GAP_DP);
            int desiredHeight = weekdayHeight + weekdayGap + circleSize;
            setMeasuredDimension(
                    resolveSize(desiredWidth, widthMeasureSpec),
                    resolveSize(desiredHeight, heightMeasureSpec)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int visibleLeft = scrollView.getScrollX();
            int visibleRight = visibleLeft + scrollView.getWidth();
            int firstVisibleDay = Math.max(0, (visibleLeft / cellWidth) - 1);
            int lastVisibleDay = Math.min(dayCount - 1, (visibleRight / cellWidth) + 1);
            float circleRadius = circleSize / 2f;
            float circleCenterY = weekdayHeight + weekdayGap + circleRadius;
            paint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 11f);
            float weekdayBaseline = getCenteredTextBaseline(weekdayHeight);
            paint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 13f);
            float dayBaseline = circleCenterY + getTextCenterOffset();

            for (int dayIndex = firstVisibleDay; dayIndex <= lastVisibleDay; dayIndex++) {
                LocalDate date = HISTORY_START_DATE.plusDays(dayIndex);
                boolean isRecorded = recordedDates.contains(date);
                boolean isToday = date.equals(today);
                float circleCenterX = (dayIndex * cellWidth) + circleRadius;

                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 11f);
                paint.setColor(getColor(isToday ? R.color.widget_accent : R.color.widget_text_muted));
                canvas.drawText(
                        date.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        circleCenterX,
                        weekdayBaseline,
                        paint
                );

                circleBounds.set(
                        circleCenterX - circleRadius,
                        circleCenterY - circleRadius,
                        circleCenterX + circleRadius,
                        circleCenterY + circleRadius
                );
                paint.setColor(getColor(isRecorded ? R.color.widget_accent : android.R.color.white));
                canvas.drawOval(circleBounds, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dpToPx(isToday ? 2 : 1));
                paint.setColor(getColor(isRecorded || isToday ? R.color.widget_accent : R.color.widget_border));
                canvas.drawOval(circleBounds, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 13f);
                paint.setColor(getColor(isRecorded ? android.R.color.white : R.color.widget_text_secondary));
                canvas.drawText(String.valueOf(date.getDayOfMonth()), circleCenterX, dayBaseline, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) {
                return false;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchDownX = event.getX();
                touchDownY = event.getY();
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                boolean isTap = Math.abs(event.getX() - touchDownX) <= touchSlop
                        && Math.abs(event.getY() - touchDownY) <= touchSlop;
                pendingClickDate = isTap ? getCircleDateAt(event.getX(), event.getY()) : null;
                return pendingClickDate != null && performClick();
            }

            return event.getActionMasked() != MotionEvent.ACTION_CANCEL;
        }

        @Override
        public boolean performClick() {
            super.performClick();

            if (pendingClickDate == null) {
                return false;
            }

            LocalDate clickedDate = pendingClickDate;
            pendingClickDate = null;

            if (LastTimeStorage.toggleRefreshDay(MainActivity.this, itemId, clickedDate)) {
                Log.d(LOG_TAG, "Toggled item history day from calendar strip: " + itemId + " on " + clickedDate);
                if (!recordedDates.remove(clickedDate)) {
                    recordedDates.add(clickedDate);
                }
                invalidate();
                onDateToggled.run();
                LastTimeIWidgetProvider.requestRefreshAll(MainActivity.this);
                return true;
            }

            return false;
        }

        private LocalDate getCircleDateAt(float x, float y) {
            int dayIndex = Math.min(dayCount - 1, Math.max(0, (int) (x / cellWidth)));
            float circleRadius = circleSize / 2f;
            float circleCenterX = (dayIndex * cellWidth) + circleRadius;
            float circleCenterY = weekdayHeight + weekdayGap + circleRadius;
            float xDistance = x - circleCenterX;
            float yDistance = y - circleCenterY;

            if ((xDistance * xDistance) + (yDistance * yDistance) > circleRadius * circleRadius) {
                return null;
            }

            return HISTORY_START_DATE.plusDays(dayIndex);
        }

        private LocalDate getDateAtContentX(float x) {
            int dayIndex = Math.min(dayCount - 1, Math.max(0, (int) (x / cellWidth)));
            return HISTORY_START_DATE.plusDays(dayIndex);
        }

        private float getCenteredTextBaseline(float areaHeight) {
            return (areaHeight / 2f) + getTextCenterOffset();
        }

        private float getTextCenterOffset() {
            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            return -((fontMetrics.ascent + fontMetrics.descent) / 2f);
        }
    }

    private void updateItemStatusViews(TextView titleView, TextView summaryView, LastTimeItem item) {
        if (item == null) {
            return;
        }

        boolean isOverdue = item.isOverdue();
        boolean isDeleted = item.isDeleted();
        String summary = LastTimeFormatter.getAppSummary(this, item.getLastRefreshedAtMillis());
        summaryView.setText(getItemSummary(item, summary, isOverdue, isDeleted));
        titleView.setTextColor(getColor(getItemTitleColor(isOverdue, isDeleted)));
        summaryView.setTextColor(getColor(getItemSummaryColor(isOverdue, isDeleted)));
    }

    private String getItemSummary(LastTimeItem item, String summary, boolean isOverdue, boolean isDeleted) {
        if (isDeleted) {
            return getString(
                    R.string.item_deleted_summary,
                    LastTimeFormatter.getDateLabel(item.getDeletedAtMillis()),
                    summary
            );
        }

        return isOverdue ? getString(R.string.item_overdue_summary, summary) : summary;
    }

    private int getItemTitleColor(boolean isOverdue, boolean isDeleted) {
        if (isDeleted) {
            return R.color.widget_text_muted;
        }

        return isOverdue ? R.color.widget_overdue : R.color.widget_text_primary;
    }

    private int getItemSummaryColor(boolean isOverdue, boolean isDeleted) {
        if (isDeleted) {
            return R.color.widget_text_muted;
        }

        return isOverdue ? R.color.widget_overdue : R.color.widget_text_secondary;
    }

    private List<LastTimeItem> getFilteredAppItems(List<LastTimeItem> items) {
        if (trackedItemsSearchQuery.isEmpty()) {
            return new ArrayList<>(items);
        }

        String normalizedQuery = trackedItemsSearchQuery.toLowerCase(Locale.getDefault());
        List<LastTimeItem> filteredItems = new ArrayList<>();

        for (LastTimeItem item : items) {
            if (item.getTitle().toLowerCase(Locale.getDefault()).contains(normalizedQuery)
                    || tagsContainQuery(item.getTags(), normalizedQuery)) {
                filteredItems.add(item);
            }
        }

        return filteredItems;
    }

    private boolean tagsContainQuery(List<String> tags, String normalizedQuery) {
        for (String tag : tags) {
            if (tag.toLowerCase(Locale.getDefault()).contains(normalizedQuery)) {
                return true;
            }
        }

        return false;
    }

    private String formatTags(List<String> tags) {
        List<String> labels = new ArrayList<>(tags.size());

        for (String tag : tags) {
            labels.add(getString(R.string.item_tag_label, tag));
        }

        return String.join("  ", labels);
    }

    private List<LastTimeItem> getSortedAppItems(List<LastTimeItem> items) {
        List<LastTimeItem> sortedItems = new ArrayList<>(items);

        if (selectedSortOrder == SORT_OLDEST_UPDATED) {
            Collections.sort(sortedItems, (left, right) -> compareDeletedThen(left, right, compareByLastUpdated(left, right, true)));
            return sortedItems;
        }

        if (selectedSortOrder == SORT_ALPHABETICAL) {
            Collections.sort(sortedItems, (left, right) -> compareDeletedThen(left, right, compareByTitle(left, right)));
            return sortedItems;
        }

        Collections.sort(sortedItems, (left, right) -> compareDeletedThen(left, right, compareByLastUpdated(left, right, false)));
        return sortedItems;
    }

    private int compareDeletedThen(LastTimeItem left, LastTimeItem right, int activeComparison) {
        if (left.isDeleted() != right.isDeleted()) {
            return left.isDeleted() ? 1 : -1;
        }

        return activeComparison;
    }

    private int compareByLastUpdated(LastTimeItem left, LastTimeItem right, boolean oldestFirst) {
        int lastUpdatedComparison = oldestFirst
                ? Long.compare(left.getLastRefreshedAtMillis(), right.getLastRefreshedAtMillis())
                : Long.compare(right.getLastRefreshedAtMillis(), left.getLastRefreshedAtMillis());

        if (lastUpdatedComparison != 0) {
            return lastUpdatedComparison;
        }

        return compareByTitle(left, right);
    }

    private int compareByTitle(LastTimeItem left, LastTimeItem right) {
        int titleComparison = String.CASE_INSENSITIVE_ORDER.compare(left.getTitle(), right.getTitle());

        if (titleComparison != 0) {
            return titleComparison;
        }

        titleComparison = left.getTitle().compareTo(right.getTitle());

        if (titleComparison != 0) {
            return titleComparison;
        }

        return left.getId().compareTo(right.getId());
    }

    private int getActiveItemCount(List<LastTimeItem> items) {
        int activeItemCount = 0;

        for (LastTimeItem item : items) {
            if (!item.isDeleted()) {
                activeItemCount++;
            }
        }

        return activeItemCount;
    }

    private void showItemEditorDialog(String itemId) {
        LastTimeItem existingItem = itemId == null ? null : LastTimeStorage.getItem(this, itemId);

        if (itemId != null && existingItem == null) {
            Log.d(LOG_TAG, "Tried to edit missing item " + itemId);
            syncItemsAndWidget();
            return;
        }

        if (existingItem != null && existingItem.isDeleted()) {
            Log.d(LOG_TAG, "Tried to edit soft-deleted item " + itemId);
            syncItemsAndWidget();
            return;
        }

        EditText titleInput = new EditText(this);
        titleInput.setHint(R.string.item_dialog_hint);
        titleInput.setSingleLine();

        List<String> editorTags = existingItem == null
                ? new ArrayList<>()
                : new ArrayList<>(existingItem.getTags());
        LinearLayout tagsEditor = createTagsEditor(editorTags, getStoredTagSuggestions());
        EditText pendingTagInput = (EditText) tagsEditor.getTag();

        EditText intervalInput = new EditText(this);
        intervalInput.setHint(R.string.item_interval_hint);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalInput.setSingleLine();

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.addView(titleInput);

        LinearLayout.LayoutParams tagsLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tagsLayoutParams.topMargin = dpToPx(8);
        dialogContent.addView(tagsEditor, tagsLayoutParams);

        LinearLayout.LayoutParams intervalLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        intervalLayoutParams.topMargin = dpToPx(8);
        dialogContent.addView(intervalInput, intervalLayoutParams);

        if (existingItem != null) {
            titleInput.setText(existingItem.getTitle());
            titleInput.setSelection(titleInput.getText().length());

            if (existingItem.getIntervalDays() > 0) {
                intervalInput.setText(String.valueOf(existingItem.getIntervalDays()));
                intervalInput.setSelection(intervalInput.getText().length());
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existingItem == null ? R.string.dialog_add_title : R.string.dialog_edit_title)
                .setView(dialogContent)
                .setPositiveButton(existingItem == null ? R.string.add_item_button : R.string.save_item_button, null)
                .setNegativeButton(android.R.string.cancel, null);

        if (existingItem != null) {
            builder.setNeutralButton(R.string.delete_item_button, null);
        }

        AlertDialog dialog = builder.create();
        LastTimeItem dialogItem = existingItem;

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                String title = titleInput.getText().toString().trim();

                if (title.isEmpty()) {
                    titleInput.setError(getString(R.string.item_title_required));
                    return;
                }

                int intervalDays = getIntervalDaysFromInput(intervalInput);

                if (intervalDays < 0) {
                    intervalInput.setError(getString(R.string.item_interval_invalid));
                    return;
                }

                addTagsIfNew(editorTags, pendingTagInput.getText().toString());

                if (dialogItem == null) {
                    LastTimeStorage.addItem(this, title, intervalDays, editorTags);
                    Log.d(LOG_TAG, "Tracked item added from app dialog");
                } else {
                    LastTimeStorage.updateItem(this, dialogItem.getId(), title, intervalDays, editorTags);
                    Log.d(LOG_TAG, "Tracked item updated from app dialog: " + dialogItem.getId());
                }

                dialog.dismiss();
                syncItemsAndWidget();
            });

            if (dialogItem != null) {
                Button deleteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                deleteButton.setOnClickListener(view -> {
                    dialog.dismiss();
                    confirmDeleteItem(dialogItem.getId());
                });
            }
        });

        dialog.show();
    }

    private List<String> getStoredTagSuggestions() {
        Set<String> storedTags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (LastTimeItem item : LastTimeStorage.getItems(this)) {
            storedTags.addAll(item.getTags());
        }

        return new ArrayList<>(storedTags);
    }

    private LinearLayout createTagsEditor(List<String> tags, List<String> tagSuggestions) {
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(R.string.item_tags_label);
        label.setTextColor(getColor(R.color.widget_text_secondary));
        label.setTextSize(14f);
        editor.addView(label);

        HorizontalScrollView tagsScrollView = new HorizontalScrollView(this);
        tagsScrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout tagsContainer = new LinearLayout(this);
        tagsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tagsContainer.setGravity(Gravity.CENTER_VERTICAL);
        tagsScrollView.addView(tagsContainer);
        LinearLayout.LayoutParams tagsScrollLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tagsScrollLayoutParams.topMargin = dpToPx(6);
        editor.addView(tagsScrollView, tagsScrollLayoutParams);

        LinearLayout addTagRow = new LinearLayout(this);
        addTagRow.setOrientation(LinearLayout.HORIZONTAL);
        addTagRow.setGravity(Gravity.CENTER_VERTICAL);
        AutoCompleteTextView tagInput = new AutoCompleteTextView(this);
        tagInput.setHint(R.string.item_tag_add_hint);
        tagInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        tagInput.setSingleLine();
        tagInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        tagInput.setThreshold(0);
        tagInput.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                tagSuggestions
        ));
        tagInput.setOnClickListener(view -> tagInput.showDropDown());
        editor.setTag(tagInput);
        addTagRow.addView(tagInput, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        Button addTagButton = new Button(this);
        addTagButton.setText(R.string.item_tag_add_button);
        addTagRow.addView(addTagButton);
        LinearLayout.LayoutParams addTagRowLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        addTagRowLayoutParams.topMargin = dpToPx(2);
        editor.addView(addTagRow, addTagRowLayoutParams);

        Runnable addTag = () -> {
            addTagsIfNew(tags, tagInput.getText().toString());
            tagInput.setText("");
            renderTagEditorTags(tagsContainer, tagsScrollView, tags);
        };

        addTagButton.setOnClickListener(view -> addTag.run());
        tagInput.setOnItemClickListener((parent, view, position, id) -> addTag.run());
        tagInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }

            addTag.run();
            return true;
        });
        renderTagEditorTags(tagsContainer, tagsScrollView, tags);
        return editor;
    }

    private void addTagsIfNew(List<String> tags, String rawTags) {
        for (String enteredTag : LastTimeItem.parseTags(rawTags)) {
            boolean alreadyAdded = false;

            for (String tag : tags) {
                if (tag.equalsIgnoreCase(enteredTag)) {
                    alreadyAdded = true;
                    break;
                }
            }

            if (!alreadyAdded) {
                tags.add(enteredTag);
            }
        }
    }

    private void renderTagEditorTags(
            LinearLayout tagsContainer,
            HorizontalScrollView tagsScrollView,
            List<String> tags
    ) {
        tagsContainer.removeAllViews();

        for (String tag : new ArrayList<>(tags)) {
            TextView tagChip = new TextView(this);
            tagChip.setText(getString(R.string.item_tag_remove_label, tag));
            tagChip.setTextColor(getColor(R.color.widget_text_secondary));
            tagChip.setTextSize(14f);
            tagChip.setGravity(Gravity.CENTER);
            tagChip.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

            GradientDrawable background = new GradientDrawable();
            background.setColor(getColor(R.color.tag_background));
            background.setCornerRadius(dpToPx(18));
            tagChip.setBackground(background);
            tagChip.setContentDescription(getString(R.string.item_tag_remove_description, tag));
            tagChip.setOnClickListener(view -> {
                tags.remove(tag);
                renderTagEditorTags(tagsContainer, tagsScrollView, tags);
            });

            LinearLayout.LayoutParams chipLayoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            chipLayoutParams.setMarginEnd(dpToPx(8));
            tagsContainer.addView(tagChip, chipLayoutParams);
        }

        tagsScrollView.setVisibility(tags.isEmpty() ? View.GONE : View.VISIBLE);
        tagsScrollView.post(() -> tagsScrollView.fullScroll(View.FOCUS_RIGHT));
    }

    private int getIntervalDaysFromInput(EditText intervalInput) {
        String rawInterval = intervalInput.getText().toString().trim();

        if (rawInterval.isEmpty()) {
            return 0;
        }

        try {
            long intervalDays = Long.parseLong(rawInterval);

            if (intervalDays < 0L || intervalDays > Integer.MAX_VALUE) {
                return -1;
            }

            return (int) intervalDays;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void showHistoryDialog(String itemId) {
        LastTimeItem item = LastTimeStorage.getItem(this, itemId);

        if (item == null) {
            Log.d(LOG_TAG, "Tried to open history for missing item " + itemId);
            syncItemsAndWidget();
            return;
        }

        View historyView = LayoutInflater.from(this).inflate(R.layout.dialog_item_history, null, false);
        final LocalDate[] visibleMonthStart = new LocalDate[] { LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1) };
        final int[] pendingSlideDirection = new int[] { 0 };
        final Runnable[] refreshHistoryDialog = new Runnable[1];
        refreshHistoryDialog[0] = () -> bindHistoryDialog(itemId, historyView, visibleMonthStart, pendingSlideDirection, refreshHistoryDialog[0]);
        refreshHistoryDialog[0].run();

        new AlertDialog.Builder(this)
                .setTitle(R.string.history_dialog_title)
                .setView(historyView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void bindHistoryDialog(
            String itemId,
            View historyView,
            LocalDate[] visibleMonthStart,
            int[] pendingSlideDirection,
            Runnable refreshAction
    ) {
        LastTimeItem item = LastTimeStorage.getItem(this, itemId);

        if (item == null) {
            return;
        }

        TextView titleView = historyView.findViewById(R.id.history_item_title);
        TextView summaryView = historyView.findViewById(R.id.history_item_summary);
        Button previousMonthButton = historyView.findViewById(R.id.history_previous_month_button);
        TextView monthLabelView = historyView.findViewById(R.id.history_calendar_month_label);
        Button nextMonthButton = historyView.findViewById(R.id.history_next_month_button);
        LinearLayout weekdayHeaderContainer = historyView.findViewById(R.id.history_weekday_header_container);
        LinearLayout calendarContainer = historyView.findViewById(R.id.history_calendar_container);
        TextView calendarCaptionView = historyView.findViewById(R.id.history_calendar_caption);
        LinearLayout recentEventsContainer = historyView.findViewById(R.id.history_recent_events_container);

        List<LocalDate> refreshHistoryDates = item.getRefreshHistoryDates();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate minimumMonthStart = HISTORY_START_DATE.withDayOfMonth(1);
        LocalDate currentMonthStart = today.withDayOfMonth(1);

        if (visibleMonthStart[0].isBefore(minimumMonthStart)) {
            visibleMonthStart[0] = minimumMonthStart;
        } else if (visibleMonthStart[0].isAfter(currentMonthStart)) {
            visibleMonthStart[0] = currentMonthStart;
        }

        titleView.setText(item.getTitle());
        summaryView.setText(getString(
                R.string.history_dialog_summary,
                refreshHistoryDates.size(),
                LastTimeFormatter.getAppSummary(this, item.getLastRefreshedAtMillis())
        ));

        monthLabelView.setText(getMonthLabel(visibleMonthStart[0]));
        previousMonthButton.setEnabled(visibleMonthStart[0].isAfter(minimumMonthStart));
        previousMonthButton.setOnClickListener(view -> showPreviousHistoryMonth(visibleMonthStart, minimumMonthStart, pendingSlideDirection, refreshAction));
        nextMonthButton.setEnabled(visibleMonthStart[0].isBefore(currentMonthStart));
        nextMonthButton.setOnClickListener(view -> showNextHistoryMonth(visibleMonthStart, currentMonthStart, pendingSlideDirection, refreshAction));

        View.OnTouchListener monthSwipeListener = buildHistoryMonthSwipeListener(
                visibleMonthStart,
                minimumMonthStart,
                currentMonthStart,
                pendingSlideDirection,
                refreshAction
        );

        renderWeekdayHeader(weekdayHeaderContainer);
        renderHistoryCalendar(calendarContainer, itemId, refreshHistoryDates, visibleMonthStart[0], HISTORY_START_DATE, today, refreshAction);
        applyHistoryMonthSwipeListener(weekdayHeaderContainer, monthSwipeListener);
        applyHistoryMonthSwipeListener(calendarContainer, monthSwipeListener);
        animateHistoryMonthChange(monthLabelView, weekdayHeaderContainer, calendarContainer, pendingSlideDirection);
        calendarCaptionView.setText(getString(
                R.string.history_calendar_caption,
                LastTimeFormatter.getDateLabel(HISTORY_START_DATE),
                LastTimeFormatter.getDateLabel(today)
        ));
        renderRecentEvents(recentEventsContainer, refreshHistoryDates);
    }

    private View.OnTouchListener buildHistoryMonthSwipeListener(
            LocalDate[] visibleMonthStart,
            LocalDate minimumMonthStart,
            LocalDate currentMonthStart,
            int[] pendingSlideDirection,
            Runnable refreshAction
    ) {
        float minimumSwipeDistance = dpToPx(HISTORY_MONTH_SWIPE_MIN_DISTANCE_DP);
        float interceptDistance = dpToPx(HISTORY_MONTH_SWIPE_INTERCEPT_DISTANCE_DP);
        float[] touchStartX = new float[1];
        float[] touchStartY = new float[1];
        boolean[] isHorizontalSwipe = new boolean[1];

        return (view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchStartX[0] = event.getX();
                touchStartY[0] = event.getY();
                isHorizontalSwipe[0] = false;
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float deltaX = event.getX() - touchStartX[0];
                float deltaY = event.getY() - touchStartY[0];

                if (isMostlyHorizontalSwipe(deltaX, deltaY, interceptDistance)) {
                    isHorizontalSwipe[0] = true;
                    setParentInterceptAllowed(view, false);
                }

                return true;
            }

            if (event.getActionMasked() != MotionEvent.ACTION_UP) {
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    isHorizontalSwipe[0] = false;
                    setParentInterceptAllowed(view, true);
                }

                return false;
            }

            float deltaX = event.getX() - touchStartX[0];
            float deltaY = event.getY() - touchStartY[0];

            if (!isHorizontalSwipe[0]
                    || Math.abs(deltaX) < minimumSwipeDistance
                    || !isMostlyHorizontalSwipe(deltaX, deltaY, minimumSwipeDistance)) {
                setParentInterceptAllowed(view, true);
                view.performClick();
                return true;
            }

            setParentInterceptAllowed(view, true);

            if (deltaX < 0) {
                return showNextHistoryMonth(visibleMonthStart, currentMonthStart, pendingSlideDirection, refreshAction);
            }

            return showPreviousHistoryMonth(visibleMonthStart, minimumMonthStart, pendingSlideDirection, refreshAction);
        };
    }

    private boolean isMostlyHorizontalSwipe(float deltaX, float deltaY, float minimumDistance) {
        return Math.abs(deltaX) >= minimumDistance
                && Math.abs(deltaX) > Math.abs(deltaY) * HISTORY_MONTH_SWIPE_HORIZONTAL_RATIO;
    }

    private void animateHistoryMonthChange(
            TextView monthLabelView,
            LinearLayout weekdayHeaderContainer,
            LinearLayout calendarContainer,
            int[] pendingSlideDirection
    ) {
        if (pendingSlideDirection[0] == 0) {
            return;
        }

        int slideDirection = pendingSlideDirection[0];
        pendingSlideDirection[0] = 0;

        animateHistoryMonthView(monthLabelView, slideDirection);
        animateHistoryMonthView(weekdayHeaderContainer, slideDirection);
        animateHistoryMonthView(calendarContainer, slideDirection);
    }

    private void animateHistoryMonthView(View view, int slideDirection) {
        view.animate().cancel();
        view.post(() -> {
            float slideDistance = view.getWidth() > 0 ? view.getWidth() : dpToPx(240);

            view.setAlpha(0.35f);
            view.setTranslationX(slideDirection * slideDistance);
            view.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(HISTORY_MONTH_SLIDE_DURATION_MS)
                    .start();
        });
    }

    private void setParentInterceptAllowed(View view, boolean isAllowed) {
        ViewParent parent = view.getParent();

        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!isAllowed);
            parent = parent.getParent();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void applyHistoryMonthSwipeListener(View view, View.OnTouchListener monthSwipeListener) {
        view.setOnTouchListener(monthSwipeListener);

        if (!(view instanceof LinearLayout)) {
            return;
        }

        LinearLayout container = (LinearLayout) view;

        for (int childIndex = 0; childIndex < container.getChildCount(); childIndex++) {
            applyHistoryMonthSwipeListener(container.getChildAt(childIndex), monthSwipeListener);
        }
    }

    private boolean showPreviousHistoryMonth(
            LocalDate[] visibleMonthStart,
            LocalDate minimumMonthStart,
            int[] pendingSlideDirection,
            Runnable refreshAction
    ) {
        if (!visibleMonthStart[0].isAfter(minimumMonthStart)) {
            return false;
        }

        visibleMonthStart[0] = visibleMonthStart[0].minusMonths(1);
        pendingSlideDirection[0] = -1;
        refreshAction.run();
        return true;
    }

    private boolean showNextHistoryMonth(
            LocalDate[] visibleMonthStart,
            LocalDate currentMonthStart,
            int[] pendingSlideDirection,
            Runnable refreshAction
    ) {
        if (!visibleMonthStart[0].isBefore(currentMonthStart)) {
            return false;
        }

        visibleMonthStart[0] = visibleMonthStart[0].plusMonths(1);
        pendingSlideDirection[0] = 1;
        refreshAction.run();
        return true;
    }

    private void renderWeekdayHeader(LinearLayout container) {
        container.removeAllViews();
        DayOfWeek[] days = new DayOfWeek[] {
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
        };

        for (int index = 0; index < days.length; index++) {
            TextView headerView = new TextView(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

            if (index > 0) {
                layoutParams.setMarginStart(dpToPx(6));
            }

            headerView.setLayoutParams(layoutParams);
            headerView.setGravity(Gravity.CENTER);
            headerView.setText(days[index].getDisplayName(TextStyle.NARROW, Locale.getDefault()));
            headerView.setTextColor(getColor(R.color.widget_text_muted));
            headerView.setTextSize(12f);
            container.addView(headerView);
        }
    }

    private void renderHistoryCalendar(
            LinearLayout container,
            String itemId,
            List<LocalDate> refreshHistoryDates,
            LocalDate visibleMonthStart,
            LocalDate minimumDate,
            LocalDate today,
            Runnable refreshAction
    ) {
        container.removeAllViews();
        LocalDate calendarStart = visibleMonthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate calendarEnd = visibleMonthStart
                .with(TemporalAdjusters.lastDayOfMonth())
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        int weekCount = (int) ChronoUnit.WEEKS.between(
                calendarStart,
                calendarEnd
        ) + 1;

        for (int weekIndex = 0; weekIndex < weekCount; weekIndex++) {
            LocalDate weekStart = calendarStart.plusWeeks(weekIndex);

            LinearLayout weekRow = new LinearLayout(this);
            LinearLayout.LayoutParams weekLayoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );

            if (weekIndex > 0) {
                weekLayoutParams.topMargin = dpToPx(6);
            }

            weekRow.setLayoutParams(weekLayoutParams);
            weekRow.setOrientation(LinearLayout.HORIZONTAL);

            for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
                LocalDate date = weekStart.plusDays(dayIndex);
                TextView dayView = buildHistoryDayView(
                        itemId,
                        date,
                        visibleMonthStart,
                        minimumDate,
                        today,
                        refreshHistoryDates.contains(date),
                        refreshAction
                );
                weekRow.addView(dayView);
            }

            container.addView(weekRow);
        }
    }

    private String getMonthLabel(LocalDate monthDate) {
        return monthDate.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + monthDate.getYear();
    }

    private TextView buildHistoryDayView(
            String itemId,
            LocalDate date,
            LocalDate visibleMonthStart,
            LocalDate minimumDate,
            LocalDate today,
            boolean isRecorded,
            Runnable refreshAction
    ) {
        TextView dayView = new TextView(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, dpToPx(34), 1f);

        if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
            layoutParams.setMarginStart(dpToPx(6));
        }

        dayView.setLayoutParams(layoutParams);
        dayView.setGravity(Gravity.CENTER);
        dayView.setTextSize(12f);

        if (date.getMonth() != visibleMonthStart.getMonth()
                || date.getYear() != visibleMonthStart.getYear()
                || date.isBefore(minimumDate)
                || date.isAfter(today)) {
            dayView.setText("");
            dayView.setBackgroundResource(R.drawable.history_day_future);
            return dayView;
        }

        dayView.setText(String.valueOf(date.getDayOfMonth()));
        dayView.setClickable(true);
        dayView.setFocusable(true);

        if (isRecorded) {
            dayView.setBackgroundResource(R.drawable.history_day_active);
            dayView.setTextColor(getColor(android.R.color.white));
            dayView.setContentDescription(getString(
                    R.string.history_day_refreshed_description,
                    LastTimeFormatter.getDateLabel(date)
            ));
        } else {
            dayView.setBackgroundResource(R.drawable.history_day_inactive);
            dayView.setTextColor(getColor(R.color.widget_text_secondary));
            dayView.setContentDescription(getString(
                    R.string.history_day_empty_description,
                    LastTimeFormatter.getDateLabel(date)
            ));
        }

        dayView.setOnClickListener(view -> {
            if (LastTimeStorage.toggleRefreshDay(this, itemId, date)) {
                Log.d(LOG_TAG, "Toggled item history day from calendar: " + itemId + " on " + date);
                syncItemsAndWidget();
                refreshAction.run();
            }
        });

        return dayView;
    }

    private void renderRecentEvents(LinearLayout container, List<LocalDate> refreshHistoryDates) {
        container.removeAllViews();

        if (refreshHistoryDates.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(R.string.history_no_recent_events);
            emptyView.setTextColor(getColor(R.color.widget_text_secondary));
            emptyView.setTextSize(14f);
            container.addView(emptyView);
            return;
        }

        int startIndex = Math.max(0, refreshHistoryDates.size() - RECENT_EVENT_COUNT);

        for (int index = refreshHistoryDates.size() - 1; index >= startIndex; index--) {
            TextView eventView = new TextView(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );

            if (index < refreshHistoryDates.size() - 1) {
                layoutParams.topMargin = dpToPx(8);
            }

            eventView.setLayoutParams(layoutParams);
            eventView.setLineSpacing(0f, 1.1f);
            eventView.setTextColor(getColor(R.color.widget_text_secondary));
            eventView.setTextSize(14f);
            eventView.setText(getString(
                    R.string.history_recent_event_row,
                    LastTimeFormatter.getDateLabel(refreshHistoryDates.get(index))
            ));
            container.addView(eventView);
        }

        int olderEventCount = startIndex;

        if (olderEventCount > 0) {
            TextView olderEventsView = new TextView(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            layoutParams.topMargin = dpToPx(10);
            olderEventsView.setLayoutParams(layoutParams);
            olderEventsView.setTextColor(getColor(R.color.widget_text_muted));
            olderEventsView.setTextSize(13f);
            olderEventsView.setText(getString(R.string.history_more_events, olderEventCount));
            container.addView(olderEventsView);
        }
    }

    private int dpToPx(int valueInDp) {
        return Math.round(valueInDp * getResources().getDisplayMetrics().density);
    }

    private void confirmDeleteItem(String itemId) {
        LastTimeItem item = LastTimeStorage.getItem(this, itemId);

        if (item == null) {
            syncItemsAndWidget();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_item_button)
                .setMessage(getString(R.string.delete_item_confirmation, item.getTitle()))
                .setPositiveButton(R.string.delete_item_button, (dialogInterface, which) -> {
                    LastTimeStorage.deleteItem(this, itemId);
                    Log.d(LOG_TAG, "Tracked item soft-deleted from app: " + itemId);
                    syncItemsAndWidget();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void restoreItem(String itemId) {
        if (LastTimeStorage.restoreItem(this, itemId)) {
            Log.d(LOG_TAG, "Tracked item restored from app: " + itemId);
            syncItemsAndWidget();
        }
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();

        if (LastTimeIWidgetProvider.ACTION_OPEN_ADD_ITEM.equals(action)) {
            Log.d(LOG_TAG, "Opening add-item flow from widget");
            clearHandledIntent(intent);
            showItemEditorDialog(null);
            return;
        }

        if (!LastTimeIWidgetProvider.ACTION_OPEN_EDIT_ITEM.equals(action)) {
            return;
        }

        String itemId = intent.getStringExtra(LastTimeIWidgetProvider.EXTRA_ITEM_ID);

        if (itemId == null) {
            clearHandledIntent(intent);
            return;
        }

        Log.d(LOG_TAG, "Opening edit flow from widget for item " + itemId);
        clearHandledIntent(intent);
        showItemEditorDialog(itemId);
    }

    private void clearHandledIntent(Intent intent) {
        intent.setAction(null);
        intent.removeExtra(LastTimeIWidgetProvider.EXTRA_ITEM_ID);
        setIntent(intent);
    }

    private void syncItemsAndWidget() {
        int widgetCount = LastTimeIWidgetProvider.requestRefreshAll(this);
        List<LastTimeItem> items = LastTimeStorage.getItems(this);
        renderItems(items);
        updateStatusText(widgetCount, items.size(), false);
    }
}
