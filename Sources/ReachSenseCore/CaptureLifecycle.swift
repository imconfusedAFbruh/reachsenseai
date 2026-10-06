import Foundation

public struct CaptureLifecycle: Sendable {
    private var generation = 0
    private var requested = false
    public init() {}
    public mutating func begin() -> Int {
        generation += 1
        requested = true
        return generation
    }
    public mutating func stop() { requested = false; generation += 1 }
    public func accepts(_ generation: Int) -> Bool { requested && generation == self.generation }
}

public enum PreviewFreshness {
    public static func isCurrent(capturedAt: Double, now: Double, maximumAge: Double) -> Bool {
        capturedAt.isFinite && now.isFinite && maximumAge.isFinite && maximumAge >= 0
            && now >= capturedAt && now - capturedAt <= maximumAge
    }
}

public enum LocalObservationStore {
    public static func save(_ payload: Data, frameID: UUID, directory: URL) throws -> URL {
        var folder = directory
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        var flags = URLResourceValues()
        flags.isExcludedFromBackup = true
        try folder.setResourceValues(flags)
        var file = folder.appendingPathComponent("frame-\(frameID.uuidString).json")
        try payload.write(to: file, options: .atomic)
        try file.setResourceValues(flags)
        return file
    }
}
