package org.betamc.authmebungeebridge;

import lombok.Data;

@Data
public class PlayerAuth {

    private final String name;
    private final String hash;
    private final String ip;
    private final long lastLogin;
}
