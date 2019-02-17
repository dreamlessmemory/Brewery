package com.dreamless.brewery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Rotatable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Effect;

public class BCauldron {
	public static CopyOnWriteArrayList<BCauldron> bcauldrons = new CopyOnWriteArrayList<BCauldron>();

	private BIngredients ingredients = new BIngredients();
	private Block block;
	private int state = 0;
	private boolean cooking = false;
	private long lastCook = 0;
	
	public BCauldron(Block block) {
		this.block = block;
		cooking = false;
		bcauldrons.add(this);
	}

	// loading from file
	public BCauldron(Block block, BIngredients ingredients, int state, boolean cooking) {
		this.block = block;
		this.state = state;
		this.ingredients = ingredients;
		this.cooking = cooking;
		bcauldrons.add(this);
	}
	
	public BCauldron(Block block, BIngredients ingredients, int state, boolean cooking, long lastCook) {
		this.block = block;
		this.state = state;
		this.ingredients = ingredients;
		this.cooking = cooking;
		this.lastCook = lastCook;
		bcauldrons.add(this);
	}

	public void onUpdate() {//UPDATE THE POTION
		// Check if fire still alive
		if ((!block.getChunk().isLoaded() || fireAndAirInPlace().getResult()) && cooking) {
			
			if(getFillLevel(block) == 0) {//remove yourself if empty
				remove(block);
				return;
			}
			
			//Check if a minute has passed	
			long currentTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
			//Brewery.breweryDriver.debugLog("Second tick = " + currentTime);
			if((currentTime - lastCook) > 60) {
				//Update cook time
				lastCook = currentTime;
				
				// add a minute to cooking time
				state++;
				
				//Sound and particle effects
				block.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, block.getLocation().getX() + 0.5, block.getLocation().getY() + 1.5, block.getLocation().getZ() + 0.5, 10, 0.5, 0.5, 0.5);
				block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, (float)(Math.random()/8) + 0.1f, (float)(Math.random()/2) + 0.75f);
				
				//Run aspect calculation
				ingredients.fermentOneStep(state);
				
				//Update Sign
				
				//Set Sign facing player
				Block above = block.getRelative(BlockFace.UP);
				
				if(above.getType() != Material.SIGN) {//If no sign, make one
					above.getWorld().getBlockAt(above.getLocation()).setType(Material.SIGN);
				}
				Sign sign = (Sign) block.getWorld().getBlockAt(above.getLocation()).getState();
				//MaterialData
				sign.setLine(0, "[Brewery]");
				sign.setLine(1, state + " minutes");
				//((Rotatable)sign).setRotation(BreweryUtils.getPlayerDirection(player).getOppositeFace());
				sign.update();
				
				
			} else {
				//Bubble effects
				block.getWorld().spawnParticle(Particle.SMOKE_NORMAL, block.getLocation().getX() + 0.5, block.getLocation().getY() + 1.5, block.getLocation().getZ() + 0.5, 10, 0.15, 0.15, 0.15, 0.05);
				block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, (float)(Math.random()/8) + 0.1f, (float)(Math.random() * 1.5) + 0.5f);
			}
		} else { //no fire, stop cooking
			if(isCooking()) {
				setCooking(false);
				block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 2.0f, 1.0f);
			}
		}
	}

	public boolean isCooking() {
		return cooking;
	}

	public void setCooking(boolean cooking) {
		this.cooking = cooking;
	}

	// get cauldron by Block
	public static BCauldron get(Block block) {
		for (BCauldron bcauldron : bcauldrons) {
			if (bcauldron.block.equals(block)) {
				return bcauldron;
			}
		}
		return null;
	}


	// fills players bottle with cooked brew
	public static boolean fill(Player player, Block block) {
		BCauldron bcauldron = get(block);
		if (bcauldron != null) {
			if (!player.hasPermission("brewery.cauldron.fill")) {
				Brewery.breweryDriver.msg(player, Brewery.breweryDriver.languageReader.get("Perms_NoCauldronFill"));
				return true;
			}
			ItemStack potion = bcauldron.ingredients.finishFermentation(bcauldron.state, player);
			
			if (potion != null) {
				
				Levelled cauldronData = (Levelled) block.getBlockData();
				int level = cauldronData.getLevel();
				
				if(level > cauldronData.getMaximumLevel()) {
					level = cauldronData.getMaximumLevel();
				} else if (level <= 0) {
					bcauldrons.remove(bcauldron);
					return false;
				}
				
				cauldronData.setLevel(--level);
				block.setBlockData(cauldronData);
				block.getState().update();
				
				if (level == 0) {
					bcauldrons.remove(bcauldron);
				}
				giveItem(player, potion);
				return true;
			}
		}
		return false;
	}

	// 0 = empty, 1 = something in, 2 = full
	public static int getFillLevel(Block block) {
		if (block.getType() == Material.CAULDRON) {
			Levelled fillLevel = (Levelled) block.getState().getBlockData();
			return fillLevel.getLevel();
		}
		return 0;
	}

	// prints the current cooking time to the player
	public static void printTime(Player player, Block block) {
		if (!player.hasPermission("brewery.cauldron.time")) {
			Brewery.breweryDriver.msg(player, Brewery.breweryDriver.languageReader.get("Error_NoPermissions"));
			return;
		}
		BCauldron bcauldron = get(block);
		if (bcauldron != null) {
			if (bcauldron.state > 1) {
				Brewery.breweryDriver.msg(player, Brewery.breweryDriver.languageReader.get("Player_CauldronInfo2", Integer.toString(bcauldron.state)));
			} else if (bcauldron.state == 1) {
				Brewery.breweryDriver.msg(player, Brewery.breweryDriver.languageReader.get("Player_CauldronInfo1"));
			} else {
				Brewery.breweryDriver.msg(player, Brewery.breweryDriver.languageReader.get("Player_CauldronInfo0"));
			}
		}
	}
	
	public static Inventory getInventory(Block block) {
		BCauldron bcauldron = get(block);
		if (bcauldron == null) {
			if(getFillLevel(block) > 1) {
				bcauldron = new BCauldron(block);
			} else {
				return null;
			}
		}
		return bcauldron.ingredients.getInventory();
	}
	
	// reset to normal cauldron
	public static void remove(Block block) {
		BCauldron bcauldron = get(block);
		if (bcauldron != null) {
			if(!bcauldron.cooking) {
				bcauldron.ingredients.dumpContents(block);
			}
			bcauldrons.remove(bcauldron);
			
			Block above = block.getRelative(BlockFace.UP);
			above.getWorld().getBlockAt(above.getLocation()).setType(Material.AIR);
		}
	}

	// unloads cauldrons that are in a unloading world
	// as they were written to file just before, this is safe to do
	public static void onUnload(String name) {
		for (BCauldron bcauldron : bcauldrons) {
			if (bcauldron.block.getWorld().getName().equals(name)) {
				bcauldrons.remove(bcauldron);
			}
		}
	}

	public static void save() {
		int id = 0;
		if (!bcauldrons.isEmpty()) {
			for (BCauldron cauldron : bcauldrons) {	
				Brewery.breweryDriver.debugLog("CAULDRON");
				//Location
				String location = Brewery.gson.toJson(cauldron.block.getLocation().serialize());
				Brewery.breweryDriver.debugLog(location);
				
				String jsonInventory = null;
				//inventory
				try {
					jsonInventory = BreweryUtils.toBase64(cauldron.ingredients.getInventory());
					Brewery.breweryDriver.debugLog(jsonInventory);
				} catch (IllegalStateException e) {
					e.printStackTrace();
				}
				
				//Aspects
				String aspects = Brewery.gson.toJson(cauldron.ingredients.getAspects());
				Brewery.breweryDriver.debugLog(aspects);
				
				//TODO: revert cauldron_test -> cauldrons
				String query = "REPLACE " + Brewery.database + "cauldron_test SET idcauldrons=?, location=?, contents=?, aspects=?, state=?, cooking=?, lastCook=?";
				try(PreparedStatement stmt = Brewery.connection.prepareStatement(query)) {
					stmt.setInt(1, id);
					stmt.setString(2, location);
					stmt.setString(3, jsonInventory);
					stmt.setString(4, aspects);
					stmt.setInt(5, cauldron.state);
					stmt.setBoolean(6, cauldron.cooking);
					stmt.setLong(7, cauldron.lastCook);
					
					Brewery.breweryDriver.debugLog(stmt.toString());
					
					stmt.executeUpdate();
				} catch (SQLException e1) {
					e1.printStackTrace();
					return;
				}				
				id++;
			}
		}
		//clean up extras
		String query = "DELETE FROM " + Brewery.database + "cauldrons WHERE idcauldrons >=?";
		try(PreparedStatement stmt = Brewery.connection.prepareStatement(query)) {
			stmt.setInt(1, id);
			Brewery.breweryDriver.debugLog(stmt.toString());
			stmt.executeUpdate();
		} catch (SQLException e1) {
			e1.printStackTrace();
			return;
		}
	}

	// bukkit bug not updating the inventory while executing event, have to
	// schedule the give
	public static void giveItem(final Player player, final ItemStack item) {
		Brewery.breweryDriver.getServer().getScheduler().runTaskLater(Brewery.breweryDriver, new Runnable() {
			public void run() {
				player.getInventory().addItem(item);
			}
		}, 1L);
	}
	
	private BreweryMessage fireAndAirInPlace() {
		
		Material down = block.getRelative(BlockFace.DOWN).getType();
		Material up = block.getRelative(BlockFace.UP).getType();
		
		if(down != Material.FIRE && down != Material.LAVA && down != Material.MAGMA_BLOCK) {
			return new BreweryMessage(false, "There is no heat source for this cauldron.");
		}
		
		if(up != Material.AIR && up != Material.SIGN) {
			return new BreweryMessage(false, "The space above the cauldron is not clear.");
		}
		
		return new BreweryMessage(true, "");
	}
	
	/*public static boolean fireActive(Block block) {
		BCauldron cauldron = get(block);
		if(cauldron ==null) {
			return false;
		}
		return cauldron.fireActive();
	}*/
	
	public static boolean isCooking(Block block) {
		BCauldron bcauldron = get(block);
		if(bcauldron != null) {
			return bcauldron.cooking;
		} else {
			return false;
		}
	}

	public static BreweryMessage startCooking(Block block, Player player) {
		BCauldron bcauldron = get(block);
		if(bcauldron!= null) {
			BreweryMessage spaceCheck = bcauldron.fireAndAirInPlace();
			
			if(!spaceCheck.getResult()) {
				return spaceCheck;
			}
			
			if(bcauldron.ingredients.isEmpty()) {
				return new BreweryMessage(false, "This cauldron is empty and cannot start cooking");
			}
			
			BreweryMessage result = bcauldron.ingredients.startCooking(block);
			bcauldron.cooking = result.getResult();
			//Set cook time
			if(result.getResult()) {
				//Set time
				bcauldron.lastCook = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
				
				//Set Sign facing player
				Block above = block.getRelative(BlockFace.UP);
				player.getWorld().getBlockAt(above.getLocation()).setType(Material.SIGN);
				Sign sign = (Sign) player.getWorld().getBlockAt(above.getLocation()).getState();
				BlockData signData = sign.getBlockData();
				//MaterialData
				sign.setLine(0, "[Brewery]");
				sign.setLine(1, "Started boiling");
				//TODO: Fix rotation or replace with holograms
				((Rotatable)signData).setRotation(BreweryUtils.getPlayerDirection(player).getOppositeFace());
				sign.setBlockData(signData);
				sign.update();
			}
			
				
			return result;
		}
		return new BreweryMessage(false, "No cauldron at this location.");
	}
}