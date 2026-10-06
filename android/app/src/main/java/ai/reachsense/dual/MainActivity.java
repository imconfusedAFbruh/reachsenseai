package ai.reachsense.dual;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity implements DualCapture.Listener {
    private static final int CAMERA_PERMISSION = 1, EXPORT_REPORT = 2;
    private DualCapture capture;
    private LinearLayout content;
    private Spinner pairs, roles, saved;
    private EditText threshold;
    private Button scan, start, stop, export;
    private TextView status;
    private final ImageView[] previews = new ImageView[2];
    private final TextView[] labels = new TextView[2];
    private final CheckBox[] identities = new CheckBox[2];
    private final List<DualCapture.Pair> candidates = new ArrayList<>();
    private final List<File> reports = new ArrayList<>();
    private List<RunReport> pending = new ArrayList<>();
    private RunReport exportingPending;
    private File exporting;
    private byte[] exportingMemory;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(244, 247, 251));
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(24)); scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else scroll.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        text("ReachSense Dual", 28, true);
        text("S23 Ultra · camera verification", 16, false);
        text("Test two rear lenses together. Reports stay on this phone; preview images are not recorded.", 14, false);
        scan = button("Check cameras", () -> {
            if (requirePendingExport()) return;
            if (cameraPermission()) capture.discover(); else requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        });
        text("1  Select a physical-camera pair", 18, true);
        pairs = new Spinner(this); content.addView(pairs);
        pairs.setOnItemSelectedListener(new Selection() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { resetPreviews(); }
        });
        text("Maximum image timestamp difference (ms)", 14, false);
        threshold = new EditText(this); threshold.setText("5");
        threshold.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        threshold.setContentDescription("Exploratory maximum image timestamp difference in milliseconds");
        content.addView(threshold);
        text("Exploratory pairing filter. Exposure synchronization requires separate validation.", 13, false);
        LinearLayout actions = new LinearLayout(this);
        start = new Button(this); start.setText("Start 30-second test"); start.setOnClickListener(v -> begin());
        stop = new Button(this); stop.setText("Stop"); stop.setOnClickListener(v -> { stop.setEnabled(false); capture.stop("researcher_stopped"); });
        actions.addView(start, new LinearLayout.LayoutParams(0, dp(56), 2));
        actions.addView(stop, new LinearLayout.LayoutParams(0, dp(56), 1)); content.addView(actions);
        status = text("Grant camera access and check the available pairs.", 15, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        text("2  Verify both lenses", 18, true);
        for (int stream = 0; stream < 2; stream++) {
            labels[stream] = text("Stream " + (stream == 0 ? "A" : "B"), 16, true);
            previews[stream] = new ImageView(this); previews[stream].setBackgroundColor(Color.rgb(18, 29, 44));
            previews[stream].setScaleType(ImageView.ScaleType.FIT_CENTER);
            previews[stream].setContentDescription("Live preview for stream " + (stream == 0 ? "A" : "B"));
            content.addView(previews[stream], new LinearLayout.LayoutParams(-1, dp(220)));
        }
        text("Cover each physical lens in turn. Only its assigned preview should be obscured. Use the FOV and lens location to assign wide/ultrawide.", 14, false);
        roles = new Spinner(this);
        roles.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[] {"Choose lens roles after checking", "A = wide · B = ultrawide", "A = ultrawide · B = wide"}));
        content.addView(roles);
        roles.setOnItemSelectedListener(new Selection() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                for (CheckBox box : identities) if (box != null) { box.setChecked(false); box.setEnabled(capture != null && capture.isRunning() && position > 0); }
                if (capture != null) capture.assignRoles(position);
            }
        });
        for (int stream = 0; stream < 2; stream++) {
            final int index = stream;
            identities[stream] = new CheckBox(this);
            identities[stream].setText("I verified lens " + (stream == 0 ? "A" : "B") + " by covering it");
            identities[stream].setMinHeight(dp(48)); content.addView(identities[stream]);
            identities[stream].setOnCheckedChangeListener((view, checked) -> { if (capture != null) capture.confirm(index, checked); });
        }
        text("3  Export the evidence", 18, true);
        saved = new Spinner(this); content.addView(saved);
        export = button("Export selected JSON report", this::exportReport);
        text("A complete run demonstrates stream delivery. Stereo matching still needs camera calibration and an optical timing test, especially when synchronization is approximate or unknown.", 13, false);
        capture = new DualCapture(this, this);
        controls(false); refreshReports(null);
        if (state != null) {
            String name = state.getString("export_file");
            for (File file : reports) if (file.getName().equals(name)) exporting = file;
            String id = state.getString("export_pending_id");
            for (RunReport report : pending) if (report.data.optString("id").equals(id)) {
                exportingPending = report; exportingMemory = report.data.toString().getBytes(StandardCharsets.UTF_8);
            }
        }
        if (!requirePendingExport() && cameraPermission()) capture.discover();
    }
    private void begin() {
        if (requirePendingExport()) return;
        if (!cameraPermission()) { requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION); return; }
        int selection = pairs.getSelectedItemPosition();
        if (selection < 0 || selection >= candidates.size()) { status.setText("Check cameras and select an available pair first."); return; }
        try {
            long limit = TimingEvidence.pairingLimitNs(Double.parseDouble(threshold.getText().toString().trim()));
            roles.setSelection(0); resetPreviews();
            status.setText("Opening two physical-camera outputs…");
            capture.start(candidates.get(selection), limit); controls(true);
        } catch (IllegalArgumentException error) { threshold.setError(error.getMessage()); }
    }
    private void controls(boolean running) {
        boolean needsExport = !RunReport.pendingSnapshot().isEmpty();
        scan.setEnabled(!running && !needsExport); pairs.setEnabled(!running); threshold.setEnabled(!running);
        start.setEnabled(!running && !needsExport && !candidates.isEmpty()); stop.setEnabled(running); roles.setEnabled(running);
        for (CheckBox box : identities) box.setEnabled(running && roles.getSelectedItemPosition() > 0);
        saved.setEnabled(!running); export.setEnabled(!running && (!reports.isEmpty() || !pending.isEmpty()));
        if (running) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void resetPreviews() {
        int position = pairs.getSelectedItemPosition();
        for (int stream = 0; stream < 2; stream++) {
            if (previews[stream] != null) previews[stream].setImageDrawable(null);
            if (identities[stream] != null) identities[stream].setChecked(false);
            if (labels[stream] != null) labels[stream].setText("Stream " + (stream == 0 ? "A" : "B")
                + (position >= 0 && position < candidates.size() ? " · " + candidates.get(position).label(stream) : ""));
        }
    }
    @Override public void inventory(List<DualCapture.Pair> available, String message) {
        candidates.clear(); candidates.addAll(available);
        pairs.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, candidates));
        status.setText(message); controls(false); resetPreviews();
    }
    @Override public void preview(int stream, Bitmap bitmap) { previews[stream].setImageBitmap(bitmap); }
    @Override public void status(String message) { status.setText(message); }
    @Override public void finished(RunReport report, File file, String message) {
        org.json.JSONObject summary = report.data.optJSONObject("summary");
        String exposure = "Exposure synchronization unknown; optical timing test required.";
        if (summary != null && summary.optString("exposure_synchronization").startsWith("hardware_calibrated"))
            exposure = "Hardware-calibrated synchronization reported; stereo validation still required.";
        else if (summary != null && summary.optString("exposure_synchronization").startsWith("approximate"))
            exposure = "Approximate synchronization reported; optical timing test required.";
        status.setText(message + (summary == null ? "" : "\n" + summary.optInt("matched_pairs") + " image timestamp pairs.") + "\n" + exposure);
        refreshReports(file); controls(false);
    }
    private void refreshReports(File preferred) {
        pending = RunReport.pendingSnapshot();
        File[] files = new File(getFilesDir(), "reports").listFiles((dir, name) -> name.endsWith(".json"));
        reports.clear();
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            reports.addAll(Arrays.asList(files));
        }
        List<String> names = new ArrayList<>();
        for (RunReport report : pending) names.add("Unsaved — " + report.data.optString("id").substring(0, 8) + " — export now");
        java.text.DateFormat date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM);
        for (File report : reports) names.add(date.format(new java.util.Date(report.lastModified())) + " · " + report.getName().substring(4, 12));
        saved.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        if (preferred != null && reports.contains(preferred) && pending.isEmpty()) saved.setSelection(reports.indexOf(preferred));
        export.setEnabled(!reports.isEmpty() || !pending.isEmpty());
    }
    private void exportReport() {
        int selection = saved.getSelectedItemPosition();
        if (selection < 0) return;
        exportingMemory = null; exporting = null; exportingPending = null;
        if (selection < pending.size()) {
            exportingPending = pending.get(selection);
            exportingMemory = exportingPending.data.toString().getBytes(StandardCharsets.UTF_8);
        }
        else {
            int index = selection - pending.size();
            if (index >= 0 && index < reports.size()) exporting = reports.get(index);
        }
        if (exporting == null && exportingMemory == null) return;
        Intent document = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        document.putExtra(Intent.EXTRA_TITLE, exporting == null ? "reachsense-unsaved-report.json" : exporting.getName());
        try { startActivityForResult(document, EXPORT_REPORT); }
        catch (RuntimeException error) { status.setText("Cannot open export picker: " + error.getMessage()); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_REPORT || result != RESULT_OK || data == null || data.getData() == null) return;
        try {
          try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new java.io.IOException("Destination is unavailable");
            if (exportingMemory != null) output.write(exportingMemory);
            else if (exporting != null) try (FileInputStream input = new FileInputStream(exporting)) {
                byte[] buffer = new byte[8192]; int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            } else throw new java.io.IOException("Report selection was interrupted. Select and export it again.");
          }
            if (exportingPending != null) RunReport.pendingExported(exportingPending);
            refreshReports(null); controls(false);
            Toast.makeText(this, "Report exported", Toast.LENGTH_SHORT).show();
        } catch (Exception error) { status.setText("Export failed. The original report is retained: " + error.getMessage()); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != CAMERA_PERMISSION) return;
        if (cameraPermission()) capture.discover();
        else {
            status.setText("Camera permission was denied. Allow it in Settings → Apps → ReachSense Dual → Permissions, then check cameras again.");
            capture.diagnostic("permission_denied", "Camera access denied by user or system");
        }
    }
    @Override protected void onPause() { if (capture != null && capture.isRunning()) capture.stop("backgrounded"); super.onPause(); }
    @Override protected void onResume() {
        super.onResume();
        if (capture != null && !capture.isRunning()) { refreshReports(null); controls(false); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (exporting != null) state.putString("export_file", exporting.getName());
        if (exportingPending != null) state.putString("export_pending_id", exportingPending.data.optString("id"));
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { if (capture != null) capture.close(); super.onDestroy(); }
    private boolean cameraPermission() { return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED; }
    private boolean requirePendingExport() {
        if (RunReport.pendingSnapshot().isEmpty()) return false;
        refreshReports(null); controls(false);
        status.setText("Local storage failed. Keep the app open and export every pending report before starting another check.");
        return true;
    }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.rgb(25, 39, 55)); view.setPadding(0, dp(bold ? 18 : 8), 0, dp(8));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(view); return view;
    }
    private Button button(String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setMinHeight(dp(52));
        button.setOnClickListener(view -> action.run()); content.addView(button); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private abstract static class Selection implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) { }
    }
}
