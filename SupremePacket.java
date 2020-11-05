package io.nathan.supremepacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

public class NathanPacket implements Listener {
	
	private Map<UUID, Data> datas;

	public SupremePacket(JavaPlugin plugin) {
		this.datas = new HashMap<>();
		Bukkit.getOnlinePlayers().forEach(this::injectPlayer);
		Bukkit.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onJoin(PlayerJoinEvent e) {
				NathanPacket.this.injectPlayer(e.getPlayer());
			}

			@EventHandler
			public void onQuit(PlayerQuitEvent e) {
				NathanPacket.this.removePlayer(e.getPlayer());
			}
		}, plugin);
	}

	public void onDisable() {
		Bukkit.getOnlinePlayers().forEach(this::removePlayer);
	}

	private void injectPlayer(Player player) {
		ChannelDuplexHandler channelDuplexHandler = new ChannelDuplexHandler() {
			@Override
			public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
				super.write(ctx, packet, promise);
			}

			@Override
			public void channelRead(ChannelHandlerContext channelHandlerContext, Object packet) throws Exception {
				Data data;
				if (datas.containsKey(player.getUniqueId())
						&& packet.getClass().getName().endsWith((data = datas.get(player.getUniqueId())).packetName)) {
					data.rs.apply(packet);
					datas.remove(player.getUniqueId());
				} else
					super.channelRead(channelHandlerContext, packet);
			}
		};

		try {
			Object handle = player.getClass().getMethod("getHandle").invoke(player);
			Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
			Object networkManager = playerConnection.getClass().getField("networkManager").get(playerConnection);
			Object channel = networkManager.getClass().getField("channel").get(networkManager);

			ChannelPipeline pipeline = (ChannelPipeline) channel.getClass().getMethod("pipeline").invoke(channel);
			pipeline.addBefore("packet_handler", player.getName() + "nathan", channelDuplexHandler);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void removePlayer(Player player) {
		try {
			Object handle = player.getClass().getMethod("getHandle").invoke(player);
			Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
			Object networkManager = playerConnection.getClass().getField("networkManager").get(playerConnection);

			Channel channel = (Channel) networkManager.getClass().getField("channel").get(networkManager);
			channel.eventLoop().submit(() -> {
				channel.pipeline().remove(player.getName() + "nathan");
				return null;
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void next(UUID uuid, String packetName, Result<Object> rs) {
		datas.put(uuid, new Data(packetName, rs));
	}

	public static Class<?> getNMSClass(String name) {
		try {
			return Class.forName("net.minecraft.server."
					+ Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + "." + name);
		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static Class<?> getNMSBClass(String name) {
		try {
			return Class.forName("org.bukkit.craftbukkit."
					+ Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + "." + name);
		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static void sendPacket(Player player, Object packet) {
		try {
			Object handle = player.getClass().getMethod("getHandle").invoke(player);
			Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
			playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static Object world(World w) {
		try {
			return w.getClass().getMethod("getHandle").invoke(w);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@FunctionalInterface
	public static interface Result<A> {
		public void apply(A obj);
	}

	private class Data {
		private String packetName;
		private Result<Object> rs;

		private Data(String packetName, Result<Object> rs) {
			this.packetName = packetName;
			this.rs = rs;
		}
	}
}
