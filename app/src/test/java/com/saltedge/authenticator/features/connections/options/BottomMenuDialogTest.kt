/*
 * This file is part of the Salt Edge Authenticator distribution
 * (https://github.com/saltedge/sca-authenticator-android).
 * Copyright (c) 2019 Salt Edge Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 or later.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * For the additional permissions granted for Salt Edge Authenticator
 * under Section 7 of the GNU General Public License see THIRD_PARTY_NOTICES.md
 */
package com.saltedge.authenticator.features.connections.options

import com.saltedge.authenticator.app.KEY_ID
import com.saltedge.authenticator.features.menu.BottomMenuDialog
import com.saltedge.authenticator.features.menu.MenuItemData
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BottomMenuDialogTest {

    @Test
    @Throws(Exception::class)
    fun newInstanceTest() {
        val arguments = BottomMenuDialog.newInstance(
            menuId = "88", menuItems = listOf(MenuItemData(0, 0, 0))
        ).arguments!!

        assertThat(arguments.getString(KEY_ID), equalTo("88"))
        assertThat(
            arguments.getSerializable(BottomMenuDialog.KEY_ITEMS) as List<MenuItemData>,
            equalTo(listOf(MenuItemData(0, 0, 0)))
        )
    }
}