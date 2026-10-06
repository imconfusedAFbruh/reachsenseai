package ai.reachsense.dual;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** One logical device, two physical outputs. All camera/report state lives on worker. */
final class DualCapture {
    interface Listener {
        void inventory(List<Pair> pairs, String message);
        void preview(int stream, Bitmap bitmap);
        void status(String message);
        void finished(RunReport report, File file, String message);
    }
    static final class Pair {
        final String logicalId;
        final String[] ids;
        final CameraCharacteristics logical;
        final CameraCharacteristics[] sensors;
        final Size size;
        Pair(String logicalId, String first, String second, CameraCharacteristics logical,
             CameraCharacteristics a, CameraCharacteristics b, Size size) {
            this.logicalId = logicalId; ids = new String[] {first, second};
            this.logical = logical; sensors = new CameraCharacteristics[] {a, b}; this.size = size;
        }
        JSONObject configuration() {
            return RunReport.object("logical_id", logicalId, "physical_ids", new JSONArray(Arrays.asList(ids)),
                "width", size.getWidth(), "height", size.getHeight(), "format", "YUV_420_888",
                "sync_type", logical.get(CameraCharacteristics.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE),
                "physical", new JSONArray().put(sensorInfo(ids[0], sensors[0])).put(sensorInfo(ids[1], sensors[1])));
        }
        String label(int stream) {
            JSONObject info = sensorInfo(ids[stream], sensors[stream]);
            return "ID " + ids[stream] + " · " + (info.isNull("estimated_horizontal_fov_degrees") ? "FOV unavailable"
                : String.format(java.util.Locale.US, "%.0f° FOV", info.optDouble("estimated_horizontal_fov_degrees")));
        }
        @Override public String toString() { return logicalId + ": " + label(0) + " / " + label(1) + " · " + size; }
    }
    private final Activity activity;
    private final Listener listener;
    private final CameraManager manager;
    private final HandlerThread thread = new HandlerThread("dual-camera");
    private final Handler worker;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private JSONObject inventory = RunReport.object("cameras", new JSONArray());
    private CameraDevice camera;
    private CameraCaptureSession session;
    private final ImageReader[] readers = new ImageReader[2];
    private volatile RunReport report;
    private Pair selected;
    private volatile int generation;
    private volatile boolean running;
    private final boolean[] previewPending = new boolean[2], identities = new boolean[2];
    private final long[] lastFrame = new long[2], lastPreview = new long[2];
    private long started, captureStarted;
    private volatile boolean closing;
    private boolean configured;

    DualCapture(Activity activity, Listener listener) {
        this.activity = activity; this.listener = listener;
        manager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
        thread.start(); worker = new Handler(thread.getLooper());
    }
    boolean isRunning() { return running; }
    void discover() {
        worker.post(() -> {
            if (running || closing) return;
            List<Pair> pairs = new ArrayList<>();
            JSONArray cameras = new JSONArray();
            try {
                for (String id : manager.getCameraIdList()) {
                    CameraCharacteristics logical = manager.getCameraCharacteristics(id);
                    JSONObject item = sensorInfo(id, logical);
                    cameras.put(item);
                    Integer facing = logical.get(CameraCharacteristics.LENS_FACING);
                    if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
                    int[] capabilities = logical.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    if (capabilities == null || Arrays.stream(capabilities).noneMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) continue;
                    List<String> ids = new ArrayList<>(logical.getPhysicalCameraIds());
                    java.util.Collections.sort(ids);
                    JSONArray physical = new JSONArray();
                    List<CameraCharacteristics> sensors = new ArrayList<>();
                    for (String physicalId : ids) {
                        try {
                            CameraCharacteristics sensor = manager.getCameraCharacteristics(physicalId);
                            sensors.add(sensor); physical.put(sensorInfo(physicalId, sensor));
                        } catch (CameraAccessException | IllegalArgumentException | SecurityException error) {
                            sensors.add(null); physical.put(RunReport.object("id", physicalId, "error", error.toString()));
                        }
                    }
                    item.put("physical", physical).put("sync_type", logical.get(CameraCharacteristics.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE));
                    for (int a = 0; a < ids.size(); a++) for (int b = a + 1; b < ids.size(); b++) {
                        if (sensors.get(a) == null || sensors.get(b) == null) continue;
                        Size common = commonSize(logical, sensors.get(a), sensors.get(b));
                        if (common != null) pairs.add(new Pair(id, ids.get(a), ids.get(b), logical, sensors.get(a), sensors.get(b), common));
                    }
                }
                inventory = RunReport.object("cameras", cameras);
                String message = pairs.isEmpty() ? "No eligible rear physical-camera pair is exposed. Export the capability report."
                    : "Choose the two lenses using their IDs/FOV, then verify each by covering its lens.";
                saveDiagnostic("capability_scan", message);
                main.post(() -> { if (!closing) listener.inventory(pairs, message); });
            } catch (Exception error) {
                inventory = RunReport.object("cameras", cameras, "scan_error", error.toString());
                saveDiagnostic("capability_scan_failed", error.toString());
                main.post(() -> { if (!closing) listener.inventory(pairs, "Camera discovery failed: " + error.getMessage()); });
            }
        });
    }
    void diagnostic(String reason, String detail) { worker.post(() -> saveDiagnostic(reason, detail)); }
    private JSONObject device() {
        return RunReport.object("manufacturer", Build.MANUFACTURER, "model", Build.MODEL,
            "android_release", Build.VERSION.RELEASE, "sdk", Build.VERSION.SDK_INT, "fingerprint", Build.FINGERPRINT,
            "app_version", BuildConfig.VERSION_NAME, "version_code", BuildConfig.VERSION_CODE, "source_commit", BuildConfig.SOURCE_COMMIT);
    }
    private void saveDiagnostic(String reason, String detail) {
        RunReport diagnostic = new RunReport(device(), inventory, new JSONObject(), 5_000_000);
        diagnostic.event(reason, detail, System.nanoTime());
        diagnostic.complete(reason, 0, new boolean[2]);
        deliverSaved(diagnostic, detail);
    }
    void start(Pair pair, long limitNs) {
        if (running || closing) return;
        running = true;
        worker.post(() -> {
            if (closing) { running = false; return; }
            final int run = ++generation;
            selected = pair;
            Arrays.fill(identities, false); Arrays.fill(previewPending, false);
            Arrays.fill(lastFrame, 0); Arrays.fill(lastPreview, 0);
            configured = false; started = System.nanoTime(); captureStarted = 0;
            report = new RunReport(device(), inventory, pair.configuration(), limitNs);
            if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                finish("permission_denied"); return;
            }
            try {
                for (int stream = 0; stream < 2; stream++) {
                    final int index = stream;
                    readers[stream] = ImageReader.newInstance(pair.size.getWidth(), pair.size.getHeight(), ImageFormat.YUV_420_888, 4);
                    readers[stream].setOnImageAvailableListener(reader -> images(reader, index, run), worker);
                }
                worker.postDelayed(() -> { if (current(run) && !configured) finish("camera_setup_timeout"); }, 10_000);
                manager.openCamera(pair.logicalId, new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice device) {
                        if (!current(run)) { device.close(); return; }
                        camera = device; configure(run);
                    }
                    @Override public void onDisconnected(CameraDevice device) {
                        device.close(); if (current(run)) finish("camera_disconnected");
                    }
                    @Override public void onError(CameraDevice device, int error) {
                        device.close(); if (current(run)) { report.event("camera_error", Integer.toString(error), System.nanoTime()); finish("camera_error"); }
                    }
                }, worker);
            } catch (CameraAccessException | RuntimeException error) { failure("camera_open_failed", error); }
        });
    }
    private boolean current(int run) { return running && generation == run && report != null; }
    private void configure(int run) {
        try {
            List<OutputConfiguration> outputs = new ArrayList<>();
            for (int stream = 0; stream < 2; stream++) {
                OutputConfiguration output = new OutputConfiguration(readers[stream].getSurface());
                output.setPhysicalCameraId(selected.ids[stream]); outputs.add(output);
            }
            SessionConfiguration config = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, command -> worker.post(command), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession created) {
                    if (!current(run)) { created.close(); return; }
                    session = created;
                    try {
                        CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        for (ImageReader reader : readers) request.addTarget(reader.getSurface());
                        Range<Integer>[] ranges = selected.logical.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                        Range<Integer> fps = ranges == null ? null : Arrays.stream(ranges).filter(range -> range.getUpper() <= 30)
                            .max(Comparator.comparingInt((Range<Integer> r) -> r.getUpper()).thenComparingInt(r -> r.getLower())).orElse(null);
                        if (fps != null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps);
                        request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
                        request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                        report.configuration.put("requested_fps_range", fps == null ? JSONObject.NULL : new JSONArray().put(fps.getLower()).put(fps.getUpper()));
                        session.setRepeatingRequest(request.build(), callbacks(run), worker);
                        configured = true; captureStarted = System.nanoTime();
                        report.event("session_configured", "Two physical YUV outputs in one logical session", captureStarted);
                        worker.postDelayed(() -> { if (current(run)) finish("completed"); }, 30_000);
                        watch(run);
                    } catch (Exception error) { failure("capture_request_failed", error); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) {
                    failed.close(); if (current(run)) finish("simultaneous_session_rejected");
                }
            });
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    boolean supported = camera.isSessionConfigurationSupported(config);
                    report.configuration.put("session_support_probe", supported);
                    if (!supported) { finish("simultaneous_session_unsupported"); return; }
                } else report.configuration.put("session_support_probe", "not_available_on_api_28");
            } catch (UnsupportedOperationException error) {
                report.configuration.put("session_support_probe", "not_available");
            }
            camera.createCaptureSession(config);
        } catch (Exception error) { failure("session_creation_failed", error); }
    }
    private CameraCaptureSession.CaptureCallback callbacks(int run) {
        return new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(CameraCaptureSession active, CaptureRequest request, TotalCaptureResult result) {
                if (!current(run)) return;
                Map<String, ? extends CaptureResult> physical = Build.VERSION.SDK_INT >= 31
                    ? result.getPhysicalCameraTotalResults() : result.getPhysicalCameraResults();
                JSONObject[] metadata = new JSONObject[2];
                for (int stream = 0; stream < 2; stream++) {
                    CaptureResult value = physical.get(selected.ids[stream]);
                    if (value != null) metadata[stream] = RunReport.object("physical_id", selected.ids[stream],
                        "sensor_timestamp_ns", value.get(CaptureResult.SENSOR_TIMESTAMP),
                        "exposure_duration_ns", value.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                        "frame_duration_ns", value.get(CaptureResult.SENSOR_FRAME_DURATION),
                        "rolling_shutter_skew_ns", value.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW),
                        "focal_length_mm", value.get(CaptureResult.LENS_FOCAL_LENGTH),
                        "focus_distance_diopters", value.get(CaptureResult.LENS_FOCUS_DISTANCE),
                        "optical_stabilization_mode", value.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE),
                        "video_stabilization_mode", value.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE));
                }
                report.result(result.getFrameNumber(), result.get(CaptureResult.SENSOR_TIMESTAMP), System.nanoTime(), metadata);
            }
            @Override public void onCaptureFailed(CameraCaptureSession active, CaptureRequest request, CaptureFailure failed) {
                if (current(run)) { report.event("capture_failure", "Frame " + failed.getFrameNumber() + ", reason " + failed.getReason(), System.nanoTime()); finish("capture_failure"); }
            }
            @Override public void onCaptureBufferLost(CameraCaptureSession active, CaptureRequest request, android.view.Surface target, long frameNumber) {
                if (current(run)) { report.event("buffer_lost", "Frame " + frameNumber, System.nanoTime()); finish("buffer_lost"); }
            }
        };
    }
    private void images(ImageReader reader, int stream, int run) {
        if (!current(run)) return;
        try {
            Image image;
            while (current(run) && (image = reader.acquireNextImage()) != null) {
                try (Image observed = image) {
                    long now = System.nanoTime();
                    report.image(stream, observed.getTimestamp(), now); lastFrame[stream] = now;
                    if (!previewPending[stream] && now - lastPreview[stream] >= 200_000_000) {
                        Bitmap bitmap = previewBitmap(observed, selected.sensors[stream]);
                        previewPending[stream] = true; lastPreview[stream] = now;
                        main.post(() -> {
                            if (current(run) && !closing) listener.preview(stream, bitmap);
                            worker.post(() -> { if (generation == run) previewPending[stream] = false; });
                        });
                    }
                }
            }
        } catch (RuntimeException error) { if (current(run)) failure("image_acquisition_failed", error); }
    }
    private void watch(int run) {
        if (!current(run)) return;
        long now = System.nanoTime();
        for (int stream = 0; stream < 2; stream++) {
            if (now - (lastFrame[stream] == 0 ? captureStarted : lastFrame[stream]) >= 3_000_000_000L) {
                report.event("stream_stalled", "Stream " + stream, now); finish("stream_stalled"); return;
            }
        }
        String message = String.format(java.util.Locale.US, "%.0f / 30 s · A: %d frames · B: %d frames\n%d timestamp pairs · exposure synchronization still needs validation",
            (now - captureStarted) / 1e9, report.timing.counts[0], report.timing.counts[1], report.timing.pairs.size());
        main.post(() -> { if (current(run) && !closing) listener.status(message); });
        worker.postDelayed(() -> watch(run), 500);
    }
    void confirm(int stream, boolean confirmed) { worker.post(() -> { if (running) identities[stream] = confirmed; }); }
    void assignRoles(int choice) {
        worker.post(() -> {
            if (report == null || selected == null) return;
            Arrays.fill(identities, false);
            try {
                report.configuration.put("wide_physical_id", choice == 0 ? JSONObject.NULL : selected.ids[choice == 1 ? 0 : 1]);
                report.configuration.put("ultrawide_physical_id", choice == 0 ? JSONObject.NULL : selected.ids[choice == 1 ? 1 : 0]);
                report.event("operator_lens_assignment", Integer.toString(choice), System.nanoTime());
            } catch (org.json.JSONException error) { failure("lens_assignment_failed", error); }
        });
    }
    void stop(String reason) { worker.post(() -> { if (report != null) finish(reason); }); }
    void close() { closing = true; worker.post(() -> { if (report != null) finish("activity_destroyed"); thread.quitSafely(); }); }
    private void failure(String reason, Exception error) {
        if (report != null) { report.event(reason, error.toString(), System.nanoTime()); finish(reason); }
    }
    private void finish(String reason) {
        RunReport completed = report;
        if (completed == null) return;
        report = null; generation++;
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
        for (int stream = 0; stream < 2; stream++) {
            if (readers[stream] != null) { readers[stream].close(); readers[stream] = null; }
        }
        completed.complete(reason, System.nanoTime() - started, identities.clone());
        running = false;
        deliverSaved(completed, "Capture ended: " + reason);
    }
    private void deliverSaved(RunReport completed, String message) {
        File saved = null;
        try { saved = completed.save(new File(activity.getFilesDir(), "reports")); }
        catch (IOException error) { message += "\nLocal save failed; export this in-memory report now: " + error.getMessage(); }
        File file = saved; String detail = message;
        main.post(() -> { if (!closing) listener.finished(completed, file, detail); });
    }
    private static JSONObject sensorInfo(String id, CameraCharacteristics sensor) {
        float[] focal = sensor.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF dimensions = sensor.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        Double fov = focal != null && focal.length > 0 && focal[0] > 0 && dimensions != null
            ? Math.toDegrees(2 * Math.atan(dimensions.getWidth() / (2 * focal[0]))) : null;
        JSONArray focalLengths = new JSONArray();
        if (focal != null) for (float value : focal) focalLengths.put((Object) value);
        return RunReport.object("id", id, "lens_facing", sensor.get(CameraCharacteristics.LENS_FACING),
            "focal_lengths_mm", focal == null ? JSONObject.NULL : focalLengths,
            "sensor_width_mm", dimensions == null ? null : dimensions.getWidth(),
            "sensor_height_mm", dimensions == null ? null : dimensions.getHeight(), "estimated_horizontal_fov_degrees", fov,
            "timestamp_source", sensor.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE),
            "sensor_orientation", sensor.get(CameraCharacteristics.SENSOR_ORIENTATION));
    }
    private static Size commonSize(CameraCharacteristics logical, CameraCharacteristics first, CameraCharacteristics second) {
        StreamConfigurationMap map = logical.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        StreamConfigurationMap a = first.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        StreamConfigurationMap b = second.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null || a == null || b == null) return null;
        Size[] logicalSizes = map.getOutputSizes(ImageFormat.YUV_420_888), firstSizes = a.getOutputSizes(ImageFormat.YUV_420_888), secondSizes = b.getOutputSizes(ImageFormat.YUV_420_888);
        if (logicalSizes == null || firstSizes == null || secondSizes == null) return null;
        List<Size> candidates = new ArrayList<>();
        for (Size size : logicalSizes) if (Arrays.asList(firstSizes).contains(size) && Arrays.asList(secondSizes).contains(size)) candidates.add(size);
        // ponytail: one modest resolution per pair; add a resolution picker only if device validation needs it.
        return candidates.stream().min(Comparator.comparingLong(size -> Math.abs((long) size.getWidth() * size.getHeight() - 640L * 480))).orElse(null);
    }
    private Bitmap previewBitmap(Image image, CameraCharacteristics sensor) {
        Rect crop = image.getCropRect();
        int step = Math.max(1, (int) Math.ceil(crop.width() / 320.0));
        int width = (crop.width() + step - 1) / step, height = (crop.height() + step - 1) / step;
        int[] pixels = new int[width * height];
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer[] buffers = {planes[0].getBuffer(), planes[1].getBuffer(), planes[2].getBuffer()};
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int sx = crop.left + x * step, sy = crop.top + y * step;
            int yy = sample(planes[0], buffers[0], sx, sy), u = sample(planes[1], buffers[1], sx / 2, sy / 2) - 128;
            int v = sample(planes[2], buffers[2], sx / 2, sy / 2) - 128;
            int c = Math.max(0, yy - 16);
            int red = clamp((298 * c + 409 * v + 128) >> 8), green = clamp((298 * c - 100 * u - 208 * v + 128) >> 8);
            int blue = clamp((298 * c + 516 * u + 128) >> 8);
            pixels[y * width + x] = 0xff000000 | red << 16 | green << 8 | blue;
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        Integer orientation = sensor.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int display = activity.getWindowManager().getDefaultDisplay().getRotation() * 90;
        Matrix rotation = new Matrix(); rotation.postRotate(((orientation == null ? 0 : orientation) - display + 360) % 360);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, rotation, false);
    }
    private static int sample(Image.Plane plane, ByteBuffer buffer, int x, int y) { return buffer.get(buffer.position() + y * plane.getRowStride() + x * plane.getPixelStride()) & 255; }
    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
}
