package thebetweenlands.common.capability.decay;

public interface IDecayCapability {
	/**
	 * Returns the decay stats
	 * @return
	 */
	public DecayStats getDecayStats();

	/**
	 * Returns the maximum player health with the current decay level
	 * @return
	 */
	public float getMaxPlayerHealth();

	/**
	 * Returns whether decay is currently enabled for this player
	 * @return
	 */
	public boolean isDecayEnabled();
}