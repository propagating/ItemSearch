/*
 * Copyright (c) 2026, propagating <propagating@protonmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.itemsearch.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import net.runelite.client.game.ItemManager;
import com.itemsearch.model.ShopData;
import com.itemsearch.model.ShopItem;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ShopResultViewTest
{
	private static ShopData shop()
	{
		return new ShopData("Zaff's Superior Staffs", "Varrock", "Coins", Arrays.asList(
			new ShopItem(1381, "Staff of air", 5, 1500, 60),
			new ShopItem(1383, "Staff of water", 0, 1500, 60),
			new ShopItem(-1, "Mystery box", 5, null, null)));
	}

	private static <T> List<T> collect(Container root, Class<T> type)
	{
		List<T> out = new ArrayList<>();
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				out.add(type.cast(c));
			}
			if (c instanceof Container)
			{
				out.addAll(collect((Container) c, type));
			}
		}
		return out;
	}

	private static JLabel labelWithText(Container root, String text)
	{
		for (JLabel l : collect(root, JLabel.class))
		{
			if (l.getText() != null && l.getText().contains(text))
			{
				return l;
			}
		}
		return null;
	}

	@Test
	public void rendersEveryCatalogRowWithPriceAndStock()
	{
		ShopResultView view = new ShopResultView(shop(), mock(ItemManager.class), () ->
		{
		}, i ->
		{
		});
		assertNotNull(labelWithText(view, "Staff of air"));
		assertNotNull(labelWithText(view, "Mystery box"));
		assertNotNull(labelWithText(view, "1,500 gp"));
	}

	@Test
	public void zeroStockRowsAreDimmedAndAnnotated()
	{
		ShopResultView view = new ShopResultView(shop(), mock(ItemManager.class), () ->
		{
		}, i ->
		{
		});
		JLabel name = labelWithText(view, "Staff of water");
		assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, name.getForeground());
		assertNotNull(labelWithText(view, "not stocked"));
	}

	@Test
	public void clickingAResolvedItemFiresCallbackButUnresolvedDoesNot()
	{
		AtomicReference<ShopItem> clicked = new AtomicReference<>();
		ShopResultView view = new ShopResultView(shop(), mock(ItemManager.class), () ->
		{
		}, clicked::set);

		JLabel air = labelWithText(view, "Staff of air");
		MouseListener[] listeners = air.getMouseListeners();
		assertTrue(listeners.length > 0);
		listeners[0].mouseClicked(new MouseEvent(air, MouseEvent.MOUSE_CLICKED,
			0, 0, 0, 0, 1, false));
		assertEquals(1381, clicked.get().getItemId());

		JLabel mystery = labelWithText(view, "Mystery box");
		assertEquals(0, mystery.getMouseListeners().length);
	}
}
