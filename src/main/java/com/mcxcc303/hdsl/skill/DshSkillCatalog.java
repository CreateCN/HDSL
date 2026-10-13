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
package com.mcxcc303.hdsl.skill;

import com.mcxcc303.hdsl.core.DshException;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;

// A catalogue of published skills
@NotNullByDefault
public interface DshSkillCatalog {
	/**
	 * Returns the name this catalogue is stored and reported by.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * @param query what to search for
	 * @param limit limit of skills to return
	 *
	 * @return queried skill list
	 * @throws DshException when it cannot be read
	 */
	List<DshSkillSource.Offering> search(String query, int limit) throws DshException;
}
