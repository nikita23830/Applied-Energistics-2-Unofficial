package appeng.api.events;

import appeng.client.gui.AEBaseGui;
import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

@Cancelable
public class EventFinishCraft extends Event {

    public final EntityPlayer player;
    public final ItemStack stack;

    public EventFinishCraft(EntityPlayer player, ItemStack stack) {
        this.player = player;
        this.stack = stack;
    }


}
