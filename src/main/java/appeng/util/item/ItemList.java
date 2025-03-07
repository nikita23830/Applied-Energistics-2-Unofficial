/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util.item;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public final class ItemList implements IItemList<IAEItemStack> {

    private final Object2ObjectAVLTreeMap<IAEItemStack, IAEItemStack> records = new Object2ObjectAVLTreeMap<>();
    private final ObjectOpenHashSet<IAEItemStack> setRecords = new ObjectOpenHashSet<>();

    @Override
    public void add(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.setRecords.get(option);

        if (st != null) {
            st.add(option);
            return;
        }

        final IAEItemStack opt = option.copy();

        this.putItemRecord(opt);
    }

    @Override
    public IAEItemStack findPrecise(final IAEItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        return this.setRecords.get(itemStack);
    }

    @Override
    public Collection<IAEItemStack> findFuzzy(final IAEItemStack filter, final FuzzyMode fuzzy) {
        if (filter == null) {
            return Collections.emptyList();
        }

        final AEItemStack ais = (AEItemStack) filter;

        if (ais.isOre()) {
            final OreReference or = ais.getDefinition().getIsOre();

            if (or.getAEEquivalents().size() == 1) {
                final IAEItemStack is = or.getAEEquivalents().get(0);

                return this
                        .findFuzzyDamage((AEItemStack) is, fuzzy, is.getItemDamage() == OreDictionary.WILDCARD_VALUE);
            } else {
                final Collection<IAEItemStack> output = new LinkedList<>();

                for (final IAEItemStack is : or.getAEEquivalents()) {
                    output.addAll(
                            this.findFuzzyDamage(
                                    (AEItemStack) is,
                                    fuzzy,
                                    is.getItemDamage() == OreDictionary.WILDCARD_VALUE));
                }

                return output;
            }
        }

        return this.findFuzzyDamage(ais, fuzzy, false);
    }

    @Override
    public boolean isEmpty() {
        return !this.iterator().hasNext();
    }

    @Override
    public void addStorage(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.setRecords.get(option);

        if (st != null) {
            st.incStackSize(option.getStackSize());
            return;
        }

        final IAEItemStack opt = option.copy();

        this.putItemRecord(opt);
    }

    /*
     * public void clean() { Iterator<StackType> i = iterator(); while (i.hasNext()) { StackType AEI = i.next(); if (
     * !AEI.isMeaningful() ) i.remove(); } }
     */

    @Override
    public void addCrafting(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.setRecords.get(option);

        if (st != null) {
            st.setCraftable(true);
            return;
        }

        final IAEItemStack opt = option.copy();
        opt.setStackSize(0);
        opt.setCraftable(true);

        this.putItemRecord(opt);
    }

    @Override
    public void addRequestable(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.setRecords.get(option);

        if (st != null) {
            st.setCountRequestable(st.getCountRequestable() + option.getCountRequestable());
            st.setCountRequestableCrafts(st.getCountRequestableCrafts() + option.getCountRequestableCrafts());
            return;
        }

        final IAEItemStack opt = option.copy();
        opt.setStackSize(0);
        opt.setCraftable(false);
        opt.setCountRequestable(option.getCountRequestable());
        opt.setCountRequestableCrafts(option.getCountRequestableCrafts());

        this.putItemRecord(opt);
    }

    @Override
    public IAEItemStack getFirstItem() {
        for (final IAEItemStack stackType : this) {
            return stackType;
        }

        return null;
    }

    @Override
    public int size() {
        return this.setRecords.size();
    }

    @Override
    public Iterator<IAEItemStack> iterator() {
        return new MeaningfulItemIterator<>(new Iterator<>() {

            private final Iterator<IAEItemStack> i = ItemList.this.records.values().iterator();
            private IAEItemStack next = null;

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public IAEItemStack next() {
                return (next = i.next());
            }

            @Override
            public void remove() {
                i.remove();
                ItemList.this.setRecords.remove(next);
            }
        });
    }

    @Override
    public void resetStatus() {
        for (final IAEItemStack i : this) {
            i.reset();
        }
    }

    @Override
    public String toString() {
        return "{ItemList: [" + this.setRecords.toString() + "]}";
    }

    public void clear() {
        this.setRecords.clear();
        this.records.clear();
    }

    private void putItemRecord(final IAEItemStack itemStack) {
        this.setRecords.add(itemStack);
        this.records.put(itemStack, itemStack);
    }

    private Collection<IAEItemStack> findFuzzyDamage(final AEItemStack filter, final FuzzyMode fuzzy,
            final boolean ignoreMeta) {
        final IAEItemStack low = filter.getLow(fuzzy, ignoreMeta);
        final IAEItemStack high = filter.getHigh(fuzzy, ignoreMeta);
        ObjectArrayList<IAEItemStack> ias = new ObjectArrayList<>();
        for (Map.Entry<IAEItemStack, IAEItemStack> iks : records.entrySet()) {
            IAEItemStack ik = iks.getKey();
            if (ik.getItem() != filter.getItem())
                continue;
            if (filter.hasTagCompound()) {
                if (ik.hasTagCompound()) {
                    if (!filter.getTagCompound().equals(ik.getTagCompound())) {
                        continue;
                    } else {
                        if (ik.getItemDamage() >= low.getItemDamage() && ik.getItemDamage() <= high.getItemDamage()) {
                            ias.add(iks.getValue());
                        }
                    }
                }
            } else if (!ik.hasTagCompound()) {
                if (ik.getItemDamage() >= low.getItemDamage() && ik.getItemDamage() <= high.getItemDamage()) {
                    ias.add(iks.getValue());
                }
            }
        }
        ias.sort(Comparator.comparing(IAEItemStack::getItemDamage).reversed());
        return ias;
//        return this.records.subMap(low, true, high, true).descendingMap().values();
    }
}
