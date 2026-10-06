import AVFoundation
import CoreImage
import UIKit
import Darwin
import ReachSenseCore

final class TrueDepthCapture: NSObject, AVCaptureDataOutputSynchronizerDelegate {
    var onStatus: ((String, Bool) -> Void)?
    var onPreview: ((SensorPreview) -> Void)?
    var onPreviewUnavailable: (() -> Void)?
    var onExport: ((Result<URL, Error>) -> Void)?

    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "org.reachsenseai.capture-session")
    private let frameQueue = DispatchQueue(label: "org.reachsenseai.synchronized-frames")
    private let videoOutput = AVCaptureVideoDataOutput()
    private let depthOutput = AVCaptureDepthDataOutput()
    private let imageContext = CIContext()
    private var synchronizer: AVCaptureDataOutputSynchronizer?
    private var configured = false
    private var lifecycle = CaptureLifecycle() // sessionQueue only
    private var acceptingFrames = false // frameQueue only
    private var observers: [NSObjectProtocol] = []
    private var device: AVCaptureDevice?
    private var latestFrame: CapturedSensorFrame?
    private var received = 0
    private var dropped = 0
    private var lastPreviewTime = -Double.infinity
    // Acquisition diagnostic only; not a calibrated measurement-QC threshold.
    private let maximumPairSkew = 0.005

    override init() {
        super.init()
        for name in [AVCaptureSession.wasInterruptedNotification, AVCaptureSession.runtimeErrorNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: session, queue: nil) { [weak self] _ in
                self?.stop(message: "Capture interrupted. Restart after the interruption ends.")
            })
        }
    }

    deinit { observers.forEach(NotificationCenter.default.removeObserver) }

    func start() {
        sessionQueue.async {
            let generation = self.lifecycle.begin()
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized: self.startAuthorized(generation)
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { allowed in
                    self.sessionQueue.async {
                        guard self.lifecycle.accepts(generation) else { return }
                        if allowed { self.startAuthorized(generation) }
                        else { self.publishStatus("Camera permission is required. Enable it in Settings.", running: false) }
                    }
                }
            default: self.publishStatus("Camera access is denied. Enable it in Settings.", running: false)
            }
        }
    }

    func stop(message: String = "Capture stopped.") {
        sessionQueue.async {
            self.lifecycle.stop()
            if self.session.isRunning { self.session.stopRunning() }
            self.frameQueue.async { self.acceptingFrames = false; self.invalidatePreview() }
            self.publishStatus(message, running: false)
        }
    }

    // All starts, permission completions and stops serialize on sessionQueue.
    private func startAuthorized(_ generation: Int) {
        guard lifecycle.accepts(generation) else { return }
        do {
            guard !session.isInterrupted else { throw CaptureError.message("Camera is interrupted. Retry after it becomes available.") }
            if !configured { try configure() }
            if !session.isRunning { session.startRunning() }
            guard session.isRunning && !session.isInterrupted else { throw CaptureError.message("Capture did not start. Stop and retry.") }
            publishStatus("Front TrueDepth capture active  -  feasibility mode, no flexibility score.", running: true)
            frameQueue.async { self.lastPreviewTime = -Double.infinity; self.acceptingFrames = true }
        } catch {
            lifecycle.stop()
            if session.isRunning { session.stopRunning() }
            frameQueue.async { self.acceptingFrames = false; self.invalidatePreview() }
            publishStatus(error.localizedDescription, running: false)
        }
    }

    private func invalidatePreview() {
        latestFrame = nil
        DispatchQueue.main.async { self.onPreviewUnavailable?() }
    }

    private func configure() throws {
        guard let camera = AVCaptureDevice.default(.builtInTrueDepthCamera, for: .video, position: .front) else {
            throw CaptureError.message("This device has no available front TrueDepth camera. Simulator capture is unsupported.")
        }
        let candidates = camera.formats.filter { format in
            let d = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
            return d.width <= 1920 && !format.supportedDepthDataFormats.filter(Self.isMetricDepth).isEmpty
        }
        guard let videoFormat = candidates.max(by: {
            CMVideoFormatDescriptionGetDimensions($0.formatDescription).width < CMVideoFormatDescriptionGetDimensions($1.formatDescription).width
        }), let depthFormat = videoFormat.supportedDepthDataFormats.filter(Self.isMetricDepth).max(by: {
            CMVideoFormatDescriptionGetDimensions($0.formatDescription).width < CMVideoFormatDescriptionGetDimensions($1.formatDescription).width
        }) else { throw CaptureError.message("No compatible RGB and metric depth formats are available.") }

        let input = try AVCaptureDeviceInput(device: camera)
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        // A failed earlier setup must not leave duplicate inputs/outputs on retry.
        session.inputs.forEach { session.removeInput($0) }
        session.outputs.forEach { session.removeOutput($0) }
        session.sessionPreset = .inputPriority
        guard session.canAddInput(input) else { throw CaptureError.message("Cannot attach the front TrueDepth input.") }
        session.addInput(input)
        videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        videoOutput.alwaysDiscardsLateVideoFrames = true
        depthOutput.isFilteringEnabled = false
        depthOutput.alwaysDiscardsLateDepthData = true
        guard session.canAddOutput(videoOutput), session.canAddOutput(depthOutput) else {
            throw CaptureError.message("Synchronized RGB/depth outputs are unavailable.")
        }
        session.addOutput(videoOutput)
        session.addOutput(depthOutput)
        try camera.lockForConfiguration()
        camera.activeFormat = videoFormat
        camera.activeDepthDataFormat = depthFormat
        if [videoFormat, depthFormat].allSatisfy({ $0.videoSupportedFrameRateRanges.contains { $0.minFrameRate <= 30 && $0.maxFrameRate >= 30 } }) {
            camera.activeVideoMinFrameDuration = CMTime(value: 1, timescale: 30)
            camera.activeVideoMaxFrameDuration = CMTime(value: 1, timescale: 30)
        }
        camera.unlockForConfiguration()
        for output in [videoOutput as AVCaptureOutput, depthOutput as AVCaptureOutput] {
            for connection in output.connections {
                if connection.isVideoRotationAngleSupported(0) { connection.videoRotationAngle = 0 }
                if connection.isVideoMirroringSupported {
                    connection.automaticallyAdjustsVideoMirroring = false
                    connection.isVideoMirrored = false
                }
            }
        }
        if let connection = videoOutput.connection(with: .video), connection.isCameraIntrinsicMatrixDeliverySupported {
            connection.isCameraIntrinsicMatrixDeliveryEnabled = true
        }
        guard let depthConnection = depthOutput.connection(with: .depthData) else {
            throw CaptureError.message("No depth-data connection is available.")
        }
        depthConnection.isEnabled = true
        device = camera
        synchronizer = AVCaptureDataOutputSynchronizer(dataOutputs: [videoOutput, depthOutput])
        synchronizer?.setDelegate(self, queue: frameQueue)
        configured = true
    }

    private static func isMetricDepth(_ format: AVCaptureDevice.Format) -> Bool {
        let type = CMFormatDescriptionGetMediaSubType(format.formatDescription)
        return type == kCVPixelFormatType_DepthFloat16 || type == kCVPixelFormatType_DepthFloat32
    }

    func dataOutputSynchronizer(_ synchronizer: AVCaptureDataOutputSynchronizer,
                                didOutput collection: AVCaptureSynchronizedDataCollection) {
        guard acceptingFrames else { return }
        guard let video = collection.synchronizedData(for: videoOutput) as? AVCaptureSynchronizedSampleBufferData,
              let depth = collection.synchronizedData(for: depthOutput) as? AVCaptureSynchronizedDepthData,
              !video.sampleBufferWasDropped, !depth.depthDataWasDropped,
              let rgbBuffer = CMSampleBufferGetImageBuffer(video.sampleBuffer) else {
            dropped += 1; invalidatePreview(); return
        }
        let videoTime = CMTimeGetSeconds(video.timestamp)
        let depthTime = CMTimeGetSeconds(depth.timestamp)
        let converted = depth.depthData.converting(toDepthDataType: kCVPixelFormatType_DepthFloat32)
        let calibration = converted.cameraCalibrationData.map(CalibrationRecord.init)
        guard videoTime.isFinite, depthTime.isFinite,
              abs(videoTime-depthTime) <= maximumPairSkew else {
            dropped += 1; invalidatePreview(); return
        }
        let map = converted.depthDataMap
        let width = CVPixelBufferGetWidth(map), height = CVPixelBufferGetHeight(map)
        CVPixelBufferLockBaseAddress(map, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(map, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(map) else { dropped += 1; invalidatePreview(); return }
        let stride = CVPixelBufferGetBytesPerRow(map)
        var values: [Float] = []
        values.reserveCapacity(width * height)
        for y in 0..<height {
            let row = base.advanced(by: y*stride).assumingMemoryBound(to: Float.self)
            values.append(contentsOf: UnsafeBufferPointer(start: row, count: width))
        }
        let validCount = values.filter { $0.isFinite && $0 > 0 }.count
        let centerValue = Double(values[(height/2)*width + width/2])
        let centerDepth = centerValue.isFinite && centerValue > 0 ? centerValue : nil
        var centerPoint: Point3D?
        if let c = calibration, let z = centerDepth,
           let pixel = c.rectification?.rectifiedPixel(x: Double(width/2), y: Double(height/2), width: width, height: height) {
            centerPoint = c.intrinsics.unproject(x: pixel.x, y: pixel.y, depthMeters: z, mapWidth: width, mapHeight: height)
        }
        let usable = FramePairGate.accepts(videoTime: videoTime, depthTime: depthTime,
                                           videoDropped: false, depthDropped: false,
                                           hasCalibration: calibration != nil, maximumSkew: maximumPairSkew)
        let metadata = SensorFrameMetadata(frameID: UUID(), wallTime: Date(),
            videoTimestampSeconds: videoTime, depthTimestampSeconds: depthTime,
            synchronizationSkewSeconds: abs(videoTime-depthTime),
            rgbWidth: CVPixelBufferGetWidth(rgbBuffer), rgbHeight: CVPixelBufferGetHeight(rgbBuffer),
            depthWidth: width, depthHeight: height,
            depthAccuracy: converted.depthDataAccuracy == .absolute ? "absolute" : "relative",
            depthQuality: converted.depthDataQuality == .high ? "high" : "low",
            hardwareIdentifier: Self.hardwareIdentifier(),
            operatingSystem: ProcessInfo.processInfo.operatingSystemVersionString,
            captureDeviceID: device?.uniqueID ?? "unknown", captureDeviceName: device?.localizedName ?? "unknown",
            videoIntrinsicMatrixData: CMGetAttachment(video.sampleBuffer, key: kCMSampleBufferAttachmentKey_CameraIntrinsicMatrix,
                                                       attachmentModeOut: nil) as? Data,
            calibration: calibration, validDepthRatio: Double(validCount)/Double(values.count),
            centerDepthMeters: centerDepth, centerCameraPointMeters: centerPoint,
            geometryUsable: usable && calibration?.rectification != nil,
            geometryFailureReason: calibration == nil ? "missing-camera-calibration" :
                (calibration?.rectification == nil ? "missing-distortion-rectification" : nil))
        latestFrame = CapturedSensorFrame(rgbBuffer: rgbBuffer, depthValues: values, metadata: metadata)
        received += 1
        guard videoTime - lastPreviewTime >= 0.1 else { return }
        lastPreviewTime = videoTime
        guard let rgb = rgbImage(rgbBuffer), let depthImage = depthImage(values, width: width, height: height) else { return }
        let preview = SensorPreview(rgb: rgb, depth: depthImage, metadata: metadata, receivedPairs: received, droppedPairs: dropped)
        DispatchQueue.main.async { self.onPreview?(preview) }
    }

    func exportFrame(referenceDistanceMillimeters: Double?, note: String) {
        frameQueue.async {
            do {
                guard self.acceptingFrames, let frame = self.latestFrame,
                      let image = self.rgbImage(frame.rgbBuffer), let png = image.pngData() else {
                    throw CaptureError.message("No current synchronized frame is available. Restart capture and try again.")
                }
                guard PreviewFreshness.isCurrent(capturedAt: frame.metadata.wallTime.timeIntervalSince1970,
                    now: Date().timeIntervalSince1970, maximumAge: 0.5) else {
                    throw CaptureError.message("The last frame is stale. Wait for a new synchronized observation.")
                }
                let depthData = frame.depthValues.withUnsafeBytes { Data($0) }
                let record = FeasibilityFrameExport(metadata: frame.metadata,
                    referenceDistanceMillimeters: referenceDistanceMillimeters, operatorNote: note,
                    rgbPNG: png, depthFloat32: depthData)
                let encoder = JSONEncoder()
                encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
                encoder.dateEncodingStrategy = .iso8601
                let folder = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                    .appendingPathComponent("Feasibility", isDirectory: true)
                let url = try LocalObservationStore.save(encoder.encode(record),
                    frameID: frame.metadata.frameID, directory: folder)
                DispatchQueue.main.async { self.onExport?(.success(url)) }
            } catch { DispatchQueue.main.async { self.onExport?(.failure(error)) } }
        }
    }

    private func rgbImage(_ buffer: CVPixelBuffer) -> UIImage? {
        let image = CIImage(cvPixelBuffer: buffer)
        return imageContext.createCGImage(image, from: image.extent).map { UIImage(cgImage: $0) }
    }

    private func depthImage(_ values: [Float], width: Int, height: Int) -> UIImage? {
        var pixels = [UInt8](repeating: 0, count: values.count*4)
        for (index, d) in values.enumerated() {
            if d.isFinite && d > 0 {
                let t = min(1, max(0, (d-0.2)/0.6))
                pixels[index*4] = UInt8(255*(1-t))
                pixels[index*4+1] = UInt8(255*(1-abs(2*t-1)))
                pixels[index*4+2] = UInt8(255*t)
            }
            pixels[index*4+3] = 255
        }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let image = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                bytesPerRow: width*4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent) else { return nil }
        return UIImage(cgImage: image)
    }

    private func publishStatus(_ text: String, running: Bool) {
        DispatchQueue.main.async { self.onStatus?(text, running) }
    }

    private static func hardwareIdentifier() -> String {
        var info = utsname(); uname(&info)
        let bytes = Mirror(reflecting: info.machine).children.compactMap { ($0.value as? Int8).map { UInt8(bitPattern: $0) } }
        return String(bytes: bytes.prefix(while: { $0 != 0 }), encoding: .utf8) ?? "unknown"
    }
}

enum CaptureError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}
