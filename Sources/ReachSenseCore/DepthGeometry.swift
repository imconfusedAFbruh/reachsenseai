import Foundation

public struct Point3D: Codable, Equatable, Sendable {
    public let x: Double
    public let y: Double
    public let z: Double
    public init(x: Double, y: Double, z: Double) {
        self.x = x; self.y = y; self.z = z
    }
}

public struct CameraIntrinsics: Codable, Equatable, Sendable {
    public let fx: Double
    public let fy: Double
    public let cx: Double
    public let cy: Double
    public let referenceWidth: Double
    public let referenceHeight: Double
    public init(fx: Double, fy: Double, cx: Double, cy: Double,
                referenceWidth: Double, referenceHeight: Double) {
        self.fx = fx; self.fy = fy; self.cx = cx; self.cy = cy
        self.referenceWidth = referenceWidth; self.referenceHeight = referenceHeight
    }
    public func unproject(x: Double, y: Double, depthMeters: Double,
                          mapWidth: Int, mapHeight: Int) -> Point3D? {
        // Test-first contract scaffold. Implement only after observed RED.
        return nil
    }
}
