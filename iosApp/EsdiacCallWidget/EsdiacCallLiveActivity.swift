import ActivityKit
import WidgetKit
import SwiftUI

// This struct must exactly match the definition in CallIntegrationManager.swift.
// Widget extensions cannot import the main app target, so it is duplicated here.
struct EsdiacCallAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var destination: String
        var phase: String
        var isMuted: Bool
        var isSpeakerOn: Bool
    }

    var title: String
}

struct EsdiacCallLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: EsdiacCallAttributes.self) { context in
            // Lock Screen / notification banner presentation
            EsdiacCallLockScreenView(
                state: context.state,
                title: context.attributes.title
            )
            .widgetURL(URL(string: "esdiac://opencall"))
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded regions when user long-presses the Dynamic Island
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Image(systemName: "phone.fill")
                            .foregroundColor(.green)
                            .font(.title3)
                    }
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        Text(context.state.destination)
                            .font(.headline)
                            .lineLimit(1)
                        Text(phaseText(context.state.phase))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    HStack(spacing: 6) {
                        if context.state.isMuted {
                            Image(systemName: "mic.slash.fill")
                                .foregroundColor(.red)
                                .font(.caption)
                        }
                        if context.state.isSpeakerOn {
                            Image(systemName: "speaker.wave.3.fill")
                                .foregroundColor(.blue)
                                .font(.caption)
                        }
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Link(destination: URL(string: "esdiac://endcall")!) {
                        HStack {
                            Image(systemName: "phone.down.fill")
                            Text("End Call")
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.red)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                // Compact view - left side of Dynamic Island pill
                Image(systemName: "phone.fill")
                    .foregroundColor(.green)
            } compactTrailing: {
                // Compact view - right side of Dynamic Island pill
                Text(compactPhase(context.state.phase))
                    .font(.caption2)
                    .foregroundColor(.green)
                    .lineLimit(1)
            } minimal: {
                // Minimal view - when another Live Activity is also active
                Image(systemName: "phone.fill")
                    .foregroundColor(.green)
            }
            .widgetURL(URL(string: "esdiac://opencall"))
        }
    }

    private func phaseText(_ phase: String) -> String {
        switch phase {
        case "Connecting": return "Connecting..."
        case "Calling": return "Calling..."
        case "Ringing": return "Ringing..."
        case "Connected": return "Connected"
        default: return phase
        }
    }

    private func compactPhase(_ phase: String) -> String {
        switch phase {
        case "Connecting": return "..."
        case "Calling": return "Calling"
        case "Ringing": return "Ring"
        case "Connected": return "Live"
        default: return phase
        }
    }
}
