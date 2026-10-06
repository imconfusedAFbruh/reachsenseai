import XCTest
@testable import ReachSenseCore

final class FramePairTests: XCTestCase {
    // Catches silently pairing dropped, stale, or uncalibrated observations.
    func testOnlyCurrentCalibratedSynchronizedPairsPass() {
        XCTAssertTrue(FramePairGate.accepts(videoTime: 10, depthTime: 10.001,
                                            videoDropped: false, depthDropped: false,
                                            hasCalibration: true, maximumSkew: 0.005))
        XCTAssertFalse(FramePairGate.accepts(videoTime: 10, depthTime: 10.1,
                                             videoDropped: false, depthDropped: false,
                                             hasCalibration: true, maximumSkew: 0.005))
        XCTAssertFalse(FramePairGate.accepts(videoTime: 10, depthTime: 10,
                                             videoDropped: false, depthDropped: true,
                                             hasCalibration: true, maximumSkew: 0.005))
        XCTAssertFalse(FramePairGate.accepts(videoTime: 10, depthTime: 10,
                                             videoDropped: false, depthDropped: false,
                                             hasCalibration: false, maximumSkew: 0.005))
        XCTAssertFalse(FramePairGate.accepts(videoTime: .nan, depthTime: 10,
                                             videoDropped: false, depthDropped: false,
                                             hasCalibration: true, maximumSkew: 0.005))
    }

    // Catches projecting a distorted depth pixel as a rectilinear camera ray.
    func testRectifiesObservedPixelsUsingRadialMagnificationAndReferenceScale() throws {
        let lens = LensRectification(centerX: 400, centerY: 300,
                                     referenceWidth: 800, referenceHeight: 600,
                                     observedToRectifiedTable: [0.1, 0.1])
        let p = try XCTUnwrap(lens.rectifiedPixel(x: 300, y: 150, width: 400, height: 300))
        XCTAssertEqual(p.x, 310, accuracy: 0.000001)
        XCTAssertEqual(p.y, 150, accuracy: 0.000001)
        XCTAssertNil(lens.rectifiedPixel(x: -1, y: 150, width: 400, height: 300))
    }

    func testMissingOrMalformedDistortionDoesNotPretendToBeRectified() {
        let missing = LensRectification(centerX: 400, centerY: 300,
                                        referenceWidth: 800, referenceHeight: 600,
                                        observedToRectifiedTable: [])
        XCTAssertNil(missing.rectifiedPixel(x: 300, y: 150, width: 400, height: 300))
        let bad = LensRectification(centerX: 400, centerY: 300,
                                    referenceWidth: 800, referenceHeight: 600,
                                    observedToRectifiedTable: [.nan, 0])
        XCTAssertNil(bad.rectifiedPixel(x: 300, y: 150, width: 400, height: 300))
    }
}
