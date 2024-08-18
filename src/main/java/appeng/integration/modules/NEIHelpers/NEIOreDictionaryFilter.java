package appeng.integration.modules.NEIHelpers;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiOreFilter;
import appeng.util.prioitylist.OreFilteredList;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemFilter.ItemFilterProvider;

public class NEIOreDictionaryFilter implements ItemFilterProvider {

    @Override
    public ItemFilter getFilter() {
        GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;

        if (currentScreen instanceof GuiOreFilter) {
            GuiOreFilter oreScreen = (GuiOreFilter) currentScreen;

            if (oreScreen.useNEIFilter() && !oreScreen.getText().isEmpty()) {
                return new Filter(oreScreen.getText());
            }
        }

        return null;
    }

    public static class Filter implements ItemFilter {

        private Predicate<IAEItemStack> list;

        public Filter(String pattern) {
            this.list = OreFilteredList.makeFilter(pattern);
        }

        @Override
        public boolean matches(ItemStack itemStack) {
            return this.list == null || list.test(AEApi.instance().storage().createItemStack(itemStack));
        }
    }
}
