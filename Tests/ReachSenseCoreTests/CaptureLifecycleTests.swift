import XCTest
@testable import ReachSenseCore

final class CaptureLifecycleTests: XCTestCase {
    func testStopInvalidatesPermissionAndEarlierStartRequests() {
        var state = CaptureLifecycle()
        let first = state.begin()
        XCTAssertTrue(state.accepts(first))
        state.stop()
        XCTAssertFalse(state.accepts(first))
        let next = state.begin()
        XCTAssertFalse(state.accepts(first))
        XCTAssertTrue(state.accepts(next))
        let newest = state.begin()
        XCTAssertFalse(state.accepts(next))
        XCTAssertTrue(state.accepts(newest))
    }

    func testOldFutureAndInvalidPreviewTimesAreNotLive() {
        XCTAssertTrue(PreviewFreshness.isCurrent(capturedAt: 10, now: 10.1, maximumAge: 0.5))
        XCTAssertFalse(PreviewFreshness.isCurrent(capturedAt: 10, now: 11, maximumAge: 0.5))
        XCTAssertFalse(PreviewFreshness.isCurrent(capturedAt: 10, now: 9, maximumAge: 0.5))
        XCTAssertFalse(PreviewFreshness.isCurrent(capturedAt: .nan, now: 11, maximumAge: 0.5))
    }

    func testSavedObservationIsLosslessAndRequestsBackupExclusion() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        let payload = Data([0, 1, 2, 255])
        let url = try LocalObservationStore.save(payload, frameID: UUID(), directory: folder)
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        XCTAssertEqual(try? Data(contentsOf: url), payload)
        XCTAssertEqual(try? folder.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
        XCTAssertEqual(try? url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
    }
}
