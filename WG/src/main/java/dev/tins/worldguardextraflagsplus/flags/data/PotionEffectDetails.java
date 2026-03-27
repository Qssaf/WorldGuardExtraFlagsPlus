package dev.tins.worldguardextraflagsplus.flags.data;

import java.util.concurrent.TimeUnit;

public class PotionEffectDetails
{
	private long endTime;
	private int amplifier;
	private boolean ambient;
	private boolean particles;

	public PotionEffectDetails() {
	}

	public PotionEffectDetails(long endTime, int amplifier, boolean ambient, boolean particles) {
		this.endTime = endTime;
		this.amplifier = amplifier;
		this.ambient = ambient;
		this.particles = particles;
	}

	public long getEndTime() {
		return endTime;
	}

	public int getAmplifier() {
		return amplifier;
	}

	public boolean isAmbient() {
		return ambient;
	}

	public boolean isParticles() {
		return particles;
	}
	
	public long getTimeLeft()
	{
		return (this.endTime - System.nanoTime());
	}
	
	public int getTimeLeftInTicks()
	{
		return (int)(this.getTimeLeft() / TimeUnit.MILLISECONDS.toNanos(50L));
	}
}



