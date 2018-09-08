package com.dreamless.brewery.listeners;

import java.util.ListIterator;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import com.dreamless.brewery.Barrel;
import com.dreamless.brewery.Brew;
import com.dreamless.brewery.P;
import com.dreamless.brewery.integration.LWCBarrel;

public class EntityListener implements Listener {

	// Remove the Potion from Brew when it despawns
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onItemDespawn(ItemDespawnEvent event) {
		ItemStack item = event.getEntity().getItemStack();
		if (item.getType() == Material.POTION) {
			Brew brew = Brew.get(item);
			if (brew != null) {
				brew.remove(item);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityCombust(EntityCombustEvent event) {
		Entity entity = event.getEntity();
		if (entity.getType() == EntityType.DROPPED_ITEM) {
			if (entity instanceof Item) {
				ItemStack item = ((Item) entity).getItemStack();
				if (item.getType() == Material.POTION) {
					Brew brew = Brew.get(item);
					if (brew != null) {
						brew.remove(item);
					}
				}
			}
		}
	}

	//  --- Barrel Breaking ---

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onExplode(EntityExplodeEvent event) {
		ListIterator<Block> iter = event.blockList().listIterator();
		Barrel barrel = null;
		boolean removedBarrel = false;
		while (iter.hasNext()) {
			Block block = iter.next();
			if (barrel == null || !barrel.hasBlock(block)) {
				barrel = Barrel.get(block);
				removedBarrel = false;
			}
			if (!removedBarrel) {
				if (barrel != null) {
					if (P.p.useLWC) {
						try {
							if (LWCBarrel.blockExplosion(barrel, block)) {
								iter.remove();
							} else {
								removedBarrel = true;
							}
						} catch (Exception e) {
							P.p.errorLog("Failed to Check LWC on Barrel Explosion!");
							e.printStackTrace();
							removedBarrel = true;
						}
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockChange(EntityChangeBlockEvent event) {
		if (Barrel.get(event.getBlock()) != null) {
			event.setCancelled(true);
		}
	}

}
