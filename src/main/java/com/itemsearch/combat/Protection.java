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
package com.itemsearch.combat;

import javax.annotation.Nullable;

/**
 * One way to defend against an attack — either a protection prayer or a protecting item, optionally
 * marked as only partially effective (e.g. Protect from Magic against dragonfire) with an extra note.
 */
public final class Protection
{
	private final ProtectionPrayer prayer;
	private final ProtectionItem item;
	private final boolean partial;
	private final String note;

	private Protection(ProtectionPrayer prayer, ProtectionItem item, boolean partial, @Nullable String note)
	{
		this.prayer = prayer;
		this.item = item;
		this.partial = partial;
		this.note = note;
	}

	public static Protection prayer(ProtectionPrayer prayer, boolean partial)
	{
		return new Protection(prayer, null, partial, null);
	}

	public static Protection item(ProtectionItem item, @Nullable String note)
	{
		return new Protection(null, item, false, note);
	}

	public boolean isPrayer()
	{
		return prayer != null;
	}

	public ProtectionPrayer getPrayer()
	{
		return prayer;
	}

	public ProtectionItem getItem()
	{
		return item;
	}

	/** Tooltip text: the prayer/item name, flagged "(partial)" when only partially effective, plus any note. */
	public String getDisplayName()
	{
		String base = prayer != null ? prayer.getDisplayName() : "Protect with " + item.getDisplayName();
		if (partial)
		{
			base += " (partial)";
		}
		if (note != null)
		{
			base += " " + note;
		}
		return base;
	}
}
