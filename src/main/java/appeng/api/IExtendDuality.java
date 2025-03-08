package appeng.api;

import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerUpgradeable;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

public interface IExtendDuality {
    default int getNumberOfStorageSlots(int a) {
        return a;
    }

    default int getNumberOfConfigSlots(int a) {
        return a;
    }

    default int getNumberOfPatternSlots(int a) {
        return a;
    }

    default int getNumblerUpgrades_() {
        return 4;
    }
    default void onRender(GuiInterface gi, ContainerInterface ci, Slot sl) {}
    default ResourceLocation renderDefaultBack(GuiUpgradeable gi, ContainerUpgradeable ci) {
        return null;
    }
}
