/*
 * HDSL
 * Copyright (C) 2026  HDSL contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.dsh.install;

import com.mcxcc303.hdsl.harness.DshPackageRegistry;
import com.mcxcc303.hdsl.harness.DshPreset;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.dsh.PluginVersionList;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * The page opened by clicking a plugin card.
 *
 * <p>Nothing is downloaded here: the wizard records the chosen version, and the download happens when the create page is finished.
 */
@NotNullByDefault
public final class PresetChoicePage extends VBox implements WizardPage {
	// The wizard controller used to return
	private final WizardController controller;

	// The catalogue entry being configured
	private final DshPreset preset;

	// Creates the page.
	public PresetChoicePage(WizardController controller, DshPreset preset) {
		this.controller = controller;
		this.preset = preset;

		setPadding(new Insets(16));
		setSpacing(10);

		Label title = new Label(preset.name());
		title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

		Label description = new Label(preset.description());
		description.setWrapText(true);

		PluginVersionList versions = new PluginVersionList(DshPackageRegistry.packageNameOf(preset.spec()),
		                                                   null,
		                                                   null,
		                                                   this::choose,
		                                                   this::choose);
		getChildren().addAll(title, description, versions);
		VBox.setVgrow(versions, Priority.ALWAYS);

		// Judged against the harness the previous page chose, which is not installed yet.
		SettingsMap settings = controller.getSettings();
		String chosen = DshInstallWizardProvider.presetChoices(settings).get(preset.id());
		versions.refresh(null,
		                 settings.get(DshInstallWizardProvider.VERSION),
		                 chosen == null || chosen.isBlank() ? null : chosen);
	}

	// Records the choice and returns to the quick-install page.
	private void choose(String version) {
		DshInstallWizardProvider.presetChoices(controller.getSettings()).put(preset.id(), version);
		controller.onPrev(false);
	}

	@Override
	public void onNavigate(SettingsMap settings) {
		// Reaching this page again means returning from it; nothing to refresh.
	}

	@Override
	public String getTitle() {
		return i18n("dsh.install.plugin.choose");
	}
}
