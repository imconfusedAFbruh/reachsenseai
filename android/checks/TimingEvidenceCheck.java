package ai.reachsense.dual;

public final class TimingEvidenceCheck {
    public static void main(String[] args) {
        TimingEvidence timing = new TimingEvidence(5_000_000, 120);
        timing.observe(0, 10_000_000);
        timing.observe(1, 15_000_000);
        timing.observe(0, 40_000_000);
        timing.observe(1, 45_000_001);
        timing.observe(0, 70_000_000);
        timing.observe(1, 71_000_000);
        timing.observe(1, 72_000_000);
        timing.finish();
        assert timing.pairs.size() == 2 : "Inclusive boundary and later match";
        assert timing.pairs.get(0).differenceNs == 5_000_000;
        assert timing.pairs.get(1).differenceNs == 1_000_000;
        assert timing.unmatched(0) == 1 && timing.unmatched(1) == 2;
        assert timing.frames.get(0).partnerIndex == 1;
        assert timing.frames.get(4).partnerIndex == 5 : "A frame cannot be reused";

        TimingEvidence disorder = new TimingEvidence(5, 2);
        disorder.observe(0, 20);
        disorder.observe(0, 20);
        disorder.observe(0, 19);
        disorder.observe(1, 0);
        disorder.observe(1, 21);
        disorder.finish();
        assert disorder.pairs.size() == 1;
        assert disorder.frames.get(1).reason.equals("nonmonotonic_timestamp");
        assert disorder.frames.get(2).reason.equals("nonmonotonic_timestamp");
        assert disorder.frames.get(3).reason.equals("invalid_timestamp");
        assert disorder.unmatched(0) == 2 && disorder.unmatched(1) == 1;

        TimingEvidence overflow = new TimingEvidence(5, 2);
        overflow.observe(0, 10);
        overflow.observe(0, 20);
        overflow.observe(0, 30);
        overflow.observe(1, 20);
        overflow.finish();
        assert overflow.overflows[0] == 1;
        assert overflow.frames.get(0).reason.equals("queue_overflow");
        assert overflow.frames.get(2).reason.equals("end_of_run_unmatched");
        assert overflow.pairs.size() == 1;
        assert overflow.unmatched(0) == 2;
        overflow.finish();
        assert overflow.pairs.size() == 1 : "Finish is idempotent";
        rejects(() -> overflow.observe(0, 40));
        rejects(() -> new TimingEvidence(0, 2));
        rejects(() -> new TimingEvidence(1, 0));
        rejects(() -> new TimingEvidence(1, 2).observe(2, 10));
        rejects(() -> TimingEvidence.pairingLimitNs(Double.NaN));
        rejects(() -> TimingEvidence.pairingLimitNs(Double.POSITIVE_INFINITY));
        rejects(() -> TimingEvidence.pairingLimitNs(-1));
        rejects(() -> TimingEvidence.pairingLimitNs(0));
        rejects(() -> TimingEvidence.pairingLimitNs(1e20));
        assert TimingEvidence.pairingLimitNs(5) == 5_000_000;
        assert TimingEvidence.exposureAssessment(0).equals("approximate_requires_optical_timing_test");
        assert TimingEvidence.exposureAssessment(null).equals("unknown_requires_optical_timing_test");
        assert TimingEvidence.exposureAssessment(1).equals("hardware_calibrated_reported_requires_stereo_validation");

        TimingEvidence.MetadataIndex metadata = new TimingEvidence.MetadataIndex();
        metadata.add(0, 10L, 11L);
        metadata.add(1, 20L, null);
        metadata.add(2, 30L, 30L);
        assert metadata.lookup(10).resultIndex == 0;
        assert metadata.lookup(11).basis.equals("physical_sensor_timestamp");
        assert metadata.lookup(20).resultIndex == 1;
        assert metadata.lookup(30).resultIndex == 2 : "Same event with two equal timestamp keys is unique";
        assert metadata.lookup(40).reason.equals("missing_result");
        metadata.add(3, 10L, 15L);
        assert metadata.lookup(10).reason.equals("ambiguous_result");
        assert metadata.lookup(10).resultIndex == -1;
        metadata.add(4, null, null);
        assert metadata.lookup(0).reason.equals("missing_result");
        long[] lastFrames = {29_000_000_000L, 26_800_000_000L};
        assert TimingEvidence.stalledStream(29_500_000_000L, 0, lastFrames) == -1;
        assert TimingEvidence.stalledStream(30_000_000_000L, 0, lastFrames) == 1 : "Completion must reject a stream stale for 3.2 seconds";
        assert TimingEvidence.stalledStream(3_000_000_000L, 0, new long[2]) == 0 : "Never-started stream stalls at the boundary";
        System.out.println("TimingEvidence checks passed");
    }

    private static void rejects(Runnable operation) {
        try { operation.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected rejected input or state");
    }
}
