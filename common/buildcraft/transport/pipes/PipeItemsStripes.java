/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.transport.pipes;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.IEnergyHandler;

import buildcraft.BuildCraftTransport;
import buildcraft.api.core.IIconProvider;
import buildcraft.api.core.Position;
import buildcraft.api.transport.IStripesHandler;
import buildcraft.api.transport.IStripesHandler.StripesHandlerType;
import buildcraft.api.transport.IStripesPipe;
import buildcraft.api.transport.PipeManager;
import buildcraft.core.inventory.InvUtils;
import buildcraft.core.proxy.CoreProxy;
import buildcraft.core.utils.BlockUtils;
import buildcraft.transport.Pipe;
import buildcraft.transport.PipeIconProvider;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TileGenericPipe;
import buildcraft.transport.TravelingItem;
import buildcraft.transport.pipes.events.PipeEventItem;
import buildcraft.transport.stripes.StripesHandlerDefault;
import buildcraft.transport.utils.TransportUtils;

public class PipeItemsStripes extends Pipe<PipeTransportItems> implements IEnergyHandler, IStripesPipe {

	private static IStripesHandler defaultItemsHandler = new StripesHandlerDefault();

	public PipeItemsStripes(Item item) {
		super(new PipeTransportItems(), item);
	}

	@Override
	public void updateEntity() {
		super.updateEntity();

		if (container.getWorldObj().isRemote) {
			return;
		}
	}

	public void eventHandler(PipeEventItem.DropItem event) {
		if (container.getWorldObj().isRemote) {
			return;
		}
		
		Position p = new Position(container.xCoord, container.yCoord,
				container.zCoord, event.direction);
		p.moveForwards(1.0);

		ItemStack stack = event.entity.getEntityItem();
		EntityPlayer player = CoreProxy.proxy.getBuildCraftPlayer((WorldServer) getWorld(),
				(int) p.x, (int) p.y, (int) p.z).get();
		
		switch (event.direction) {
			case DOWN:
				player.rotationPitch = 90;
				player.rotationYaw = 0;
				break;
			case UP:
				player.rotationPitch = 270;
				player.rotationYaw = 0;
				break;
			case NORTH:
				player.rotationPitch = 0;
				player.rotationYaw = 180;
				break;
			case SOUTH:
				player.rotationPitch = 0;
				player.rotationYaw = 0;
				break;
			case WEST:
				player.rotationPitch = 0;
				player.rotationYaw = 90;
				break;
			case EAST:
				player.rotationPitch = 0;
				player.rotationYaw = 270;
				break;
			case UNKNOWN:
				break;
		}
		
		/**
		 * Check if there's a handler for this item type.
		 */
		for (IStripesHandler handler : PipeManager.stripesHandlers) {
			if (handler.getType() == StripesHandlerType.ITEM_USE
					&& handler.shouldHandle(stack)) {
				if (handler.handle(getWorld(), (int) p.x, (int) p.y, (int) p.z,
						event.direction, stack, player, this)) {
					event.entity = null;
					return;
				}
			}
		}

		if (defaultItemsHandler.handle(getWorld(), (int) p.x, (int) p.y, (int) p.z,
				event.direction, stack, player, this)) {
			event.entity = null;
		}
	}
	
	@Override
	public void dropItem(ItemStack itemStack, ForgeDirection direction) {
		Position p = new Position(container.xCoord, container.yCoord,
				container.zCoord, direction);
		p.moveForwards(1.0);

		InvUtils.dropItems(getWorld(), itemStack, (int) p.x, (int) p.y, (int) p.z);

	}
	
	@Override
	public void sendItem(ItemStack itemStack, ForgeDirection direction) {
		TravelingItem newItem = TravelingItem.make(
				container.xCoord + 0.5,
				container.yCoord + TransportUtils.getPipeFloorOf(itemStack),
				container.zCoord + 0.5, itemStack);
		transport.injectItem(newItem, direction);
	}

	@Override
	public IIconProvider getIconProvider() {
		return BuildCraftTransport.instance.pipeIconProvider;
	}

	@Override
	public int getIconIndex(ForgeDirection direction) {
		return PipeIconProvider.TYPE.Stripes.ordinal();
	}

	@Override
	public boolean canPipeConnect(TileEntity tile, ForgeDirection side) {
		if (tile instanceof TileGenericPipe) {
			TileGenericPipe tilePipe = (TileGenericPipe) tile;

			if (tilePipe.pipe instanceof PipeItemsStripes) {
				return false;
			}
		}

		return super.canPipeConnect(tile, side);
	}

	@Override
	public boolean canConnectEnergy(ForgeDirection from) {
		return true;
	}

	@Override
	public int receiveEnergy(ForgeDirection from, int maxReceive,
			boolean simulate) {
		if (maxReceive == 0) {
			return 0;
		} else if (simulate) {
			return maxReceive;
		}

		ForgeDirection o = getOpenOrientation();

		if (o != ForgeDirection.UNKNOWN) {
			Position p = new Position(container.xCoord, container.yCoord,
					container.zCoord, o);
			p.moveForwards(1.0);

			if (!BlockUtils.isUnbreakableBlock(getWorld(), (int) p.x, (int) p.y, (int) p.z)) {
				Block block = getWorld().getBlock((int) p.x, (int) p.y, (int) p.z);
				int metadata = getWorld().getBlockMetadata((int) p.x, (int) p.y, (int) p.z);
				
				ItemStack stack = new ItemStack(block, 1, metadata);
				EntityPlayer player = CoreProxy.proxy.getBuildCraftPlayer((WorldServer) getWorld(),
						(int) p.x, (int) p.y, (int) p.z).get();
				
				for (IStripesHandler handler : PipeManager.stripesHandlers) {
					if (handler.getType() == StripesHandlerType.BLOCK_BREAK
							&& handler.shouldHandle(stack)) {
						if (handler.handle(getWorld(), (int) p.x, (int) p.y, (int) p.z,
								o, stack, player, this)) {
							return maxReceive;
						}
					}
				}
				
				ArrayList<ItemStack> stacks = block.getDrops(
						getWorld(), (int) p.x, (int) p.y, (int) p.z,
						metadata, 0
				);

				if (stacks != null) {
					for (ItemStack s : stacks) {
						if (s != null) {
							sendItem(s, o.getOpposite());
						}
					}
				}

				getWorld().setBlockToAir((int) p.x, (int) p.y, (int) p.z);
			}
		}

		return maxReceive;
	}

	@Override
	public int extractEnergy(ForgeDirection from, int maxExtract,
			boolean simulate) {
		return 0;
	}

	@Override
	public int getEnergyStored(ForgeDirection from) {
		return 0;
	}

	@Override
	public int getMaxEnergyStored(ForgeDirection from) {
		return 10;
	}
}
