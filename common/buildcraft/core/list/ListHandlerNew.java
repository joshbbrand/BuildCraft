package buildcraft.core.list;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import buildcraft.core.lib.inventory.StackHelper;
import buildcraft.core.lib.utils.NBTUtils;

public class ListHandlerNew {
	public static final int WIDTH = 9;
	public static final int HEIGHT = 2;

	public static class Line {
		public final ItemStack[] stacks;
		public boolean precise, byType, byMaterial;

		public Line() {
			stacks = new ItemStack[WIDTH];
		}

		public boolean isOneStackMode() {
			return byType || byMaterial;
		}

		public boolean getOption(int id) {
			return (id == 0 ? precise : (id == 1 ? byType : byMaterial));
		}

		public void toggleOption(int id) {
			if (byType == false && byMaterial == false && (id == 1 || id == 2)) {
				for (int i = 1; i < stacks.length; i++) {
					stacks[i] = null;
				}
			}
			switch (id) {
				case 0:
					precise = !precise;
					break;
				case 1:
					byType = !byType;
					break;
				case 2:
					byMaterial = !byMaterial;
					break;
			}
		}

		public boolean matches(ItemStack target) {
			if (byType || byMaterial) {
				if (stacks[0] == null) {
					return false;
				}

				List<ListMatchHandler> handlers = ListRegistry.getHandlers();
				ListMatchHandler.Type type = byType ? (byMaterial ? ListMatchHandler.Type.CLASS : ListMatchHandler.Type.TYPE) : ListMatchHandler.Type.MATERIAL;
				for (ListMatchHandler h : handlers) {
					if (h.matches(type, stacks[0], target, precise)) {
						return true;
					}
				}
			} else {
				for (ItemStack s : stacks) {
					if (s != null && StackHelper.isMatchingItem(s, target, precise || target.getItem().getHasSubtypes(), precise)) {
						return true;
					}
				}
			}
			return false;
		}

		public static Line fromNBT(NBTTagCompound data) {
			Line line = new Line();

			if (data != null && data.hasKey("st")) {
				NBTTagList l = data.getTagList("st", 10);
				for (int i = 0; i < l.tagCount(); i++) {
					line.stacks[i] = ItemStack.loadItemStackFromNBT(l.getCompoundTagAt(i));
				}

				line.precise = data.getBoolean("Fp");
				line.byType = data.getBoolean("Ft");
				line.byMaterial = data.getBoolean("Fm");
			}

			return line;
		}

		public NBTTagCompound toNBT() {
			NBTTagCompound data = new NBTTagCompound();
			NBTTagList stackList = new NBTTagList();
			for (int i = 0; i < stacks.length; i++) {
				NBTTagCompound stack = new NBTTagCompound();
				if (stacks[i] != null) {
					stacks[i].writeToNBT(stack);
				}
				stackList.appendTag(stack);
			}
			data.setTag("st", stackList);
			data.setBoolean("Fp", precise);
			data.setBoolean("Ft", byType);
			data.setBoolean("Fm", byMaterial);
			return data;
		}

		public void setStack(int slotIndex, ItemStack stack) {
			if (slotIndex == 0 || (!byType && !byMaterial)) {
				if (stack != null && stack.getItem() != null) {
					stacks[slotIndex] = stack.copy();
					stacks[slotIndex].stackSize = 1;
				} else {
					stacks[slotIndex] = null;
				}
			}
		}

		public ItemStack getStack(int i) {
			return i >= 0 && i < stacks.length ? stacks[i] : null;
		}
	}

	public static Line[] getLines(ItemStack item) {
		NBTTagCompound data = NBTUtils.getItemData(item);
		if (data.hasKey("written") && data.hasKey("lines")) {
			NBTTagList list = data.getTagList("lines", 10);
			Line[] lines = new Line[list.tagCount()];
			for (int i = 0; i < lines.length; i++) {
				lines[i] = Line.fromNBT(list.getCompoundTagAt(i));
			}
			return lines;
		} else {
			Line[] lines = new Line[HEIGHT];
			for (int i = 0; i < lines.length; i++) {
				lines[i] = new Line();
			}
			return lines;
		}
	}

	public static void saveLines(ItemStack stackList, Line[] lines) {
		NBTTagCompound data = NBTUtils.getItemData(stackList);
		data.setBoolean("written", true);
		NBTTagList lineList = new NBTTagList();
		for (Line l : lines) {
			lineList.appendTag(l.toNBT());
		}
		data.setTag("lines", lineList);
	}

	public static boolean matches(ItemStack stackList, ItemStack item) {
		NBTTagCompound data = NBTUtils.getItemData(stackList);
		if (data.hasKey("written") && data.hasKey("lines")) {
			NBTTagList list = data.getTagList("lines", 10);
			for (int i = 0; i < list.tagCount(); i++) {
				Line line = Line.fromNBT(list.getCompoundTagAt(i));
				if (line.matches(item)) {
					return true;
				}
			}
		}

		return false;
	}
}
