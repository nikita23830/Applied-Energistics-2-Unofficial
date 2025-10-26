/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.widgets;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import appeng.api.config.CraftingAllow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.CellType;
import appeng.api.config.CondenserOutput;
import appeng.api.config.CraftingMode;
import appeng.api.config.CraftingSortOrder;
import appeng.api.config.CraftingStatus;
import appeng.api.config.FullnessMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.LevelType;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.OperationMode;
import appeng.api.config.PatternBeSubstitution;
import appeng.api.config.PatternSlotConfig;
import appeng.api.config.PowerUnits;
import appeng.api.config.PriorityCardMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.RelativeDirection;
import appeng.api.config.SchedulingMode;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SidelessMode;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.StorageFilter;
import appeng.api.config.StringOrder;
import appeng.api.config.TerminalStyle;
import appeng.api.config.TypeFilter;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.util.Platform;

public class GuiImgButton extends GuiButton implements ITooltip {

    private static final Pattern COMPILE = Pattern.compile("%s");
    private static final Pattern PATTERN_NEW_LINE = Pattern.compile("\\n", Pattern.LITERAL);
    private static Map<EnumPair, ButtonAppearance> appearances;
    private final Enum buttonSetting;
    private boolean halfSize = false;
    private String fillVar;
    private Enum currentValue;

    public GuiImgButton(final int x, final int y, final Enum idx, final Enum val) {
        super(0, 0, 16, "");

        this.buttonSetting = idx;
        this.currentValue = val;
        this.xPosition = x;
        this.yPosition = y;
        this.width = 16;
        this.height = 16;

        if (appearances == null) {
            appearances = new HashMap<>();
            this.registerApp(
                    16 * 7,
                    Settings.CONDENSER_OUTPUT,
                    CondenserOutput.TRASH,
                    ButtonToolTips.CondenserOutput,
                    ButtonToolTips.Trash);
            this.registerApp(
                    16 * 7 + 1,
                    Settings.CONDENSER_OUTPUT,
                    CondenserOutput.MATTER_BALLS,
                    ButtonToolTips.CondenserOutput,
                    ButtonToolTips.MatterBalls);
            this.registerApp(
                    16 * 7 + 2,
                    Settings.CONDENSER_OUTPUT,
                    CondenserOutput.SINGULARITY,
                    ButtonToolTips.CondenserOutput,
                    ButtonToolTips.Singularity);

            this.registerApp(
                    16 * 9 + 1,
                    Settings.ACCESS,
                    AccessRestriction.READ,
                    ButtonToolTips.IOMode,
                    ButtonToolTips.Read);
            this.registerApp(
                    16 * 9,
                    Settings.ACCESS,
                    AccessRestriction.WRITE,
                    ButtonToolTips.IOMode,
                    ButtonToolTips.Write);
            this.registerApp(
                    16 * 9 + 2,
                    Settings.ACCESS,
                    AccessRestriction.READ_WRITE,
                    ButtonToolTips.IOMode,
                    ButtonToolTips.ReadWrite);

            this.registerApp(
                    16 * 10,
                    Settings.POWER_UNITS,
                    PowerUnits.AE,
                    ButtonToolTips.PowerUnits,
                    PowerUnits.AE.unlocalizedName);
            this.registerApp(
                    16 * 10 + 1,
                    Settings.POWER_UNITS,
                    PowerUnits.EU,
                    ButtonToolTips.PowerUnits,
                    PowerUnits.EU.unlocalizedName);
            this.registerApp(
                    16 * 10 + 2,
                    Settings.POWER_UNITS,
                    PowerUnits.MK,
                    ButtonToolTips.PowerUnits,
                    PowerUnits.MK.unlocalizedName);
            this.registerApp(
                    16 * 10 + 3,
                    Settings.POWER_UNITS,
                    PowerUnits.WA,
                    ButtonToolTips.PowerUnits,
                    PowerUnits.WA.unlocalizedName);
            this.registerApp(
                    16 * 10 + 4,
                    Settings.POWER_UNITS,
                    PowerUnits.RF,
                    ButtonToolTips.PowerUnits,
                    PowerUnits.RF.unlocalizedName);

            this.registerApp(
                    3,
                    Settings.REDSTONE_CONTROLLED,
                    RedstoneMode.IGNORE,
                    ButtonToolTips.RedstoneMode,
                    ButtonToolTips.AlwaysActive);
            this.registerApp(
                    0,
                    Settings.REDSTONE_CONTROLLED,
                    RedstoneMode.LOW_SIGNAL,
                    ButtonToolTips.RedstoneMode,
                    ButtonToolTips.ActiveWithoutSignal);
            this.registerApp(
                    1,
                    Settings.REDSTONE_CONTROLLED,
                    RedstoneMode.HIGH_SIGNAL,
                    ButtonToolTips.RedstoneMode,
                    ButtonToolTips.ActiveWithSignal);
            this.registerApp(
                    2,
                    Settings.REDSTONE_CONTROLLED,
                    RedstoneMode.SIGNAL_PULSE,
                    ButtonToolTips.RedstoneMode,
                    ButtonToolTips.ActiveOnPulse);

            this.registerApp(
                    0,
                    Settings.REDSTONE_EMITTER,
                    RedstoneMode.LOW_SIGNAL,
                    ButtonToolTips.RedstoneMode,
                    ButtonToolTips.EmitLevelsBelow);
            this.registerApp(
                    1,
                    Settings.REDSTONE_EMITTER,
                    RedstoneMode.HIGH_SIGNAL,
                    ButtonToolTips.RedstoneMode,
                    ButtonToolTips.EmitLevelAbove);

            this.registerApp(
                    51,
                    Settings.OPERATION_MODE,
                    OperationMode.FILL,
                    ButtonToolTips.TransferDirection,
                    ButtonToolTips.TransferToStorageCell);
            this.registerApp(
                    50,
                    Settings.OPERATION_MODE,
                    OperationMode.EMPTY,
                    ButtonToolTips.TransferDirection,
                    ButtonToolTips.TransferToNetwork);

            this.registerApp(
                    51,
                    Settings.IO_DIRECTION,
                    RelativeDirection.LEFT,
                    ButtonToolTips.TransferDirection,
                    ButtonToolTips.TransferToStorageCell);
            this.registerApp(
                    50,
                    Settings.IO_DIRECTION,
                    RelativeDirection.RIGHT,
                    ButtonToolTips.TransferDirection,
                    ButtonToolTips.TransferToNetwork);

            this.registerApp(
                    48,
                    Settings.SORT_DIRECTION,
                    SortDir.ASCENDING,
                    ButtonToolTips.SortOrder,
                    ButtonToolTips.ToggleSortDirection);
            this.registerApp(
                    49,
                    Settings.SORT_DIRECTION,
                    SortDir.DESCENDING,
                    ButtonToolTips.SortOrder,
                    ButtonToolTips.ToggleSortDirection);

            this.registerApp(
                    16 * 2 + 3,
                    Settings.SEARCH_MODE,
                    SearchBoxMode.AUTOSEARCH,
                    ButtonToolTips.SearchMode,
                    ButtonToolTips.SearchMode_Auto);
            this.registerApp(
                    16 * 2 + 4,
                    Settings.SEARCH_MODE,
                    SearchBoxMode.MANUAL_SEARCH,
                    ButtonToolTips.SearchMode,
                    ButtonToolTips.SearchMode_Standard);
            this.registerApp(
                    16 * 2 + 5,
                    Settings.SEARCH_MODE,
                    SearchBoxMode.NEI_AUTOSEARCH,
                    ButtonToolTips.SearchMode,
                    ButtonToolTips.SearchMode_NEIAuto);
            this.registerApp(
                    16 * 2 + 6,
                    Settings.SEARCH_MODE,
                    SearchBoxMode.NEI_MANUAL_SEARCH,
                    ButtonToolTips.SearchMode,
                    ButtonToolTips.SearchMode_NEIStandard);

            this.registerApp(
                    16 * 2 + 7,
                    Settings.SAVE_SEARCH,
                    YesNo.YES,
                    ButtonToolTips.SaveSearchString,
                    ButtonToolTips.SaveSearchStringYes);
            this.registerApp(
                    16 * 2 + 8,
                    Settings.SAVE_SEARCH,
                    YesNo.NO,
                    ButtonToolTips.SaveSearchString,
                    ButtonToolTips.SaveSearchStringNo);

            this.registerApp(
                    16 * 5 + 3,
                    Settings.LEVEL_TYPE,
                    LevelType.ENERGY_LEVEL,
                    ButtonToolTips.LevelType,
                    ButtonToolTips.LevelType_Energy);
            this.registerApp(
                    16 * 4 + 3,
                    Settings.LEVEL_TYPE,
                    LevelType.ITEM_LEVEL,
                    ButtonToolTips.LevelType,
                    ButtonToolTips.LevelType_Item);

            this.registerApp(
                    16 * 13,
                    Settings.TERMINAL_STYLE,
                    TerminalStyle.TALL,
                    ButtonToolTips.TerminalStyle,
                    ButtonToolTips.TerminalStyle_Tall);
            this.registerApp(
                    16 * 13 + 1,
                    Settings.TERMINAL_STYLE,
                    TerminalStyle.SMALL,
                    ButtonToolTips.TerminalStyle,
                    ButtonToolTips.TerminalStyle_Small);
            this.registerApp(
                    16 * 13 + 2,
                    Settings.TERMINAL_STYLE,
                    TerminalStyle.FULL,
                    ButtonToolTips.TerminalStyle,
                    ButtonToolTips.TerminalStyle_Full);
            this.registerApp(
                    16 * 13 + 6,
                    Settings.HIDE_STORED,
                    YesNo.NO,
                    ButtonToolTips.HideStored,
                    ButtonToolTips.Disabled);
            this.registerApp(
                    16 * 13 + 7,
                    Settings.HIDE_STORED,
                    YesNo.YES,
                    ButtonToolTips.HideStored,
                    ButtonToolTips.Enable);

            this.registerApp(64, Settings.SORT_BY, SortOrder.NAME, ButtonToolTips.SortBy, ButtonToolTips.ItemName);
            this.registerApp(
                    65,
                    Settings.SORT_BY,
                    SortOrder.AMOUNT,
                    ButtonToolTips.SortBy,
                    ButtonToolTips.NumberOfItems);
            this.registerApp(
                    68,
                    Settings.SORT_BY,
                    SortOrder.INVTWEAKS,
                    ButtonToolTips.SortBy,
                    ButtonToolTips.InventoryTweaks);
            this.registerApp(69, Settings.SORT_BY, SortOrder.MOD, ButtonToolTips.SortBy, ButtonToolTips.Mod);

            this.registerApp(
                    64,
                    Settings.CRAFTING_SORT_BY,
                    CraftingSortOrder.NAME,
                    ButtonToolTips.SortBy,
                    ButtonToolTips.ItemName);
            this.registerApp(
                    65,
                    Settings.CRAFTING_SORT_BY,
                    CraftingSortOrder.AMOUNT,
                    ButtonToolTips.SortBy,
                    ButtonToolTips.NumberOfItems);
            this.registerApp(
                    18,
                    Settings.CRAFTING_SORT_BY,
                    CraftingSortOrder.CRAFTS,
                    ButtonToolTips.SortBy,
                    GuiText.ToCraftRequests.getUnlocalized());

            this.registerApp(
                    69,
                    Settings.CRAFTING_SORT_BY,
                    CraftingSortOrder.MOD,
                    ButtonToolTips.SortBy,
                    ButtonToolTips.Mod);

            this.registerApp(
                    74,
                    Settings.CRAFTING_SORT_BY,
                    CraftingSortOrder.PERCENT,
                    ButtonToolTips.SortBy,
                    ButtonToolTips.UsedPercent);

            this.registerApp(
                    66,
                    Settings.ACTIONS,
                    ActionItems.WRENCH,
                    ButtonToolTips.PartitionStorage,
                    ButtonToolTips.PartitionStorageHint);
            this.registerApp(
                    6,
                    Settings.ACTIONS,
                    ActionItems.CLOSE,
                    ButtonToolTips.Clear,
                    ButtonToolTips.ClearSettings);
            this.registerApp(6, Settings.ACTIONS, ActionItems.STASH, ButtonToolTips.Stash, ButtonToolTips.StashDesc);

            this.registerApp(
                    8,
                    Settings.ACTIONS,
                    ActionItems.ENCODE,
                    ButtonToolTips.Encode,
                    ButtonToolTips.EncodeDescription);
            this.registerApp(
                    4 + 3 * 16,
                    Settings.ACTIONS,
                    ItemSubstitution.ENABLED,
                    ButtonToolTips.Substitutions,
                    ButtonToolTips.SubstitutionsDescEnabled);
            this.registerApp(
                    7 + 3 * 16,
                    Settings.ACTIONS,
                    ItemSubstitution.DISABLED,
                    ButtonToolTips.Substitutions,
                    ButtonToolTips.SubstitutionsDescDisabled);
            this.registerApp(
                    2 + 1 * 16,
                    Settings.ACTIONS,
                    PatternBeSubstitution.ENABLED,
                    ButtonToolTips.BeSubstitutions,
                    ButtonToolTips.BeSubstitutionsDescEnabled);
            this.registerApp(
                    5 + 5 * 16,
                    Settings.ACTIONS,
                    PatternBeSubstitution.DISABLED,
                    ButtonToolTips.BeSubstitutions,
                    ButtonToolTips.BeSubstitutionsDescDisabled);

            this.registerApp(
                    8 + 16,
                    Settings.ACTIONS,
                    PatternSlotConfig.C_16_4,
                    ButtonToolTips.PatternSlotConfigTitle,
                    ButtonToolTips.PatternSlotConfigInfo);
            this.registerApp(
                    8 + 16,
                    Settings.ACTIONS,
                    PatternSlotConfig.C_4_16,
                    ButtonToolTips.PatternSlotConfigTitle,
                    ButtonToolTips.PatternSlotConfigInfo);

            this.registerApp(16, Settings.VIEW_MODE, ViewItems.STORED, ButtonToolTips.View, ButtonToolTips.StoredItems);
            this.registerApp(
                    18,
                    Settings.VIEW_MODE,
                    ViewItems.ALL,
                    ButtonToolTips.View,
                    ButtonToolTips.StoredCraftable);
            this.registerApp(
                    19,
                    Settings.VIEW_MODE,
                    ViewItems.CRAFTABLE,
                    ButtonToolTips.View,
                    ButtonToolTips.Craftable);

            this.registerApp(
                    16 * 6,
                    Settings.FUZZY_MODE,
                    FuzzyMode.PERCENT_25,
                    ButtonToolTips.FuzzyMode,
                    ButtonToolTips.FZPercent_25);
            this.registerApp(
                    16 * 6 + 1,
                    Settings.FUZZY_MODE,
                    FuzzyMode.PERCENT_50,
                    ButtonToolTips.FuzzyMode,
                    ButtonToolTips.FZPercent_50);
            this.registerApp(
                    16 * 6 + 2,
                    Settings.FUZZY_MODE,
                    FuzzyMode.PERCENT_75,
                    ButtonToolTips.FuzzyMode,
                    ButtonToolTips.FZPercent_75);
            this.registerApp(
                    16 * 6 + 3,
                    Settings.FUZZY_MODE,
                    FuzzyMode.PERCENT_99,
                    ButtonToolTips.FuzzyMode,
                    ButtonToolTips.FZPercent_99);
            this.registerApp(
                    16 * 6 + 4,
                    Settings.FUZZY_MODE,
                    FuzzyMode.IGNORE_ALL,
                    ButtonToolTips.FuzzyMode,
                    ButtonToolTips.FZIgnoreAll);

            this.registerApp(
                    80,
                    Settings.FULLNESS_MODE,
                    FullnessMode.EMPTY,
                    ButtonToolTips.OperationMode,
                    ButtonToolTips.MoveWhenEmpty);
            this.registerApp(
                    81,
                    Settings.FULLNESS_MODE,
                    FullnessMode.HALF,
                    ButtonToolTips.OperationMode,
                    ButtonToolTips.MoveWhenWorkIsDone);
            this.registerApp(
                    82,
                    Settings.FULLNESS_MODE,
                    FullnessMode.FULL,
                    ButtonToolTips.OperationMode,
                    ButtonToolTips.MoveWhenFull);

            this.registerApp(
                    16 + 5,
                    Settings.BLOCK,
                    YesNo.YES,
                    ButtonToolTips.InterfaceBlockingMode,
                    ButtonToolTips.Blocking);
            this.registerApp(
                    16 + 4,
                    Settings.BLOCK,
                    YesNo.NO,
                    ButtonToolTips.InterfaceBlockingMode,
                    ButtonToolTips.NonBlocking);
            this.registerApp(
                    16 + 9,
                    Settings.SMART_BLOCK,
                    YesNo.YES,
                    ButtonToolTips.InterfaceSmartBlockingMode,
                    ButtonToolTips.SmartBlocking);
            this.registerApp(
                    16 + 5,
                    Settings.SMART_BLOCK,
                    YesNo.NO,
                    ButtonToolTips.InterfaceSmartBlockingMode,
                    ButtonToolTips.NonSmartBlocking);

            this.registerApp(16 + 3, Settings.CRAFT_ONLY, YesNo.YES, ButtonToolTips.Craft, ButtonToolTips.CraftOnly);
            this.registerApp(16 + 2, Settings.CRAFT_ONLY, YesNo.NO, ButtonToolTips.Craft, ButtonToolTips.CraftEither);

            this.registerApp(
                    16 * 11 + 2,
                    Settings.CRAFT_VIA_REDSTONE,
                    YesNo.YES,
                    ButtonToolTips.EmitterMode,
                    ButtonToolTips.CraftViaRedstone);
            this.registerApp(
                    16 * 11 + 1,
                    Settings.CRAFT_VIA_REDSTONE,
                    YesNo.NO,
                    ButtonToolTips.EmitterMode,
                    ButtonToolTips.EmitWhenCrafting);

            this.registerApp(
                    16 * 3 + 5,
                    Settings.STORAGE_FILTER,
                    StorageFilter.EXTRACTABLE_ONLY,
                    ButtonToolTips.ReportInaccessibleItems,
                    ButtonToolTips.ReportInaccessibleItemsNo);
            this.registerApp(
                    16 * 3 + 6,
                    Settings.STORAGE_FILTER,
                    StorageFilter.NONE,
                    ButtonToolTips.ReportInaccessibleItems,
                    ButtonToolTips.ReportInaccessibleItemsYes);
            this.registerApp(
                    16 * 3 + 7,
                    Settings.TYPE_FILTER,
                    TypeFilter.ITEMS,
                    ButtonToolTips.TypeFilter,
                    ButtonToolTips.TypeFilterShowItemsOnly);
            this.registerApp(
                    16 * 3 + 8,
                    Settings.TYPE_FILTER,
                    TypeFilter.FLUIDS,
                    ButtonToolTips.TypeFilter,
                    ButtonToolTips.TypeFilterShowFluidsOnly);
            this.registerApp(
                    16 * 3 + 9,
                    Settings.TYPE_FILTER,
                    TypeFilter.ALL,
                    ButtonToolTips.TypeFilter,
                    ButtonToolTips.TypeFilterShowAll);
            this.registerApp(
                    16 * 14,
                    Settings.PLACE_BLOCK,
                    YesNo.YES,
                    ButtonToolTips.BlockPlacement,
                    ButtonToolTips.BlockPlacementYes);
            this.registerApp(
                    16 * 14 + 1,
                    Settings.PLACE_BLOCK,
                    YesNo.NO,
                    ButtonToolTips.BlockPlacement,
                    ButtonToolTips.BlockPlacementNo);

            this.registerApp(
                    16 * 15,
                    Settings.SCHEDULING_MODE,
                    SchedulingMode.DEFAULT,
                    ButtonToolTips.SchedulingMode,
                    ButtonToolTips.SchedulingModeDefault);
            this.registerApp(
                    16 * 15 + 1,
                    Settings.SCHEDULING_MODE,
                    SchedulingMode.ROUNDROBIN,
                    ButtonToolTips.SchedulingMode,
                    ButtonToolTips.SchedulingModeRoundRobin);
            this.registerApp(
                    16 * 15 + 2,
                    Settings.SCHEDULING_MODE,
                    SchedulingMode.RANDOM,
                    ButtonToolTips.SchedulingMode,
                    ButtonToolTips.SchedulingModeRandom);

            this.registerApp(
                    70,
                    Settings.ACTIONS,
                    ActionItems.ORE_FILTER,
                    ButtonToolTips.OreFilter,
                    ButtonToolTips.OreFilterHint);
            this.registerApp(
                    71,
                    Settings.ACTIONS,
                    ActionItems.DOUBLE,
                    ButtonToolTips.DoublePattern,
                    ButtonToolTips.DoublePatternHint);
            this.registerApp(
                    16 * 14 + 2,
                    Settings.CRAFTING_STATUS,
                    CraftingStatus.BUTTON,
                    ButtonToolTips.CraftingStatus,
                    ButtonToolTips.CraftingStatusDesc);

            this.registerApp(
                    4 + 5 * 16,
                    Settings.ACTIONS,
                    ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_ON,
                    ButtonToolTips.ToggleShowOnlyInvalidInterface,
                    ButtonToolTips.ToggleShowOnlyInvalidInterfaceOnDesc);
            this.registerApp(
                    5 + 5 * 16,
                    Settings.ACTIONS,
                    ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_OFF,
                    ButtonToolTips.ToggleShowOnlyInvalidInterface,
                    ButtonToolTips.ToggleShowOnlyInvalidInterfaceOffDesc);

            this.registerApp(
                    6 + 5 * 16,
                    Settings.ACTIONS,
                    ActionItems.MOLECULAR_ASSEMBLEERS_ON,
                    ButtonToolTips.ToggleMolecularAssemblers,
                    ButtonToolTips.ToggleMolecularAssemblersDescOn);
            this.registerApp(
                    7 + 5 * 16,
                    Settings.ACTIONS,
                    ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON,
                    ButtonToolTips.ToggleShowFullInterfaces,
                    ButtonToolTips.ToggleShowFullInterfacesOnDesc);
            this.registerApp(
                    8 + 5 * 16,
                    Settings.ACTIONS,
                    ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF,
                    ButtonToolTips.ToggleShowFullInterfaces,
                    ButtonToolTips.ToggleShowFullInterfacesOffDesc);
            this.registerApp(
                    9 + 5 * 16,
                    Settings.ACTIONS,
                    ActionItems.MOLECULAR_ASSEMBLEERS_OFF,
                    ButtonToolTips.ToggleMolecularAssemblers,
                    ButtonToolTips.ToggleMolecularAssemblersDescOff);
            this.registerApp(
                    6 + 6 * 16,
                    Settings.ACTIONS,
                    ActionItems.HIGHLIGHT_INTERFACE,
                    ButtonToolTips.HighlightInterface,
                    "");
            this.registerApp(
                    72,
                    Settings.ACTIONS,
                    ActionItems.MULTIPLY,
                    ButtonToolTips.MultiplyPattern,
                    ButtonToolTips.MultiplyOrDividePatternHint);
            this.registerApp(
                    73,
                    Settings.ACTIONS,
                    ActionItems.DIVIDE,
                    ButtonToolTips.DividePattern,
                    ButtonToolTips.MultiplyOrDividePatternHint);

            this.registerApp(
                    16 * 9 + 3,
                    Settings.INSERTION_MODE,
                    InsertionMode.DEFAULT,
                    ButtonToolTips.InsertionModeDefault,
                    ButtonToolTips.InsertionModeDefaultDesc);
            this.registerApp(
                    16 * 9 + 4,
                    Settings.INSERTION_MODE,
                    InsertionMode.PREFER_EMPTY,
                    ButtonToolTips.InsertionModePreferEmpty,
                    ButtonToolTips.InsertionModePreferEmptyDesc);
            this.registerApp(
                    16 * 9 + 5,
                    Settings.INSERTION_MODE,
                    InsertionMode.ONLY_EMPTY,
                    ButtonToolTips.InsertionModeOnlyEmpty,
                    ButtonToolTips.InsertionModeOnlyEmptyDesc);

            this.registerApp(
                    16 * 9 + 6,
                    Settings.SIDELESS_MODE,
                    SidelessMode.SIDED,
                    ButtonToolTips.SidelessModeSided,
                    ButtonToolTips.SidelessModeSidedDesc);
            this.registerApp(
                    16 * 9 + 7,
                    Settings.SIDELESS_MODE,
                    SidelessMode.SIDELESS,
                    ButtonToolTips.SidelessModeSideless,
                    ButtonToolTips.SidelessModeSidelessDesc);
            this.registerApp(
                    16 * 9 + 8,
                    Settings.ADVANCED_BLOCKING_MODE,
                    AdvancedBlockingMode.DEFAULT,
                    ButtonToolTips.AdvancedBlockingModeDefault,
                    ButtonToolTips.AdvancedBlockingModeDefaultDesc);
            this.registerApp(
                    16 * 9 + 9,
                    Settings.ADVANCED_BLOCKING_MODE,
                    AdvancedBlockingMode.BLOCK_ON_ALL,
                    ButtonToolTips.AdvancedBlockingModeAll,
                    ButtonToolTips.AdvancedBlockingModeAllDesc);
            this.registerApp(
                    10,
                    Settings.LOCK_CRAFTING_MODE,
                    LockCraftingMode.NONE,
                    ButtonToolTips.LockCraftingMode,
                    ButtonToolTips.LockCraftingModeNone);
            this.registerApp(
                    2,
                    Settings.LOCK_CRAFTING_MODE,
                    LockCraftingMode.LOCK_UNTIL_PULSE,
                    ButtonToolTips.LockCraftingMode,
                    ButtonToolTips.LockCraftingUntilRedstonePulse);
            this.registerApp(
                    0,
                    Settings.LOCK_CRAFTING_MODE,
                    LockCraftingMode.LOCK_WHILE_HIGH,
                    ButtonToolTips.LockCraftingMode,
                    ButtonToolTips.LockCraftingWhileRedstoneHigh);
            this.registerApp(
                    1,
                    Settings.LOCK_CRAFTING_MODE,
                    LockCraftingMode.LOCK_WHILE_LOW,
                    ButtonToolTips.LockCraftingMode,
                    ButtonToolTips.LockCraftingWhileRedstoneLow);
            this.registerApp(
                    7,
                    Settings.LOCK_CRAFTING_MODE,
                    LockCraftingMode.LOCK_UNTIL_RESULT,
                    ButtonToolTips.LockCraftingMode,
                    ButtonToolTips.LockCraftingUntilResultReturned);
            this.registerApp(
                    16 + 2,
                    Settings.CRAFTING_MODE,
                    CraftingMode.STANDARD,
                    ButtonToolTips.CraftingModeStandard,
                    ButtonToolTips.CraftingModeStandardDesc);
            this.registerApp(
                    16 * 6 + 7,
                    Settings.CRAFTING_MODE,
                    CraftingMode.IGNORE_MISSING,
                    ButtonToolTips.CraftingModeIgnoreMissing,
                    ButtonToolTips.CraftingModeIgnoreMissingDesc);
            this.registerApp(16 * 6 + 5, Settings.ACTIONS, ActionItems.EXTRA_OPTIONS, ButtonToolTips.ExtraOptions, "");

            this.registerApp(
                    16 * 10 + 6,
                    Settings.CELL_TYPE,
                    CellType.ITEM,
                    ButtonToolTips.SwitchBytesInfo,
                    ButtonToolTips.SwitchBytesInfo_Item);
            this.registerApp(
                    16 * 10 + 7,
                    Settings.CELL_TYPE,
                    CellType.FLUID,
                    ButtonToolTips.SwitchBytesInfo,
                    ButtonToolTips.SwitchBytesInfo_Fluid);
            this.registerApp(
                    16 * 10 + 8,
                    Settings.CELL_TYPE,
                    CellType.ESSENTIA,
                    ButtonToolTips.SwitchBytesInfo,
                    ButtonToolTips.SwitchBytesInfo_Essentia);

            this.registerApp(
                    16 * 7 + 3,
                    Settings.PRIORITY_CARD_MODE,
                    PriorityCardMode.EDIT,
                    ButtonToolTips.PriorityCardMode,
                    ButtonToolTips.PriorityCardMode_Edit);
            this.registerApp(
                    16 * 7 + 4,
                    Settings.PRIORITY_CARD_MODE,
                    PriorityCardMode.VIEW,
                    ButtonToolTips.PriorityCardMode,
                    ButtonToolTips.PriorityCardMode_View);
            this.registerApp(
                    16 * 7 + 5,
                    Settings.PRIORITY_CARD_MODE,
                    PriorityCardMode.SET,
                    ButtonToolTips.PriorityCardMode,
                    ButtonToolTips.PriorityCardMode_Set);
            this.registerApp(
                    16 * 7 + 6,
                    Settings.PRIORITY_CARD_MODE,
                    PriorityCardMode.INC,
                    ButtonToolTips.PriorityCardMode,
                    ButtonToolTips.PriorityCardMode_Inc);
            this.registerApp(
                    16 * 7 + 7,
                    Settings.PRIORITY_CARD_MODE,
                    PriorityCardMode.DEC,
                    ButtonToolTips.PriorityCardMode,
                    ButtonToolTips.PriorityCardMode_Dec);

            this.registerApp(
                    64,
                    Settings.INTERFACE_TERMINAL_SECTION_ORDER,
                    StringOrder.NATURAL,
                    ButtonToolTips.StringOrder,
                    ButtonToolTips.StringOrderNatural);
            this.registerApp(
                    16,
                    Settings.INTERFACE_TERMINAL_SECTION_ORDER,
                    StringOrder.ALPHANUM,
                    ButtonToolTips.StringOrder,
                    ButtonToolTips.StringOrderAlphanum);

            this.registerApp(
                    16 * 6 + 8,
                    Settings.ACTIONS,
                    ActionItems.CELL_RESTRICTION,
                    ButtonToolTips.CellRestrictionLabel,
                    ButtonToolTips.CellRestrictionHint);

            this.registerApp(
                    16 * 14 + 3,
                    Settings.CRAFTING_ALLOW,
                    CraftingAllow.ALLOW_ALL,
                    ButtonToolTips.CPUAllowMode,
                    ButtonToolTips.CPUAllowAllDesc);
            this.registerApp(
                    16 * 14 + 4,
                    Settings.CRAFTING_ALLOW,
                    CraftingAllow.ONLY_PLAYER,
                    ButtonToolTips.CPUAllowMode,
                    ButtonToolTips.CPUOnlyAllowPlayerDesc);
            this.registerApp(
                    16 * 14 + 5,
                    Settings.CRAFTING_ALLOW,
                    CraftingAllow.ONLY_NONPLAYER,
                    ButtonToolTips.CPUAllowMode,
                    ButtonToolTips.CPUOnlyAllowNonPlayerDesc);

            this.registerApp(
                    16 * 3 + 7,
                    Settings.ACTIONS,
                    ActionItems.TOGGLE_SHOW_ONLY_SUBSTITUTE_ON,
                    ButtonToolTips.ToggleShowOnlySubstitute,
                    ButtonToolTips.ToggleShowOnlySubstituteOnDesc);

            this.registerApp(
                    16 * 3 + 4,
                    Settings.ACTIONS,
                    ActionItems.TOGGLE_SHOW_ONLY_SUBSTITUTE_OFF,
                    ButtonToolTips.ToggleShowOnlySubstitute,
                    ButtonToolTips.ToggleShowOnlySubstituteOffDesc);
        }
    }

    private void registerApp(final int iconIndex, final Settings setting, final Enum val, final ButtonToolTips title,
            final Object hint) {
        final ButtonAppearance a = new ButtonAppearance();
        a.displayName = title.getUnlocalized();
        a.displayValue = (String) (hint instanceof String ? hint : ((ButtonToolTips) hint).getUnlocalized());
        a.index = iconIndex;
        appearances.put(new EnumPair(setting, val), a);
    }

    public void setVisibility(final boolean vis) {
        this.visible = vis;
        this.enabled = vis;
    }

    @Override
    public void drawButton(final Minecraft mc, final int mouseX, final int mouseY) {
        if (this.visible) {
            if (this.halfSize) {
                this.width = 8;
                this.height = 8;
            }
            this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                    && mouseX < this.xPosition + this.width
                    && mouseY < this.yPosition + this.height;
            if (this.enabled) {
                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                GL11.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
            }
            mc.renderEngine.bindTexture(ExtraBlockTextures.GuiTexture("guis/states.png"));
            final int iconIndex = this.getIconIndex();
            final int uv_y = (int) Math.floor(iconIndex / 16);
            final int uv_x = iconIndex - uv_y * 16;
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTranslatef(this.xPosition, this.yPosition, 0.0F);
            if (this.halfSize) GL11.glScalef(0.5f, 0.5f, 0.5f);
            this.drawTexturedModalRect(0, 0, 256 - 16, 256 - 16, 16, 16);
            this.drawTexturedModalRect(0, 0, uv_x * 16, uv_y * 16, 16, 16);
            if (this.halfSize) GL11.glScalef(2f, 2f, 2f);
            GL11.glTranslatef(-this.xPosition, -this.yPosition, 0.0F);
            this.mouseDragged(mc, mouseX, mouseY);
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private int getIconIndex() {
        if (this.buttonSetting != null && this.currentValue != null) {
            final ButtonAppearance app = appearances.get(new EnumPair(this.buttonSetting, this.currentValue));
            if (app == null) {
                return 256 - 1;
            }
            return app.index;
        }
        return 256 - 1;
    }

    public Settings getSetting() {
        return (Settings) this.buttonSetting;
    }

    public Enum getCurrentValue() {
        return this.currentValue;
    }

    @Override
    public String getMessage() {
        String displayName = null;
        String displayValue = null;

        if (this.buttonSetting != null && this.currentValue != null) {
            final ButtonAppearance buttonAppearance = appearances
                    .get(new EnumPair(this.buttonSetting, this.currentValue));
            if (buttonAppearance == null) {
                return "No Such Message";
            }

            displayName = buttonAppearance.displayName;
            displayValue = buttonAppearance.displayValue;
        }

        if (displayName != null) {
            String name = StatCollector.translateToLocal(displayName);
            String value = StatCollector.translateToLocal(displayValue);

            if (name == null || name.isEmpty()) {
                name = displayName;
            }
            if (value == null || value.isEmpty()) {
                value = displayValue;
            }

            if (this.fillVar != null) {
                value = COMPILE.matcher(value).replaceFirst(this.fillVar);
            }

            value = PATTERN_NEW_LINE.matcher(value).replaceAll("\n");

            if (Platform.isServer()) return name + '\n' + value;

            value = Minecraft.getMinecraft().fontRenderer.wrapFormattedStringToWidth(value, 150);

            return name + '\n' + value;
        }
        return null;
    }

    @Override
    public int xPos() {
        return this.xPosition;
    }

    @Override
    public int yPos() {
        return this.yPosition;
    }

    @Override
    public int getWidth() {
        return this.halfSize ? 8 : 16;
    }

    @Override
    public int getHeight() {
        return this.halfSize ? 8 : 16;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    public boolean getMouseIn() {
        return this.field_146123_n;
    }

    public void set(final Enum e) {
        if (this.currentValue != e) {
            this.currentValue = e;
        }
    }

    public boolean isHalfSize() {
        return this.halfSize;
    }

    public void setHalfSize(final boolean halfSize) {
        this.halfSize = halfSize;
    }

    public String getFillVar() {
        return this.fillVar;
    }

    public void setFillVar(final String fillVar) {
        this.fillVar = fillVar;
    }

    private static final class EnumPair {

        final Enum setting;
        final Enum value;

        EnumPair(final Enum a, final Enum b) {
            this.setting = a;
            this.value = b;
        }

        @Override
        public int hashCode() {
            return this.setting.hashCode() ^ this.value.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            final EnumPair other = (EnumPair) obj;
            return other.setting == this.setting && other.value == this.value;
        }
    }

    private static class ButtonAppearance {

        public int index;
        public String displayName;
        public String displayValue;
    }
}
