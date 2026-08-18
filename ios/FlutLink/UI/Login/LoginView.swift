// TEST — iOS port created by opencode. Not a production build.
import SwiftUI

struct LoginView: View {
    @ObservedObject var viewModel: LoginViewModel
    let onSignedIn: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()
                VStack(spacing: 8) {
                    Image(systemName: "cloud.fill")
                        .font(.system(size: 64))
                        .foregroundStyle(Color.accentColor)
                    Text("FlutLink")
                        .font(.largeTitle.bold())
                    Text("login_tagline".localized)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                if let step = viewModel.step {
                    Text(step).font(.caption).foregroundStyle(.secondary)
                }
                if let error = viewModel.errorMessage {
                    ErrorBanner(message: error) { viewModel.clearError() }
                }
                Picker("Mode", selection: $viewModel.registerMode) {
                    Text("login_tab_sign_in".localized).tag(false)
                    Text("login_tab_register".localized).tag(true)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                VStack(spacing: 12) {
                    TextField("server_url".localized, text: $viewModel.serverUrl)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.URL)
                        .autocapitalization(.none)
                        .disabled(viewModel.urlLocked)
                    TextField("username".localized, text: $viewModel.username)
                        .textFieldStyle(.roundedBorder)
                        .autocapitalization(.none)
                    if viewModel.registerMode {
                        TextField("login_new_username".localized, text: $viewModel.displayName)
                            .textFieldStyle(.roundedBorder)
                    }
                    SecureField("app_token".localized, text: $viewModel.password)
                        .textFieldStyle(.roundedBorder)
                }
                .padding(.horizontal)
                if viewModel.registerMode {
                    VStack(spacing: 12) {
                        Text("admin_credentials".localized)
                            .font(.headline)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        TextField("admin_username".localized, text: $viewModel.adminUsername)
                            .textFieldStyle(.roundedBorder)
                            .autocapitalization(.none)
                        SecureField("admin_password".localized, text: $viewModel.adminPassword)
                            .textFieldStyle(.roundedBorder)
                    }
                    .padding(.horizontal)
                    Text("register_description".localized)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                }
                Button {
                    if viewModel.registerMode {
                        viewModel.register(onSuccess: onSignedIn)
                    } else {
                        viewModel.signIn(onSuccess: onSignedIn)
                    }
                } label: {
                    if viewModel.loading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text(viewModel.registerMode ? "register".localized : "sign_in".localized)
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(viewModel.loading)
                .padding(.horizontal)
                Spacer()
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

extension String {
    var localized: String { NSLocalizedString(self, comment: "") }
}
