package sa.osa373.lifecampus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ReminderStore {
    private static final String PREFS = "life_campus_reminders_v2";
    private static final String ITEMS = "items";
    private ReminderStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized JSONArray read(Context context) {
        try { return new JSONArray(prefs(context).getString(ITEMS, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    static JSONObject item(String id, String title, long at, String source, boolean exact) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title);
            o.put("at", at);
            o.put("source", source);
            o.put("exact", exact);
        } catch (Exception ignored) {}
        return o;
    }

    static synchronized void addManual(Context context, String title, long at) {
        JSONArray records = read(context);
        records.put(item("manual:" + System.nanoTime(), title, at, "manual", true));
        save(context, records);
        reschedule(context);
    }

    static synchronized void remove(Context context, String id) {
        JSONArray old = read(context), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o == null) continue;
            if (id.equals(o.optString("id"))) cancel(context, id);
            else next.put(o);
        }
        save(context, next);
    }

    static synchronized void reconcileAuto(Context context, JSONArray wanted) {
        JSONArray old = read(context), next = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id");
            if ("manual".equals(o.optString("source"))) {
                if (o.optLong("at") > now) next.put(o);
                else cancel(context, id);
                continue;
            }
            JSONObject match = find(wanted, id);
            if (match == null || match.optLong("at") != o.optLong("at")
                    || !match.optString("title").equals(o.optString("title"))) {
                cancel(context, id);
            }
        }
        for (int i = 0; i < wanted.length(); i++) {
            JSONObject desired = wanted.optJSONObject(i);
            if (desired == null || desired.optLong("at") <= now) continue;
            next.put(desired);
            JSONObject previous = find(old, desired.optString("id"));
            if (previous == null || previous.optLong("at") != desired.optLong("at")
                    || !previous.optString("title").equals(desired.optString("title"))) {
                schedule(context, desired);
            }
        }
        save(context, next);
    }

    private static JSONObject find(JSONArray arr, String id) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    static synchronized void reschedule(Context context) {
        JSONArray items = read(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && item.optLong("at") > System.currentTimeMillis()) schedule(context, item);
        }
    }

    static synchronized void delivered(Context context, String id) {
        remove(context, id);
    }

    static void save(Context context, JSONArray arr) {
        prefs(context).edit().putString(ITEMS, arr.toString()).apply();
    }

    static void schedule(Context context, JSONObject data) {
        long at = data.optLong("at");
        if (at <= System.currentTimeMillis()) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = pending(context, data.optString("id"), data.optString("title"));
        // A user-created alarm is intentionally exact; automatic task reminders are best-effort.
        if (data.optBoolean("exact")) {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, openApp(context)), pending);
        } else if (Build.VERSION.SDK_INT >= 23) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, at, pending);
        }
    }

    static void cancel(Context context, String id) {
        AlarmManager m = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (m != null) m.cancel(pending(context, id, ""));
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 27, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent pending(Context context, String id, String title) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction("sa.osa373.lifecampus.REMINDER")
                .setData(android.net.Uri.parse("lifecampus-reminder://alarm/" + android.net.Uri.encode(id)))
                .putExtra("id", id)
                .putExtra("title", title);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
