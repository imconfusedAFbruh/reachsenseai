import SwiftUI
import UIKit
import ReachSenseCore

@MainActor
final class SensorModel: ObservableObject {
    @Published var status = "Start front TrueDepth capture to inspect synchronized observations."
    @Published var running = false
    @Published var preview: SensorPreview?
    @Published var exportedURL: URL?
    @Published var error: String?
    private let capture = TrueDepthCapture()
    private var captureRequested = false

    init() {
        capture.onStatus = { [weak self] text, running in
            guard !running || self?.captureRequested == true else { return }
            self?.status = text; self?.running = running
            if !running { self?.preview = nil }
        }
        capture.onPreview = { [weak self] preview in
            guard self?.running == true,
                  PreviewFreshness.isCurrent(capturedAt: preview.metadata.wallTime.timeIntervalSince1970,
                    now: Date().timeIntervalSince1970, maximumAge: 0.5) else { return }
            self?.preview = preview
        }
        capture.onPreviewUnavailable = { [weak self] in self?.preview = nil }
        capture.onExport = { [weak self] result in
            switch result {
            case .success(let url): self?.exportedURL = url
            case .failure(let error): self?.error = error.localizedDescription
            }
        }
    }
    func start() { captureRequested = true; capture.start() }
    func stop() { captureRequested = false; running = false; preview = nil; capture.stop() }
    func expirePreview() {
        if let preview, !PreviewFreshness.isCurrent(capturedAt: preview.metadata.wallTime.timeIntervalSince1970,
            now: Date().timeIntervalSince1970, maximumAge: 0.5) { self.preview = nil }
    }
    func save(distance: String, note: String) {
        exportedURL = nil
        let text = distance.trimmingCharacters(in: .whitespacesAndNewlines)
        let value = text.isEmpty ? nil : Double(text)
        if !text.isEmpty && (value == nil || !value!.isFinite || value! <= 0) {
            error = "Enter a positive reference distance in millimeters, or leave it blank."
            return
        }
        capture.exportFrame(referenceDistanceMillimeters: value, note: note)
    }
}

struct SensorView: View {
    @StateObject private var model = SensorModel()
    @State private var referenceDistance = ""
    @State private var operatorNote = ""
    @State private var recordingAuthorized = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Sensor feasibility").font(.title2.bold())
                    Text("Inspect synchronized front RGB and depth. This screen produces no shoulder-flexibility score.")
                        .foregroundStyle(.secondary)
                    HStack {
                        Button(model.running ? "Stop capture" : "Start capture") {
                            if model.running { model.stop() } else { model.start() }
                        }.buttonStyle(.borderedProminent)
                        Text(model.status).font(.callout)
                    }
                    if let preview = model.preview {
                        HStack(alignment: .top) {
                            imagePanel("RGB · raw, unmirrored", image: preview.rgb)
                            imagePanel("Depth · red near / blue far", image: preview.depth)
                        }
                        let m = preview.metadata
                        Text("RGB \(m.rgbWidth)×\(m.rgbHeight) · Depth \(m.depthWidth)×\(m.depthHeight) · valid depth \(m.validDepthRatio*100, specifier: "%.1f")%")
                        Text("Paired frames \(preview.receivedPairs) · dropped \(preview.droppedPairs) · timestamp skew \(m.synchronizationSkewSeconds*1000, specifier: "%.2f") ms")
                        if let point = m.centerCameraPointMeters {
                            Text("Center camera point estimate: X \(point.x*1000, specifier: "%.1f"), Y \(point.y*1000, specifier: "%.1f"), Z \(point.z*1000, specifier: "%.1f") mm")
                        } else { Text("Center 3D point unavailable: invalid depth or missing rectification.").foregroundStyle(.secondary) }
                        Text("\(m.hardwareIdentifier) · \(m.depthAccuracy) depth · \(m.depthQuality) quality · calibration \(m.calibration == nil ? "missing" : "received")")
                            .font(.caption).foregroundStyle(.secondary)
                    } else {
                        ContentUnavailableView("No live observations", systemImage: "camera",
                            description: Text("Use a physical front TrueDepth-equipped iPhone. Camera permission is required."))
                    }
                    Divider()
                    Text("Save a feasibility observation").font(.headline)
                    TextField("Known reference distance in mm (optional)", text: $referenceDistance).textFieldStyle(.roundedBorder)
                    TextField("Operator note / target / viewing angle", text: $operatorNote).textFieldStyle(.roundedBorder)
                    Toggle("Bench target, or participant consent confirmed for RGB/depth recording", isOn: $recordingAuthorized)
                    Text("Save stores one current paired frame with lossless RGB, float32 depth, timestamps and calibration. Files are retained indefinitely on device, with system backup exclusion requested. Share only when explicitly selected.")
                        .font(.caption).foregroundStyle(.secondary)
                    Button("Save current synchronized frame") { model.save(distance: referenceDistance, note: operatorNote) }
                        .buttonStyle(.bordered).disabled(!model.running || model.preview == nil || !recordingAuthorized)
                    if let url = model.exportedURL {
                        ShareLink(item: url) { Label("Share selected observation", systemImage: "square.and.arrow.up") }
                        Text(url.lastPathComponent).font(.caption)
                    }
                }.padding()
            }
            .navigationTitle("ReachSenseAI")
            .alert("Capture issue", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) {
                Button("OK") { model.error = nil }
            } message: { Text(model.error ?? "") }
            .onReceive(Timer.publish(every: 0.2, on: .main, in: .common).autoconnect()) { _ in model.expirePreview() }
            .onChange(of: scenePhase) { _, phase in if phase != .active { model.stop() } }
        }
    }

    private func imagePanel(_ title: String, image: UIImage) -> some View {
        VStack(alignment: .leading) {
            Text(title).font(.caption.bold())
            Image(uiImage: image).resizable().scaledToFit().background(.black).clipShape(RoundedRectangle(cornerRadius: 12))
        }.frame(maxWidth: .infinity)
    }
}
