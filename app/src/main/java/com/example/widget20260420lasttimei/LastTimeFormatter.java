package com.example.widget20260420lasttimei;

import android.content.Context;

import java.text.DateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;

public final class LastTimeFormatter {
    private static final DateTimeFormatter LOCAL_DATE_LABEL_FORMATTER =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault());

    private LastTimeFormatter() {
    }

    public static String getDateKey(LocalDate date) {
        return date.toString();
    }

    public static long getStartOfDayMillis(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static LocalDate getLocalDate(long timeMillis) {
        return Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static String getDateLabel(LocalDate date) {
        return LOCAL_DATE_LABEL_FORMATTER.format(date);
    }

    public static String getDateLabel(long timeMillis) {
        return getDateLabel(getLocalDate(timeMillis));
    }

    public static String getDayCountLabel(Context context, long lastRefreshedAtMillis) {
        if (lastRefreshedAtMillis <= 0L) {
            return context.getString(R.string.day_count_never);
        }

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
        if (lastRefreshedAtMillis <= 0L) {
            return context.getString(R.string.last_seen_summary_never);
        }

        String dateLabel = getDateLabel(lastRefreshedAtMillis);
        return context.getString(R.string.last_seen_summary, dateLabel, getDayCountLabel(context, lastRefreshedAtMillis));
    }

    public static long getDaysSince(long lastRefreshedAtMillis) {
        if (lastRefreshedAtMillis <= 0L) {
            return 0L;
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate lastDate = Instant.ofEpochMilli(lastRefreshedAtMillis).atZone(zoneId).toLocalDate();
        LocalDate today = LocalDate.now(zoneId);
        long dayCount = ChronoUnit.DAYS.between(lastDate, today);
        return Math.max(dayCount, 0L);
    }
}
