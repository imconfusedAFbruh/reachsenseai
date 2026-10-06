package ai.reachsense.dual;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Contains metadata only. Pixel buffers are never serialized. Camera-thread owned. */
final class RunReport {
    final TimingEvidence timing;
    final JSONObject data, configuration;
    final JSONArray images = new JSONArray(), results = new JSONArray(), events = new JSONArray();
    private boolean completed;

    RunReport(JSONObject device, JSONObject inventory, JSONObject configuration, long limitNs) {
        this.configuration = configuration;
        timing = new TimingEvidence(limitNs, 120);
        data = object("schema_version", 1, "id", UUID.randomUUID().toString(),
            "started_utc", Instant.now().toString(), "device", device, "inventory", inventory,
            "configuration", configuration, "maximum_pair_difference_ns", limitNs,
            "pairing_threshold_validated", false, "callback_clock", "System.nanoTime",
            "images", images, "capture_results", results, "events", events,
            "participant_imagery_stored", false);
    }
    void image(int stream, long timestamp, long arrival) {
        TimingEvidence.Frame frame = timing.observe(stream, timestamp);
        images.put(object("index", frame.index, "stream", stream,
            "image_timestamp_ns", timestamp, "arrival_ns", arrival));
    }
    void result(long frameNumber, Long logicalTimestamp, long arrival, JSONObject[] physical) {
        results.put(object("index", results.length(), "frame_number", frameNumber,
            "logical_sensor_timestamp_ns", logicalTimestamp, "arrival_ns", arrival,
            "physical", new JSONArray().put(physical[0] == null ? JSONObject.NULL : physical[0])
                .put(physical[1] == null ? JSONObject.NULL : physical[1])));
    }
    void event(String reason, String detail, long arrival) {
        events.put(object("reason", reason, "detail", detail, "arrival_ns", arrival));
    }
    JSONObject complete(String reason, long durationNs, boolean[] identities) {
        if (completed) return data;
        timing.finish();
        TimingEvidence.MetadataIndex[] indices = {new TimingEvidence.MetadataIndex(), new TimingEvidence.MetadataIndex()};
        try {
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                JSONArray physical = result.getJSONArray("physical");
                for (int stream = 0; stream < 2; stream++) {
                    if (!physical.isNull(stream)) indices[stream].add(i,
                        number(result, "logical_sensor_timestamp_ns"), number(physical.getJSONObject(stream), "sensor_timestamp_ns"));
                }
            }
            for (TimingEvidence.Frame frame : timing.frames) {
                TimingEvidence.Association association = indices[frame.stream].lookup(frame.timestampNs);
                JSONObject image = images.getJSONObject(frame.index);
                image.put("pair_reason", frame.reason).put("partner_index", frame.partnerIndex)
                    .put("result_index", association.resultIndex).put("association_basis", association.basis)
                    .put("association_reason", association.reason);
            }
            JSONArray pairs = new JSONArray();
            for (TimingEvidence.Pair pair : timing.pairs) {
                JSONObject row = object("first_image_index", pair.firstIndex, "second_image_index", pair.secondIndex,
                    "image_timestamp_difference_ns", pair.differenceNs,
                    "physical_sensor_timestamp_difference_ns", null, "physical_clock_comparable", false);
                int a = images.getJSONObject(pair.firstIndex).getInt("result_index");
                int b = images.getJSONObject(pair.secondIndex).getInt("result_index");
                row.put("same_capture_result", a >= 0 && a == b);
                if (a >= 0 && b >= 0) {
                    Long first = number(results.getJSONObject(a).getJSONArray("physical").getJSONObject(0), "sensor_timestamp_ns");
                    Long second = number(results.getJSONObject(b).getJSONArray("physical").getJSONObject(1), "sensor_timestamp_ns");
                    JSONArray sensors = configuration.optJSONArray("physical");
                    boolean comparable = sensors != null && sensors.length() == 2
                        && sensors.getJSONObject(0).optInt("timestamp_source", -1) == 1
                        && sensors.getJSONObject(1).optInt("timestamp_source", -1) == 1;
                    if (first != null && second != null && first > 0 && second > 0 && comparable) {
                        row.put("physical_clock_comparable", true)
                            .put("physical_sensor_timestamp_difference_ns", Math.abs(first - second));
                    }
                }
                pairs.put(row);
            }
            List<Long> differences = new ArrayList<>();
            for (TimingEvidence.Pair pair : timing.pairs) differences.add(pair.differenceNs);
            Collections.sort(differences);
            Integer sync = configuration.isNull("sync_type") || !configuration.has("sync_type") ? null : configuration.getInt("sync_type");
            JSONObject summary = object("dual_stream_delivery", reason.equals("completed") && timing.counts[0] >= 2 && timing.counts[1] >= 2
                    ? "observed_for_completed_run" : "not_completed",
                "lens_identity_confirmed", new JSONArray().put(identities[0]).put(identities[1]),
                "matched_pairs", timing.pairs.size(), "streams", new JSONArray().put(streamSummary(0)).put(streamSummary(1)),
                "median_image_timestamp_difference_ns", percentile(differences, 0.5),
                "p95_image_timestamp_difference_ns", percentile(differences, 0.95),
                "maximum_image_timestamp_difference_ns", percentile(differences, 1),
                "exposure_synchronization", TimingEvidence.exposureAssessment(sync), "stereo_readiness", "unvalidated");
            data.put("end_reason", reason).put("ended_utc", Instant.now().toString())
                .put("duration_ns", durationNs).put("summary", summary).put("pairs", pairs);
            completed = true;
            return data;
        } catch (JSONException error) { throw new IllegalStateException("Cannot finalize timing report", error); }
    }
    private JSONObject streamSummary(int stream) {
        List<Long> intervals = new ArrayList<>();
        long first = 0, last = 0;
        int valid = 0, rejected = 0;
        for (TimingEvidence.Frame frame : timing.frames) {
            if (frame.stream != stream) continue;
            if (frame.timestampNs <= last || frame.timestampNs <= 0) { rejected++; continue; }
            if (first == 0) first = frame.timestampNs;
            if (last > 0) intervals.add(frame.timestampNs - last);
            last = frame.timestampNs; valid++;
        }
        Collections.sort(intervals);
        Long median = percentile(intervals, 0.5);
        int gaps = 0;
        if (median != null) for (Long interval : intervals) if (interval / (double) median > 2) gaps++;
        return object("stream", stream, "observed_frames", timing.counts[stream],
            "unmatched_frames", timing.unmatched(stream), "pair_queue_overflows", timing.overflows[stream],
            "rejected_timestamps", rejected, "observed_fps", valid > 1 && last > first ? (valid - 1) * 1e9 / (last - first) : null,
            "median_interval_ns", median, "maximum_interval_ns", percentile(intervals, 1),
            "gaps_over_twice_median_interval", gaps, "gap_count_is_sensor_drop_count", false);
    }
    File save(File directory) throws IOException {
        if (!completed) throw new IllegalStateException("Report has not been finalized");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create report directory");
        File destination = new File(directory, "run-" + data.optString("id") + ".json");
        File temporary = File.createTempFile("report-", ".pending", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(data.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
            return destination;
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }
    private static Long percentile(List<Long> values, double quantile) {
        if (values.isEmpty()) return null;
        return values.get(Math.max(0, (int) Math.ceil(values.size() * quantile) - 1));
    }
    private static Long number(JSONObject object, String key) throws JSONException {
        return !object.has(key) || object.isNull(key) ? null : object.getLong(key);
    }
    static JSONObject object(Object... entries) {
        JSONObject object = new JSONObject();
        try {
            for (int i = 0; i < entries.length; i += 2) object.put((String) entries[i], entries[i + 1] == null ? JSONObject.NULL : entries[i + 1]);
            return object;
        } catch (JSONException error) { throw new IllegalArgumentException("Invalid report value", error); }
    }
}
