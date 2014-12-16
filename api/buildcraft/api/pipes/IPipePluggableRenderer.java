package buildcraft.api.pipes;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraftforge.common.util.ForgeDirection;
import buildcraft.api.core.render.ITextureStates;

public interface IPipePluggableRenderer {
	void renderPluggable(RenderBlocks renderblocks, IPipe pipe, ForgeDirection side,
						 PipePluggable pipePluggable, ITextureStates blockStateMachine,
						 int renderPass, int x, int y, int z);
}
