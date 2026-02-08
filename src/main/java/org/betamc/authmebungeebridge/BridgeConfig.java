package org.betamc.authmebungeebridge;

import lombok.Getter;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.util.logging.Level;

@Getter
@ConfigSerializable
public class BridgeConfig {

    private String authServer = "main";
    private String secretKey = "change_me";

    public static BridgeConfig load(AuthMeBungeeBridge plugin) {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(plugin.getDataFolder().toPath().resolve("config.yml"))
                .indent(2)
                .nodeStyle(NodeStyle.BLOCK)
                .build();

        try {
            BridgeConfig config = loader.load().get(BridgeConfig.class);
            loader.save(loader.createNode().set(config));
            return config;
        } catch (ConfigurateException e) {
            plugin.getProxy().getLogger().log(Level.SEVERE, "Failed to load configuration");
            throw new RuntimeException(e);
        }
    }

}
