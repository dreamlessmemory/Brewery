package com.dreamless.brewery;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.Bisected;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;


import org.apache.commons.lang.ArrayUtils;

public class Barrel implements InventoryHolder {

	public static CopyOnWriteArrayList<Barrel> barrels = new CopyOnWriteArrayList<Barrel>();
	private static int check = 0;
	
	//Difficulty adjustments
	public static double minutesPerYear = 20.0; 
	
	private Block spigot;
	private int[] woodsloc = null; // location of wood Blocks
	private int[] stairsloc = null; // location of stair Blocks
	private byte signoffset;
	private boolean checked;
	private Inventory inventory;
	private float time;

	public Barrel(Block spigot, byte signoffset) {
		this.spigot = spigot;
		this.signoffset = signoffset;
	}

	// load from file
	public Barrel(Block spigot, byte sign, String[] st, String[] wo, Map<String, Object> items, float time) {
		this.spigot = spigot;
		this.signoffset = sign;
		if (isLarge()) {
			this.inventory = org.bukkit.Bukkit.createInventory(this, 27, P.p.languageReader.get("Etc_Barrel"));
		} else {
			this.inventory = org.bukkit.Bukkit.createInventory(this, 9, P.p.languageReader.get("Etc_Barrel"));
		}
		if (items != null) {
			for (String slot : items.keySet()) {
				if (items.get(slot) instanceof ItemStack) {
					this.inventory.setItem(P.p.parseInt(slot), (ItemStack) items.get(slot));
				}
			}
		}
		this.time = time;

		int i = 0;
		if (wo.length > 1) {
			woodsloc = new int[wo.length];
			for (String wos : wo) {
				woodsloc[i] = P.p.parseInt(wos);
				i++;
			}
			i = 0;
		}
		if (st.length > 1) {
			stairsloc = new int[st.length];
			for (String sts : st) {
				stairsloc[i] = P.p.parseInt(sts);
				i++;
			}
		}

		if (woodsloc == null && stairsloc == null) {
			Block broken = getBrokenBlock(true);
			if (broken != null) {
				remove(broken, null);
				return;
			}
		}

		barrels.add(this);
	}

	public static void onUpdate() {//UPDATE THE POTION TODO
		for (Barrel barrel : barrels) {
			// Minecraft day is 20 min, so add 1/20 to the time every minute
			//barrel.time += (1.0 / 20.0);
			barrel.time += (1.0 / minutesPerYear);
		}
		if (check == 0 && barrels.size() > 0) {
			Barrel random = barrels.get((int) Math.floor(Math.random() * barrels.size()));
			if (random != null) {
				// You have been selected for a random search
				// We want to check at least one barrel every time
				random.checked = false;
			}
			new BarrelCheck().runTaskTimer(P.p, 1, 1);
		}
	}

	public boolean hasPermsOpen(Player player, PlayerInteractEvent event) {
		if (isLarge()) {
			if (!player.hasPermission("brewery.openbarrel.big")) {
				P.p.msg(player, P.p.languageReader.get("Error_NoBarrelAccess"));
				return false;
			}
		} else {
			if (!player.hasPermission("brewery.openbarrel.small")) {
				P.p.msg(player, P.p.languageReader.get("Error_NoBarrelAccess"));
				return false;
			}
		}



		return true;
	}

	// player opens the barrel
	public void open(Player player) {
		player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
		if (inventory == null) {
			if (isLarge()) {
				inventory = org.bukkit.Bukkit.createInventory(this, 27, P.p.languageReader.get("Etc_Barrel"));
			} else {
				inventory = org.bukkit.Bukkit.createInventory(this, 9, P.p.languageReader.get("Etc_Barrel"));
			}
		} else {
			if (time > 0) {
				// if nobody has the inventory opened
				if (inventory.getViewers().isEmpty()) {
					// if inventory contains potions
					if (inventory.contains(Material.POTION)) {
						byte wood = getWood();
						long loadTime = System.nanoTime();
						for (ItemStack item : inventory.getContents()) {
							if (item != null) {
								Brew brew = Brew.get(item);
								if (brew != null) {
									brew.age(item, time, wood);
								}
							}
						}
						loadTime = System.nanoTime() - loadTime;
						float ftime = (float) (loadTime / 1000000.0);
						P.p.debugLog("opening Barrel with potions (" + ftime + "ms)");
					}
				}
			}
		}
		// reset barreltime, potions have new age
		time = 0;

		/*if (P.p.useLB) {
			try {
				LogBlockBarrel.openBarrel(player, inventory, spigot.getLocation());
			} catch (Throwable e) {
				P.p.errorLog("Failed to Log Barrel to LogBlock!");
				P.p.errorLog("Brewery was tested with version 1.94 of LogBlock!");
				e.printStackTrace();
			}
		}*/
		player.openInventory(inventory);
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	// Returns true if this Block is part of this Barrel
	public boolean hasBlock(Block block) {
		if (block != null) {
			if (isWood(block.getType())) {
				if (hasWoodBlock(block)) {
					return true;
				}
			} else if (isStairs(block.getType())) {
				if (hasStairsBlock(block)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasWoodBlock(Block block) {
		if (woodsloc != null) {
			if (spigot.getWorld() != null && spigot.getWorld().equals(block.getWorld())) {
				if (woodsloc.length > 2) {
					int x = block.getX();
					if (Math.abs(x - woodsloc[0]) < 10) {
						for (int i = 0; i < woodsloc.length - 2; i += 3) {
							if (woodsloc[i] == x) {
								if (woodsloc[i + 1] == block.getY()) {
									if (woodsloc[i + 2] == block.getZ()) {
										return true;
									}
								}
							}
						}
					}
				}
			}
		}
		return false;
	}

	public boolean hasStairsBlock(Block block) {
		if (stairsloc != null) {
			if (spigot.getWorld() != null && spigot.getWorld().equals(block.getWorld())) {
				if (stairsloc.length > 2) {
					int x = block.getX();
					if (Math.abs(x - stairsloc[0]) < 10) {
						for (int i = 0; i < stairsloc.length - 2; i += 3) {
							if (stairsloc[i] == x) {
								if (stairsloc[i + 1] == block.getY()) {
									if (stairsloc[i + 2] == block.getZ()) {
										return true;
									}
								}
							}
						}
					}
				}
			}
		}
		return false;
	}

	// Returns true if the Offset of the clicked Sign matches the Barrel.
	// This prevents adding another sign to the barrel and clicking that.
	public boolean isSignOfBarrel(byte offset) {
		return offset == 0 || signoffset == 0 || signoffset == offset;
	}

	// Get the Barrel by Block, null if that block is not part of a barrel
	public static Barrel get(Block block) {
		if (block != null) {
			switch (block.getType()) {
			case OAK_FENCE:
			case NETHER_BRICK_FENCE:
			case SIGN:
			case WALL_SIGN:
			case ACACIA_FENCE:
			case BIRCH_FENCE:
			case DARK_OAK_FENCE:
			case IRON_BARS:
			case JUNGLE_FENCE:
			case SPRUCE_FENCE:
				Barrel barrel = getBySpigot(block);
				if (barrel != null) {
					return barrel;
				}
				return null;
			case OAK_PLANKS:
			case SPRUCE_PLANKS:
			case BIRCH_PLANKS:
			case JUNGLE_PLANKS:
			case ACACIA_PLANKS:
			case DARK_OAK_PLANKS:
			case OAK_STAIRS:
			case BIRCH_STAIRS:
			case JUNGLE_STAIRS:
			case SPRUCE_STAIRS:
			case ACACIA_STAIRS:
			case DARK_OAK_STAIRS:
				Barrel barrel2 = getByWood(block);
				if (barrel2 != null) {
					return barrel2;
				}
			default:
				break;
			}
		}
		return null;
	}

	// Get the Barrel by Sign or Spigot (Fastest)
	public static Barrel getBySpigot(Block sign) {
		// convert spigot if neccessary
		Block spigot = getSpigotOfSign(sign);

		byte signoffset = 0;
		if (!spigot.equals(sign)) {
			signoffset = (byte) (sign.getY() - spigot.getY());
		}

		for (Barrel barrel : barrels) {
			if (barrel.isSignOfBarrel(signoffset)) {
				if (barrel.spigot.equals(spigot)) {
					if (barrel.signoffset == 0 && signoffset != 0) {
						// Barrel has no signOffset even though we clicked a sign, may be old
						barrel.signoffset = signoffset;
					}
					return barrel;
				}
			}
		}
		return null;
	}

	// Get the barrel by its corpus (Wood Planks, Stairs)
	public static Barrel getByWood(Block wood) {
		if (isWood(wood.getType())) {
			for (Barrel barrel : barrels) {
				if (barrel.hasWoodBlock(wood)) {
					return barrel;
				}
			}
		} else if (isStairs(wood.getType())) {
			for (Barrel barrel : Barrel.barrels) {
				if (barrel.hasStairsBlock(wood)) {
					return barrel;
				}
			}
		}
		return null;
	}

	// creates a new Barrel out of a sign
	public static boolean create(Block sign, Player player) {
		Block spigot = getSpigotOfSign(sign);

		byte signoffset = 0;
		if (!spigot.equals(sign)) {
			signoffset = (byte) (sign.getY() - spigot.getY());
		}

		Barrel barrel = getBySpigot(spigot);
		if (barrel == null) {
			barrel = new Barrel(spigot, signoffset);
			if (barrel.getBrokenBlock(true) == null) {
				if (isSign(spigot)) {
					if (!player.hasPermission("brewery.createbarrel.small")) {
						P.p.msg(player, P.p.languageReader.get("Perms_NoSmallBarrelCreate"));
						return false;
					}
				} else {
					if (!player.hasPermission("brewery.createbarrel.big")) {
						P.p.msg(player, P.p.languageReader.get("Perms_NoBigBarrelCreate"));
						return false;
					}
				}
				barrels.add(barrel);
				return true;
			}
		} else {
			if (barrel.signoffset == 0 && signoffset != 0) {
				barrel.signoffset = signoffset;
				return true;
			}
		}
		return false;
	}

	// removes a barrel, throwing included potions to the ground
	public void remove(Block broken, Player breaker) {
		if (inventory != null) {
			for (HumanEntity human : inventory.getViewers()) {
				human.closeInventory();
			}
			ItemStack[] items = inventory.getContents();
			/*if (P.p.useLB && breaker != null) {
				try {
					LogBlockBarrel.breakBarrel(breaker.getName(), items, spigot.getLocation());
				} catch (Throwable e) {
					P.p.errorLog("Failed to Log Barrel-break to LogBlock!");
					P.p.errorLog("Brewery was tested with version 1.94 of LogBlock!");
					e.printStackTrace();
				}
			}*/
			for (ItemStack item : items) {
				if (item != null) {
					Brew brew = Brew.get(item);
					if (brew != null) {
						// Brew before throwing
						brew.age(item, time, getWood());
						PotionMeta meta = (PotionMeta) item.getItemMeta();
						if (Brew.hasColorLore(meta)) {
							brew.convertLore(meta, false);
							item.setItemMeta(meta);
						}
					}
					// "broken" is the block that destroyed, throw them there!
					if (broken != null) {
						broken.getWorld().dropItem(broken.getLocation(), item);
					} else {
						spigot.getWorld().dropItem(spigot.getLocation(), item);
					}
				}
			}
		}

		barrels.remove(this);
	}

	//unloads barrels that are in a unloading world
	public static void onUnload(String name) {
		for (Barrel barrel : barrels) {
			if (barrel.spigot.getWorld().getName().equals(name)) {
				barrels.remove(barrel);
			}
		}
	}

	// If the Sign of a Large Barrel gets destroyed, set signOffset to 0
	public void destroySign() {
		signoffset = 0;
	}

	// Saves all data
	public static void save(ConfigurationSection config, ConfigurationSection oldData) {
		P.p.createWorldSections(config);

		if (!barrels.isEmpty()) {
			int id = 0;
			for (Barrel barrel : barrels) {

				String worldName = barrel.spigot.getWorld().getName();
				String prefix;

				if (worldName.startsWith("DXL_")) {
					prefix = P.p.getDxlName(worldName) + "." + id;
				} else {
					prefix = barrel.spigot.getWorld().getUID().toString() + "." + id;
				}

				// block: x/y/z
				config.set(prefix + ".spigot", barrel.spigot.getX() + "/" + barrel.spigot.getY() + "/" + barrel.spigot.getZ());

				if (barrel.signoffset != 0) {
					config.set(prefix + ".sign", barrel.signoffset);
				}
				if (barrel.stairsloc != null && barrel.stairsloc.length > 0) {
					StringBuilder st = new StringBuilder();
					for (int i : barrel.stairsloc) {
						st.append(i).append(",");
					}
					config.set(prefix + ".st", st.substring(0, st.length() - 1));
				}
				if (barrel.woodsloc != null && barrel.woodsloc.length > 0) {
					StringBuilder wo = new StringBuilder();
					for (int i : barrel.woodsloc) {
						wo.append(i).append(",");
					}
					config.set(prefix + ".wo", wo.substring(0, wo.length() - 1));
				}

				if (barrel.inventory != null) {
					int slot = 0;
					ItemStack item;
					ConfigurationSection invConfig = null;
					while (slot < barrel.inventory.getSize()) {
						item = barrel.inventory.getItem(slot);
						if (item != null) {
							if (invConfig == null) {
								if (barrel.time != 0) {
									config.set(prefix + ".time", barrel.time);
								}
								invConfig = config.createSection(prefix + ".inv");
							}
							// ItemStacks are configurationSerializeable, makes them
							// really easy to save
							invConfig.set(slot + "", item);
						}

						slot++;
					}
				}

				id++;
			}
		}
		// also save barrels that are not loaded
		if (oldData != null){
			for (String uuid : oldData.getKeys(false)) {
				if (!config.contains(uuid)) {
					config.set(uuid, oldData.get(uuid));
				}
			}
		}
	}

	// direction of the barrel from the spigot
	public static int getDirection(Block spigot) {
		int direction = 0;// 1=x+ 2=x- 3=z+ 4=z-
		Material type = spigot.getRelative(0, 0, 1).getType();
		if (isWood(type) || isStairs(type)) {
			direction = 3;
		}
		type = spigot.getRelative(0, 0, -1).getType();
		if (isWood(type) || isStairs(type)) {
			if (direction == 0) {
				direction = 4;
			} else {
				return 0;
			}
		}
		type = spigot.getRelative(1, 0, 0).getType();
		if (isWood(type) || isStairs(type)) {
			if (direction == 0) {
				direction = 1;
			} else {
				return 0;
			}
		}
		type = spigot.getRelative(-1, 0, 0).getType();
		if (isWood(type) || isStairs(type)) {
			if (direction == 0) {
				direction = 2;
			} else {
				return 0;
			}
		}
		return direction;
	}

	// is this a Large barrel?
	public boolean isLarge() {
		return !isSign(spigot);
	}

	// true for small barrels
	public static boolean isSign(Block spigot) {
		return spigot.getType() == Material.WALL_SIGN || spigot.getType() == Material.SIGN;
	}

	// woodtype of the block the spigot is attached to
	public byte getWood() {
		Block wood;
		switch (getDirection(spigot)) { // 1=x+ 2=x- 3=z+ 4=z-
			case 0:
				return 0;
			case 1:
				wood = spigot.getRelative(1, 0, 0);
				break;
			case 2:
				wood = spigot.getRelative(-1, 0, 0);
				break;
			case 3:
				wood = spigot.getRelative(0, 0, 1);
				break;
			default:
				wood = spigot.getRelative(0, 0, -1);
		}
		try {
			switch (wood.getType()) {
				case OAK_PLANKS:
				case OAK_STAIRS:
					return 2;
				case SPRUCE_PLANKS:
				case SPRUCE_STAIRS:
					return 4;
				case BIRCH_PLANKS:
				case BIRCH_STAIRS:
					return 1;
				case JUNGLE_PLANKS:
				case JUNGLE_STAIRS:
					return 3;
				case ACACIA_PLANKS:
				case ACACIA_STAIRS:
					return 5;
				case DARK_OAK_PLANKS:
				case DARK_OAK_STAIRS:
					return 6;
				default:
					return 0;
			}

		} catch (NoSuchFieldError | NoClassDefFoundError e) {
			// Using older minecraft versions some fields and classes do not exist
			return 0;
		}
	}

	// returns the Sign of a large barrel, the spigot if there is none
	public Block getSignOfSpigot() {
		if (signoffset != 0) {
			if (isSign(spigot)) {
				return spigot;
			}

			if (isSign(spigot.getRelative(0, signoffset, 0))) {
				return spigot.getRelative(0, signoffset, 0);
			} else {
				signoffset = 0;
			}
		}
		return spigot;
	}

	// returns the fence above/below a block, itself if there is none
	public static Block getSpigotOfSign(Block block) {

		int y = -2;
		while (y <= 1) {
			// Fence and Netherfence
			Block relative = block.getRelative(0, y, 0);
			if (isFence(relative.getType())) {
				return (relative);
			}
			y++;
		}
		return block;
	}

	public static boolean isStairs(Material material) {
		switch (material) {
			case OAK_STAIRS:
			case SPRUCE_STAIRS:
			case BIRCH_STAIRS:
			case JUNGLE_STAIRS:
			case ACACIA_STAIRS:
			case DARK_OAK_STAIRS:
				return true;
			default:
				return false;
		}
	}

	public static boolean isWood(Material material) {
		switch (material) {
			case OAK_PLANKS:
			case SPRUCE_PLANKS:
			case BIRCH_PLANKS:
			case JUNGLE_PLANKS:
			case ACACIA_PLANKS:
			case DARK_OAK_PLANKS:
				return true;
			default:
				return false;
		}
	}

	
	public static boolean isFence(Material material) {
		switch (material) {
			case OAK_FENCE:
			case NETHER_BRICK_FENCE:
			case ACACIA_FENCE:
			case BIRCH_FENCE:
			case DARK_OAK_FENCE:
			case IRON_BARS:
			case JUNGLE_FENCE:
			case SPRUCE_FENCE:
				return true;
			default:
				return false;
		}
	}

	// returns null if Barrel is correctly placed; the block that is missing when not
	// the barrel needs to be formed correctly
	// flag force to also check if chunk is not loaded
	public Block getBrokenBlock(boolean force) {
		if (force || spigot.getChunk().isLoaded()) {
			spigot = getSpigotOfSign(spigot);
			if (isSign(spigot)) {
				return checkSBarrel();
			} else {
				return checkLBarrel();
			}
		}
		return null;
	}

	public Block checkSBarrel() {
		int direction = getDirection(spigot);// 1=x+ 2=x- 3=z+ 4=z-
		if (direction == 0) {
			return spigot;
		}
		int startX;
		int startZ;
		int endX;
		int endZ;

		ArrayList<Integer> stairs = new ArrayList<Integer>();

		if (direction == 1) {
			startX = 1;
			endX = startX + 1;
			startZ = -1;
			endZ = 0;
		} else if (direction == 2) {
			startX = -2;
			endX = startX + 1;
			startZ = 0;
			endZ = 1;
		} else if (direction == 3) {
			startX = 0;
			endX = 1;
			startZ = 1;
			endZ = startZ + 1;
		} else {
			startX = -1;
			endX = 0;
			startZ = -2;
			endZ = startZ + 1;
		}

		Material type;
		int x = startX;
		int y = 0;
		int z = startZ;
		while (y <= 1) {
			while (x <= endX) {
				while (z <= endZ) {
					Block block = spigot.getRelative(x, y, z);
					type = block.getType();

					if (isStairs(type)) {
						if (y == 0) {
							// stairs have to be upside down
							
							Stairs stair = (Stairs) block.getBlockData();
							if(stair.getHalf() == Bisected.Half.BOTTOM) {
								return block;
							}
							/*MaterialData data = block.getState().getData();
							if (data instanceof Stairs) {
								if (!((Stairs) data).isInverted()) {
									return block;
								}
							}*/
							
						}
						stairs.add(block.getX());
						stairs.add(block.getY());
						stairs.add(block.getZ());
						z++;
					} else {
						return spigot.getRelative(x, y, z);
					}
				}
				z = startZ;
				x++;
			}
			z = startZ;
			x = startX;
			y++;
		}
		stairsloc = ArrayUtils.toPrimitive(stairs.toArray(new Integer[stairs.size()]));
		return null;
	}

	public Block checkLBarrel() {
		int direction = getDirection(spigot);// 1=x+ 2=x- 3=z+ 4=z-
		if (direction == 0) {
			return spigot;
		}
		int startX;
		int startZ;
		int endX;
		int endZ;

		ArrayList<Integer> stairs = new ArrayList<Integer>();
		ArrayList<Integer> woods = new ArrayList<Integer>();

		if (direction == 1) {
			startX = 1;
			endX = startX + 3;
			startZ = -1;
			endZ = 1;
		} else if (direction == 2) {
			startX = -4;
			endX = startX + 3;
			startZ = -1;
			endZ = 1;
		} else if (direction == 3) {
			startX = -1;
			endX = 1;
			startZ = 1;
			endZ = startZ + 3;
		} else {
			startX = -1;
			endX = 1;
			startZ = -4;
			endZ = startZ + 3;
		}

		Material type;
		int x = startX;
		int y = 0;
		int z = startZ;
		while (y <= 2) {
			while (x <= endX) {
				while (z <= endZ) {
					Block block = spigot.getRelative(x, y, z);
					type = block.getType();
					if (direction == 1 || direction == 2) {
						if (y == 1 && z == 0) {
							if (x == -1 || x == -4 || x == 1 || x == 4) {
								woods.add(block.getX());
								woods.add(block.getY());
								woods.add(block.getZ());
							}
							z++;
							continue;
						}
					} else {
						if (y == 1 && x == 0) {
							if (z == -1 || z == -4 || z == 1 || z == 4) {
								woods.add(block.getX());
								woods.add(block.getY());
								woods.add(block.getZ());
							}
							z++;
							continue;
						}
					}
					if (isWood(type) || isStairs(type)) {
						if (isWood(type)) {
							woods.add(block.getX());
							woods.add(block.getY());
							woods.add(block.getZ());
						} else {
							stairs.add(block.getX());
							stairs.add(block.getY());
							stairs.add(block.getZ());
						}
						z++;
					} else {
						return block;
					}
				}
				z = startZ;
				x++;
			}
			z = startZ;
			x = startX;
			y++;
		}
		stairsloc = ArrayUtils.toPrimitive(stairs.toArray(new Integer[stairs.size()]));
		woodsloc = ArrayUtils.toPrimitive(woods.toArray(new Integer[woods.size()]));

		return null;
	}

	public static class BarrelCheck extends BukkitRunnable {
		@Override
		public void run() {
			boolean repeat = true;
			while (repeat) {
				if (check < barrels.size()) {
					Barrel barrel = barrels.get(check);
					if (!barrel.checked) {
						Block broken = barrel.getBrokenBlock(false);
						if (broken != null) {
							P.p.debugLog("Barrel at " + broken.getWorld().getName() + "/" + broken.getX() + "/" + broken.getY() + "/" + broken.getZ()
									+ " has been destroyed unexpectedly, contents will drop");
							// remove the barrel if it was destroyed
							//barrel.willDestroy();
							barrel.remove(broken, null);
						} else {
							// Dont check this barrel again, its enough to check it once after every restart
							// as now this is only the backup if we dont register the barrel breaking, as sample
							// when removing it with some world editor
							barrel.checked = true;
						}
						repeat = false;
					}
					check++;
				} else {
					check = 0;
					repeat = false;
					cancel();
				}
			}
		}

	}

}
