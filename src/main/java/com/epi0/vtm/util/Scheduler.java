package com.epi0.vtm.util;

import com.epi0.vtm.VtmMod;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs a task a number of server ticks from now. Tasks are dropped when the server stops. */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class Scheduler {
    private record Task(long dueTick, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    public static void later(MinecraftServer server, int ticks, Runnable action) {
        TASKS.add(new Task(server.getTickCount() + ticks, action));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        if (TASKS.isEmpty()) {
            return;
        }
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.dueTick <= now) {
                due.add(task.action);
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }

    private Scheduler() {}
}
