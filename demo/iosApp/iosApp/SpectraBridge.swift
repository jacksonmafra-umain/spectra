import Foundation
import UIKit
import AVFoundation
import Shared          // the Kotlin Multiplatform framework (SpectraNativeBridge, NativeFrame, NativePhotoResult)
import MWDATCore
import MWDATCamera
#if canImport(MWDATMockDevice)
import MWDATMockDevice
#endif

//
// SpectraBridge — the Swift half of the iOS bridge.
//
// Implements the Kotlin `SpectraNativeBridge` protocol (from the `Shared`
// framework) by talking to Meta's iOS SDK. MWDAT and the Kotlin framework both
// define DeviceSession / Stream / StreamConfiguration / VideoFrame /
// PermissionStatus, so the MWDAT ones are fully qualified.
//
final class SpectraBridge: NSObject, SpectraNativeBridge, AVSpeechSynthesizerDelegate {

    // Computed (not stored) so building this object doesn't touch Wearables.shared
    // before configure() has run.
    private var wearables: WearablesInterface { Wearables.shared }
    private var session: MWDATCore.DeviceSession?
    private var stream: MWDATCamera.Stream?

    private var onSessionState: ((String) -> Void)?
    private var onStreamState: ((String) -> Void)?
    private var onFrame: ((NativeFrame) -> Void)?
    private var pendingPhoto: ((NativePhotoResult) -> Void)?
    private var onDevicesCallback: (([String]) -> Void)?
    private var onMockKitState: ((NativeMockDeviceKitState) -> Void)?

    // Stream listener tokens MUST be retained, or the SDK cancels the listeners the
    // instant they're created — bytes arrive over the wire but no frames/state reach
    // the app. (Same gotcha as the devices listener.)
    private var streamStateToken: AnyListenerToken?
    private var frameToken: AnyListenerToken?
    private var photoToken: AnyListenerToken?
    private var streamErrorToken: AnyListenerToken?

    // Audio (plain Bluetooth, not DAT): A2DP playback via speech, HFP mic via the engine.
    private let speech = AVSpeechSynthesizer()
    private var audioEngine: AVAudioEngine?
    private var onAudioState: ((NativeAudioState) -> Void)?
    private var audioProfile = "NONE"
    private var audioPlaying = false

    // One auto-selector, shared by device observation and session creation, so the
    // "active device" the UI sees is the same one a session is built against.
    private lazy var deviceSelector = AutoDeviceSelector(wearables: wearables)
    private var devicesTask: Task<Void, Never>?
    private var rawDevicesTask: Task<Void, Never>?

    override init() {
        super.init()
        // Must run before ANYTHING touches Wearables.shared. The Kotlin client
        // registers state observers (which hit the SDK) the instant it's built,
        // which is right after this object is created — so configure here.
        do { try Wearables.configure() } catch { /* already configured — fine */ }
        speech.delegate = self
    }

    // MARK: - Setup & registration

    func configure() {
        // Configuration already happened in init(); a second call just throws
        // alreadyConfigured, which we ignore.
        try? Wearables.configure()
    }

    func startRegistration() {
        Task {
            do { try await wearables.startRegistration() }
            catch { print("Spectra: startRegistration error=\(error)") }
        }
    }
    func startUnregistration() { Task { try? await wearables.startUnregistration() } }

    func openGlassesAppUpdate() {
        Task {
            do { try await wearables.openDATGlassesAppUpdate() }
            catch { print("Spectra: openDATGlassesAppUpdate error=\(error)") }
        }
    }

    func observeRegistrationState(onState: @escaping (String) -> Void) {
        Task {
            for await state in wearables.registrationStateStream() {
                let mapped = Self.mapRegistration(state)
                print("Spectra: registrationState raw=\(state) mapped=\(mapped)")
                onState(mapped)
            }
        }
    }

    func observeDevices(onDevices: @escaping ([String]) -> Void) {
        self.onDevicesCallback = onDevices

        // Keep the device-discovery pipeline warm by subscribing to devicesStream(),
        // exactly like the official sample's WearablesViewModel does. Without an active
        // subscriber here, the auto-selector may never report an active device.
        rawDevicesTask?.cancel()
        rawDevicesTask = Task {
            for await devices in wearables.devicesStream() {
                print("Spectra: devicesStream count=\(devices.count) ids=\(devices)")
            }
        }

        // Drive the "active device" UI off the auto-selector's activeDeviceStream —
        // NOT wearables.devices, which can stay empty even with glasses connected.
        // Emit a one-element list when there's an active device, empty otherwise.
        devicesTask?.cancel()
        devicesTask = Task {
            for await device in deviceSelector.activeDeviceStream() {
                let ids = device.map { ["\($0)"] } ?? []   // DeviceIdentifier is a String typealias
                print("Spectra: activeDevice=\(String(describing: device))")
                onDevices(ids)
            }
        }
    }

    // MARK: - Permissions

    func checkCameraPermission(onResult: @escaping (String) -> Void) {
        Task {
            if let status = try? await wearables.checkPermissionStatus(.camera) {
                onResult(Self.mapPermission(status))
            } else {
                onResult("UNAVAILABLE")
            }
        }
    }

    func requestCameraPermission(onResult: @escaping (String) -> Void) {
        Task {
            if let status = try? await wearables.requestPermission(.camera) {
                onResult(Self.mapPermission(status))
                // Device presence is reported by activeDeviceStream (see
                // observeDevices), not by re-reading wearables.devices here.
            } else {
                onResult("DENIED")
            }
        }
    }

    // MARK: - Session

    func createSession(onResult: @escaping (String?) -> Void) {
        do {
            let s = try wearables.createSession(deviceSelector: deviceSelector)
            self.session = s
            Task { for await st in s.stateStream() { self.onSessionState?(Self.mapSessionState(st)) } }
            onResult(nil)
        } catch {
            onResult("\(error)")
        }
    }

    func startSession() {
        do { try session?.start() }
        catch { print("Spectra: session.start() error=\(error)") }
    }
    func stopSession() { session?.stop() }
    func observeSessionState(onState: @escaping (String) -> Void) {
        self.onSessionState = onState
        // Replay the current state: the session may have already reached .started
        // before the Kotlin observer was attached (stateStream doesn't buffer).
        if let s = session { onState(Self.mapSessionState(s.state)) }
    }

    /// Wait (up to ~10s) for the session to reach `.started`. The official sample's
    /// DeviceSessionManager does the same before adding a stream — `addStream` on a
    /// not-yet-started session is what makes "the device won't open".
    private func awaitSessionStarted(_ session: MWDATCore.DeviceSession) async -> Bool {
        if session.state == .started { return true }
        do { try session.start() } catch { print("Spectra: session.start() error=\(error)") }
        for _ in 0..<100 {
            switch session.state {
            case .started: return true
            case .stopped: return false
            default: break
            }
            try? await Task.sleep(nanoseconds: 100_000_000) // 0.1s
        }
        return session.state == .started
    }

    // MARK: - Stream

    func startStream(quality: String, frameRate: Int32, onError: @escaping (String) -> Void) {
        guard let session = session else { onError("No active session"); return }
        Task {
            // The session must be running before a stream can attach.
            guard await awaitSessionStarted(session) else {
                onError("Session never reached .started — the glasses may be folded/asleep or the active device dropped.")
                print("Spectra: startStream aborted, session.state=\(session.state)")
                return
            }
            do {
                let config = MWDATCamera.StreamConfiguration(
                    videoCodec: .raw,
                    resolution: Self.mapResolution(quality),
                    frameRate: UInt(frameRate)
                )
                guard let s = try session.addStream(config: config) else {
                    onError("addStream returned nil")
                    return
                }
                self.stream = s
                print("Spectra: stream added, starting…")

                // Retain every token (see property comment) — discarding them kills the listeners.
                self.streamStateToken = s.statePublisher.listen { state in
                    self.onStreamState?(Self.mapStreamState(state))
                }
                self.frameToken = s.videoFramePublisher.listen { (frame: MWDATCamera.VideoFrame) in
                    if let (data, w, h) = Self.rgbaBytes(from: frame) {
                        let ts = Int64(Date().timeIntervalSince1970 * 1000)
                        self.onFrame?(NativeFrame(data: data, width: Int32(w), height: Int32(h), timestampMillis: ts))
                    }
                }
                self.streamErrorToken = s.errorPublisher.listen { streamError in
                    print("Spectra: stream error=\(streamError)")
                    onError("\(streamError)")
                }
                self.photoToken = s.photoDataPublisher.listen { photoData in
                    let img = UIImage(data: photoData.data)
                    self.pendingPhoto?(NativePhotoResult(
                        data: photoData.data,
                        width: Int32(img?.size.width ?? 0),
                        height: Int32(img?.size.height ?? 0),
                        error: nil
                    ))
                    self.pendingPhoto = nil
                }
                await s.start()
            } catch {
                onError("\(error)")
            }
        }
    }

    func stopStream() {
        Task { await stream?.stop() }
        // Release the listeners so a fresh startStream re-attaches cleanly.
        streamStateToken = nil
        frameToken = nil
        photoToken = nil
        streamErrorToken = nil
    }
    func observeStreamState(onState: @escaping (String) -> Void) { self.onStreamState = onState }
    func observeFrames(onFrame: @escaping (NativeFrame) -> Void) { self.onFrame = onFrame }

    func capturePhoto(onResult: @escaping (NativePhotoResult) -> Void) {
        guard let stream = stream else {
            onResult(NativePhotoResult(data: nil, width: 0, height: 0, error: "No active stream"))
            return
        }
        self.pendingPhoto = onResult
        _ = stream.capturePhoto(format: .jpeg)
    }

    // MARK: - MockDeviceKit (developer-only device simulation)
    //
    // Wraps Meta's `MockDeviceKit.shared` (MWDATMockDevice). Enabling it and
    // pairing a Ray-Ban Meta makes a simulated device active — so registration,
    // sessions and streaming all run with no real glasses present. This is the
    // same facility the official CameraAccess sample exposes behind its debug
    // (ladybug) button. Guarded by `canImport` so builds without the
    // MWDATMockDevice product still compile (the calls just become no-ops).

    func observeMockDeviceKit(onState: @escaping (NativeMockDeviceKitState) -> Void) {
        self.onMockKitState = onState
        emitMockKitState()
    }

    func enableMockDeviceKit() {
        #if canImport(MWDATMockDevice)
        // Same call the sample's "Enable MockDeviceKit" debug button makes.
        MockDeviceKit.shared.enable()
        #endif
        emitMockKitState()
    }

    func disableMockDeviceKit() {
        #if canImport(MWDATMockDevice)
        MockDeviceKit.shared.disable()
        #endif
        emitMockKitState()
    }

    func pairMockGlasses() {
        #if canImport(MWDATMockDevice)
        let device = MockDeviceKit.shared.pairRaybanMeta()
        // Bring it fully "on the face" so the auto-selector treats it as active.
        device.powerOn()
        (device as? MockDisplaylessGlasses)?.unfold()
        device.don()
        #endif
        emitMockKitState()
    }

    private func emitMockKitState() {
        #if canImport(MWDATMockDevice)
        let enabled = MockDeviceKit.shared.isEnabled
        let count = MockDeviceKit.shared.pairedDevices.count
        onMockKitState?(NativeMockDeviceKitState(enabled: enabled, pairedCount: Int32(count)))
        #else
        onMockKitState?(NativeMockDeviceKitState(enabled: false, pairedCount: 0))
        #endif
    }

    // MARK: - Audio (platform Bluetooth: A2DP playback / HFP mic)
    //
    // None of this touches the DAT SDK — the Ray-Ban speakers/mic are plain
    // Bluetooth (A2DP out, HFP in), shared with the system audio stack. Straight
    // out of Meta's "Use device microphones and speakers" guide.

    func observeAudio(onState: @escaping (NativeAudioState) -> Void) {
        self.onAudioState = onState
        emitAudioState()
    }

    /// A2DP: speak text out to the glasses (high quality, no audio asset needed).
    func playAudioToGlasses(text: String) {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch { print("Spectra: audio playback session error=\(error)") }
        audioProfile = "A2DP_PLAYBACK"
        audioPlaying = true
        emitAudioState()
        speech.speak(AVSpeechUtterance(string: text))
    }

    func stopAudioPlayback() {
        speech.stopSpeaking(at: .immediate)
        audioPlaying = false
        audioProfile = "NONE"
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        emitAudioState()
    }

    /// HFP: capture the wearer's voice (8 kHz mono) and report a live input level.
    func startMicCapture() {
        Task {
            let granted = await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
                AVAudioSession.sharedInstance().requestRecordPermission { ok in c.resume(returning: ok) }
            }
            guard granted else { print("Spectra: mic permission denied"); return }
            do {
                let session = AVAudioSession.sharedInstance()
                // .allowBluetoothHFP on current SDKs; older SDKs expose .allowBluetooth — swap if needed.
                try session.setCategory(.playAndRecord, mode: .default, options: [.allowBluetoothHFP])
                try session.setActive(true, options: .notifyOthersOnDeactivation)
                if let hfp = session.availableInputs?.first(where: { $0.portType == .bluetoothHFP }) {
                    try session.setPreferredInput(hfp)
                }
                let engine = AVAudioEngine()
                self.audioEngine = engine
                let input = engine.inputNode
                let format = input.inputFormat(forBus: 0)
                input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                    self.audioProfile = "HFP_MIC"
                    self.emitAudioState(micLevel: Self.rmsLevel(buffer))
                }
                engine.prepare()
                try engine.start()
                audioProfile = "HFP_MIC"
                emitAudioState()
                print("Spectra: mic capture started")
            } catch {
                print("Spectra: mic start error=\(error)")
            }
        }
    }

    func stopMicCapture() {
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine?.stop()
        audioEngine = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        audioProfile = "NONE"
        emitAudioState()
    }

    // AVSpeechSynthesizerDelegate — flip "playing" off when speech ends/cancels.
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        audioPlaying = false
        if audioProfile == "A2DP_PLAYBACK" { audioProfile = "NONE" }
        emitAudioState()
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        audioPlaying = false
        if audioProfile == "A2DP_PLAYBACK" { audioProfile = "NONE" }
        emitAudioState()
    }

    private func emitAudioState(micLevel: Float = 0) {
        let route = AVAudioSession.sharedInstance().currentRoute
        let onGlasses = route.outputs.contains { $0.portType == .bluetoothA2DP }
            || route.inputs.contains { $0.portType == .bluetoothHFP }
        onAudioState?(NativeAudioState(
            profile: audioProfile,
            micLevel: micLevel,
            isPlaying: audioPlaying,
            routedToGlasses: onGlasses
        ))
    }

    /// Normalized RMS (0..1) of a PCM buffer, for a simple VU meter.
    private static func rmsLevel(_ buffer: AVAudioPCMBuffer) -> Float {
        guard let channel = buffer.floatChannelData?[0] else { return 0 }
        let n = Int(buffer.frameLength)
        if n == 0 { return 0 }
        var sum: Float = 0
        for i in 0..<n { let s = channel[i]; sum += s * s }
        let rms = (sum / Float(n)).squareRoot()
        return min(1.0, rms * 20)   // scale; mic RMS is small
    }

    // MARK: - Mapping helpers (string-based to survive enum case-name differences)

    private static func mapRegistration(_ state: MWDATCore.RegistrationState) -> String {
        switch state {
        case .registered: return "REGISTERED"
        case .registering: return "REGISTERING"
        case .available, .unavailable: return "NOT_REGISTERED"
        @unknown default: return "NOT_REGISTERED"
        }
    }

    private static func mapPermission(_ status: MWDATCore.PermissionStatus) -> String {
        switch status {
        case .granted: return "GRANTED"
        case .denied: return "DENIED"
        @unknown default: return "DENIED"
        }
    }

    private static func mapSessionState(_ state: MWDATCore.DeviceSessionState) -> String {
        switch state {
        case .started: return "RUNNING"
        case .starting: return "STARTING"
        case .paused: return "PAUSED"
        case .idle, .stopping, .stopped: return "STOPPED"
        @unknown default: return "STOPPED"
        }
    }

    private static func mapStreamState(_ state: MWDATCamera.StreamState) -> String {
        switch state {
        case .streaming: return "STREAMING"
        case .starting, .waitingForDevice: return "STARTING"
        case .paused: return "STARTED"
        case .stopping: return "STOPPING"
        case .stopped: return "STOPPED"
        @unknown default: return "STOPPED"
        }
    }

    private static func mapResolution(_ quality: String) -> MWDATCamera.StreamingResolution {
        switch quality.uppercased() {
        case "HIGH": return .high
        case "MEDIUM": return .medium
        default: return .low
        }
    }

    /// Convert a DAT video frame to raw RGBA bytes for the shared CameraView.
    private static func rgbaBytes(from frame: MWDATCamera.VideoFrame) -> (Data, Int, Int)? {
        guard let image = frame.makeUIImage(), let cg = image.cgImage else { return nil }
        let w = cg.width, h = cg.height
        var buffer = [UInt8](repeating: 0, count: w * h * 4)
        let cs = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: &buffer, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: cs, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (Data(buffer), w, h)
    }
}
