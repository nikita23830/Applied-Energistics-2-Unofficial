package appeng.helpers;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.google.common.collect.ImmutableSet;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.InventoryAdaptor;

public class MultiCraftingTracker {

    private final int size;
    private final ICraftingRequester owner;

    private final Map<Integer, Future<ICraftingJob>> jobs = new HashMap<>();
    private final Map<Integer, ICraftingLink> links = new HashMap<>();

    public MultiCraftingTracker(final ICraftingRequester o, final int size) {
        this.owner = o;
        this.size = size;
    }

    public void readFromNBT(final NBTTagCompound extra) {
        for (int x = 0; x < this.size; x++) {
            final NBTTagCompound linkTag = extra.getCompoundTag("links-" + x);

            if (linkTag != null && !linkTag.hasNoTags()) {
                ICraftingLink link = AEApi.instance().storage().loadCraftingLink(linkTag, this.owner);
                if (link != null) {
                    links.put(x, link);
                }
            }
        }
    }

    public void writeToNBT(final NBTTagCompound extra) {
        for (Entry<Integer, ICraftingLink> entry : links.entrySet()) {
            if (entry.getValue() != null) {
                final NBTTagCompound linkTag = new NBTTagCompound();
                entry.getValue().writeToNBT(linkTag);
                extra.setTag("links-" + entry.getKey(), linkTag);
            }
        }
    }

    public boolean handleCrafting(final int slot, final long itemToCraft, final IAEItemStack ais, final InventoryAdaptor d,
                                  final World world, final IGrid grid, final ICraftingGrid craftingGrid, final BaseActionSource source) {
        if (ais != null && d.simulateAdd(ais.getItemStack()) == null) {
            if (links.containsKey(slot)) {
                return false;
            }

            Future<ICraftingJob> craftingJob = jobs.get(slot);
            if (craftingJob != null) {
                try {
                    if (craftingJob.isDone()) {
                        ICraftingJob job = craftingJob.get();
                        if (job == null) {
                            return false;
                        }
                        ICraftingLink link = craftingGrid.submitJob(job, this.owner, null, false, source);
                        jobs.remove(slot);

                        if (link != null) {
                            links.put(slot, link);
                            return true;
                        }
                    }
                } catch (final InterruptedException | ExecutionException | CancellationException e) {
                    jobs.remove(slot);
                }
            } else {
                if (!links.containsKey(slot)) {
                    jobs.put(slot, craftingGrid.beginCraftingJob(world, grid, source, ais.copy().setStackSize(itemToCraft), null));
                }
            }
        }
        return false;
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.copyOf(links.values());
    }

    public void jobStateChange(final ICraftingLink link) {
        links.values().removeIf(existingLink -> existingLink == link);
    }

    public int getSlot(final ICraftingLink link) {
        return links.entrySet().stream()
                .filter(entry -> entry.getValue() == link)
                .map(Entry::getKey)
                .findFirst()
                .orElse(-1);
    }

    public void cancel() {
        Collection<ICraftingLink> values = links.values();
        for (ICraftingLink value : values) {
            value.cancel();
        }
        links.clear();

        Collection<Future<ICraftingJob>> values1 = jobs.values();
        for (Future<ICraftingJob> iCraftingJobFuture : values1) {
            iCraftingJobFuture.cancel(true);
        }
        jobs.clear();
    }

    public boolean isBusy(final int slot) {
        return links.containsKey(slot) || jobs.containsKey(slot);
    }
}
