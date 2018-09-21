package com.dreamless.brewery.listeners;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.dreamless.brewery.*;
import com.dreamless.brewery.filedata.UpdateChecker;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


public class PlayerListener implements Listener {
	public static boolean openEverywhere;
	private static Set<UUID> interacted = new HashSet<UUID>();

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

						if (materialInHand == null || materialInHand == Material.BUCKET) {
							return;

						} else if (materialInHand == Material.CLOCK) {
							if(BCauldron.isCooking(clickedBlock)) {
								BCauldron.printTime(player, clickedBlock);
							} else {
								BCauldron.setCooking(clickedBlock, true);
								clickedBlock.getWorld().playSound(clickedBlock.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.0f, 1.0f);
							}	
							return;
						} else if (materialInHand == Material.IRON_SHOVEL) {
							BCauldron.printContents(player, clickedBlock);
							return;
						} else if (materialInHand == Material.GLASS_BOTTLE) { // fill a glass bottle with potion
							if (player.getInventory().firstEmpty() != -1 || item.getAmount() == 1) {
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

						// Check if fire alive below cauldron when adding ingredients
						Block down = clickedBlock.getRelative(BlockFace.DOWN);
						if ((down.getType() == Material.FIRE || down.getType() == Material.MAGMA_BLOCK || down.getType() == Material.LAVA) && !BCauldron.isCooking(clickedBlock)) {

							event.setCancelled(true);
							boolean handSwap = false;

							// Interact event is called twice!!!?? in 1.9, once for each hand.
							// Certain Items in Hand cause one of them to be cancelled or not called at all sometimes.
							// We mark if a player had the event for the main hand
							// If not, we handle the main hand in the event for the off hand
							if (P.use1_9) {
								if (event.getHand() == EquipmentSlot.HAND) {
									final UUID id = player.getUniqueId();
									interacted.add(id);
									P.p.getServer().getScheduler().runTask(P.p, new Runnable() {
										@Override
										public void run() {
											interacted.remove(id);
										}
									});
								} else if (event.getHand() == EquipmentSlot.OFF_HAND) {
									if (!interacted.remove(player.getUniqueId())) {
										item = player.getInventory().getItemInMainHand();
										if (item != null && item.getType() != Material.AIR) {
											materialInHand = item.getType();
											handSwap = true;
										} else {
											item = event.getItem();
										}
									}
								}
							}
							if (item == null) return;

							// add ingredient to cauldron that meet the previous conditions
							if (BIngredients.acceptableIngredients.contains(materialInHand)) {

								if (player.hasPermission("brewery.cauldron.insert")) {
									if (BCauldron.ingredientAdd(clickedBlock, item)) {
										boolean isBucket = item.getType().equals(Material.WATER_BUCKET)
												|| item.getType().equals(Material.LAVA_BUCKET)
												|| item.getType().equals(Material.MILK_BUCKET);
										if (item.getAmount() > 1) {
											item.setAmount(item.getAmount() - 1);

											if (isBucket) {
												BCauldron.giveItem(player, new ItemStack(Material.BUCKET));
											}
										} else {
											if (isBucket) {
												setItemInHand(event, Material.BUCKET, handSwap);
											} else {
												setItemInHand(event, Material.AIR, handSwap);
											}
										}
									}
									clickedBlock.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, clickedBlock.getLocation().getX() + 0.5, clickedBlock.getLocation().getY() + 1.5, clickedBlock.getLocation().getZ() + 0.5, 10, 0.5, 0.5, 0.5);
								} else {
									P.p.msg(player, P.p.languageReader.get("Perms_NoCauldronInsert"));
								}
							}
						}
						return;
					}

					if (P.use1_9 && event.getHand() != EquipmentSlot.HAND) {
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
				Brew brew = Brew.get(item);
				if (brew != null) {
					BPlayer.drink(brew, player);
					if (player.getGameMode() != GameMode.CREATIVE) {
						brew.remove(item);
					}
					if (P.use1_9) {
						if (player.getGameMode() != GameMode.CREATIVE) {
							// replace the potion with an empty potion to avoid effects
							event.setItem(new ItemStack(Material.POTION));
						} else {
							// Dont replace the item when keeping the potion, just cancel the event
							event.setCancelled(true);
						}
					}
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
				bPlayer.setData(bPlayer.getDrunkeness() - 20, 0);
			} else {
				BPlayer.remove(event.getPlayer());
			}
		}
	}

	// player walks while drunk, push him around!
	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerMove(PlayerMoveEvent event) {
		if (BPlayer.hasPlayer(event.getPlayer())) {
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
						bplayer.setData(100, 0);
					}
					bplayer.join(player);
					return;
				}
				switch (bplayer.canJoin()) {
					case 0:
						bplayer.join(player);
						return;
					case 2:
						event.disallow(PlayerLoginEvent.Result.KICK_OTHER, P.p.languageReader.get("Player_LoginDeny"));
						return;
					case 3:
						event.disallow(PlayerLoginEvent.Result.KICK_OTHER, P.p.languageReader.get("Player_LoginDenyLong"));
				}
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerJoin(PlayerJoinEvent event) {
		UpdateChecker.notify(event.getPlayer());
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
