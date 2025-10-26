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
import java.util.List;

import appeng.api.config.CraftingAllow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCompressedNBT;
import appeng.core.sync.packets.PacketCraftingRemainingOperations;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ICustomNameObject;
import appeng.me.cluster.IAEMultiBlock;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.Platform;

public class ContainerCraftingCPU extends AEBaseContainer
        implements IMEMonitorHandlerReceiver<IAEItemStack>, ICustomNameObject {

    private final IItemList<IAEItemStack> list = AEApi.instance().storage().createItemList();
    private IGrid network;
    private CraftingCPUCluster monitor = null;
    private String cpuName = null;

    @GuiSync(0)
    public long elapsed = -1;
    @GuiSync(1)
    public int allow = 0;

    public ContainerCraftingCPU(final InventoryPlayer ip, final Object te) {
        super(ip, te);
        final IGridHost host = (IGridHost) (te instanceof IGridHost ? te : null);

        if (host != null) {
            this.findNode(host, ForgeDirection.UNKNOWN);
            for (final ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                this.findNode(host, d);
            }
        }

        if (te instanceof TileCraftingTile) {
            this.setCPU((ICraftingCPU) ((IAEMultiBlock) te).getCluster());
        }

        if (this.getNetwork() == null && Platform.isServer()) {
            this.setValidContainer(false);
        }
    }

    private void findNode(final IGridHost host, final ForgeDirection d) {
        if (this.getNetwork() == null) {
            final IGridNode node = host.getGridNode(d);
            if (node != null) {
                this.setNetwork(node.getGrid());
            }
        }
    }

    protected void setCPU(final ICraftingCPU c) {
        if (c == this.getMonitor()) {
            return;
        }

        if (this.getMonitor() != null) {
            this.getMonitor().removeListener(this);
        }

        for (final Object g : this.crafters) {
            if (g instanceof EntityPlayer) {
                try {
                    NetworkHandler.instance
                            .sendTo(new PacketValueConfig("CraftingStatus", "Clear"), (EntityPlayerMP) g);
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }
        }

        if (c instanceof CraftingCPUCluster) {
            this.cpuName = c.getName();
            this.setMonitor((CraftingCPUCluster) c);
            this.list.resetStatus();
            this.getMonitor().getListOfItem(this.list, CraftingItemList.ALL);
            this.getMonitor().addListener(this, null);
            this.setElapsedTime(0);
            this.allow = this.getMonitor().getCraftingAllowMode().ordinal();
        } else {
            this.setMonitor(null);
            this.cpuName = "";
            this.setElapsedTime(-1);
        }
    }

    public void changeAllowMode(String msg) {
        if (this.getMonitor() != null) {
            CraftingAllow newAllowMode = CraftingAllow.values()[Integer.valueOf(msg)].next();
            this.getMonitor().changeCraftingAllowMode(newAllowMode);
            this.allow = newAllowMode.ordinal();
        }
    }

    public CraftingAllow getAllowMode() {
        if (this.getMonitor() != null) {
            return this.getMonitor().getCraftingAllowMode();
        }
        return null;
    }

    public void cancelCrafting() {
        if (this.getMonitor() != null) {
            this.getMonitor().cancel();
        }
        this.setElapsedTime(-1);
    }

    @Override
    public void removeCraftingFromCrafters(final ICrafting c) {
        super.removeCraftingFromCrafters(c);

        if (this.crafters.isEmpty() && this.getMonitor() != null) {
            this.getMonitor().removeListener(this);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.getMonitor() != null) {
            this.getMonitor().removeListener(this);
        }
    }

    public void sendUpdateFollowPacket(List<String> playersFollowingCurrentCraft) {
        NBTTagCompound nbttc = new NBTTagCompound();
        NBTTagList tagList = new NBTTagList();

        if (playersFollowingCurrentCraft != null) {
            for (String name : playersFollowingCurrentCraft) {
                tagList.appendTag(new NBTTagString(name));
            }
        }
        nbttc.setTag("playNameList", tagList);

        for (final Object g : this.crafters) {
            if (g instanceof EntityPlayerMP epmp) {
                try {
                    NetworkHandler.instance.sendTo(new PacketCompressedNBT(nbttc), epmp);
                } catch (final IOException e) {
                    // :P
                }
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer() && this.getMonitor() != null && !this.list.isEmpty()) {
            try {
                this.setElapsedTime(this.getMonitor().getElapsedTime());

                NBTTagCompound nbttc = new NBTTagCompound();
                NBTTagList tagList = new NBTTagList();
                List<String> playersFollowingCurrentCraft = this.getPlayersFollowingCurrentCraft();

                if (playersFollowingCurrentCraft != null) {
                    for (String name : playersFollowingCurrentCraft) {
                        tagList.appendTag(new NBTTagString(name));
                    }
                }
                nbttc.setTag("playNameList", tagList);

                final PacketMEInventoryUpdate a = new PacketMEInventoryUpdate((byte) 0);
                final PacketMEInventoryUpdate b = new PacketMEInventoryUpdate((byte) 1);
                final PacketMEInventoryUpdate c = new PacketMEInventoryUpdate((byte) 2);

                final PacketCompressedNBT d = new PacketCompressedNBT(nbttc);

                for (final IAEItemStack out : this.list) {
                    a.appendItem(this.getMonitor().getItemStack(out, CraftingItemList.STORAGE));
                    b.appendItem(this.getMonitor().getItemStack(out, CraftingItemList.ACTIVE));
                    c.appendItem(this.getMonitor().getItemStack(out, CraftingItemList.PENDING));
                }

                this.list.resetStatus();

                for (final Object g : this.crafters) {
                    if (g instanceof EntityPlayerMP epmp) {
                        if (!a.isEmpty()) {
                            NetworkHandler.instance.sendTo(a, epmp);
                        }

                        if (!b.isEmpty()) {
                            NetworkHandler.instance.sendTo(b, epmp);
                        }

                        if (!c.isEmpty()) {
                            NetworkHandler.instance.sendTo(c, epmp);
                        }

                        NetworkHandler.instance.sendTo(d, epmp);

                        NetworkHandler.instance.sendTo(
                                new PacketCraftingRemainingOperations(this.getMonitor().getRemainingOperations()),
                                epmp);
                    }
                }
            } catch (final IOException e) {
                // :P
            }
        }
        super.detectAndSendChanges();
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(final IBaseMonitor<IAEItemStack> monitor, final Iterable<IAEItemStack> change,
            final BaseActionSource actionSource) {
        for (IAEItemStack is : change) {
            is = is.copy();
            is.setStackSize(1);
            this.list.add(is);
        }
    }

    @Override
    public void onListUpdate() {}

    @Override
    public String getCustomName() {
        return this.cpuName;
    }

    @Override
    public boolean hasCustomName() {
        return this.cpuName != null && this.cpuName.length() > 0;
    }

    @Override
    public void setCustomName(String name) {
        this.cpuName = name;
    }

    public long getElapsedTime() {
        return this.elapsed;
    }

    private void setElapsedTime(final long elapsed) {
        this.elapsed = elapsed;
    }

    CraftingCPUCluster getMonitor() {
        return this.monitor;
    }

    private void setMonitor(final CraftingCPUCluster monitor) {
        this.monitor = monitor;
    }

    IGrid getNetwork() {
        return this.network;
    }

    private void setNetwork(final IGrid network) {
        this.network = network;
    }

    public void togglePlayerFollowStatus(final String name) {
        if (this.getMonitor() != null) {
            this.getMonitor().togglePlayerFollowStatus(name);
        }
    }

    public List<String> getPlayersFollowingCurrentCraft() {
        if (this.getMonitor() != null) {
            return this.getMonitor().getPlayersFollowingCurrentCraft();
        }
        return null;
    }
}
