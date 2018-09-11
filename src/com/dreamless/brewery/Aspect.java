package com.dreamless.brewery;

import java.util.HashMap;

public class Aspect {
	//Static Numbers for balancing
	public static double commonPotency = 6;
	public static double uncommonPotency = 20;
	public static double superiorPotency = 42;
	public static double rarePotency = 64;
	public static double legendaryPotency = 100;
	public static double commonSaturation = 0.2;
	public static double uncommonSaturation = 0.4;
	public static double superiorSaturation = 0.6;
	public static double rareSaturation = 0.8;
	public static double legendarySaturation = 1.0;
	
	public static HashMap<String, double[]>aspectStageMultipliers = new HashMap<String, double[]>();
	
	//Private
	private String name;
	private double potency = 0;
	private double saturation = 0.0;
	
	public Aspect(String name) {
		this.name = name;
	}
	
	public Aspect (String name, double potency, double saturation) {
		this.name = name;
		this.potency = potency;
		this.saturation = saturation;
	}

	public double getPotency() {
		return potency;
	}

	public void setPotency(double potency) {
		this.potency = potency;
	}

	public double getSaturation() {
		return saturation;
	}

	public void setSaturation(double saturation) {
		this.saturation = saturation;
	}

	public String getName() {
		return name;
	}
	
	public String toString() {
		return "Name: " + name + " Potency: " + potency + " Saturation: " + saturation;
	}
	
	public static double calculateRarityPotency(String rarity){
		switch(rarity) {
			case ("COMMON"):
				return commonPotency;
			case ("UNCOMMON"):
				return uncommonPotency;
			case ("SUPERIOR"):
				return superiorPotency;
			case ("RARE"):
				return rarePotency;
			case ("LEGENDARY"):
				return legendaryPotency;
			default:
				return commonPotency;
		}
	}
	
	public static double calculateRaritySaturation(String rarity){
		switch(rarity) {
		case ("COMMON"):
			return commonSaturation;
		case ("UNCOMMON"):
			return uncommonSaturation;
		case ("SUPERIOR"):
			return superiorSaturation;
		case ("RARE"):
			return rareSaturation;
		case ("LEGENDARY"):
			return legendarySaturation;
		default:
			return commonSaturation;
	}
	}
}
