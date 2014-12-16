/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located
 * as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.pipes;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.common.util.ForgeDirection;
import buildcraft.api.core.INBTStoreable;
import buildcraft.api.core.ISerializable;

/**
 * An IPipePluggable MUST have an empty constructor for client-side
 * rendering!
 */
public abstract class PipePluggable implements INBTStoreable, ISerializable {
	public abstract ItemStack[] getDropItems(IPipeContainer pipe);

	public void update(IPipeContainer pipe, ForgeDirection direction) {

	}

	public void onAttachedPipe(IPipeContainer pipe, ForgeDirection direction) {
		validate(pipe, direction);
	}

	public void onDetachedPipe(IPipeContainer pipe, ForgeDirection direction) {
		invalidate();
	}

	public abstract boolean isBlocking(IPipeContainer pipe, ForgeDirection direction);

	public void invalidate() {

	}

	public void validate(IPipeContainer pipe, ForgeDirection direction) {

	}

	public boolean isSolidOnSide(IPipeContainer pipe, ForgeDirection direction) {
		return false;
	}

	public abstract AxisAlignedBB getBoundingBox(ForgeDirection side);

	@SideOnly(Side.CLIENT)
	public abstract IPipePluggableRenderer getRenderer();
}
