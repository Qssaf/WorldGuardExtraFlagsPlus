package dev.tins.worldguardextraflagsplus.wg.handlers;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.session.handler.FlagValueChangeHandler;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;

import dev.tins.worldguardextraflagsplus.flags.Flags;
import dev.tins.worldguardextraflagsplus.wg.WorldGuardUtils;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChamberedEnderPearlFlagHandler extends FlagValueChangeHandler<State> implements Listener
{

    // Maps player UUID to their tracked ender pearls (thrown outside flagged regions)
    private static final Multimap<UUID, Pearl> trackedPearls = HashMultimap.create();

    // Maps player UUID to whether they're currently in a flagged region
    private static final ConcurrentHashMap<UUID, Boolean> playersInFlaggedRegions = new ConcurrentHashMap<>();

	public static final Factory FACTORY()
	{
		return new Factory();
	}

    public static class Factory extends Handler.Factory<ChamberedEnderPearlFlagHandler>
    {
		@Override
        public ChamberedEnderPearlFlagHandler create(Session session)
        {
            return new ChamberedEnderPearlFlagHandler(session);
        }
    }

	protected ChamberedEnderPearlFlagHandler(Session session)
	{
		super(session, Flags.CHAMBERED_ENDERPEARL);
	}

	@Override
	protected void onInitialValue(LocalPlayer player, ApplicableRegionSet set, State value)
	{
		this.handleValue(player, player.getWorld(), value);
	}

	@Override
	protected boolean onSetValue(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet, State currentValue, State lastValue, MoveType moveType)
	{
		this.handleValue(player, (World) to.getExtent(), currentValue);
		return true;
	}

	@Override
	protected boolean onAbsentValue(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet, State lastValue, MoveType moveType)
	{
		this.handleValue(player, (World) to.getExtent(), null);
		return true;
	}

	private void handleValue(LocalPlayer player, World world, State state)
	{
		Player bukkitPlayer = ((BukkitPlayer) player).getPlayer();

		if (!WorldGuardUtils.isPluginEnabled() || !bukkitPlayer.isOnline())
		{
			return;
		}

		UUID playerUUID = bukkitPlayer.getUniqueId();
		boolean wasInFlaggedRegion = playersInFlaggedRegions.getOrDefault(playerUUID, false);
		boolean nowInFlaggedRegion = state == State.DENY;

		if (nowInFlaggedRegion != wasInFlaggedRegion)
		{
			playersInFlaggedRegions.put(playerUUID, nowInFlaggedRegion);

			// If player just entered a DENY region, kill all their cached pearls and remove from cache
			if (nowInFlaggedRegion && !wasInFlaggedRegion)
			{
				Collection<Pearl> pearls = trackedPearls.asMap().get(playerUUID);
				if (pearls != null)
				{
					for (Pearl pearl : pearls)
					{
						EnderPearl enderPearl = pearl.pearl.get();
						if (enderPearl != null)
						{
							// Kill the pearl entity immediately
							try
							{
								if (!enderPearl.isDead())
								{
									enderPearl.remove();
								}
							}
							catch (Exception e)
							{
								// Pearl might be in unloaded chunk or invalid, try scheduled removal
								WorldGuardUtils.getScheduler().runNextTick(task -> {
									try {
										if (enderPearl != null && !enderPearl.isDead()) {
											enderPearl.remove();
										}
									} catch (Exception ex) {
										// Ignore - pearl is probably already gone
									}
								});
							}
						}
					}
				}
				trackedPearls.removeAll(playerUUID);
			}
		}
	}

	// Static methods for global pearl tracking

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public static void onProjectileLaunch(ProjectileLaunchEvent event) {
		if (event.getEntityType() != EntityType.ENDER_PEARL) {
			return;
		}

		final EnderPearl enderPearl = (EnderPearl) event.getEntity();

		if (!(enderPearl.getShooter() instanceof Player player)) {
			return;
		}

		UUID playerUUID = player.getUniqueId();

		// Only track pearls if player is NOT in a flagged region
		if (playersInFlaggedRegions.getOrDefault(playerUUID, false)) {
			return;
		}

			// Add pearl to cache when thrown
		trackedPearls.put(playerUUID, new Pearl(enderPearl));

		// Periodic cleanup of invalid pearls (every ~10 pearls to avoid performance impact)
		if (trackedPearls.size() % 10 == 0) {
			cleanupInvalidPearls();
		}
	}

	@EventHandler
	public static void onProjectileHit(ProjectileHitEvent event) {
		if (!(event.getEntity() instanceof EnderPearl enderPearl)) {
			return;
		}

		if (!(enderPearl.getShooter() instanceof Player player)) {
			return;
		}

		UUID playerUUID = player.getUniqueId();
		final Collection<Pearl> pearls = trackedPearls.asMap().get(playerUUID);

		if (pearls == null || pearls.isEmpty()) {
			return;
		}

		final Iterator<Pearl> iterator = pearls.iterator();

		while (iterator.hasNext()) {
			final Pearl pearl = iterator.next();

			if (enderPearl.equals(pearl.pearl.get())) {
				iterator.remove();
				break;
			}
		}
	}

	@EventHandler
	public static void onEntityDeath(EntityDeathEvent event) {
		if (!(event.getEntity() instanceof EnderPearl enderPearl)) {
			return;
		}

		if (!(enderPearl.getShooter() instanceof Player player)) {
			return;
		}

		UUID playerUUID = player.getUniqueId();
		final Collection<Pearl> pearls = trackedPearls.asMap().get(playerUUID);

		if (pearls == null || pearls.isEmpty()) {
			return;
		}

		final Iterator<Pearl> iterator = pearls.iterator();

		while (iterator.hasNext()) {
			final Pearl pearl = iterator.next();

			if (enderPearl.equals(pearl.pearl.get())) {
				iterator.remove();
				break;
			}
		}
	}

	// Periodic cleanup of invalid pearls (run occasionally)
	public static void cleanupInvalidPearls() {
		for (UUID playerUUID : trackedPearls.keySet()) {
			Collection<Pearl> pearls = trackedPearls.asMap().get(playerUUID);
			if (pearls != null) {
				pearls.removeIf(pearl -> {
					EnderPearl enderPearl = pearl.pearl.get();
					return enderPearl == null || !enderPearl.isValid() || enderPearl.isDead();
				});
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public static void onPlayerTeleport(PlayerTeleportEvent event) {
		if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
			return;
		}

		Player player = event.getPlayer();
		UUID playerUUID = player.getUniqueId();

		// If player is not in a flagged region, allow the teleport
		if (!playersInFlaggedRegions.getOrDefault(playerUUID, false)) {
			return;
		}

		// Check if this teleport is from a tracked pearl
		final Collection<Pearl> pearls = trackedPearls.asMap().get(playerUUID);

		if (pearls == null || pearls.isEmpty()) {
			return;
		}

		// Find the pearl that caused this teleport
		for (Pearl pearl : pearls) {
			EnderPearl enderPearl = pearl.pearl.get();
			if (enderPearl != null && enderPearl.isValid() && !enderPearl.isDead()) {
				// This is a tracked pearl causing teleportation while player is in flagged region
				event.setCancelled(true);

				// Remove the pearl safely using Folia-compatible scheduler
				final EnderPearl pearlToRemove = enderPearl;
				WorldGuardUtils.getScheduler().runNextTick(task -> {
					if (pearlToRemove.isValid() && !pearlToRemove.isDead()) {
						pearlToRemove.remove();
					}
				});
				break;
			}
		}
	}

	@EventHandler
	public static void onPlayerQuit(PlayerQuitEvent event) {
		UUID playerUUID = event.getPlayer().getUniqueId();
		trackedPearls.asMap().remove(playerUUID);
		playersInFlaggedRegions.remove(playerUUID);
	}

	private static class Pearl {
		private final WeakReference<EnderPearl> pearl;

		public Pearl(final EnderPearl pearl) {
			this.pearl = new WeakReference<>(pearl);
		}
	}
}
