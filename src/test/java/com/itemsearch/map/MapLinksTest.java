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
package com.itemsearch.map;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MapLinksTest
{
	@Test
	public void surfaceLocationsNeedNoLink()
	{
		assertNull(MapLinks.layeredMapUrl(3203, 3434, 0, null));
		assertNull(MapLinks.layeredMapUrl(3203, 3434, null, null));
		assertNull(MapLinks.layeredMapUrl(3203, 3434, 0, -1));
		assertNull(MapLinks.layeredMapUrl(3203, 3434, 0, 0));
		assertNull(MapLinks.layeredMapUrl(null, null, 1, 10)); // no coords, nothing to link
	}

	@Test
	public void upperFloorsLinkToTheExplorerWithPlane()
	{
		assertEquals("https://mejrs.github.io/osrs?m=-1&z=4&p=1&x=2662&y=3657",
			MapLinks.layeredMapUrl(2662, 3657, 1, null));
	}

	@Test
	public void nonSurfaceMapIdsLinkToThatLayer()
	{
		assertEquals("https://mejrs.github.io/osrs?m=10&z=4&p=0&x=2857&y=10189",
			MapLinks.layeredMapUrl(2857, 10189, 0, 10));
	}
}
