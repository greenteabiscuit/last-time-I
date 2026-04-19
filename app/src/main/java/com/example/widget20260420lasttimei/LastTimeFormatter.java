package com.example.widget20260420lasttimei;

import android.content.Context;

import java.text.DateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public final class LastTimeFormatter {
    private LastTimeFormatter() {
    }

    public static String getDayCountLabel(Context context, long lastRefreshedAtMillis) {
        long dayCount = getDaysSince(lastRefreshedAtMillis);

        if (dayCount == 0L) {
            return context.getString(R.string.day_count_today);
        }

        if (dayCount == 1L) {
            return context.getString(R.string.day_count_single);
        }

        return context.getString(R.string.day_count_plural, dayCount);
    }

    public static String getAppSummary(Context context, long lastRefreshedAtMillis) {
        String dateLabel = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(lastRefreshedAtMillis));
        return context.getString(R.string.last_seen_summary, dateLabel, getDayCountLabel(context, lastRefreshedAtMillis));
    }

    private static long getDaysSince(long lastRefreshedAtMillis) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate lastDate = Instant.ofEpochMilli(lastRefreshedAtMillis).atZone(zoneId).toLocalDate();
        LocalDate today = LocalDate.now(zoneId);
        long dayCount = ChronoUnit.DAYS.between(lastDate, today);
        return Math.max(dayCount, 0L);
    }
}
