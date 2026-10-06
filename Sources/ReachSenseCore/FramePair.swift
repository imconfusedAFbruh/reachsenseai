import Foundation

public enum FramePairGate {
    public static func accepts(videoTime: Double, depthTime: Double,
                               videoDropped: Bool, depthDropped: Bool,
                               hasCalibration: Bool, maximumSkew: Double) -> Bool {
        !videoDropped && !depthDropped && hasCalibration &&
        videoTime.isFinite && depthTime.isFinite && videoTime >= 0 && depthTime >= 0 &&
        maximumSkew.isFinite && maximumSkew >= 0 && abs(videoTime-depthTime) <= maximumSkew
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
        guard width > 0, height > 0, x.isFinite, y.isFinite,
              x >= 0, y >= 0, x < Double(width), y < Double(height),
              referenceWidth.isFinite, referenceHeight.isFinite,
              referenceWidth > 0, referenceHeight > 0,
              centerX.isFinite, centerY.isFinite,
              centerX >= 0, centerX <= referenceWidth, centerY >= 0, centerY <= referenceHeight,
              observedToRectifiedTable.count >= 2,
              observedToRectifiedTable.allSatisfy({ $0.isFinite && $0 > -1 }) else { return nil }
        let scaleX = referenceWidth / Double(width)
        let scaleY = referenceHeight / Double(height)
        let dx = x * scaleX - centerX, dy = y * scaleY - centerY
        let radius = hypot(dx, dy)
        let maximumRadius = hypot(max(centerX, referenceWidth-centerX),
                                  max(centerY, referenceHeight-centerY))
        guard maximumRadius > 0 else { return nil }
        let index = min(Double(observedToRectifiedTable.count-1),
                        radius / maximumRadius * Double(observedToRectifiedTable.count-1))
        let low = Int(index), high = min(low+1, observedToRectifiedTable.count-1)
        let fraction = index-Double(low)
        let magnification = observedToRectifiedTable[low]*(1-fraction) + observedToRectifiedTable[high]*fraction
        return Pixel2D(x: (centerX+dx*(1+magnification))/scaleX,
                       y: (centerY+dy*(1+magnification))/scaleY)
    }
}
