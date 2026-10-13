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
package org.jackhuang.hmcl.ui.dsh;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckTreeCell;
import com.jfoenix.controls.JFXTreeView;
import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.launch.DshSession;
import com.mcxcc303.hdsl.launch.DshSessions;
import com.mcxcc303.hdsl.pack.DshPackForge;
import com.mcxcc303.hdsl.pack.PackExporter;
import com.mcxcc303.hdsl.pack.PackManifest;
import com.mcxcc303.hdsl.plugin.DshPluginInstaller;
import com.mcxcc303.hdsl.plugin.DshPluginSettings;
import com.mcxcc303.hdsl.skill.DshSkillManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.NoneMultipleSelectionModel;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Asks what of the instance goes into the pack.
///
/// The original's step, which offers a tree of what a game instance holds — mods,
/// worlds, settings — and lets each be left out. What an instance of DeepSeek
/// Harness holds is a configuration and a history: the configuration is what makes
/// the pack a pack and always goes, and the history is the conversations, which is
/// the one thing worth asking about. A pack of a hundred conversations is a
/// hundred megabytes and one of none is a kilobyte, and which of those somebody
/// wants depends on whether they are handing over an environment or a workspace.
///
/// The tree is drawn the way the original draws its own: the same view, the same
/// checkbox cell, and the same rule that a row is not selectable — only its box is.
///
/// One branch is not the original's: a plugin keeps its own settings in the harness's settings file,
/// one section per plugin, and that is most of what "the same environment" means for it. The sections
/// travel unless somebody says otherwise, and the values that look like credentials never do — which
/// is what the branch's note says, because a person handing a pack to somebody else should be told
/// what is in it.
@NotNullByDefault
public final class ModpackFilesPage extends VBox implements WizardPage {
	/// The wizard's settings.
	private final SettingsMap settings;

	/// The boxes for the bundles, so what was unticked can be read when the pack is written.
	private final List<CheckBoxTreeItem<String>> bundleItems = new ArrayList<>();
	/// The boxes for the plugins' own settings, one per section of the harness's settings file.
	private final List<ModpackFileTreeItem> settingsItems = new ArrayList<>();
	/// The boxes for the skill packs, one per pack under the home.
	private final List<ModpackFileTreeItem> skillsItems = new ArrayList<>();
	/// The wizard this page belongs to.
	private final WizardController controller;
	/// The instance being written out.
	private final DshInstance instance;
	/// The box for the conversations.
	private CheckBoxTreeItem<String> sessionsItem;
	/// How many conversations the instance has.
	private int sessionCount;

	/// Creates the page.
	///
	/// @param controller the wizard controller
	/// @param instance   the instance being written out
	/// @param settings   the wizard's settings
	public ModpackFilesPage(WizardController controller, DshInstance instance, SettingsMap settings) {
		this.controller = controller;
		this.instance = instance;
		this.settings = settings;

		setSpacing(10);
		setPadding(new Insets(10));

		Label title = new Label(i18n("dsh.modpack.files.title"));
		getChildren().add(title);

		// A tree, as the original draws this step: the instance at the root, what it holds beneath
		// it, and a box on everything that can travel. Ticking a branch takes its children with it,
		// which is how the original's boxes behave too. The view, the cell and the selection model
		// are the original's three: without them the boxes are the platform's small square ones
		// rather than the round ones every other page of this launcher draws.
		JFXTreeView<String> tree = new JFXTreeView<>(buildTree());
		tree.setCellFactory(view -> new ModpackFileTreeCell());
		tree.setSelectionModel(new NoneMultipleSelectionModel<>());
		tree.setShowRoot(true);
		VBox.setVgrow(tree, Priority.ALWAYS);
		getChildren().add(tree);

		HBox buttons = new HBox(8);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		JFXButton write = FXUtils.newRaisedButton(i18n("modpack.export"));
		write.setPrefSize(100, 40);
		write.setOnAction(event -> {
			cleanup(settings);
			List<String> development = selectedDevelopmentPlugins();
			if (development.isEmpty()) {
				controller.onFinish();
				return;
			}
			// Told to their face rather than found in the pack afterwards: these boxes are ticked
			// and the pack will not hold them. Asked rather than announced, because the answer can
			// be to go back over the list and untick one — which is the only way to make the pack
			// say what the boxes said.
			Controllers.confirm(i18n("dsh.modpack.development.selected", String.join("\n", development)),
			                    i18n("dsh.modpack.development.title"),
			                    MessageType.WARNING,
			                    controller::onFinish,
			                    null);
		});
		buttons.getChildren().add(write);
		getChildren().add(buttons);
	}

	/// Makes a branch's box say what the boxes under it say, now and after every change.
	///
	/// @param branch the branch
	static void follow(CheckBoxTreeItem<String> branch) {
		for (TreeItem<String> child : branch.getChildren()) {
			if (child instanceof CheckBoxTreeItem<String> box) {
				box.selectedProperty().addListener((observable, was, now) -> refreshFrom(box));
				follow(box);
			}
		}
		refreshBranch(branch);
	}

	/// Refreshes a branch and every branch above it.
	///
	/// @param box the box that changed
	private static void refreshFrom(CheckBoxTreeItem<String> box) {
		if (box.getParent() instanceof CheckBoxTreeItem<String> parent) {
			refreshBranch(parent);
			refreshFrom(parent);
		}
	}

	/// Sets one branch's box from the boxes directly under it.
	///
	/// Three answers, not two: everything under it travels, nothing does, or something in between —
	/// and the third is the one JavaFX never draws on its own.
	///
	/// **A partly ticked branch is marked and left otherwise alone.** A branch that is not independent
	/// pushes its own `selected` down onto its children — that is what makes ticking a branch take the
	/// plugins with it, and it is also why clearing `selected` here would untick every child under it.
	/// So the mark is set, and `selected` is only written when the children already agree with it.
	///
	/// @param branch the branch
	static void refreshBranch(CheckBoxTreeItem<String> branch) {
		int travelling = 0;
		int some = 0;
		int boxes = 0;
		for (TreeItem<String> child : branch.getChildren()) {
			if (child instanceof CheckBoxTreeItem<String> box) {
				boxes++;
				boolean ticked = box.isSelected() || box.isIndeterminate();
				if (ticked) {
					some++;
				}
				if (box.isSelected() && !box.isIndeterminate()) {
					travelling++;
				}
			}
		}
		if (boxes == 0) {
			return;
		}
		branch.setIndeterminate(some > 0 && travelling < boxes);
		if (travelling == boxes) {
			branch.setSelected(true);
		} else if (some == 0) {
			branch.setSelected(false);
		}
	}

	/// Writes a pack, reporting what it is doing.
	///
	/// The dialog the person watches is the wizard's own: this returns the work, and the wizard shows
	/// it — which is also what puts the completion message at the end of it. Wrapping it in a dialog
	/// here would put two of them on the screen, and the one that closed last would be the silent one.
	///
	/// @param instance the instance to write out
	/// @param target   where to write it
	/// @param options  what it should say about itself
	/// @param report   receives progress lines
	/// @throws DshException when the pack cannot be written
	static void write(DshInstance instance, Path target, PackManifest.Options options, Consumer<String> report)
	throws DshException {
		PackExporter.export(instance, target, options, report);
	}

	/// Writes a pack the community's tooling reads, and the digest beside it.
	///
	/// A publisher writes the digest next to the pack, which is what the community's index and its
	/// installers check against; writing it here means an exported pack is publishable without
	/// another step.
	///
	/// @param instance the instance to write out
	/// @param target   where to write it
	/// @param options  what it should say about itself
	/// @param report   receives progress lines
	/// @throws DshException when the pack cannot be written
	static void writePackForge(DshInstance instance, Path target, DshPackForge.Options options, Consumer<String> report)
	throws DshException {
		DshPackForge.Result result = DshPackForge.export(instance, target, options, report);
		try {
			Files.writeString(target.resolveSibling(target.getFileName() + ".sha256"),
			                  result.sha256() + "  " + target.getFileName() + "\n");
			report.accept("Wrote " + target.getFileName() + ".sha256");
		} catch (IOException e) {
			// The pack is written and is usable; only its digest is missing, and saying so beats
			// failing an export that succeeded.
			report.accept("The pack was written, but its digest was not: " + e.getMessage());
		}
	}

	/// Builds the tree of what the pack can carry.
	///
	/// @return the root
	private ModpackFileTreeItem buildTree() {
		ModpackFileTreeItem root = new ModpackFileTreeItem(instance.id());
		root.setExpanded(true);
		// The root is not ticked by hand: what it says is what its branches say, and that is computed
		// at the end of this method. Setting it here is what made it read "everything travels" while
		// a whole branch was unticked.

		// The plugins, one box each: a bundle somebody unticks is not in the pack and is not
		// recorded as one of its plugins.
		List<String> bundles = List.of();
		try {
			bundles = DshPluginInstaller.readBundles(instance.homeDirectory(), instance.profile());
		} catch (DshException | RuntimeException e) {
			// The page still works without the list; the pack is then simply written whole.
		}
		ModpackFileTreeItem plugins = new ModpackFileTreeItem(i18n("dsh.modpack.files.plugins", bundles.size()));
		plugins.setExpanded(true);
		for (String bundle : bundles) {
			ModpackFileTreeItem item = new ModpackFileTreeItem(bundle);
			item.setSelected(true);
			plugins.getChildren().add(item);
		}
		bundleItems.clear();
		for (TreeItem<String> child : plugins.getChildren()) {
			bundleItems.add((CheckBoxTreeItem<String>) child);
		}
		root.getChildren().add(plugins);

		// The configuration always travels, so its boxes are there to be seen rather than used.
		ModpackFileTreeItem configuration = new ModpackFileTreeItem(i18n("dsh.modpack.files.configuration"));
		for (String name : List.of("package.json", "cordis.patch.yml")) {
			ModpackFileTreeItem item = new ModpackFileTreeItem(name);
			item.setSelected(true);
			configuration.getChildren().add(item);
		}
		root.getChildren().add(configuration);

		// The plugins' own settings. Each section is a plugin that has been configured, so the
		// branch is also the answer to "what have I actually set up here", which the plugin list
		// alone does not tell anybody.
		List<String> sections = List.of();
		try {
			sections = DshPluginSettings.sectionsOf(instance.homeDirectory());
		} catch (DshException e) {
			// Without the list the pack still carries what the wizard's settings say; this branch is
			// how a person sees and changes it.
		}
		if (!sections.isEmpty()) {
			Set<String> wanted = ModpackExportWizardProvider.settingsOf(settings);
			ModpackFileTreeItem settingsItem = new ModpackFileTreeItem(i18n("dsh.modpack.files.settings",
			                                                                sections.size()));
			settingsItem.setExpanded(true);
			for (String section : sections) {
				ModpackFileTreeItem item = new ModpackFileTreeItem(section);
				item.setSelected(wanted.isEmpty() || wanted.contains(section));
				settingsItem.getChildren().add(item);
				settingsItems.add(item);
			}
			root.getChildren().add(settingsItem);
			follow(settingsItem);
		}

		// The skill packs. Content rather than configuration: a skill cannot be named and
		// fetched the way a plugin can, so a pack that leaves them out cannot reproduce the
		// instance it was made from.
		List<String> skills = List.of();
		try {
			skills = DshSkillManager.packableNames(instance.homeDirectory());
		} catch (DshException e) {
			// Without the list the pack still carries what the wizard settings say; this
			// branch is how a person sees and changes it.
		}
		if (!skills.isEmpty()) {
			Set<String> wanted = ModpackExportWizardProvider.skillsOf(settings);
			ModpackFileTreeItem skillsItem = new ModpackFileTreeItem(i18n("dsh.modpack.files.skills", skills.size()));
			skillsItem.setExpanded(true);
			for (String skill : skills) {
				ModpackFileTreeItem item = new ModpackFileTreeItem(skill);
				item.setSelected(wanted.isEmpty() || wanted.contains(skill));
				skillsItem.getChildren().add(item);
				skillsItems.add(item);
			}
			root.getChildren().add(skillsItem);
			follow(skillsItem);
		}

		// The conversations, and the attachments that go with them.
		List<DshSession> sessions;
		try {
			sessions = DshSessions.list(instance.homeDirectory());
		} catch (DshException e) {
			sessions = List.of();
		}
		sessionCount = sessions.size();

		sessionsItem = new ModpackFileTreeItem(i18n("dsh.modpack.files.sessions"));
		sessionsItem.setSelected(
				Boolean.TRUE.equals(settings.get(ModpackExportWizardProvider.SESSIONS)) && sessionCount > 0);
		sessionsItem.setIndependent(sessionCount == 0);
		CheckBoxTreeItem<String> attachments = new ModpackFileTreeItem(i18n("dsh.modpack.files.attachments"));
		attachments.setSelected(sessionsItem.isSelected());
		sessionsItem.getChildren().add(attachments);
		root.getChildren().add(sessionsItem);

		// And now every branch is told what it is. JavaFX does not do this for us: a child that was
		// already ticked when it was attached never makes its parent recompute, and a branch with one
		// ticked child of two is drawn as fully ticked rather than as partially — which is how the
		// page came to show "plugins" empty with every plugin ticked, and the instance ticked with a
		// branch missing. The marks are what the person reads to know what will travel, so they are
		// ours to keep right, here and on every change under them.
		follow(plugins);
		follow(configuration);
		follow(sessionsItem);
		refreshBranch(root);

		return root;
	}

	@Override
	public void onNavigate(SettingsMap settings) {
		// Nothing to load: what the instance holds was read when the page was built.
	}

	@Override
	public void cleanup(SettingsMap settings) {
		settings.put(ModpackExportWizardProvider.SESSIONS, sessionsItem != null && sessionsItem.isSelected());
		Set<String> excluded = new LinkedHashSet<>();
		for (CheckBoxTreeItem<String> item : bundleItems) {
			if (!item.isSelected()) {
				excluded.add(item.getValue());
			}
		}
		settings.put(ModpackExportWizardProvider.EXCLUDED_BUNDLES, excluded);
		Set<String> sections = new LinkedHashSet<>();
		for (ModpackFileTreeItem item : settingsItems) {
			if (item.isSelected()) {
				sections.add(item.getValue());
			}
		}
		settings.put(ModpackExportWizardProvider.SETTINGS, sections);
		Set<String> skills = new LinkedHashSet<>();
		for (ModpackFileTreeItem item : skillsItems) {
			if (item.isSelected()) {
				skills.add(item.getValue());
			}
		}
		settings.put(ModpackExportWizardProvider.SKILLS, skills);
	}

	/// Returns the plugins somebody ticked that a pack does not carry.
	///
	/// A plugin installed from a directory somebody is working on has no files of the instance's
	/// own to carry, so a ticked box for one is a promise the pack will not keep — and this is the
	/// list that says so.
	///
	/// @return the names, in the tree's order, empty when none of them is a link
	private List<String> selectedDevelopmentPlugins() {
		Set<String> development = new LinkedHashSet<>();
		for (PackExporter.LocalPlugin plugin : ModpackExportWizardProvider.localPluginsOf(settings)) {
			if (plugin.development()) {
				development.add(plugin.name());
			}
		}
		return bundleItems.stream()
		                  .filter(CheckBoxTreeItem::isSelected)
		                  .map(CheckBoxTreeItem::getValue)
		                  .filter(development::contains)
		                  .toList();
	}

	@Override
	public String getTitle() {
		return i18n("dsh.modpack.files");
	}

	/// A row of the tree.
	private static final class ModpackFileTreeItem extends CheckBoxTreeItem<String> {
		/// Creates a row showing its name.
		///
		/// @param name what the row says
		ModpackFileTreeItem(String name) {
			super(name);
		}
	}

	/// Draws a row as the original does: the bound checkbox, then the name.
	///
	/// The checkbox comes from [JFXCheckTreeCell], which is the material checkbox this launcher
	/// draws everywhere else and the one the original's export page uses.
	@NotNullByDefault
	private static final class ModpackFileTreeCell extends JFXCheckTreeCell<String> {
		/// Holds the inherited checkbox followed by the label.
		private final HBox content = new HBox(3);

		/// Shows the current row's name.
		private final Label name = new Label();

		/// Creates a reusable label whose mouse events pass through to the cell.
		private ModpackFileTreeCell() {
			name.setMouseTransparent(true);
			content.setAlignment(Pos.CENTER_LEFT);
			content.setPickOnBounds(false);
		}

		/// Refreshes the labels and removes the content when the cell is cleared.
		@Override
		protected void updateDisplay(@Nullable String item, boolean empty) {
			content.getChildren().clear();
			super.updateDisplay(item, empty);

			name.setText(null);
			if (empty || item == null) {
				return;
			}

			// Setting the text again makes the skin reattach the current graphic to the cell, so it
			// is cleared first and the checkbox graphic is moved into the container instead.
			setText(null);
			@Nullable Node graphic = getGraphic();
			if (graphic != null) {
				content.getChildren().add(graphic);
			}
			name.setText(item);
			content.getChildren().add(name);
			setGraphic(content);
		}
	}
}
