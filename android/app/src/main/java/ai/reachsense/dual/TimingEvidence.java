package ai.reachsense.dual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Image timestamp evidence, deliberately separate from exposure synchronization. */
public final class TimingEvidence {
    public static final class Frame {
        public final int index, stream;
        public final long timestampNs;
        public int partnerIndex = -1;
        public String reason = "pending";
        Frame(int index, int stream, long timestampNs) {
            this.index = index; this.stream = stream; this.timestampNs = timestampNs;
        }
    }
    public static final class Pair {
        public final int firstIndex, secondIndex;
        public final long differenceNs;
        Pair(Frame first, Frame second) {
            firstIndex = first.index; secondIndex = second.index;
            differenceNs = Math.abs(first.timestampNs - second.timestampNs);
        }
    }
    public final List<Frame> frames = new ArrayList<>();
    public final List<Pair> pairs = new ArrayList<>();
    public final int[] counts = new int[2], overflows = new int[2];
    private final long maximumDifferenceNs;
    private final int queueLimit;
    private final List<ArrayDeque<Frame>> queues = Arrays.asList(new ArrayDeque<>(), new ArrayDeque<>());
    private final long[] lastTimestamp = new long[2];
    private boolean finished;

    public TimingEvidence(long maximumDifferenceNs, int queueLimit) {
        if (maximumDifferenceNs <= 0 || queueLimit <= 0) throw new IllegalArgumentException("Positive pairing limit and queue size required");
        this.maximumDifferenceNs = maximumDifferenceNs; this.queueLimit = queueLimit;
    }
    public Frame observe(int stream, long timestampNs) {
        if (finished) throw new IllegalStateException("Run is finished");
        if (stream < 0 || stream > 1) throw new IllegalArgumentException("Unknown stream");
        Frame frame = new Frame(frames.size(), stream, timestampNs);
        frames.add(frame); counts[stream]++;
        if (timestampNs <= 0) { frame.reason = "invalid_timestamp"; return frame; }
        if (timestampNs <= lastTimestamp[stream]) { frame.reason = "nonmonotonic_timestamp"; return frame; }
        lastTimestamp[stream] = timestampNs;
        ArrayDeque<Frame> queue = queues.get(stream);
        if (queue.size() == queueLimit) {
            queue.removeFirst().reason = "queue_overflow";
            overflows[stream]++;
        }
        queue.addLast(frame);
        while (!queues.get(0).isEmpty() && !queues.get(1).isEmpty()) {
            Frame first = queues.get(0).getFirst(), second = queues.get(1).getFirst();
            if (Math.abs(first.timestampNs - second.timestampNs) <= maximumDifferenceNs) {
                queues.get(0).removeFirst(); queues.get(1).removeFirst();
                first.reason = second.reason = "paired";
                first.partnerIndex = second.index; second.partnerIndex = first.index;
                pairs.add(new Pair(first, second));
            } else {
                queues.get(first.timestampNs < second.timestampNs ? 0 : 1).removeFirst().reason = "outside_pairing_limit";
            }
        }
        return frame;
    }
    public void finish() {
        for (ArrayDeque<Frame> queue : queues) {
            while (!queue.isEmpty()) queue.removeFirst().reason = "end_of_run_unmatched";
        }
        finished = true;
    }
    public int unmatched(int stream) { return counts[stream] - pairs.size(); }
    public static long pairingLimitNs(double milliseconds) {
        double ns = milliseconds * 1_000_000;
        if (!Double.isFinite(ns) || ns < 1 || ns >= Long.MAX_VALUE) throw new IllegalArgumentException("Enter a positive finite pairing limit of at least 0.000001 ms");
        return (long) ns;
    }
    public static String exposureAssessment(Integer syncType) {
        if (syncType != null && syncType == 1) return "hardware_calibrated_reported_requires_stereo_validation";
        if (syncType != null && syncType == 0) return "approximate_requires_optical_timing_test";
        return "unknown_requires_optical_timing_test";
    }

    public static final class Association {
        public final int resultIndex;
        public final String basis, reason;
        Association(int index, String basis, String reason) { resultIndex = index; this.basis = basis; this.reason = reason; }
    }
    /** Results may arrive before or after images; associate only at finalization. */
    public static final class MetadataIndex {
        private final Map<Long, List<Association>> entries = new HashMap<>();
        public void add(int resultIndex, Long logicalTimestamp, Long physicalTimestamp) {
            insert(resultIndex, logicalTimestamp, "logical_sensor_timestamp");
            if (physicalTimestamp != null && !physicalTimestamp.equals(logicalTimestamp)) insert(resultIndex, physicalTimestamp, "physical_sensor_timestamp");
        }
        private void insert(int index, Long timestamp, String basis) {
            if (timestamp != null && timestamp > 0) entries.computeIfAbsent(timestamp, key -> new ArrayList<>()).add(new Association(index, basis, "unique_timestamp_match"));
        }
        public Association lookup(long timestamp) {
            List<Association> matches = entries.get(timestamp);
            if (matches == null) return new Association(-1, "none", "missing_result");
            if (matches.size() != 1) return new Association(-1, "none", "ambiguous_result");
            return matches.get(0);
        }
    }
}
