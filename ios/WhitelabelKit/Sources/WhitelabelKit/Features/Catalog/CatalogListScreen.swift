import SwiftUI
import Shared

/** The paginated, searchable list on the shared CatalogListViewModel: search, category chips, sort, paging, retry. */
struct CatalogListScreen: View {
    let options: CatalogOptions

    @State private var screen: ScreenModel<CatalogListState, CatalogListAction>
    @State private var query = ""

    init(options: CatalogOptions) {
        self.options = options
        _screen = State(initialValue: ScreenModel {
            let viewModel = SharedGraph.shared.catalogListViewModel(options: options)
            return SharedModel(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
        })
    }

    var body: some View {
        let model = screen.model
        CatalogListContent(state: model.state, categories: options.categories, send: model.send)
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: KitStrings.text("Search")
            )
            .bufferedText(model.state.text, local: $query) { model.send(CatalogListActionOnQueryChange(text: $0)) }
            .refreshable {
                model.send(CatalogListActionOnRefresh.shared)
                while true {
                    try? await Task.sleep(nanoseconds: 100_000_000)
                    if !model.state.isRefreshing || Task.isCancelled { break }
                }
            }
            .navigationTitle(KitStrings.text("Catalog"))
            .messageBanner(model.state.message) { model.send(CatalogListActionOnMessageShown.shared) }
    }
}

struct CatalogListContent: View {
    let state: CatalogListState
    let categories: [String]
    let send: (CatalogListAction) -> Void
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        // One list: the sort and chips row stays on top whatever the state, and the states are its rows.
        List {
            chips
                .listRowInsets(EdgeInsets())
                .listRowSeparator(.hidden)
            content
        }
        .listStyle(.plain)
        .background(style.colors.background)
    }

    @ViewBuilder private var content: some View {
        if state.unavailable {
            EmptyStateView(title: KitStrings.text("Catalog unavailable"), message: KitStrings.text("The catalog isn't available right now."))
                .listRowSeparator(.hidden)
        } else if let error = state.firstPageError, state.items.isEmpty {
            SharedErrorState(message: error) { send(CatalogListActionOnRetry.shared) }
                .listRowSeparator(.hidden)
        } else if state.items.isEmpty && state.isLoadingFirst {
            LoadingView().listRowSeparator(.hidden)
        } else if state.items.isEmpty {
            EmptyStateView(title: KitStrings.text("No results"), message: KitStrings.text("Try another search or filter."))
                .listRowSeparator(.hidden)
        } else {
            ForEach(state.items, id: \.id) { item in
                NavigationLink(value: Destination(CatalogRoutes.shared.DETAIL, args: ["id": item.id])) {
                    CatalogRow(item: item)
                }
                .onAppear { if item.id == state.items.last?.id { send(CatalogListActionOnNearEnd.shared) } }
            }
            if state.isLoadingMore {
                ProgressView().frame(maxWidth: .infinity)
            } else if let error = state.moreError {
                SharedErrorState(message: error) { send(CatalogListActionOnRetry.shared) }
            }
        }
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                // Here rather than in the toolbar, which iOS hides while the search is active.
                SortMenu(selected: state.sort) { send(CatalogListActionOnSortSelect(sort: $0)) }
                if !categories.isEmpty {
                    chip(KitStrings.text("All"), selected: state.category == nil) { send(CatalogListActionOnCategorySelect(category: nil)) }
                }
                ForEach(categories, id: \.self) { category in
                    chip(category, selected: state.category == category) { send(CatalogListActionOnCategorySelect(category: category)) }
                }
            }
            .padding(.horizontal, style.pack.sizing.screenGutter)
            .padding(.vertical, Spacing.sm)
        }
    }

    private func chip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(style.typography.label)
                .padding(.horizontal, Spacing.md)
                .frame(minHeight: 36)
                .foregroundStyle(selected ? style.colors.onPrimary : style.colors.onSurface)
                .background(selected ? style.colors.primary : style.colors.surfaceVariant, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct CatalogRow: View {
    let item: CatalogItem
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        HStack(spacing: Spacing.md) {
            CatalogImage(url: item.imageUrl).frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(item.title).font(style.typography.title).foregroundStyle(style.colors.onSurface)
                if let category = item.category {
                    Text(category).font(style.typography.label).foregroundStyle(style.colors.onSurfaceVariant)
                }
            }
        }
        .padding(.vertical, Spacing.xs)
        .accessibilityElement(children: .combine)
    }
}

/** The sort orders, labelled as on Android. */
private struct SortMenu: View {
    let selected: CatalogSort
    let onSelect: (CatalogSort) -> Void

    var body: some View {
        Menu {
            ForEach(Self.sorts, id: \.self) { sort in
                Button { onSelect(sort) } label: {
                    if sort == selected { Label(Self.label(sort), systemImage: "checkmark") } else { Text(Self.label(sort)) }
                }
            }
        } label: {
            Label(KitStrings.text("Sort"), systemImage: "arrow.up.arrow.down")
                .font(.footnote.weight(.semibold))
                .padding(.horizontal, Spacing.md)
                .frame(minHeight: 36)
                .overlay(Capsule().strokeBorder(.secondary.opacity(0.4)))
        }
    }

    static let sorts: [CatalogSort] = [.newest, .oldest, .titleAsc, .titleDesc]

    static func label(_ sort: CatalogSort) -> String {
        switch sort {
        case .oldest: KitStrings.text("Oldest first")
        case .titleAsc: KitStrings.text("Title A–Z")
        case .titleDesc: KitStrings.text("Title Z–A")
        default: KitStrings.text("Newest first")
        }
    }
}

/** A shared error message with a retry. */
struct SharedErrorState: View {
    let message: UiText
    let onRetry: () -> Void
    @State private var text = ""

    var body: some View {
        ErrorStateView(message: text, onRetry: onRetry)
            .task(id: ObjectIdentifier(message)) { text = await resolve(message) }
    }
}
