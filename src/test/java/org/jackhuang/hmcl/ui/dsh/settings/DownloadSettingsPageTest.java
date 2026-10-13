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
package org.jackhuang.hmcl.ui.dsh.settings;

import com.mcxcc303.hdsl.skill.DshSkillSource;
import com.mcxcc303.hdsl.skill.DshSkillSources;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.FxToolkit;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineSelectButton;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// The download source page and the skill catalogue row it carries
class DownloadSettingsPageTest {

	// Starts the toolkit for this class, or skips the class where it cannot run.
	@BeforeAll
	static void startToolkit() {
		FxToolkit.requireRunning();
	}

	private static void onFxThread(Runnable work) throws Exception {
		FutureTask<Void> task = new FutureTask<>(work, null);
		Platform.runLater(task);
		task.get(30, TimeUnit.SECONDS);
	}

	// Returns the source rows the page draws, in the order it draws them.
	private static List<Node> rows() {
		ComponentList sources = (ComponentList) ((VBox) ((ScrollPane) new DownloadSettingsPage()).getContent())
				.getChildren().get(1);
		return sources.getContent().stream()
		              .filter(LineSelectButton.class::isInstance)
		              .toList();
	}

	// Returns the row that offers the skill catalogues, found by the options it holds.
	private static LineSelectButton<?> skillRow() {
		List<Node> rows = rows().stream()
		                        .filter(row -> ((LineSelectButton<?>) row).getItems()
		                                                                  .contains(DshSkillSources.SKILLS_SH))
		                        .toList();
		assertEquals(1, rows.size(), "exactly one row chooses the skill catalogue");
		return (LineSelectButton<?>) rows.get(0);
	}

	// Returns what one option of a skill row reads as: its name, and the line under it in the list.
	@SuppressWarnings("unchecked")
	private static String[] readsAs(LineSelectButton<?> row, DshSkillSources source) {
		LineSelectButton<DshSkillSources> skillRow = (LineSelectButton<DshSkillSources>) row;
		return new String[]{
				skillRow.getConverter().apply(source),
				skillRow.getDescriptionConverter().apply(source)
		};
	}

	// Returns a skill row as the row whose options are skill sources.
	@SuppressWarnings("unchecked")
	private static LineSelectButton<DshSkillSources> asSkillRow(LineSelectButton<?> row) {
		return (LineSelectButton<DshSkillSources>) row;
	}

	@Test
	void thePageHasARowThatChoosesTheSkillCatalogue() throws Exception {
		onFxThread(() -> {
			assertEquals(3, rows().size(), "npm, the plugin market, and the skill catalogue");

			LineSelectButton<?> row = skillRow();
			assertEquals(List.of(DshSkillSources.values()), List.copyOf(row.getItems()));
			assertEquals(DshSkillSource.selectedSource(),
			             row.getValue(),
			             "the row opens on the source a search would use, not on nothing");
			for (DshSkillSources source : DshSkillSources.values()) {
				String[] texts = readsAs(row, source);
				assertEquals(source.label(), texts[0]);
				assertFalse(texts[1].isBlank(), source + " says what it is in the list the row opens");
			}
		});
	}

	@Test
	void choosingASourceIsWrittenDownAndIsTheSourceASearchUses() throws Exception {
		onFxThread(() -> {
			DshSkillSources was = DshSkillSource.selectedSource();
			DshSkillSources now = was == DshSkillSources.SKILLS_MP ? DshSkillSources.SKILLS_SH : DshSkillSources.SKILLS_MP;
			try {
				asSkillRow(skillRow()).setValue(now);
				assertEquals(now, DshSkillSource.selectedSource(), "the choice is what the next search reads");
				assertSame(now.catalog(), DshSkillSource.selected(), "and it is that catalogue that is asked");
				assertEquals(now.id(), SettingsManager.settings().skillSourceProperty().get(), "and it is stored");
			} finally {
				DshSkillSource.setSelectedSource(was);
			}
			assertEquals(was.id(),
			             SettingsManager.settings().skillSourceProperty().get(),
			             "the choice is written down, so the next start reads it back");
		});
	}
}
