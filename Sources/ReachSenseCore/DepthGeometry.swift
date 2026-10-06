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
        guard depthMeters.isFinite, depthMeters > 0,
              x.isFinite, y.isFinite, fx.isFinite, fy.isFinite,
              cx.isFinite, cy.isFinite, fx > 0, fy > 0,
              referenceWidth.isFinite, referenceHeight.isFinite,
              referenceWidth > 0, referenceHeight > 0,
              mapWidth > 0, mapHeight > 0,
              x >= 0, y >= 0, x < Double(mapWidth), y < Double(mapHeight) else { return nil }
        let scaleX = Double(mapWidth) / referenceWidth
        let scaleY = Double(mapHeight) / referenceHeight
        let point = Point3D(x: (x - cx * scaleX) * depthMeters / (fx * scaleX),
                            y: (y - cy * scaleY) * depthMeters / (fy * scaleY),
                            z: depthMeters)
        guard point.x.isFinite, point.y.isFinite else { return nil }
        return point
    }
}
