package shortestgrave;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.Silent.class)
public class ShortestGravePluginTest
{
	private static final WorldPoint DEATH_LOCATION = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint RESPAWN_LOCATION = new WorldPoint(3222, 3218, 0);
	@Mock
	private Client client;
	@Mock
	private EventBus eventBus;
	@Mock
	private ClientThread clientThread;
	@Mock
	private ShortestGraveConfig config;
	@Mock
	private Player player;
	@InjectMocks
	private ShortestGravePlugin plugin;

	private static void setField(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	// --- helpers ---

	private static Object getField(Object target, String name) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	@Before
	public void setUp() throws Exception
	{
		setField(plugin, "currentPlayer", player);
	}

	// --- onActorDeath ---

	@Test
	public void testOnActorDeath_setsGraveLocationForCurrentPlayer() throws Exception
	{
		when(config.pathDelay()).thenReturn(1);
		when(player.getWorldLocation()).thenReturn(DEATH_LOCATION);

		plugin.onActorDeath(new ActorDeath(player));

		assertEquals(DEATH_LOCATION, getField(plugin, "pendingGraveLocation"));
	}

	@Test
	public void testOnActorDeath_setsTicksUntilPath() throws Exception
	{
		when(config.pathDelay()).thenReturn(5);
		when(player.getWorldLocation()).thenReturn(DEATH_LOCATION);

		plugin.onActorDeath(new ActorDeath(player));

		assertEquals(5, getField(plugin, "ticksUntilPath"));
	}

	@Test
	public void testOnActorDeath_ignoredForNonPlayerActor() throws Exception
	{
		Actor otherActor = mock(Actor.class);
		plugin.onActorDeath(new ActorDeath(otherActor));

		assertNull(getField(plugin, "pendingGraveLocation"));
		assertEquals(-1, getField(plugin, "ticksUntilPath"));
	}

	@Test
	public void testOnActorDeath_ignoredForDifferentPlayer() throws Exception
	{
		Player otherPlayer = mock(Player.class);
		plugin.onActorDeath(new ActorDeath(otherPlayer));

		assertNull(getField(plugin, "pendingGraveLocation"));
		assertEquals(-1, getField(plugin, "ticksUntilPath"));
	}

	// --- onChatMessage ---

	@Test
	public void testOnChatMessage_gravestoneMentionSetsFlagWhenPendingLocationExists() throws Exception
	{
		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);

		ChatMessage event = mock(ChatMessage.class);
		when(event.getMessage()).thenReturn("A gravestone has been placed at your location.");
		when(event.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);

		plugin.onChatMessage(event);

		assertTrue((boolean) getField(plugin, "gravestoneSpawned"));
	}

	@Test
	public void testOnChatMessage_ignoresGravestoneMessageWithoutPendingLocation() throws Exception
	{
		ChatMessage event = mock(ChatMessage.class);
		when(event.getMessage()).thenReturn("A gravestone has been placed at your location.");
		when(event.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);

		plugin.onChatMessage(event);

		assertFalse((boolean) getField(plugin, "gravestoneSpawned"));
	}

	@Test
	public void testOnChatMessage_ignoresNonGravestoneGameMessage() throws Exception
	{
		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);

		ChatMessage event = mock(ChatMessage.class);
		when(event.getMessage()).thenReturn("You have gained 1000 experience.");
		when(event.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);

		plugin.onChatMessage(event);

		assertFalse((boolean) getField(plugin, "gravestoneSpawned"));
	}

	@Test
	public void testOnChatMessage_ignoresNonGameMessageType() throws Exception
	{
		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);

		ChatMessage event = mock(ChatMessage.class);
		when(event.getMessage()).thenReturn("Player: gravestone message");
		when(event.getType()).thenReturn(ChatMessageType.PUBLICCHAT);

		plugin.onChatMessage(event);

		assertFalse((boolean) getField(plugin, "gravestoneSpawned"));
	}

	// --- onGameTick ---

	@Test
	public void testOnGameTick_decrementsTicksUntilPath() throws Exception
	{
		setField(plugin, "ticksUntilPath", 3);

		plugin.onGameTick(new GameTick());

		assertEquals(2, getField(plugin, "ticksUntilPath"));
	}

	@Test
	public void testOnGameTick_doesNothingWhenTicksIsNegative() throws Exception
	{
		setField(plugin, "ticksUntilPath", -1);

		plugin.onGameTick(new GameTick());

		verify(eventBus, never()).post(any());
		assertEquals(-1, getField(plugin, "ticksUntilPath"));
	}

	@Test
	public void testOnGameTick_sendsPathWhenGravestoneSpawned() throws Exception
	{
		when(config.alwaysPath()).thenReturn(false);
		when(config.colourPath()).thenReturn(null);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(RESPAWN_LOCATION);

		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);
		setField(plugin, "ticksUntilPath", 1);
		setField(plugin, "gravestoneSpawned", true);

		plugin.onGameTick(new GameTick());

		verify(eventBus).post(any(PluginMessage.class));
	}

	@Test
	public void testOnGameTick_sendsPathWithAlwaysPathEvenWithoutGravestone() throws Exception
	{
		when(config.alwaysPath()).thenReturn(true);
		when(config.colourPath()).thenReturn(null);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(RESPAWN_LOCATION);

		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);
		setField(plugin, "ticksUntilPath", 1);
		setField(plugin, "gravestoneSpawned", false);

		plugin.onGameTick(new GameTick());

		verify(eventBus).post(any(PluginMessage.class));
	}

	@Test
	public void testOnGameTick_doesNotSendPathWhenNoGravestoneAndAlwaysPathDisabled() throws Exception
	{
		when(config.alwaysPath()).thenReturn(false);

		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);
		setField(plugin, "ticksUntilPath", 1);
		setField(plugin, "gravestoneSpawned", false);

		plugin.onGameTick(new GameTick());

		verify(eventBus, never()).post(any());
	}

	// --- PluginMessage content ---

	@Test
	public void testPathMessage_containsCorrectStartAndTarget() throws Exception
	{
		when(config.alwaysPath()).thenReturn(true);
		when(config.colourPath()).thenReturn(null);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(RESPAWN_LOCATION);

		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);
		setField(plugin, "ticksUntilPath", 1);

		plugin.onGameTick(new GameTick());

		ArgumentCaptor<PluginMessage> captor = ArgumentCaptor.forClass(PluginMessage.class);
		verify(eventBus).post(captor.capture());

		PluginMessage msg = captor.getValue();
		assertEquals("shortestpath", msg.getNamespace());
		assertEquals("path", msg.getName());

		Map<String, Object> data = msg.getData();
		assertEquals(RESPAWN_LOCATION, data.get("start"));
		assertEquals(DEATH_LOCATION, data.get("target"));
	}

	@Test
	public void testPathMessage_omitsConfigOverrideWhenColourPathIsNull() throws Exception
	{
		when(config.alwaysPath()).thenReturn(true);
		when(config.colourPath()).thenReturn(null);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(RESPAWN_LOCATION);

		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);
		setField(plugin, "ticksUntilPath", 1);

		plugin.onGameTick(new GameTick());

		ArgumentCaptor<PluginMessage> captor = ArgumentCaptor.forClass(PluginMessage.class);
		verify(eventBus).post(captor.capture());

		Map<String, Object> data = captor.getValue().getData();
		assertFalse("config key should be absent when colourPath is null", data.containsKey("config"));
	}

	@Test
	public void testPathMessage_includesColourPathInConfigOverride() throws Exception
	{
		when(config.alwaysPath()).thenReturn(true);
		when(config.colourPath()).thenReturn(Color.RED);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(RESPAWN_LOCATION);

		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);
		setField(plugin, "ticksUntilPath", 1);

		plugin.onGameTick(new GameTick());

		ArgumentCaptor<PluginMessage> captor = ArgumentCaptor.forClass(PluginMessage.class);
		verify(eventBus).post(captor.capture());

		Map<String, Object> data = captor.getValue().getData();
		Map<String, Object> configOverride = (Map<String, Object>) data.get("config");
		assertEquals(Color.RED, configOverride.get("colourPath"));
	}

	@Test
	public void testPathMessage_clearsPendingStateAfterSending() throws Exception
	{
		when(config.alwaysPath()).thenReturn(true);
		when(config.colourPath()).thenReturn(null);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(RESPAWN_LOCATION);

		setField(plugin, "pendingGraveLocation", DEATH_LOCATION);
		setField(plugin, "ticksUntilPath", 1);
		setField(plugin, "gravestoneSpawned", true);

		plugin.onGameTick(new GameTick());

		assertNull(getField(plugin, "pendingGraveLocation"));
		assertFalse((boolean) getField(plugin, "gravestoneSpawned"));
		assertEquals(-1, getField(plugin, "ticksUntilPath"));
	}
}

