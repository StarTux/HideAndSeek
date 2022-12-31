package com.cavetale.hideandseek;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Tag {
    protected String worldName;
    protected Map<UUID, Integer> fairness = new HashMap<>();
    protected Map<UUID, Integer> scores = new HashMap<>();
    protected int gameTime = 60 * 5;
    protected int hideTime = 30;
    protected int glowTime = 30;
    protected int bonusTime = 60;
    protected boolean event;
    protected boolean pause;
}
