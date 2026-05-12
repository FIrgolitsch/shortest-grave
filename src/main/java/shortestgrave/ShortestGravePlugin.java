package shortestgrave;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Shortest Grave"
)
public class ShortestGravePlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private EventBus eventBus;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ShortestGraveConfig config;

	private Player currentPlayer;

	private WorldPoint pendingGraveLocation;

	private int ticksUntilPath = -1;

	private boolean gravestoneSpawned;

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath)
	{
		Actor actor = actorDeath.getActor();
		if (actor instanceof Player && actor.equals(currentPlayer))
		{
			// Store death location and start countdown
			pendingGraveLocation = this.currentPlayer.getWorldLocation();
			ticksUntilPath = this.config.pathDelay();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			if (this.currentPlayer == null)
			{
				// Keep trying to set the current player until sucess.
				// localPlayer is not set immediatly after GameSate switches to LOGGED_IN.
				clientThread.invokeLater(() ->
				{
					this.currentPlayer = this.client.getLocalPlayer();
					return this.currentPlayer != null;
				});
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		// Only set gravestone spawned if the message comes form the game and there is still a path to be planned.
		// Prevents planning another route after the gravestone is emptied.
		if (chatMessage.getMessage().toLowerCase().contains("gravestone") &&
			this.pendingGraveLocation != null &&
			chatMessage.getType() == ChatMessageType.GAMEMESSAGE)
		{
			// Must be set before the countdown has reached zero.
			// See pathDelay in config
			this.gravestoneSpawned = true;
		}
	}


	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		// Wait until player has respawned.
		if (this.ticksUntilPath > 0)
		{
			this.ticksUntilPath--;
			if (this.ticksUntilPath == 0)
			{
				// Only spawn when the gravestone has spawned or the user has overriden the behaviour.
				if (this.config.alwaysPath() || this.gravestoneSpawned)
				{
					this.ticksUntilPath = -1;
					this.afterDeath(pendingGraveLocation);
				}
			}
		}
	}

	@Provides
	public ShortestGraveConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ShortestGraveConfig.class);
	}

	/**
	 * Delayed function after death.
	 *
	 * @param graveLocation The location where the player has died.
	 */
	private void afterDeath(WorldPoint graveLocation)
	{
		WorldPoint respawnLocation = this.client.getLocalPlayer().getWorldLocation();
		sendShortestPathMessage(respawnLocation, graveLocation);
	}

	/**
	 * Send a path to Shortest Path with a custom color.
	 *
	 * @param startPoint The start point of the path
	 * @param endPoint   The end point of the path (the gravestone is this case)
	 */
	private void sendShortestPathMessage(WorldPoint startPoint, WorldPoint endPoint)
	{
		Map<String, Object> shortestPathMessage = new HashMap<>();

		shortestPathMessage.put("start", startPoint);
		shortestPathMessage.put("target", endPoint);

		// Don't bother to send a null object to shortest path when no colour is set in the config.
		if (this.config.colourPath() != null)
		{
			Map<String, Object> configOverride = new HashMap<>();
			configOverride.put("colourPath", this.config.colourPath());
			shortestPathMessage.put("config", configOverride);
		}

		eventBus.post(new PluginMessage("shortestpath", "path", shortestPathMessage));
		this.pendingGraveLocation = null;
		this.gravestoneSpawned = false;
	}
}
