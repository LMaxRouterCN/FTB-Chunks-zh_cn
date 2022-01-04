package dev.ftb.mods.ftbchunks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.ftb.mods.ftbchunks.ColorMapLoader;
import dev.ftb.mods.ftbchunks.FTBChunks;
import dev.ftb.mods.ftbchunks.FTBChunksCommon;
import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.client.map.ChunkUpdateTask;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbchunks.client.map.MapRegionData;
import dev.ftb.mods.ftbchunks.client.map.MapTask;
import dev.ftb.mods.ftbchunks.client.map.RegionSyncKey;
import dev.ftb.mods.ftbchunks.client.map.UpdateChunkFromServerTask;
import dev.ftb.mods.ftbchunks.client.map.Waypoint;
import dev.ftb.mods.ftbchunks.client.map.WaypointType;
import dev.ftb.mods.ftbchunks.client.map.color.ColorUtils;
import dev.ftb.mods.ftbchunks.core.BiomeManagerFTBC;
import dev.ftb.mods.ftbchunks.core.ClientboundSectionBlocksUpdatePacketFTBC;
import dev.ftb.mods.ftbchunks.data.PlayerLocation;
import dev.ftb.mods.ftbchunks.integration.MapIcon;
import dev.ftb.mods.ftbchunks.integration.MapIconEvent;
import dev.ftb.mods.ftbchunks.integration.RefreshMinimapIconsEvent;
import dev.ftb.mods.ftbchunks.net.LoginDataPacket;
import dev.ftb.mods.ftbchunks.net.PartialPackets;
import dev.ftb.mods.ftbchunks.net.PlayerDeathPacket;
import dev.ftb.mods.ftbchunks.net.SendChunkPacket;
import dev.ftb.mods.ftbchunks.net.SendGeneralDataPacket;
import dev.ftb.mods.ftbchunks.net.SendManyChunksPacket;
import dev.ftb.mods.ftbchunks.net.SendVisiblePlayerListPacket;
import dev.ftb.mods.ftblibrary.icon.FaceIcon;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.math.MathUtils;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import dev.ftb.mods.ftblibrary.ui.CustomClickEvent;
import dev.ftb.mods.ftblibrary.util.ClientUtils;
import dev.ftb.mods.ftbteams.data.ClientTeam;
import dev.ftb.mods.ftbteams.event.ClientTeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.event.TeamEvent;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.shedaniel.architectury.event.events.GuiEvent;
import me.shedaniel.architectury.event.events.client.ClientPlayerEvent;
import me.shedaniel.architectury.event.events.client.ClientRawInputEvent;
import me.shedaniel.architectury.event.events.client.ClientScreenInputEvent;
import me.shedaniel.architectury.event.events.client.ClientTickEvent;
import me.shedaniel.architectury.platform.Platform;
import me.shedaniel.architectury.registry.KeyBindings;
import me.shedaniel.architectury.registry.ReloadListeners;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author LatvianModder
 */
public class FTBChunksClient extends FTBChunksCommon {
	private static final ResourceLocation BUTTON_ID = new ResourceLocation("ftbchunks:open_gui");
	public static final ResourceLocation CIRCLE_MASK = new ResourceLocation("ftbchunks:textures/circle_mask.png");
	public static final ResourceLocation CIRCLE_BORDER = new ResourceLocation("ftbchunks:textures/circle_border.png");
	public static final ResourceLocation PLAYER = new ResourceLocation("ftbchunks:textures/player.png");
	public static final ResourceLocation[] COMPASS = {
			new ResourceLocation("ftbchunks:textures/compass_e.png"),
			new ResourceLocation("ftbchunks:textures/compass_n.png"),
			new ResourceLocation("ftbchunks:textures/compass_w.png"),
			new ResourceLocation("ftbchunks:textures/compass_s.png"),
	};

	private static final List<Component> MINIMAP_TEXT_LIST = new ArrayList<>(3);

	private static final ArrayDeque<MapTask> taskQueue = new ArrayDeque<>();
	public static long taskQueueTicks = 0L;
	public static Map<ChunkPos, IntOpenHashSet> rerenderCache = new HashMap<>();

	public static void queue(MapTask task) {
		taskQueue.addLast(task);
	}

	public static KeyMapping openMapKey;
	public static KeyMapping zoomInKey;
	public static KeyMapping zoomOutKey;

	public static int minimapTextureId = -1;
	private int currentPlayerChunkX, currentPlayerChunkZ;
	private double currentPlayerX, currentPlayerY, currentPlayerZ;
	private double prevPlayerX, prevPlayerY, prevPlayerZ;
	private static int renderedDebugCount = 0;

	public static boolean updateMinimap = false;
	public static boolean alwaysRenderChunksOnMap = false;
	public static SendGeneralDataPacket generalData;
	private long nextRegionSave = 0L;
	private double prevZoom = FTBChunksClientConfig.MINIMAP_ZOOM.get();
	private long lastZoomTime = 0L;
	private final List<MapIcon> mapIcons = new ArrayList<>();
	private long lastMapIconUpdate = 0L;
	private final List<WaypointMapIcon> visibleWaypoints = new ArrayList<>();

	@Override
	public void init() {
		if (Minecraft.getInstance() == null) {
			return;
		}

		FTBChunksClientConfig.init();
		registerKeys();

		ReloadListeners.registerReloadListener(PackType.CLIENT_RESOURCES, new EntityIcons());
		ReloadListeners.registerReloadListener(PackType.CLIENT_RESOURCES, new ColorMapLoader());
		ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(this::loggedOut);
		CustomClickEvent.EVENT.register(this::customClick);
		ClientRawInputEvent.KEY_PRESSED.register(this::keyPressed);
		ClientScreenInputEvent.KEY_PRESSED_PRE.register(this::keyPressed);
		GuiEvent.RENDER_HUD.register(this::renderHud);
		GuiEvent.INIT_PRE.register(this::screenOpened);
		ClientTickEvent.CLIENT_PRE.register(this::clientTick);
		TeamEvent.CLIENT_PROPERTIES_CHANGED.register(this::teamPropertiesChanged);
		MapIconEvent.LARGE_MAP.register(this::mapIcons);
		MapIconEvent.MINIMAP.register(this::mapIcons);
		RefreshMinimapIconsEvent.EVENT.register(this::refreshMinimapIcons);
		registerPlatform();
	}

	public void refreshMinimapIcons() {
		lastMapIconUpdate = 0L;
	}

	private static void registerKeys() {
		// Keybinding to open Large map screen
		openMapKey = new KeyMapping("key.ftbchunks.map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.ui");
		KeyBindings.registerKeyBinding(openMapKey);

		// Keybindings to zoom in minimap
		zoomInKey = new KeyMapping("key.ftbchunks.minimap.zoomIn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "key.categories.ui");
		KeyBindings.registerKeyBinding(zoomInKey);

		zoomOutKey = new KeyMapping("key.ftbchunks.minimap.zoomOut", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "key.categories.ui");
		KeyBindings.registerKeyBinding(zoomOutKey);
	}

	@ExpectPlatform
	public static void registerPlatform() {
		throw new AssertionError();
	}

	public static void openGui() {
		new LargeMapScreen().openGui();
	}

	public static void saveAllRegions() {
		if (MapManager.inst == null) {
			return;
		}

		for (MapDimension dimension : MapManager.inst.getDimensions().values()) {
			for (MapRegion region : dimension.getLoadedRegions()) {
				if (region.saveData) {
					queue(region);
					region.saveData = false;
				}
			}

			if (dimension.saveData) {
				queue(dimension);
				dimension.saveData = false;
			}
		}

		if (MapManager.inst.saveData) {
			queue(MapManager.inst);
			MapManager.inst.saveData = false;
		}
	}

	@Override
	public void login(LoginDataPacket loginData) {
		FTBChunks.LOGGER.info("Loading FTB Chunks client data from world " + loginData.serverId);
		FTBChunksWorldConfig.CONFIG.read(loginData.config);

		Path dir = Platform.getGameFolder().resolve("local/ftbchunks/data/" + loginData.serverId);

		if (Files.notExists(dir)) {
			try {
				Files.createDirectories(dir);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		MapManager.inst = new MapManager(loginData.serverId, dir);
		updateMinimap = true;
		renderedDebugCount = 0;
		ChunkUpdateTask.debugLastTime = 0L;
	}

	public void loggedOut(@Nullable LocalPlayer player) {
		MapManager manager = MapManager.inst;

		if (manager != null) {
			saveAllRegions();

			MapTask task;

			while ((task = taskQueue.pollFirst()) != null) {
				try {
					task.runMapTask();
				} catch (Exception ex) {
					FTBChunks.LOGGER.error("Failed to run task " + task);
					ex.printStackTrace();
				}
			}

			MapDimension.updateCurrent();
			manager.release();
		}

		MapManager.inst = null;
	}

	@Override
	public void updateGeneralData(SendGeneralDataPacket packet) {
		generalData = packet;
	}

	@Override
	public void updateChunk(SendChunkPacket packet) {
		if (MapManager.inst == null) {
			return;
		}

		MapDimension dimension = MapManager.inst.getDimension(packet.dimension);
		Date now = new Date();
		queue(new UpdateChunkFromServerTask(dimension, packet.chunk, packet.teamId, now));
	}

	@Override
	public void updateAllChunks(SendManyChunksPacket packet) {
		if (MapManager.inst == null) {
			return;
		}

		MapDimension dimension = MapManager.inst.getDimension(packet.dimension);
		Date now = new Date();

		for (SendChunkPacket.SingleChunk c : packet.chunks) {
			queue(new UpdateChunkFromServerTask(dimension, c, packet.teamId, now));
		}
	}

	@Override
	public void updateVisiblePlayerList(SendVisiblePlayerListPacket packet) {
		PlayerLocation.CLIENT_LIST.clear();
		PlayerLocation.currentDimension = packet.dim;
		PlayerLocation.CLIENT_LIST.addAll(packet.players);
	}

	@Override
	public void syncRegion(RegionSyncKey key, int offset, int total, byte[] data) {
		PartialPackets.REGION.read(key, offset, total, data);
	}

	@Override
	public void playerDeath(PlayerDeathPacket packet) {
		if (FTBChunksClientConfig.DEATH_WAYPOINTS.get()) {
			MapDimension dimension = MapManager.inst.getDimension(packet.dimension);

			for (Waypoint w : dimension.getWaypoints()) {
				if (w.type == WaypointType.DEATH) {
					w.hidden = true;
					w.update();
				}
			}

			Waypoint w = new Waypoint(dimension);
			w.name = "Death #" + packet.number;
			w.x = packet.x;
			w.y = packet.y;
			w.z = packet.z;
			w.type = WaypointType.DEATH;
			w.color = 0xFF0000;
			dimension.getWaypoints().add(w);
			w.update();
			dimension.saveData = true;
			RefreshMinimapIconsEvent.trigger();
		}
	}

	@Override
	public int blockColor() {
		Minecraft mc = Minecraft.getInstance();

		mc.submit(() -> {
			mc.setScreen(null);

			new Thread(() -> {
				try {
					Thread.sleep(50L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				mc.submit(() -> {
					if (mc.hitResult instanceof BlockHitResult) {
						ResourceLocation id = Registry.BLOCK.getKey(mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos()).getBlock());
						Window window = mc.getWindow();
						NativeImage image = Screenshot.takeScreenshot(window.getWidth(), window.getHeight(), mc.getMainRenderTarget());
						int col = image.getPixelRGBA(image.getWidth() / 2 - (int) (2D * window.getGuiScale()), image.getHeight() / 2 - (int) (2D * window.getGuiScale()));
						String s = String.format("\"%s\": \"#%06X\"", id.getPath(), ColorUtils.convertFromNative(col) & 0xFFFFFF);
						mc.player.sendMessage(new TextComponent(id.getNamespace() + " - " + s).withStyle(Style.EMPTY.applyFormat(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, s)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent("Click to copy")))), Util.NIL_UUID);
					}
				});
			}, "Color getter").start();
		});

		return 1;
	}

	@Override
	public void updateLoadedChunkView(ResourceKey<Level> dimension, Collection<ChunkPos> chunks) {
		MapDimension dim = MapManager.inst.getDimension(dimension);
		dim.loadedChunkView = chunks;
		MapManager.inst.updateAllRegions(false);
	}

	@Override
	public boolean skipBlock(BlockState state) {
		ResourceLocation id = FTBChunks.BLOCK_REGISTRY.getId(state.getBlock());
		return id == null || ColorMapLoader.getBlockColor(id).isIgnored();
	}

	public InteractionResult customClick(CustomClickEvent event) {
		if (event.getId().equals(BUTTON_ID)) {
			openGui();
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	public InteractionResult keyPressed(Minecraft client, int keyCode, int scanCode, int action, int modifiers) {
		if (openMapKey.isDown()) {
			if (Screen.hasControlDown()) {
				SNBTCompoundTag tag = new SNBTCompoundTag();
				tag.putBoolean(FTBChunksClientConfig.MINIMAP_ENABLED.key, !FTBChunksClientConfig.MINIMAP_ENABLED.get());
				FTBChunksClientConfig.MINIMAP_ENABLED.read(tag);
				FTBChunksClientConfig.saveConfig();
			} else if (FTBChunksClientConfig.DEBUG_INFO.get() && Screen.hasAltDown()) {
				FTBChunks.LOGGER.info("=== Task Queue: " + taskQueue.size());

				for (MapTask task : taskQueue) {
					FTBChunks.LOGGER.info(task.toString());
				}

				FTBChunks.LOGGER.info("===");
			} else {
				openGui();
				return InteractionResult.SUCCESS;
			}
		} else if (zoomInKey.isDown()) {
			return changeZoom(true);
		} else if (zoomOutKey.isDown()) {
			return changeZoom(false);
		}

		return InteractionResult.PASS;
	}

	public InteractionResult keyPressed(Minecraft client, Screen screen, int keyCode, int scanCode, int modifiers) {
		if (openMapKey.isDown()) {
			LargeMapScreen gui = ClientUtils.getCurrentGuiAs(LargeMapScreen.class);

			if (gui != null) {
				gui.closeGui(false);
				return InteractionResult.SUCCESS;
			}
		}

		return InteractionResult.PASS;
	}

	private InteractionResult changeZoom(boolean zoomIn) {
		prevZoom = FTBChunksClientConfig.MINIMAP_ZOOM.get();
		double zoom = prevZoom;
		double zoomFactor = zoomIn ? 1D : -1D;

		if (zoom + zoomFactor > 4D) {
			zoom = 4D;
		} else if (zoom + zoomFactor < 1D) {
			zoom = 1D;
		} else {
			zoom += zoomFactor;
		}

		lastZoomTime = System.currentTimeMillis();
		FTBChunksClientConfig.MINIMAP_ZOOM.set(zoom);
		return InteractionResult.SUCCESS;
	}

	public float getZoom() {
		double z = FTBChunksClientConfig.MINIMAP_ZOOM.get();

		if (prevZoom != z) {
			long max = (long) (400D / z);
			long t = Mth.clamp(System.currentTimeMillis() - lastZoomTime, 0L, max);

			if (t == max) {
				lastZoomTime = 0L;
				return (float) z;
			}

			return (float) Mth.lerp(t / (double) max, prevZoom, z);
		}

		return (float) z;
	}

	public void renderHud(PoseStack matrixStack, float tickDelta) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.level == null || MapManager.inst == null) {
			return;
		}

		double playerX = Mth.lerp(tickDelta, prevPlayerX, currentPlayerX);
		double playerY = Mth.lerp(tickDelta, prevPlayerY, currentPlayerY);
		double playerZ = Mth.lerp(tickDelta, prevPlayerZ, currentPlayerZ);
		double guiScale = mc.getWindow().getGuiScale();
		MapDimension dim = MapDimension.getCurrent();

		if (dim.dimension != mc.level.dimension()) {
			MapDimension.updateCurrent();
			dim = MapDimension.getCurrent();
		}

		long now = System.currentTimeMillis();

		if (nextRegionSave == 0L || now >= nextRegionSave) {
			nextRegionSave = now + 60000L;
			saveAllRegions();
		}

		if (minimapTextureId == -1) {
			minimapTextureId = TextureUtil.generateTextureId();
			TextureUtil.prepareImage(minimapTextureId, FTBChunks.MINIMAP_SIZE, FTBChunks.MINIMAP_SIZE);
			updateMinimap = true;
		}

		RenderSystem.enableTexture();
		RenderSystem.bindTexture(minimapTextureId);

		float zoom0 = getZoom();
		float zoom = zoom0 / 3.5F;
		MinimapBlurMode blurMode = FTBChunksClientConfig.MINIMAP_BLUR_MODE.get();
		boolean minimapBlur = blurMode == MinimapBlurMode.AUTO ? (zoom0 < 1.5F) : blurMode == MinimapBlurMode.ON;
		int filter = minimapBlur ? GL11.GL_LINEAR : GL11.GL_NEAREST;
		RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
		RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);

		int cx = Mth.floor(playerX) >> 4;
		int cz = Mth.floor(playerZ) >> 4;

		if (cx != currentPlayerChunkX || cz != currentPlayerChunkZ) {
			updateMinimap = true;
		}

		if (updateMinimap) {
			updateMinimap = false;

			// TODO: More math here to upload from (up to) 4 regions instead of all chunks inside them, to speed things up

			for (int mz = 0; mz < FTBChunks.TILES; mz++) {
				for (int mx = 0; mx < FTBChunks.TILES; mx++) {
					int ox = cx + mx - FTBChunks.TILE_OFFSET;
					int oz = cz + mz - FTBChunks.TILE_OFFSET;

					MapRegion region = dim.getRegion(XZ.regionFromChunk(ox, oz));
					region.getRenderedMapImage().upload(0, mx * 16, mz * 16, (ox & 31) * 16, (oz & 31) * 16, 16, 16, minimapBlur, false, false, false);
				}
			}

			currentPlayerChunkX = cx;
			currentPlayerChunkZ = cz;
		}

		if (mc.options.renderDebug || !FTBChunksClientConfig.MINIMAP_ENABLED.get() || FTBChunksClientConfig.MINIMAP_VISIBILITY.get() == 0 || FTBChunksWorldConfig.FORCE_DISABLE_MINIMAP.get()) {
			return;
		}

		float scale = (float) (FTBChunksClientConfig.MINIMAP_SCALE.get() * 4D / guiScale);
		float minimapRotation = (FTBChunksClientConfig.MINIMAP_LOCKED_NORTH.get() ? 180F : -mc.player.yRot) % 360F;

		int s = (int) (64D * scale);
		double s2d = s / 2D;
		float s2f = s / 2F;
		int x = FTBChunksClientConfig.MINIMAP_POSITION.get().getX(mc.getWindow().getGuiScaledWidth(), s);
		int y = FTBChunksClientConfig.MINIMAP_POSITION.get().getY(mc.getWindow().getGuiScaledHeight(), s);
		int z = 0;

		float border = 0F;
		int alpha = FTBChunksClientConfig.MINIMAP_VISIBILITY.get();

		Tesselator tessellator = Tesselator.getInstance();
		BufferBuilder buffer = tessellator.getBuilder();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableCull();
		RenderSystem.enableTexture();
		RenderSystem.enableDepthTest();
		RenderSystem.enableAlphaTest();

		matrixStack.pushPose();
		matrixStack.translate(x + s2d, y + s2d, 490 + z);

		Matrix4f m = matrixStack.last().pose();

		// See AdvancementTabGui
		RenderSystem.colorMask(false, false, false, false);
		mc.getTextureManager().bind(CIRCLE_MASK);
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		buffer.vertex(m, -s2f + border, -s2f + border, 0F).color(255, 255, 255, 255).uv(0F, 0F).endVertex();
		buffer.vertex(m, -s2f + border, s2f - border, 0F).color(255, 255, 255, 255).uv(0F, 1F).endVertex();
		buffer.vertex(m, s2f - border, s2f - border, 0F).color(255, 255, 255, 255).uv(1F, 1F).endVertex();
		buffer.vertex(m, s2f - border, -s2f + border, 0F).color(255, 255, 255, 255).uv(1F, 0F).endVertex();
		tessellator.end();
		RenderSystem.colorMask(true, true, true, true);

		matrixStack.mulPose(Vector3f.ZP.rotationDegrees(minimapRotation + 180F));

		RenderSystem.depthFunc(GL11.GL_GEQUAL);
		RenderSystem.bindTexture(minimapTextureId);

		float s2fb = s2f - border;
		float offX = 0.5F + (float) ((MathUtils.mod(playerX, 16D) / 16D - 0.5D) / (double) FTBChunks.TILES);
		float offZ = 0.5F + (float) ((MathUtils.mod(playerZ, 16D) / 16D - 0.5D) / (double) FTBChunks.TILES);
		float zws = 2F / (FTBChunks.TILES * zoom);

		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		buffer.vertex(m, -s2fb, -s2fb, 0F).color(255, 255, 255, alpha).uv(offX - zws, offZ - zws).endVertex();
		buffer.vertex(m, -s2fb, s2fb, 0F).color(255, 255, 255, alpha).uv(offX - zws, offZ + zws).endVertex();
		buffer.vertex(m, s2fb, s2fb, 0F).color(255, 255, 255, alpha).uv(offX + zws, offZ + zws).endVertex();
		buffer.vertex(m, s2fb, -s2fb, 0F).color(255, 255, 255, alpha).uv(offX + zws, offZ - zws).endVertex();
		tessellator.end();

		RenderSystem.disableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.defaultBlendFunc();

		mc.getTextureManager().bind(CIRCLE_BORDER);
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		buffer.vertex(m, -s2f, -s2f, 0F).color(255, 255, 255, alpha).uv(0F, 0F).endVertex();
		buffer.vertex(m, -s2f, s2f, 0F).color(255, 255, 255, alpha).uv(0F, 1F).endVertex();
		buffer.vertex(m, s2f, s2f, 0F).color(255, 255, 255, alpha).uv(1F, 1F).endVertex();
		buffer.vertex(m, s2f, -s2f, 0F).color(255, 255, 255, alpha).uv(1F, 0F).endVertex();
		tessellator.end();

		RenderSystem.disableTexture();
		buffer.begin(GL11.GL_LINES, DefaultVertexFormat.POSITION_COLOR);
		buffer.vertex(m, -s2f, 0, 0F).color(0, 0, 0, 30).endVertex();
		buffer.vertex(m, s2f, 0, 0F).color(0, 0, 0, 30).endVertex();
		buffer.vertex(m, 0, -s2f, 0F).color(0, 0, 0, 30).endVertex();
		buffer.vertex(m, 0, s2f, 0F).color(0, 0, 0, 30).endVertex();
		tessellator.end();

		matrixStack.popPose();

		m = matrixStack.last().pose();

		RenderSystem.enableTexture();

		if (FTBChunksClientConfig.MINIMAP_COMPASS.get()) {
			for (int face = 0; face < 4; face++) {
				double d = s / 2.2D;

				double angle = (minimapRotation + 180D - face * 90D) * Math.PI / 180D;

				float wx = (float) (x + s2d + Math.cos(angle) * d);
				float wy = (float) (y + s2d + Math.sin(angle) * d);
				float ws = s / 32F;

				mc.getTextureManager().bind(COMPASS[face]);
				buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
				buffer.vertex(m, wx - ws, wy - ws, z).color(255, 255, 255, 255).uv(0F, 0F).endVertex();
				buffer.vertex(m, wx - ws, wy + ws, z).color(255, 255, 255, 255).uv(0F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy + ws, z).color(255, 255, 255, 255).uv(1F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy - ws, z).color(255, 255, 255, 255).uv(1F, 0F).endVertex();
				tessellator.end();
			}
		}

		if (lastMapIconUpdate == 0L || (now - lastMapIconUpdate) >= FTBChunksClientConfig.MINIMAP_ICON_UPDATE_TIMER.get()) {
			lastMapIconUpdate = now;

			mapIcons.clear();
			MapIconEvent.MINIMAP.invoker().accept(new MapIconEvent(mc, dim, mapIcons, MapType.MINIMAP));

			if (mapIcons.size() >= 2) {
				mapIcons.sort(new MapIconComparator(mc.player.position()));
			}
		}

		for (MapIcon icon : mapIcons) {
			Vec3 pos = icon.getPos();
			double distance = MathUtils.dist(playerX, playerZ, pos.x, pos.z);
			double d = distance * scale * zoom;

			if (!icon.isVisible(MapType.MINIMAP, distance, d > s2d)) {
				continue;
			}

			if (d > s2d) {
				d = s2d;
			}

			double angle = Math.atan2(playerZ - pos.z, playerX - pos.x) + minimapRotation * Math.PI / 180D;

			double ws = s / (32D / icon.getIconScale(MapType.MINIMAP));
			double wx = x + s2d + Math.cos(angle) * d;
			double wy = y + s2d + Math.sin(angle) * d;
			float wsf = (float) (ws * 2D);

			matrixStack.pushPose();
			matrixStack.translate(wx - ws, wy - ws - (icon.isIconOnEdge(MapType.MINIMAP, d >= s2d) ? ws / 2D : 0D), z);
			matrixStack.scale(wsf, wsf, 1F);
			icon.draw(MapType.MINIMAP, matrixStack, 0, 0, 1, 1, d >= s2d);
			matrixStack.popPose();
		}

		if (FTBChunksClientConfig.MINIMAP_LOCKED_NORTH.get()) {
			mc.getTextureManager().bind(PLAYER);
			matrixStack.pushPose();
			matrixStack.translate(x + s2d, y + s2d, z);
			matrixStack.mulPose(Vector3f.ZP.rotationDegrees(mc.player.yRot + 180F));
			matrixStack.scale(s / 16F, s / 16F, 1F);
			m = matrixStack.last().pose();

			buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
			buffer.vertex(m, -1, -1, 0).color(255, 255, 255, 200).uv(0F, 0F).endVertex();
			buffer.vertex(m, -1, 1, 0).color(255, 255, 255, 200).uv(0F, 1F).endVertex();
			buffer.vertex(m, 1, 1, 0).color(255, 255, 255, 200).uv(1F, 1F).endVertex();
			buffer.vertex(m, 1, -1, 0).color(255, 255, 255, 200).uv(1F, 0F).endVertex();
			tessellator.end();

			matrixStack.popPose();
		}

		MINIMAP_TEXT_LIST.clear();

		if (FTBChunksClientConfig.MINIMAP_ZONE.get()) {
			MapRegionData data = dim.getRegion(XZ.regionFromChunk(currentPlayerChunkX, currentPlayerChunkZ)).getData();

			if (data != null) {
				ClientTeam team = data.getChunk(XZ.of(currentPlayerChunkX, currentPlayerChunkZ)).getTeam();

				if (team != null) {
					MINIMAP_TEXT_LIST.add(team.getColoredName());
				}
			}
		}

		if (FTBChunksClientConfig.MINIMAP_XYZ.get()) {
			MINIMAP_TEXT_LIST.add(new TextComponent(Mth.floor(playerX) + " " + Mth.floor(playerY) + " " + Mth.floor(playerZ)));
		}

		if (FTBChunksClientConfig.MINIMAP_BIOME.get()) {
			ResourceKey<Biome> biome = mc.level.getBiomeName(mc.player.blockPosition()).orElse(null);

			if (biome != null) {
				MINIMAP_TEXT_LIST.add(new TranslatableComponent("biome." + biome.location().getNamespace() + "." + biome.location().getPath()));
			}
		}

		if (FTBChunksClientConfig.DEBUG_INFO.get()) {
			XZ r = XZ.regionFromChunk(currentPlayerChunkX, currentPlayerChunkZ);
			MINIMAP_TEXT_LIST.add(new TextComponent("Queued tasks: " + taskQueue.size()));
			MINIMAP_TEXT_LIST.add(new TextComponent(r.toRegionString()));
			MINIMAP_TEXT_LIST.add(new TextComponent("Total updates: " + renderedDebugCount));

			if (ChunkUpdateTask.debugLastTime > 0L) {
				MINIMAP_TEXT_LIST.add(new TextComponent(String.format("LU: %,d ns", ChunkUpdateTask.debugLastTime)));
			}
		}

		if (!MINIMAP_TEXT_LIST.isEmpty()) {
			matrixStack.pushPose();
			matrixStack.translate(x + s2d, y + s + 3D, 0D);
			matrixStack.scale((float) (0.5D * scale), (float) (0.5D * scale), 1F);

			for (int i = 0; i < MINIMAP_TEXT_LIST.size(); i++) {
				FormattedCharSequence bs = MINIMAP_TEXT_LIST.get(i).getVisualOrderText();
				int bsw = mc.font.width(bs);
				mc.font.drawShadow(matrixStack, bs, -bsw / 2F, i * 11, 0xFFFFFFFF);
			}

			matrixStack.popPose();
		}

		RenderSystem.enableDepthTest();
	}

	public void renderWorldLast(PoseStack ms) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.options.hideGui || !FTBChunksClientConfig.IN_WORLD_WAYPOINTS.get() || MapManager.inst == null || mc.level == null || mc.player == null) {
			return;
		}

		MapDimension dim = MapDimension.getCurrent();

		if (dim.getWaypoints().isEmpty()) {
			return;
		}

		double playerX = mc.player.getX();
		double playerZ = mc.player.getZ();

		for (Waypoint waypoint : dim.getWaypoints()) {
			if (waypoint.hidden) {
				continue;
			}

			double faceOutDistance = FTBChunksClientConfig.WAYPOINT_FADE_DISTANCE.get();
			double faceOutDistanceP = faceOutDistance * 2D / 3D;

			double distance = MathUtils.dist(playerX, playerZ, waypoint.x + 0.5D, waypoint.z + 0.5D);

			if (distance <= faceOutDistanceP || distance > waypoint.inWorldDistance) {
				continue;
			}

			int alpha = 150;

			if (distance < faceOutDistance) {
				alpha = (int) (alpha * ((distance - faceOutDistanceP) / (faceOutDistance - faceOutDistanceP)));
			}

			if (alpha > 0) {
				waypoint.mapIcon.distance = distance;
				waypoint.mapIcon.alpha = alpha;
				visibleWaypoints.add(waypoint.mapIcon);
			}
		}

		if (visibleWaypoints.isEmpty()) {
			return;
		} else if (visibleWaypoints.size() >= 2) {
			visibleWaypoints.sort(new MapIconComparator(mc.player.position()));
		}

		Camera camera = Minecraft.getInstance().getEntityRenderDispatcher().camera;
		Vec3 cameraPos = camera.getPosition();
		ms.pushPose();
		ms.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		VertexConsumer depthBuffer = mc.renderBuffers().bufferSource().getBuffer(FTBChunksRenderTypes.WAYPOINTS_DEPTH);

		float h = (float) (cameraPos.y + 30D);
		float h2 = h + 70F;

		for (WaypointMapIcon waypoint : visibleWaypoints) {
			double angle = Math.atan2(cameraPos.z - waypoint.pos.z, cameraPos.x - waypoint.pos.x) * 180D / Math.PI;

			int r = (waypoint.waypoint.color >> 16) & 0xFF;
			int g = (waypoint.waypoint.color >> 8) & 0xFF;
			int b = (waypoint.waypoint.color >> 0) & 0xFF;

			ms.pushPose();
			ms.translate(waypoint.pos.x, 0, waypoint.pos.z);
			ms.mulPose(Vector3f.YP.rotationDegrees((float) (-angle - 135D)));

			float s = 0.6F;

			Matrix4f m = ms.last().pose();

			depthBuffer.vertex(m, -s, 0, s).color(r, g, b, waypoint.alpha).uv(0F, 1F).endVertex();
			depthBuffer.vertex(m, -s, h, s).color(r, g, b, waypoint.alpha).uv(0F, 0F).endVertex();
			depthBuffer.vertex(m, s, h, -s).color(r, g, b, waypoint.alpha).uv(1F, 0F).endVertex();
			depthBuffer.vertex(m, s, 0, -s).color(r, g, b, waypoint.alpha).uv(1F, 1F).endVertex();

			depthBuffer.vertex(m, -s, h, s).color(r, g, b, waypoint.alpha).uv(0F, 1F).endVertex();
			depthBuffer.vertex(m, -s, h2, s).color(r, g, b, 0).uv(0F, 0F).endVertex();
			depthBuffer.vertex(m, s, h2, -s).color(r, g, b, 0).uv(1F, 0F).endVertex();
			depthBuffer.vertex(m, s, h, -s).color(r, g, b, waypoint.alpha).uv(1F, 1F).endVertex();

			ms.popPose();
		}

		ms.popPose();

		mc.renderBuffers().bufferSource().endBatch(FTBChunksRenderTypes.WAYPOINTS_DEPTH);
		visibleWaypoints.clear();
	}

	private InteractionResult screenOpened(Screen screen, List<AbstractWidget> widgets, List<GuiEventListener> children) {
		if (screen instanceof PauseScreen) {
			nextRegionSave = System.currentTimeMillis() + 60000L;
			saveAllRegions();
		}

		return InteractionResult.PASS;
	}

	private void clientTick(Minecraft mc) {
		MapManager manager = MapManager.inst;

		if (manager != null && mc.level != null) {
			if (mc.player != null) {
				prevPlayerX = currentPlayerX;
				prevPlayerY = currentPlayerY;
				prevPlayerZ = currentPlayerZ;
				currentPlayerX = mc.player.getX();
				currentPlayerY = mc.player.getY();
				currentPlayerZ = mc.player.getZ();
			}

			if (taskQueueTicks % FTBChunksClientConfig.RERENDER_QUEUE_TICKS.get() == 0L) {
				if (!rerenderCache.isEmpty()) {
					Level level = mc.level;
					long biomeZoomSeed = ((BiomeManagerFTBC) level.getBiomeManager()).getBiomeZoomSeedFTBC();

					for (Map.Entry<ChunkPos, IntOpenHashSet> pos : rerenderCache.entrySet()) {
						ChunkAccess chunkAccess = level.getChunk(pos.getKey().x, pos.getKey().z, ChunkStatus.FULL, false);

						if (chunkAccess != null) {
							queueOrExecute(new ChunkUpdateTask(manager, level, chunkAccess, pos.getKey(), chunkAccess.getBiomes(), biomeZoomSeed, pos.getValue().toIntArray()));
						}
					}

					rerenderCache = new HashMap<>();
				}
			}

			if (taskQueueTicks % FTBChunksClientConfig.TASK_QUEUE_TICKS.get() == 0L) {
				int s = Math.min(taskQueue.size(), FTBChunksClientConfig.TASK_QUEUE_MAX.get());

				if (s > 0) {
					MapTask[] tasks = new MapTask[s];

					for (int i = 0; i < s; i++) {
						tasks[i] = taskQueue.pollFirst();

						if (tasks[i] == null || tasks[i].cancelOtherTasks()) {
							break;
						}
					}

					for (MapTask task : tasks) {
						if (task != null) {
							try {
								task.runMapTask();
							} catch (Exception ex) {
								FTBChunks.LOGGER.error("Failed to run task " + task);
								ex.printStackTrace();
							}
						}
					}
				}
			}

			taskQueueTicks++;
		}
	}

	private void teamPropertiesChanged(ClientTeamPropertiesChangedEvent event) {
		if (MapManager.inst != null) {
			MapManager.inst.updateAllRegions(false);
		}
	}

	private void mapIcons(MapIconEvent event) {
		Minecraft mc = event.mc;

		if (FTBChunksClientConfig.MINIMAP_WAYPOINTS.get()) {
			for (Waypoint w : event.mapDimension.getWaypoints()) {
				if (!w.hidden || !event.mapType.isMinimap()) {
					event.add(w.mapIcon);
				}
			}
		}

		if (FTBChunksClientConfig.MINIMAP_ENTITIES.get()) {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof AbstractClientPlayer || entity.getType().getCategory() == MobCategory.MISC || entity.isInvisibleTo(mc.player)) {
					continue;
				}

				Icon icon = EntityIcons.get(entity);

				if (icon != Icon.EMPTY) {
					if (FTBChunksClientConfig.ONLY_SURFACE_ENTITIES.get() && !mc.level.dimensionType().hasCeiling()) {
						int x = Mth.floor(entity.getX());
						int z = Mth.floor(entity.getZ());
						MapRegion region = event.mapDimension.getRegion(XZ.regionFromBlock(x, z));

						MapRegionData data = region.getData();

						if (data != null) {
							int y = data.height[(x & 511) + (z & 511) * 512];

							if (entity.getY() >= y - 10) {
								event.add(new EntityMapIcon(entity, icon));
							}
						}
					} else {
						event.add(new EntityMapIcon(entity, icon));
					}
				}
			}
		}

		if (FTBChunksClientConfig.MINIMAP_PLAYER_HEADS.get() && mc.level.players().size() > 1) {
			for (AbstractClientPlayer player : mc.level.players()) {
				if (player == mc.player || player.isInvisibleTo(mc.player)) {
					continue;
				}

				event.add(new EntityMapIcon(player, FaceIcon.getFace(player.getGameProfile())));
			}
		}

		if (!event.mapType.isMinimap()) {
			event.add(new EntityMapIcon(mc.player, FaceIcon.getFace(mc.player.getGameProfile())));
		}
	}

	public static void rerender(BlockPos pos) {
		ChunkPos chunkPos = new ChunkPos(pos);
		IntOpenHashSet set = rerenderCache.get(chunkPos);

		if (set == null) {
			set = new IntOpenHashSet();
			rerenderCache.put(chunkPos, set);
		}

		if (set.add((pos.getX() & 15) + ((pos.getZ() & 15) * 16))) {
			if (FTBChunksClientConfig.DEBUG_INFO.get()) {
				renderedDebugCount++;
			}
		}
	}

	public static void handlePacket(ClientboundSectionBlocksUpdatePacketFTBC p) {
		SectionPos sectionPos = p.getSectionPosFTBC();

		short[] positions = p.getPositionsFTBC();

		for (short position : positions) {
			rerender(sectionPos.relativeToBlockPos(position));
		}
	}

	public static void handlePacket(ClientboundLevelChunkPacket p) {
		MapManager manager = MapManager.inst;

		Level level = Minecraft.getInstance().level;

		if (level != null && p.isFullChunk()) {
			ChunkAccess chunkAccess = level.getChunk(p.getX(), p.getZ(), ChunkStatus.FULL, false);

			if (chunkAccess != null) {
				long biomeZoomSeed = ((BiomeManagerFTBC) level.getBiomeManager()).getBiomeZoomSeedFTBC();
				ChunkBiomeContainer biomeContainer = p.getBiomes() == null ? null : new ChunkBiomeContainer(level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), p.getBiomes());
				queueOrExecute(new ChunkUpdateTask(manager, level, chunkAccess, new ChunkPos(p.getX(), p.getZ()), biomeContainer, biomeZoomSeed, ChunkUpdateTask.ALL_BLOCKS));
			}
		}

	}

	public static void queueOrExecute(MapTask task) {
		// Implement this config later
		FTBChunks.EXECUTOR.execute(task);
	}

	public static void handlePacket(ClientboundBlockUpdatePacket p) {
		rerender(p.getPos());
	}
}
