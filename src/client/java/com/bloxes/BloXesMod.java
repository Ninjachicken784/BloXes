package com.bloxes;

import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class BloXesMod implements ClientModInitializer {
	public static final String MOD_ID = "bloxes";

	/** A screen to open on the next tick (opening it straight from a command would be closed with the chat). */
	private static Supplier<Screen> pendingScreen;

	@Override
	public void onInitializeClient() {
		BloxConfig.load();
		BloxHud.register();

		// The edge of the area, drawn in the world.
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(OutlineRenderer::render);

		// Block counting, and opening screens requested by commands.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			AreaScanner.INSTANCE.tick(client);
			if (pendingScreen != null) {
				Supplier<Screen> supplier = pendingScreen;
				pendingScreen = null;
				client.setScreen(supplier.get());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		// Commands are case sensitive, so register both spellings.
		for (String name : new String[] { "bloxes", "BloXes" }) {
			dispatcher.register(ClientCommands.literal(name)
					.executes(context -> {
						pendingScreen = SettingsScreen::new;
						return 1;
					})
					.then(ClientCommands.literal("here").executes(context -> {
						SettingsScreen.centerOnPlayer();
						context.getSource().sendFeedback(Component.literal("BloXes: area centered on you."));
						return 1;
					}))
					.then(ClientCommands.literal("circle").executes(context -> {
						BloxConfig.get().shape = BloxConfig.Shape.CIRCLE;
						BloxConfig.save();
						context.getSource().sendFeedback(Component.literal("BloXes: circle."));
						return 1;
					}))
					.then(ClientCommands.literal("square").executes(context -> {
						BloxConfig.get().shape = BloxConfig.Shape.SQUARE;
						BloxConfig.save();
						context.getSource().sendFeedback(Component.literal("BloXes: square."));
						return 1;
					}))
					.then(ClientCommands.literal("off").executes(context -> {
						BloxConfig.get().areaActive = false;
						BloxConfig.save();
						context.getSource().sendFeedback(Component.literal("BloXes: area off."));
						return 1;
					}))
					.then(ClientCommands.literal("list").executes(context -> {
						pendingScreen = () -> new BlockListScreen(null);
						return 1;
					})));
		}
	}
}
