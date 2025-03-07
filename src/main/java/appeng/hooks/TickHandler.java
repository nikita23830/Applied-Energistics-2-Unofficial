/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.hooks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.google.common.base.Stopwatch;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.parts.CableRenderMode;
import appeng.api.util.AEColor;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.CommonHelper;
import appeng.core.sync.packets.PacketPaintedEntity;
import appeng.entity.EntityFloatingItem;
import appeng.me.Grid;
import appeng.me.NetworkList;
import appeng.tile.AEBaseTile;
import appeng.util.IWorldCallable;
import appeng.util.Platform;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.Type;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;

public class TickHandler {

    public static final TickHandler INSTANCE = new TickHandler();
    private final Queue<IWorldCallable<?>> serverQueue = new LinkedList<>();
    private final Multimap<World, ICraftingJob> craftingJobs = LinkedListMultimap.create();
    private final WeakHashMap<World, Queue<IWorldCallable<?>>> callQueue = new WeakHashMap<>();
    private final HandlerRep server = new HandlerRep();
    private final HandlerRep client = new HandlerRep();
    private final HashMap<Integer, PlayerColor> cliPlayerColors = new HashMap<>();
    private final HashMap<Integer, PlayerColor> srvPlayerColors = new HashMap<>();
    private CableRenderMode crm = CableRenderMode.Standard;
    // must be a thread safe collection since this can be called from finalizer thread
    private final BlockingDeque<Integer> callListToDelete = new LinkedBlockingDeque<>();

    public HashMap<Integer, PlayerColor> getPlayerColors() {
        if (Platform.isServer()) {
            return this.srvPlayerColors;
        }
        return this.cliPlayerColors;
    }

    public void scheduleCallListDelete(Integer id) {
        callListToDelete.add(id);
    }

    public void addCallable(final World w, final IWorldCallable<?> c) {
        if (w == null) {
            this.serverQueue.add(c);
        } else {
            Queue<IWorldCallable<?>> queue = this.callQueue.computeIfAbsent(w, k -> new LinkedList<>());

            queue.add(c);
        }
    }

    public void addInit(final AEBaseTile tile) {
        if (Platform.isServer()) // for no there is no reason to care about this on the client...
        {
            this.getRepo().tiles.add(tile);
        }
    }

    private HandlerRep getRepo() {
        if (Platform.isServer()) {
            return this.server;
        }
        return this.client;
    }

    public void addNetwork(final Grid grid) {
        if (Platform.isServer()) // for no there is no reason to care about this on the client...
        {
            this.getRepo().networks.add(grid);
        }
    }

    public void removeNetwork(final Grid grid) {
        if (Platform.isServer()) // for no there is no reason to care about this on the client...
        {
            this.getRepo().networks.remove(grid);
        }
    }

    public Iterable<Grid> getGridList() {
        return this.getRepo().networks;
    }

    public void shutdown() {
        this.getRepo().clear();
    }

    @SubscribeEvent
    public void unloadWorld(final WorldEvent.Unload ev) {
        if (Platform.isServer()) // for no there is no reason to care about this on the client...
        {
            final LinkedList<IGridNode> toDestroy = new LinkedList<>();

            for (final Grid g : this.getRepo().networks) {
                for (final IGridNode n : g.getNodes()) {
                    if (n.getWorld() == ev.world) {
                        toDestroy.add(n);
                    }
                }
            }

            for (final IGridNode n : toDestroy) {
                n.destroy();
            }
        }
    }

    @SubscribeEvent
    public void onChunkLoad(final ChunkEvent.Load load) {
        for (final Object te : load.getChunk().chunkTileEntityMap.values()) {
            if (te instanceof AEBaseTile) {
                ((AEBaseTile) te).onChunkLoad();
            }
        }
    }

    @SubscribeEvent
    public void onTick(final TickEvent ev) {

        if (ev.type == Type.CLIENT && ev.phase == Phase.START) {
            this.tickColors(this.cliPlayerColors);
            this.deleteCallLists();
            EntityFloatingItem.ageStatic = (EntityFloatingItem.ageStatic + 1) % 60000;
            final CableRenderMode currentMode = AEApi.instance().partHelper().getCableRenderMode();
            if (currentMode != this.crm) {
                this.crm = currentMode;
                CommonHelper.proxy.triggerUpdates();
            }
        }

        if (ev.type == Type.WORLD && ev.phase == Phase.END) {
            final WorldTickEvent wte = (WorldTickEvent) ev;
            synchronized (this.craftingJobs) {
                final Collection<ICraftingJob> jobSet = this.craftingJobs.get(wte.world);
                if (!jobSet.isEmpty()) {
                    final int simTime = Math.max(1, AEConfig.instance.craftingCalculationTimePerTick / jobSet.size());
                    jobSet.removeIf(cj -> !cj.simulateFor(simTime));
                }
            }
        }

        // for no there is no reason to care about this on the client...
        else if (ev.type == Type.SERVER && ev.phase == Phase.END) {
            this.tickColors(this.srvPlayerColors);
            // ready tiles.
            final HandlerRep repo = this.getRepo();
            while (!repo.tiles.isEmpty()) {
                final AEBaseTile bt = repo.tiles.poll();
                if (!bt.isInvalid()) {
                    bt.onReady();
                }
            }

            // tick networks.
            for (final Grid g : this.getRepo().networks) {
                long startTime = System.nanoTime();
                g.update();
                g.pushUpdateTime(System.nanoTime() - startTime);
            }

            // cross world queue.
            this.processQueue(this.serverQueue, null);
        }

        // world synced queue(s)
        if (ev.type == Type.WORLD && ev.phase == Phase.START) {
            final World world = ((WorldTickEvent) ev).world;
            final Queue<IWorldCallable<?>> queue = this.callQueue.get(world);
            this.processQueue(queue, world);
        }
    }

    private void deleteCallLists() {
        // we have only one consumer, so this is safe
        // even if we missed some, we will delete them next tick.
        while (!callListToDelete.isEmpty()) GLAllocation.deleteDisplayLists(callListToDelete.remove());
    }

    private void tickColors(final HashMap<Integer, PlayerColor> playerSet) {
        final Iterator<PlayerColor> i = playerSet.values().iterator();
        while (i.hasNext()) {
            final PlayerColor pc = i.next();
            if (pc.ticksLeft <= 0) {
                i.remove();
            }
            pc.ticksLeft--;
        }
    }

    private void processQueue(final Queue<IWorldCallable<?>> queue, final World world) {
        if (queue == null) {
            return;
        }

        final Stopwatch sw = Stopwatch.createStarted();

        IWorldCallable<?> c = null;
        while ((c = queue.poll()) != null) {
            try {
                c.call(world);

                if (sw.elapsed(TimeUnit.MILLISECONDS) > 50) {
                    break;
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }

        // long time = sw.elapsed( TimeUnit.MILLISECONDS );
        // if ( time > 0 )
        // AELog.info( "processQueue Time: " + time + "ms" );
    }

    public void registerCraftingSimulation(final World world, final ICraftingJob craftingJob) {
        synchronized (this.craftingJobs) {
            this.craftingJobs.put(world, craftingJob);
        }
    }

    private static class HandlerRep {

        private Queue<AEBaseTile> tiles = new LinkedList<>();

        private Collection<Grid> networks = new NetworkList();

        private void clear() {
            this.tiles = new LinkedList<>();
            this.networks = new NetworkList();
        }
    }

    public static class PlayerColor {

        public final AEColor myColor;
        private final int myEntity;
        private int ticksLeft;

        public PlayerColor(final int id, final AEColor col, final int ticks) {
            this.myEntity = id;
            this.myColor = col;
            this.ticksLeft = ticks;
        }

        public PacketPaintedEntity getPacket() {
            return new PacketPaintedEntity(this.myEntity, this.myColor, this.ticksLeft);
        }
    }
}
