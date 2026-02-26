import SwiftUI

struct EsdiacCallLockScreenView: View {
    let state: EsdiacCallAttributes.ContentState
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "phone.fill")
                .foregroundColor(.green)
                .font(.title2)

            VStack(alignment: .leading, spacing: 2) {
                Text(state.destination)
                    .font(.headline)
                    .lineLimit(1)
                Text(phaseText)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }

            Spacer()

            HStack(spacing: 12) {
                if state.isMuted {
                    Image(systemName: "mic.slash.fill")
                        .foregroundColor(.red)
                }
                if state.isSpeakerOn {
                    Image(systemName: "speaker.wave.3.fill")
                        .foregroundColor(.blue)
                }
            }

            Link(destination: URL(string: "esdiac://endcall")!) {
                Image(systemName: "phone.down.fill")
                    .font(.body.weight(.bold))
                    .foregroundColor(.white)
                    .padding(10)
                    .background(Color.red)
                    .clipShape(Circle())
            }
        }
        .padding()
    }

    private var phaseText: String {
        switch state.phase {
        case "Connecting": return "Connecting..."
        case "Calling": return "Calling..."
        case "Ringing": return "Ringing..."
        case "Connected": return "Connected"
        default: return state.phase
        }
    }
}
