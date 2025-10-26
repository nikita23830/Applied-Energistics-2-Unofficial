/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import static com.gtnewhorizon.gtnhlib.capability.Capabilities.getCapability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import appeng.api.IExtendDuality;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.ImmutableSet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.crafting.ICraftingIconProvider;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkCraftingPushedPattern;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.registries.BlockingModeIgnoreItemRegistry;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.storage.MEMonitorIInventory;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.me.storage.NullInventory;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.tile.networking.TileCableBus;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorIInventory;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.ItemSlot;
import appeng.util.inv.WrapperInvSlot;
import appeng.util.item.AEItemStack;
import cofh.api.transport.IItemDuct;
import cpw.mods.fml.common.Loader;
import appeng.api.config.FuzzyMode;
import appeng.me.cache.NetworkMonitor;
import appeng.util.IterationCounter;

public class DualityInterface implements IGridTickable, IStorageMonitorable, IInventoryDestination, IAEAppEngInventory,
        IConfigManagerHost, ICraftingProvider, IUpgradeableHost, IPriorityHost {

    public static final int NUMBER_OF_STORAGE_SLOTS = 9;
    public static final int NUMBER_OF_CONFIG_SLOTS = 9;
    public static final int NUMBER_OF_PATTERN_SLOTS = 9;

    private static final Collection<Block> BAD_BLOCKS = new HashSet<>(100);
    private int[] sides = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
    private IAEItemStack[] requireWork = { null, null, null, null, null, null, null, null, null };
    private final boolean[] hasFuzzyConfig = { false, false, false, false, false, false, false, false, false, };
    private final MultiCraftingTracker craftingTracker;
    protected final AENetworkProxy gridProxy;
    private final IInterfaceHost iHost;
    private final BaseActionSource mySource;
    private final BaseActionSource interfaceRequestSource;
    private final ConfigManager cm = new ConfigManager(this);
    private AppEngInternalAEInventory config = new AppEngInternalAEInventory(this, getNumberOfConfigSlots(NUMBER_OF_CONFIG_SLOTS));
    private AppEngInternalInventory storage = new AppEngInternalInventory(this, getNumberOfStorageSlots(NUMBER_OF_STORAGE_SLOTS));
    private AppEngInternalInventory patterns = new AppEngInternalInventory(this, getNumberOfPatternSlots(NUMBER_OF_PATTERN_SLOTS * 4));
    private WrapperInvSlot slotInv = new WrapperInvSlot(this.storage);
    private final MEMonitorPassThrough<IAEItemStack> items = new MEMonitorPassThrough<>(
            new NullInventory<IAEItemStack>(),
            StorageChannel.ITEMS);
    private final MEMonitorPassThrough<IAEFluidStack> fluids = new MEMonitorPassThrough<>(
            new NullInventory<IAEFluidStack>(),
            StorageChannel.FLUIDS);
    private final UpgradeInventory upgrades;
    private ItemStack stored;
    private IAEItemStack fuzzyItemStack;
    private boolean hasConfig = false;
    private int priority;
    public List<ICraftingPatternDetails> craftingList = null;
    private List<ItemStack> waitingToSend = null;
    private IMEInventory<IAEItemStack> destination;
    private boolean isWorking = false;
    protected static final boolean EIO = Loader.isModLoaded("EnderIO");

    private YesNo redstoneState = YesNo.UNDECIDED;
    private UnlockCraftingEvent unlockEvent;
    private List<IAEItemStack> unlockStacks;
    private int lastInputHash = 0;

    public DualityInterface(final AENetworkProxy networkProxy, final IInterfaceHost ih) {
        this.gridProxy = networkProxy;
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);

        this.upgrades = new StackUpgradeInventory(this.gridProxy.getMachineRepresentation(), this, 4);
        this.cm.registerSetting(Settings.BLOCK, YesNo.NO);
        this.cm.registerSetting(Settings.SMART_BLOCK, YesNo.NO);
        this.cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
        this.cm.registerSetting(Settings.INSERTION_MODE, InsertionMode.DEFAULT);
        this.cm.registerSetting(Settings.ADVANCED_BLOCKING_MODE, AdvancedBlockingMode.DEFAULT);
        this.cm.registerSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
        this.cm.registerSetting(Settings.PATTERN_OPTIMIZATION, YesNo.YES);
        this.cm.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);

        this.iHost = ih;
        this.craftingTracker = new MultiCraftingTracker(this.iHost, 9);

        final MachineSource actionSource = new MachineSource(this.iHost);
        this.mySource = actionSource;
        this.fluids.setChangeSource(actionSource);
        this.items.setChangeSource(actionSource);

        this.interfaceRequestSource = new InterfaceRequestSource(this.iHost);
        if (ih instanceof IExtendDuality dual) {
            config = new AppEngInternalAEInventory(this, dual.getNumberOfConfigSlots(NUMBER_OF_CONFIG_SLOTS));
            storage = new AppEngInternalInventory(this, dual.getNumberOfStorageSlots(NUMBER_OF_STORAGE_SLOTS));
            patterns = new AppEngInternalInventory(this, dual.getNumberOfPatternSlots(NUMBER_OF_PATTERN_SLOTS * 4));
            requireWork = new IAEItemStack[dual.getNumberOfConfigSlots(NUMBER_OF_CONFIG_SLOTS)];
            Arrays.fill(requireWork, null);
            slotInv = new WrapperInvSlot(this.storage);
            sides = new int[config.getSizeInventory()];
            for (int x = 0; x < config.getSizeInventory(); ++x)
                sides[x] = x;
        }
    }

    public int getSizeStorage_() {
        return storage.getSizeInventory();
    }

    public int getSizeConfig_() {
        return config.getSizeInventory();
    }

    public int getSizePatterns_() {
        return patterns.getSizeInventory();
    }

    protected int getNumberOfStorageSlots(int a) {
        if (this.getHost() != null && this.getHost() instanceof IExtendDuality)
            return ((IExtendDuality) this.getHost()).getNumberOfStorageSlots(a);
        return a;
    }

    protected int getNumberOfConfigSlots(int a) {
        if (this.getHost() != null && this.getHost() instanceof IExtendDuality)
            return ((IExtendDuality) this.getHost()).getNumberOfConfigSlots(a);
        return a;
    }

    protected int getNumberOfPatternSlots(int a) {
        if (this.getHost() != null && this.getHost() instanceof IExtendDuality)
            return ((IExtendDuality) this.getHost()).getNumberOfPatternSlots(a);
        return a;
    }

    @Override
    public void saveChanges() {
        this.iHost.saveChanges();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removed,
            final ItemStack added) {
        if (mc == InvOperation.markDirty) {
            TileEntity te = getHost().getTile();
            if (te != null && te.getWorldObj() != null)
                te.getWorldObj().markTileEntityChunkModified(te.xCoord, te.yCoord, te.zCoord, te);
        }

        if (this.isWorking) {
            return;
        }

        if (inv == this.config) {
            this.readConfig();
        } else if (inv == this.patterns) {
            if (removed != null || added != null) {
                this.updateCraftingList();
            }
        } else if (inv == this.storage) {
            if (slot >= 0) {
                final boolean had = this.hasWorkToDo();

                this.updatePlan(slot);

                final boolean now = this.hasWorkToDo();

                if (had != now) {
                    try {
                        if (now) {
                            this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
                        } else {
                            this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
                        }
                    } catch (final GridAccessException e) {
                        // :P
                    }
                }
            }
        } else if (inv == this.upgrades) {
            if (this.getInstalledUpgrades(Upgrades.LOCK_CRAFTING) == 0) {
                cm.putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
                resetCraftingLock();
            }
        }
    }

    public void writeToNBT(final NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
        this.patterns.writeToNBT(data, "patterns");
        this.storage.writeToNBT(data, "storage");
        this.upgrades.writeToNBT(data, "upgrades");
        this.cm.writeToNBT(data);
        this.craftingTracker.writeToNBT(data);
        data.setInteger("priority", this.priority);

        if (unlockEvent == UnlockCraftingEvent.PULSE) {
            data.setByte("unlockEvent", (byte) 1);
        } else if (unlockEvent == UnlockCraftingEvent.RESULT) {
            if (unlockStacks != null && !unlockStacks.isEmpty()) {
                data.setByte("unlockEvent", (byte) 2);
                NBTTagList stackList = new NBTTagList();
                for (IAEItemStack stack : unlockStacks) {
                    NBTTagCompound stackTag = new NBTTagCompound();
                    stack.writeToNBT(stackTag);
                    stackList.appendTag(stackTag);
                }
                data.setTag("unlockStacks", stackList);
            } else {
                AELog.error("Saving interface {}, locked waiting for stack, but stack is null!", iHost);
            }
        }

        final NBTTagList waitingToSend = new NBTTagList();
        if (this.waitingToSend != null) {
            for (final ItemStack is : this.waitingToSend) {
                final NBTTagCompound item = new NBTTagCompound();
                is.writeToNBT(item);
                if (is.stackSize > Byte.MAX_VALUE) {
                    item.setInteger("Count", is.stackSize);
                }
                waitingToSend.appendTag(item);
            }
        }
        data.setTag("waitingToSend", waitingToSend);
    }

    public void readFromNBT(final NBTTagCompound data) {
        this.waitingToSend = null;
        final NBTTagList waitingList = data.getTagList("waitingToSend", 10);
        if (waitingList != null) {
            for (int x = 0; x < waitingList.tagCount(); x++) {
                final NBTTagCompound c = waitingList.getCompoundTagAt(x);
                if (c != null) {
                    final ItemStack is = ItemStack.loadItemStackFromNBT(c);
                    if (is == null) {
                        continue;
                    }
                    if (c.hasKey("Count", NBT.TAG_INT)) {
                        is.stackSize = c.getInteger("Count");
                    }
                    this.addToSendList(is);
                }
            }
        }

        var unlockEventType = data.getByte("unlockEvent");
        this.unlockEvent = switch (unlockEventType) {
            case 0 -> null;
            case 1 -> UnlockCraftingEvent.PULSE;
            case 2 -> UnlockCraftingEvent.RESULT;
            default -> {
                AELog.error("Unknown unlock event type {} in NBT for interface: {}", unlockEventType, data);
                yield null;
            }
        };
        if (this.unlockEvent == UnlockCraftingEvent.RESULT) {
            NBTTagList stackList = data.getTagList("unlockStacks", 10);
            for (int index = 0; index < stackList.tagCount(); index++) {
                NBTTagCompound stackTag = stackList.getCompoundTagAt(index);
                IAEItemStack unlockStack = AEItemStack.loadItemStackFromNBT(stackTag);
                if (unlockStack == null) {
                    AELog.error("Could not load unlock stack for interface from NBT: {}", data);
                    continue;
                }
                if (this.unlockStacks == null) {
                    this.unlockStacks = new ArrayList<>();
                }
                this.unlockStacks.add(unlockStack);
            }
        } else {
            this.unlockStacks = null;
        }

        this.craftingTracker.readFromNBT(data);
        this.upgrades.readFromNBT(data, "upgrades");
        this.config.readFromNBT(data, "config");
        this.patterns.readFromNBT(data, "patterns");
        this.storage.readFromNBT(data, "storage");
        this.priority = data.getInteger("priority");
        this.cm.readFromNBT(data);
        this.readConfig();
        this.updateCraftingList();
    }

    private void addToSendList(final ItemStack is) {
        if (is == null) {
            return;
        }

        if (this.waitingToSend == null) {
            this.waitingToSend = new LinkedList<>();
        }

        this.waitingToSend.add(is);

        try {
            this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void readConfig() {
        this.hasConfig = false;

        for (final ItemStack p : this.config) {
            if (p != null) {
                this.hasConfig = true;
                break;
            }
        }

        final boolean had = this.hasWorkToDo();

        for (int x = 0; x < getNumberOfConfigSlots(NUMBER_OF_CONFIG_SLOTS); x++) {
            this.updatePlan(x);
        }

        final boolean has = this.hasWorkToDo();

        if (had != has) {
            try {
                if (has) {
                    this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
                } else {
                    this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
                }
            } catch (final GridAccessException e) {
                // :P
            }
        }

        this.notifyNeighbors();
    }

    public void updateCraftingList() {

        final boolean[] accountedFor = new boolean[patterns.getSizeInventory()];

        if (!this.gridProxy.isReady()) {
            return;
        }

        if (this.craftingList != null) {
            final Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();
            while (i.hasNext()) {
                final ICraftingPatternDetails details = i.next();
                boolean found = false;

                for (int x = 0; x < accountedFor.length; x++) {
                    final ItemStack is = this.patterns.getStackInSlot(x);
                    if (details.getPattern() == is) {
                        accountedFor[x] = found = true;
                    }
                }

                if (!found) {
                    i.remove();
                }
            }
        }

        for (int x = 0; x < accountedFor.length; x++) {
            if (!accountedFor[x]) {
                this.addToCraftingList(x);
            }
        }

        try {
            this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private boolean hasWorkToDo() {
        if (this.hasItemsToSend()) {
            return true;
        } else {
            for (final IAEItemStack requiredWork : this.requireWork) {
                if (requiredWork != null) {
                    return true;
                }
            }

            return false;
        }
    }

    private void updatePlan(final int slot) {
        IAEItemStack req = this.config.getAEStackInSlot(slot);
        if (req != null && req.getStackSize() <= 0) {
            this.config.setInventorySlotContents(slot, null);
            req = null;
        }

        final int fuzzycards = this.getInstalledUpgrades(Upgrades.FUZZY);
        final ItemStack Stored = this.storage.getStackInSlot(slot);
        this.stored = Stored;

        if (req == null && Stored != null) {
            final IAEItemStack work = AEApi.instance().storage().createItemStack(Stored);
            this.requireWork[slot] = work.setStackSize(-work.getStackSize());
            return;
        } else if (req != null) {
            if (Stored == null) // need to add stuff!
            {
                this.requireWork[slot] = req.copy();
                return;

                /*
                 * Checks if a fuzzy-matched item exists, and sets the config slot stack to that item; and if it doesnt
                 * exist, it sets the variable for it equal to an IAEItemStack version of the ItemStack in the storage
                 * slot. This ensures fast and accurate adjustment of the stack size stocked in the storage slot.
                 */
            } else if (((fuzzycards == 1) && (slot) > 5) || ((fuzzycards == 2) && (slot > 2)) || (fuzzycards == 3)) {
                if (this.fuzzyItemStack == null) {
                    this.fuzzyItemStack = AEApi.instance().storage().createItemStack(Stored);
                }
                if ((req.getStackSize() != Stored.stackSize)) {
                    this.requireWork[slot] = this.fuzzyItemStack.copy();
                    this.requireWork[slot].setStackSize(req.getStackSize() - Stored.stackSize);
                    return;
                }
            } else if (req.isSameType(Stored)) { // same type,
                if (req.getStackSize() != Stored.stackSize) {
                    this.requireWork[slot] = req.copy();
                    this.requireWork[slot].setStackSize(req.getStackSize() - Stored.stackSize);
                    return;
                }
            } else
            // Stored != null; dispose!
            {
                final IAEItemStack work = AEApi.instance().storage().createItemStack(Stored);
                this.requireWork[slot] = work.setStackSize(-work.getStackSize());
                return;
            }
        }

        // else

        this.requireWork[slot] = null;
    }

    public IAEItemStack[] fuzzyPoweredExtraction(final IEnergySource energy, final IMEInventory<IAEItemStack> cell,
                                                 final IAEItemStack itemStack, final ItemStack is, final BaseActionSource src, int iteration) {
        Collection<IAEItemStack> fzlist = null;
        IAEItemStack fuzzyItemStack = null;
        IAEItemStack pe = null;
        /*
         * This returns a NetworkInventoryHandler object. getSortedFuzzyItems has an Override definition in there.
         */
        if (cell instanceof NetworkMonitor<?>) {
            fzlist = ((NetworkMonitor<IAEItemStack>) cell).getHandler().getSortedFuzzyItems(
                    new ArrayList<>(),
                    itemStack,
                    ((FuzzyMode) cm.getSetting(Settings.FUZZY_MODE)),
                    iteration);

        } else return new IAEItemStack[] { null, fuzzyItemStack };

        if (fzlist.iterator().hasNext()) {
            fuzzyItemStack = fzlist.iterator().next();

            /*
             * Checks if the fuzzy-matched item can be merged with the ItemStack currently in the storage slot.
             */
            if ((fuzzyItemStack.isSameType(is)) || (is == null)) {
                fuzzyItemStack.setStackSize(itemStack.getStackSize());

                // To prevent duping in case fuzzy-matched item cannot be merged with stack in storage
                // slot...
            } else fuzzyItemStack.setStackSize(0);

            pe = Platform.poweredExtraction(energy, cell, fuzzyItemStack, src);
        }
        return new IAEItemStack[] { pe, fuzzyItemStack };

    }

    public void notifyNeighbors() {
        if (this.gridProxy.isActive()) {
            try {
                this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
                this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
            } catch (final GridAccessException e) {
                // :P
            }
        }

        final TileEntity te = this.iHost.getTileEntity();
        if (te != null && te.getWorldObj() != null) {
            Platform.notifyBlocksOfNeighbors(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord);
        }
    }

    protected void addToCraftingList(final int slot) {
        final ItemStack is = this.patterns.getStackInSlot(slot);

        if (is == null) {
            return;
        }

        if (is.getItem() instanceof ICraftingPatternItem cpi) {
            final ICraftingPatternDetails details = cpi.getPatternForItem(is, this.iHost.getTileEntity().getWorldObj());

            if (details != null) {
                if (this.craftingList == null) {
                    this.craftingList = new LinkedList<>();
                }

                details.setPriority(slot - 36 * this.getPriority());
                this.craftingList.add(details);
            }
        }
    }

    private boolean hasItemsToSend() {
        return this.waitingToSend != null && !this.waitingToSend.isEmpty();
    }

    @Override
    public boolean canInsert(final ItemStack stack) {
        final IAEItemStack out = this.destination
                .injectItems(AEApi.instance().storage().createItemStack(stack), Actionable.SIMULATE, null);
        if (out == null) {
            return true;
        }
        return out.getStackSize() != stack.stackSize;
        // ItemStack after = adaptor.simulateAdd( stack );
        // if ( after == null )
        // return true;
        // return after.stackSize != stack.stackSize;
    }

    public IInventory getConfig() {
        return this.config;
    }

    public IInventory getPatterns() {
        return this.patterns;
    }

    public void gridChanged() {
        try {
            this.items.setInternal(this.gridProxy.getStorage().getItemInventory());
            this.fluids.setInternal(this.gridProxy.getStorage().getFluidInventory());
        } catch (final GridAccessException gae) {
            this.items.setInternal(new NullInventory<>());
            this.fluids.setInternal(new NullInventory<>());
        }

        this.notifyNeighbors();
    }

    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this.iHost.getTileEntity());
    }

    public IInventory getInternalInventory() {
        return this.storage;
    }

    public List<ItemStack> getWaitingToSend() {
        return this.waitingToSend;
    }

    public void markDirty() {
        for (int slot = 0; slot < this.storage.getSizeInventory(); slot++) {
            this.onChangeInventory(this.storage, slot, InvOperation.markDirty, null, null);
        }
    }

    public int[] getAccessibleSlotsFromSide(final int side) {
        return this.sides;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(
                TickRates.Interface.getMin(),
                TickRates.Interface.getMax(),
                !this.hasWorkToDo(),
                true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.gridProxy.isActive()) {
            if (AEConfig.instance.debugLogTiming) {
                TileEntity te = iHost.getTileEntity();
                AELog.debug(
                        "Timing: interface at (%d %d %d) is ticking while the grid is booting",
                        te.xCoord,
                        te.yCoord,
                        te.zCoord);
            }
            return this.hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
        }

        boolean sentItems = false;
        if (this.hasItemsToSend()) {
            sentItems = this.pushItemsOut(this.iHost.getTargets());
        }

        final boolean couldDoWork = this.updateStorage();
        final boolean hasWorkToDo = this.hasWorkToDo();
        return (hasWorkToDo || (sentItems && this.hasItemsToSend()))
                ? (couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                : TickRateModulation.SLEEP;
    }

    // Returns if it successfully sent some items
    private boolean pushItemsOut(final EnumSet<ForgeDirection> possibleDirections) {
        if (!this.hasItemsToSend()) {
            return false;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorldObj();

        final Iterator<ItemStack> i = this.waitingToSend.iterator();
        boolean sentSomething = false;
        while (i.hasNext()) {
            ItemStack whatToSend = i.next();

            for (final ForgeDirection s : possibleDirections) {
                final TileEntity te = w
                        .getTileEntity(tile.xCoord + s.offsetX, tile.yCoord + s.offsetY, tile.zCoord + s.offsetZ);

                if (te == null) {
                    continue;
                }

                if (te.getClass().getName().equals("li.cil.oc.common.tileentity.Adapter")) continue;

                if (te instanceof IInterfaceHost host) {
                    try {
                        if (host.getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                            continue;
                        }
                    } catch (GridAccessException e) {
                        continue;
                    }
                }

                final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
                ItemStack Result = whatToSend;
                if (ad != null) {
                    Result = ad.addItems(whatToSend, getInsertionMode());
                } else if (EIO && te instanceof IItemDuct) {
                    Result = ((IItemDuct) te).insertItem(s.getOpposite(), whatToSend);
                }
                if (Result == null) {
                    whatToSend = null;
                    sentSomething = true;
                } else {
                    sentSomething |= Result.stackSize < whatToSend.stackSize;
                    whatToSend.stackSize = Result.stackSize;
                    whatToSend.setTagCompound(Result.getTagCompound());
                }

                if (whatToSend == null) {
                    break;
                }
            }

            if (whatToSend == null) {
                i.remove();
            }
        }

        if (this.waitingToSend.isEmpty()) {
            this.waitingToSend = null;
        }
        return sentSomething;
    }

    private boolean updateStorage() {
        boolean didSomething = false;

        for (int x = 0; x < getNumberOfStorageSlots(NUMBER_OF_STORAGE_SLOTS); x++) {
            if (this.requireWork[x] != null) {
                didSomething = this.usePlan(x, this.requireWork[x]) || didSomething;
            }
        }

        return didSomething;
    }

    private boolean usePlan(final int x, final IAEItemStack itemStack) {
        final InventoryAdaptor adaptor = this.getAdaptor(x);
        final int fuzzycards = this.getInstalledUpgrades(Upgrades.FUZZY);
        IAEItemStack acquired = null;
        this.isWorking = true;

        boolean changed = false;
        try {
            this.destination = this.gridProxy.getStorage().getItemInventory();
            final IEnergySource src = this.gridProxy.getEnergy();

            if (itemStack.getStackSize() < 0) {
                IAEItemStack toStore = itemStack.copy();
                toStore.setStackSize(-toStore.getStackSize());

                long diff = toStore.getStackSize();

                // make sure strange things didn't happen...
                final ItemStack canExtract = adaptor.simulateRemove((int) diff, toStore.getItemStack(), null);
                if (canExtract == null || canExtract.stackSize != diff) {
                    changed = true;
                    throw new GridAccessException();
                }

                toStore = Platform.poweredInsert(src, this.destination, toStore, this.interfaceRequestSource);

                if (toStore != null) {
                    diff -= toStore.getStackSize();
                }

                if (diff != 0) {
                    // extract items!
                    changed = true;
                    final ItemStack removed = adaptor.removeItems((int) diff, null, null);
                    if (removed == null) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( removeItems )");
                    } else if (removed.stackSize != diff) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( removeItems )");
                    }
                    onStackReturnedToNetwork(AEItemStack.create(removed));
                }
            } else if (this.craftingTracker.isBusy(x)) {
                changed = this.handleCrafting(x, adaptor, itemStack);
            } else if (itemStack.getStackSize() > 0) {
                // make sure strange things didn't happen...
                if (adaptor.simulateAdd(itemStack.getItemStack()) != null) {
                    changed = true;
                    throw new GridAccessException();
                }

                if (((fuzzycards == 1) && (x > 5)) || ((fuzzycards == 2) && (x > 2)) || (fuzzycards == 3)) {
                    int iteration = IterationCounter.fetchNewId();
                    final IAEItemStack[] fpe = fuzzyPoweredExtraction(
                            src,
                            this.destination,
                            itemStack,
                            this.stored,
                            this.interfaceRequestSource,
                            iteration);
                    acquired = fpe[0];
                    this.fuzzyItemStack = fpe[1];
                    hasFuzzyConfig[x] = true;
                } else {
                    acquired = Platform
                            .poweredExtraction(src, this.destination, itemStack, this.interfaceRequestSource);
                    this.fuzzyItemStack = null;
                }
                if (acquired != null) {
                    changed = true;
                    final ItemStack issue = adaptor.addItems(acquired.getItemStack());
                    if (issue != null) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( addItems )");
                    }
                } else {
                    changed = this.handleCrafting(x, adaptor, itemStack);
                    if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
                        changed = true;
                    }
                }
            }
            // else wtf?
        } catch (final GridAccessException e) {
            // :P
        }

        if (changed) {
            this.updatePlan(x);
        }

        this.isWorking = false;
        return changed;
    }

    private InventoryAdaptor getAdaptor(final int slot) {
        return new AdaptorIInventory(this.slotInv.getWrapper(slot));
    }

    @Override
    public BlockingMode getBlockingMode() {
        if (this.isBlocking() && this.isSmartBlocking()) return BlockingMode.SMART_BLOCKING;
        if (this.isBlocking()) return BlockingMode.BLOCKING;
        return BlockingMode.NONE;
    }

    private boolean handleCrafting(final int x, final InventoryAdaptor d, final IAEItemStack itemStack) {
        try {
            if (this.getInstalledUpgrades(Upgrades.CRAFTING) > 0 && itemStack != null) {
                return this.craftingTracker.handleCrafting(
                        x,
                        itemStack.getStackSize(),
                        itemStack,
                        d,
                        this.iHost.getTileEntity().getWorldObj(),
                        this.gridProxy.getGrid(),
                        this.gridProxy.getCrafting(),
                        this.mySource);
            }
        } catch (final GridAccessException e) {
            // :P
        }

        return false;
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        if (this.upgrades == null) {
            return 0;
        }
        return this.upgrades.getInstalledUpgrades(u);
    }

    @Override
    public TileEntity getTile() {
        return (TileEntity) (this.iHost instanceof TileEntity ? this.iHost : null);
    }

    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        if (this.hasConfig()) {
            return new InterfaceInventory(this);
        }

        return this.items;
    }

    private boolean hasConfig() {
        return this.hasConfig;
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("storage")) {
            return this.storage;
        }

        if (name.equals("patterns")) {
            return this.patterns;
        }

        if (name.equals("config")) {
            return this.config;
        }

        if (name.equals("upgrades")) {
            return this.upgrades;
        }

        return null;
    }

    public IInventory getStorage() {
        return this.storage;
    }

    @Override
    public appeng.api.util.IConfigManager getConfigManager() {
        return this.cm;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.getInstalledUpgrades(Upgrades.CRAFTING) == 0) {
            this.cancelCrafting();
        }

        if (settingName == Settings.LOCK_CRAFTING_MODE) {
            if (unlockEvent != null && !unlockEvent.matches((LockCraftingMode) newValue)) {
                resetCraftingLock();
            }
        }

        this.markDirty();
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        if (this.hasConfig()) {
            return null;
        }

        return this.fluids;
    }

    private void cancelCrafting() {
        this.craftingTracker.cancel();
    }

    public IStorageMonitorable getMonitorable(final ForgeDirection side, final BaseActionSource src,
            final IStorageMonitorable myInterface) {
        if (Platform.canAccess(this.gridProxy, src)) {
            return myInterface;
        }

        final DualityInterface di = this;

        return new IStorageMonitorable() {

            @Override
            public IMEMonitor<IAEItemStack> getItemInventory() {
                return new InterfaceInventory(di);
            }

            @Override
            public IMEMonitor<IAEFluidStack> getFluidInventory() {
                return null;
            }
        };
    }

    private boolean tileHasOnlyIgnoredItems(InventoryAdaptor ad) {
        for (ItemSlot i : ad) {
            ItemStack is = i.getItemStack();
            if (is == null || BlockingModeIgnoreItemRegistry.instance().isIgnored(is)) continue;
            return false;
        }
        return true;
    }

    private boolean shouldCheckFluid() {
        String hostName = this.iHost.getClass().getName();
        return hostName.contains("TileFluidInterface") || hostName.contains("PartFluidInterface");
    }

    private boolean inventoryCountsAsEmpty(TileEntity te, InventoryAdaptor ad, ForgeDirection side) {
        String name = te.getBlockType().getUnlocalizedName();
        boolean isEmpty = (name.equals("gt.blockmachines") || name.equals("tile.interface")
                || name.equals("tile.blockWritingTable")) && tileHasOnlyIgnoredItems(ad);
        if (shouldCheckFluid()) {
            isEmpty = name.equals("tile.interface");
        }
        return isEmpty;
    }
          
    public void notifyPushedPattern(IInterfaceHost pushingHost) {
        if (this.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) == 0) return;
        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorldObj();

        final EnumSet<ForgeDirection> possibleDirections = this.iHost.getTargets();

        for (ForgeDirection s : possibleDirections) {
            final TileEntity te = w
                    .getTileEntity(tile.xCoord + s.offsetX, tile.yCoord + s.offsetY, tile.zCoord + s.offsetZ);
            if (te == null) continue;
            try {
                if (te instanceof IInterfaceHost host) {

                    if (host.getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                        continue;
                    }
                    if (host == pushingHost) {
                        continue;
                    }
                    host.getInterfaceDuality().receivePatternPushedEvent();

                } else if (te instanceof TileCableBus cableBus) {
                    IPart part = cableBus.getPart(s.getOpposite());
                    if (part instanceof IInterfaceHost host) {
                        if (host == pushingHost) {
                            continue;
                        }
                        host.getInterfaceDuality().receivePatternPushedEvent();
                    }
                }
            } catch (final GridAccessException e) {
                continue;
            }
        }
    }

    private CraftingCPUCluster cluster;

    @Override
    public boolean pushPatternWithCluster(ICraftingPatternDetails patternDetails, InventoryCrafting table, CraftingCPUCluster cluster) {
        this.cluster = cluster;
        return pushPattern(patternDetails, table);
    }

    public void receivePatternPushedEvent() {
        this.lastInputHash = 0;
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        if (this.hasItemsToSend() || !this.gridProxy.isActive() || !this.craftingList.contains(patternDetails)) {
            return false;
        }
        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorldObj();

        final EnumSet<ForgeDirection> possibleDirections = this.iHost.getTargets();
        EnumSet<ForgeDirection> out = EnumSet.noneOf(ForgeDirection.class);
        for (final ForgeDirection s : possibleDirections) {
            final TileEntity te = w
                    .getTileEntity(tile.xCoord + s.offsetX, tile.yCoord + s.offsetY, tile.zCoord + s.offsetZ);

            if (te == null) continue;

            if (te.getClass().getName().equals("li.cil.oc.common.tileentity.Adapter")) continue;

            if (te instanceof ICraftingMachine cm) {
                if (cm.acceptsPlans()) {
                    if (cm.pushPatternWithCluster(patternDetails, table, s.getOpposite(), cluster)) {
                        if (this.isSmartBlocking()) {
                            this.lastInputHash = patternDetails.hashCode();
                        }
                        onPushPatternSuccess(te, s.getOpposite(), patternDetails);
                        return true;
                    }
                    continue;
                }
            }

            if (te instanceof IInterfaceHost) {
                try {
                    if (((IInterfaceHost) te).getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                        continue;
                    }
                } catch (final GridAccessException e) {
                    continue;
                }
            }

            final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
            if (ad != null) {
                if (this.isBlocking() && !(this.isSmartBlocking() && this.lastInputHash == patternDetails.hashCode())
                        && ad.containsItems()
                        && !inventoryCountsAsEmpty(te, ad, s.getOpposite()))
                    continue;

                if (acceptsItems(ad, table, getInsertionMode())) {
                    for (int x = 0; x < table.getSizeInventory(); x++) {
                        final ItemStack is = table.getStackInSlot(x);
                        if (is != null) {
                            this.addToSendList(ad.addItems(is, getInsertionMode()));
                        }
                    }
                    out.add(s);
                    this.pushItemsOut(out);
                    onPushPatternSuccess(te, s.getOpposite(), patternDetails);
                    return true;
                }
            } else if (EIO && te instanceof IItemDuct) {
                boolean hadAcceptedSome = false;
                for (int x = 0; x < table.getSizeInventory(); x++) {
                    final ItemStack is = table.getStackInSlot(x);
                    if (is != null) {
                        final ItemStack rest = ((IItemDuct) te).insertItem(s.getOpposite(), is);
                        if (!hadAcceptedSome && rest != null && rest.stackSize == is.stackSize) break; // conduit should
                        // accept all the
                        // pattern or
                        // nothing.
                        hadAcceptedSome = true;
                        this.addToSendList(rest);
                    }
                }
                if (hadAcceptedSome) {
                    out.add(s);
                    this.pushItemsOut(out);
                    onPushPatternSuccess(te, s.getOpposite(), patternDetails);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isBusy() {
        if (this.hasItemsToSend()) {
            return true;
        }

        boolean busy = false;

        if (this.isBlocking()) {
            if (this.isSmartBlocking()) {
                return false;
            }
            final EnumSet<ForgeDirection> possibleDirections = this.iHost.getTargets();
            final TileEntity tile = this.iHost.getTileEntity();
            final World w = tile.getWorldObj();

            boolean allAreBusy = true;

            for (final ForgeDirection s : possibleDirections) {
                final TileEntity te = w
                        .getTileEntity(tile.xCoord + s.offsetX, tile.yCoord + s.offsetY, tile.zCoord + s.offsetZ);
                if (te != null && te.getClass().getName().equals("li.cil.oc.common.tileentity.Adapter")) continue;
                final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
                if (ad != null) {
                    if (ad.simulateRemove(1, null, null) == null || inventoryCountsAsEmpty(te, ad, s.getOpposite())) {
                        allAreBusy = false;
                        break;
                    }
                }
            }

            busy = allAreBusy;
        }

        if (this.getCraftingLockedReason() != LockCraftingMode.NONE) {
            busy = true;
        }

        return busy;
    }

    private boolean sameGrid(final IGrid grid) throws GridAccessException {
        return grid == this.gridProxy.getGrid();
    }

    private boolean isBlocking() {
        return this.cm.getSetting(Settings.BLOCK) == YesNo.YES;
    }

    private boolean isSmartBlocking() {
        return this.cm.getSetting(Settings.SMART_BLOCK) == YesNo.YES;
    }

    private InsertionMode getInsertionMode() {
        return (InsertionMode) cm.getSetting(Settings.INSERTION_MODE);
    }

    public boolean isFakeCraftingMode() {
        return this.getInstalledUpgrades(Upgrades.FAKE_CRAFTING) != 0;
    }

    private static boolean acceptsItems(final InventoryAdaptor ad, final InventoryCrafting table,
            final InsertionMode insertionMode) {
        for (int x = 0; x < table.getSizeInventory(); x++) {
            ItemStack is = table.getStackInSlot(x);
            if (is == null) {
                continue;
            }
            is = is.copy();
            boolean moreItemsMayFit;
            do {
                final int originalCount = is.stackSize;
                final ItemStack remainingAfterSimulatedAdd = ad.simulateAdd(is.copy(), insertionMode);
                final int remainingCount;
                if (remainingAfterSimulatedAdd != null) {
                    if (remainingAfterSimulatedAdd.isItemEqual(is)) {
                        remainingCount = originalCount - remainingAfterSimulatedAdd.stackSize;
                    } else {
                        return false;
                    }
                } else {
                    remainingCount = 0;
                }
                is.stackSize = remainingCount;
                moreItemsMayFit = (remainingCount > 0) && (remainingCount < originalCount);
            } while (moreItemsMayFit);
            if (is.stackSize > 0) {
                // Can't fit all of the items
                return false;
            }
        }

        return true;
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        if (this.gridProxy.isActive() && this.craftingList != null) {
            for (final ICraftingPatternDetails details : this.craftingList) {
                craftingTracker.addCraftingOption(this, details);
            }
        }
    }

    public void addDrops(final List<ItemStack> drops) {
        if (this.waitingToSend != null) {
            for (final ItemStack is : this.waitingToSend) {
                if (is != null) {
                    drops.add(is);
                }
            }
        }

        for (final ItemStack is : this.upgrades) {
            if (is != null) {
                drops.add(is);
            }
        }

        for (final ItemStack is : this.storage) {
            if (is != null) {
                drops.add(is);
            }
        }

        for (final ItemStack is : this.patterns) {
            if (is != null) {
                drops.add(is);
            }
        }
    }

    public IUpgradeableHost getHost() {
        if (this.getPart() != null) {
            return (IUpgradeableHost) this.getPart();
        }
        if (this.getTile() instanceof IUpgradeableHost) {
            return (IUpgradeableHost) this.getTile();
        }
        return null;
    }

    private IPart getPart() {
        return (IPart) (this.iHost instanceof IPart ? this.iHost : null);
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.craftingTracker.getRequestedJobs();
    }

    public IAEItemStack injectCraftedItems(final ICraftingLink link, final IAEItemStack acquired,
            final Actionable mode) {
        final int slot = this.craftingTracker.getSlot(link);

        if (acquired != null && slot >= 0 && slot <= this.requireWork.length) {
            final InventoryAdaptor adaptor = this.getAdaptor(slot);

            if (mode == Actionable.SIMULATE) {
                return AEItemStack.create(adaptor.simulateAdd(acquired.getItemStack()));
            } else {
                final IAEItemStack is = AEItemStack.create(adaptor.addItems(acquired.getItemStack()));
                this.updatePlan(slot);
                return is;
            }
        }

        return acquired;
    }

    public void jobStateChange(final ICraftingLink link) {
        this.craftingTracker.jobStateChange(link);
    }

    @Override
    public ItemStack getCrafterIcon() {
        final TileEntity hostTile = this.iHost.getTileEntity();
        final World hostWorld = hostTile.getWorldObj();

        String customName = null;
        if (((ICustomNameObject) this.iHost).hasCustomName()) {
            customName = ((ICustomNameObject) this.iHost).getCustomName();
        }

        final EnumSet<ForgeDirection> possibleDirections = this.iHost.getTargets();
        for (final ForgeDirection direction : possibleDirections) {
            final int xPos = hostTile.xCoord + direction.offsetX;
            final int yPos = hostTile.yCoord + direction.offsetY;
            final int zPos = hostTile.zCoord + direction.offsetZ;
            final TileEntity directedTile = hostWorld.getTileEntity(xPos, yPos, zPos);

            if (directedTile == null) {
                continue;
            }

            if (directedTile instanceof IInterfaceHost) {
                try {
                    if (((IInterfaceHost) directedTile).getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                        continue;
                    }
                } catch (final GridAccessException e) {
                    continue;
                }
            }

            ICraftingIconProvider craftingIconProvider = getCapability(directedTile, ICraftingIconProvider.class);
            if (craftingIconProvider != null) {
                final ItemStack icon = craftingIconProvider.getMachineCraftingIcon();
                if (icon != null) {
                    if (customName != null) {
                        icon.setStackDisplayName(customName);
                    }
                    return icon;
                }
            }

            final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(directedTile, direction.getOpposite());
            if (directedTile instanceof ICraftingMachine || adaptor != null) {
                if (directedTile instanceof IInventory && ((IInventory) directedTile).getSizeInventory() == 0) {
                    continue;
                }

                if (directedTile instanceof ISidedInventory) {
                    final int[] sides = ((ISidedInventory) directedTile)
                            .getAccessibleSlotsFromSide(direction.getOpposite().ordinal());

                    if (sides == null || sides.length == 0) {
                        continue;
                    }
                }

                final Block directedBlock = hostWorld.getBlock(xPos, yPos, zPos);
                ItemStack what = new ItemStack(
                        directedBlock,
                        1,
                        directedBlock.getDamageValue(hostWorld, xPos, yPos, zPos));
                try {
                    Vec3 from = Vec3
                            .createVectorHelper(hostTile.xCoord + 0.5, hostTile.yCoord + 0.5, hostTile.zCoord + 0.5);
                    from = from
                            .addVector(direction.offsetX * 0.501, direction.offsetY * 0.501, direction.offsetZ * 0.501);
                    final Vec3 to = from.addVector(direction.offsetX, direction.offsetY, direction.offsetZ);
                    final MovingObjectPosition mop = hostWorld.rayTraceBlocks(from, to, true);
                    if (mop != null && !BAD_BLOCKS.contains(directedBlock)) {
                        if (mop.blockX == directedTile.xCoord && mop.blockY == directedTile.yCoord
                                && mop.blockZ == directedTile.zCoord) {
                            final ItemStack g = directedBlock.getPickBlock(
                                    mop,
                                    hostWorld,
                                    directedTile.xCoord,
                                    directedTile.yCoord,
                                    directedTile.zCoord,
                                    null);
                            if (g != null) {
                                what = g;
                            }
                        }
                    }
                } catch (final Throwable t) {
                    BAD_BLOCKS.add(directedBlock); // nope!
                }

                if (what.getItem() != null) {
                    if (customName != null) {
                        what.setStackDisplayName(customName);
                    }
                    return what;
                }

                final Item item = Item.getItemFromBlock(directedBlock);
                if (item != null) {
                    final ItemStack icon = new ItemStack(item);
                    if (customName != null) {
                        icon.setStackDisplayName(customName);
                    }
                    return icon;
                }
            }
        }

        return null;
    }

    public String getTermName() {
        if (((ICustomNameObject) this.iHost).hasCustomName()) {
            return ((ICustomNameObject) this.iHost).getCustomName();
        }

        final ItemStack item = getCrafterIcon();
        if (item != null) {
            return item.getUnlocalizedName();
        } else {
            return "Nothing";
        }
    }

    public BaseActionSource getActionSource() {
        return interfaceRequestSource;
    }

    public void initialize() {
        this.updateCraftingList();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.markDirty();

        // Update the priority of stored patterns.
        this.craftingList = null;
        this.updateCraftingList();

        try {
            this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
        } catch (final GridAccessException e) {
            // :P
        }
    }

    public void resetCraftingLock() {
        if (unlockEvent != null) {
            unlockEvent = null;
            unlockStacks = null;
            saveChanges();
        }
    }

    private void onPushPatternSuccess(TileEntity te, ForgeDirection s, ICraftingPatternDetails pattern) {
        if (this.isSmartBlocking()) {
            this.lastInputHash = pattern.hashCode();
            if (te instanceof IInterfaceHost oppositeHost) {
                try {
                    if (oppositeHost.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0) {
                        oppositeHost.getInterfaceDuality().gridProxy.getGrid()
                                .postEvent(new MENetworkCraftingPushedPattern(this.iHost));
                    }
                } catch (GridAccessException e) {
                    // :P
                }
            } else if (te instanceof TileCableBus cableBus) {
                IPart part = cableBus.getPart(s);
                if (part instanceof IInterfaceHost oppositeHost) {
                    try {
                        if (oppositeHost.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0) {
                            oppositeHost.getInterfaceDuality().gridProxy.getGrid()
                                    .postEvent(new MENetworkCraftingPushedPattern(this.iHost));
                        }
                    } catch (GridAccessException e) {
                        // :P
                    }
                }
            }
        }
        resetCraftingLock();

        LockCraftingMode lockMode = (LockCraftingMode) cm.getSetting(Settings.LOCK_CRAFTING_MODE);
        switch (lockMode) {
            case LOCK_UNTIL_PULSE -> {
                unlockEvent = UnlockCraftingEvent.PULSE;
                saveChanges();
            }
            case LOCK_UNTIL_RESULT -> {
                unlockEvent = UnlockCraftingEvent.RESULT;
                if (unlockStacks == null) {
                    unlockStacks = new ArrayList<>();
                }
                for (IAEItemStack output : pattern.getCondensedOutputs()) {
                    unlockStacks.add(output.copy());
                }
                saveChanges();
            }
        }
    }

    /**
     * Gets if the crafting lock is in effect and why.
     *
     * @return LockCraftingMode.NONE if the lock isn't in effect
     */
    public LockCraftingMode getCraftingLockedReason() {
        var lockMode = cm.getSetting(Settings.LOCK_CRAFTING_MODE);
        if (lockMode == LockCraftingMode.LOCK_WHILE_LOW && !getRedstoneState()) {
            // Crafting locked by redstone signal
            return LockCraftingMode.LOCK_WHILE_LOW;
        } else if (lockMode == LockCraftingMode.LOCK_WHILE_HIGH && getRedstoneState()) {
            return LockCraftingMode.LOCK_WHILE_HIGH;
        } else if (unlockEvent != null) {
            // Crafting locked by waiting for unlock event
            switch (unlockEvent) {
                case PULSE -> {
                    return LockCraftingMode.LOCK_UNTIL_PULSE;
                }
                case RESULT -> {
                    return LockCraftingMode.LOCK_UNTIL_RESULT;
                }
            }
        }
        return LockCraftingMode.NONE;
    }

    /**
     * @return Null if {@linkplain #getCraftingLockedReason()} is not {@link LockCraftingMode#LOCK_UNTIL_RESULT}.
     */
    public List<IAEItemStack> getUnlockStacks() {
        return unlockStacks;
    }

    /**
     * Called when an ItemStack has been pushed into the network from the internal buffer. Public to enable other
     * interface types (mainly AE2FC) to work with locking return mode.
     */
    public void onStackReturnedToNetwork(IAEItemStack returnedStack) {
        if (unlockEvent != UnlockCraftingEvent.RESULT) {
            return; // If we're not waiting for the result, we don't care
        }

        if (unlockStacks == null) {
            // Actually an error state...
            AELog.error("interface was waiting for RESULT, but no result was set");
            unlockEvent = null;
            return;
        }
        boolean changed = false;
        for (Iterator<IAEItemStack> iterator = unlockStacks.iterator(); iterator.hasNext();) {
            IAEItemStack unlockStack = iterator.next();
            if (unlockStack.equals(returnedStack)) {
                changed = true;
                unlockStack.decStackSize(returnedStack.getStackSize());
                if (unlockStack.getStackSize() <= 0) {
                    iterator.remove();
                }
                break;
            }
        }
        if (unlockStacks.isEmpty()) {
            unlockEvent = null;
            unlockStacks = null;
        }
        if (changed) {
            saveChanges();
        }
    }

    public void updateRedstoneState() {
        // reset cache to undecided
        redstoneState = YesNo.UNDECIDED;

        // If we're waiting for a pulse, update immediately
        if (unlockEvent == UnlockCraftingEvent.PULSE && getRedstoneState()) {
            unlockEvent = null; // Unlocked!
            saveChanges();
        }
    }

    private boolean getRedstoneState() {
        if (redstoneState == YesNo.UNDECIDED) {
            TileEntity tile = this.getHost().getTile();
            redstoneState = tile.getWorldObj().isBlockIndirectlyGettingPowered(tile.xCoord, tile.yCoord, tile.zCoord)
                    ? YesNo.YES
                    : YesNo.NO;
        }
        return redstoneState == YesNo.YES;
    }

    private static class InterfaceRequestSource extends MachineSource {

        public InterfaceRequestSource(final IActionHost v) {
            super(v);
        }
    }

    private class InterfaceInventory extends MEMonitorIInventory {

        public InterfaceInventory(final DualityInterface tileInterface) {
            super(new AdaptorIInventory(tileInterface.storage));
            this.setActionSource(new MachineSource(DualityInterface.this.iHost));
        }

        @Override
        public IAEItemStack injectItems(final IAEItemStack input, final Actionable type, final BaseActionSource src) {
            if (src instanceof InterfaceRequestSource) {
                return input;
            }

            return super.injectItems(input, type, src);
        }

        @Override
        public IAEItemStack extractItems(final IAEItemStack request, final Actionable type,
                final BaseActionSource src) {
            if (src instanceof InterfaceRequestSource) {
                return null;
            }

            return super.extractItems(request, type, src);
        }
    }
}
