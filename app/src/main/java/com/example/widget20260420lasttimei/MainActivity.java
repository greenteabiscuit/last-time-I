package com.example.widget20260420lasttimei;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final String LOG_TAG = "LastTimeIWidget";

    private TextView statusText;
    private TextView emptyStateText;
    private LinearLayout itemsContainer;

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

        addItemButton.setOnClickListener(view -> showItemEditorDialog(null));

        refreshButton.setOnClickListener(view -> {
            int widgetCount = LastTimeIWidgetProvider.requestRefreshAll(this);
            Log.d(LOG_TAG, "Refresh requested from app for " + widgetCount + " widget(s)");
            List<LastTimeItem> items = LastTimeStorage.getItems(this);
            renderItems(items);
            updateStatusText(widgetCount, items.size(), true);
        });

        List<LastTimeItem> items = LastTimeStorage.getItems(this);
        renderItems(items);
        updateStatusText(LastTimeIWidgetProvider.getInstalledWidgetCount(this), items.size(), false);
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();

        List<LastTimeItem> items = LastTimeStorage.getItems(this);
        renderItems(items);
        updateStatusText(LastTimeIWidgetProvider.getInstalledWidgetCount(this), items.size(), false);
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
        itemsContainer.removeAllViews();

        if (items.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            return;
        }

        emptyStateText.setVisibility(View.GONE);

        for (LastTimeItem item : items) {
            View row = layoutInflater.inflate(R.layout.last_time_item_row, itemsContainer, false);
            TextView titleView = row.findViewById(R.id.item_title);
            TextView summaryView = row.findViewById(R.id.item_summary);
            Button markTodayButton = row.findViewById(R.id.item_mark_today_button);
            Button editButton = row.findViewById(R.id.item_edit_button);
            Button deleteButton = row.findViewById(R.id.item_delete_button);

            titleView.setText(item.getTitle());
            summaryView.setText(LastTimeFormatter.getAppSummary(this, item.getLastRefreshedAtMillis()));

            View.OnClickListener editClickListener = view -> showItemEditorDialog(item.getId());
            row.setOnClickListener(editClickListener);
            titleView.setOnClickListener(editClickListener);
            summaryView.setOnClickListener(editClickListener);
            editButton.setOnClickListener(editClickListener);

            markTodayButton.setOnClickListener(view -> {
                if (LastTimeStorage.markNow(this, item.getId())) {
                    Log.d(LOG_TAG, "Marked item as today from app: " + item.getId());
                    syncItemsAndWidget();
                }
            });

            deleteButton.setOnClickListener(view -> confirmDeleteItem(item.getId()));
            itemsContainer.addView(row);
        }
    }

    private void showItemEditorDialog(String itemId) {
        LastTimeItem existingItem = itemId == null ? null : LastTimeStorage.getItem(this, itemId);

        if (itemId != null && existingItem == null) {
            Log.d(LOG_TAG, "Tried to edit missing item " + itemId);
            syncItemsAndWidget();
            return;
        }

        EditText titleInput = new EditText(this);
        titleInput.setHint(R.string.item_dialog_hint);
        titleInput.setSingleLine();

        if (existingItem != null) {
            titleInput.setText(existingItem.getTitle());
            titleInput.setSelection(titleInput.getText().length());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existingItem == null ? R.string.dialog_add_title : R.string.dialog_edit_title)
                .setView(titleInput)
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

                if (dialogItem == null) {
                    LastTimeStorage.addItem(this, title);
                    Log.d(LOG_TAG, "Tracked item added from app dialog");
                } else {
                    LastTimeStorage.updateItemTitle(this, dialogItem.getId(), title);
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
                    Log.d(LOG_TAG, "Tracked item deleted from app: " + itemId);
                    syncItemsAndWidget();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
