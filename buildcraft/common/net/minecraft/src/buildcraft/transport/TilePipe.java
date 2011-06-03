package net.minecraft.src.buildcraft.transport;

import java.util.LinkedList;

import net.minecraft.src.BuildCraftTransport;
import net.minecraft.src.IInventory;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.Packet230ModLoader;
import net.minecraft.src.TileEntity;
import net.minecraft.src.mod_BuildCraftTransport;
import net.minecraft.src.buildcraft.api.APIProxy;
import net.minecraft.src.buildcraft.api.EntityPassiveItem;
import net.minecraft.src.buildcraft.api.IPipeEntry;
import net.minecraft.src.buildcraft.api.Orientations;
import net.minecraft.src.buildcraft.api.Position;
import net.minecraft.src.buildcraft.core.CoreProxy;
import net.minecraft.src.buildcraft.core.StackUtil;
import net.minecraft.src.buildcraft.core.TileCurrentPowered;
import net.minecraft.src.buildcraft.core.Utils;

public abstract class TilePipe extends TileCurrentPowered implements IPipeEntry {
	class EntityData {	
		boolean toCenter = true;
		EntityPassiveItem item;
		
		public Orientations orientation;
		
		public EntityData (EntityPassiveItem citem, Orientations orientation) {
			item = citem;
			
			this.orientation = orientation;
		}
	}
	
	LinkedList <EntityData> travelingEntities = new LinkedList <EntityData> ();
	LinkedList <EntityData> entitiesToLoad = new LinkedList <EntityData> ();
	
	public TilePipe () {

	}
	
	public final void entityEntering (EntityPassiveItem item, Orientations orientation) {
		// Readjust the speed
		
		if (item.speed > Utils.pipeNormalSpeed) {
			item.speed = item.speed - Utils.pipeNormalSpeed;
		}
		
		if (item.speed < Utils.pipeNormalSpeed) {
			item.speed = Utils.pipeNormalSpeed;
		}
		
		if (!APIProxy.isClient(worldObj)) {
			concreteEntityEntering(item, orientation);
		}
	}
	
	public void concreteEntityEntering (EntityPassiveItem item, Orientations orientation) {
		travelingEntities.add(new EntityData (item, orientation));		
		
		// Reajusting Ypos to make sure the object looks like sitting on the
		// pipe.
		if (orientation != Orientations.YPos && orientation != Orientations.YNeg) {
			item.setPosition(item.posX, yCoord + Utils.getPipeFloorOf(item.item), item.posZ);
		}				
		
		if (APIProxy.isServerSide()) {
			if (worldObj.getWorldTime() - item.lastSynchronizationDate > 20) {
				item.lastSynchronizationDate = worldObj.getWorldTime(); 
				CoreProxy.sendToPlayers(createItemPacket(item, orientation),
						xCoord, yCoord, zCoord, 50);
			}
		}
	}

	/**
	 * Returns a list of all possible movements, that is to say adjacent 
	 * implementers of IPipeEntry or TileEntityChest.
	 */
	public LinkedList<Orientations> getPossibleMovements(Position pos,
			EntityPassiveItem item) {
		LinkedList<Orientations> result = new LinkedList<Orientations>();
		
		for (int o = 0; o <= 5; ++o) {
			if (Orientations.values()[o] != pos.orientation.reverse()) {
				Position newPos = new Position(pos);
				newPos.orientation = Orientations.values()[o];
				newPos.moveForwards(1.0);

				if (canReceivePipeObjects(newPos, item)) {
					result.add(newPos.orientation);
				}
			}
		}

		return result;
	}
	
	public boolean canReceivePipeObjects(Position p,
			EntityPassiveItem item) {
		TileEntity entity = worldObj.getBlockTileEntity((int) p.x, (int) p.y,
				(int) p.z);
		
		if (entity instanceof IPipeEntry) {
			return true;
		} else if (entity instanceof IInventory) {			
			if (new StackUtil(item.item).checkAvailableSlot((IInventory) entity,
					 false, p.orientation.reverse())) {
				return true;
			}
		}
		
		return false;
	}
	
	public void updateEntity() {
		super.updateEntity();
		
		for (EntityData data : entitiesToLoad) {
			worldObj.entityJoinedWorld(data.item);
			travelingEntities.add(data);
		}
		
		entitiesToLoad.clear();
		
		LinkedList <EntityData> toRemove = new LinkedList <EntityData> ();				
		
		for (EntityData data : travelingEntities) {
			Position motion = new Position (0, 0, 0, data.orientation);
			motion.moveForwards(data.item.speed);			
						
			data.item.motionX = motion.x;
			data.item.motionY = motion.y;
			data.item.motionZ = motion.z;
			
			data.item.moveEntity(motion.x, motion.y, motion.z);
									
			if (data.toCenter && middleReached(data)) {
				data.toCenter = false;
				
				// Reajusting to the middle 

				data.item.setPosition(xCoord + 0.5,
						yCoord + Utils.getPipeFloorOf(data.item.item),
						zCoord + + 0.5);

				Orientations nextOrientation = resolveDestination (data);
				
				if (nextOrientation == Orientations.Unknown) {
					toRemove.add(data);

					data.item.toEntityItem(worldObj, data.orientation);
				} else {
					data.orientation = nextOrientation;
				}
				
				
		    } else if (!data.toCenter && endReached (data)) {
		    	toRemove.add(data);
		    	
				Position destPos = new Position(xCoord, yCoord, zCoord,
						data.orientation);
		    	
				destPos.moveForwards(1.0);
				
				TileEntity tile = worldObj.getBlockTileEntity((int) destPos.x,
						(int) destPos.y, (int) destPos.z);
				
				if (tile instanceof IPipeEntry) {
					((IPipeEntry) tile).entityEntering(data.item,
							data.orientation);
				} else if (tile instanceof IInventory
						&& (new StackUtil(data.item.item).checkAvailableSlot(
								(IInventory) tile, true,
								destPos.orientation.reverse()))) {
					
					APIProxy.removeEntity(worldObj, data.item);
				} else {
					data.item.toEntityItem(worldObj, data.orientation);
				}
		    }
		}	
		
		travelingEntities.removeAll(toRemove);		
	}
	
	public boolean middleReached(EntityData entity) {
		float middleLimit = entity.item.speed * 1.01F;
		return (Math.abs(xCoord + 0.5 - entity.item.posX) < middleLimit
				&& Math.abs(yCoord + Utils.getPipeFloorOf(entity.item.item)
						- entity.item.posY) < middleLimit && Math.abs(zCoord + 0.5
				- entity.item.posZ) < middleLimit);
	}
	
	public boolean endReached (EntityData entity) {
		return entity.item.posX > xCoord + 1.0 
		|| entity.item.posX < xCoord
		|| entity.item.posY > yCoord + 1.0
		|| entity.item.posY < yCoord
		|| entity.item.posZ > zCoord + 1.0
		|| entity.item.posZ < zCoord;
	}
	
	public Position getPosition() {
		return new Position (xCoord, yCoord, zCoord);
	}
	
	public void readFromNBT(NBTTagCompound nbttagcompound)
    {
		super.readFromNBT(nbttagcompound);
		
		NBTTagList nbttaglist = nbttagcompound.getTagList("travelingEntities");		
		
		for (int j = 0; j < nbttaglist.tagCount(); ++j) {
			try {
				NBTTagCompound nbttagcompound2 = (NBTTagCompound) nbttaglist
				.tagAt(j);			

				EntityPassiveItem entity = new EntityPassiveItem (APIProxy.getWorld());
				entity.readFromNBT(nbttagcompound2);

				EntityData data = new EntityData(entity,
						Orientations.values()[nbttagcompound2.getInteger("orientation")]);
				data.toCenter = nbttagcompound2.getBoolean("toCenter"); 

				entitiesToLoad.add(data);
			} catch (Throwable t) {
				//  It may be the case that entities cannot be reloaded between
				//  two versions - ignore these errors.
			}
		}
    }

    public void writeToNBT(NBTTagCompound nbttagcompound) {
    	super.writeToNBT(nbttagcompound);    	
    
    	NBTTagList nbttaglist = new NBTTagList();
    	    	
    	for (EntityData data : travelingEntities) {    		
    		NBTTagCompound nbttagcompound2 = new NBTTagCompound ();
    		nbttaglist.setTag(nbttagcompound2);
    		data.item.writeToNBT(nbttagcompound2);
    		nbttagcompound2.setBoolean("toCenter", data.toCenter);
    		nbttagcompound2.setInteger("orientation", data.orientation.ordinal());    		
    	}
    	
    	nbttagcompound.setTag("travelingEntities", nbttaglist);
    }
    
    public Orientations resolveDestination (EntityData data) {
    	LinkedList<Orientations> listOfPossibleMovements = getPossibleMovements(new Position(
				xCoord, yCoord, zCoord, data.orientation), data.item);
		
		if (listOfPossibleMovements.size() == 0) {					
			return Orientations.Unknown;													
		} else {					
			int i = Math.abs(data.item.entityId + xCoord + yCoord + zCoord)
					% listOfPossibleMovements.size();
			
			return listOfPossibleMovements.get(i);															
		}				
    }
    
    public void destroy () {
    	for (EntityData data : travelingEntities) {
    		data.item.toEntityItem(worldObj, data.orientation);
    	}
    	
    	travelingEntities.clear();
    }
    
    protected void doWork () {}

	public void handleItemPacket(Packet230ModLoader packet) {
		EntityPassiveItem item = (EntityPassiveItem) APIProxy.getEntity(
				worldObj, packet.dataInt[3]);
		
		if (item == null) {
			item = new EntityPassiveItem(worldObj);
			item.entityId = packet.dataInt [3];
			
			int itemId = packet.dataInt [5];
			int stackSize = packet.dataInt [6];
			int dmg = packet.dataInt [7];
			
			item.item = new ItemStack(itemId, stackSize, dmg);		

			APIProxy.storeEntity(worldObj, item);
		}		
		
		Orientations orientation;						
		orientation = Orientations.values()[packet.dataInt [4]];
		
		item.setPosition(packet.dataFloat[0], packet.dataFloat[1],
				packet.dataFloat[2]);
		item.speed = packet.dataFloat [3];
		
		concreteEntityEntering(item, orientation);
	}
	
	public Packet230ModLoader createItemPacket (EntityPassiveItem item, Orientations orientation) {
		Packet230ModLoader packet = new Packet230ModLoader();
		
		packet.modId = mod_BuildCraftTransport.instance.getId();
		packet.packetType = BuildCraftTransport.tilePipeItemPacket;
		
		packet.dataInt = new int [8];
		packet.dataInt [0] = xCoord;
		packet.dataInt [1] = yCoord;
		packet.dataInt [2] = zCoord;
		packet.dataInt [3] = item.entityId;
		packet.dataInt [4] = orientation.ordinal();
		packet.dataInt [5] = item.item.itemID;
		packet.dataInt [6] = item.item.stackSize;
		packet.dataInt [7] = item.item.getItemDamage();
		
		packet.dataFloat = new float [4];
		packet.dataFloat [0] = (float) item.posX;
		packet.dataFloat [1] = (float) item.posY;
		packet.dataFloat [2] = (float) item.posZ;
		packet.dataFloat [3] = (float) item.speed;
		
		return packet;		
	}
	
}
