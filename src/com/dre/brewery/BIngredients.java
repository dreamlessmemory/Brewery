package com.dre.brewery;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class BIngredients {
	public static Set<Material> possibleIngredients = new HashSet<Material>();
	public static ArrayList<BRecipe> recipes = new ArrayList<BRecipe>();
	public static Map<Material, String> cookedNames = new HashMap<Material, String>();
	private static int lastId = 0;
	private static final float AGE_DIFF_SCALE = 1.5f;
	private static final float INGREDIENT_MULTIPLIER = 2.0f;
	private static final float COOKING_MULTIPLIER = 1.0f;
	private static final float WOOD_MULTIPLIER = 1.0f;
	private static final float AGE_MULTIPLIER = 2.0f;

	private int id;
	private ArrayList<ItemStack> ingredients = new ArrayList<ItemStack>();
	private Map<Material, Integer> materials = new HashMap<Material, Integer>(); // Merged List Of ingredients that doesnt consider Durability
	private int cookedTime;

	// Represents ingredients in Cauldron, Brew
	// Init a new BIngredients
	public BIngredients() {
		this.id = lastId;
		lastId++;
	}

	// Load from File
	public BIngredients(ArrayList<ItemStack> ingredients, int cookedTime) {
		this.ingredients = ingredients;
		this.cookedTime = cookedTime;
		this.id = lastId;
		lastId++;

		for (ItemStack item : ingredients) {
			addMaterial(item);
		}
	}

	// Add an ingredient to this
	public void add(ItemStack ingredient) {
		addMaterial(ingredient);
		for (ItemStack item : ingredients) {
			if (item.isSimilar(ingredient)) {
				item.setAmount(item.getAmount() + ingredient.getAmount());
				return;
			}
		}
		ingredients.add(ingredient);
	}

	private void addMaterial(ItemStack ingredient) {
		if (materials.containsKey(ingredient.getType())) {
			int newAmount = materials.get(ingredient.getType()) + ingredient.getAmount();
			materials.put(ingredient.getType(), newAmount);
		} else {
			materials.put(ingredient.getType(), ingredient.getAmount());
		}
	}

	// returns an Potion item with cooked ingredients
	public ItemStack cook(int state) {

		ItemStack potion = new ItemStack(Material.POTION);
		PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();

		// cookedTime is always time in minutes, state may differ with number of ticks
		cookedTime = state;
		String cookedName = null;
		BRecipe cookRecipe = getCookRecipe();

		int uid = Brew.generateUID();

		if (cookRecipe != null) {
			// Potion is best with cooking only
			int quality = (int) Math.round((getIngredientQuality(cookRecipe) + getCookingQuality(cookRecipe, false)) / 2.0);
			P.p.debugLog("cooked potion has Quality: " + quality);
			Brew brew = new Brew(uid, quality, cookRecipe, this);
			Brew.addOrReplaceEffects(potionMeta, brew.getEffects(), brew.getQuality());
			brew.convertLore(potionMeta, false);

			cookedName = cookRecipe.getName(quality);
			Brew.PotionColor.valueOf(cookRecipe.getColor()).colorBrew(potionMeta, potion, false);

		} else {
			// new base potion
			new Brew(uid, this);

			if (state <= 1) {
				cookedName = P.p.languageReader.get("Brew_ThickBrew");
				Brew.PotionColor.BLUE.colorBrew(potionMeta, potion, false);
			} else {
				//Get the cooked name
				Material highest = null;
				int highestCount = 0;
				//Search for the cooked Name
				for (Material ingredient : materials.keySet()) {
					if (cookedNames.containsKey(ingredient)) {
						if(materials.get(ingredient) > highestCount) {
							highest = ingredient;
							highestCount = materials.get(ingredient);
						}
					}
				}
				//Assign a Name if found
				if (highest != null) {
					cookedName = cookedNames.get(highest);
					Brew.PotionColor.CYAN.colorBrew(potionMeta, potion, true);
				}
			}
		}
		if (cookedName == null) {// if no name could be found
			cookedName = P.p.languageReader.get("Brew_Undefined");
			Brew.PotionColor.CYAN.colorBrew(potionMeta, potion, true);
		}
		//Set Display Name
		potionMeta.setDisplayName(P.p.color("&f" + cookedName));
		// This effect stores the UID in its Duration
		potionMeta.addCustomEffect((PotionEffectType.REGENERATION).createEffect((uid * 4), 0), true);
		potion.setItemMeta(potionMeta);

		return potion;
	}

	// returns amount of ingredients
	private int getIngredientsCount() {
		int count = 0;
		for (ItemStack item : ingredients) {
			count += item.getAmount();
		}
		return count;
	}

	/*public Map<Material, Integer> getIngredients() {
		return ingredients;
	}*/

	public int getCookedTime() {
		return cookedTime;
	}

	public BRecipe getBestRecipe() { //Cooked Only
		float quality = 0;
		int ingredientQuality;
		int cookingQuality;
		BRecipe bestRecipe = null;
		for (BRecipe recipe : recipes) {
			ingredientQuality = getIngredientQuality(recipe);
			cookingQuality = getCookingQuality(recipe);
			
			//If correct ingredients and timing
			if (ingredientQuality > -1 && cookingQuality > -1) {
				P.p.debugLog("Ingredient Quality: " + ingredientQuality + " Cooking Quality: " + cookingQuality + " for " + recipe.getName(5));
	
				// is this recipe better than the previous best?
				float testQuality = getQualityScore(ingredientQuality, cookingQuality);
				//System.out.println("[Brewery] (Simple) Testing - I: " + ingredientQuality + " C: " + cookingQuality + " total: " + testQuality + "/30.0 for " + recipe.getName(5));
				if (testQuality > quality) {
					//System.out.println("[Brewery] (Simple) New Pick: " + recipe.getName(5) + " " + testQuality);
					quality = testQuality;
					bestRecipe = recipe;
				}
			}
		}
		if (bestRecipe != null) {
			P.p.debugLog("best recipe: " + bestRecipe.getName(5) + " has Quality= " + quality);
			//System.out.println("[Brewery] Best Recipe (Simple): " + bestRecipe.getName(5)); 
		} 
		return bestRecipe;
	}

	// best recipe for current state of potion, STILL not always returns the
	// correct one...
	public BRecipe getBestRecipe(float wood, float time, boolean distilled) {
		float quality = 0;
		int ingredientQuality;
		int cookingQuality;
		int woodQuality = 0;
		int ageQuality = 0;
		BRecipe bestRecipe = null;
		for (BRecipe recipe : recipes) {
			ingredientQuality = getIngredientQuality(recipe);
			cookingQuality = getCookingQuality(recipe, distilled);
			
			//If fermentation is close enough
			if (ingredientQuality > -1 && cookingQuality > -1) {
				if (recipe.needsToAge() || time > 0.5) {
					// needs Aging in barrel
					ageQuality = getAgeQuality(recipe, time);
					woodQuality = getWoodQuality(recipe, wood);
				}
				P.p.debugLog("Ingredient Quality: " + ingredientQuality + " Cooking Quality: " + cookingQuality + " for " + recipe.getName(5));
				
				// is this recipe better than the previous best?
				float testQuality = getQualityScore(ingredientQuality, cookingQuality, ageQuality, woodQuality, recipe.needsToAge());
				//System.out.println("[Brewery] (Full) Testing - I: " + ingredientQuality + " C: " + cookingQuality + " A: " + ageQuality + " W: " + woodQuality +" T: " + testQuality + "/60.0 for " + recipe.getName(5));
				if (testQuality > quality) {
					//System.out.println("[Brewery] (Full) New Pick: " + recipe.getName(5) + " " + testQuality);
					quality = testQuality;
					bestRecipe = recipe;
				}
				/*//If needs to Age or has aged for 10 mins
				if (recipe.needsToAge() || time > 0.5) {
					// needs Aging in barrel
					ageQuality = getAgeQuality(recipe, time);
					woodQuality = getWoodQuality(recipe, wood);
					P.p.debugLog("Ingredient Quality: " + ingredientQuality + " Cooking Quality: " + cookingQuality +
						" Wood Quality: " + woodQuality + " age Quality: " + ageQuality + " for " + recipe.getName(5));

					// is this recipe better than the previous best?
					if ((((float) ingredientQuality + cookingQuality + woodQuality + ageQuality) / 4) > quality) {
						quality = ((float) ingredientQuality + cookingQuality + woodQuality + ageQuality) / 4;
						bestRecipe = recipe;
					}
				} else {
					P.p.debugLog("Ingredient Quality: " + ingredientQuality + " Cooking Quality: " + cookingQuality + " for " + recipe.getName(5));
					// calculate quality without age and barrel
					if ((((float) ingredientQuality + cookingQuality) / 2) > quality) {
						quality = ((float) ingredientQuality + cookingQuality) / 2;
						bestRecipe = recipe;
					}
				}*/
			}
		}
		if (bestRecipe != null) {
			P.p.debugLog("best recipe: " + bestRecipe.getName(5) + " has Quality= " + quality);
			System.out.println("[Brewery] Best Recipe (Full): " + bestRecipe.getName(5));
		}
		return bestRecipe;
	}
	
	private float getQualityScore(int ingredientQuality, int cookingQuality, int woodQuality, int ageQuality, boolean age) {
		float score = 0;
		score += ingredientQuality * INGREDIENT_MULTIPLIER;
		score += cookingQuality * COOKING_MULTIPLIER;
		if(age) {
			score += woodQuality * WOOD_MULTIPLIER;
			score += ageQuality * AGE_MULTIPLIER;
		} else {
			score *=2;
		}
		return score;
	}

	private float getQualityScore(int ingredientQuality, int cookingQuality) {
		float score = 0;
		score += ingredientQuality * INGREDIENT_MULTIPLIER;
		score += cookingQuality * COOKING_MULTIPLIER;
		return score;
	}

	// returns recipe that is cooking only and matches the ingredients and
	// cooking time
	public BRecipe getCookRecipe() {
		BRecipe bestRecipe = getBestRecipe();

		// Check if best recipe is cooking only
		if (bestRecipe != null) {
			if (bestRecipe.isCookingOnly()) {
				return bestRecipe;
			}
		}
		return null;
	}

	// returns the currently best matching recipe for distilling for the
	// ingredients and cooking time
	public BRecipe getDistillRecipe(float wood, float time) {
		BRecipe bestRecipe = getBestRecipe(wood, time, true);

		// Check if best recipe needs to be distilled
		if (bestRecipe != null) {
			if (bestRecipe.needsDistilling()) {
				return bestRecipe;
			}
		}
		return null;
	}

	// returns currently best matching recipe for ingredients, cooking- and
	// ageingtime
	public BRecipe getAgeRecipe(float wood, float time, boolean distilled) {
		BRecipe bestRecipe = getBestRecipe(wood, time, distilled);

		if (bestRecipe != null) {
			if (bestRecipe.needsToAge()) {
				return bestRecipe;
			}
		}
		return null;
	}

	// returns the quality of the ingredients conditioning given recipe, -1 if
	// no recipe is near them
	public int getIngredientQuality(BRecipe recipe) {
		float quality = 10;
		int count;
		int badStuff = 0;
		if (recipe.isMissingIngredients(ingredients)) {
			// when ingredients are not complete
			return -1;
		}
		ArrayList<Material> mergedChecked = new ArrayList<Material>();
		for (ItemStack ingredient : ingredients) {
			if (mergedChecked.contains(ingredient.getType())) {
				// This ingredient type was already checked as part of a merged material
				continue;
			}
			int amountInRecipe = recipe.amountOf(ingredient);
			// If we dont consider durability for this ingredient, check the merged material
			if (recipe.hasExactData(ingredient)) {
				count = ingredient.getAmount();
			} else {
				mergedChecked.add(ingredient.getType());
				count = materials.get(ingredient.getType());
			}
			if (amountInRecipe == 0) {
				// this ingredient doesnt belong into the recipe
				if (count > (getIngredientsCount() / 2)) {
					// when more than half of the ingredients dont fit into the
					// recipe
					return -1;
				}
				badStuff++;
				if (badStuff < ingredients.size()) {
					// when there are other ingredients
					quality -= count * (recipe.getDifficulty() / 2);
					continue;
				} else {
					// ingredients dont fit at all
					return -1;
				}
			}
			// calculate the quality
			quality -= ((float) Math.abs(count - amountInRecipe) / recipe.allowedCountDiff(amountInRecipe)) * 10.0;
		}
		if (quality >= 0) {
			return Math.round(quality);
		}
		return -1;
	}
	
	public int[] getIngredientMismatch(BRecipe recipe) {
		int[] parameters = {0, 0, 0, 0}; //Over, Under, Incorrect, Missing
		for (ItemStack ingredient : ingredients) {
			int parity = ingredient.getAmount() - recipe.amountOf(ingredient);
			if(recipe.amountOf(ingredient) == 0) {
				parameters[2]++;
			} else if(parity > 0) {
				parameters[0]++;
			} else if (parity < 0) {
				parameters[1]++;
			}
		}
		parameters[3] = recipe.countMissingIngredients(ingredients);
		//Code here
		return parameters;
	}
	
	public String getIngredientMismatchString(BRecipe recipe) {
		String output = "";
		List<Integer> extra = new ArrayList<Integer>();
		List<Integer> missing = new ArrayList<Integer>();
		int unnecessary = 0;
		for (ItemStack ingredient : ingredients) {
			int parity = ingredient.getAmount() - recipe.amountOf(ingredient);
			if(recipe.amountOf(ingredient) == 0) {//unnecessary
				unnecessary++;
			} else if(parity > 0) {//extra
				extra.add(parity);
			} else if (parity < 0) {//missing
				missing.add(Math.abs(parity));
			}
		}
		if(!extra.isEmpty()) {
			Collections.sort(extra);
			output = output.concat(" +(");
			for(int i = 0; i < extra.size(); i++) {
				output = output.concat(extra.get(i).toString());
				if (i < extra.size() -1) {
					output = output.concat(", ");
				}
			}
			output = output.concat(")");
		}
		if(!missing.isEmpty()) {
			Collections.sort(missing);
			output = output.concat(" -(");
			for(int i = 0; i < missing.size(); i++) {
				output = output.concat(missing.get(i).toString());
				if (i < missing.size() - 1) {
					output = output.concat(", ");
				}
			}
			output = output.concat(")");
		}
		if(unnecessary > 0) {
			output = output.concat(" *" + unnecessary);
		}
		return output;
	}

	// returns the quality regarding the cooking-time conditioning given Recipe
	public int getCookingQuality(BRecipe recipe, boolean distilled) {
		if (!recipe.needsDistilling() == distilled) {
			return -1;
		}
		int quality = 10 - (int) Math.round(((float) Math.abs(cookedTime - recipe.getCookingTime()) / recipe.allowedTimeDiff(recipe.getCookingTime())) * 10.0);

		if (quality >= 0) {
			if (cookedTime <= 1) {
				return 0;
			}
			return quality;
		}
		return -1;
	}
	//Only Based on Time
	public int getCookingQuality(BRecipe recipe) {
		int quality = 10 - (int) Math.round(((float) Math.abs(cookedTime - recipe.getCookingTime()) / recipe.allowedTimeDiff(recipe.getCookingTime())) * 10.0);
		if (quality >= 0) {
			if (cookedTime <= 1) {
				return 0;
			}
			return quality;
		}
		return -1;
	}

	// returns pseudo quality of distilling. 0 if doesnt match the need of the recipes distilling
	public int getDistillQuality(BRecipe recipe, int distillRuns) {
		if (recipe.needsDistilling() != distillRuns > 0) {
			return 0;
		}
		return 10 - Math.abs(recipe.getDistillRuns() - distillRuns);
	}

	// returns the quality regarding the barrel wood conditioning given Recipe
	public int getWoodQuality(BRecipe recipe, float wood) {
		if (recipe.getWood() == 0) {
			// type of wood doesnt matter
			return 10;
		}
		int quality = 10 - Math.round(recipe.getWoodDiff(wood) * recipe.getDifficulty());

		if (quality > 0) {
			return quality;
		}
		return 0;
	}

	// returns the quality regarding the ageing time conditioning given Recipe
	public int getAgeQuality(BRecipe recipe, float time) {
		int quality = 10 - Math.round(Math.abs(time - recipe.getAge()) * ((float) recipe.getDifficulty() / AGE_DIFF_SCALE));

		if (quality > 0) {
			return quality;
		}
		return 0;
	}

	// Creates a copy ingredients
	public BIngredients clone() {
		BIngredients copy = new BIngredients();
		copy.ingredients.addAll(ingredients);
		copy.materials.putAll(materials);
		copy.cookedTime = cookedTime;
		return copy;
	}

	// saves data into main Ingredient section. Returns the save id
	public int save(ConfigurationSection config) {
		String path = "Ingredients." + id;
		if (cookedTime != 0) {
			config.set(path + ".cookedTime", cookedTime);
		}
		config.set(path + ".mats", serializeIngredients());
		return id;
	}

	//convert the ingredient Material to String
	public Map<String, Integer> serializeIngredients() {
		Map<String, Integer> mats = new HashMap<String, Integer>();
		for (ItemStack item : ingredients) {
			String mat = item.getType().name() + "," + item.getDurability();
			mats.put(mat, item.getAmount());
		}
		return mats;
	}
	
	public String getContents() {
		String manifest = " ";
		for (ItemStack item: ingredients) {
			///System.out.println(item.getType().toString() + ": " + item.getAmount());
			String itemName = "";
			for(String part: item.getType().toString().split("_")) {
				itemName = itemName.concat(part.substring(0, 1) + part.substring(1).toLowerCase()+  " ");
			}
			manifest = manifest.concat(itemName + "x" + item.getAmount() + " - ");
		}
		return manifest.substring(0, manifest.length() - 3);
	}

}