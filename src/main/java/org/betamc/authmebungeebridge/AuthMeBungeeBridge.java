package org.betamc.authmebungeebridge;

import com.google.common.eventbus.Subscribe;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class AuthMeBungeeBridge extends Plugin implements Listener {

    private static final String PREFIX = "[AuthMeBungeeBridge] ";
    private BridgeConfig config;
    private final Map<String, PlayerAuth> authenticated = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void onEnable() {
        this.config = BridgeConfig.load(this);
        getProxy().registerChannel("authme:login");
        getProxy().registerChannel("authme:logout");
        getProxy().getPluginManager().registerListener(this, this);
    }

    public PlayerAuth getAuthentication(ProxiedPlayer player) {
        return this.authenticated.get(player.getName().toLowerCase(Locale.ROOT));
    }

    public boolean isAuthenticated(ProxiedPlayer player) {
        return getAuthentication(player) != null;
    }

    @Subscribe
    private void onServerConnect(ServerConnectEvent event) {
        if (event.getPlayer().getServer() == null) return;
        PlayerAuth auth = getAuthentication(event.getPlayer());
        if (auth != null) {
            if (event.getTarget().getName().equals(this.config.getAuthServer())) {
                sendAuthMeLogin(auth, event.getPlayer(), event.getTarget());
            }
        } else {
            event.setCancelled(true);
            getProxy().getLogger().log(Level.INFO, PREFIX + event.getPlayer().getName() + " tried to connect to server " + event.getTarget().getName() + " while being unauthenticated");
        }
    }

    @Subscribe
    private void onChat(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        String message = event.getMessage().toLowerCase(Locale.ROOT);
        if (!isAuthenticated(player) && !message.startsWith("/login") && !message.startsWith("/register")) {
            event.setCancelled(true);
            getProxy().getLogger().log(Level.INFO, PREFIX + player.getName() + " tried to send message while being unauthenticated: '" + event.getMessage() + "'");
        }
    }

    @Subscribe
    private void onPluginMessage(PluginMessageEvent event) {
        ProxiedPlayer player = event.getReceiver();
        ServerInfo server = event.getSender().getInfo();

        if (event.getTag().equals("authme:login")) {
            event.setCancelled(true);
            handleAuthMeLogin(player, server, event.getData());
        } else if (event.getTag().equals("authme:logout")) {
            event.setCancelled(true);
            handleAuthMeLogout(player, server, event.getData());
        }
    }

    @Subscribe
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        this.authenticated.remove(event.getPlayer().getName().toLowerCase(Locale.ROOT));
        getProxy().getLogger().log(Level.INFO, PREFIX + "Removed " + event.getPlayer().getName() + " from the authentication cache");
    }

    private void sendAuthMeLogin(PlayerAuth auth, ProxiedPlayer player, ServerInfo target) {
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(data);

            out.writeUTF(this.config.getSecretKey());
            out.writeUTF(auth.getName());
            out.writeUTF(auth.getHash());
            out.writeUTF(auth.getIp());
            out.writeLong(auth.getLastLogin());

            target.sendData("authme:login", data.toByteArray());
            getProxy().getLogger().log(Level.INFO, PREFIX + "Sent authme:login for " + player.getName() + " to server " + target.getName());
        } catch (IOException e) {
            getProxy().getLogger().log(Level.SEVERE, PREFIX + "Failed to send authme:login for " + player.getName() + " to server " + target.getName(), e);
        }
    }

    private void handleAuthMeLogin(ProxiedPlayer player, ServerInfo server, byte[] data) {
        getProxy().getLogger().log(Level.INFO, PREFIX + "Received authme:login for " + player.getName() + " from server " + server.getName());

        String secretKey;
        String name;
        String hash;
        String ip;
        long lastLogin;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            secretKey = in.readUTF();
            name = in.readUTF();
            hash = in.readUTF();
            ip = in.readUTF();
            lastLogin = in.readLong();
        } catch (IOException e) {
            getProxy().getLogger().log(Level.SEVERE, PREFIX + "Failed to read authme:login for " + player.getName(), e);
            return;
        }

        if (!secretKey.equals(this.config.getSecretKey())) {
            getProxy().getLogger().log(Level.WARNING, PREFIX + "Received unauthenticated authme:login for " + player.getName() + " from server " + server.getName());
            return;
        }

        if (!name.toLowerCase(Locale.ROOT).equals(player.getName().toLowerCase(Locale.ROOT))) {
            getProxy().getLogger().log(Level.WARNING, PREFIX + "The name '" + name + "' specified in authme:login does not match the name of the target player " + player.getName());
            return;
        }

        PlayerAuth auth = new PlayerAuth(name, hash, ip, lastLogin);
        this.authenticated.put(name.toLowerCase(Locale.ROOT), auth);
        getProxy().getLogger().log(Level.INFO, PREFIX + "Added " + player.getName() + " to the authentication cache");
    }

    private void handleAuthMeLogout(ProxiedPlayer player, ServerInfo server, byte[] data) {
        getProxy().getLogger().log(Level.INFO, PREFIX + "Received authme:logout for " + player.getName() + " from server " + server.getName());

        String secretKey;
        String name;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            secretKey = in.readUTF();
            name = in.readUTF();
        } catch (IOException e) {
            getProxy().getLogger().log(Level.SEVERE, PREFIX + "Failed to read authme:logout for " + player.getName(), e);
            return;
        }

        if (!secretKey.equals(this.config.getSecretKey())) {
            getProxy().getLogger().log(Level.WARNING, PREFIX + "Received unauthenticated authme:logout for " + player.getName() + " from server " + server.getName());
            return;
        }

        this.authenticated.remove(name.toLowerCase(Locale.ROOT));
        getProxy().getLogger().log(Level.INFO, PREFIX + "Removed " + player.getName() + " from the authentication cache");
    }

}
