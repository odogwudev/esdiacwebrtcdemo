import Foundation
import CallKit
#if canImport(ActivityKit)
import ActivityKit
#endif

extension Notification.Name {
    static let esdiacCallStateChanged = Notification.Name("EsdiacCallStateChanged")
    static let esdiacCallKitEndRequested = Notification.Name("EsdiacCallKitEndRequested")
}

#if canImport(ActivityKit)
@available(iOS 16.1, *)
struct EsdiacCallAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var destination: String
        var phase: String
        var isMuted: Bool
        var isSpeakerOn: Bool
    }

    var title: String
}
#endif

final class CallIntegrationManager: NSObject, CXProviderDelegate {
    static let shared = CallIntegrationManager()

    private let provider: CXProvider
    private let callController = CXCallController()
    private var activeCallUUID: UUID?
    private var hasReportedConnected = false

    #if canImport(ActivityKit)
    @available(iOS 16.1, *)
    private var liveActivity: Activity<EsdiacCallAttributes>?
    #endif

    private var isCallKitEnabled: Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        return true
        #endif
    }

    private override init() {
        let config = CXProviderConfiguration(localizedName: "Esdiacwebrtcdemo")
        config.supportsVideo = false
        config.includesCallsInRecents = false
        config.maximumCallsPerCallGroup = 1
        config.maximumCallGroups = 1
        config.supportedHandleTypes = [.phoneNumber, .generic]
        provider = CXProvider(configuration: config)
        super.init()
        if isCallKitEnabled {
            provider.setDelegate(self, queue: nil)
        }
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(onCallStateChanged(_:)),
            name: .esdiacCallStateChanged,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc
    private func onCallStateChanged(_ notification: Notification) {
        guard let userInfo = notification.userInfo else { return }
        let destinationNumber = userInfo["destinationNumber"] as? String ?? ""
        let callPhase = userInfo["callPhase"] as? String ?? "Idle"
        let isMuted = userInfo["isMuted"] as? Bool ?? false
        let isSpeakerOn = userInfo["isSpeakerOn"] as? Bool ?? false
        handleCallState(
            destinationNumber: destinationNumber,
            callPhase: callPhase,
            isMuted: isMuted,
            isSpeakerOn: isSpeakerOn
        )
    }

    private func handleCallState(
        destinationNumber: String,
        callPhase: String,
        isMuted: Bool,
        isSpeakerOn: Bool
    ) {
        switch callPhase {
        case "Connecting", "Calling", "Ringing", "Connected":
            if isCallKitEnabled {
                startCallIfNeeded(destinationNumber: destinationNumber)
                if callPhase == "Connected" {
                    reportConnectedIfNeeded()
                }
            }
            updateLiveActivity(
                destinationNumber: destinationNumber,
                callPhase: callPhase,
                isMuted: isMuted,
                isSpeakerOn: isSpeakerOn
            )
        default:
            if isCallKitEnabled {
                endCall()
            }
            endLiveActivity()
        }
    }

    private func startCallIfNeeded(destinationNumber: String) {
        guard isCallKitEnabled else { return }
        guard activeCallUUID == nil else { return }

        let uuid = UUID()
        activeCallUUID = uuid
        hasReportedConnected = false

        let normalizedHandle = destinationNumber.isEmpty ? "Call" : destinationNumber
        let containsOnlyPhoneChars = normalizedHandle.rangeOfCharacter(
            from: CharacterSet(charactersIn: "+0123456789").inverted
        ) == nil
        let handleType: CXHandle.HandleType = containsOnlyPhoneChars ? .phoneNumber : .generic
        let handle = CXHandle(type: handleType, value: normalizedHandle)

        let startCallAction = CXStartCallAction(call: uuid, handle: handle)
        startCallAction.isVideo = false
        let transaction = CXTransaction(action: startCallAction)

        callController.request(transaction) { [weak self] error in
            guard let self else { return }
            if let error {
                print("[CallKit] Start call transaction failed: \(error.localizedDescription)")
                self.activeCallUUID = nil
                self.hasReportedConnected = false
                return
            }
            let update = CXCallUpdate()
            update.remoteHandle = handle
            update.localizedCallerName = normalizedHandle
            update.hasVideo = false
            self.provider.reportCall(with: uuid, updated: update)
            self.provider.reportOutgoingCall(with: uuid, startedConnectingAt: Date())
        }
    }

    private func reportConnectedIfNeeded() {
        guard isCallKitEnabled else { return }
        guard let uuid = activeCallUUID, !hasReportedConnected else { return }
        hasReportedConnected = true
        provider.reportOutgoingCall(with: uuid, connectedAt: Date())
    }

    private func endCall() {
        guard isCallKitEnabled else { return }
        guard let uuid = activeCallUUID else { return }
        provider.reportCall(with: uuid, endedAt: Date(), reason: .remoteEnded)
        activeCallUUID = nil
        hasReportedConnected = false
    }

    private func updateLiveActivity(
        destinationNumber: String,
        callPhase: String,
        isMuted: Bool,
        isSpeakerOn: Bool
    ) {
        #if canImport(ActivityKit)
        guard #available(iOS 16.1, *) else { return }

        let state = EsdiacCallAttributes.ContentState(
            destination: destinationNumber.isEmpty ? "Unknown" : destinationNumber,
            phase: callPhase,
            isMuted: isMuted,
            isSpeakerOn: isSpeakerOn
        )

        if liveActivity == nil {
            do {
                liveActivity = try Activity.request(
                    attributes: EsdiacCallAttributes(title: "Call in progress"),
                    contentState: state,
                    pushType: nil
                )
            } catch {
                print("[ActivityKit] Failed to start live activity: \(error.localizedDescription)")
            }
            return
        }

        Task {
            await liveActivity?.update(using: state)
        }
        #endif
    }

    private func endLiveActivity() {
        #if canImport(ActivityKit)
        guard #available(iOS 16.1, *) else { return }
        guard let activity = liveActivity else { return }
        Task {
            await activity.end(dismissalPolicy: .immediate)
        }
        liveActivity = nil
        #endif
    }

    func providerDidReset(_ provider: CXProvider) {
        guard isCallKitEnabled else { return }
        activeCallUUID = nil
        hasReportedConnected = false
        endLiveActivity()
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        guard isCallKitEnabled else {
            action.fulfill()
            return
        }
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        guard isCallKitEnabled else {
            action.fulfill()
            return
        }
        activeCallUUID = nil
        hasReportedConnected = false
        endLiveActivity()
        NotificationCenter.default.post(name: .esdiacCallKitEndRequested, object: nil)
        action.fulfill()
    }
}
