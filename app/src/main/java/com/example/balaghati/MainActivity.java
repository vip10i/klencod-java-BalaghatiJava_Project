package com.example.balaghati;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private final String PREFS = "balaghati_store";
    private final String KEY = "reports";
    private SharedPreferences prefs;
    private LinearLayout reportList, root;
    private TextView countText;
    private final String[] platforms = {"TikTok", "Instagram", "Facebook"};
    private final String[] reasons = {"انتحال شخصية", "احتيال أو نصب", "تحرش أو تهديد", "محتوى ضار", "رسائل مزعجة", "حقوق ملكية فكرية", "أخرى"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        renderReports();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.RIGHT);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    private void pad(View v, int all) { v.setPadding(dp(all), dp(all), dp(all), dp(all)); }

    private void buildUi() {
        root = column();
        root.setBackgroundColor(Color.rgb(243,245,249));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout header = column();
        header.setPadding(dp(20), dp(24), dp(20), dp(24));
        header.setBackgroundColor(Color.rgb(17,24,39));
        TextView title = text("🛡️ بلاغاتي", 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        TextView sub = text("مساعد تنظيم البلاغات", 15, Color.rgb(219,234,254), false);
        sub.setGravity(Gravity.CENTER);
        header.addView(title); header.addView(sub);
        root.addView(header);

        LinearLayout warning = card();
        TextView warn = text("استخدم البلاغات للمشكلات الحقيقية فقط. هذا التطبيق يحفظ تفاصيل البلاغ على جهازك ولا يرسل البلاغ تلقائيًا، ولا يضمن إغلاق أي حساب.", 14, Color.rgb(104,75,5), false);
        warning.setBackground(bg(Color.rgb(255,248,230), 16));
        warning.addView(warn);
        addOuter(warning);

        LinearLayout form = card();
        form.addView(text("إنشاء بلاغ جديد", 21, Color.rgb(23,32,51), true));
        Spinner platform = spinner(platforms);
        addField(form, "المنصة", platform);

        EditText target = edit("https://...");
        target.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addField(form, "رابط الحساب أو المحتوى", target);

        Spinner reason = spinner(reasons);
        addField(form, "نوع المشكلة", reason);

        EditText details = edit("اشرح ما حدث باختصار وبدقة...");
        details.setMinLines(3); details.setGravity(Gravity.TOP | Gravity.RIGHT);
        addField(form, "تفاصيل الواقعة", details);

        Button save = button("💾 حفظ البلاغ");
        save.setOnClickListener(v -> {
            String url = target.getText().toString().trim();
            String desc = details.getText().toString().trim();
            Uri uri = Uri.parse(url);
            if (!(url.startsWith("https://") || url.startsWith("http://")) || TextUtils.isEmpty(uri.getHost())) {
                target.setError("أدخل رابطًا صحيحًا يبدأ بـ https://");
                return;
            }
            if (desc.isEmpty()) { details.setError("اكتب تفاصيل المشكلة"); return; }
            try {
                JSONArray arr = getReports();
                JSONObject obj = new JSONObject();
                obj.put("platform", platform.getSelectedItem().toString());
                obj.put("target", url);
                obj.put("reason", reason.getSelectedItem().toString());
                obj.put("details", desc);
                obj.put("status", "قيد المتابعة");
                obj.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
                arr.put(obj);
                prefs.edit().putString(KEY, arr.toString()).apply();
                target.setText(""); details.setText("");
                renderReports();
                new AlertDialog.Builder(this).setMessage("تم حفظ البلاغ على هذا الجهاز. استخدم قناة المنصة الرسمية لتقديمه.").setPositiveButton("حسنًا", null).show();
            } catch (Exception ex) {
                new AlertDialog.Builder(this).setMessage("تعذر حفظ البلاغ.").setPositiveButton("حسنًا", null).show();
            }
        });
        form.addView(save);
        addOuter(form);

        LinearLayout history = card();
        history.addView(text("سجل البلاغات", 21, Color.rgb(23,32,51), true));
        countText = text("عدد البلاغات: 0", 15, Color.rgb(49,87,213), true);
        countText.setPadding(0, dp(8), 0, dp(8));
        history.addView(countText);
        reportList = column();
        history.addView(reportList);

        Button clear = button("مسح السجل المحلي");
        clear.setBackground(bg(Color.rgb(255,232,232), 10));
        clear.setTextColor(Color.rgb(180,35,24));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setMessage("هل تريد حذف جميع البلاغات المحفوظة على هذا الجهاز؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف", (d,w) -> { prefs.edit().remove(KEY).apply(); renderReports(); }).show());
        history.addView(clear);
        addOuter(history);

        LinearLayout links = card();
        links.addView(text("قنوات الإبلاغ الرسمية", 21, Color.rgb(23,32,51), true));
        addLink(links, "TikTok — الإبلاغ والملاحظات", "https://www.tiktok.com/legal/report/feedback");
        addLink(links, "Instagram — مركز المساعدة", "https://help.instagram.com/");
        addLink(links, "Facebook — مركز المساعدة", "https://www.facebook.com/help/");
        links.addView(text("قد تختلف صفحة الإبلاغ حسب نوع المشكلة. قرار اتخاذ الإجراء يعود إلى المنصة.", 12, Color.GRAY, false));
        addOuter(links);
    }

    private LinearLayout card() {
        LinearLayout l = column();
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(bg(Color.WHITE, 16));
        return l;
    }

    private void addOuter(View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(13), dp(10), dp(13), dp(2));
        root.addView(v, p);
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(false); e.setTextSize(15); e.setHint(hint);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(bg(Color.rgb(248,249,252), 10));
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        return e;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setPadding(dp(8), dp(4), dp(8), dp(4));
        s.setBackground(bg(Color.rgb(248,249,252), 10));
        return s;
    }

    private void addField(LinearLayout parent, String label, View input) {
        TextView t = text(label, 14, Color.rgb(23,32,51), true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(12); parent.addView(t, tp);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(5); parent.addView(input, ip);
    }

    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(15);
        b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setBackground(bg(Color.rgb(49,87,213), 10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(14); b.setPadding(dp(10), dp(10), dp(10), dp(10));
        return b;
    }

    private void addLink(LinearLayout parent, String label, String url) {
        Button b = button(label + " ↗");
        b.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception ignored) {}
        });
        parent.addView(b);
    }

    private JSONArray getReports() {
        try { return new JSONArray(prefs.getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void renderReports() {
        if (reportList == null) return;
        JSONArray arr = getReports();
        reportList.removeAllViews();
        countText.setText("عدد البلاغات: " + arr.length());
        if (arr.length() == 0) {
            reportList.addView(text("لا توجد بلاغات محفوظة بعد.", 14, Color.GRAY, false));
            return;
        }
        for (int i = arr.length()-1; i >= 0; i--) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            LinearLayout item = column();
            item.setPadding(dp(12), dp(12), dp(12), dp(12));
            item.setBackground(bg(Color.rgb(246,248,252), 12));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
            ip.topMargin = dp(8);
            reportList.addView(item, ip);
            item.addView(text(obj.optString("platform") + " · " + obj.optString("reason"), 16, Color.rgb(35,68,177), true));
            item.addView(text("الحالة: " + obj.optString("status"), 13, Color.rgb(49,87,213), true));
            item.addView(text("التاريخ: " + obj.optString("date"), 12, Color.GRAY, false));
            TextView details = text("التفاصيل: " + obj.optString("details"), 14, Color.rgb(23,32,51), false);
            details.setPadding(0, dp(5), 0, dp(5)); item.addView(details);
            Button open = button("فتح الرابط");
            String target = obj.optString("target");
            open.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target))); } catch(Exception ignored){} });
            item.addView(open);
        }
    }
}