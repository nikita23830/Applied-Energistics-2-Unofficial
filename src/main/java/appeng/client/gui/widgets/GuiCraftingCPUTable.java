package appeng.client.gui.widgets;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.ContainerCPUTable;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.ReadableNumberConverter;

public class GuiCraftingCPUTable {

    private final AEBaseGui parent;
    private final ContainerCPUTable container;
    private final Predicate<CraftingCPUStatus> jobMergeable;

    public static final int CPU_TABLE_WIDTH = 94;
    public static int CPU_TABLE_HEIGHT = 164;
    public static int CPU_TABLE_SLOTS = 6;
    public static final int CPU_TABLE_SLOT_XOFF = 100;
    public static final int CPU_TABLE_SLOT_YOFF = 0;
    public static final int CPU_TABLE_SLOT_WIDTH = 67;
    public static final int CPU_TABLE_SLOT_HEIGHT = 23;

    private final GuiScrollbar cpuScrollbar;

    private String selectedCPUName = "";
    private static final DecimalFormat DF = new DecimalFormat("#.##");

    private static int processBarStartColorInt = GuiColors.ProcessBarStartColor.getColor();
    private static final int[] PROCESS_BAR_START_COLOR_INT_ARR = new int[] { (processBarStartColorInt >> 24) & 0xFF,
            (processBarStartColorInt >> 16) & 0xFF, (processBarStartColorInt >> 8) & 0xFF,
            processBarStartColorInt & 0xFF };

    private static int processBarMiddleColorInt = GuiColors.ProcessBarMiddleColor.getColor();
    private static final int[] PROCESS_BAR_MIDDLE_COLOR_INT_ARR = new int[] { (processBarMiddleColorInt >> 24) & 0xFF,
            (processBarMiddleColorInt >> 16) & 0xFF, (processBarMiddleColorInt >> 8) & 0xFF,
            processBarMiddleColorInt & 0xFF };

    private static int processBarEndColorInt = GuiColors.ProcessBarEndColor.getColor();
    private static final int[] PROCESS_BAR_END_COLOR_INT_ARR = new int[] { (processBarEndColorInt >> 24) & 0xFF,
            (processBarEndColorInt >> 16) & 0xFF, (processBarEndColorInt >> 8) & 0xFF, processBarEndColorInt & 0xFF };

    public GuiCraftingCPUTable(AEBaseGui parent, ContainerCPUTable container,
                               Predicate<CraftingCPUStatus> jobMergeable) {
        this.parent = parent;
        this.container = container;
        this.jobMergeable = jobMergeable;
        this.cpuScrollbar = new GuiScrollbar();
        this.cpuScrollbar.setLeft(-16);
        this.cpuScrollbar.setTop(19);
        this.cpuScrollbar.setWidth(12);
        updateScrollBar();
    }

    private void updateScrollBar() {
        this.cpuScrollbar.setHeight(CPU_TABLE_HEIGHT - 27);
    }

    public ContainerCPUTable getContainer() {
        return container;
    }

    public String getSelectedCPUName() {
        return selectedCPUName;
    }

    public void drawScreen() {
        final List<CraftingCPUStatus> cpus = container.getCPUs();
        final int selectedCpuSerial = container.selectedCpuSerial;

        this.selectedCPUName = null;
        this.cpuScrollbar.setRange(0, Integer.max(0, cpus.size() - CPU_TABLE_SLOTS), 1);
        for (CraftingCPUStatus cpu : cpus) {
            if (cpu.getSerial() == selectedCpuSerial) {
                this.selectedCPUName = cpu.getName();
            }
        }
    }

    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY, int guiLeft, int guiTop) {
        if (this.cpuScrollbar != null) {
            this.cpuScrollbar.draw(parent);
        }
        final List<CraftingCPUStatus> cpus = container.getCPUs();
        final int selectedCpuSerial = container.selectedCpuSerial;
        final int firstCpu = this.cpuScrollbar.getCurrentScroll();
        CraftingCPUStatus hoveredCpu = hitCpu(mouseX - guiLeft, mouseY - guiTop);
        final ItemStack craftingAccelerator = AEApi.instance().definitions().blocks().craftingAccelerator()
                .maybeStack(1).orNull();
        final ItemStack cell64k = AEApi.instance().definitions().items().cell64k().maybeStack(1).orNull();
        {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            for (int i = firstCpu; i < firstCpu + CPU_TABLE_SLOTS; i++) {
                if (i < 0 || i >= cpus.size()) {
                    continue;
                }
                CraftingCPUStatus cpu = cpus.get(i);
                if (cpu == null) {
                    continue;
                }
                int x = -CPU_TABLE_WIDTH + 9;
                int y = 19 + (i - firstCpu) * CPU_TABLE_SLOT_HEIGHT;
                if (cpu.getSerial() == selectedCpuSerial) {
                    if (!container.getCpuFilter().test(cpu)) {
                        GL11.glColor4f(1.0F, 0.25F, 0.25F, 1.0F);
                    } else if (jobMergeable.test(cpu)) {
                        GL11.glColor4f(1.0F, 1.0F, 0.25F, 1.0F);
                    } else {
                        GL11.glColor4f(0.0F, 0.8352F, 1.0F, 1.0F);
                    }
                } else if (hoveredCpu != null && hoveredCpu.getSerial() == cpu.getSerial()) {
                    GL11.glColor4f(0.65F, 0.9F, 1.0F, 1.0F);
                } else if (!container.getCpuFilter().test(cpu)) {
                    GL11.glColor4f(0.9F, 0.65F, 0.65F, 1.0F);
                } else if (jobMergeable.test(cpu)) {
                    GL11.glColor4f(1.0F, 1.0F, 0.7F, 1.0F);
                } else {
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
                parent.bindTexture("guis/cpu_selector.png");
                parent.drawTexturedModalRect(
                        x,
                        y,
                        CPU_TABLE_SLOT_XOFF,
                        CPU_TABLE_SLOT_YOFF,
                        CPU_TABLE_SLOT_WIDTH,
                        CPU_TABLE_SLOT_HEIGHT);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                String name = cpu.getName();
                if (name == null || name.isEmpty()) {
                    name = GuiText.CPUs.getLocal() + " #" + NumberFormat.getInstance().format((cpu.getSerial()));
                }
                if (name.length() > 12) {
                    name = name.substring(0, 11) + "..";
                }
                GL11.glPushMatrix();
                GL11.glTranslatef(x + 3, y + 3, 0);
                GL11.glScalef(0.8f, 0.8f, 1.0f);
                font.drawString(name, 0, 0, GuiColors.CraftingStatusCPUName.getColor());
                GL11.glPopMatrix();

                GL11.glPushMatrix();
                GL11.glTranslatef(x + 3, y + 11, 0);
                final IAEItemStack craftingStack = cpu.getCrafting();
                if (craftingStack != null) {
                    final int iconIndex = 16 * 11 + 2;
                    parent.bindTexture("guis/states.png");
                    final int uv_y = iconIndex / 16;
                    final int uv_x = iconIndex - uv_y * 16;

                    GL11.glScalef(0.5f, 0.5f, 1.0f);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    parent.drawTexturedModalRect(0, 0, uv_x * 16, uv_y * 16, 16, 16);
                    GL11.glTranslatef(18.0f, 2.0f, 0.0f);
                    String amount = NumberFormat.getInstance().format(craftingStack.getStackSize());
                    double craftingPercentage = (double) (cpu.getTotalItems() - Math.max(cpu.getRemainingItems(), 0))
                            / (double) cpu.getTotalItems();
                    if (amount.length() > 9) {
                        amount = ReadableNumberConverter.INSTANCE.toWideReadableForm(craftingStack.getStackSize());
                    }
                    GL11.glScalef(1.5f, 1.5f, 1.0f);
                    font.drawString(amount, 0, 0, GuiColors.CraftingStatusCPUAmount.getColor());

                    GL11.glPopMatrix();
                    GL11.glPushMatrix();
                    GL11.glTranslatef(x + CPU_TABLE_SLOT_WIDTH - 19, y + 3, 0);
                    parent.drawItem(0, 0, craftingStack.getItemStack());

                    GL11.glPopMatrix();
                    GL11.glPushMatrix();
                    AEBaseGui.drawRect(
                            x,
                            y + CPU_TABLE_SLOT_HEIGHT - 3,
                            x + (int) ((CPU_TABLE_SLOT_WIDTH - 1) * craftingPercentage),
                            y + CPU_TABLE_SLOT_HEIGHT - 2,
                            this.calculateGradientColor(craftingPercentage));
                } else {
                    parent.bindTexture("guis/states.png");

                    GL11.glScalef(0.5f, 0.5f, 1.0f);

                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    parent.drawItem(0, 0, cell64k);
                    switch (cpu.allowMode()) {
                        case ALLOW_ALL -> parent.drawTexturedModalRect(16 * 7 - 1, 0, 16 * 3, 16 * 14, 16, 16);
                        case ONLY_PLAYER -> parent.drawTexturedModalRect(16 * 7 - 1, 0, 16 * 4, 16 * 14, 16, 16);
                        case ONLY_NONPLAYER -> parent.drawTexturedModalRect(16 * 7 - 1, 0, 16 * 5, 16 * 14, 16, 16);
                    }
                    GL11.glColor4f(0.5F, 0.5F, 0.5F, 1.0F);

                    parent.drawItem(16 * 4, 0, craftingAccelerator);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                    GL11.glTranslatef(18.0f, 6.0f, 0.0f);
                    GL11.glScalef(1.1f, 1.1f, 1f);

                    font.drawString(cpu.formatStorage(), 0, 0, GuiColors.CraftingStatusCPUStorage.getColor());
                    font.drawString(
                            cpu.formatShorterCoprocessors(),
                            16 * 4 - 5,
                            0,
                            GuiColors.CraftingStatusCPUStorage.getColor());

                }
                GL11.glPopMatrix();
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (hoveredCpu != null) {
            StringBuilder tooltip = new StringBuilder();
            String name = hoveredCpu.getName();
            if (name != null && !name.isEmpty()) {
                tooltip.append(name);
                tooltip.append('\n');
            } else {
                tooltip.append(GuiText.CPUs.getLocal());
                tooltip.append(" #");
                tooltip.append(NumberFormat.getInstance().format(hoveredCpu.getSerial()));
                tooltip.append('\n');
            }
            IAEItemStack crafting = hoveredCpu.getCrafting();
            if (crafting != null && crafting.getStackSize() > 0) {
                final long elapsedInMilliseconds = TimeUnit.MILLISECONDS
                        .convert(hoveredCpu.getCraftingElapsedTime(), TimeUnit.NANOSECONDS);
                final String elapsedTimeText = DurationFormatUtils
                        .formatDuration(elapsedInMilliseconds, GuiText.ETAFormat.getLocal());

                double craftingPercentage = 100
                        - (double) (hoveredCpu.getRemainingItems()) / (double) hoveredCpu.getTotalItems() * 100;
                tooltip.append(GuiText.Crafting.getLocal());
                tooltip.append(": ");
                tooltip.append(NumberFormat.getInstance().format(crafting.getStackSize()));
                tooltip.append(' ');
                tooltip.append(crafting.getItemStack().getDisplayName());
                tooltip.append('\n');
                tooltip.append(NumberFormat.getInstance().format(hoveredCpu.getRemainingItems()));
                tooltip.append(" / ");
                tooltip.append(NumberFormat.getInstance().format(hoveredCpu.getTotalItems()));
                tooltip.append(String.format(" %02.2f %%", craftingPercentage));
                tooltip.append('\n');

                tooltip.append(GuiText.TimeUsed.getLocal());
                tooltip.append(": ");
                tooltip.append(elapsedTimeText);
                tooltip.append('\n');
            }

            if (hoveredCpu.getUsedStorage() > 0) {
                tooltip.append(GuiText.BytesUsed.getLocal());
                tooltip.append(": ");
                tooltip.append(hoveredCpu.formatUsedStorage());
                tooltip.append(" / ");
                tooltip.append(hoveredCpu.formatStorage());
                tooltip.append('\n');
            } else if (hoveredCpu.getStorage() > 0) {
                tooltip.append(GuiText.Bytes.getLocal());
                tooltip.append(": ");
                tooltip.append(hoveredCpu.formatStorage());
                tooltip.append('\n');
            }
            if (hoveredCpu.getCoprocessors() > 0) {
                tooltip.append(GuiText.CoProcessors.getLocal());
                tooltip.append(": ");
                tooltip.append(hoveredCpu.formatCoprocessors());
                tooltip.append('\n');
            }
            if (tooltip.length() > 0) {
                parent.drawTooltip(mouseX - offsetX, mouseY - offsetY, tooltip.toString());
            }
        }
    }

    public void drawBG(int offsetX, int offsetY) {
        parent.bindTexture("guis/cpu_selector.png");
        if (CPU_TABLE_HEIGHT != 164) {
            parent.drawTexturedModalRect(offsetX - CPU_TABLE_WIDTH, offsetY, 0, 0, CPU_TABLE_WIDTH, 41);
            int y = 41;
            int rows = CPU_TABLE_SLOTS;
            for (int row = 1; row < rows - 1; row++) {
                parent.drawTexturedModalRect(offsetX - CPU_TABLE_WIDTH, offsetY + y, 0, 41, CPU_TABLE_WIDTH, 23);
                y += 23;
            }
            parent.drawTexturedModalRect(offsetX - CPU_TABLE_WIDTH, offsetY + y, 0, 133, CPU_TABLE_WIDTH, 31);
        } else {
            parent.drawTexturedModalRect(offsetX - CPU_TABLE_WIDTH, offsetY, 0, 0, CPU_TABLE_WIDTH, CPU_TABLE_HEIGHT);
        }
        updateScrollBar();
    }

    /**
     * Tests if a cpu button is under the cursor. Subtract guiLeft, guiTop from x, y before calling
     */
    public CraftingCPUStatus hitCpu(int x, int y) {
        x -= -CPU_TABLE_WIDTH;
        if (!(x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9 && y >= 19 && y < 19 + CPU_TABLE_SLOTS * CPU_TABLE_SLOT_HEIGHT)) {
            return null;
        }
        int scrollOffset = this.cpuScrollbar != null ? this.cpuScrollbar.getCurrentScroll() : 0;
        int cpuId = scrollOffset + (y - 19) / CPU_TABLE_SLOT_HEIGHT;
        List<CraftingCPUStatus> cpus = container.getCPUs();
        return (cpuId >= 0 && cpuId < cpus.size()) ? cpus.get(cpuId) : null;
    }

    /**
     * Subtract guiLeft, guiTop from x, y before calling
     */
    public void mouseClicked(int xCoord, int yCoord, int btn) {
        if (cpuScrollbar != null) {
            cpuScrollbar.click(parent, xCoord, yCoord);
        }
        CraftingCPUStatus hit = hitCpu(xCoord, yCoord);
        if (hit != null) {
            sendCPUSwitch(hit.getSerial());
        }
    }

    public void sendCPUSwitch(int serial) {
        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig("CPUTable.Cpu.Set", Integer.toString(serial)));
        } catch (final IOException e) {
            AELog.warn(e);
        }
    }

    /**
     * Subtract guiLeft, guiTop from x, y before calling
     */
    public void mouseClickMove(int xCoord, int yCoord) {
        if (cpuScrollbar != null) {
            cpuScrollbar.clickMove(yCoord);
        }
    }

    /**
     * @return True if event was handled
     */
    public boolean handleMouseInput(int guiLeft, int guiTop) {
        int x = Mouse.getEventX() * parent.width / parent.mc.displayWidth;
        int y = parent.height - Mouse.getEventY() * parent.height / parent.mc.displayHeight - 1;
        x -= guiLeft - CPU_TABLE_WIDTH;
        y -= guiTop;
        int dwheel = Mouse.getEventDWheel();
        if (x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9 && y >= 19 && y < 19 + CPU_TABLE_SLOTS * CPU_TABLE_SLOT_HEIGHT) {
            if (this.cpuScrollbar != null && dwheel != 0) {
                this.cpuScrollbar.wheel(dwheel);
                return true;
            }
        }
        return false;
    }

    public boolean hideItemPanelSlot(int x, int y, int w, int h) {
        x += CPU_TABLE_WIDTH;
        return x + w >= 0 && x <= CPU_TABLE_WIDTH && y + h >= 0 && y <= CPU_TABLE_HEIGHT;
    }

    public void cycleCPU(boolean backwards) {
        int current = container.selectedCpuSerial;
        List<CraftingCPUStatus> cpus = container.getCPUs();
        final int next_increment = backwards ? (cpus.size() - 1) : 1;
        if (cpus.isEmpty()) {
            return;
        }
        int next = 0;
        for (int i = 0; i < cpus.size(); i++) {
            if (cpus.get(i).getSerial() == current) {
                next = i + next_increment;
                break;
            }
        }
        final boolean preferBusy = container.isBusyCPUsPreferred();
        for (int i = 0; i < cpus.size(); i++) {
            next = next % cpus.size();
            CraftingCPUStatus cpu = cpus.get(next);
            if (preferBusy) {
                // If viewing crafting status, pick next busy CPU (subject to filter)
                if (cpu.isBusy() && container.getCpuFilter().test(cpu)) {
                    break;
                }
            } else if (container.getCpuFilter().test(cpu)) {
                // If viewing crafting confirmation, pick next compatible CPU
                break;
            } else {
                next += next_increment;
            }
        }
        next = next % cpus.size();
        sendCPUSwitch(cpus.get(next).getSerial());
        if (next < cpuScrollbar.getCurrentScroll() || next >= cpuScrollbar.getCurrentScroll() + CPU_TABLE_SLOTS) {
            cpuScrollbar.setCurrentScroll(next);
        }
    }

    private int calculateGradientColor(double percentage) {
        int start[] = null;
        int end[] = null;
        double ratio = 0;
        if (percentage <= 0.5) {
            start = PROCESS_BAR_START_COLOR_INT_ARR;
            end = PROCESS_BAR_MIDDLE_COLOR_INT_ARR;
            ratio = percentage * 2;
        } else {
            start = PROCESS_BAR_MIDDLE_COLOR_INT_ARR;
            end = PROCESS_BAR_END_COLOR_INT_ARR;
            ratio = (percentage - 0.5d) * 2;
        }
        int a = (int) (start[0] + ratio * (end[0] - start[0]));
        int r = (int) (start[1] + ratio * (end[1] - start[1]));
        int g = (int) (start[2] + ratio * (end[2] - start[2]));
        int b = (int) (start[3] + ratio * (end[3] - start[3]));
        return (a << 24) | (r << 16) | (g << 8) | (b);
    }
}