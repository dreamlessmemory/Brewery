package com.dreamless.brewery;

public class Aspect {
	private String name;
	private int potency = 0;
	private double saturation = 0.0;
	
	public Aspect(String name) {
		this.name = name;
	}
	
	public Aspect (String name, int potency, double saturation) {
		this.name = name;
		this.potency = potency;
		this.saturation = saturation;
	}

	public int getPotency() {
		return potency;
	}

	public void setPotency(int potency) {
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
}
