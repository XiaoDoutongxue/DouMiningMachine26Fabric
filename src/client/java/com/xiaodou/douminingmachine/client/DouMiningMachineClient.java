package com.xiaodou.douminingmachine.client;

import com.xiaodou.douminingmachine.DouMiningMachine;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class DouMiningMachineClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(DouMiningMachine.MINING_MACHINE_MENU, MiningMachineScreen::new);
    }
}
