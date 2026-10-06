import AVFoundation
import UIKit
import ReachSenseCore

struct CalibrationRecord: Codable {
    let intrinsics: CameraIntrinsics
    let intrinsicMatrixColumnMajor: [Double]
    let extrinsicMatrixColumnMajor: [Double]
    let distortionCenterX: Double
    let distortionCenterY: Double
    let lensDistortionLookupTable: Data?
    let inverseLensDistortionLookupTable: Data?
    let pixelSizeMillimeters: Double

    init(_ calibration: AVCameraCalibrationData) {
        let k = calibration.intrinsicMatrix
        let dimensions = calibration.intrinsicMatrixReferenceDimensions
        intrinsics = CameraIntrinsics(fx: Double(k[0][0]), fy: Double(k[1][1]),
                                      cx: Double(k[2][0]), cy: Double(k[2][1]),
                                      referenceWidth: Double(dimensions.width),
                                      referenceHeight: Double(dimensions.height))
        intrinsicMatrixColumnMajor = (0..<3).flatMap { c in (0..<3).map { r in Double(k[c][r]) } }
        let e = calibration.extrinsicMatrix
        extrinsicMatrixColumnMajor = (0..<4).flatMap { c in (0..<3).map { r in Double(e[c][r]) } }
        distortionCenterX = Double(calibration.lensDistortionCenter.x)
        distortionCenterY = Double(calibration.lensDistortionCenter.y)
        lensDistortionLookupTable = calibration.lensDistortionLookupTable
        inverseLensDistortionLookupTable = calibration.inverseLensDistortionLookupTable
        pixelSizeMillimeters = Double(calibration.pixelSize)
    }

    var rectification: LensRectification? {
        guard let data = inverseLensDistortionLookupTable, data.count >= 8, data.count % 4 == 0 else { return nil }
        let values: [Double] = data.withUnsafeBytes { bytes in
            (0..<(data.count / 4)).map { Double(bytes.loadUnaligned(fromByteOffset: $0 * 4, as: Float.self)) }
        }
        return LensRectification(centerX: distortionCenterX, centerY: distortionCenterY,
                                  referenceWidth: intrinsics.referenceWidth,
                                  referenceHeight: intrinsics.referenceHeight,
                                  observedToRectifiedTable: values)
    }
}

struct SensorFrameMetadata: Encodable {
    let schemaVersion = 1
    let frameID: UUID
    let wallTime: Date
    let videoTimestampSeconds: Double
    let depthTimestampSeconds: Double
    let synchronizationSkewSeconds: Double
    let rgbWidth: Int
    let rgbHeight: Int
    let depthWidth: Int
    let depthHeight: Int
    let depthUnits = "meters"
    let depthEncoding = "float32-little-endian-row-major-no-padding"
    let cameraFrameConvention = "x-right-y-down-z-forward; raw-unmirrored; videoRotationAngle=0"
    let depthFilteringEnabled = false
    let depthAccuracy: String
    let depthQuality: String
    let hardwareIdentifier: String
    let operatingSystem: String
    let captureDeviceID: String
    let captureDeviceName: String
    let videoIntrinsicMatrixData: Data?
    let calibration: CalibrationRecord?
    let validDepthRatio: Double
    let centerDepthMeters: Double?
    let centerCameraPointMeters: Point3D?
    let geometryUsable: Bool
    let geometryFailureReason: String?
    let pointStatus = "distortion-corrected-pinhole-estimate-not-validated-accuracy"
}

struct FeasibilityFrameExport: Encodable {
    let metadata: SensorFrameMetadata
    let referenceDistanceMillimeters: Double?
    let operatorNote: String
    let rgbPNG: Data
    let depthFloat32: Data
}

struct SensorPreview {
    let rgb: UIImage
    let depth: UIImage
    let metadata: SensorFrameMetadata
    let receivedPairs: Int
    let droppedPairs: Int
}

struct CapturedSensorFrame {
    let rgbBuffer: CVPixelBuffer
    let depthValues: [Float]
    let metadata: SensorFrameMetadata
}
