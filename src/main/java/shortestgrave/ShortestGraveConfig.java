package shortestgrave;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("shortestgrave")
public interface ShortestGraveConfig extends Config
{
	@Alpha
	@ConfigItem(
		keyName = "gravePathColour",
		name = "Grave Path Colour",
		description = "The colour to use for the path to the grave. Defaults to regular Shortest Path colour.",
		position = 0
	)
	default Color colourPath()
	{
		return null;
	}

	@ConfigItem(
		keyName = "alwaysPath",
		name = "Always Show Path",
		description = "Always path to the location of death, even without gravestone.",
		position = 1
	)
	default boolean alwaysPath()
	{
		return false;
	}

	@ConfigSection(
		name = "Debug",
		description = "Debug options.",
		position = 2,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	@ConfigItem(
		keyName = "pathDelay",
		name = "Path Delay",
		description = "If the path does not get planned, you can try and increase this to compensate for latency.<br>" +
			"Reduce this too much and the path will be auto-completed, as your still at your grave location.",
		position = 3,
		warning = "Only change this if you have issues with the path not being planned.",
		section = debugSection
	)
	default int pathDelay()
	{
		return 9;
	}
}
