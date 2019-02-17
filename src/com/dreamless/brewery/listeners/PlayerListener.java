package com.dreamless.brewery.listeners;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.dreamless.brewery.*;
import com.dreamless.brewery.utils.BreweryMessage;
import com.dreamless.brewery.utils.NBTItem;


public class PlayerListener implements Listener {
	public static boolean openEverywhere;
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Block clickedBlock = event.getClickedBlock();

		if (clickedBlock != null) {
			if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
				Player player = event.getPlayer();
				if (!player.isSneaking()) {
					Material type = clickedBlock.getType();

					// Interacting with a Cauldron
					if (type == Material.CAULDRON) {
						Material materialInHand = event.getMaterial();
						ItemStack item = event.getItem();

						if (materialInHand == null) {
							return;
						} else if (materialInHand == Material.BUCKET) {
							BCauldron.remove(clickedBlock);
							return;
						} else if (materialInHand == Material.CLOCK) {
							if(BCauldron.isCooking(clickedBlock)) {//Print time if cooking
								BCauldron.printTime(player, clickedBlock);
							} else if(((Levelled)clickedBlock.getBlockData()).getLevel() > 0){
								BreweryMessage result = BCauldron.startCooking(clickedBlock, player);
								if(result.getResult()) {//Start cooking
									clickedBlock.getWorld().playSound(clickedBlock.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.0f, 1.0f);
								}
								Brewery.breweryDriver.msg(player, result.getMessage());
							}	
							return;
						} else if (materialInHand == Material.IRON_SHOVEL) {//Interact with inventory
							if (player.hasPermission("brewery.cauldron.insert")) {
								Inventory inventory = BCauldron.getInventory(clickedBlock);				
								if(inventory != null) {
									player.openInventory(inventory);
								}
							} else {
								Brewery.breweryDriver.msg(player, Brewery.breweryDriver.languageReader.get("Perms_NoCauldronInsert"));
							}
							//player.openInventory(BCauldron.getInventory(clickedBlock));
							return;
						} else if (materialInHand == Material.GLASS_BOTTLE) { // fill a glass bottle with potion
							if (BCauldron.isCooking(clickedBlock) && player.getInventory().firstEmpty() != -1 || item.getAmount() == 1) {
								if (BCauldron.fill(player, clickedBlock)) {
									event.setCancelled(true);
									if (player.hasPermission("brewery.cauldron.fill")) {
										if (item.getAmount() > 1) {
											item.setAmount(item.getAmount() - 1);
										} else {
											setItemInHand(event, Material.AIR, false);
										}
									}
								}
							} else {
								event.setCancelled(true);
							}
							return;
							// reset cauldron when refilling to prevent unlimited source of potions
						} else if (materialInHand == Material.WATER_BUCKET) {
							if (BCauldron.getFillLevel(clickedBlock) != 0 && BCauldron.getFillLevel(clickedBlock) < 2) {
								// will only remove when existing
								BCauldron.remove(clickedBlock);
							}
							return;
						}
						return;
					}

					if (Brewery.use1_9 && event.getHand() != EquipmentSlot.HAND) {
						return;
					}

					// Access a Barrel
					Barrel barrel = null;
					if (Barrel.isWood(type)) {
						if (openEverywhere) {
							barrel = Barrel.get(clickedBlock);
						}
					} else if (Barrel.isStairs(type)) {
						for (Barrel barrel2 : Barrel.barrels) {
							if (barrel2.hasStairsBlock(clickedBlock)) {
								if (openEverywhere || !barrel2.isLarge()) {
									barrel = barrel2;
								}
								break;
							}
						}
					} else if (Barrel.isFence(type) || type == Material.SIGN || type == Material.WALL_SIGN) {
						barrel = Barrel.getBySpigot(clickedBlock);
					}

					if (barrel != null) {
						event.setCancelled(true);
						if (!barrel.hasPermsOpen(player, event)) {
							return;
						}
						barrel.open(player);
					}
				}
			}
		}
	}

	
	public void setItemInHand(PlayerInteractEvent event, Material mat, boolean swapped) {
		if ((event.getHand() == EquipmentSlot.OFF_HAND) != swapped) {
			event.getPlayer().getInventory().setItemInOffHand(new ItemStack(mat));
		} else {
			event.getPlayer().getInventory().setItemInMainHand(new ItemStack(mat));
		}
	}

	@EventHandler
	public void onClickAir(PlayerInteractEvent event) {
		if (Wakeup.checkPlayer == null) return;

		if (event.getAction() == Action.LEFT_CLICK_AIR) {
			if (!event.hasItem()) {
				if (event.getPlayer() == Wakeup.checkPlayer) {
					Wakeup.tpNext();
				}
			}
		}
	}

	// player drinks a custom potion
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
		Player player = event.getPlayer();
		ItemStack item = event.getItem();
		if (item != null) {
			if (item.getType() == Material.POTION) {
				NBTItem nbti = new NBTItem(item);
				if(nbti.hasKey("brewery")) {
					BPlayer.drink(player, item);
				}
			} else if (BPlayer.drainItems.containsKey(item.getType())) {
				BPlayer bplayer = BPlayer.get(player);
				if (bplayer != null) {
					bplayer.drainByItem(player, item.getType());
				}
			}
		}
	}

	// Player has died! Decrease Drunkeness by 20
	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		BPlayer bPlayer = BPlayer.get(event.getPlayer());
		if (bPlayer != null) {
			if (bPlayer.getDrunkeness() > 20) {
				bPlayer.setData(bPlayer.getDrunkeness() - 20);
			} else {
				BPlayer.remove(event.getPlayer());
			}
		}
	}

	// player walks while drunk, push him around!
	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerMove(PlayerMoveEvent event) {
		if (BPlayer.hasPlayer(event.getPlayer()) && BPlayer.get(event.getPlayer()).isDrunkEffects()) {
			BPlayer.playerMove(event);
		}
	}

	// player talks while drunk, but he cant speak very well
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		Words.playerChat(event);
	}

	// player commands while drunk, distort chat commands
	@EventHandler(priority = EventPriority.LOWEST)
	public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
		Words.playerCommand(event);
	}

	// player joins while passed out
	@EventHandler()
	public void onPlayerLogin(PlayerLoginEvent event) {
		if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
			final Player player = event.getPlayer();
			BPlayer bplayer = BPlayer.get(player);
			if (bplayer != null) {
				if (player.hasPermission("brewery.bypass.logindeny")) {
					if (bplayer.getDrunkeness() > 100) {
						bplayer.setData(100);
					}
					bplayer.join(player);
					return;
				}
				switch (bplayer.canJoin()) {
					case 0:
						bplayer.join(player);
						return;
					case 2:
						event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Brewery.breweryDriver.languageReader.get("Player_LoginDeny"));
						return;
					case 3:
						event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Brewery.breweryDriver.languageReader.get("Player_LoginDenyLong"));
				}
			}
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		BPlayer bplayer = BPlayer.get(event.getPlayer());
		if (bplayer != null) {
			bplayer.disconnecting();
		}
	}

	@EventHandler
	public void onPlayerKick(PlayerKickEvent event) {
		BPlayer bplayer = BPlayer.get(event.getPlayer());
		if (bplayer != null) {
			bplayer.disconnecting();
		}
	}
}
