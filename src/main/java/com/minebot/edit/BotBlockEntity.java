package com.minebot.edit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.minebot.MineBot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The mining machine. Server-side logic only: mines blocks in a chosen region,
 * auto-swaps tools, keeps drops in its own inventory, and absorbs nearby item drops.
 */
public class BotBlockEntity extends BlockEntity {
	public static final int TOOLS = 4;
	public static final int INV_SLOTS = 15;
	public static final int CORNER_LIMIT = 24;
	public static final int SCAN_RANGE = 3;

	/** tool slot purposes: 0=pickaxe 1=axe 2=shovel 3=hoe */
	public static final int SLOT_PICK = 0;
	public static final int SLOT_AXE = 1;
	public static final int SLOT_SHOVEL = 2;
	public static final int SLOT_HOE = 3;

	private final NonNullList<ItemStack> tools = NonNullList.withSize(TOOLS, ItemStack.EMPTY);
	private final NonNullList<ItemStack> inventory = NonNullList.withSize(INV_SLOTS, ItemStack.EMPTY);

	private int c1x, c1y, c1z;
	private int c2x, c2y, c2z;
	private boolean c1Set, c2Set;
	private boolean running;

	private int curX, curY, curZ;
	private boolean cursorInit;
	private boolean exhausted;

	private int tickCounter;
	private int statsTick;
	private final LinkedHashMap<String, Integer> stats = new LinkedHashMap<>();
	private String statsCache = "";

	public BotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// ---------- getters for networking ----------

	public boolean isRunning() { return running; }
	public boolean corner1Set() { return c1Set; }
	public boolean corner2Set() { return c2Set; }
	public int c1x() { return c1x; }
	public int c1y() { return c1y; }
	public int c1z() { return c1z; }
	public int c2x() { return c2x; }
	public int c2y() { return c2y; }
	public int c2z() { return c2z; }
	public NonNullList<ItemStack> toolsView() { return tools; }
	public NonNullList<ItemStack> inventoryView() { return inventory; }

	public int toolMask() {
		int mask = 0;
		for (int i = 0; i < TOOLS; i++) {
			if (!tools.get(i).isEmpty()) {
				mask |= (1 << i);
			}
		}
		return mask;
	}

	public String toolsDesc() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < TOOLS; i++) {
			ItemStack st = tools.get(i);
			if (sb.length() > 0) {
				sb.append(';');
			}
			if (st.isEmpty()) {
				sb.append('-');
			} else {
				sb.append(BuiltInRegistries.ITEM.getKey(st.getItem()))
					.append('|').append(st.getDamageValue()).append('|').append(st.getMaxDamage());
			}
		}
		return sb.toString();
	}

	public String invDesc() {
		StringBuilder sb = new StringBuilder();
		for (ItemStack st : inventory) {
			if (sb.length() > 0) {
				sb.append(';');
			}
			if (st.isEmpty()) {
				sb.append('-');
			} else {
				sb.append(BuiltInRegistries.ITEM.getKey(st.getItem()))
					.append('|').append(st.getCount());
			}
		}
		return sb.toString();
	}

	public String statsDesc() {
		return statsCache;
	}

	// ---------- server logic ----------

	public void serverTick(Level level) {
		if (!running || level.isClientSide()) {
			return;
		}
		// absorb nearby item drops once a second
		if (tickCounter % 20 == 0) {
			absorbNearby(level);
		}
		tickCounter++;
		if (tickCounter < interval()) {
			return;
		}
		tickCounter = 0;
		ServerLevel sl = (ServerLevel) level;
		mineOnce(sl);
		// keep stats serializable text fresh
		if (++statsTick >= 20 && !stats.isEmpty()) {
			statsTick = 0;
			statsCache = writeStats();
		}
	}

	private int interval() {
		ItemStack tool = currentTool();
		if (tool == null) {
			return 100; // no fitting tool: 5s per block
		}
		int max = tool.getMaxDamage();
		if (max >= 1500) {
			return 20; // diamond / netherite: 1s
		}
		if (max >= 250) {
			return 40; // iron: 2s
		}
		if (max >= 120) {
			return 60; // stone: 3s
		}
		return 80; // wood / gold: 4s
	}

	private ItemStack currentTool() {
		BlockPos cur = new BlockPos(curX, curY, curZ);
		BlockState st = level.getBlockState(cur);
		int slot = slotForState(st);
		return slot >= 0 ? tools.get(slot) : null;
	}

	public static int slotForState(BlockState st) {
		if (st.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return SLOT_PICK;
		}
		if (st.is(BlockTags.MINEABLE_WITH_AXE)) {
			return SLOT_AXE;
		}
		if (st.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return SLOT_SHOVEL;
		}
		if (st.is(BlockTags.MINEABLE_WITH_HOE)) {
			return SLOT_HOE;
		}
		return -1;
	}

	/** Mines exactly one block if possible. Exposed for the headless self-test. */
	public boolean mineOnce(ServerLevel level) {
		if (!c1Set || !c2Set) {
			return false;
		}
		if (!findNext(level)) {
			if (!exhausted) {
				exhausted = true;
				running = false;
				setChanged();
				MineBot.LOGGER.info("[MineBot] region finished at {}", getBlockPos().toShortString());
			}
			return false;
		}
		if (inventoryFull()) {
			return false; // wait for space
		}
		BlockPos target = new BlockPos(curX, curY, curZ);
		BlockState st = level.getBlockState(target);
		if (st.isAir() || !level.isLoaded(target)
				|| st.getDestroySpeed(level, target) < 0
				|| level.getBlockEntity(target) != null) {
			return false;
		}
		int slot = slotForState(st);
		ItemStack tool = (slot >= 0) ? tools.get(slot) : ItemStack.EMPTY;
		BlockEntity be = st.hasBlockEntity() ? level.getBlockEntity(target) : null;
		List<ItemStack> drops = Block.getDrops(st, level, target, be, null,
			tool.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : tool);
		level.removeBlock(target, false);
		if (!tool.isEmpty()) {
			hurtTool(slot, tool);
		}
		absorbDrops(level, target, drops);
		String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
		stats.merge(id, 1, Integer::sum);
		if (stats.size() > 30) {
			stats.remove(stats.keySet().iterator().next());
		}
		setChanged();
		return true;
	}

	private void hurtTool(int slot, ItemStack tool) {
		tool.setDamageValue(tool.getDamageValue() + 1);
		if (tool.getDamageValue() >= tool.getMaxDamage()) {
			tools.set(slot, ItemStack.EMPTY);
			MineBot.LOGGER.info("[MineBot] tool broke in slot {}", slot);
		}
	}

	/** Advances the cursor to the next mineable block. Returns false when region exhausted. */
	private boolean findNext(Level level) {
		BlockPos min = new BlockPos(Math.min(c1x, c2x), Math.min(c1y, c2y), Math.min(c1z, c2z));
		BlockPos max = new BlockPos(Math.max(c1x, c2x), Math.max(c1y, c2y), Math.max(c1z, c2z));
		if (!cursorInit) {
			curX = min.getX();
			curY = min.getY();
			curZ = min.getZ();
			cursorInit = true;
		}
		int budget = 600;
		while (budget-- > 0) {
			BlockState st = level.getBlockState(new BlockPos(curX, curY, curZ));
			if (!st.isAir()) {
				return true;
			}
			curX++;
			if (curX > max.getX()) {
				curX = min.getX();
				curY++;
				if (curY > max.getY()) {
					curY = min.getY();
					curZ++;
					if (curZ > max.getZ()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private boolean inventoryFull() {
		for (ItemStack st : inventory) {
			if (st.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void absorbDrops(Level level, BlockPos target, List<ItemStack> drops) {
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			ItemStack leftover = insertToInv(drop);
			if (leftover != null && !leftover.isEmpty()) {
				Block.popResource(level, target, leftover);
			}
		}
	}

	/** Returns leftover when the inventory could not fully accept the stack. */
	private ItemStack insertToInv(ItemStack src) {
		int remaining = src.getCount();
		int maxStack = src.getMaxStackSize();
		// merge into matching slots
		for (int i = 0; i < INV_SLOTS && remaining > 0; i++) {
			ItemStack slot = inventory.get(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, src)) {
				int room = maxStack - slot.getCount();
				if (room > 0) {
					int add = Math.min(room, remaining);
					slot.grow(add);
					remaining -= add;
				}
			}
		}
		// place into empty slots
		for (int i = 0; i < INV_SLOTS && remaining > 0; i++) {
			if (inventory.get(i).isEmpty()) {
				int add = Math.min(maxStack, remaining);
				remaining -= add;
				ItemStack put = src.copyWithCount(add);
				inventory.set(i, put);
			}
		}
		setChanged();
		if (remaining == src.getCount()) {
			return src; // nothing fit
		}
		if (remaining > 0) {
			return src.copyWithCount(remaining);
		}
		return ItemStack.EMPTY;
	}

	private void absorbNearby(Level level) {
		BlockPos pos = getBlockPos();
		AABB box = new AABB(pos).inflate(SCAN_RANGE, SCAN_RANGE, SCAN_RANGE);
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive());
		for (ItemEntity item : items) {
			ItemStack stack = item.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack leftover = insertToInv(stack);
			if (leftover.isEmpty()) {
				item.discard();
			} else if (leftover.getCount() != stack.getCount()) {
				stack.setCount(leftover.getCount());
			}
		}
	}

	// ---------- commands from the network layer ----------

	public boolean setCorner(int which, BlockPos pos) {
		if (which == 1) {
			c1x = pos.getX();
			c1y = pos.getY();
			c1z = pos.getZ();
			c1Set = true;
		} else {
			c2x = pos.getX();
			c2y = pos.getY();
			c2z = pos.getZ();
			c2Set = true;
		}
		resetCursor();
		setChanged();
		return true;
	}

	public void resetCursor() {
		cursorInit = false;
		exhausted = false;
	}

	public boolean start(boolean on) {
		if (on && (!c1Set || !c2Set)) {
			return false;
		}
		if (on) {
			int minX = Math.min(c1x, c2x), maxX = Math.max(c1x, c2x);
			int minY = Math.min(c1y, c2y), maxY = Math.max(c1y, c2y);
			int minZ = Math.min(c1z, c2z), maxZ = Math.max(c1z, c2z);
			if (maxX - minX + 1 > CORNER_LIMIT || maxY - minY + 1 > CORNER_LIMIT
					|| maxZ - minZ + 1 > CORNER_LIMIT) {
				return false;
			}
		}
		running = on;
		if (on) {
			resetCursor();
		}
		setChanged();
		return true;
	}

	public boolean giveTool(int slot, ItemStack held) {
		if (slot < 0 || slot >= TOOLS) {
			return false;
		}
		if (held.isEmpty()) {
			return false;
		}
		// return the old tool of that slot to nothing (inventory NOT full assumed)
		tools.set(slot, held.copyWithCount(1));
		setChanged();
		return true;
	}

	public List<ItemStack> takeAllTools() {
		List<ItemStack> taken = new ArrayList<>();
		for (int i = 0; i < TOOLS; i++) {
			ItemStack st = tools.get(i);
			if (!st.isEmpty()) {
				taken.add(st);
				tools.set(i, ItemStack.EMPTY);
			}
		}
		setChanged();
		return taken;
	}

	public int collectAll(List<ItemStack> outAdded) {
		int moved = 0;
		for (int i = 0; i < INV_SLOTS; i++) {
			ItemStack st = inventory.get(i);
			if (!st.isEmpty()) {
				outAdded.add(st);
				inventory.set(i, ItemStack.EMPTY);
				moved++;
			}
		}
		setChanged();
		return moved;
	}

	/** Trading: callers add stacks via insertToInv. */
	public ItemStack insertExternal(ItemStack src) {
		return insertToInv(src);
	}

	private String writeStats() {
		StringBuilder sb = new StringBuilder();
		int n = 0;
		for (Map.Entry<String, Integer> e : stats.entrySet()) {
			if (n >= 12) {
				break;
			}
			if (sb.length() > 0) {
				sb.append(';');
			}
			sb.append(e.getKey()).append('|').append(e.getValue());
			n++;
		}
		return sb.toString();
	}

	// ---------- persistence ----------

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		net.minecraft.world.ContainerHelper.saveAllItems(out.child("tools"), tools);
		net.minecraft.world.ContainerHelper.saveAllItems(out.child("inv"), inventory);
		out.putBoolean("running", running);
		out.putInt("c1x", c1x);
		out.putInt("c1y", c1y);
		out.putInt("c1z", c1z);
		out.putBoolean("c1s", c1Set);
		out.putInt("c2x", c2x);
		out.putInt("c2y", c2y);
		out.putInt("c2z", c2z);
		out.putBoolean("c2s", c2Set);
		out.putString("stats", statsCache);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		net.minecraft.world.ContainerHelper.loadAllItems(in.childOrEmpty("tools"), tools);
		net.minecraft.world.ContainerHelper.loadAllItems(in.childOrEmpty("inv"), inventory);
		running = in.getBooleanOr("running", false);
		c1x = in.getIntOr("c1x", 0);
		c1y = in.getIntOr("c1y", 0);
		c1z = in.getIntOr("c1z", 0);
		c1Set = in.getBooleanOr("c1s", false);
		c2x = in.getIntOr("c2x", 0);
		c2y = in.getIntOr("c2y", 0);
		c2z = in.getIntOr("c2z", 0);
		c2Set = in.getBooleanOr("c2s", false);
		statsCache = in.getString("stats").orElse("");
	}

}
