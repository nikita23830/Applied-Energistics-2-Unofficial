/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import appeng.container.AEBaseContainer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.lwjgl.opengl.GL11;

import com.google.common.base.Joiner;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.DimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.IGuiTooltipHandler;
import appeng.client.gui.widgets.GuiAeButton;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.render.BlockPosHighlighter;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingItemInterface;
import appeng.core.sync.packets.PacketCraftingRemainingOperations;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.api.config.CraftingAllow;
import appeng.api.config.CraftingAllow;
import appeng.helpers.InventoryAction;
import appeng.core.sync.packets.PacketInventoryAction;

public class GuiCraftingCPU extends AEBaseGui implements ISortSource, IGuiTooltipHandler {

    protected static final int GUI_HEIGHT = 184;
    protected static final int GUI_WIDTH = 238;

    protected static final int TEXTURE_BELOW_TOP_ROW_Y = 41;
    protected static final int TEXTURE_ABOVE_BOTTOM_ROW_Y = 51;
    protected static final int DISPLAYED_ROWS = 6;

    protected static final int SECTION_LENGTH = 67;
    protected static final int SECTION_HEIGHT = 23;

    protected static final int SCROLLBAR_TOP = 19;
    protected static final int SCROLLBAR_LEFT = 218;
    protected static final int SCROLLBAR_HEIGHT = 137;

    private static final int CANCEL_LEFT_OFFSET = 163;
    private static final int CANCEL_TOP_OFFSET = 25;
    private static final int CANCEL_HEIGHT = 20;
    private static final int CANCEL_WIDTH = 50;

    private static final int TITLE_TOP_OFFSET = 7;
    private static final int TITLE_LEFT_OFFSET = 8;

    private static final int ITEMSTACK_LEFT_OFFSET = 9;
    private static final int ITEMSTACK_TOP_OFFSET = 22;
    private static final int ITEMS_PER_ROW = 3;

    private final ContainerCraftingCPU craftingCpu;

    protected IItemList<IAEItemStack> storage = AEApi.instance().storage().createItemList();
    protected IItemList<IAEItemStack> active = AEApi.instance().storage().createItemList();
    protected IItemList<IAEItemStack> pending = AEApi.instance().storage().createItemList();
    private IAEItemStack hoveredAEStack = null;

    protected int rows = DISPLAYED_ROWS;

    private class RemainingOperations implements ITooltip {

        private long refreshTick = System.currentTimeMillis();
        private long lastWorkingTick = 0;

        private int remainingOperations = 0;

        public long getLastWorkingTick() {
            return lastWorkingTick;
        }

        public long getRefreshTick() {
            return refreshTick;
        }

        public void setLastWorkingTick(long lastWorkingTick) {
            this.lastWorkingTick = lastWorkingTick;
        }

        public void setRefreshTick(long refreshTick) {
            this.refreshTick = refreshTick;
        }

        @Override
        public String getMessage() {
            return GuiText.RemainingOperations.getLocal();
        }

        @Override
        public int xPos() {
            return guiLeft + TITLE_LEFT_OFFSET + 200 - this.getStringWidth();
        }

        @Override
        public int yPos() {
            return guiTop + TITLE_TOP_OFFSET;
        }

        @Override
        public int getWidth() {
            return this.getStringWidth();
        }

        @Override
        public int getHeight() {
            return fontRendererObj.FONT_HEIGHT;
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        public void setRemainingOperations(int remainingOperations) {
            this.remainingOperations = remainingOperations;
        }

        public int getRemainingOperations() {
            return this.remainingOperations;
        }

        public int getStringWidth() {
            return fontRendererObj.getStringWidth(String.valueOf(this.remainingOperations));
        }
    }

    protected List<IAEItemStack> visual = new ArrayList<>();
    private GuiButton cancel;
    protected List<IAEItemStack> visualHiddenStored = new ArrayList<>();
    protected GuiImgButton toggleHideStored;
    protected boolean hideStored;
    private int tooltip = -1;
    private final RemainingOperations remainingOperations = new RemainingOperations();
    private ItemStack hoveredStack;
    private ItemStack hoveredNbtStack;
    private GuiAeButton findNext;
    private GuiAeButton findPrev;
    private GuiImgButton changeAllow;
    private MEGuiTextField searchField;
    private ArrayList<Integer> goToData = new ArrayList<>();
    private int searchGotoIndex = -1;
    private IAEItemStack needHighlight;

    public GuiCraftingCPU(final InventoryPlayer inventoryPlayer, final Object te) {
        this(new ContainerCraftingCPU(inventoryPlayer, te));
    }

    protected GuiCraftingCPU(final ContainerCraftingCPU container) {
        super(container);
        this.craftingCpu = container;
        this.ySize = GUI_HEIGHT;
        this.xSize = GUI_WIDTH;
        this.hideStored = AEConfig.instance.getConfigManager().getSetting(Settings.HIDE_STORED) == YesNo.YES;

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
    }

    public void clearItems() {
        this.storage = AEApi.instance().storage().createItemList();
        this.active = AEApi.instance().storage().createItemList();
        this.pending = AEApi.instance().storage().createItemList();
        this.visual = new ArrayList<>();
        this.visualHiddenStored = new ArrayList<>();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);
        if (this.cancel == btn) {
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("TileCrafting.Cancel", "Cancel"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        } else if (this.toggleHideStored == btn) {
            this.hideStored ^= true;
            AEConfig.instance.getConfigManager().putSetting(Settings.HIDE_STORED, hideStored ? YesNo.YES : YesNo.NO);
            this.toggleHideStored.set(hideStored ? YesNo.YES : YesNo.NO);
            hideStoredSorting();
            this.setScrollBar();
            updateSearchGoToList(true);
        } else if (btn == this.findNext) {
            searchGoTo(true);
        } else if (btn == this.findPrev) {
            searchGoTo(false);
        } else if (btn == this.changeAllow) {
            String msg = String.valueOf(((CraftingAllow) this.changeAllow.getCurrentValue()).ordinal());
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("TileCrafting.Allow", msg));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }

    }

    public IAEItemStack getHoveredAEStack() {
        return hoveredAEStack;
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        if (isShiftKeyDown() && this.hoveredNbtStack != null) {
            NBTTagCompound data = Platform.openNbtData(this.hoveredNbtStack);
            // when using the highlight feature in the crafting GUI we want to show all the interfaces
            // that currently received items so the player can see if the items are processed properly
            BlockPosHighlighter.highlightBlocks(
                    mc.thePlayer,
                    DimensionalCoord.readAsListFromNBT(data),
                    PlayerMessages.InterfaceHighlighted.getName(),
                    PlayerMessages.InterfaceInOtherDim.getName());
            mc.thePlayer.closeScreen();
        } else if (hoveredAEStack != null && btn == 2) {
            ((AEBaseContainer) inventorySlots).setTargetStack(hoveredAEStack);
            final PacketInventoryAction p = new PacketInventoryAction(
                    InventoryAction.AUTO_CRAFT,
                    inventorySlots.inventorySlots.size(),
                    0);
            NetworkHandler.instance.sendToServer(p);
        }
        super.mouseClicked(xCoord, yCoord, btn);
        this.searchField.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.setScrollBar();
        this.cancel = new GuiButton(
                0,
                this.guiLeft + CANCEL_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                CANCEL_WIDTH,
                CANCEL_HEIGHT,
                GuiText.Cancel.getLocal());
        this.toggleHideStored = new GuiImgButton(
                this.guiLeft + 221,
                this.guiTop + this.ySize - 19,
                Settings.HIDE_STORED,
                AEConfig.instance.getConfigManager().getSetting(Settings.HIDE_STORED));
        this.buttonList.add(this.toggleHideStored);
        this.buttonList.add(this.cancel);

        this.searchField = new MEGuiTextField(52, 12, "Search") {

            @Override
            public void onTextChange(String oldText) {
                super.onTextChange(oldText);
                updateSearchGoToList(true);
            }
        };
        this.searchField.x = this.guiLeft + this.xSize - 101;
        this.searchField.y = this.guiTop + 5;

        this.findPrev = new GuiAeButton(
                0,
                this.guiLeft + this.xSize - 48,
                this.guiTop + 6,
                10,
                10,
                "↑",
                ButtonToolTips.SearchGotoPrev.getLocal());
        this.buttonList.add(this.findPrev);

        this.findNext = new GuiAeButton(
                0,
                this.guiLeft + this.xSize - 36,
                this.guiTop + 6,
                10,
                10,
                "↓",
                ButtonToolTips.SearchGotoNext.getLocal());
        this.buttonList.add(this.findNext);

        this.changeAllow = new GuiImgButton(
                this.guiLeft - 20,
                this.guiTop + 2,
                Settings.CRAFTING_ALLOW,
                CraftingAllow.ALLOW_ALL);
        this.buttonList.add(this.changeAllow);
    }

    private void setScrollBar() {
        int size;
        if (this.hideStored) {
            size = this.visualHiddenStored.size();
        } else {
            size = this.visual.size();
        }
        this.getScrollBar().setTop(SCROLLBAR_TOP).setLeft(SCROLLBAR_LEFT).setHeight(SCROLLBAR_HEIGHT);
        this.getScrollBar().setRange(0, (size + 2) / ITEMS_PER_ROW - rows, 1);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cancel.enabled = !this.visual.isEmpty();
        this.changeAllow.set(CraftingAllow.values()[this.craftingCpu.allow]);

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        final int offY = SECTION_HEIGHT;
        int y = 0;
        int x = 0;
        for (int z = 0; z <= ITEMS_PER_ROW * rows; z++) {
            final int minX = gx + ITEMSTACK_LEFT_OFFSET + x * SECTION_LENGTH;
            final int minY = gy + ITEMSTACK_TOP_OFFSET + y * offY;

            if (minX < mouseX && minX + SECTION_LENGTH > mouseX) {
                if (minY < mouseY && minY + offY > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x == ITEMS_PER_ROW) {
                y++;
                x = 0;
            }
        }

        this.handleTooltip(mouseX, mouseY, remainingOperations);
        super.drawScreen(mouseX, mouseY, btn);
    }

    private void updateSearchGoToList(boolean dropIndex) {
        needHighlight = null;
        goToData.clear();
        if (this.searchField.getText().isEmpty()) return;
        String s = this.searchField.getText().toLowerCase();
        int visCount = 0;
        for (IAEItemStack aeis : hideStored ? this.visualHiddenStored : this.visual) {
            if (aeis != null && Platform.getItemDisplayName(aeis).toLowerCase().contains(s)) {
                goToData.add(visCount);
            }
            visCount++;
        }
        if (dropIndex) {
            searchGotoIndex = -1;
            searchGoTo(true);
        }
    }

    private void searchGoTo(boolean forward) {
        String s = this.searchField.getText().toLowerCase();
        if (s.isEmpty() || goToData.isEmpty()) return;
        if (forward) {
            searchGotoIndex++;
            if (searchGotoIndex >= goToData.size()) searchGotoIndex = 0;
        } else {
            if (searchGotoIndex <= 0) searchGotoIndex = goToData.size();
            searchGotoIndex--;
        }

        List<IAEItemStack> visualTemp;
        if (this.hideStored) {
            visualTemp = this.visualHiddenStored;
        } else {
            visualTemp = this.visual;
        }

        IAEItemStack aeis = visualTemp.get(goToData.get(searchGotoIndex));
        this.getScrollBar().setCurrentScroll(goToData.get(searchGotoIndex) / 3 - this.rows / 2);
        needHighlight = aeis.copy();
    }

    private void updateRemainingOperations() {
        int interval = 1000;
        if (this.remainingOperations.getRefreshTick() >= this.remainingOperations.getLastWorkingTick() + interval) {
            try {
                NetworkHandler.instance.sendToServer(new PacketCraftingRemainingOperations());
            } catch (IOException ignored) {}
            this.remainingOperations.setLastWorkingTick(this.remainingOperations.refreshTick);
        } else {
            this.remainingOperations.setRefreshTick(System.currentTimeMillis());
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        String title = this.getGuiDisplayName(GuiText.CraftingStatus.getLocal());

        if (this.craftingCpu.getElapsedTime() > 0 && !this.visual.isEmpty()) {
            final long elapsedInMilliseconds = TimeUnit.MILLISECONDS
                    .convert(this.craftingCpu.getElapsedTime(), TimeUnit.NANOSECONDS);
            final String elapsedTimeText = DurationFormatUtils
                    .formatDuration(elapsedInMilliseconds, GuiText.ETAFormat.getLocal());

            // If title is empty, don't show that ' - '
            if (title.length() == 0) {
                title = elapsedTimeText;
            } else {
                title += " - " + elapsedTimeText;
            }
        }
        updateRemainingOperations();
        this.fontRendererObj.drawString(
                String.valueOf(remainingOperations.getRemainingOperations()),
                TITLE_LEFT_OFFSET + 128 - this.remainingOperations.getStringWidth(),
                TITLE_TOP_OFFSET,
                GuiColors.CraftingCPUTitle.getColor());

        this.fontRendererObj
                .drawString(title, TITLE_LEFT_OFFSET, TITLE_TOP_OFFSET, GuiColors.CraftingCPUTitle.getColor());

        int x = 0;
        int y = 0;
        final int viewStart = this.getScrollBar().getCurrentScroll() * ITEMS_PER_ROW;
        final int viewEnd = viewStart + ITEMS_PER_ROW * rows;

        String dspToolTip = "";
        final List<String> lineList = new LinkedList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        hoveredStack = null;

        final int offY = 23;

        final ReadableNumberConverter converter = ReadableNumberConverter.INSTANCE;
        List<IAEItemStack> visualTemp;
        if (this.hideStored) {
            visualTemp = this.visualHiddenStored;
        } else {
            visualTemp = this.visual;
        }
        for (int z = viewStart; z < Math.min(viewEnd, visualTemp.size()); z++) {
            final IAEItemStack refStack = visualTemp.get(z); // repo.getReferenceItem( z );
            if (refStack != null) {
                GL11.glPushMatrix();
                GL11.glScaled(0.5, 0.5, 0.5);

                final IAEItemStack stored = this.storage.findPrecise(refStack);
                final IAEItemStack activeStack = this.active.findPrecise(refStack);
                final IAEItemStack pendingStack = this.pending.findPrecise(refStack);

                int lines = 0;

                if (stored != null && stored.getStackSize() > 0) {
                    lines++;
                }
                boolean active = false;
                if (activeStack != null && activeStack.getStackSize() > 0) {
                    lines++;
                    active = true;
                }
                boolean scheduled = false;
                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    lines++;
                    scheduled = true;
                }

                if (AEConfig.instance.useColoredCraftingStatus && (active || scheduled)) {
                    final int bgColor = active ? GuiColors.CraftingCPUActive.getColor()
                            : GuiColors.CraftingCPUInactive.getColor();
                    final int startX = (x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET) * 2;
                    final int startY = ((y * offY + ITEMSTACK_TOP_OFFSET) - 3) * 2;
                    drawRect(startX, startY, startX + (SECTION_LENGTH * 2), startY + (offY * 2) - 2, bgColor);
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                if (stored != null && stored.getStackSize() > 0) {
                    final String str = GuiText.Stored.getLocal() + ": "
                            + converter.toWideReadableForm(stored.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);
                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2,
                            GuiColors.CraftingCPUStored.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.Stored.getLocal() + ": "
                                        + NumberFormat.getInstance().format(stored.getStackSize()));
                    }

                    downY += 5;
                }

                if (activeStack != null && activeStack.getStackSize() > 0) {
                    final String str = GuiText.Crafting.getLocal() + ": "
                            + converter.toWideReadableForm(activeStack.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);

                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2,
                            GuiColors.CraftingCPUAmount.getColor());

                    if (this.tooltip == z - viewStart) {
                        hoveredAEStack = refStack;
                        lineList.add(
                                GuiText.Crafting.getLocal() + ": "
                                        + NumberFormat.getInstance().format(activeStack.getStackSize()));
                    }

                    downY += 5;
                }

                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    final String str = GuiText.Scheduled.getLocal() + ": "
                            + converter.toWideReadableForm(pendingStack.getStackSize());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);

                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2,
                            GuiColors.CraftingCPUScheduled.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.Scheduled.getLocal() + ": "
                                        + NumberFormat.getInstance().format(pendingStack.getStackSize()));
                    }
                }

                GL11.glPopMatrix();
                final int posX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19;
                final int posY = y * offY + ITEMSTACK_TOP_OFFSET;

                final ItemStack is = refStack.copy().getItemStack();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = Platform.getItemDisplayName(is);

                    if (lineList.size() > 0) {
                        addItemTooltip(refStack, lineList);
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 8;
                    toolPosY = y * offY + ITEMSTACK_TOP_OFFSET;

                    hoveredStack = is;
                }

                this.drawItem(posX, posY, is);

                if (!this.searchField.getText().isEmpty() && goToData.contains(z)) {
                    final int startX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET;
                    final int startY = posY - 4;
                    final int color = needHighlight != null && needHighlight.isSameType(refStack)
                            ? GuiColors.SearchGoToHighlight.getColor()
                            : GuiColors.SearchHighlight.getColor();
                    drawVerticalLine(startX, startY, startY + offY, color);
                    drawVerticalLine(startX + SECTION_LENGTH - 1, startY, startY + offY, color);
                    drawHorizontalLine(startX + 1, startX + SECTION_LENGTH - 2, startY + 1, color);
                    drawHorizontalLine(startX + 1, startX + SECTION_LENGTH - 2, startY + offY - 1, color);
                }

                x++;

                if (x > 2) {
                    y++;
                    x = 0;
                }
            }
        }

        if (this.tooltip >= 0 && dspToolTip.length() > 0) {
            this.drawTooltip(toolPosX, toolPosY + 10, dspToolTip);
        }
    }

    @SuppressWarnings("unchecked")
    protected void addItemTooltip(IAEItemStack refStack, List<String> lineList) {
        if (isShiftKeyDown()) {
            final ItemStack is = refStack.copy().getItemStack();
            final List l = is.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
            if (!l.isEmpty()) l.remove(0);
            lineList.addAll(l);
            if (this.hoveredNbtStack == null || this.hoveredNbtStack.getItem() != is.getItem()) {
                this.hoveredNbtStack = is;
                try {
                    NetworkHandler.instance.sendToServer(new PacketCraftingItemInterface(refStack.copy()));
                } catch (Exception ignored) {}
            } else {
                NBTTagCompound data = Platform.openNbtData(this.hoveredNbtStack);
                List<DimensionalCoord> blocks = DimensionalCoord.readAsListFromNBT(data);
                if (blocks.isEmpty()) return;
                for (DimensionalCoord blockPos : blocks) {
                    lineList.add(
                            String.format(
                                    "Dim:%s X:%s Y:%s Z:%s",
                                    blockPos.getDimension(),
                                    blockPos.x,
                                    blockPos.y,
                                    blockPos.z));
                }
                lineList.add(GuiText.HoldShiftClick_HIGHLIGHT_INTERFACE.getLocal());
            }
        } else {
            this.hoveredNbtStack = null;
            lineList.add(GuiText.HoldShiftForTooltip.getLocal());
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craftingcpu.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        drawSearch();
    }

    public void drawSearch() {
        this.bindTexture("guis/searchField.png");
        this.drawTexturedModalRect(this.guiLeft + this.xSize - 101, this.guiTop + 5, 0, 0, 52, 12);
        this.searchField.drawTextBox();
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!(this.searchField.textboxKeyTyped(character, key))) {
            super.keyTyped(character, key);
        }
    }

    public void postUpdate(IAEItemStack is) {
        this.hoveredNbtStack = is.getItemStack();
    }

    public void postUpdate(int remainingOperations) {
        this.remainingOperations.setRemainingOperations(remainingOperations);
    }

    public void postUpdate(final List<IAEItemStack> list, final byte ref) {
        switch (ref) {
            case 0 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.storage, l);
                }
            }
            case 1 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.active, l);
                }
            }
            case 2 -> {
                for (final IAEItemStack l : list) {
                    this.handleInput(this.pending, l);
                }
            }
        }

        for (final IAEItemStack l : list) {
            final long amt = this.getTotal(l);

            if (amt <= 0) {
                this.deleteVisualStack(l);
            } else {
                final IAEItemStack is = this.findVisualStack(l);
                is.setStackSize(amt);
            }
        }

        if (this.hideStored) this.hideStoredSorting();
        updateSearchGoToList(false);
        this.setScrollBar();
    }

    private void handleInput(final IItemList<IAEItemStack> s, final IAEItemStack l) {
        IAEItemStack a = s.findPrecise(l);

        if (l.getStackSize() <= 0) {
            if (a != null) {
                a.reset();
            }
        } else {
            if (a == null) {
                s.add(l.copy());
                a = s.findPrecise(l);
            }

            if (a != null) {
                a.setStackSize(l.getStackSize());
            }
        }
    }

    private long getTotal(final IAEItemStack is) {
        final IAEItemStack a = this.storage.findPrecise(is);
        final IAEItemStack b = this.active.findPrecise(is);
        final IAEItemStack c = this.pending.findPrecise(is);

        long total = 0;

        if (a != null) {
            total += a.getStackSize();
        }

        if (b != null) {
            total += b.getStackSize();
        }

        if (c != null) {
            total += c.getStackSize();
        }

        return total;
    }

    private void deleteVisualStack(final IAEItemStack l) {
        final Iterator<IAEItemStack> i = this.visual.iterator();

        while (i.hasNext()) {
            final IAEItemStack o = i.next();
            if (o.equals(l)) {
                i.remove();
                return;
            }
        }
    }

    private IAEItemStack findVisualStack(final IAEItemStack l) {
        for (final IAEItemStack o : this.visual) {
            if (o.equals(l)) {
                return o;
            }
        }

        final IAEItemStack stack = l.copy();
        this.visual.add(stack);

        return stack;
    }

    @Override
    public Enum getSortBy() {
        return SortOrder.NAME;
    }

    @Override
    public Enum getSortDir() {
        return SortDir.ASCENDING;
    }

    @Override
    public Enum getSortDisplay() {
        return ViewItems.ALL;
    }

    @Override
    public ItemStack getHoveredStack() {
        return hoveredStack;
    }

    private void hideStoredSorting() {
        this.visualHiddenStored = new ArrayList<>();
        for (final IAEItemStack refStack : this.visual) {
            if (refStack != null) {
                final IAEItemStack activeStack = this.active.findPrecise(refStack);
                final IAEItemStack pendingStack = this.pending.findPrecise(refStack);
                if ((activeStack != null && activeStack.getStackSize() > 0)
                        || (pendingStack != null && pendingStack.getStackSize() > 0)) {
                    this.visualHiddenStored.add(refStack);
                }
            }
        }
    }
}
