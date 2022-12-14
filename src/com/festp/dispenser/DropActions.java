package com.festp.dispenser;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.festp.Main;
import com.festp.utils.UtilsType;
import com.festp.utils.Vector3i;
import com.festp.dispenser.PumpManager.PumpState;
import com.festp.dispenser.PumpManager.PumpType;

// TODO REFACTOR
public class DropActions implements Listener
{
	Main pl;

	int maxDy = 50;
	int maxDxz = 50; // TODO: configurable pump area
	int minDxz = -maxDxz;
	int pumpArea = maxDxz * 2 + 1;
	
	// dispensers to pump the water
	List<Dispenser> dispsPump = new ArrayList<>();
	List<Block> pumpingBlocks = new ArrayList<>();
	
	public DropActions(Main plugin) {
		this.pl = plugin;
	}
	
	public void onTick() {
		for (int i = dispsPump.size()-1; i >= 0; i--) {
			dispenserPump(dispsPump.get(i));
			dispsPump.remove(i);
		}
		pumpingBlocks.clear();
	}
	
	@EventHandler
	public void onBlockDispense(BlockDispenseEvent event)
	{
		if (event.getItem() == null || event.getBlock().getType() != Material.DISPENSER)
			return;
		
		Dispenser dispenser = (Dispenser)event.getBlock().getState();
		PumpState pr = PumpManager.test(dispenser, event.getItem());
		if (pr == PumpState.READY) {
			dispsPump.add(dispenser);
			event.setCancelled(true);
		}
		else if (pr == PumpState.MODULE) {
			event.setCancelled(true);
		}
	}
	
	public void dispenserCauldron() {
		
	}
	
	public void dispenserPump(Dispenser d) {
		Inventory inv = d.getInventory();
		PumpType pump_type = PumpType.NONE;
		int bucket_index = -1, module_index = -1;
		int pipe_index = -1, null_index = -1, multybucket_index = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack is;
			is = inv.getItem(i);
			if (is != null)
			{
				if (module_index < 0 && is.getType() == Material.BLAZE_ROD
						&& is.hasItemMeta() && is.getItemMeta().hasLore()) {
					if (PumpManager.isWeakPump(is)) {
						module_index = i;
						pump_type = PumpType.REGULAR;
						if (bucket_index >= 0) break;
					}
					else if (PumpManager.isStrongPump(is)) {
						module_index = i;
						pump_type = PumpType.ADVANCED;
						if (bucket_index >= 0) break;
					}
				}
				else if ( is.getType() == Material.BUCKET ) {
					if (is.getEnchantmentLevel(PumpManager.bottomless_bucket_metaench) > 0) // TODO
						bucket_index = 9;
					else if (is.getAmount() == 1 && bucket_index < 0)
						bucket_index = i;
					else {
						if (multybucket_index < 0)
							multybucket_index = i;
						if (null_index < 0) continue;
					}
					if (module_index >= 0) break;
				}
				else if (is.getType() == Material.NETHER_BRICK_FENCE)
					pipe_index = i;
			}
			else if (null_index < 0) null_index = i;
		}
		if (module_index >= 0 && ( bucket_index >= 0 || pipe_index >= 0 || (null_index >= 0 && multybucket_index >= 0)) ) {
			if (pump_type == PumpType.REGULAR)
				work_regularPump(d, bucket_index, null_index, multybucket_index);
			else if (pump_type == PumpType.ADVANCED)
				work_advancedPump(d, bucket_index, null_index, multybucket_index);
		}
	}
	
	private static Block getActionBlock(Dispenser dispenser)
	{
		Directional directonal = (Directional)dispenser.getBlockData();
		BlockFace face = directonal.getFacing();
		int dx = (face == BlockFace.WEST ? -1 : (face == BlockFace.EAST ? 1 : 0));
		int dy = (face == BlockFace.DOWN ? -1 : (face == BlockFace.UP ? 1 : 0));
		int dz = (face == BlockFace.NORTH ? -1 : (face == BlockFace.SOUTH ? 1 : 0));
		int x = dispenser.getX(), y = dispenser.getY(), z = dispenser.getZ();
		return dispenser.getWorld().getBlockAt(x + dx, y + dy, z + dz);
	}
	
	public void work_regularPump(Dispenser d, int bucket_index, int null_index, int multybucket_index) {
		Inventory inv = d.getInventory();
		Block block = getActionBlock(d);
		int dy = block.getY() - d.getY();
		Block block_to_pump = null;
		// can place pipe
		if (dy <= 0) {
			int pipes = 0;
			//scroll all placed pipe blocks
			while (block.getType() == Material.NETHER_BRICK_FENCE) {
				block = block.getRelative(0, -1, 0);
				pipes += 1;
			}
			int pipe_index = -1;
			Block test_liquid = block;
			//test available liquid below pipe
			while (UtilsType.isAir(test_liquid.getType()) || UtilsType.isFlowingLiquid(test_liquid) || test_liquid.getType() == Material.NETHER_BRICK_FENCE
					 || UtilsType.isSlab(test_liquid.getType())) {
				if (test_liquid.isLiquid()) {
					block_to_pump = findBlockToPump_regular(test_liquid);
					if (block_to_pump == null)
						test_liquid = test_liquid.getRelative(0, -1, 0);
					else break;
				}
				test_liquid = test_liquid.getRelative(0, -1, 0);
			}
			//remove fences
			if (findBlockToPump_regular(test_liquid) == null) {
				if (pipes > 0) {
					for (int i = 0; i < 9; i++) {
						ItemStack is;
						is = inv.getItem(i);
						if (is != null && is.getType() == Material.NETHER_BRICK_FENCE && is.getAmount() < 64)
						{
							pipe_index = i;
							is.setAmount(is.getAmount()+1);
							block.getRelative(0, 1, 0).setType(Material.AIR);
							break;
						}
					}
					if (pipe_index < 0 && null_index >= 0) {
						inv.setItem(null_index, new ItemStack(Material.NETHER_BRICK_FENCE, 1));
						block.getRelative(0, 1, 0).setType(Material.AIR);
					}
				}
				return;
			}
			//place fences
			block_to_pump = findBlockToPump_regular(block);
			if (block.isEmpty() || (block.isLiquid() && block_to_pump == null)) {
				for (int i = 0; i < 9; i++) {
					ItemStack is;
					is = inv.getItem(i);
					if(is != null && is.getType() == Material.NETHER_BRICK_FENCE)
					{
						pipe_index = i;
						break;
					}
				}
				if (pipe_index >= 0) {
					ItemStack pipe = inv.getItem(pipe_index);
					pipe.setAmount(pipe.getAmount() - 1);
					block.setType(Material.NETHER_BRICK_FENCE);
					block = block.getRelative(0, -1, 0);
				}
				return;
			}
		}
		if ( bucket_index < 0 && ( multybucket_index < 0 || null_index < 0 ) )
			return;
		
		//pump
		if (block_to_pump == null)
			block_to_pump = findBlockToPump_regular(block);
		if (block_to_pump != null)
		{
			pumpingBlocks.add(block_to_pump);
			Material pumped = pump(block_to_pump);
			if (bucket_index < 9) {
				if (bucket_index < 0) {
					inv.getItem(multybucket_index).setAmount(inv.getItem(multybucket_index).getAmount()-1);
					bucket_index = null_index;
				}
				if (pumped == Material.LAVA)
					inv.setItem(bucket_index, new ItemStack(Material.LAVA_BUCKET));
				else if (pumped == Material.WATER)
					inv.setItem(bucket_index, new ItemStack(Material.WATER_BUCKET));
			}
		}
	}
	
	public void work_advancedPump(Dispenser d, int bucket_index, int null_index, int multybucket_index) {
		//  _.._        ____          ____
		// |    |      |    |        |    |
		// |____|      |    = ll     |_.._|
		//             |____| ll       ll
		//
		//   up       horizontal      down
		//
		// '..' or '=' is the dispenser hole, 'll' is pipe block
		Block block = getActionBlock(d);
		int dy = block.getY() - d.getY();
		Block block_to_pump = null;
		Inventory inv = d.getInventory();
		if (dy <= 0)
		{
			int pipes = 0;
			while (block.getType() == Material.NETHER_BRICK_FENCE) {
				block = block.getRelative(0, -1, 0);
				pipes += 1;
			}
			int pipe_index = -1;
			Block test_liquid = block;
			while (UtilsType.isAir(test_liquid.getType()) || test_liquid.getType() == Material.NETHER_BRICK_FENCE)
				test_liquid = test_liquid.getRelative(0, -1, 0);
			
			//remove fences
			block_to_pump = findBlockToPump_advanced(test_liquid);
			if (block_to_pump == null) {
				if (pipes > 0) {
					//find slot to add 1 fence
					for (int i = 0; i < 9; i++) {
						ItemStack is;
						is = inv.getItem(i);
						if (is != null && is.getType() == Material.NETHER_BRICK_FENCE && is.getAmount() < 64)
						{
							pipe_index = i;
							is.setAmount(is.getAmount()+1);
							block.getRelative(0, 1, 0).setType(Material.AIR);
							break;
						}
					}
					if (pipe_index < 0 && null_index >= 0) {
						inv.setItem(null_index, new ItemStack(Material.NETHER_BRICK_FENCE, 1));
						block.getRelative(0, 1, 0).setType(Material.AIR);
					}
				}
				return;
			}
			//place fences
			block_to_pump = findBlockToPump_advanced(block);
			if (block.isEmpty() || (block.isLiquid() && block_to_pump == null)) {
				//find slot to remove 1 fence
				for (int i = 0; i < 9; i++) {
					ItemStack is;
					is = inv.getItem(i);
					if (is != null && is.getType() == Material.NETHER_BRICK_FENCE)
					{
						pipe_index = i;
						break;
					}
				}
				if (pipe_index >= 0) {
					ItemStack pipe = inv.getItem(pipe_index);
					pipe.setAmount(pipe.getAmount() - 1);
					block.setType(Material.NETHER_BRICK_FENCE);
					block = block.getRelative(0, -1, 0);
				}
				return;
			}
		}
		if( bucket_index < 0 && ( multybucket_index < 0 || null_index < 0 ) )
			return;
		
		//pump
		if (block_to_pump == null)
			block_to_pump = findBlockToPump_advanced(block);

		if (block_to_pump != null)
		{
			pumpingBlocks.add(block_to_pump);
			Material pumped = pump(block_to_pump);
			if (pumped == null)
				return;
			
			if (bucket_index < 9) {
				if (bucket_index < 0) {
					inv.getItem(multybucket_index).setAmount(inv.getItem(multybucket_index).getAmount() - 1);
					bucket_index = null_index;
				}
				if (pumped == Material.LAVA)
					inv.setItem(bucket_index, new ItemStack(Material.LAVA_BUCKET));
				else if (pumped == Material.WATER)
					inv.setItem(bucket_index, new ItemStack(Material.WATER_BUCKET));
			}
		}
	}

	public Block findBlockToPump_advanced(Block block)
	{
		Block max_dist_block = null;
		if (continuePump(block))
		{
			int top_layer_dy = 0;
			List<LayerSet> layers = new ArrayList<>();
			LayerSet top_layer = new LayerSet(maxDxz);
			layers.add(top_layer);
			
			int dist = 0;
			List<Vector3i> unchecked = new ArrayList<>();
			List<Vector3i> next_unchecked = new ArrayList<>();
			next_unchecked.add(new Vector3i(0, 0, 0));
			while (next_unchecked.size() > 0)
			{
				dist++;
				unchecked = next_unchecked;
				next_unchecked = new ArrayList<>();
				for (Vector3i loc : unchecked)
				{
					int dx = loc.getX(), dy = loc.getY(), dz = loc.getZ();
					Block b = block.getRelative(dx, dy, dz);
					
					if (dy >= layers.size())
					{
						top_layer = new LayerSet(maxDxz);
						layers.add(top_layer);
						top_layer_dy++;
					}
					
					LayerSet cur_layer = layers.get(dy);
					
					if (canPump(b))
					{
						if (!cur_layer.isDefinedFarthest() || dist > cur_layer.max_distance)
						{
							cur_layer.farthest[0] = dx;
							cur_layer.farthest[1] = dz;
							cur_layer.max_distance = dist;
						}
					}
					
					cur_layer.setDistance(dx, dz, dist);
					
					Block rel;
					if (dy < maxDy) {
						rel = b.getRelative(0, 1, 0);
						if (continuePump(rel))
							if (dy >= top_layer_dy)
								next_unchecked.add(new Vector3i(dx, dy + 1, dz));
							else if (layers.get(dy + 1).isUnchecked(dx, dz)) {
								next_unchecked.add(new Vector3i(dx, dy + 1, dz));
								layers.get(dy + 1).setNext(dx, dz);
							}
					}
					if (dx < maxDxz) {
						rel = b.getRelative(1, 0, 0);
						if (continuePump(rel))
							if (cur_layer.isUnchecked(dx + 1, dz)) {
								next_unchecked.add(new Vector3i(dx + 1, dy, dz));
								cur_layer.setNext(dx + 1, dz);
							}
					}
					if (minDxz < dx) {
						rel = b.getRelative(-1, 0, 0);
						if (continuePump(rel))
							if (cur_layer.isUnchecked(dx - 1, dz)) {
								next_unchecked.add(new Vector3i(dx - 1, dy, dz));
								cur_layer.setNext(dx - 1, dz);
							}
					}
					if (dz < maxDxz) {
						rel = b.getRelative(0, 0, 1);
						if (continuePump(rel))
							if (cur_layer.isUnchecked(dx, dz + 1)) {
								next_unchecked.add(new Vector3i(dx, dy, dz + 1));
								cur_layer.setNext(dx, dz + 1);
							}
					}
					if (minDxz < dz) {
						rel = b.getRelative(0, 0, -1);
						if (continuePump(rel))
							if (cur_layer.isUnchecked(dx, dz - 1)) {
								next_unchecked.add(new Vector3i(dx, dy, dz - 1));
								cur_layer.setNext(dx, dz - 1);
							}
					}
				}
			}
			
			for (int dy = layers.size() - 1; dy >= 0; dy--) {
				LayerSet layer = layers.get(dy);
				if (layer.isDefinedFarthest())
				{
					int dx = layer.farthest[0];
					int dz = layer.farthest[1];
					max_dist_block = block.getRelative(dx, dy, dz);
					break;
				}
			}
			/*for (dy = 0; dy < layers.size(); dy++)
			{
				LayerSet layer = layers.get(dy);
				layer.print_scale(block.getY() + dy);
			}*/
		}
		return max_dist_block;
	}
	

	public Block findBlockToPump_regular(Block block)
	{
		int max_distance = 0;
		Block max_dist_block = null;
		if (!continuePump(block))
			return null;
		
		if (canPump(block.getRelative(2, 0, 0)) && continuePump(block.getRelative(1, 0, 0))) {
			max_dist_block = block.getRelative(2, 0, 0);
			max_distance = 3;
		} else if (max_distance < 1 && canPump(block.getRelative(1, 0, 0))) {
			max_dist_block = block.getRelative(1, 0, 0);
			max_distance = 1;
		}
		if (canPump(block.getRelative(0, 0, 2)) && continuePump(block.getRelative(0, 0, 1))) {
			max_dist_block = block.getRelative(0, 0, 2);
			max_distance = 3;
		} else if (max_distance < 1 && canPump(block.getRelative(0, 0, 1))) {
			max_dist_block = block.getRelative(0, 0, 1);
			max_distance = 1;
		}
		if (canPump(block.getRelative(-2, 0, 0)) && continuePump(block.getRelative(-1, 0, 0))) {
			max_dist_block = block.getRelative(-2, 0, 0);
			max_distance = 3;
		} else if (max_distance < 1 && canPump(block.getRelative(-1, 0, 0))) {
			max_dist_block = block.getRelative(-1, 0, 0);
			max_distance = 1;
		}
		if (canPump(block.getRelative(0, 0, -2)) && continuePump(block.getRelative(0, 0, -1))) {
			max_dist_block = block.getRelative(0, 0, -2);
			max_distance = 3;
		} else if (max_distance < 1 && canPump(block.getRelative(0, 0, -1))) {
			max_dist_block = block.getRelative(0, 0, -1);
			max_distance = 1;
		}
		if (max_distance < 2 && canPump(block.getRelative(1, 0, 1)) && (continuePump(block.getRelative(1, 0, 0)) || continuePump(block.getRelative(0, 0, 1))))
			max_dist_block = block.getRelative(1, 0, 1);
		else if (max_distance < 2 && canPump(block.getRelative(-1, 0, 1)) && (continuePump(block.getRelative(-1, 0, 0)) || continuePump(block.getRelative(0, 0, 1)))) 
			max_dist_block = block.getRelative(-1, 0, 1);
		else if (max_distance < 2 && canPump(block.getRelative(-1, 0, -1)) && (continuePump(block.getRelative(-1, 0, 0)) || continuePump(block.getRelative(0, 0, -1)))) 
			max_dist_block = block.getRelative(-1, 0, -1);
		else if (max_distance < 2 && canPump(block.getRelative(1, 0, -1)) && (continuePump(block.getRelative(1, 0, 0)) || continuePump(block.getRelative(0, 0, -1)))) 
			max_dist_block = block.getRelative(1, 0, -1);
		if (max_distance == 0 && canPump(block)) {
			max_dist_block = block;
		}
		return max_dist_block;
	}
	
	static boolean continuePump(Block b)
	{
		if (b.getY() >= b.getWorld().getMaxHeight())
			return false;
		if (isPumpable(b))
			return true;
		if (b.isLiquid())
			return true;
		return false;
	}
	
	static boolean isPumpable(Block b)
	{
		if (UtilsType.isStationaryLiquid(b))
			return true;
		BlockData block_data = b.getBlockData();
		Material material = b.getType();
		if (block_data instanceof Waterlogged)
			return ((Waterlogged) block_data).isWaterlogged();
		if (UtilsType.isWaterPlant(material))
			return true;
		return false;
	}
	
	boolean canPump(Block b)
	{
		if (pumpingBlocks.contains(b))
			return false;
		return isPumpable(b);
	}
	
	/** @return Liquid material: Material.LAVA or Material.WATER */
	static Material pump(Block b)
	{
		Material material = b.getType();
		if (material == Material.BUBBLE_COLUMN) {
			material = Material.WATER;
		}
		
		if (material == Material.LAVA || material == Material.WATER)
		{
			b.setType(Material.AIR);
			
			return material;
		}
		
		BlockData data = b.getBlockData();
		if (data instanceof Waterlogged)
		{
			Waterlogged waterlogged = (Waterlogged) data;
			if (waterlogged.isWaterlogged())
			{
				waterlogged.setWaterlogged(false);
				b.setBlockData(waterlogged);
				b.getState().update(); // destroy lily pads and etc
				return Material.WATER;
			}
		}
		else if (UtilsType.isWaterPlant(material))
		{
			b.breakNaturally();
			return Material.WATER;
		}
		
		return null;
	}
}
