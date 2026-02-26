import Foundation
import PushKit

final class VoipPushManager: NSObject, PKPushRegistryDelegate {
    static let shared = VoipPushManager()

    private var registry: PKPushRegistry?

    private override init() {
        super.init()
    }

    func start() {
        guard registry == nil else { return }
        let newRegistry = PKPushRegistry(queue: DispatchQueue.main)
        newRegistry.delegate = self
        newRegistry.desiredPushTypes = [.voIP]
        registry = newRegistry
    }

    func pushRegistry(
        _ registry: PKPushRegistry,
        didUpdate pushCredentials: PKPushCredentials,
        for type: PKPushType
    ) {
        guard type == .voIP else { return }
        let token = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
        NotificationCenter.default.post(
            name: .esdiacVoipPushTokenUpdated,
            object: nil,
            userInfo: ["token": token]
        )
    }

    func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        guard type == .voIP else { return }
        NotificationCenter.default.post(
            name: .esdiacVoipPushTokenUpdated,
            object: nil,
            userInfo: ["token": ""]
        )
    }

    func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        handleIncoming(payload: payload, type: type)
        completion()
    }

    private func handleIncoming(payload: PKPushPayload, type: PKPushType) {
        guard type == .voIP else { return }

        let userInfo = payload.dictionaryPayload
        let callerName = (userInfo["caller_name"] as? String) ?? "Incoming Call"
        let callerHandle = (userInfo["caller_id"] as? String) ?? callerName
        let uuid = (userInfo["call_uuid"] as? String).flatMap(UUID.init(uuidString:)) ?? UUID()

        NotificationCenter.default.post(
            name: .esdiacVoipPushReceived,
            object: nil,
            userInfo: ["payload": userInfo]
        )

        CallIntegrationManager.shared.reportIncomingCall(
            uuid: uuid,
            handle: callerHandle,
            displayName: callerName
        )
    }
}
