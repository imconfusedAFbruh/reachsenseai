// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ReachSenseCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "ReachSenseCore", targets: ["ReachSenseCore"])],
    targets: [
        .target(name: "ReachSenseCore"),
        .testTarget(name: "ReachSenseCoreTests", dependencies: ["ReachSenseCore"])
    ]
)
