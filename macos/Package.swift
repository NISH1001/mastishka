// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "mastishka",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "MastishkaCore"),
        .executableTarget(
            name: "Mastishka",
            dependencies: ["MastishkaCore"],
            resources: [.process("Resources")]
        ),
        .testTarget(
            name: "MastishkaCoreTests",
            dependencies: ["MastishkaCore"]
        ),
    ]
)
