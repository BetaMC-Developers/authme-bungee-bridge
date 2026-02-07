package org.betamc.authmebungeebridge;

import com.google.common.eventbus.Subscribe;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public class AuthMeBungeeBridge extends Plugin implements Listener {

    private static final String PREFIX = "[AuthMeBungeeBridge] ";
    private final Set<String> authenticated = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
    }

    public boolean isUnauthenticated(ProxiedPlayer player) {
        return !this.authenticated.contains(player.getName().toLowerCase(Locale.ROOT));
    }

    @Subscribe
    private void onServerConnect(ServerConnectEvent event) {
        if (event.getPlayer().getServer() == null)
            return;

        if (isUnauthenticated(event.getPlayer())) {
            event.setCancelled(true);
            getProxy().getLogger().log(Level.INFO, PREFIX + event.getPlayer().getName() + " tried to connect to server " + event.getTarget().getName() + " while being unauthenticated");
        }
    }

    @Subscribe
    private void onChat(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer))
            return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        String message = event.getMessage().toLowerCase(Locale.ROOT);
        if (isUnauthenticated(player) && !message.startsWith("/login") && !message.startsWith("/register")) {
            event.setCancelled(true);
            getProxy().getLogger().log(Level.INFO, PREFIX + player.getName() + " tried to send message while being unauthenticated: '" + event.getMessage() + "'");
        }
    }

    @Subscribe
    private void onPluginMessage(PluginMessageEvent event) {
        if (!(event.getSender() instanceof Server) || !(event.getReceiver() instanceof ProxiedPlayer))
            return;

        ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();
        ServerInfo server = ((Server) event.getSender()).getInfo();

        if (event.getTag().equals("authme:login")) {
            event.setCancelled(true);
            handleLogin(player, server, event.getData());
        } else if (event.getTag().equals("authme:logout")) {
            event.setCancelled(true);
            handleLogout(player, server, event.getData());
        }
    }

    private void handleLogin(ProxiedPlayer player, ServerInfo server, byte[] data) {
        getProxy().getLogger().log(Level.INFO, PREFIX + "Received authme:login for " + player.getName() + " from server " + server.getName());

        String name;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            name = in.readUTF();
        } catch (IOException e) {
            getProxy().getLogger().log(Level.SEVERE, PREFIX + "Failed to read authme:login for " + player.getName(), e);
            return;
        }

        if (!name.toLowerCase(Locale.ROOT).equals(player.getName().toLowerCase(Locale.ROOT))) {
            getProxy().getLogger().log(Level.WARNING, PREFIX + "The name '" + name + "' specified in authme:login does not match the name of the target player " + player.getName());
            return;
        }

        this.authenticated.add(name.toLowerCase(Locale.ROOT));
        getProxy().getLogger().log(Level.INFO, PREFIX + "Added " + player.getName() + " to the authentication cache");
    }

    private void handleLogout(ProxiedPlayer player, ServerInfo server, byte[] data) {
        getProxy().getLogger().log(Level.INFO, PREFIX + "Received authme:logout for " + player.getName() + " from server " + server.getName());

        String name;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            name = in.readUTF();
        } catch (IOException e) {
            getProxy().getLogger().log(Level.SEVERE, PREFIX + "Failed to read authme:logout for " + player.getName(), e);
            return;
        }

        this.authenticated.remove(name.toLowerCase(Locale.ROOT));
        getProxy().getLogger().log(Level.INFO, PREFIX + "Removed " + player.getName() + " from the authentication cache");
    }

}
