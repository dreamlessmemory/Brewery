package com.dreamless.brewery;

import java.util.ArrayList;
import java.util.Set;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.dreamless.brewery.utils.BreweryMessage;
import com.dreamless.brewery.utils.NBTCompound;
import com.dreamless.brewery.utils.NBTItem;
import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;
import com.gmail.filoghost.holographicdisplays.api.line.ItemLine;
import com.gmail.filoghost.holographicdisplays.api.line.TextLine;

public class Distiller implements InventoryHolder {
	
	public static ArrayList<Distiller> distillers = new ArrayList<Distiller>();
	private static final int FILTER_LIMT = 9;
	
	private ArrayList<Material> filters = new ArrayList<Material>(); 
	private Block block;
	private Inventory filterInventory;
	private BrewerInventory brewingInventory;
	private boolean distilling = false;
	private boolean finishedDistilling = false;
	
	private Hologram hologram;
	private ItemLine filterLine;
	private TextLine statusLine;
	private TextLine secondStatusLine;


	public Distiller(Block block) {
		this.block = block;
		
		//Initialize Inventory
		filterInventory = org.bukkit.Bukkit.createInventory(this, FILTER_LIMT, "Distiller Filter Cache");
		
		brewingInventory = (BrewerInventory) ((InventoryHolder)block.getState()).getInventory();
		
		//Hologram
		if(hologram == null) {
			createHologram(block);
		}
		
		distillers.add(this);
	}
	
	
	//Static methods
	public static Distiller get(Block block) {
		for (Distiller distiller : distillers) {
			if (distiller.block.equals(block)) {
				return distiller;
			}
		}
		return null;
	}
	
	public static void add(Distiller distiller) {
		distillers.add(distiller);
	}
	
	public static void remove(Block block) {
		Distiller distiller = get(block);
		if (distiller != null) {
			for(ItemStack item: distiller.filterInventory) {
				distiller.ejectItem(item);
			}
			distillers.remove(distiller);
			
			//Remove hologram
			distiller.hologram.delete();
			
			//ruin brew if distilling
			if(distiller.distilling) {
				distiller.ruinPotions();
			}
		}
	}
	
	//Prep?
	public BreweryMessage prepDistiller() {
		if(loadFilters() == 0) {//No filters
			removeSelf();
			return new BreweryMessage(false, "No appropriate filters were loaded.");
		}
		if(filterLine == null) {
			filterLine = hologram.insertItemLine(0, new ItemStack(filters.get(0)));
		} else {
			filterLine.setItemStack(new ItemStack(filters.get(0)));
		}
		
		statusLine.setText("Filter: " + WordUtils.capitalize((filters.get(0).toString().toLowerCase().replace("_", " "))));
		secondStatusLine.setText((filters.size() + (filters.size() > 1 ? " filters" : " filter") + " loaded"));
		
		return new BreweryMessage(true, filters.size() + (filters.size() > 1 ? " filters" : " filter") + " loaded into the distiller.");
	}
	
	
	//Load Filters
	private int loadFilters() {
		ItemStack[] filterCache = new ItemStack[9];
		ItemStack[] convertedInventory = filterInventory.getContents();
		int index = 0;
		
		filters.clear();
		
		for(int i = 0; i < convertedInventory.length; i++) {
			ItemStack item = convertedInventory[i];
			
			if(item == null || item.getType() == Material.AIR) {
				continue;
			}
			
			//If you're already full or not valid, eject
			if(index >= FILTER_LIMT || !isValidFilter(item)) {
				//Eject
				ejectItem(item);
		    	continue;
			}
			
			//Add to cache
			filterCache[index++] = new ItemStack(item.getType(), 1);
			filters.add(item.getType());
			Brewery.breweryDriver.debugLog("Filter added: " + item.getType());
			//Reduce item
			i--;
			item.setAmount(item.getAmount()-1);
		}
		filterInventory.setContents(filterCache);
		return filters.size();
	}
	
	private boolean isValidFilter(ItemStack item) {
		switch(item.getType()) {
			case GLOWSTONE_DUST:
			case REDSTONE:
			case GUNPOWDER:
			case SUGAR:
				return true;
			default:
				return false;
		}
	}
	
	private void ejectItem(ItemStack item) {
		if(item != null && item.getType() != Material.AIR)
		block.getWorld().dropItem(block.getRelative(BlockFace.UP).getLocation().add(0.5, 0, 0.5), item);
    	block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP,(float)(Math.random()/2) + 0.75f, (float)(Math.random()/2) + 0.75f);
	}
	//Distilling Handling
	
	public BreweryMessage startDistilling(Player player) {
		
		if(finishedDistilling) {
			return new BreweryMessage(false, "Please remove finished brews before starting a new brew.");
		}
		
		boolean result = false;
		
		for(int i = 0; i < 3; i++) {
			ItemStack item = brewingInventory.getItem(i);
			if(item != null) {
				NBTItem nbti = new NBTItem(item);
				
				if(nbti.hasKey("brewery") && !nbti.getCompound("brewery").hasKey("distilled")) {
					result = true;
					
					//Tag as distilling brew
					NBTCompound brewery = nbti.getCompound("brewery");
					brewery.setBoolean("distilled", true);
					brewery.setString("placedInBrewer", player.getUniqueId().toString());
					item = nbti.getItem();
	
					brewingInventory.setItem(i, item);	
				} else {
					ejectItem(item);
					brewingInventory.setItem(i, null);
				}	
			}
		}

		if(!result) {
			return new BreweryMessage(false, "There are no brews that can be distilled");
		} else {
			distilling = true;
			return new BreweryMessage(true, "Distilling started");
		}
	}
	
	

	// distill all custom potions in the brewer
	public void distillAll() {
		Material nextFilterMaterial = filters.remove(0);
		filterInventory.remove(nextFilterMaterial);
		for(int i = 0; i < 3; i++) {
			ItemStack item = brewingInventory.getItem(i);
			if(item != null) {
				brewingInventory.setItem(i, distillSlot(item, nextFilterMaterial));
			}
		}
	}

	// distill custom potion in given slot
	private ItemStack distillSlot(ItemStack item, Material filter) {
		Brewery.breweryDriver.debugLog("DISTILLING 1 CYCLE : " + item.toString() + " FILTER: " + filter.toString());
		
		//Pull NBT
		NBTItem nbti = new NBTItem(item);
		NBTCompound brewery = nbti.getCompound("brewery");
		//Pull aspects
		NBTCompound aspectList = brewery.getCompound("aspectsActivation");
		Set<String> aspects = aspectList.getKeys();
		
		for(String currentAspect : aspects) {
			double aspectPotency = aspectList.getDouble(currentAspect);
			double newPotency = Aspect.processFilter(currentAspect, brewery.getString("type"), aspectPotency, filter);
			Brewery.breweryDriver.debugLog("Update Potency of " + currentAspect + ": " + aspectPotency + " -> " + newPotency);
			//Update NBT
			aspectList.setDouble(currentAspect, newPotency);
		}
		
		item = nbti.getItem();
		
		return item;
	}
	
	private void finishDistilling() {
		distilling = false;
		finishedDistilling = true;
		for(int i = 0; i < 3; i++) {
			ItemStack item = brewingInventory.getItem(i);
			if(item != null) {
				item = BRecipe.revealMaskedBrew(item);
				brewingInventory.setItem(i, item);
			}
		}
		filterLine.setItemStack(new ItemStack(Material.POTION));
		statusLine.setText("Brews distilled.");
		secondStatusLine.setText("Remove brews");
	}
	
	
	public boolean isEmpty() {
		for(int i = 0; i < 3; i++) {
			ItemStack item = brewingInventory.getItem(i);
			if(item == null) continue;
			if(item.getType() != Material.AIR) {
				return false;
			}
		}
		return true;
	}
	
	public void removeSelf() {
		hologram.delete();
		distillers.remove(this);
		Brewery.breweryDriver.debugLog("Check distill list: " + distillers.size());
	}
	
	public void ruinPotions() {
		for(int i = 0; i < 3; i++) {
			ItemStack item = brewingInventory.getItem(i);
			if(item == null) continue;
			
			PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
			
			potionMeta.setDisplayName("Ruined Brew");
			
			ArrayList<String> agedFlavorText = new ArrayList<String>();
			agedFlavorText.add("A brew that was ruined");
			agedFlavorText.add("by being removed during distillation.");
			potionMeta.setLore(agedFlavorText);
			
			potionMeta.clearCustomEffects();
			
			item.setItemMeta(potionMeta);
			
			brewingInventory.setItem(i, item);	
		}
	}
	
	private void createHologram(Block block) {
		Location above = block.getRelative(BlockFace.UP).getLocation();
		above.setX(above.getX()+ 0.5);
		above.setY(above.getY()+ 1.25);
		above.setZ(above.getZ()+ 0.5);
		hologram = HologramsAPI.createHologram(Brewery.breweryDriver, above);
		
		//Create ready message
		statusLine = hologram.appendTextLine("Distiller Ready");
		secondStatusLine = hologram.appendTextLine("Awaiting filters...");
	}

	@Override
	public Inventory getInventory() {
		return filterInventory;
	}
	
	public boolean isDistilling() {
		return distilling;
	}
	
	public boolean isFinishedDistilling() {
		return finishedDistilling;
	}
	
	public static class DistillerRunnable extends BukkitRunnable {
		private final int cycles;
		private final int cycleLength;
		private int currentCycle = 1;
		private int currentTime = 0;
		private Distiller distiller;
		
		public DistillerRunnable(int cycleLength, Distiller distiller) {
			cycles = distiller.filters.size();
			this.cycleLength = cycleLength;
			this.distiller = distiller;
			
		}
		
		@Override
		public void run() {
			if(++currentTime < cycleLength) {
				//Update Hologram
				distiller.secondStatusLine.setText("Cycle " +  currentCycle + "/" + cycles + " : " + (cycleLength - currentTime) + " s remaining");
				
			} else {
				//increment cycles
				currentTime = 0;
				currentCycle +=1;
				distiller.distillAll();
				if(currentCycle > cycles) {
					distiller.finishDistilling();
					Brewery.breweryDriver.debugLog("End distill");
					this.cancel();
				} else {
					distiller.filterLine.setItemStack(new ItemStack(distiller.filters.get(0)));
					distiller.statusLine.setText("Filter: " + WordUtils.capitalize((distiller.filters.get(0).toString().toLowerCase().replace("_", " "))));
					distiller.secondStatusLine.setText("Cycle " +  currentCycle + "/" + cycles + " : " + (cycleLength - currentTime) + " s remaining");
				}
			}
		}
		
	}
	
	
}
