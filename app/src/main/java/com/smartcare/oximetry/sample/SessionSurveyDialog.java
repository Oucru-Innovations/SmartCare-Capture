package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Khi phiên đo 20 phút kết thúc: một popup gộp Session note + "Bảng khảo sát ý kiến bệnh nhân về
 * thiết bị đeo" → xuất một file Annotation_*.json (data_type = "session_survey")
 * và upload như annotation.
 */
final class SessionSurveyDialog {
    private static final String TAG = "SessionSurveyDialog";

    private static final String[] DISCOMFORT = {"Không", "Khó chịu không đáng kể", "Có", "Rất khó chịu"};
    private static final String[] DISCOMFORT_CODES = {"khong", "khong_dang_ke", "co", "rat_kho_chiu"};

    private final Activity activity;
    private final String researchCode;
    private final Map<String, Object> measurement;

    private SessionSurveyDialog(Activity activity, String researchCode, Map<String, Object> measurement) {
        this.activity = activity;
        this.researchCode = researchCode;
        this.measurement = measurement;
    }

    /** Hiện popup gộp (Session note + khảo sát) khi phiên đo kết thúc; {@code measurement} ghi kèm JSON. */
    static void show(Activity activity, String researchCode, Map<String, Object> measurement) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        new SessionSurveyDialog(activity, researchCode, measurement).build().show();
    }

    private AlertDialog build() {
        LinearLayout root = column();

        bold(root, "Thời điểm thu:");
        Choice meal = new Choice(root, new String[]{"Trước bữa ăn", "Sau bữa ăn"});
        bold(root, "Vận động:");
        Choice exercise = new Choice(root, new String[]{"Trước vận động", "Sau vận động"});

        bold(root, "1. Nhìn chung, việc đeo đồng hồ đo nhịp tim và độ bão hoà oxy máu có gây khó chịu không?");
        Choice q1 = new Choice(root, DISCOMFORT);
        text(root, "Nếu chọn có khó chịu, sự khó chịu đến từ:", Typeface.ITALIC);
        text(root, "a. Đồng hồ đeo ở cổ tay:", Typeface.NORMAL);
        Choice q1a = new Choice(root, DISCOMFORT);
        text(root, "b. Cảm biến đeo ở đầu ngón tay:", Typeface.NORMAL);
        Choice q1b = new Choice(root, DISCOMFORT);
        text(root, "c. Dây nối giữa đồng hồ và cảm biến:", Typeface.NORMAL);
        Choice q1c = new Choice(root, DISCOMFORT);
        EditText q1d = labeledInput(root, "d. Lý do khác:", Typeface.NORMAL);

        bold(root, "2. Trong thời gian đeo thiết bị, các thông số sinh hiệu như nhịp tim, độ bão hoà oxy trong máu (SpO2),… được xem trung bình mấy lần/ngày?");
        EditText q2 = timesPerDayInput(root);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);

        return buildDialog("Bảng khảo sát ý kiến bệnh nhân về thiết bị đeo", scroll, "Gửi", () -> {
            boolean q1Discomfort = q1.selected() > 0;
            if (meal.selected() < 0 || exercise.selected() < 0 || q1.selected() < 0 || value(q2).isEmpty()
                    || (q1Discomfort && (q1a.selected() < 0 || q1b.selected() < 0 || q1c.selected() < 0))) {
                return false;
            }
            Map<String, Object> sessionNote = new LinkedHashMap<>();
            sessionNote.put("meal", meal.selected() == 0 ? "before" : "after");
            sessionNote.put("exercise", exercise.selected() == 0 ? "before" : "after");
            Map<String, Object> survey = new LinkedHashMap<>();
            survey.put("q1_watch_discomfort", q1.code(DISCOMFORT_CODES));
            survey.put("q1a_wrist", q1a.code(DISCOMFORT_CODES));
            survey.put("q1b_finger_sensor", q1b.code(DISCOMFORT_CODES));
            survey.put("q1c_cable", q1c.code(DISCOMFORT_CODES));
            survey.put("q1d_other", value(q1d));
            survey.put("q2_views_per_day", Integer.parseInt(value(q2)));
            submit(sessionNote, survey);
            return true;
        });
    }

    private void submit(Map<String, Object> sessionNote, Map<String, Object> survey) {
        Date now = new Date();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("research_code", researchCode);
        json.put("recording_time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(now));
        json.put("data_type", "session_survey");
        json.put("session_note", sessionNote);
        json.put("measurement", measurement);
        json.put("survey", survey);

        // Cùng quy ước tên với ShowAnnotate để File Manager / retry 17h nhận diện được.
        String fileName = "Annotation_" + researchCode + "_"
                + new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US).format(now) + "_.json";
        try {
            AnnotationStorage.exportAndUpload(activity, fileName,
                    new GsonBuilder().serializeNulls().create().toJson(json));
            Toast.makeText(activity, "Đã lưu khảo sát", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save session survey", e);
            Toast.makeText(activity, "Lưu khảo sát thất bại", Toast.LENGTH_LONG).show();
        }
    }

    /** Dialog bắt buộc, tiêu đề in đậm căn giữa; nút chính chỉ đóng khi {@code onSubmit} trả về true. */
    private AlertDialog buildDialog(String title, View content, String buttonText, BooleanSupplier onSubmit) {
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleView.setPadding(dp(16), dp(16), dp(16), dp(4));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(titleView)
                .setView(content)
                .setCancelable(false)
                .setPositiveButton(buttonText, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (onSubmit.getAsBoolean()) {
                dialog.dismiss();
            } else {
                Toast.makeText(activity, "Vui lòng trả lời đầy đủ các câu hỏi", Toast.LENGTH_SHORT).show();
            }
        }));
        return dialog;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), 0, dp(16), dp(8));
        return layout;
    }

    private void bold(LinearLayout parent, String text) {
        TextView tv = text(parent, text, Typeface.BOLD);
        tv.setPadding(0, dp(14), 0, dp(2));
    }

    private TextView text(ViewGroup parent, String text, int style) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTypeface(null, style);
        tv.setTextColor(0xFF000000);
        tv.setPadding(0, dp(6), 0, 0);
        parent.addView(tv);
        return tv;
    }

    /** "Nhãn: [________]" trên cùng một dòng như trong bảng giấy. */
    private EditText labeledInput(LinearLayout parent, String label, int style) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = text(row, label, style);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        EditText et = new EditText(activity);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        row.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));
        parent.addView(row);
        return et;
    }

    /** "[________] lần/ngày" */
    private EditText timesPerDayInput(LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText et = new EditText(activity);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        row.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        text(row, "lần/ngày", Typeface.NORMAL);
        parent.addView(row);
        return et;
    }

    private static String value(EditText et) {
        return et.getText().toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /**
     * Nhóm radio chọn 1, xếp ngang và tự xuống dòng khi hết chỗ ("○ Không ○ Khó chịu không đáng kể
     * ○ Có ○ Rất khó chịu"). RadioGroup không tự xuống dòng nên tự lo phần loại trừ lẫn nhau.
     */
    private final class Choice {
        private final RadioButton[] buttons;

        Choice(LinearLayout parent, String[] options) {
            WrapLayout row = new WrapLayout(activity);
            buttons = new RadioButton[options.length];
            for (int i = 0; i < options.length; i++) {
                RadioButton rb = new RadioButton(activity);
                rb.setText(options[i]);
                rb.setTextColor(0xFF000000);
                rb.setPadding(0, 0, dp(12), 0);
                rb.setOnCheckedChangeListener((b, checked) -> {
                    if (!checked) {
                        return;
                    }
                    for (RadioButton other : buttons) {
                        if (other != b) {
                            other.setChecked(false);
                        }
                    }
                });
                buttons[i] = rb;
                row.addView(rb);
            }
            parent.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        /** Vị trí đang chọn, -1 nếu chưa chọn. */
        int selected() {
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].isChecked()) {
                    return i;
                }
            }
            return -1;
        }

        String code(String[] codes) {
            int i = selected();
            return i < 0 ? null : codes[i];
        }
    }

    /** ViewGroup xếp con theo hàng ngang, hết chiều rộng thì xuống dòng. */
    private static final class WrapLayout extends ViewGroup {
        WrapLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int childSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }
            setMeasuredDimension(width, place(width, false));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            place(r - l, true);
        }

        /** Xếp các con theo dòng; trả về tổng chiều cao. */
        private int place(int width, boolean layout) {
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int w = child.getMeasuredWidth();
                int h = child.getMeasuredHeight();
                if (x > 0 && x + w > width) {
                    x = 0;
                    y += rowHeight;
                    rowHeight = 0;
                }
                if (layout) {
                    child.layout(x, y, x + w, y + h);
                }
                x += w;
                rowHeight = Math.max(rowHeight, h);
            }
            return y + rowHeight;
        }
    }
}
