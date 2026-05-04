package dev.tins.worldguardextraflagsplus.flags.helpers;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;

/**
 * Allowed entries for {@code disable-throw} — throwable consumables only (egg, snowball, pearl, XP bottle).
 * Trident, wind charge, etc. remain on {@code disable-completely}.
 */
public final class ThrowableItemFlag extends Flag<String>
{
	private static final Set<String> THROWABLE_ITEMS = new HashSet<>();

	static
	{
		THROWABLE_ITEMS.add("EGG");
		THROWABLE_ITEMS.add("SNOWBALL");
		THROWABLE_ITEMS.add("ENDER_PEARL");
		THROWABLE_ITEMS.add("EXPERIENCE_BOTTLE");
	}

	public ThrowableItemFlag(String name)
	{
		super(name);
	}

	public static Set<String> getThrowableMaterials()
	{
		return Collections.unmodifiableSet(THROWABLE_ITEMS);
	}

	public static boolean isThrowableMaterial(String materialName)
	{
		return materialName != null && THROWABLE_ITEMS.contains(materialName.toUpperCase());
	}

	public static boolean isThrowableMaterial(Material material)
	{
		return material != null && material != Material.AIR && isThrowableMaterial(material.name());
	}

	@Override
	public Object marshal(String o)
	{
		return o;
	}

	@Override
	public String parseInput(FlagContext context) throws InvalidFlagFormat
	{
		String input = context.getUserInput().trim();
		if (input.isEmpty())
		{
			throw new InvalidFlagFormat("Item name cannot be empty");
		}
		String upperInput = input.toUpperCase();
		if (!THROWABLE_ITEMS.contains(upperInput))
		{
			throw new InvalidFlagFormat("Invalid item '" + input + "'. Allowed throwables: " + String.join(", ", THROWABLE_ITEMS));
		}
		return upperInput;
	}

	@Override
	public String unmarshal(Object o)
	{
		if (o instanceof String)
		{
			String item = ((String) o).toUpperCase();
			if (THROWABLE_ITEMS.contains(item))
			{
				return item;
			}
		}
		return null;
	}
}
