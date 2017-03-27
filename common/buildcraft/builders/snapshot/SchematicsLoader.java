package buildcraft.builders.snapshot;

import buildcraft.lib.misc.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.items.CapabilityItemHandler;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public enum SchematicsLoader {
    INSTANCE;

    private Set<JsonRule> getRules(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block
    ) {
        return RulesLoader.INSTANCE.rules.stream()
                .filter(rule -> rule.selectors != null)
                .filter(rule ->
                        rule.selectors.stream()
                                .anyMatch(selector -> {
                                    boolean complex = selector.contains("[");
                                    return Block.getBlockFromName(
                                            complex
                                                    ? selector.substring(0, selector.indexOf("["))
                                                    : selector
                                    ) == block &&
                                            (!complex ||
                                                    Arrays.stream(
                                                            selector.substring(
                                                                    selector.indexOf("[") + 1,
                                                                    selector.indexOf("]")
                                                            )
                                                                    .split(",")
                                                    )
                                                            .map(nameValue -> nameValue.split("="))
                                                            .allMatch(nameValue ->
                                                                    blockState.getPropertyKeys().stream()
                                                                            .filter(property ->
                                                                                    property.getName().equals(nameValue[0])
                                                                            )
                                                                            .findFirst()
                                                                            .map(blockState::getValue)
                                                                            .map(Object::toString)
                                                                            .map(nameValue[1]::equals)
                                                                            .orElse(false)
                                                            )
                                            );
                                })
                )
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean setRequiredBlockOffsets(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block,
            Set<JsonRule> rules,
            SchematicBlock schematicBlock
    ) {
        Set<BlockPos> requiredBlockOffsets = rules.stream()
                .filter(rule -> rule.requiredBlockOffsets != null)
                .map(rule -> rule.requiredBlockOffsets)
                .flatMap(poses -> poses.stream().map(ints -> new BlockPos(ints[0], ints[1], ints[2])))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (block instanceof BlockFalling) {
            requiredBlockOffsets.add(new BlockPos(0, -1, 0));
        }
        rules.stream()
                .map(rule -> rule.copyOppositeRequiredBlockOffsetFromProperty)
                .forEach(propertyName ->
                        blockState.getProperties().keySet().stream()
                                .filter(property -> property.getName().equals(propertyName))
                                .map(property -> (PropertyDirection) property)
                                .map(blockState::getValue)
                                .map(EnumFacing::getOpposite)
                                .map(EnumFacing::getDirectionVec)
                                .map(BlockPos::new)
                                .forEach(requiredBlockOffsets::add)
                );
        if (rules.stream().anyMatch(rule -> rule.copyRequiredBlockOffsetsFromProperties)) {
            for (EnumFacing side : EnumFacing.values()) {
                if (blockState.getProperties().keySet().stream()
                        .filter(property -> property.getName().equals(side.getName()))
                        .map(property -> (PropertyBool) property)
                        .anyMatch(blockState::getValue)) {
                    requiredBlockOffsets.add(new BlockPos(side.getDirectionVec()));
                }
            }
        }
        schematicBlock.requiredBlockOffsets = requiredBlockOffsets;
        return true;
    }

    private boolean setIgnoredProperties(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block,
            Set<JsonRule> rules,
            SchematicBlock schematicBlock
    ) {
        schematicBlock.ignoredProperties = rules.stream()
                .map(rule -> rule.ignoredProperties)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .flatMap(propertyName ->
                        blockState.getProperties().keySet().stream()
                                .filter(property -> property.getName().equals(propertyName))
                )
                .collect(Collectors.toList());
        return true;
    }

    private boolean setTileNbt(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block,
            Set<JsonRule> rules,
            SchematicBlock schematicBlock
    ) {
        NBTTagCompound tileNbt = null;
        if (block.hasTileEntity(blockState)) {
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity != null) {
                tileNbt = tileEntity.serializeNBT();
            }
        }
        schematicBlock.tileNbt = tileNbt;
        return true;
    }

    private boolean setIgnoredTags(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block,
            Set<JsonRule> rules,
            SchematicBlock schematicBlock
    ) {
        schematicBlock.ignoredTags = rules.stream()
                .map(rule -> rule.ignoredTags)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        return true;
    }

    private boolean setPlaceBlock(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block,
            Set<JsonRule> rules,
            SchematicBlock schematicBlock
    ) {
        schematicBlock.placeBlock = rules.stream()
                .map(rule -> rule.placeBlock)
                .filter(Objects::nonNull)
                .findFirst()
                .map(Block::getBlockFromName)
                .orElse(block);
        return true;
    }

    private boolean setCanBeReplacedWithBlocks(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block,
            Set<JsonRule> rules,
            SchematicBlock schematicBlock
    ) {
        schematicBlock.canBeReplacedWithBlocks =
                Stream.concat(
                        rules.stream()
                                .map(rule -> rule.canBeReplacedWithBlocks)
                                .filter(Objects::nonNull)
                                .flatMap(Collection::stream)
                                .map(Block::getBlockFromName),
                        Stream.of(block, schematicBlock.placeBlock)
                )
                        .collect(Collectors.toCollection(HashSet::new));
        return true;
    }

    private boolean setRequiredItems(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block,
            Set<JsonRule> rules,
            SchematicBlock schematicBlock
    ) {
        List<ItemStack> requiredItems = new ArrayList<>();
        requiredItems.add(
                block.getPickBlock(
                        blockState,
                        null,
                        world,
                        pos,
                        null
                )
        );
        if (rules.stream().anyMatch(rule -> rule.copyRequiredItemsFromDrops)) {
            requiredItems.clear();
            requiredItems.addAll(block.getDrops(
                    world,
                    pos,
                    blockState,
                    0
            ));
        }
        if (rules.stream().noneMatch(rule -> rule.doNotCopyRequiredItemsFromBreakBlockDrops)) {
            if (world instanceof FakeWorld) {
                requiredItems.addAll(((FakeWorld) world).breakBlockAndGetDrops(pos));
            }
        }
        if (rules.stream().filter(rule -> rule.requiredItems != null).count() > 0) {
            requiredItems.clear();
            rules.stream()
                    .filter(rule -> rule.requiredItems != null)
                    .map(rule -> rule.requiredItems)
                    .flatMap(itemNames ->
                            itemNames.stream()
                                    .map(itemName -> itemName.contains("@") ? itemName : itemName + "@0")
                                    .map(itemName ->
                                            new ItemStack(
                                                    Objects.requireNonNull(
                                                            Item.getByNameOrId(
                                                                    itemName.substring(
                                                                            0,
                                                                            itemName.indexOf("@")
                                                                    )
                                                            )
                                                    ),
                                                    1,
                                                    Integer.parseInt(itemName.substring(itemName.indexOf("@") + 1))
                                            )
                                    )
                    )
                    .filter(Objects::nonNull)
                    .forEach(requiredItems::add);
        }
        rules.stream()
                .map(rule -> rule.copyRequiredItemsCountFromProperty)
                .filter(Objects::nonNull)
                .forEach(propertyName ->
                        blockState.getProperties().keySet().stream()
                                .filter(property -> property.getName().equals(propertyName))
                                .map(property -> (PropertyInteger) property)
                                .map(blockState::getValue)
                                .findFirst()
                                .ifPresent(value -> requiredItems.forEach(stack -> stack.setCount(stack.getCount() * value)))
                );
        if (block.hasTileEntity(blockState)) {
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity != null) {
                rules.stream()
                        .map(rule -> rule.copyRequiredItemsFromItemHandlersOnSides)
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .map(EnumFacing::byName)
                        .filter(side -> tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side))
                        .map(side -> tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side))
                        .filter(Objects::nonNull)
                        .flatMap(itemHandler ->
                                IntStream.range(0, itemHandler.getSlots()).mapToObj(itemHandler::getStackInSlot)
                        )
                        .filter(stack -> !stack.isEmpty())
                        .forEach(requiredItems::add);
            }
        }
        requiredItems.removeIf(ItemStack::isEmpty);
        schematicBlock.requiredItems = requiredItems;
        return true;
    }

    private boolean setRequiredFluids(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block,
            Set<JsonRule> rules,
            SchematicBlock schematicBlock
    ) {
        List<Fluid> requiredFluids = new ArrayList<>();
        if (BlockUtil.getFluidWithFlowing(block) != null) {
            if (BlockUtil.getFluid(block) != null) {
                requiredFluids.add(BlockUtil.getFluid(block));
            } else {
                return false;
            }
        }
        schematicBlock.requiredFluids = requiredFluids;
        return true;
    }

    public SchematicBlock getSchematicBlock(
            World world,
            BlockPos basePos,
            BlockPos pos,
            IBlockState blockState,
            Block block
    ) {
        SchematicBlock schematicBlock = new SchematicBlock();
        boolean ignore = false;
        ResourceLocation registryName = block.getRegistryName();
        if (registryName == null) {
            ignore = true;
        }
        if (!ignore) {
            if (!RulesLoader.INSTANCE.readDomains.contains(registryName.getResourceDomain())) {
                ignore = true;
            }
        }
        if (!ignore) {
            Set<JsonRule> rules = getRules(world, basePos, pos, blockState, block);
            if (rules.stream().anyMatch(rule -> rule.ignore) ||
                    !setRequiredBlockOffsets /*   */(world, basePos, pos, blockState, block, rules, schematicBlock) ||
                    !setIgnoredProperties /*      */(world, basePos, pos, blockState, block, rules, schematicBlock) ||
                    !setTileNbt /*                */(world, basePos, pos, blockState, block, rules, schematicBlock) ||
                    !setIgnoredTags /*            */(world, basePos, pos, blockState, block, rules, schematicBlock) ||
                    !setPlaceBlock /*             */(world, basePos, pos, blockState, block, rules, schematicBlock) ||
                    !setCanBeReplacedWithBlocks /**/(world, basePos, pos, blockState, block, rules, schematicBlock) ||
                    !setRequiredItems /*          */(world, basePos, pos, blockState, block, rules, schematicBlock) ||
                    !setRequiredFluids /*         */(world, basePos, pos, blockState, block, rules, schematicBlock)
                    ) {
                ignore = true;
            }
        }
        if (ignore) {
            schematicBlock = getIgnoredSchematicBlock(world, basePos, pos);
        }
        return schematicBlock;
    }

    public SchematicBlock getIgnoredSchematicBlock(World world, BlockPos basePos, BlockPos pos) {
        return getSchematicBlock(world, basePos, pos, Blocks.AIR.getDefaultState(), Blocks.AIR);
    }

    private void computeRequiredForPos(FakeWorld world, Blueprint blueprint, BlockPos pos) {
        BlockPos basePos = FakeWorld.BLUEPRINT_OFFSET;
        SchematicBlock schematicBlock = blueprint.data
                [pos.getX() - basePos.getX()]
                [pos.getY() - basePos.getY()]
                [pos.getZ() - basePos.getZ()];
        IBlockState blockState = world.getBlockState(pos);
        Block block = blockState.getBlock();
        Set<JsonRule> rules = getRules(world, basePos, pos, blockState, block);
        if (!setRequiredItems(world, basePos, pos, blockState, block, rules, schematicBlock) ||
                !setRequiredFluids(world, basePos, pos, blockState, block, rules, schematicBlock)) {
            schematicBlock.requiredItems = null;
            schematicBlock.requiredFluids = null;
        }
    }

    public void computeRequired(Blueprint blueprint) {
        FakeWorld world = new FakeWorld();
        for (int z = 0; z < blueprint.size.getZ(); z++) {
            for (int y = 0; y < blueprint.size.getY(); y++) {
                for (int x = 0; x < blueprint.size.getX(); x++) {
                    world.uploadBlueprint(blueprint);
                    computeRequiredForPos(world, blueprint, new BlockPos(x, y, z).add(FakeWorld.BLUEPRINT_OFFSET));
                    world.clear();
                }
            }
        }
    }
}
