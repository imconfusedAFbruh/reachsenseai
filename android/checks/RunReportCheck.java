package ai.reachsense.dual;

import java.io.File;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;

public final class RunReportCheck {
    public static void main(String[] args) throws Exception {
        RunReport report = new RunReport(new JSONObject(), new JSONObject(), new JSONObject(), 5_000_000);
        report.image(0, 10_000_000, 100);
        report.image(1, 10_000_000, 110);
        report.image(0, 40_000_000, 120);
        report.result(1, 10_000_000L, 130, new JSONObject[] {
            RunReport.object("sensor_timestamp_ns", 9_000_000L), null
        });
        report.event("capture_failure", "device error", 140);
        JSONObject data = report.complete("backgrounded", 1_000_000_000, new boolean[] {true, false});
        JSONArray images = data.getJSONArray("images");
        assert images.length() == 3;
        assert images.getJSONObject(0).getInt("result_index") == 0 : "Late metadata still associates";
        assert images.getJSONObject(1).getString("association_reason").equals("missing_result");
        assert images.getJSONObject(2).getString("pair_reason").equals("end_of_run_unmatched");
        assert data.getJSONObject("summary").getString("dual_stream_delivery").equals("not_completed");
        assert data.getJSONObject("summary").getString("stereo_readiness").equals("unvalidated");
        assert data.getJSONArray("capture_results").getJSONObject(0).getJSONArray("physical").isNull(1);
        assert data.getJSONArray("events").length() == 1;
        File directory = Files.createTempDirectory("reachsense-report-check").toFile();
        File first = report.save(directory);
        JSONObject restored = new JSONObject(Files.readString(first.toPath()));
        assert restored.getString("end_reason").equals("backgrounded");
        RunReport second = new RunReport(new JSONObject(), new JSONObject(), new JSONObject(), 5);
        second.complete("permission_denied", 0, new boolean[2]);
        File next = second.save(directory);
        assert !next.equals(first) && first.isFile() && next.isFile() : "Reports cannot overwrite each other";
        assert restored.getJSONArray("images").length() == 3;
        File notDirectory = new File(directory, "regular-file");
        Files.writeString(notDirectory.toPath(), "keep");
        try { report.save(notDirectory); throw new AssertionError("Expected save failure"); }
        catch (java.io.IOException expected) { assert Files.readString(notDirectory.toPath()).equals("keep"); }
        for (File file : directory.listFiles()) Files.delete(file.toPath());
        Files.delete(directory.toPath());
        System.out.println("RunReport checks passed");
    }
}
