package thebetweenlands.common.block.container;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import thebetweenlands.api.block.IDungeonFogBlock;
import thebetweenlands.client.tab.BLCreativeTabs;
import thebetweenlands.common.TheBetweenlands;
import thebetweenlands.common.block.BasicBlock;
import thebetweenlands.common.inventory.container.ContainerCenser;
import thebetweenlands.common.proxy.CommonProxy;
import thebetweenlands.common.tile.TileEntityCenser;

public class BlockCenser extends BasicBlock implements ITileEntityProvider, IDungeonFogBlock {
	public static final PropertyDirection FACING = BlockHorizontal.FACING;

	public BlockCenser() {
		super(Material.ROCK);
		setHardness(2.0F);
		setResistance(5.0F);
		setCreativeTab(BLCreativeTabs.BLOCKS);
		setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
	}

	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
		return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
		world.setBlockState(pos, state.withProperty(FACING, placer.getHorizontalFacing()), 2);
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,  EnumFacing side, float hitX, float hitY, float hitZ) {
		ItemStack heldItem = player.getHeldItem(hand);

		if (world.isRemote) {
			return true;
		}

		if (world.getTileEntity(pos) instanceof TileEntityCenser) {
			TileEntityCenser tile = (TileEntityCenser) world.getTileEntity(pos);

			if (player.isSneaking()) {
				return false;
			}

			if (!heldItem.isEmpty()) {
				IFluidHandler handler = heldItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
				if(handler != null) {
					IItemHandler playerInventory = player.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
					if (playerInventory != null) {
						FluidActionResult fluidActionResult = FluidUtil.tryEmptyContainerAndStow(heldItem, tile, playerInventory, Integer.MAX_VALUE, player, true);

						if (fluidActionResult.isSuccess()) {
							player.setHeldItem(hand, fluidActionResult.getResult());
							return true;
						}
					}
				}
			}

			if (tile != null) {
				player.openGui(TheBetweenlands.instance, CommonProxy.GUI_CENSER, world, pos.getX(), pos.getY(), pos.getZ());
			}
		}
		return true;
	}

	@Override
	public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
		TileEntity tileEntity = worldIn.getTileEntity(pos);

		if (tileEntity instanceof IInventory) {
			IInventory inventory = (IInventory) tileEntity;
			if(ContainerCenser.SLOT_INTERNAL < inventory.getSizeInventory()) {
				//Destroy contents of internal slot since they shouldn't be dropped
				inventory.setInventorySlotContents(ContainerCenser.SLOT_INTERNAL, ItemStack.EMPTY);
			}
			InventoryHelper.dropInventoryItems(worldIn, pos, inventory);
			worldIn.updateComparatorOutputLevel(pos, this);
		}

		super.breakBlock(worldIn, pos, state);
	}

	@Override
	public EnumBlockRenderType getRenderType(IBlockState state) {
		return EnumBlockRenderType.MODEL;
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		EnumFacing facing = EnumFacing.byIndex(meta);

		if (facing.getAxis() == EnumFacing.Axis.Y) {
			facing = EnumFacing.NORTH;
		}

		return this.getDefaultState().withProperty(FACING, facing);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public BlockRenderLayer getRenderLayer() {
		return BlockRenderLayer.CUTOUT;
	}

	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return state.getValue(FACING).getIndex();
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityCenser();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean shouldSideBeRendered(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		return true;
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, FACING);
	}

	@Override
	public boolean isCreatingDungeonFog(IBlockAccess world, BlockPos pos, IBlockState state) {
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof TileEntityCenser) {
			return ((TileEntityCenser) te).getDungeonFogStrength(1) >= 0.1F;
		}
		return false;
	}
}