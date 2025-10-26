package appeng.container.implementations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.NumberFormat;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import appeng.api.config.CraftingAllow;

/**
 * Summary status for the crafting CPU selection widget
 */
public class CraftingCPUStatus implements Comparable<CraftingCPUStatus> {

    private static final IWideReadableNumberConverter NUMBER_CONVERTER = ReadableNumberConverter.INSTANCE;

    @Nullable
    private final ICraftingCPU serverCluster;

    private final String name;
    private final int serial;
    private final long storage;
    private final long usedStorage;
    private final long coprocessors;
    private final boolean isBusy;
    private final long totalItems;
    private final long remainingItems;
    private final IAEItemStack crafting;
    private final CraftingAllow allowMode;
    private final long craftingElapsedTime;

    public CraftingCPUStatus() {
        this.serverCluster = null;
        this.name = "ERROR";
        this.serial = 0;
        this.storage = 0;
        this.usedStorage = 0;
        this.coprocessors = 0;
        this.isBusy = false;
        this.totalItems = 0;
        this.remainingItems = 0;
        this.crafting = null;
        this.allowMode = CraftingAllow.ALLOW_ALL;
        this.craftingElapsedTime = 0;
    }

    public CraftingCPUStatus(ICraftingCPU cluster, int serial) {
        this.serverCluster = cluster;
        this.name = cluster.getName();
        this.serial = serial;
        this.isBusy = cluster.isBusy();
        if (isBusy) {
            crafting = cluster.getFinalOutput();
            usedStorage = cluster.getUsedStorage();
            totalItems = cluster.getStartItemCount();
            remainingItems = cluster.getRemainingItemCount();
            craftingElapsedTime = cluster.getElapsedTime();
        } else {
            crafting = null;
            usedStorage = 0;
            totalItems = 0;
            remainingItems = 0;
            craftingElapsedTime = 0;
        }
        this.storage = cluster.getAvailableStorage();
        this.coprocessors = cluster.getCoProcessors();
        this.allowMode = cluster.getCraftingAllowMode();
    }

    public CraftingCPUStatus(NBTTagCompound i) {
        this.serverCluster = null;
        this.name = i.getString("name");
        this.serial = i.getInteger("serial");
        this.storage = i.getLong("storage");
        this.usedStorage = i.getLong("usedStorage");
        this.coprocessors = i.getLong("coprocessors");
        this.isBusy = i.getBoolean("isBusy");
        this.totalItems = i.getLong("totalItems");
        this.remainingItems = i.getLong("remainingItems");
        this.crafting = i.hasKey("crafting") ? AEItemStack.loadItemStackFromNBT(i.getCompoundTag("crafting")) : null;
        this.allowMode = i.hasKey("allowMode") ? CraftingAllow.values()[i.getInteger("allowMode")]
                : CraftingAllow.ALLOW_ALL;
        this.craftingElapsedTime = i.hasKey("craftingElapsedTime") ? i.getLong("craftingElapsedTime") : 0;
    }

    public CraftingCPUStatus(ByteBuf packet) throws IOException {
        this(readNBTFromPacket(packet));
    }

    private static NBTTagCompound readNBTFromPacket(ByteBuf packet) throws IOException {
        final int size = packet.readInt();
        final byte[] tagBytes = new byte[size];
        packet.readBytes(tagBytes);
        final ByteArrayInputStream di = new ByteArrayInputStream(tagBytes);
        return CompressedStreamTools.read(new DataInputStream(di));
    }

    public void writeToNBT(NBTTagCompound i) {
        if (name != null && !name.isEmpty()) {
            i.setString("name", name);
        }
        i.setInteger("serial", serial);
        i.setLong("storage", storage);
        i.setLong("usedStorage", usedStorage);
        i.setLong("coprocessors", coprocessors);
        i.setBoolean("isBusy", isBusy);
        i.setLong("totalItems", totalItems);
        i.setLong("remainingItems", remainingItems);
        i.setLong("craftingElapsedTime", craftingElapsedTime);
        if (crafting != null) {
            NBTTagCompound stack = new NBTTagCompound();
            crafting.writeToNBT(stack);
            i.setTag("crafting", stack);
        }
        i.setInteger("allowMode", this.allowMode.ordinal());
    }

    public CraftingAllow allowMode() {
        return allowMode;
    }

    public long getCraftingElapsedTime() {
        return craftingElapsedTime;
    }

    public void writeToPacket(ByteBuf i) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream data = new DataOutputStream(bytes);

        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        CompressedStreamTools.write(tag, data);

        final byte[] tagBytes = bytes.toByteArray();
        final int size = tagBytes.length;

        i.writeInt(size);
        i.writeBytes(tagBytes);
    }

    @Nullable
    public ICraftingCPU getServerCluster() {
        return serverCluster;
    }

    public String getName() {
        return name;
    }

    public int getSerial() {
        return serial;
    }

    public long getStorage() {
        return storage;
    }

    public long getUsedStorage() {
        return usedStorage;
    }

    public long getCoprocessors() {
        return coprocessors;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public long getRemainingItems() {
        return remainingItems;
    }

    public IAEItemStack getCrafting() {
        return crafting;
    }

    public boolean isBusy() {
        return isBusy;
    }

    @Override
    public int compareTo(CraftingCPUStatus o) {
        final int a = ItemSorters.compareLong(o.getCoprocessors(), this.getCoprocessors());
        if (a != 0) {
            return a;
        }
        return ItemSorters.compareLong(o.getStorage(), this.getStorage());
    }

    public String formatCoprocessors() {
        return NumberFormat.getInstance().format(getCoprocessors());
    }

    public String formatShorterCoprocessors() {
        return NUMBER_CONVERTER.toWideReadableForm(getCoprocessors());
        // return Platform.formatNumberLong(getCoprocessors());
    }

    public String formatStorage() {
        return Platform.formatByteDouble(getStorage());
    }

    public String formatUsedStorage() {
        return Platform.formatByteDouble(getUsedStorage());
    }
}
