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
import java.util.Map.Entry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.util.map.ItemStackMap;

import appeng.api.AEApi;
import appeng.api.config.CellType;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.core.AEConfig;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.me.cache.GridStorageCache;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class ContainerNetworkStatus extends AEBaseContainer {

    @GuiSync(0)
    public long avgAddition;

    @GuiSync(1)
    public long powerUsage;

    @GuiSync(2)
    public long currentPower;

    @GuiSync(3)
    public long maxPower;

    @GuiSync(4)
    public long itemBytesTotal;

    @GuiSync(5)
    public long itemBytesUsed;

    @GuiSync(6)
    public long itemTypesTotal;

    @GuiSync(7)
    public long itemTypesUsed;

    @GuiSync(8)
    public long itemCellG;

    @GuiSync(9)
    public long itemCellB;

    @GuiSync(10)
    public long itemCellO;

    @GuiSync(11)
    public long itemCellR;

    @GuiSync(12)
    public long fluidBytesTotal;

    @GuiSync(13)
    public long fluidBytesUsed;

    @GuiSync(14)
    public long fluidTypesTotal;

    @GuiSync(15)
    public long fluidTypesUsed;

    @GuiSync(16)
    public long fluidCellG;

    @GuiSync(17)
    public long fluidCellB;

    @GuiSync(18)
    public long fluidCellO;

    @GuiSync(19)
    public long fluidCellR;

    @GuiSync(20)
    public long essentiaBytesTotal;

    @GuiSync(21)
    public long essentiaBytesUsed;

    @GuiSync(22)
    public long essentiaTypesTotal;

    @GuiSync(23)
    public long essentiaTypesUsed;

    @GuiSync(24)
    public long essentiaCellG;

    @GuiSync(25)
    public long essentiaCellB;

    @GuiSync(26)
    public long essentiaCellO;

    @GuiSync(27)
    public long essentiaCellR;

    @GuiSync(28)
    public long itemCellCount;

    @GuiSync(29)
    public long fluidCellCount;

    @GuiSync(30)
    public long essentiaCellCount;

    @GuiSync(31)
    public boolean powerInfinite;

    private IGrid network;
    private int delay = 40;
    private boolean isConsume = true;

    public ContainerNetworkStatus(final InventoryPlayer ip, final INetworkTool te) {
        super(ip, null, null);
        final IGridHost host = te.getGridHost();

        if (host != null) {
            this.findNode(host, ForgeDirection.UNKNOWN);
            for (final ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                this.findNode(host, d);
            }
        }

        if (this.network == null && Platform.isServer()) {
            this.setValidContainer(false);
        }
    }

    private void findNode(final IGridHost host, final ForgeDirection d) {
        if (this.network == null) {
            final IGridNode node = host.getGridNode(d);
            if (node != null) {
                this.network = node.getGrid();
            }
        }
    }

    public boolean isPowerInfinite() {
        return powerInfinite;
    }

    public void setPowerInfinite(boolean powerInfinite) {
        this.powerInfinite = powerInfinite;
    }

    @Override
    public void detectAndSendChanges() {
        this.delay++;
        if (Platform.isServer() && this.delay > 15 && this.network != null) {
            this.delay = 0;

            final IEnergyGrid eg = this.network.getCache(IEnergyGrid.class);
            if (eg != null) {
                this.setAverageAddition((long) (100.0 * eg.getAvgPowerInjection()));
                this.setPowerUsage((long) (100.0 * eg.getAvgPowerUsage()));
                this.setCurrentPower((long) (100.0 * eg.getStoredPower()));
                this.setMaxPower((long) (100.0 * eg.getMaxStoredPower()));
                this.setPowerInfinite(eg.getHasInfiniteStore());
            }

            try {
                final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();
                final IItemList<IAEItemStack> list = AEApi.instance().storage().createItemList();

                // Network machine
                if (this.isConsume) {
                    for (final Class<? extends IGridHost> machineClass : this.network.getMachinesClasses()) {
                        for (final IGridNode machine : this.network.getMachines(machineClass)) {
                            final IGridBlock blk = machine.getGridBlock();
                            final ItemStack is = blk.getMachineRepresentation();
                            if (is != null && is.getItem() != null) {
                                final IAEItemStack ais = AEItemStack.create(is);
                                ais.setStackSize(1);
                                ais.setCountRequestable(
                                        (long) PowerMultiplier.CONFIG.multiply(blk.getIdlePowerUsage() * 100.0));
                                list.add(ais);
                            }
                        }
                    }

                } else {
                    // Networ Cells
                    final GridStorageCache sg = this.network.getCache(IStorageGrid.class);
                    CellType selectedCellType = AEConfig.instance.selectedCellType();
                    ItemStackMap<Integer> cells = switch (selectedCellType) {
                        case ITEM -> sg.getItemCells();
                        case FLUID -> sg.getFluidCells();
                        case ESSENTIA -> sg.getEssentiaCells();
                    };

                    for (Entry<ItemStack, Integer> set : cells.entrySet()) {
                        final IAEItemStack ais = AEItemStack.create(set.getKey());
                        ais.setStackSize(set.getValue());
                        list.add(ais);
                    }
                }

                for (final IAEItemStack ais : list) {
                    piu.appendItem(ais);
                }

                // Send packet
                for (final Object c : this.crafters) {
                    if (c instanceof EntityPlayer) {
                        NetworkHandler.instance.sendTo(piu, (EntityPlayerMP) c);
                    }
                }
            } catch (final IOException e) {
                // :P
            }

            final GridStorageCache sg = this.network.getCache(IStorageGrid.class);
            if (sg != null) {
                this.itemBytesUsed = Double.doubleToLongBits(sg.getItemBytesUsed());
                this.itemBytesTotal = Double.doubleToLongBits(sg.getItemBytesTotal());
                this.itemCellG = sg.getItemCellG();
                this.itemCellB = sg.getItemCellB();
                this.itemCellO = sg.getItemCellO();
                this.itemCellR = sg.getItemCellR();
                this.itemCellCount = sg.getItemCellCount();
                this.itemTypesUsed = sg.getItemTypesUsed();
                this.itemTypesTotal = sg.getItemTypesTotal();

                this.fluidBytesUsed = Double.doubleToLongBits(sg.getFluidBytesUsed());
                this.fluidBytesTotal = Double.doubleToLongBits(sg.getFluidBytesTotal());
                this.fluidCellG = sg.getFluidCellG();
                this.fluidCellB = sg.getFluidCellB();
                this.fluidCellO = sg.getFluidCellO();
                this.fluidCellR = sg.getFluidCellR();
                this.fluidCellCount = sg.getFluidCellCount();
                this.fluidTypesUsed = sg.getFluidTypesUsed();
                this.fluidTypesTotal = sg.getFluidTypesTotal();

                this.essentiaBytesUsed = Double.doubleToLongBits(sg.getEssentiaBytesUsed());
                this.essentiaBytesTotal = Double.doubleToLongBits(sg.getEssentiaBytesTotal());
                this.essentiaCellG = sg.getEssentiaCellG();
                this.essentiaCellB = sg.getEssentiaCellB();
                this.essentiaCellO = sg.getEssentiaCellO();
                this.essentiaCellR = sg.getEssentiaCellR();
                this.essentiaCellCount = sg.getEssentiaCellCount();
                this.essentiaTypesUsed = sg.getEssentiaTypesUsed();
                this.essentiaTypesTotal = sg.getEssentiaTypesTotal();
            }
        }
        super.detectAndSendChanges();
    }

    public void setConsume(boolean isConsume) {
        this.isConsume = isConsume;
    }

    public long getCurrentPower() {
        return this.currentPower;
    }

    private void setCurrentPower(final long currentPower) {
        this.currentPower = currentPower;
    }

    public long getMaxPower() {
        return this.maxPower;
    }

    private void setMaxPower(final long maxPower) {
        this.maxPower = maxPower;
    }

    public long getAverageAddition() {
        return this.avgAddition;
    }

    private void setAverageAddition(final long avgAddition) {
        this.avgAddition = avgAddition;
    }

    public long getPowerUsage() {
        return this.powerUsage;
    }

    private void setPowerUsage(final long powerUsage) {
        this.powerUsage = powerUsage;
    }

    public long getItemBytesTotal() {
        return itemBytesTotal;
    }

    public long getItemBytesUsed() {
        return itemBytesUsed;
    }

    public long getItemTypesTotal() {
        return itemTypesTotal;
    }

    public long getItemTypesUsed() {
        return itemTypesUsed;
    }

    public long getItemCellG() {
        return itemCellG;
    }

    public long getItemCellB() {
        return itemCellB;
    }

    public long getItemCellO() {
        return itemCellO;
    }

    public long getItemCellR() {
        return itemCellR;
    }

    public long getFluidBytesTotal() {
        return fluidBytesTotal;
    }

    public long getFluidBytesUsed() {
        return fluidBytesUsed;
    }

    public long getFluidTypesTotal() {
        return fluidTypesTotal;
    }

    public long getFluidTypesUsed() {
        return fluidTypesUsed;
    }

    public long getFluidCellG() {
        return fluidCellG;
    }

    public long getFluidCellB() {
        return fluidCellB;
    }

    public long getFluidCellO() {
        return fluidCellO;
    }

    public long getFluidCellR() {
        return fluidCellR;
    }

    public long getEssentiaBytesTotal() {
        return essentiaBytesTotal;
    }

    public long getEssentiaBytesUsed() {
        return essentiaBytesUsed;
    }

    public long getEssentiaTypesTotal() {
        return essentiaTypesTotal;
    }

    public long getEssentiaTypesUsed() {
        return essentiaTypesUsed;
    }

    public long getEssentiaCellG() {
        return essentiaCellG;
    }

    public long getEssentiaCellB() {
        return essentiaCellB;
    }

    public long getEssentiaCellO() {
        return essentiaCellO;
    }

    public long getEssentiaCellR() {
        return essentiaCellR;
    }

    public long getItemCellCount() {
        return itemCellCount;
    }

    public long getFluidCellCount() {
        return fluidCellCount;
    }

    public long getEssentiaCellCount() {
        return essentiaCellCount;
    }
}
