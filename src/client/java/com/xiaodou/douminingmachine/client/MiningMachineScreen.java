package com.xiaodou.douminingmachine.client;

import com.xiaodou.douminingmachine.menu.MiningMachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MiningMachineScreen extends AbstractContainerScreen<MiningMachineMenu> {
    private static final int PROGRESS_MAX = 80;

    private Button startButton;
    private Button minusChunkButton;
    private Button plusChunkButton;
    private Button minusSpeedButton;
    private Button plusSpeedButton;
    private Button shovelModeButton;

    public MiningMachineScreen(MiningMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();

        this.minusChunkButton = Button.builder(Component.literal("-"), button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            }
        }).bounds(this.leftPos + 76, this.topPos + 47, 14, 14).build();
        this.addRenderableWidget(this.minusChunkButton);

        this.plusChunkButton = Button.builder(Component.literal("+"), button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 2);
            }
        }).bounds(this.leftPos + 118, this.topPos + 47, 14, 14).build();
        this.addRenderableWidget(this.plusChunkButton);

        this.minusSpeedButton = Button.builder(Component.literal("-"), button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 3);
            }
        }).bounds(this.leftPos + 136, this.topPos + 47, 14, 14).build();
        this.addRenderableWidget(this.minusSpeedButton);

        this.plusSpeedButton = Button.builder(Component.literal("+"), button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 4);
            }
        }).bounds(this.leftPos + 157, this.topPos + 47, 14, 14).build();
        this.addRenderableWidget(this.plusSpeedButton);

        this.startButton = Button.builder(getButtonText(), button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
        }).bounds(this.leftPos + 76, this.topPos + 64, isShovelScreen() ? 55 : 92, 17).build();
        this.addRenderableWidget(this.startButton);

        if (isShovelScreen()) {
            this.shovelModeButton = Button.builder(getShovelModeButtonText(), button -> {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 5);
                }
            }).bounds(this.leftPos + 134, this.topPos + 64, 34, 17).build();
            this.addRenderableWidget(this.shovelModeButton);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.startButton != null) {
            this.startButton.setMessage(getButtonText());
        }
        if (this.shovelModeButton != null) {
            this.shovelModeButton.setMessage(getShovelModeButtonText());
        }
    }

    private Component getButtonText() {
        String action = this.menu.isActive() ? "停止" : "开始";
        if (isShovelScreen()) {
            return Component.literal(action);
        }
        String titleText = this.title.getString();
        if (titleText.contains("挖石")) {
            return Component.literal(action + "挖石");
        }
        return Component.literal(action + "挖矿");
    }

    private boolean isShovelScreen() {
        return this.title.getString().contains("铲土");
    }

    private Component getShovelModeButtonText() {
        return Component.literal(this.menu.isShovelWholeChunkMode() ? "整区" : "同层");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);

        int x = this.leftPos;
        int y = this.topPos;

        // 主背景。
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        graphics.fill(x + 3, y + 3, x + this.imageWidth - 3, y + this.imageHeight - 3, 0xFFE4E4E4);

        // 左侧过滤区背景。
        graphics.fill(x + 5, y + 15, x + 66, y + 76, 0xFFA8A8A8);
        graphics.fill(x + 6, y + 16, x + 65, y + 75, 0xFFD7D7D7);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(graphics, x + 8 + col * 18, y + 18 + row * 18);
            }
        }

        // 右侧信息区背景。
        graphics.fill(x + 72, y + 7, x + 171, y + 82, 0xFFD0D0D0);
        graphics.fill(x + 73, y + 8, x + 170, y + 81, 0xFFE9E9E9);

        // 进度条。
        int progressWidth = 90;
        int progressFill = Math.min(progressWidth, Math.max(0, this.menu.getMiningProgress() * progressWidth / PROGRESS_MAX));
        graphics.fill(x + 76, y + 33, x + 76 + progressWidth, y + 39, 0xFF3A3A3A);
        graphics.fill(x + 77, y + 34, x + 77 + progressWidth - 2, y + 38, 0xFF555555);
        if (progressFill > 0) {
            graphics.fill(x + 77, y + 34, x + 77 + progressFill, y + 38, 0xFF55CC22);
        }

        // 燃料槽。
        drawSlot(graphics, x + 152, y + 18);

        // 玩家背包。
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x + 8 + col * 18, y + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x + 8 + col * 18, y + 142);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 标题居中显示，避免和过滤格子重叠。
        graphics.text(this.font, this.title, 75, 4, 0xFF404040, false);

        graphics.text(this.font, Component.literal(getFilterLabel()), 8, 7, 0xFF404040, false);
        graphics.text(this.font, Component.literal("物品栏"), 8, 74, 0xFF404040, false);

        graphics.text(this.font, Component.literal("燃料:" + this.menu.getFuelTicks()), 76, 12, 0xFF404040, false);
        graphics.text(this.font, Component.literal("进度:" + this.menu.getMiningProgress() + "/80"), 76, 22, 0xFF404040, false);

        graphics.text(this.font, Component.literal("区块"), 92, 42, 0xFF404040, false);
        graphics.text(this.font, Component.literal(this.menu.getChunkCount() + "/100"), 92, 54, 0xFF006600, false);

        graphics.text(this.font, Component.literal("速" + this.menu.getSpeedLevel() + "/10"), 136, 42, 0xFFAA5500, false);
        graphics.text(this.font, Component.literal("耗" + this.menu.getFuelMultiplier() + "x"), 136, 54, 0xFFAA0000, false);
        if (isShovelScreen()) {
            graphics.text(this.font, Component.literal("模式"), 77, 72, 0xFF404040, false);
        }

        graphics.text(this.font, Component.literal("煤炭"), 147, 8, 0xFF404040, false);
    }

    private String getFilterLabel() {
        String titleText = this.title.getString();
        if (titleText.contains("挖矿")) {
            return "矿石/矿物";
        }
        if (titleText.contains("挖石")) {
            return "筛选石头";
        }
        if (titleText.contains("铲土")) {
            return "筛选泥土";
        }
        return "筛选";
    }

    private void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF666666);
        graphics.fill(x, y, x + 16, y + 16, 0xFFEEEEEE);
        graphics.fill(x, y, x + 16, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + 16, 0xFFFFFFFF);
        graphics.fill(x + 15, y + 1, x + 16, y + 16, 0xFF9A9A9A);
        graphics.fill(x + 1, y + 15, x + 16, y + 16, 0xFF9A9A9A);
    }
}
