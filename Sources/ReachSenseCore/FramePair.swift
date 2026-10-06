import Foundation

public enum FramePairGate {
    public static func accepts(videoTime: Double, depthTime: Double,
                               videoDropped: Bool, depthDropped: Bool,
                               hasCalibration: Bool, maximumSkew: Double) -> Bool {
        return false // RED scaffold; not a research-validity classifier.
    }
}

public struct Pixel2D: Equatable, Sendable {
    public let x: Double
    public let y: Double
}

public struct LensRectification: Sendable {
    public let centerX: Double
    public let centerY: Double
    public let referenceWidth: Double
    public let referenceHeight: Double
    public let observedToRectifiedTable: [Double]
    public init(centerX: Double, centerY: Double, referenceWidth: Double,
                referenceHeight: Double, observedToRectifiedTable: [Double]) {
        self.centerX = centerX; self.centerY = centerY
        self.referenceWidth = referenceWidth; self.referenceHeight = referenceHeight
        self.observedToRectifiedTable = observedToRectifiedTable
    }
    public func rectifiedPixel(x: Double, y: Double, width: Int, height: Int) -> Pixel2D? {
        return nil // RED scaffold.
    }
}
