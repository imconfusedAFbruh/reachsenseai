import XCTest
@testable import ReachSenseCore

final class DepthGeometryTests: XCTestCase {
    // Catches using unscaled calibration intrinsics for a lower-resolution map.
    func testUnprojectsUsingCalibrationReferenceDimensions() throws {
        let intrinsics = CameraIntrinsics(fx: 1000, fy: 800, cx: 640, cy: 480,
                                          referenceWidth: 1280, referenceHeight: 960)
        let point = try XCTUnwrap(intrinsics.unproject(x: 420, y: 340,
                                                      depthMeters: 0.5,
                                                      mapWidth: 640, mapHeight: 480))
        XCTAssertEqual(point.x, 0.1, accuracy: 0.000001)
        XCTAssertEqual(point.y, 0.125, accuracy: 0.000001)
        XCTAssertEqual(point.z, 0.5, accuracy: 0.000001)
    }

    // Catches emitting geometry for invalid sensor data or out-of-map locations.
    func testInvalidDepthAndCoordinatesDoNotBecomePoints() {
        let intrinsics = CameraIntrinsics(fx: 500, fy: 500, cx: 320, cy: 240,
                                          referenceWidth: 640, referenceHeight: 480)
        for depth in [0, -0.1, Double.nan, Double.infinity] {
            XCTAssertNil(intrinsics.unproject(x: 320, y: 240, depthMeters: depth,
                                              mapWidth: 640, mapHeight: 480))
        }
        XCTAssertNil(intrinsics.unproject(x: 640, y: 240, depthMeters: 0.5,
                                          mapWidth: 640, mapHeight: 480))
        XCTAssertNil(intrinsics.unproject(x: 320, y: 240, depthMeters: 0.5,
                                          mapWidth: 0, mapHeight: 480))
    }
}
