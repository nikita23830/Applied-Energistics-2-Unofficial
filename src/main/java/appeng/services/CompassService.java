/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.services;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.google.common.base.Preconditions;

import appeng.api.AEApi;
import appeng.api.util.DimensionalCoord;
import appeng.services.compass.CompassReader;
import appeng.services.compass.ICompassCallback;
import appeng.util.Platform;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class CompassService {

    private static final int CHUNK_SIZE = 16;
    private static final int CLEANUP_TIMEOUT_IN_SECONDS = 60;

    private final Map<World, AutoClosingCompassReader> worldSet = new HashMap<>(10);
    private final ScheduledExecutorService executor;
    /**
     * AE2 Folder for each world
     */
    private final File worldCompassFolder;

    public CompassService(@Nonnull final File worldCompassFolder, @Nonnull final ThreadFactory factory) {
        Preconditions.checkNotNull(worldCompassFolder);

        this.worldCompassFolder = worldCompassFolder;
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public Future<?> getCompassDirection(final DimensionalCoord coord, final int maxRange, final ICompassCallback cc) {
        return this.executor.submit(new CMDirectionRequest(coord, maxRange, cc));
    }

    /**
     * Ensure the a compass service is removed once a world gets unloaded by forge.
     *
     * @param event the event containing the unloaded world.
     */
    @SubscribeEvent
    public void unloadWorld(final WorldEvent.Unload event) {
        if (Platform.isServer() && this.worldSet.containsKey(event.world)) {
            final AutoClosingCompassReader compassReader = this.worldSet.remove(event.world);

            compassReader.close();
        }
    }

    private void cleanUp() {
        for (final AutoClosingCompassReader cr : this.worldSet.values()) {
            cr.close();
        }
    }

    public void updateArea(final World w, final int chunkX, final int chunkZ) {
        final int x = chunkX << 4;
        final int z = chunkZ << 4;

        this.updateArea(w, x, CHUNK_SIZE, z);
        this.updateArea(w, x, CHUNK_SIZE + 32, z);
        this.updateArea(w, x, CHUNK_SIZE + 64, z);
        this.updateArea(w, x, CHUNK_SIZE + 96, z);

        this.updateArea(w, x, CHUNK_SIZE + 128, z);
        this.updateArea(w, x, CHUNK_SIZE + 160, z);
        this.updateArea(w, x, CHUNK_SIZE + 192, z);
        this.updateArea(w, x, CHUNK_SIZE + 224, z);
    }

    public Future<?> updateArea(final World w, final int x, final int y, final int z) {
        final int cx = x >> 4;
        final int cdy = y >> 5;
        final int cz = z >> 4;

        final int low_y = cdy << 5;
        final int hi_y = low_y + 32;

        // lower level...
        final Chunk c = w.getChunkFromBlockCoords(x, z);

        for (final Block skyStoneBlock : AEApi.instance().definitions().blocks().skyStone().maybeBlock().asSet()) {
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < CHUNK_SIZE; j++) {
                    for (int k = low_y; k < hi_y; k++) {
                        final Block blk = c.getBlock(i, k, j);
                        if (blk == skyStoneBlock && c.getBlockMetadata(i, k, j) == 0) {
                            return this.executor.submit(new CMUpdatePost(w, cx, cz, cdy, true));
                        }
                    }
                }
            }
        }

        return this.executor.submit(new CMUpdatePost(w, cx, cz, cdy, false));
    }

    public void kill() {
        for (final AutoClosingCompassReader cr : this.worldSet.values()) {
            cr.close();
        }

        this.worldSet.clear();
        this.executor.shutdownNow();
//        this.executor.shutdown();
//
//        try {
//            this.executor.awaitTermination(6, TimeUnit.MINUTES);
//
//            for (final AutoClosingCompassReader cr : this.worldSet.values()) {
//                cr.close();
//            }
//
//            this.worldSet.clear();
//        } catch (final InterruptedException e) {
//            this.executor.shutdownNow();
//            // wrap this up..
//        }
    }

    private CompassReader getReader(final World w) {
        AutoClosingCompassReader cr = this.worldSet.get(w);

        if (cr == null) {
            CompassReader reader = new CompassReader(w.provider.dimensionId, this.worldCompassFolder);
            cr = new AutoClosingCompassReader(reader, this.executor);
            this.worldSet.put(w, cr);
        }

        return cr.get();
    }

    private int dist(final int ax, final int az, final int bx, final int bz) {
        final int up = (bz - az) * CHUNK_SIZE;
        final int side = (bx - ax) * CHUNK_SIZE;

        return up * up + side * side;
    }

    private double rad(final int ax, final int az, final int bx, final int bz) {
        final int up = bz - az;
        final int side = bx - ax;

        return Math.atan2(-up, side) - Math.PI / 2.0;
    }

    private class CMUpdatePost implements Runnable {

        public final World world;

        public final int chunkX;
        public final int chunkZ;
        public final int doubleChunkY; // 32 blocks instead of 16.
        public final boolean value;

        public CMUpdatePost(final World w, final int cx, final int cz, final int dcy, final boolean val) {
            this.world = w;
            this.chunkX = cx;
            this.doubleChunkY = dcy;
            this.chunkZ = cz;
            this.value = val;
        }

        @Override
        public void run() {
            final CompassReader cr = CompassService.this.getReader(this.world);
            cr.setHasBeacon(this.chunkX, this.chunkZ, this.doubleChunkY, this.value);
        }
    }

    private class CMDirectionRequest implements Runnable {

        public final int maxRange;
        public final DimensionalCoord coord;
        public final ICompassCallback callback;

        public CMDirectionRequest(final DimensionalCoord coord, final int getMaxRange, final ICompassCallback cc) {
            this.coord = coord;
            this.maxRange = getMaxRange;
            this.callback = cc;
        }

        @Override
        public void run() {
            final int cx = this.coord.x >> 4;
            final int cz = this.coord.z >> 4;

            final CompassReader cr = CompassService.this.getReader(this.coord.getWorld());

            // Am I standing on it?
            if (cr.hasBeacon(cx, cz)) {
                this.callback.calculatedDirection(true, true, -999, 0);
                return;
            }

            // spiral outward...
            for (int offset = 1; offset < this.maxRange; offset++) {
                final int minX = cx - offset;
                final int minZ = cz - offset;
                final int maxX = cx + offset;
                final int maxZ = cz + offset;

                int closest = Integer.MAX_VALUE;
                int chosen_x = cx;
                int chosen_z = cz;

                for (int z = minZ; z <= maxZ; z++) {
                    if (cr.hasBeacon(minX, z)) {
                        final int closeness = CompassService.this.dist(cx, cz, minX, z);
                        if (closeness < closest) {
                            closest = closeness;
                            chosen_x = minX;
                            chosen_z = z;
                        }
                    }

                    if (cr.hasBeacon(maxX, z)) {
                        final int closeness = CompassService.this.dist(cx, cz, maxX, z);
                        if (closeness < closest) {
                            closest = closeness;
                            chosen_x = maxX;
                            chosen_z = z;
                        }
                    }
                }

                for (int x = minX + 1; x < maxX; x++) {
                    if (cr.hasBeacon(x, minZ)) {
                        final int closeness = CompassService.this.dist(cx, cz, x, minZ);
                        if (closeness < closest) {
                            closest = closeness;
                            chosen_x = x;
                            chosen_z = minZ;
                        }
                    }

                    if (cr.hasBeacon(x, maxZ)) {
                        final int closeness = CompassService.this.dist(cx, cz, x, maxZ);
                        if (closeness < closest) {
                            closest = closeness;
                            chosen_x = x;
                            chosen_z = maxZ;
                        }
                    }
                }

                if (closest < Integer.MAX_VALUE) {
                    this.callback.calculatedDirection(
                            true,
                            false,
                            CompassService.this.rad(cx, cz, chosen_x, chosen_z),
                            CompassService.this.dist(cx, cz, chosen_x, chosen_z));
                    return;
                }
            }

            // didn't find shit...
            this.callback.calculatedDirection(false, true, -999, 999);
        }
    }

    /**
     * A small helper class that wraps a {@link CompassReader}. <br />
     * Its job is to close the associated {@link CompassReader} once it wasn't retrieved for
     * {@link CompassService#CLEANUP_TIMEOUT_IN_SECONDS} seconds. <br />
     * Callers must not cache the result of {@link AutoClosingCompassReader#get}, as that would defeat the purpose of
     * this class.
     */
    private static class AutoClosingCompassReader {

        private final CompassReader cr;
        private final ScheduledExecutorService executorService;

        private final Runnable closeTask = new Runnable() {

            @Override
            public void run() {
                cr.close();
            }
        };
        private ScheduledFuture<?> scheduledCloseTask = null;

        public AutoClosingCompassReader(CompassReader cr, ScheduledExecutorService executorService) {
            this.cr = cr;
            this.executorService = executorService;
        }

        public CompassReader get() {
            if (scheduledCloseTask != null) {
                scheduledCloseTask.cancel(/* interruptIfRunning */ false);
            }
            scheduledCloseTask = executorService.schedule(closeTask, CLEANUP_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            return cr;
        }

        public void close() {
            if (scheduledCloseTask != null) {
                scheduledCloseTask.cancel(/* interruptIfRunning */ false);
            }
            cr.close();
        }
    }
}
