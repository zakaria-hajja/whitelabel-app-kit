import SwiftUI
import Shared

/** One item, opened from the list or from `<scheme>://catalog/<id>`, on the shared CatalogDetailViewModel. */
struct CatalogDetailScreen: View {
    let id: String

    @State private var screen: ScreenModel<CatalogDetailState, CatalogDetailAction>

    init(id: String) {
        self.id = id
        _screen = State(initialValue: ScreenModel {
            let viewModel = SharedGraph.shared.catalogDetailViewModel(id: id)
            return SharedModel(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
        })
    }

    var body: some View {
        let model = screen.model
        CatalogDetailContent(state: model.state) { model.send(CatalogDetailActionOnRetry.shared) }
            .navigationTitle(model.state.item?.title ?? KitStrings.text("Catalog"))
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct CatalogDetailContent: View {
    let state: CatalogDetailState
    let onRetry: () -> Void
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        if let item = state.item {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    CatalogImage(url: item.imageUrl).frame(maxWidth: .infinity).aspectRatio(16 / 9, contentMode: .fit)
                    Text(item.title).font(style.typography.headline).foregroundStyle(style.colors.onBackground).accessibilityAddTraits(.isHeader)
                    if let category = item.category {
                        Text(category).font(style.typography.label).foregroundStyle(style.colors.primary)
                    }
                    if let description = item.description_ {
                        Text(description).font(style.typography.body).foregroundStyle(style.colors.onSurface)
                    }
                }
                .padding(style.pack.sizing.screenGutter)
            }
        } else if state.notFound {
            EmptyStateView(title: "", message: KitStrings.text("This item doesn't exist or is no longer available."))
        } else if let error = state.error {
            SharedErrorState(message: error, onRetry: onRetry)
        } else {
            LoadingView()
        }
    }
}

#Preview("Detail") {
    PackPreviews {
        CatalogDetailContent(state: CatalogDetailState(item: CatalogItem(id: "11", title: "Item 11", description: "Sample item 11", category: "Music", imageUrl: nil), isLoading: false, notFound: false, error: nil), onRetry: {})
            .frame(height: 420)
    }
}

#Preview("Detail states") {
    PackPreviews {
        CatalogDetailContent(state: CatalogDetailState(item: nil, isLoading: true, notFound: false, error: nil), onRetry: {}).frame(height: 120)
        CatalogDetailContent(state: CatalogDetailState(item: nil, isLoading: false, notFound: true, error: nil), onRetry: {}).frame(height: 160)
        CatalogDetailContent(state: CatalogDetailState(item: nil, isLoading: false, notFound: false, error: UiTextDynamic(value: "No internet connection. Check your connection and try again.")), onRetry: {}).frame(height: 200)
    }
}
