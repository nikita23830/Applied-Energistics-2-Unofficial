/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import appeng.api.config.CraftingAllow;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.parts.reporting.AbstractPartDisplay;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.ICraftingCPUSelectorContainer;
import appeng.core.AELog;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingTreeData;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.crafting.v2.CraftingJobV2;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.parts.reporting.PartTerminal;
import appeng.tile.misc.TilePatternOptimizationMatrix;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ContainerCraftConfirm extends AEBaseContainer implements ICraftingCPUSelectorContainer {

    private Future<ICraftingJob> job;
    private ICraftingJob result;

    @GuiSync(0)
    public long bytesUsed;

    @GuiSync(1)
    public long cpuBytesAvail;

    @GuiSync(2)
    public int cpuCoProcessors;

    @GuiSync(3)
    public boolean autoStart = false;

    @GuiSync(4)
    public boolean simulation = true;

    @GuiSync(6)
    public boolean noCPU = true;

    @GuiSync(7)
    public String myName = "";

    @GuiSync(8)
    public boolean isAllowedToRunPatternOptimization = false;

    @GuiSync.Recurse(9)
    public final ContainerCPUTable cpuTable;

    @GuiSync(10)
    public String serializedItemToCraft = "";

    public ContainerCraftConfirm(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
        this.cpuTable = new ContainerCPUTable(this, this::onCPUUpdate, false, this::cpuMatches);
    }

    @Override
    public void selectCPU(int cpu) {
        this.cpuTable.selectCPU(cpu);
    }

    public void onCPUUpdate(ICraftingCPU cpu) {
        if (cpu == null) {
            this.setCpuAvailableBytes(0);
            this.setCpuCoProcessors(0);
            this.setName("");
        } else {
            this.setName(cpu.getName());
            this.setCpuAvailableBytes(cpu.getAvailableStorage());
            this.setCpuCoProcessors(cpu.getCoProcessors());
        }
    }

    @Override
    public void detectAndSendChanges() {
        // Wait with CPU selection until job bytes are retrieved
        if (this.bytesUsed != 0) {
            cpuTable.detectAndSendChanges(getGrid(), crafters);
        }
        if (Platform.isClient()) {
            return;
        }

        this.setNoCPU(this.cpuTable.getCPUs().isEmpty());

        IGrid grid = getGrid();
        if (grid != null) this.isAllowedToRunPatternOptimization = !getGrid()
                .getMachines(TilePatternOptimizationMatrix.class).isEmpty();

        super.detectAndSendChanges();

        if (this.getJob() != null && this.getJob().isDone()) {
            try {
                this.result = this.getJob().get();

                if (!this.result.isSimulation()) {
                    this.setSimulation(false);
                    if (this.isAutoStart()) {
                        this.startJob();
                        return;
                    }
                } else {
                    this.setSimulation(true);
                }

                try {
                    final PacketMEInventoryUpdate storageUpdate = new PacketMEInventoryUpdate((byte) 0);
                    final PacketMEInventoryUpdate pendingUpdate = new PacketMEInventoryUpdate((byte) 1);
                    final PacketMEInventoryUpdate missingUpdate = this.result.isSimulation()
                            ? new PacketMEInventoryUpdate((byte) 2)
                            : null;

                    final IItemList<IAEItemStack> plan = AEApi.instance().storage().createItemList();
                    this.result.populatePlan(plan);

                    this.setUsedBytes(this.result.getByteTotal());

                    for (final IAEItemStack plannedItem : plan) {

                        IAEItemStack toExtract = plannedItem.copy();
                        toExtract.reset();
                        toExtract.setStackSize(plannedItem.getStackSize());

                        final IAEItemStack toCraft = plannedItem.copy();
                        toCraft.reset();
                        toCraft.setStackSize(plannedItem.getCountRequestable());
                        toCraft.setCountRequestableCrafts(plannedItem.getCountRequestableCrafts());

                        final IStorageGrid sg = this.getGrid().getCache(IStorageGrid.class);
                        final IMEInventory<IAEItemStack> items = sg.getItemInventory();

                        IAEItemStack missing = null;
                        if (missingUpdate != null && this.result.isSimulation()) {
                            missing = toExtract.copy();
                            toExtract = items.extractItems(toExtract, Actionable.SIMULATE, this.getActionSource());

                            if (toExtract == null) {
                                toExtract = missing.copy();
                                toExtract.setStackSize(0);
                            }

                            missing.setStackSize(missing.getStackSize() - toExtract.getStackSize());
                        }

                        if (toExtract.getStackSize() > 0 && toCraft.getStackSize() <= 0
                                && (missing == null || missing.getStackSize() <= 0)) {
                            IAEItemStack availableStack = items
                                    .getAvailableItem(toExtract, IterationCounter.fetchNewId());
                            long available = (availableStack == null) ? 0 : availableStack.getStackSize();
                            if (available > 0) toExtract.setUsedPercent(toExtract.getStackSize() / (available / 100f));
                            else toExtract.setUsedPercent(0f);
                        }

                        if (toExtract.getStackSize() > 0) {
                            storageUpdate.appendItem(toExtract);
                        }

                        if (toCraft.getStackSize() > 0) {
                            pendingUpdate.appendItem(toCraft);
                        }

                        if (missingUpdate != null && missing != null && missing.getStackSize() > 0) {
                            missingUpdate.appendItem(missing);
                        }
                    }

                    final List<PacketCraftingTreeData> treeUpdates;
                    if (this.result instanceof CraftingJobV2) {
                        treeUpdates = PacketCraftingTreeData.createChunks((CraftingJobV2) this.result);
                    } else {
                        treeUpdates = null;
                    }
                    for (final Object player : this.crafters) {
                        if (player instanceof EntityPlayerMP playerMP) {
                            NetworkHandler.instance.sendTo(storageUpdate, playerMP);
                            NetworkHandler.instance.sendTo(pendingUpdate, playerMP);
                            if (missingUpdate != null) {
                                NetworkHandler.instance.sendTo(missingUpdate, playerMP);
                            }
                            if (treeUpdates != null) {
                                for (PacketCraftingTreeData pkt : treeUpdates) {
                                    NetworkHandler.instance.sendTo(pkt, playerMP);
                                }
                            }
                        }
                    }
                } catch (final IOException e) {
                    // :P
                }
            } catch (final Throwable e) {
                e.printStackTrace();
                this.getPlayerInv().player.addChatMessage(new ChatComponentText("Error: " + e));
                AELog.debug(e);
                this.setValidContainer(false);
                this.result = null;
            }

            this.setJob(null);
        }
        this.verifyPermissions(SecurityPermissions.CRAFT, false);
    }

    private IGrid getGrid() {
        final IActionHost h = ((IActionHost) this.getTarget());
        if (h == null || h.getActionableNode() == null) return null;
        return h.getActionableNode().getGrid();
    }

    public IAEItemStack getItemToCraft() {
        try {
            ByteBuf deserialized = Unpooled.wrappedBuffer(serializedItemToCraft.getBytes(StandardCharsets.ISO_8859_1));
            return AEApi.instance().storage().readItemFromPacket(deserialized);
        } catch (IOException e) {
            AELog.debug(e);
            AELog.debug("Deserializing IAEItemStack Failed");
            return null;
        }
    }

    public boolean cpuCraftingSameItem(final CraftingCPUStatus c) {
        if (c.getCrafting() == null || this.getItemToCraft() == null) {
            return false;
        }
        return c.getCrafting().isSameType(this.getItemToCraft());
    }

    public boolean cpuMatches(final CraftingCPUStatus c) {
        if (c.allowMode() == CraftingAllow.ONLY_NONPLAYER) return false;
        if (this.getUsedBytes() <= 0) return false;
        if (c.isBusy() && this.cpuCraftingSameItem(c)) {
            return c.getStorage() >= this.getUsedBytes() + c.getUsedStorage();
        }
        return c.getStorage() >= this.getUsedBytes() && !c.isBusy();
    }

    public void startJob() {
        try {
            if (this.result != null && !this.isSimulation() && getGrid() != null) {
                final ICraftingGrid cc = this.getGrid().getCache(ICraftingGrid.class);
                CraftingCPUStatus selected = this.cpuTable.getSelectedCPU();
                if (selected != null && selected.getServerCluster() instanceof CraftingCPUCluster && !getWorld().isRemote) {
                    ((CraftingCPUCluster) selected.getServerCluster()).setRequester((EntityPlayerMP) this.getInventoryPlayer().player);
                }
                final ICraftingLink g = cc.submitJob(
                        this.result,
                        null,
                        (selected == null) ? null : selected.getServerCluster(),
                        true,
                        this.getActionSrc());
                this.setAutoStart(false);
                if (g != null) {
                    this.switchToOriginalGUI();
                } else {
                    System.out.println("ERRR 2");
                }
            } else {
                System.out.println("ERRR 1");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void startJob(String playerName) {
        if (this.result != null && !this.isSimulation() && getGrid() != null) {
            final ICraftingGrid cc = this.getGrid().getCache(ICraftingGrid.class);
            CraftingCPUStatus selected = this.cpuTable.getSelectedCPU();
            final ICraftingLink g = cc.submitJob(
                    this.result,
                    null,
                    (selected == null) ? null : selected.getServerCluster(),
                    true,
                    this.getActionSrc());
            selected.getServerCluster().togglePlayerFollowStatus(playerName);
            this.setAutoStart(false);
            if (g != null) {
                this.switchToOriginalGUI();
            }
        }
    }

    public void optimizePatterns() {
        // only V2 supported
        if (this.result instanceof CraftingJobV2 && !this.isSimulation()
                && getGrid() != null
                && !getGrid().getMachines(TilePatternOptimizationMatrix.class).isEmpty()) {
            Platform.openGUI(
                    this.getPlayerInv().player,
                    this.getOpenContext().getTile(),
                    this.getOpenContext().getSide(),
                    GuiBridge.GUI_OPTIMIZE_PATTERNS);
            if (this.getPlayerInv().player.openContainer instanceof ContainerOptimizePatterns cop) {
                cop.setResult(this.result);
            }
        }
    }

    public void switchToOriginalGUI() {
        GuiBridge originalGui = null;

        final IActionHost ah = this.getActionHost();
        if (ah instanceof WirelessTerminalGuiObject) {
            originalGui = GuiBridge.GUI_WIRELESS_TERM;
        }

        if (ah instanceof PartTerminal) {
            originalGui = GuiBridge.GUI_ME;
        }

        if (ah instanceof PartCraftingTerminal) {
            originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (ah instanceof PartPatternTerminal) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (ah instanceof PartPatternTerminalEx) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }

        if (originalGui != null && this.getOpenContext() != null) {
            NetworkHandler.instance
                    .sendTo(new PacketSwitchGuis(originalGui), (EntityPlayerMP) this.getInventoryPlayer().player);

            final TileEntity te = this.getOpenContext().getTile();
            Platform.openGUI(this.getInventoryPlayer().player, te, this.getOpenContext().getSide(), originalGui);
        } else if (ah instanceof AbstractPartDisplay apd) {
            TileEntity te = apd.getHost().getTile();
            apd.onPartActivate(this.getInventoryPlayer().player, Vec3.createVectorHelper(te.xCoord, te.yCoord, te.zCoord));
        }
    }

    private BaseActionSource getActionSrc() {
        return new PlayerSource(this.getPlayerInv().player, (IActionHost) this.getTarget());
    }

    @Override
    public void removeCraftingFromCrafters(final ICrafting c) {
        super.removeCraftingFromCrafters(c);
        if (this.getJob() != null) {
            this.getJob().cancel(true);
            this.setJob(null);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer par1EntityPlayer) {
        super.onContainerClosed(par1EntityPlayer);
        if (this.getJob() != null) {
            this.getJob().cancel(true);
            this.setJob(null);
        }
    }

    public World getWorld() {
        return this.getPlayerInv().player.worldObj;
    }

    public boolean isAutoStart() {
        return this.autoStart;
    }

    public void setAutoStart(final boolean autoStart) {
        this.autoStart = autoStart;
    }

    public long getUsedBytes() {
        return this.bytesUsed;
    }

    private void setUsedBytes(final long bytesUsed) {
        this.bytesUsed = bytesUsed;
    }

    public long getCpuAvailableBytes() {
        return this.cpuBytesAvail;
    }

    private void setCpuAvailableBytes(final long cpuBytesAvail) {
        this.cpuBytesAvail = cpuBytesAvail;
    }

    public int getCpuCoProcessors() {
        return this.cpuCoProcessors;
    }

    private void setCpuCoProcessors(final int cpuCoProcessors) {
        this.cpuCoProcessors = cpuCoProcessors;
    }

    public int getSelectedCpu() {
        return this.cpuTable.selectedCpuSerial;
    }

    public String getName() {
        return this.myName;
    }

    private void setName(@Nonnull final String myName) {
        this.myName = myName;
    }

    public boolean hasNoCPU() {
        return this.noCPU;
    }

    private void setNoCPU(final boolean noCPU) {
        this.noCPU = noCPU;
    }

    public boolean isSimulation() {
        return this.simulation;
    }

    private void setSimulation(final boolean simulation) {
        this.simulation = simulation;
    }

    private Future<ICraftingJob> getJob() {
        return this.job;
    }

    public void setJob(final Future<ICraftingJob> job) {
        this.job = job;
    }

    public void setItemToCraft(@Nonnull final IAEItemStack itemToCraft) {
        try {
            ByteBuf serialized = Unpooled.buffer();
            itemToCraft.writeToPacket(serialized);
            this.serializedItemToCraft = serialized.toString(StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            AELog.debug(e);
            AELog.debug("Deserializing IAEItemStack Failed");
        }
    }
}
