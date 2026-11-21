package sa.osa373.lifecampus;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://life-campus.osamah-arishi.workers.dev/";
    private static final String TRUSTED_HOST = "life-campus.osamah-arishi.workers.dev";
    private WebView webView;
    private LinearLayout root;
    private Button reminderButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable syncReminderTask = new Runnable() {
        @Override public void run() {
            pullWebReminders();
            handler.postDelayed(this, 30000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReminderReceiver.createChannel(this);
        ReminderStore.reschedule(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        buildWebView();
        setupReminderButton();
        showWebView();
        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(APP_URL);
        requestNotificationPermission();
    }

    private void buildWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pullWebReminders();
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && TRUSTED_HOST.equalsIgnoreCase(uri.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) showOfflineMessage();
            }
        });
    }

    private void setupReminderButton() {
        reminderButton = new Button(this);
        reminderButton.setText("🔔 التنبيهات والمنبّه");
        reminderButton.setTextColor(Color.WHITE);
        reminderButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(28, 52, 96)));
        reminderButton.setOnClickListener(v -> showRemindersDialog());
    }

    private void showWebView() {
        root.removeAllViews();
        if (webView.getParent() != null) ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(reminderButton, new LinearLayout.LayoutParams(-1, -2));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 101 && results.length > 0 && results[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "لتفعيل صوت التذكير والإشعارات، اسمح بإشعارات التطبيق من إعدادات الهاتف", Toast.LENGTH_LONG).show();
        }
    }

    private void showRemindersDialog() {
        requestNotificationPermission();
        JSONArray items = ReminderStore.read(this);
        ArrayList<String> titles = new ArrayList<>();
        titles.add("＋ إضافة منبّه شخصي بوقت محدد");
        titles.add("♫ تجربة صوت التنبيه الآن");
        titles.add("⚙ فتح إعدادات الإشعارات");
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null || o.optLong("at") < System.currentTimeMillis()) continue;
            String when = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(o.optLong("at")));
            titles.add(when + " — " + o.optString("title") + ("manual".equals(o.optString("source")) ? " (يدوي)" : " (تلقائي)"));
        }
        new AlertDialog.Builder(this)
                .setTitle("التذكيرات الصوتية — اضغط تذكيرًا لحذفه")
                .setMessage("المهام: الساعة 9 صباحًا في يوم الاستحقاق. المحاضرات: قبل بدايتها بـ15 دقيقة. التذكير اليدوي في الوقت الذي تحدده. قد تتأخر التذكيرات التلقائية قليلًا بحسب توفير الطاقة.")
                .setItems(titles.toArray(new String[0]), (d, index) -> {
                    if (index == 0) addManualReminder();
                    else if (index == 1) ReminderReceiver.show(this, "هذا اختبار لصوت تنبيهات حياتي وجامعتي", "manual:test");
                    else if (index == 2) {
                        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(intent);
                    } else {
                        String row = titles.get(index);
                        // Find the reminder by its visible label; this also handles expired records safely.
                        JSONArray saved = ReminderStore.read(this);
                        for (int j=0; j<saved.length(); j++) {
                            JSONObject o = saved.optJSONObject(j);
                            if (o==null || o.optLong("at")<System.currentTimeMillis()) continue;
                            String when = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(o.optLong("at")));
                            String label = when+" — "+o.optString("title")+("manual".equals(o.optString("source"))?" (يدوي)":" (تلقائي)");
                            if (row.equals(label)) {
                                final String id=o.optString("id");
                                new AlertDialog.Builder(this).setMessage("حذف هذا التنبيه؟")
                                    .setPositiveButton("حذف", (dialog, which)->{
                                        ReminderStore.remove(this,id);
                                        Toast.makeText(this,"تم حذف التذكير",Toast.LENGTH_SHORT).show();
                                    }).setNegativeButton("إلغاء",null).show();
                                break;
                            }
                        }
                    }
                })
                .setNegativeButton("إغلاق", null).show();
    }

    private void addManualReminder() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("مثال: مراجعة اختبار أنظمة التشغيل");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(42, 12, 42, 12);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("عنوان المنبّه").setView(box)
            .setPositiveButton("تحديد التاريخ", (d, w)->{
                String title = input.getText().toString().trim();
                if (title.isEmpty()) { Toast.makeText(this,"أدخل عنوان التذكير أولًا",Toast.LENGTH_LONG).show(); return; }
                selectDateAndTime(title);
            }).setNegativeButton("إلغاء",null).show();
    }

    private void selectDateAndTime(String title) {
        Calendar c = Calendar.getInstance();
        DatePickerDialog pick = new DatePickerDialog(this,(v,year,month,day)->{
            c.set(Calendar.YEAR,year);
            c.set(Calendar.MONTH,month);
            c.set(Calendar.DAY_OF_MONTH,day);
            new TimePickerDialog(this,(timeView,hour,minute)->{
                c.set(Calendar.HOUR_OF_DAY,hour);
                c.set(Calendar.MINUTE,minute);
                c.set(Calendar.SECOND,0);
                c.set(Calendar.MILLISECOND,0);
                if(c.getTimeInMillis()<=System.currentTimeMillis()){
                    Toast.makeText(this,"اختر وقتًا قادمًا",Toast.LENGTH_LONG).show();
                    return;
                }
                ReminderStore.addManual(this,title,c.getTimeInMillis());
                Toast.makeText(this,"تم تفعيل المنبّه الصوتي",Toast.LENGTH_LONG).show();
            },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),false).show();
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));
        pick.getDatePicker().setMinDate(System.currentTimeMillis()-1000);
        pick.show();
    }

    private void pullWebReminders() {
        if (webView == null || webView.getUrl() == null) return;
        Uri url = Uri.parse(webView.getUrl());
        if (!"https".equalsIgnoreCase(url.getScheme()) || !TRUSTED_HOST.equalsIgnoreCase(url.getHost())) return;
        String script="(function(){try{return JSON.stringify({data:JSON.parse(localStorage.getItem('lifeCampus.v1')||'{}'),focus:JSON.parse(localStorage.getItem('lifeCampus.focus.v2')||'{}')})}catch(e){return '{}'}})()";
        webView.evaluateJavascript(script,value->{
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String)) return;
                JSONObject bundle = new JSONObject((String) decoded);
                JSONArray wanted = buildAutomaticReminders(bundle);
                ReminderStore.reconcileAuto(this,wanted);
            } catch (Exception ignored) { }
        });
    }

    private JSONArray buildAutomaticReminders(JSONObject bundle) {
        JSONArray reminders = new JSONArray();
        JSONObject data = bundle.optJSONObject("data");
        if (data == null) return reminders;
        long now=System.currentTimeMillis();
        JSONArray tasks = data.optJSONArray("tasks");
        if (tasks != null) for (int i=0; i<tasks.length(); i++) {
            JSONObject task=tasks.optJSONObject(i);
            if (task == null || task.optBoolean("done")) continue;
            String due=task.optString("due"), title=task.optString("title");
            if (!due.matches("\\d{4}-\\d{2}-\\d{2}") || title.isEmpty()) continue;
            try {
                String[] date=due.split("-");
                Calendar c=Calendar.getInstance();
                c.set(Integer.parseInt(date[0]),Integer.parseInt(date[1])-1,Integer.parseInt(date[2]),9,0,0);
                c.set(Calendar.MILLISECOND,0);
                long at=c.getTimeInMillis();
                if(at>now) reminders.put(ReminderStore.item("task:"+task.optString("id"),"تذكير مهمة: "+title,at,"task",false));
            } catch(Exception ignored){}
        }
        JSONArray lectures=data.optJSONArray("lectures");
        if(lectures!=null) for(int i=0;i<lectures.length();i++){
            JSONObject l=lectures.optJSONObject(i);
            if(l==null) continue;
            String start=l.optString("start"),day=l.optString("day"),course=l.optString("course");
            if(!start.matches("\\d{2}:\\d{2}") || !day.matches("[0-6]"))continue;
            String[] hm=start.split(":");
            for(int w=0;w<4;w++){
                try{
                    Calendar c=Calendar.getInstance();
                    int delta=(Integer.parseInt(day) - (c.get(Calendar.DAY_OF_WEEK)-1)+7)%7+7*w;
                    c.add(Calendar.DAY_OF_MONTH,delta);
                    c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hm[0]));
                    c.set(Calendar.MINUTE,Integer.parseInt(hm[1]));
                    c.set(Calendar.SECOND,0);
                    c.set(Calendar.MILLISECOND,0);
                    c.add(Calendar.MINUTE,-15);
                    long at=c.getTimeInMillis();
                    if(at>now){
                        String id="lecture:"+l.optString("id")+":"+new SimpleDateFormat("yyyyMMdd",Locale.US).format(c.getTime());
                        reminders.put(ReminderStore.item(id,"محاضرة "+course+" تبدأ بعد 15 دقيقة",at,"lecture",false));
                    }
                }catch(Exception ignored){}
            }
        }
        JSONObject focus=bundle.optJSONObject("focus");
        if(focus!=null && focus.optLong("end")>now) {
            String title=focus.optInt("duration")==5?"انتهت الاستراحة":"انتهت جلسة التركيز";
            reminders.put(ReminderStore.item("focus",title,focus.optLong("end"),"focus",true));
        }
        return reminders;
    }

    private void showOfflineMessage() {
        if (isFinishing()) return;
        runOnUiThread(() -> {
            root.removeAllViews();
            LinearLayout offline=new LinearLayout(this);
            offline.setOrientation(LinearLayout.VERTICAL);
            offline.setPadding(40,80,40,40);
            TextView text=new TextView(this);
            text.setText(R.string.no_connection);
            text.setTextSize(18);
            Button retry=new Button(this);
            retry.setText(R.string.retry);
            retry.setOnClickListener(v->{
                showWebView();
                webView.loadUrl(APP_URL);
            });
            offline.addView(text);
            offline.addView(retry);
            root.addView(offline,new LinearLayout.LayoutParams(-1,0,1));
            root.addView(reminderButton,new LinearLayout.LayoutParams(-1,-2));
        });
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(syncReminderTask);
        handler.postDelayed(syncReminderTask,2000);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(syncReminderTask);
        super.onPause();
    }
    @Override public void onBackPressed() {
        if(webView!=null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(webView!=null)webView.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(syncReminderTask);
        if(webView!=null){webView.destroy();webView=null;}
        super.onDestroy();
    }
}
