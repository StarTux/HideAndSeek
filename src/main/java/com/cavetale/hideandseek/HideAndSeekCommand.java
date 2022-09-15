package com.cavetale.hideandseek;

import com.cavetale.core.command.AbstractCommand;

public final class HideAndSeekCommand extends AbstractCommand<HideAndSeekPlugin> {
    protected HideAndSeekCommand(final HideAndSeekPlugin plugin) {
        super(plugin, "hideandseek");
    }

    @Override
    protected void onEnable() {
    }
}
