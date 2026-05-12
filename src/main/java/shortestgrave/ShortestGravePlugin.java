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
		if (chatMessage.getMessage().toLowerCase().contains("gravestone") &&
			this.pendingGraveLocation != null &&
			chatMessage.getType() == ChatMessageType.GAMEMESSAGE)
		{
			this.gravestoneSpawned = true;
		}
	}


	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (this.ticksUntilPath > 0)
		{
			this.ticksUntilPath--;
			if (this.ticksUntilPath == 0)
			{
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

	private void afterDeath(WorldPoint graveLocation)
	{
		WorldPoint respawnLocation = this.client.getLocalPlayer().getWorldLocation();
		sendShortestPathMessage(respawnLocation, graveLocation);
	}

	private void sendShortestPathMessage(WorldPoint startPoint, WorldPoint endPoint)
	{
		Map<String, Object> shortestPathMessage = new HashMap<>();
		Map<String, Object> configOverride = new HashMap<>();
		configOverride.put("colourPath", this.config.colourPath());
		shortestPathMessage.put("start", startPoint);
		shortestPathMessage.put("target", endPoint);
		shortestPathMessage.put("config", configOverride);

		eventBus.post(new PluginMessage("shortestpath", "path", shortestPathMessage));
		this.pendingGraveLocation = null;
		this.gravestoneSpawned = false;
	}
}
