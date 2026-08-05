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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
		return new ShopData("Zaff's Superior Staffs", "Varrock", "Coins", 3203, 3434, 0, Arrays.asList(
			new ShopItem(1381, "Staff of air", 5, 1500, 60),
			new ShopItem(1383, "Staff of water", 0, 1500, 60),
			new ShopItem(-1, "Mystery box", 5, null, null)));
	}

	private static ShopData shopWithoutCoords()
	{
		return new ShopData("Wandering Merchant", null, "Coins", null, null, null,
			Arrays.asList(new ShopItem(1381, "Staff of air", 5, 1500, 60)));
	}

	private static ShopResultView view(ShopData shop, AtomicReference<ShopData> marked)
	{
		return new ShopResultView(shop, mock(ItemManager.class), () ->
		{
		}, i ->
		{
		}, marked::set);
	}

	@Test
	public void showOnMapButtonFiresCallbackWhenCoordsPresent()
	{
		AtomicReference<ShopData> marked = new AtomicReference<>();
		ShopResultView v = view(shop(), marked);
		JButton button = collect(v, JButton.class).stream()
			.filter(b -> "Show on map".equals(b.getText()))
			.findFirst().orElse(null);
		assertNotNull("a Show on map button must render when the shop has coordinates", button);
		button.doClick();
		assertEquals("Zaff's Superior Staffs", marked.get().getName());
	}

	@Test
	public void noMapButtonWithoutCoordinates()
	{
		ShopResultView v = view(shopWithoutCoords(), new AtomicReference<>());
		assertTrue("no Show on map button expected without coordinates",
			collect(v, JButton.class).stream().noneMatch(b -> "Show on map".equals(b.getText())));
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
		}, m ->
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
		}, m ->
		{
		});
		JLabel name = labelWithText(view, "Staff of water");
		assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, name.getForeground());
		assertNotNull(labelWithText(view, "not stocked"));
	}

	// navigation fires on PRESS, not click: mouseClicked silently swallows any click
	// where the mouse drifts a pixel between press and release, which reads as "lag"
	@Test
	public void pressingAResolvedItemNameFiresCallbackButUnresolvedDoesNot()
	{
		AtomicReference<ShopItem> clicked = new AtomicReference<>();
		ShopResultView view = new ShopResultView(shop(), mock(ItemManager.class), () ->
		{
		}, clicked::set, m ->
		{
		});

		JLabel air = labelWithText(view, "Staff of air");
		assertTrue(air.getMouseListeners().length > 0);
		press(air);
		assertEquals(1381, clicked.get().getItemId());

		JLabel mystery = labelWithText(view, "Mystery box");
		assertEquals(0, mystery.getMouseListeners().length);
	}

	@Test
	public void pressingAnywhereOnAResolvedRowFiresCallback()
	{
		AtomicReference<ShopItem> clicked = new AtomicReference<>();
		ShopResultView view = new ShopResultView(shop(), mock(ItemManager.class), () ->
		{
		}, clicked::set, m ->
		{
		});

		JPanel row = rowWithLabel(view, "Staff of air");
		assertNotNull(row);
		assertTrue("the whole row must carry a mouse listener", row.getMouseListeners().length > 0);
		press(row);
		assertEquals(1381, clicked.get().getItemId());

		JPanel mysteryRow = rowWithLabel(view, "Mystery box");
		assertEquals("unresolved rows must not be clickable", 0, mysteryRow.getMouseListeners().length);
	}

	// the innermost JPanel whose DIRECT children include a label with the given text
	private static JPanel rowWithLabel(Container root, String text)
	{
		for (JPanel p : collect(root, JPanel.class))
		{
			for (Component c : p.getComponents())
			{
				if (c instanceof JLabel && text.equals(((JLabel) c).getText()))
				{
					return p;
				}
			}
		}
		return null;
	}

	private static void press(Component c)
	{
		MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false);
		for (MouseListener l : c.getMouseListeners())
		{
			l.mousePressed(e);
		}
	}
}
