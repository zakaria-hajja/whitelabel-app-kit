import SwiftUI

/** Where an `ActionScaffold`'s content sits when it is shorter than the space above the dock (Kotlin: ActionScaffoldAlignment). */
public enum ActionScaffoldAlignment: Equatable {
    /** At the top, as a form. */
    case top
    /** Centred above the dock, as a hero moment. Taller content scrolls from the top. */
    case center
}

/**
 * A full screen whose actions stay pinned at the bottom in an action dock, outside the scroll, on
 * the page background (Kotlin: AppActionScaffold). Use it for full screens with a primary action; not
 * for tab roots, sheets, dialogs, centred empty or error states, or keypads.
 *
 * The dock stacks, top to bottom: `message` (an error line), `primary` (full width), at most one
 * `secondary` and `finePrint`. The primary never leaves the dock. When the dock would take more than
 * `Sizing.dockMaxFraction` of the height, or less than 360 pt is left above the keyboard, the fine
 * print and then the secondary move to the end of the content. While the content can scroll further
 * down, a `Sizing.dockFadeHeight` fade of the background sits above the dock. The dock sits on the
 * keyboard and its background runs under the home indicator. It comes last for VoiceOver.
 *
 * `banner` (an undo banner, a one-off message) shows 8 above the dock, over the bottom of the
 * content, so it never covers the primary (Kotlin: snackbarHost). Pass the message line's text as
 * `messageAnnouncement` and VoiceOver announces it each time it changes to a new line (Kotlin: the
 * message is a polite live region).
 *
 * `scrolls: false` lays the content out without a `ScrollView` (clipped to the space above the
 * dock), for `ImageRenderer` snapshot tests.
 */
public struct ActionScaffold<Content: View, Primary: View, Message: View, Secondary: View, FinePrint: View, Banner: View>: View {
    @Environment(\.appStyle) private var style
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let alignment: ActionScaffoldAlignment
    private let scrolls: Bool
    private let content: Content
    private let primary: Primary
    private let message: Message
    private let secondary: Secondary
    private let finePrint: FinePrint
    private let banner: Banner
    private let messageAnnouncement: String?

    @State private var heights = DockPartHeights()
    @State private var frame = ScaffoldFrame()
    @State private var viewport: CGFloat = 0
    @State private var contentBottom: CGFloat = 0

    private static var dockTopPadding: CGFloat { 12 }
    private static var dockBottomPadding: CGFloat { 16 }
    /** A bottom inset taller than this is the keyboard, not the home indicator. */
    private static var keyboardInset: CGFloat { 100 }
    /** Under this much height above the keyboard, the dock keeps only its message and primary. */
    private static var compactHeight: CGFloat { 360 }
    private static var scrollSpace: String { "ActionScaffold.scroll" }

    public init(
        alignment: ActionScaffoldAlignment = .top,
        scrolls: Bool = true,
        messageAnnouncement: String? = nil,
        @ViewBuilder content: () -> Content,
        @ViewBuilder primary: () -> Primary,
        @ViewBuilder message: () -> Message = { EmptyView() },
        @ViewBuilder secondary: () -> Secondary = { EmptyView() },
        @ViewBuilder finePrint: () -> FinePrint = { EmptyView() },
        @ViewBuilder banner: () -> Banner = { EmptyView() }
    ) {
        self.alignment = alignment
        self.scrolls = scrolls
        self.content = content()
        self.primary = primary()
        self.message = message()
        self.secondary = secondary()
        self.finePrint = finePrint()
        self.banner = banner()
        self.messageAnnouncement = messageAnnouncement
    }

    public var body: some View {
        let overflow = currentOverflow
        VStack(spacing: 0) {
            contentArea(overflow)
                .overlay(alignment: .bottom) { banner.padding(.bottom, Spacing.sm) }
            dock(overflow)
        }
        .background(style.colors.background.ignoresSafeArea())
        .onGeometryChange(for: ScaffoldFrame.self) { proxy in
            ScaffoldFrame(height: proxy.size.height, bottomInset: proxy.safeAreaInsets.bottom)
        } action: { frame = $0 }
        .onChange(of: messageAnnouncement) { _, text in
            if let text, !text.isEmpty { ActionScaffoldAnnouncement.post(text) }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("ActionScaffold")
    }

    private static func isPresent<V>(_ type: V.Type) -> Bool { type != EmptyView.self }

    private var currentOverflow: DockOverflow {
        guard frame.height > 0 else { return .none }
        // With the keyboard up, the frame already ends at the keyboard's top and the keyboard isn't
        // dock chrome; otherwise the home indicator counts as in Kotlin (available and chrome).
        let keyboardUp = frame.bottomInset > Self.keyboardInset
        let inset = keyboardUp ? 0 : frame.bottomInset
        return ActionDockLayout.overflow(
            available: frame.height + inset,
            chrome: Self.dockTopPadding + Self.dockBottomPadding + inset,
            gap: Spacing.sm,
            message: Self.isPresent(Message.self) ? heights.message : 0,
            primary: heights.primary,
            secondary: Self.isPresent(Secondary.self) ? heights.secondary : 0,
            finePrint: Self.isPresent(FinePrint.self) ? heights.finePrint : 0,
            maxFraction: style.pack.sizing.dockMaxFraction,
            compact: keyboardUp && frame.height < Self.compactHeight
        )
    }

    private var gutter: CGFloat { style.pack.sizing.screenGutter }

    // MARK: Content

    @ViewBuilder
    private func contentArea(_ overflow: DockOverflow) -> some View {
        let column = VStack(alignment: .leading, spacing: 0) {
            content
            movedExtras(overflow)
        }
        .padding(.horizontal, gutter)
        .frame(maxWidth: .infinity, alignment: .leading)

        if scrolls {
            ScrollView {
                ViewportLayout(viewport: viewport, centred: alignment == .center, bottomPadding: Spacing.lg) { column }
                    .onGeometryChange(for: CGFloat.self) { $0.frame(in: .named(Self.scrollSpace)).maxY } action: {
                        contentBottom = $0
                    }
            }
            .coordinateSpace(.named(Self.scrollSpace))
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { viewport = $0 }
            .overlay(alignment: .bottom) { fade }
            .accessibilityIdentifier("ActionScaffold.content")
        } else {
            column
                .padding(.bottom, Spacing.lg)
                // minHeight 0: take the space above the dock even when the content is taller (it clips).
                .frame(maxWidth: .infinity, minHeight: 0, maxHeight: .infinity, alignment: alignment == .center ? .center : .top)
                .clipped()
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("ActionScaffold.content")
        }
    }

    @ViewBuilder
    private func movedExtras(_ overflow: DockOverflow) -> some View {
        let secondaryMoved = Self.isPresent(Secondary.self) && overflow == .finePrintAndSecondary
        let finePrintMoved = Self.isPresent(FinePrint.self) && overflow != .none
        if secondaryMoved || finePrintMoved {
            DockStack(gap: Spacing.sm) {
                if secondaryMoved { secondary.measuredHeight { heights.secondary = $0 } }
                if finePrintMoved { finePrint.measuredHeight { heights.finePrint = $0 } }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.lg)
        }
    }

    private var fade: some View {
        let background = style.colors.background
        let shown = ActionDockLayout.showsFade(contentBottom: contentBottom, viewportBottom: viewport)
        return LinearGradient(colors: [background.opacity(0), background], startPoint: .top, endPoint: .bottom)
            .frame(height: style.pack.sizing.dockFadeHeight)
            .opacity(shown ? 1 : 0)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.15), value: shown)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .accessibilityIdentifier("ActionDock.fade")
    }

    // MARK: Dock

    private func dock(_ overflow: DockOverflow) -> some View {
        DockStack(gap: Spacing.sm) {
            if Self.isPresent(Message.self) {
                message.measuredHeight { heights.message = $0 }
            }
            primary
                .frame(maxWidth: .infinity)
                .measuredHeight { heights.primary = $0 }
            if Self.isPresent(Secondary.self) && overflow != .finePrintAndSecondary {
                secondary.measuredHeight { heights.secondary = $0 }
            }
            if Self.isPresent(FinePrint.self) && overflow == .none {
                finePrint.measuredHeight { heights.finePrint = $0 }
            }
        }
        .padding(.horizontal, gutter)
        .padding(.top, Self.dockTopPadding)
        .padding(.bottom, Self.dockBottomPadding)
        .frame(maxWidth: .infinity)
        .fixedSize(horizontal: false, vertical: true)
        .background(style.colors.background.ignoresSafeArea(edges: .bottom))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("ActionDock")
    }
}

/** Posts the dock's message to VoiceOver; tests swap it to see what is announced. */
enum ActionScaffoldAnnouncement {
    static var post: (String) -> Void = { UIAccessibility.post(notification: .announcement, argument: $0) }
}

/** The measured heights of the dock's parts, wherever each is shown (same width in both places). */
private struct DockPartHeights: Equatable {
    var message: CGFloat = 0
    var primary: CGFloat = 0
    var secondary: CGFloat = 0
    var finePrint: CGFloat = 0
}

/** The scaffold's height (above the keyboard or the home indicator) and its bottom inset. */
private struct ScaffoldFrame: Equatable {
    var height: CGFloat = 0
    var bottomInset: CGFloat = 0
}

private extension View {
    func measuredHeight(_ update: @escaping (CGFloat) -> Void) -> some View {
        onGeometryChange(for: CGFloat.self, of: { $0.size.height }, action: update)
    }
}

/**
 * Stacks the dock's parts, centred, `gap` apart. A part of height 0 (a slot whose content shows
 * nothing, such as `if let error { … }` with no error) adds no gap, as the overflow rule counts it
 * (Kotlin: DockStack).
 */
private struct DockStack: Layout {
    let gap: CGFloat

    private func sizes(_ proposal: ProposedViewSize, _ subviews: Subviews) -> [CGSize] {
        subviews.map { $0.sizeThatFits(ProposedViewSize(width: proposal.width, height: nil)) }
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes = sizes(proposal, subviews)
        let shown = sizes.filter { $0.height > 0 }.count
        let height = sizes.reduce(0) { $0 + $1.height } + gap * CGFloat(max(shown - 1, 0))
        return CGSize(width: proposal.width ?? sizes.map(\.width).max() ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let sizes = sizes(proposal, subviews)
        var y = bounds.minY
        var placedAny = false
        for (subview, size) in zip(subviews, sizes) {
            if size.height > 0 {
                if placedAny { y += gap }
                placedAny = true
            }
            subview.place(
                at: CGPoint(x: bounds.midX, y: y),
                anchor: .top,
                proposal: ProposedViewSize(width: bounds.width, height: size.height)
            )
            y += size.height
        }
    }
}

/**
 * Lays the content out with `bottomPadding` below it. When `centred` and the content (with that
 * padding) fits in `viewport`, it fills the viewport and the content sits in its middle (Kotlin: ViewportLayout).
 */
private struct ViewportLayout: Layout {
    let viewport: CGFloat
    let centred: Bool
    let bottomPadding: CGFloat

    private func content(_ proposal: ProposedViewSize, _ subviews: Subviews) -> CGSize {
        subviews.first?.sizeThatFits(ProposedViewSize(width: proposal.width, height: nil)) ?? .zero
    }

    private func fits(_ height: CGFloat) -> Bool { centred && height + bottomPadding <= viewport }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let size = content(proposal, subviews)
        return CGSize(width: proposal.width ?? size.width, height: fits(size.height) ? viewport : size.height + bottomPadding)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let size = content(proposal, subviews)
        let y = fits(size.height) ? (viewport - size.height) / 2 : 0
        subviews.first?.place(
            at: CGPoint(x: bounds.minX, y: bounds.minY + y),
            proposal: ProposedViewSize(width: bounds.width, height: size.height)
        )
    }
}

// MARK: Previews

private struct ActionScaffoldPreview: View {
    var rows = 3
    var alignment: ActionScaffoldAlignment = .top

    var body: some View {
        ActionScaffold(alignment: alignment) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("Title").font(.title)
                ForEach(0..<rows, id: \.self) { Text("Row \($0 + 1)") }
            }
        } primary: {
            Button("Continue") {}.buttonStyle(PrimaryButtonStyle())
        } secondary: {
            Button("I don't remember") {}.buttonStyle(TextButtonStyle())
        } finePrint: {
            Text("Everything stays on this phone.").font(.footnote).multilineTextAlignment(.center)
        }
    }
}

#Preview("Short") { ActionScaffoldPreview() }
#Preview("Long with fade") { ActionScaffoldPreview(rows: 40) }
#Preview("AX3") { ActionScaffoldPreview(rows: 40).environment(\.dynamicTypeSize, .accessibility3) }
#Preview("Dark") { ActionScaffoldPreview(rows: 40).preferredColorScheme(.dark) }
#Preview("Hero centred") {
    ActionScaffold(alignment: .center) {
        Circle().fill(.tint).frame(width: 120, height: 120).frame(maxWidth: .infinity)
    } primary: {
        Button("Plant it in my garden") {}.buttonStyle(PrimaryButtonStyle())
    }
}
#Preview("Keyboard") {
    struct KeyboardPreview: View {
        @State private var name = ""
        var body: some View {
            ActionScaffold {
                TextField("Name", text: $name).textFieldStyle(.roundedBorder)
            } primary: {
                Button("Continue") {}.buttonStyle(PrimaryButtonStyle())
            } finePrint: {
                Text("Fine print moves into the content when the keyboard leaves little room.").font(.footnote)
            }
        }
    }
    return KeyboardPreview()
}
