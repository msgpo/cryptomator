package org.cryptomator.ui.fxapp;

import dagger.Lazy;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.stage.Stage;
import org.cryptomator.common.LicenseHolder;
import org.cryptomator.common.settings.Settings;
import org.cryptomator.common.settings.UiTheme;
import org.cryptomator.common.vaults.Vault;
import org.cryptomator.jni.JniException;
import org.cryptomator.jni.MacApplicationUiAppearance;
import org.cryptomator.jni.MacApplicationUiState;
import org.cryptomator.jni.MacFunctions;
import org.cryptomator.ui.common.VaultService;
import org.cryptomator.ui.mainwindow.MainWindowComponent;
import org.cryptomator.ui.preferences.PreferencesComponent;
import org.cryptomator.ui.preferences.SelectedPreferencesTab;
import org.cryptomator.ui.quit.QuitComponent;
import org.cryptomator.ui.unlock.UnlockComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.awt.desktop.QuitResponse;
import java.util.Optional;

@FxApplicationScoped
public class FxApplication extends Application {

	private static final Logger LOG = LoggerFactory.getLogger(FxApplication.class);

	private final Settings settings;
	private final Lazy<MainWindowComponent> mainWindow;
	private final Lazy<PreferencesComponent> preferencesWindow;
	private final Provider<UnlockComponent.Builder> unlockWindowBuilderProvider;
	private final Provider<QuitComponent.Builder> quitWindowBuilderProvider;
	private final Optional<MacFunctions> macFunctions;
	private final VaultService vaultService;
	private final LicenseHolder licenseHolder;
	private final BooleanBinding hasVisibleStages;

	@Inject
	FxApplication(Settings settings, Lazy<MainWindowComponent> mainWindow, Lazy<PreferencesComponent> preferencesWindow, Provider<UnlockComponent.Builder> unlockWindowBuilderProvider, Provider<QuitComponent.Builder> quitWindowBuilderProvider, Optional<MacFunctions> macFunctions, VaultService vaultService, LicenseHolder licenseHolder, ObservableSet<Stage> visibleStages) {
		this.settings = settings;
		this.mainWindow = mainWindow;
		this.preferencesWindow = preferencesWindow;
		this.unlockWindowBuilderProvider = unlockWindowBuilderProvider;
		this.quitWindowBuilderProvider = quitWindowBuilderProvider;
		this.macFunctions = macFunctions;
		this.vaultService = vaultService;
		this.licenseHolder = licenseHolder;
		this.hasVisibleStages = Bindings.isNotEmpty(visibleStages);
	}

	public void start() {
		LOG.trace("FxApplication.start()");
		Platform.setImplicitExit(false);

		hasVisibleStages.addListener(this::hasVisibleStagesChanged);

		settings.theme().addListener(this::themeChanged);
		loadSelectedStyleSheet(settings.theme().get());
	}

	@Override
	public void start(Stage stage) {
		throw new UnsupportedOperationException("Use start() instead.");
	}

	private void hasVisibleStagesChanged(@SuppressWarnings("unused") ObservableValue<? extends Boolean> observableValue, @SuppressWarnings("unused") boolean oldValue, boolean newValue) {
		if (newValue) {
			macFunctions.map(MacFunctions::uiState).ifPresent(MacApplicationUiState::transformToForegroundApplication);
		} else {
			macFunctions.map(MacFunctions::uiState).ifPresent(MacApplicationUiState::transformToAgentApplication);
		}
	}

	public void showPreferencesWindow(SelectedPreferencesTab selectedTab) {
		Platform.runLater(() -> {
			preferencesWindow.get().showPreferencesWindow(selectedTab);
			LOG.debug("Showing Preferences");
		});
	}

	public void showMainWindow() {
		Platform.runLater(() -> {
			mainWindow.get().showMainWindow();
			LOG.debug("Showing MainWindow");
		});
	}

	public void startUnlockWorkflow(Vault vault) {
		Platform.runLater(() -> {
			unlockWindowBuilderProvider.get().vault(vault).build().startUnlockWorkflow();
			LOG.debug("Showing UnlockWindow for {}", vault.getDisplayableName());
		});
	}

	public void showQuitWindow(QuitResponse response) {
		Platform.runLater(() -> {
			quitWindowBuilderProvider.get().quitResponse(response).build().showQuitWindow();
			LOG.debug("Showing QuitWindow");
		});
	}

	public VaultService getVaultService() {
		return vaultService;
	}

	private void themeChanged(@SuppressWarnings("unused") ObservableValue<? extends UiTheme> observable, @SuppressWarnings("unused") UiTheme oldValue, UiTheme newValue) {
		loadSelectedStyleSheet(newValue);
	}

	private void loadSelectedStyleSheet(UiTheme desiredTheme) {
		UiTheme theme = licenseHolder.isValidLicense() ? desiredTheme : UiTheme.LIGHT;
		switch (theme) {
			case DARK:
				Application.setUserAgentStylesheet(getClass().getResource("/css/dark_theme.css").toString());
				macFunctions.map(MacFunctions::uiAppearance).ifPresent(JniException.ignore(MacApplicationUiAppearance::setToDarkAqua));
				break;
			case LIGHT:
			default:
				Application.setUserAgentStylesheet(getClass().getResource("/css/light_theme.css").toString());
				macFunctions.map(MacFunctions::uiAppearance).ifPresent(JniException.ignore(MacApplicationUiAppearance::setToAqua));
				break;
		}
	}

}
