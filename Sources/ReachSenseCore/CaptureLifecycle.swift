import Foundation

public struct CaptureLifecycle: Sendable {
    public init() {}
    public mutating func begin() -> Int { 0 }
    public mutating func stop() {}
    public func accepts(_ generation: Int) -> Bool { false }
}

public enum PreviewFreshness {
    public static func isCurrent(capturedAt: Double, now: Double, maximumAge: Double) -> Bool { false }
}

public enum LocalObservationStore {
    public static func save(_ payload: Data, frameID: UUID, directory: URL) throws -> URL {
        directory.appendingPathComponent("frame-\(frameID.uuidString).json")
    }
}
